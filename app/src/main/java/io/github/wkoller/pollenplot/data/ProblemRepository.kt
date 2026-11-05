package io.github.wkoller.pollenplot.data

import androidx.room.withTransaction
import io.github.wkoller.pollenplot.data.db.PlantProblemEntity
import io.github.wkoller.pollenplot.data.db.PollenplotDatabase
import io.github.wkoller.pollenplot.data.db.ProblemTreatmentEntity
import io.github.wkoller.pollenplot.data.db.SearchEntity
import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase
import io.github.wkoller.pollenplot.domain.problems.PlantProblem
import io.github.wkoller.pollenplot.domain.problems.ProblemCatalogue
import io.github.wkoller.pollenplot.domain.problems.Treatment
import io.github.wkoller.pollenplot.domain.problems.TreatmentMethod
import io.github.wkoller.pollenplot.domain.semantic.ProblemGraph
import io.github.wkoller.pollenplot.domain.semantic.SearchText
import io.github.wkoller.pollenplot.domain.semantic.Triple
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * Pests, diseases and deficiencies seen on plants, with their treatments. Problems are in the garden's search index,
 * and they and their photos go with their plant.
 */
class ProblemRepository(
    private val db: PollenplotDatabase,
    private val clock: Clock,
    private val catalogue: () -> ProblemCatalogue,
    private val knowledge: suspend () -> KnowledgeBase,
    /** Deletes a stored photo file. */
    private val deleteFile: suspend (String) -> Unit = {},
) {
    private val dao = db.problemDao()
    private val search = db.searchDao()
    private val plants = db.plantDao()

    val problems: Flow<List<PlantProblem>> = dao.observeProblems().map { list -> list.map { it.toDomain() } }

    val treatments: Flow<List<Treatment>> = dao.observeTreatments().map { list -> list.mapNotNull { it.toDomain() } }

    /** A problem's name: the catalogue's, or the one the user gave it. */
    fun nameOf(problem: PlantProblem): String =
        problem.problemKey?.let { catalogue().problem(it)?.name } ?: problem.otherName ?: problem.problemKey.orEmpty()

    suspend fun problem(id: String): PlantProblem? = dao.problem(id)?.toDomain()

    suspend fun add(plantId: String, problemKey: String?, otherName: String?, observedOn: LocalDate, photo: String?, notes: String): PlantProblem {
        val problem = PlantProblem(UUID.randomUUID().toString(), plantId, problemKey, otherName?.trim()?.ifBlank { null }, observedOn, photo, notes.trim())
        db.withTransaction { write(problem) }
        return problem
    }

    suspend fun setResolved(id: String, on: LocalDate?) = db.withTransaction {
        dao.problem(id)?.toDomain()?.let { write(it.copy(resolvedOn = on)) }
    }

    suspend fun delete(id: String) {
        val photo = db.withTransaction {
            val problem = dao.problem(id) ?: return@withTransaction null
            dao.deleteProblem(id)
            search.delete(id)
            problem.photo
        }
        photo?.let { deleteFile(it) }
    }

    suspend fun addTreatment(problemId: String, method: TreatmentMethod, productText: String, appliedOn: LocalDate, followUpOn: LocalDate?, notes: String): Treatment {
        val treatment = Treatment(UUID.randomUUID().toString(), problemId, method, productText.trim(), appliedOn, followUpOn, notes.trim())
        db.withTransaction {
            dao.upsertTreatment(
                ProblemTreatmentEntity(
                    treatment.id, problemId, method.name, treatment.productText, appliedOn.toEpochDay(), followUpOn?.toEpochDay(), treatment.notes, clock.millis(),
                ),
            )
            dao.problem(problemId)?.toDomain()?.let { index(it) }
        }
        return treatment
    }

    /** Every problem and treatment as linked data. */
    suspend fun triples(): List<Triple> =
        dao.allProblems().flatMap { entity -> entity.toDomain().let { ProblemGraph.problem(it, nameOf(it)) } } +
            dao.allTreatments().mapNotNull { it.toDomain() }.flatMap { ProblemGraph.treatment(it) }

    /** Puts every problem in the search index again, after the garden's index has been rebuilt. */
    suspend fun reindex() = dao.allProblems().forEach { index(it.toDomain()) }

    private suspend fun write(problem: PlantProblem) {
        val createdAt = dao.problem(problem.id)?.createdAt ?: clock.millis()
        dao.upsertProblem(
            PlantProblemEntity(
                problem.id, problem.plantId, problem.problemKey, problem.otherName, problem.observedOn.toEpochDay(), problem.photo, problem.notes,
                problem.resolvedOn?.toEpochDay(), createdAt,
            ),
        )
        index(problem)
    }

    private suspend fun index(problem: PlantProblem) {
        val plant = plants.get(problem.plantId)
        val title = plant?.let { it.customName ?: it.speciesName?.let { name -> knowledge().species(name)?.displayName ?: name } }
        val doc = SearchText.problem(problem, nameOf(problem), title, dao.treatmentsOf(problem.id).mapNotNull { it.toDomain() })
        search.delete(problem.id)
        search.replace(SearchEntity(recordId = doc.id, kind = doc.kind.name, title = doc.title, body = doc.body))
    }

    private fun PlantProblemEntity.toDomain() = PlantProblem(
        id, plantId, problemKey, otherName, LocalDate.ofEpochDay(observedOnEpochDay), photo, notes, resolvedOnEpochDay?.let(LocalDate::ofEpochDay),
    )

    private fun ProblemTreatmentEntity.toDomain(): Treatment? = TreatmentMethod.entries.firstOrNull { it.name == method }?.let {
        Treatment(id, problemId, it, productText, LocalDate.ofEpochDay(appliedOnEpochDay), followUpOnEpochDay?.let(LocalDate::ofEpochDay), notes)
    }
}
