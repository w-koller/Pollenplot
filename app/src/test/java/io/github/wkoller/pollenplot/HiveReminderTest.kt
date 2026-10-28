package io.github.wkoller.pollenplot

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.domain.bees.honey.BroodPattern
import io.github.wkoller.pollenplot.domain.bees.honey.HiveType
import io.github.wkoller.pollenplot.domain.bees.honey.Inspection
import io.github.wkoller.pollenplot.domain.bees.honey.QueenCells
import io.github.wkoller.pollenplot.domain.bees.honey.Temperament
import io.github.wkoller.pollenplot.notify.HiveReminderWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.LocalDate

/** The morning look at the hives, on the fixed spring morning the wildlife tests use: 8 am on 28 October 2026. */
@RunWith(AndroidJUnit4::class)
@Config(application = WildlifeTestApp::class, qualifiers = "en-rAU")
class HiveReminderTest {
    private val app: WildlifeTestApp get() = ApplicationProvider.getApplicationContext()
    private val bees get() = app.container.bees
    private val system get() = app.getSystemService(NotificationManager::class.java)
    private val manager get() = shadowOf(system)

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun inspection(hiveId: String, date: LocalDate) =
        Inspection(bees.newRecordId(), hiveId, date, true, true, BroodPattern.SOLID, Temperament.CALM, 6, 3, QueenCells.NONE, emptySet(), "")

    /** A hive added a week ago and inspected the week before, so only its inspection is due, and overdue. */
    private fun hiveDueForInspection(name: String = "Hive 1"): String = runBlocking {
        val hive = bees.addHive(name, HiveType.LANGSTROTH_8, null, null, 2025, LocalDate.of(2026, 10, 20))
        bees.saveInspection(inspection(hive.id, LocalDate.of(2026, 10, 10)))
        hive.id
    }

    private fun check() = runBlocking { app.container.hiveReminders.run() }

    private fun shown(): List<Notification> = manager.allNotifications

    private val Notification.title: String get() = extras.getString(Notification.EXTRA_TITLE).orEmpty()
    private val Notification.body: String get() = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()

    @Test
    fun `a check that falls due is told about once, on the Hive tasks channel`() {
        hiveDueForInspection()
        check()
        val notification = shown().single()
        assertEquals("Hive 1 is due for an inspection", notification.title)
        assertTrue(notification.body, notification.body.startsWith("It was due by 24 October 2026. Please record it"))
        assertEquals("hive_tasks", notification.channelId)
        assertEquals("Hive tasks", system.getNotificationChannel("hive_tasks").name)

        // The next morning it is not told about again.
        system.cancelAll()
        check()
        assertTrue(shown().isEmpty())
    }

    @Test
    fun `once it is done, the check is forgotten until it falls due again`() {
        val id = hiveDueForInspection()
        check()
        runBlocking { bees.saveInspection(inspection(id, LocalDate.of(2026, 10, 28))) }
        system.cancelAll()
        check()
        assertTrue(shown().isEmpty())
        assertTrue(runBlocking { app.container.settingsStore.current().hiveTasksTold }.isEmpty())
    }

    @Test
    fun `several checks come in one notification`() {
        hiveDueForInspection("Hive 1")
        runBlocking { bees.addHive("Hive 2", HiveType.FLOW, null, null, null, LocalDate.of(2026, 10, 28)) }
        check()
        val notification = shown().single()
        assertEquals("3 hive checks are due", notification.title)
        assertEquals("Hive 1 · Inspection\nHive 2 · Inspection\nHive 2 · Brood check", notification.body)
    }

    @Test
    fun `without permission, checks wait until a notification can say so`() {
        hiveDueForInspection()
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        check()
        assertTrue(shown().isEmpty())
        assertTrue(runBlocking { app.container.settingsStore.current().hiveTasksTold }.isEmpty())

        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        check()
        assertEquals("Hive 1 is due for an inspection", shown().single().title)
    }

    @Test
    fun `nothing is sent with the reminders off`() {
        hiveDueForInspection()
        runBlocking { app.container.settingsStore.update { it.copy(hiveReminders = false) } }
        check()
        assertTrue(shown().isEmpty())
    }

    @Test
    fun `the worker looks at the hives each morning at seven`() {
        hiveDueForInspection()
        val result = runBlocking { TestListenableWorkerBuilder<HiveReminderWorker>(app).build().doWork() }
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals("Hive 1 is due for an inspection", shown().single().title)
        assertEquals("From 8 am, the next seven o'clock is tomorrow's", Duration.ofHours(23), HiveReminderWorker.untilMorning(DemoWildlife.clock))
    }
}
