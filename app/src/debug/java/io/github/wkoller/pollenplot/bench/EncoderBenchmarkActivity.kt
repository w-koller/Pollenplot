package io.github.wkoller.pollenplot.bench

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.app.Activity
import android.app.ActivityManager
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.FloatBuffer
import kotlin.random.Random

/**
 * Times image encoders on this phone, for the question of whether BioCLIP 2 could take BioCAP's place
 * (tools/models/accuracy-research.md). It is in debug builds only, is started over adb, and is not part of the app
 * a user sees, so its words are not in the app's strings.
 *
 * It runs every `.onnx` file in the app's external `benchmark` folder (or those named by the `models` extra, by file
 * name), then the installed accurate pack's encoder if there is one, each in its own session with the app's settings
 * (the processor provider, four threads), one at a time, with a pause between them so one model's heat does not slow
 * the next. For each it records the time to load, the median, 90th percentile and slowest of the timed looks, and the
 * process's memory (PSS) before loading, after loading and at its highest during the looks. Results go to the log
 * (tag [TAG]) and to `results.json` and `results.md` in the same folder. tools/models/run_benchmark.sh runs it.
 *
 * Extras: `models` (comma-separated file names), `runs` (20), `warmup` (3), `threads` (4), `pause` (seconds, 20).
 */
