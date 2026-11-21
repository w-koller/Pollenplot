package io.github.wkoller.pollenplot.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

// Field Guide moves on Material's expressive springs (MotionScheme.expressive() in PollenplotTheme): things that move
// across the screen overshoot a little and settle, and colours and fades do not. Every animation is short and can be
// interrupted, and when the phone's animations are turned off, things appear in their final place at once.

/**
 * Whether the phone's animations are on. "Remove animations" in the phone's accessibility settings sets the animator
 * duration scale to 0, and then Pollenplot shows each animation's last frame instead.
 */
fun animationsOn(context: Context): Boolean =
    runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) != 0f

/** Whether animations are on, read once by [PollenplotTheme]. */
val LocalAnimationsOn = staticCompositionLocalOf { true }

/** Whether this screen should animate. See [animationsOn]. */
@Composable
@ReadOnlyComposable
fun animationsOn(): Boolean = LocalAnimationsOn.current
