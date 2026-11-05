package io.github.wkoller.pollenplot.data

import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.problems.FlowerPlant
import io.github.wkoller.pollenplot.domain.problems.PlantProblem
import io.github.wkoller.pollenplot.domain.problems.ProblemCatalogue
import io.github.wkoller.pollenplot.domain.problems.ProblemInfo
import io.github.wkoller.pollenplot.domain.problems.ProblemTasks
import io.github.wkoller.pollenplot.domain.problems.SprayCheck
import io.github.wkoller.pollenplot.domain.problems.Treatment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/** A problem as the screens show it: its name, the catalogue's page for it when it has one, its plant and treatments. */
data class ProblemView(val problem: PlantProblem, val name: String, val info: ProblemInfo?, val plantTitle: String?, val treatments: List<Treatment>)

/** A treated problem due a second look. */
data class FollowUpDue(val problemId: String, val name: String, val plantTitle: String?, val due: LocalDate) {
    val key: String get() = "$problemId:$due"
}

/** Problems joined with their plants and the catalogue, the follow-ups due, and the plants in flower near a problem. */
class ProblemViews(
    private val problems: ProblemRepository,
    private val views: GardenViews,
    private val garden: GardenRepository,
    private val settings: SettingsStore,
    private val catalogue: () -> ProblemCatalogue,
    private val clock: Clock,
) {
    private val all: Flow<List<ProblemView>> = combine(problems.problems, problems.treatments, views.plants) { list, treatments, plants ->
        val titles = plants.associate { it.plant.id to it.title }
        val byProblem = treatments.groupBy { it.problemId }
        list.map { problem ->
            ProblemView(problem, problems.nameOf(problem), problem.problemKey?.let { catalogue().problem(it) }, titles[problem.plantId], byProblem[problem.id].orEmpty())
        }
    }

    fun forPlant(plantId: String): Flow<List<ProblemView>> = all.map { list -> list.filter { it.problem.plantId == plantId } }

    fun problem(id: String): Flow<ProblemView?> = all.map { list -> list.firstOrNull { it.problem.id == id } }

    val due: Flow<List<FollowUpDue>> = combine(problems.problems, problems.treatments, all) { list, treatments, shown ->
        val byId = shown.associateBy { it.problem.id }
        ProblemTasks.due(list, treatments, today()).map { FollowUpDue(it.problem.id, byId[it.problem.id]?.name.orEmpty(), byId[it.problem.id]?.plantTitle, it.treatment.followUpOn!!) }
    }

    /** The plants likely in flower now in the zone of [plantId]'s plant, or the plant alone when it has no zone. */
    fun inFlowerNear(plantId: String): Flow<List<String>> = combine(views.plants, garden.floweringList, settings.settings) { plants, seen, current ->
        val plant = plants.firstOrNull { it.plant.id == plantId } ?: return@combine emptyList()
        val near = plants.filter { it.isGrowing && (it.plant.id == plantId || (plant.plant.zoneId != null && it.plant.zoneId == plant.plant.zoneId)) }
        val seenDays = seen.groupBy({ it.first }, { it.second })
        SprayCheck.inFlower(near.map { FlowerPlant(it.title, it.care?.care?.flowering, seenDays[it.plant.id].orEmpty()) }, today(), current.hemisphere)
    }

    fun today(): LocalDate = LocalDate.now(clock)
}