class EncoderBenchmarkActivity : Activity() {
    private lateinit var text: TextView
    private val log = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        text = TextView(this).apply { setPadding(32, 32, 32, 32); setTextIsSelectable(true) }
        setContentView(ScrollView(this).apply { addView(text) })
        if (savedInstanceState != null) return
        val folder = getExternalFilesDir("benchmark")!!.apply { mkdirs() }
        val settings = Settings(
            names = intent.getStringExtra("models")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() },
            runs = intent.getIntExtra("runs", 20),
            warmup = intent.getIntExtra("warmup", 3),
            threads = intent.getIntExtra("threads", 4),
            pauseSeconds = intent.getIntExtra("pause", 20),
        )
        Thread({ run(folder, settings) }, "encoder-benchmark").start()
    }

    private data class Settings(val names: List<String>?, val runs: Int, val warmup: Int, val threads: Int, val pauseSeconds: Int)

    private fun say(line: String) {
        Log.i(TAG, line)
        runOnUiThread {
            log.append(line).append('\n')
            text.text = log.toString()
        }
    }

    private fun run(folder: File, settings: Settings) {
        val models = buildList {
            val files = folder.listFiles { file -> file.name.endsWith(".onnx") }.orEmpty().sortedBy { it.name }
            addAll(settings.names?.map { File(folder, it) } ?: files)
            File(filesDir, "models").listFiles { file -> file.name.startsWith("biocap-pack-") }.orEmpty()
                .map { File(it, "biocap-visual.onnx") }.filter { it.isFile }.maxByOrNull { it.parentFile!!.name }
                ?.takeIf { settings.names == null }?.let(::add)
        }
        val device = device()
        say("Device: $device")
        if (models.isEmpty()) {
            say("No models found. Please push .onnx files to ${folder.path}.")
            say("$TAG done")
            return
        }
        val results = JSONArray()
        models.forEachIndexed { index, file ->
            if (index > 0) {
                say("Pausing ${settings.pauseSeconds} s.")
                SystemClock.sleep(settings.pauseSeconds * 1_000L)
            }
            val result = try {
                measure(file, settings)
            } catch (error: Throwable) {
                JSONObject().put("file", label(file)).put("error", error.toString())
            }
            say(result.toString())
            results.put(result)
        }
        val report = JSONObject().put("device", device).put("runs", settings.runs).put("warmup", settings.warmup)
            .put("threads", settings.threads).put("results", results)
        File(folder, "results.json").writeText(report.toString(1))
        File(folder, "results.md").writeText(markdown(report))
        say("Wrote ${folder.path}/results.json and results.md.")
        say("$TAG done")
    }

    private fun label(file: File) = if (file.startsWith(filesDir)) "installed ${file.parentFile!!.name}" else file.name

    private fun measure(file: File, settings: Settings): JSONObject {
        say("${label(file)}: loading (${file.length() / 1_000_000} MB).")
        Runtime.getRuntime().gc()
        val pssBefore = Debug.getPss()
        val thermalBefore = thermal()
        val environment = OrtEnvironment.getEnvironment()
        val loadStarted = SystemClock.elapsedRealtimeNanos()
        val session = OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(settings.threads)
            environment.createSession(file.path, options)
        }
        session.use {
            val loadMs = (SystemClock.elapsedRealtimeNanos() - loadStarted) / 1_000_000
            val pssLoaded = Debug.getPss()
            val name = session.inputNames.first()
            val shape = (session.inputInfo.getValue(name).info as TensorInfo).shape.map { if (it < 0) 1L else it }.toLongArray()
            val random = Random(7)
            val pixels = FloatArray(shape.fold(1L) { a, b -> a * b }.toInt()) { random.nextFloat() * 4f - 2f }
            var pssPeak = pssLoaded
            val millis = ArrayList<Double>()
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(pixels), shape).use { input ->
                repeat(settings.warmup + settings.runs) { i ->
                    val started = SystemClock.elapsedRealtimeNanos()
                    session.run(mapOf(name to input)).close()
                    val ms = (SystemClock.elapsedRealtimeNanos() - started) / 1e6
                    if (i >= settings.warmup) millis += ms
                    pssPeak = maxOf(pssPeak, Debug.getPss())
                }
            }
            val sorted = millis.sorted()
            fun at(share: Double) = sorted[((sorted.size - 1) * share).toInt()]
            return JSONObject()
                .put("file", label(file))
                .put("megabytes", file.length() / 1_000_000)
                .put("load_ms", loadMs)
                .put("median_ms", tenth(at(0.5)))
                .put("p90_ms", tenth(at(0.9)))
                .put("slowest_ms", tenth(sorted.last()))
                .put("pss_before_mb", pssBefore / 1024)
                .put("pss_loaded_mb", pssLoaded / 1024)
                .put("pss_peak_mb", pssPeak / 1024)
                .put("thermal_before", thermalBefore)
                .put("thermal_after", thermal())
        }
    }

    /** [ms] to a tenth, without the locale's decimal sign. */
    private fun tenth(ms: Double) = Math.round(ms * 10) / 10.0

    private fun thermal(): Int = getSystemService(PowerManager::class.java).currentThermalStatus

    private fun device(): JSONObject {
        val memory = ActivityManager.MemoryInfo().also { getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        return JSONObject()
            .put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("soc", "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}")
            .put("android", Build.VERSION.SDK_INT)
            .put("total_memory_mb", memory.totalMem / 1_048_576)
            .put("processors", Runtime.getRuntime().availableProcessors())
    }

    private fun markdown(report: JSONObject): String = buildString {
        val device = report.getJSONObject("device")
        append("${device.getString("model")} (${device.getString("soc")}), Android ${device.getInt("android")}, ")
        append("${device.getLong("total_memory_mb")} MB of memory. ${report.getInt("runs")} timed looks after ${report.getInt("warmup")} ")
        append("to warm up, ${report.getInt("threads")} threads.\n\n")
        append("| Model | MB | Load, ms | Median, ms | 90th, ms | Slowest, ms | Memory loaded, MB | Memory peak, MB | Heat before, after |\n")
        append("|---|---|---|---|---|---|---|---|---|\n")
        val results = report.getJSONArray("results")
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            if (r.has("error")) {
                append("| ${r.getString("file")} | failed: ${r.getString("error")} |||||||| \n")
                continue
            }
            val before = r.getLong("pss_before_mb")
            append("| ${r.getString("file")} | ${r.getLong("megabytes")} | ${r.getLong("load_ms")} | ${r.getDouble("median_ms")} | ")
            append("${r.getDouble("p90_ms")} | ${r.getDouble("slowest_ms")} | +${r.getLong("pss_loaded_mb") - before} | ")
            append("+${r.getLong("pss_peak_mb") - before} | ${r.getInt("thermal_before")}, ${r.getInt("thermal_after")} |\n")
        }
    }

    companion object {
        const val TAG = "EncoderBenchmark"
    }
}
