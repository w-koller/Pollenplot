package io.github.wkoller.pollenplot.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A pest, disease or deficiency seen on a plant: one from the catalogue by key, or the user's own name. */
@Entity(
    tableName = "plant_problems",
    foreignKeys = [ForeignKey(entity = PlantEntity::class, parentColumns = ["id"], childColumns = ["plantId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("plantId")],
)
data class PlantProblemEntity(
    @PrimaryKey val id: String,
    val plantId: String,
    val problemKey: String?,
    val otherName: String?,
    val observedOnEpochDay: Long,
    /** A photo of it, relative to the app's files folder. */
    val photo: String?,
    val notes: String,
    val resolvedOnEpochDay: Long?,
    val createdAt: Long,
)

/** How a problem was treated, and when to look at it again. No amount is suggested or stored. */
@Entity(
    tableName = "problem_treatments",
    foreignKeys = [ForeignKey(entity = PlantProblemEntity::class, parentColumns = ["id"], childColumns = ["problemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("problemId")],
)
data class ProblemTreatmentEntity(
    @PrimaryKey val id: String,
    val problemId: String,
    /** A `TreatmentMethod`, such as SOAP_SPRAY. */
    val method: String,
    val productText: String,
    val appliedOnEpochDay: Long,
    val followUpOnEpochDay: Long?,
    val notes: String,
    val createdAt: Long,
)

@Dao
interface ProblemDao {
    @Query("SELECT * FROM plant_problems ORDER BY observedOnEpochDay DESC, createdAt DESC")
    fun observeProblems(): Flow<List<PlantProblemEntity>>

    @Query("SELECT * FROM plant_problems")
    suspend fun allProblems(): List<PlantProblemEntity>

    @Query("SELECT * FROM plant_problems WHERE id = :id")
    suspend fun problem(id: String): PlantProblemEntity?

    @Query("SELECT id FROM plant_problems WHERE plantId = :plantId")
    suspend fun idsFor(plantId: String): List<String>

    @Upsert
    suspend fun upsertProblem(problem: PlantProblemEntity)

    @Query("DELETE FROM plant_problems WHERE id = :id")
    suspend fun deleteProblem(id: String)

    @Query("SELECT * FROM problem_treatments ORDER BY appliedOnEpochDay DESC, createdAt DESC")
    fun observeTreatments(): Flow<List<ProblemTreatmentEntity>>

    @Query("SELECT * FROM problem_treatments")
    suspend fun allTreatments(): List<ProblemTreatmentEntity>

    @Query("SELECT * FROM problem_treatments WHERE problemId = :problemId")
    suspend fun treatmentsOf(problemId: String): List<ProblemTreatmentEntity>

    @Upsert
    suspend fun upsertTreatment(treatment: ProblemTreatmentEntity)
}
