package io.github.wkoller.pollenplot

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.wkoller.pollenplot.data.BeeRepository
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.Locality
import io.github.wkoller.pollenplot.data.db.PollenplotDatabase
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.domain.bees.honey.BroodPattern
import io.github.wkoller.pollenplot.domain.bees.honey.ColonyEvent
import io.github.wkoller.pollenplot.domain.bees.honey.ColonyEventKind
import io.github.wkoller.pollenplot.domain.bees.honey.HiveSign
import io.github.wkoller.pollenplot.domain.bees.honey.HiveStatus
import io.github.wkoller.pollenplot.domain.bees.honey.HiveType
import io.github.wkoller.pollenplot.domain.bees.honey.Inspection
import io.github.wkoller.pollenplot.domain.bees.honey.QueenCells
import io.github.wkoller.pollenplot.domain.bees.honey.Temperament
import io.github.wkoller.pollenplot.domain.bees.honey.VarroaCheck
import io.github.wkoller.pollenplot.domain.bees.honey.VarroaMethod
import io.github.wkoller.pollenplot.domain.garden.ZoneKind
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.semantic.JsonLd
import io.github.wkoller.pollenplot.weeds.WeedData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BeeRepositoryTest {
    private lateinit var db: PollenplotDatabase
    private lateinit var garden: GardenRepository
    private lateinit var bees: BeeRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<PollenplotApp>()
        db = PollenplotDatabase.inMemory(context)
        val knowledge = KnowledgeRepository(context, CoroutineScope(Dispatchers.IO))
        garden = GardenRepository(db, Clock.systemUTC(), { knowledge.await() }, { Locality(AuState.QLD, WeedData.EMPTY) }, { knowledge.awaitFauna() })
        bees = BeeRepository(db, Clock.systemUTC())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a hive keeps its records, and they go with it`() = runBlocking {
        val corner = garden.addZone("Hive Corner", ZoneKind.HIVE_CORNER)
        val apiary = bees.addApiary("Backyard", "QB1234")
        val hive = bees.addHive("Hive 1", HiveType.LANGSTROTH_8, corner.id, apiary.id, queenYear = 2025, startedOn = LocalDate.of(2025, 9, 1))
        bees.saveInspection(
            Inspection(
                bees.newRecordId(), hive.id, LocalDate.of(2026, 10, 20), queenSeen = true, eggsSeen = true, brood = BroodPattern.SOLID,
                temperament = Temperament.CALM, framesOfBees = 7, framesOfStores = 3, queenCells = QueenCells.NONE,
                signs = setOf(HiveSign.SMALL_HIVE_BEETLE, HiveSign.CHALKBROOD), actions = "Added a super",
            ),
        )
        bees.saveCheck(VarroaCheck(bees.newRecordId(), hive.id, LocalDate.of(2026, 10, 20), VarroaMethod.ALCOHOL_WASH, 300, 0, null))
        val inspection = bees.inspections.first().single()
        assertEquals(setOf(HiveSign.SMALL_HIVE_BEETLE, HiveSign.CHALKBROOD), inspection.signs)
        assertEquals(1, bees.checks.first().size)
        assertTrue("Found by its zone", garden.search("corner").any { it.recordId == hive.id && it.kind == "HIVE" })
        assertTrue("Found by its apiary", garden.search("backyard").any { it.recordId == hive.id })

        bees.saveEvent(ColonyEvent(bees.newRecordId(), hive.id, LocalDate.of(2026, 10, 25), ColonyEventKind.DIED))
        assertEquals("A colony that died takes its hive's status with it", HiveStatus.DEAD, bees.hives.first().single().status)

        bees.deleteHive(hive.id)
        assertTrue(bees.inspections.first().isEmpty())
        assertTrue(bees.checks.first().isEmpty())
        assertTrue(bees.events.first().isEmpty())
        assertTrue("Gone from search", garden.search("hive").none { it.recordId == hive.id })
    }

    @Test
    fun `the records go into the linked data with the garden's`() = runBlocking {
        val corner = garden.addZone("Hive Corner", ZoneKind.HIVE_CORNER)
        val hive = bees.addHive("Hive 1", HiveType.LANGSTROTH_8, corner.id, null, queenYear = 2025, startedOn = LocalDate.of(2025, 9, 1))
        val check = VarroaCheck(bees.newRecordId(), hive.id, LocalDate.of(2026, 10, 20), VarroaMethod.ALCOHOL_WASH, 300, 4, null)
        bees.saveCheck(check)
        val nodes = Json.parseToJsonElement(JsonLd.document(garden.triples() + bees.triples()).toString()).jsonObject["@graph"]!!.jsonArray.map { it.jsonObject }
        fun node(id: String) = nodes.single { it["@id"]!!.jsonPrimitive.content == "urn:uuid:$id" }
        assertEquals("pp:HoneyBeeHive", node(hive.id)["@type"]!!.jsonPrimitive.content)
        assertEquals("urn:uuid:${corner.id}", node(hive.id)["schema:containedInPlace"]!!.jsonObject["@id"]!!.jsonPrimitive.content)
        val observation = node(check.id)
        assertEquals("urn:uuid:${hive.id}", observation["sosa:hasFeatureOfInterest"]!!.jsonObject["@id"]!!.jsonPrimitive.content)
        assertEquals("1.3", observation["pp:mitesPer100Bees"]!!.jsonObject["@value"]!!.jsonPrimitive.content)
        assertTrue("The zone is there too", nodes.any { it["@id"]!!.jsonPrimitive.content == "urn:uuid:${corner.id}" })
    }

    @Test
    fun `hives stay in search when the garden's index is rebuilt`() = runBlocking {
        val hive = bees.addHive("Sugarloaf", HiveType.FLOW, null, null, queenYear = null, startedOn = null)
        garden.reindexAll()
        bees.reindex()
        assertTrue(garden.search("sugarloaf").any { it.recordId == hive.id })
    }
}
