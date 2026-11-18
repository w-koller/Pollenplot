package io.github.wkoller.pollenplot.bench

import android.os.Looper
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/**
 * The benchmark's own flow. Robolectric cannot load ONNX Runtime's native library, so a model is never timed here;
 * this checks that the screen starts, describes the phone and says where to put models when there are none.
 */
@RunWith(AndroidJUnit4::class)
class EncoderBenchmarkActivityTest {
    @Test
    fun `with no models it says where to push them and finishes`() {
        val activity = Robolectric.buildActivity(EncoderBenchmarkActivity::class.java).setup().get()
        val text = (activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as ScrollView).getChildAt(0) as TextView
        val deadline = System.currentTimeMillis() + 10_000
        while ("${EncoderBenchmarkActivity.TAG} done" !in text.text && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            shadowOf(Looper.getMainLooper()).idle()
        }
        val shown = text.text.toString()
        assertTrue(shown, shown.contains("Device: "))
        assertTrue(shown, shown.contains("No models found. Please push .onnx files to ") && shown.contains("benchmark"))
        assertTrue(shown, shown.contains("${EncoderBenchmarkActivity.TAG} done"))
    }
}
