package io.github.wkoller.pollenplot.place

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Where the phone is, as latitude and longitude, or null when that cannot be found out. */
fun interface LocationSource {
    suspend fun current(): Pair<Double, Double>?
}

/**
 * The phone's location at the approximate accuracy the app asks for, which is all a garden's area needs. It asks the
 * phone's own location service; the app has no location library of its own.
 */
class PhoneLocation(private val context: Context) : LocationSource {
    @SuppressLint("MissingPermission") // Checked first: without the permission there is no location.
    override suspend fun current(): Pair<Double, Double>? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val enabled = manager.getProviders(true)
        val provider = PREFERRED.firstOrNull { it in enabled } ?: return null
        val fresh = withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(provider, signal, context.mainExecutor) { continuation.resume(it) }
            }
        }
        val location = fresh ?: enabled.firstNotNullOfOrNull { manager.getLastKnownLocation(it) } ?: return null
        return location.latitude to location.longitude
    }

    private companion object {
        /** The fused provider combines the others; the network one is quick and good enough for a suburb. */
        val PREFERRED = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        const val TIMEOUT_MS = 30_000L
    }
}
