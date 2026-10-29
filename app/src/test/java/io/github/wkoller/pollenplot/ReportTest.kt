package io.github.wkoller.pollenplot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.domain.bees.honey.HiveType
import io.github.wkoller.pollenplot.domain.bees.honey.VarroaCheck
import io.github.wkoller.pollenplot.domain.bees.honey.VarroaMethod
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.report.ReportFigure
import io.github.wkoller.pollenplot.report.PageSink
import io.github.wkoller.pollenplot.report.PdfReportWriter
import io.github.wkoller.pollenplot.report.ReportPart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipInputStream

/** The garden report for the demo yard, on the fixed spring morning of 28 October 2026. Saves its pages as pictures. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = WildlifeTestApp::class, qualifiers = "en-rAU")
class ReportTest {
    private val app: WildlifeTestApp get() = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.of(2026, 10, 28)

    /** Draws each page on a white picture, as a PDF page would be. */
    private class PictureSink : PageSink {
        val pages = mutableListOf<Bitmap>()

        override fun begin(number: Int, width: Int, height: Int): Canvas {
            val bitmap = Bitmap.createBitmap(width * 2, height * 2, Bitmap.Config.ARGB_8888).also { pages += it }
            return Canvas(bitmap).apply {
                drawColor(Color.WHITE)
                scale(2f, 2f)
            }
        }

        override fun end() = Unit
    }

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        runBlocking {
            app.container.knowledge.await()
            app.container.knowledge.awaitFauna()
            app.container.settingsStore.update { it.copy(gardenState = AuState.QLD) }
            DemoGarden.seed(app.container.garden).also { DemoWildlife.seed(app.container.garden, it) }
            val hive = app.container.bees.addHive("Hive 1", HiveType.LANGSTROTH_8, null, null, 2025, LocalDate.of(2026, 9, 1))
            app.container.bees.saveCheck(VarroaCheck(app.container.bees.newRecordId(), hive.id, LocalDate.of(2026, 10, 20), VarroaMethod.ALCOHOL_WASH, 300, 4, null))
        }
    }

    @Test
    fun `a report covers each part chosen, with the records in its period`() = runBlocking {
        val report = app.container.reports.build(ReportPart.entries.toSet(), today.minusMonths(3), today)
        assertEquals(ReportPart.entries.size, report.sections.size)
        assertEquals("Pollenplot garden report", report.title)
        val checks = report.tables.single { it.title == "Mite checks" }
        assertEquals(1, checks.rows.size)
        val plants = report.tables.single { it.title == "Plants" }
        assertTrue("Every plant in the demo yard is listed", plants.rows.size >= 12)
        assertTrue("A weed is marked", plants.rows.any { row -> (row[0] as io.github.wkoller.pollenplot.domain.report.ReportCell.Text).text == "Lantana" && (row[7] as io.github.wkoller.pollenplot.domain.report.ReportCell.Text).text == "Yes" })
        val plot = report.sections.first().figures.filterIsInstance<ReportFigure.Plot>().single()
        assertEquals("The demo yard's four zones, in their places", 4, plot.zones.size)
        assertTrue("No plant has a cut-out yet", report.sections.first().figures.none { it is ReportFigure.Pictures })
        val onlyBees = app.container.reports.build(setOf(ReportPart.HONEY), today.minusMonths(1), today)
        assertEquals(listOf("Honey bees"), onlyBees.sections.map { it.title })
        assertEquals("A part with nothing recorded says so", listOf("Nothing was recorded in this period."), report.sections.single { it.title == "Bee hotels" }.paragraphs)
    }

    @Test
    fun `the spreadsheet has a sheet for each table`() = runBlocking {
        val out = ByteArrayOutputStream()
        app.container.writeReport(setOf(ReportPart.GARDEN, ReportPart.HONEY), today.minusMonths(3), today, pdf = false, out = out)
        val sheets = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name.startsWith("xl/worksheets/")) sheets += entry.name
            }
        }
        assertEquals("Zones and plants, then hives, inspections, mite checks, treatments and harvests", 7, sheets.size)
    }

    @Test
    fun `a plant's cut-out goes in the PDF unless pictures are left out`() = runBlocking {
        val lemon = app.container.garden.plantList.first().single { it.customName == "Mum's lemon" }
        val cut = app.container.photos.saveThumbnail(TestPhotos.paintedPlant(TestPhotos.bottlebrush, 256))
        app.container.garden.updatePlant(lemon.copy(thumbnail = cut))
        val with = app.container.reports.build(setOf(ReportPart.GARDEN), today.minusMonths(3), today)
        val pictures = with.sections.single().figures.filterIsInstance<ReportFigure.Pictures>().single()
        assertEquals(listOf("Mum's lemon"), pictures.items.map { it.caption })
        assertTrue(File(pictures.items.single().path).isFile)
        val without = app.container.reports.build(setOf(ReportPart.GARDEN), today.minusMonths(3), today, pictures = false)
        assertTrue(without.sections.single().figures.none { it is ReportFigure.Pictures })

        val sink = PictureSink()
        PdfReportWriter("Pollenplot garden report").render(with, sink)
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        sink.pages.forEachIndexed { index, page ->
            File(dir, "report_garden_page${index + 1}.png").outputStream().use { page.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun `the PDF's pages are drawn`() = runBlocking {
        val report = app.container.reports.build(ReportPart.entries.toSet(), today.minusMonths(3), today)
        val sink = PictureSink()
        PdfReportWriter("Pollenplot garden report").render(report, sink)
        assertTrue("The demo yard's report runs over more than one page", sink.pages.size > 1)
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        sink.pages.take(2).forEachIndexed { index, page ->
            File(dir, "report_page${index + 1}.png").outputStream().use { page.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
