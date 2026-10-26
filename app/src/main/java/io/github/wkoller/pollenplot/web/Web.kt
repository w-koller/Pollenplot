package io.github.wkoller.pollenplot.web

import io.github.wkoller.pollenplot.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/**
 * The few web services the app reads: place search, weed lists and plant records near the garden. Tests answer from
 * recorded responses instead, so they never reach the real sites.
 */
interface Web {
    /** The body of a GET request. Throws [IOException] when the request fails or the answer is not a success. */
    suspend fun get(url: String): String

    /** The body of a POST request that sends [json]. */
    suspend fun post(url: String, json: String): String

    /** The bytes of a file, such as a photo. */
    suspend fun bytes(url: String): ByteArray
}

/** [Web] over the platform's HTTP client, which also unpacks compressed answers. */
class HttpWeb : Web {
    override suspend fun get(url: String): String = request(url, null).decodeToString()

    override suspend fun post(url: String, json: String): String = request(url, json).decodeToString()

    override suspend fun bytes(url: String): ByteArray = request(url, null)

    private suspend fun request(url: String, body: String?): ByteArray = withContext(Dispatchers.IO) {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json, image/*")
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code from ${connection.url.host}")
            connection.inputStream.use { input ->
                // The largest answer, the plant records near a garden, is well under a megabyte.
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > MAX_BYTES) throw IOException("The answer from ${connection.url.host} is too large")
                }
                out.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 30_000
        const val MAX_BYTES = 16 * 1024 * 1024
        const val BUFFER_BYTES = 64 * 1024
        val USER_AGENT = "Pollenplot/${BuildConfig.VERSION_NAME} (+https://github.com/w-koller/Pollenplot)"
    }
}
