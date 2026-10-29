package io.github.wkoller.pollenplot

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.domain.bees.stingless.Facing
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeBox
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeLog
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeLogKind
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeOrigin
import io.github.wkoller.pollenplot.domain.bees.stingless.StinglessSpecies
import io.github.wkoller.pollenplot.domain.garden.ZoneKind
import io.github.wkoller.pollenplot.domain.place.GardenPlace
import io.github.wkoller.pollenplot.notify.HeatAlertCheck
import io.github.wkoller.pollenplot.web.Web
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.LocalDate

/** Stingless bee hives, their records and the warnings before hot days, on the fixed spring morning of 28 October 2026. */
@RunWith(AndroidJUnit4::class)
@Config(application = WildlifeTestApp::class, qualifiers = "en-rAU")
class NativeBeeTest {
    private val app: WildlifeTestApp get() = ApplicationProvider.getApplicationContext()
    private val natives get() = app.container.nativeBees
    private val system get() = app.getSystemService(NotificationManager::class.java)

    /** Answers the forecast with [answer], or fails as a phone with no connection does. */
    private class ForecastWeb(var answer: String?) : Web {
        val asked = mutableListOf<String>()

        override suspend fun get(url: String): String {
            asked += url
            return answer ?: throw IOException("No connection")
        }

        override suspend fun post(url: String, json: String): String = throw IOException("Not used")

        override suspend fun bytes(url: String): ByteArray = throw IOException("Not used")
    }

    private val hot = """{"timezone":"Australia/Brisbane","daily":{"time":["2026-10-28","2026-10-29","2026-10-30"],"temperature_2m_max":[31.0,39.4,27.0]}}"""
    private val web = ForecastWeb(hot)
    private val check get() = HeatAlertCheck(natives, app.container.settingsStore, web, app.container.notifications, DemoWildlife.clock)

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking { app.container.settingsStore.update { it.copy(gardenPlace = GardenPlace.at(-27.4987, 152.9934, "Taringa")) } }
    }

    private fun addHive() = runBlocking {
        natives.addHive("Sugarbag 1", StinglessSpecies.TETRAGONULA_CARBONARIA, NativeBox.OATH, NativeOrigin.BOUGHT, null, Facing.NE, LocalDate.of(2026, 10, 1))
    }

    private fun shown(): List<Notification> = shadowOf(system).allNotifications

    @Test
    fun `a hive keeps its records, is found in search and goes into the linked data`() = runBlocking {
        val corner = app.container.garden.addZone("Hive Corner", ZoneKind.HIVE_CORNER)
        val hive = natives.addHive("Sugarbag 1", StinglessSpecies.TETRAGONULA_CARBONARIA, NativeBox.OATH, NativeOrigin.SPLIT, corner.id, Facing.E, LocalDate.of(2026, 10, 1))
        natives.saveLog(NativeLog(natives.newRecordId(), hive.id, LocalDate.of(2026, 10, 20), NativeLogKind.WEIGHT, weightGrams = 7_100))
        natives.saveLog(NativeLog(natives.newRecordId(), hive.id, LocalDate.of(2026, 10, 21), NativeLogKind.ACTIVITY, beesPerMinute = 45))
        val view = app.container.beeViews.nativeHives.first().single()
        assertEquals("Hive Corner", view.zoneName)
        assertEquals(7_100, view.strength.weightGrams)
        assertEquals(45, view.strength.beesPerMinute)
        assertTrue(app.container.garden.search("carbonaria").any { it.recordId == hive.id && it.kind == "NATIVE_HIVE" })
        val export = app.container.exportJsonLd()
        assertTrue(export.contains("pp:StinglessBeeHive"))
        assertTrue(export.contains("\"pp:beesPerMinute\""))

        natives.deleteHive(hive.id)
        assertTrue("A hive's records go with it", natives.logs.first().isEmpty())
        assertTrue(app.container.garden.search("carbonaria").none { it.recordId == hive.id })
    }

    @Test
    fun `a hot day tomorrow brings one warning, which opens the guide`() {
        addHive()
        runBlocking { check.run() }
        val notification = shown().single()
        assertEquals("Tomorrow is forecast to reach 39.4 °C", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("hive_tasks", notification.channelId)
        assertTrue("Only the rounded place is sent", web.asked.single().contains("latitude=-27.50&longitude=152.99"))

        system.cancelAll()
        runBlocking { check.run() }
        assertTrue("Not again for the same day", shown().isEmpty())
        assertEquals(setOf("2026-10-29"), runBlocking { app.container.settingsStore.current().heatToldDays })
    }

    @Test
    fun `no warning without a hive, a place, a hot day or a connection`() {
        runBlocking { check.run() }
        assertTrue("No hive, so the forecast is not even asked for", web.asked.isEmpty())
        addHive()
        runBlocking { app.container.settingsStore.update { it.copy(heatAlertTenthsC = 400) } }
        runBlocking { check.run() }
        assertTrue("39.4 °C is under the 40 °C chosen", shown().isEmpty())
        runBlocking { app.container.settingsStore.update { it.copy(heatAlertTenthsC = 380) } }
        web.answer = null
        runBlocking { check.run() }
        assertTrue(shown().isEmpty())
        assertTrue("Tried again the next time", runBlocking { app.container.settingsStore.current().heatToldDays }.isEmpty())
    }
}
