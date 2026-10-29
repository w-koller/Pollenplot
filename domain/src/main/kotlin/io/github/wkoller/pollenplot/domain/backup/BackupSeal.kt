package io.github.wkoller.pollenplot.domain.backup

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * A sealed backup cannot be opened. With [wrongPassword], the password does not match the one the backup was sealed
 * with (a damaged header looks the same). Otherwise the password was right and the file was changed, cut short or
 * added to.
 */
class SealException(val wrongPassword: Boolean, message: String) : IOException(message)

/**
 * Encrypts a backup with a password, as a stream, so a backup with thousands of photos never has to fit in memory.
 *
 * The key comes from the password through PBKDF2 with HMAC-SHA256. The backup is cut into segments of
 * [SEGMENT_BYTES], each encrypted with AES-256-GCM under its own nonce: a random prefix, the segment's number and
 * whether it is the last. The last segment always holds fewer than [SEGMENT_BYTES], so a reader knows where the file
 * ends. A changed, reordered, cut short or extended file is therefore refused rather than read wrongly. This is the
 * STREAM construction of Hoang, Reyhanitabar, Rogaway and Vizár (2015), which Tink also uses for streaming AEAD.
 *
 * The header holds a magic word, the version, the number of PBKDF2 rounds, the salt, the nonce prefix and a check
 * value made from the key, so a wrong password is told from a damaged file before any segment is opened. The check
 * and the AES key are each made from PBKDF2's output with HMAC-SHA256 under their own label, so neither gives the
 * other away. The header is authenticated with every segment.
 */
object BackupSeal {
    private val MAGIC = "PPSEALED".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    const val ITERATIONS = 210_000

    /** A file asking for more rounds than this is damaged, and would keep the phone busy for minutes. */
    private const val MAX_ITERATIONS = 10 * ITERATIONS
    const val SEGMENT_BYTES = 64 * 1024
    private const val SALT_BYTES = 16
    private const val PREFIX_BYTES = 7
    private const val CHECK_BYTES = 16
    private const val TAG_BYTES = 16
    private const val KEY_BITS = 256
    private val HEADER_BYTES = MAGIC.size + 1 + Int.SIZE_BYTES + SALT_BYTES + PREFIX_BYTES + CHECK_BYTES
    private val KEY_LABEL = "Pollenplot backup key".toByteArray(Charsets.US_ASCII)
    private val CHECK_LABEL = "Pollenplot backup password check".toByteArray(Charsets.US_ASCII)

    /** Whether [head], the first bytes of a file, begin a sealed backup. */
    fun isSealed(head: ByteArray): Boolean = head.size >= MAGIC.size && MAGIC.indices.all { head[it] == MAGIC[it] }

    /** How many bytes [isSealed] needs to look at. */
    val MAGIC_BYTES: Int get() = MAGIC.size

    /**
     * Writes the header to [out] and returns a stream that encrypts what is written to it. Closing the returned stream
     * writes the last segment and closes [out]; a backup whose stream was not closed cannot be opened.
     */
    fun seal(out: OutputStream, password: String, random: SecureRandom = SecureRandom(), iterations: Int = ITERATIONS): OutputStream {
        require(password.isNotEmpty()) { "A sealed backup needs a password" }
        require(iterations in 1..MAX_ITERATIONS)
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val prefix = ByteArray(PREFIX_BYTES).also(random::nextBytes)
        val keys = Keys.of(password, salt, iterations)
        val header = ByteBuffer.allocate(HEADER_BYTES).put(MAGIC).put(VERSION).putInt(iterations).put(salt).put(prefix).put(keys.check).array()
        out.write(header)
        return SealingStream(out, Segments(keys.aes, prefix, header, Cipher.ENCRYPT_MODE))
    }

    /**
     * Reads the header from [input] and returns a stream of what was sealed. A wrong password is found from the header,
     * before anything else is read. Reading further throws [SealException] where the file is damaged.
     */
    fun open(input: InputStream, password: String): InputStream {
        val header = ByteArray(HEADER_BYTES)
        if (readUpTo(input, header) < HEADER_BYTES || !isSealed(header)) throw SealException(false, "This is not a sealed backup")
        val fields = ByteBuffer.wrap(header, MAGIC.size, HEADER_BYTES - MAGIC.size)
        if (fields.get() != VERSION) throw SealException(false, "This sealed backup has a version this app cannot open")
        val iterations = fields.int
        if (iterations !in 1..MAX_ITERATIONS) throw SealException(false, "The sealed backup's header is damaged")
        val salt = ByteArray(SALT_BYTES).also { fields.get(it) }
        val prefix = ByteArray(PREFIX_BYTES).also { fields.get(it) }
        val check = ByteArray(CHECK_BYTES).also { fields.get(it) }
        val keys = Keys.of(password, salt, iterations)
        if (!MessageDigest.isEqual(check, keys.check)) throw SealException(true, "The password does not match this backup")
        return OpeningStream(input, Segments(keys.aes, prefix, header, Cipher.DECRYPT_MODE))
    }

    /** The AES key and the password check, both made from the password. */
    private class Keys(val aes: SecretKeySpec, val check: ByteArray) {
        companion object {
            fun of(password: String, salt: ByteArray, iterations: Int): Keys {
                val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
                val stretched = try {
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
                } finally {
                    spec.clearPassword()
                }
                val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(stretched, "HmacSHA256")) }
                val aes = SecretKeySpec(mac.doFinal(KEY_LABEL), "AES")
                val check = mac.doFinal(CHECK_LABEL).copyOf(CHECK_BYTES)
                stretched.fill(0)
                return Keys(aes, check)
            }
        }
    }

    /** Reads until [buffer] is full or the stream ends, and returns how many bytes were read. */
    private fun readUpTo(input: InputStream, buffer: ByteArray): Int {
        var filled = 0
        while (filled < buffer.size) {
            val read = input.read(buffer, filled, buffer.size - filled)
            if (read < 0) break
            filled += read
        }
        return filled
    }

    /** Encrypts or decrypts one segment at a time, each under the nonce its number and place give it. */
    private class Segments(private val key: SecretKeySpec, private val prefix: ByteArray, private val header: ByteArray, private val mode: Int) {
        private val cipher: Cipher = Cipher.getInstance("AES/GCM/NoPadding")
        var index = 0
            private set

        fun next(bytes: ByteArray, length: Int, last: Boolean): ByteArray {
            // Four bytes count 2³¹ segments of 64 KiB, which no garden's photos come near.
            if (index == Int.MAX_VALUE) throw IOException("The backup is too large to seal")
            val nonce = ByteBuffer.allocate(PREFIX_BYTES + Int.SIZE_BYTES + 1).put(prefix).putInt(index).put(if (last) 1 else 0).array()
            cipher.init(mode, key, GCMParameterSpec(TAG_BYTES * 8, nonce))
            cipher.updateAAD(header)
            return cipher.doFinal(bytes, 0, length).also { index++ }
        }
    }

    private class SealingStream(private val out: OutputStream, private val segments: Segments) : OutputStream() {
        private val buffer = ByteArray(SEGMENT_BYTES)
        private var filled = 0
        private var closed = false

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (closed) throw IOException("The sealed backup is already closed")
            var offset = off
            var remaining = len
            while (remaining > 0) {
                val count = minOf(remaining, SEGMENT_BYTES - filled)
                System.arraycopy(b, offset, buffer, filled, count)
                filled += count
                offset += count
                remaining -= count
                // A full segment is never the last one, because the last always holds less.
                if (filled == SEGMENT_BYTES) emit(last = false)
            }
        }

        private fun emit(last: Boolean) {
            try {
                out.write(segments.next(buffer, filled, last))
            } catch (e: GeneralSecurityException) {
                throw IOException("The backup could not be sealed", e)
            }
            filled = 0
        }

        override fun flush() = out.flush()

        override fun close() {
            if (closed) return
            closed = true
            out.use {
                emit(last = true)
                it.flush()
            }
        }
    }

    private class OpeningStream(private val input: InputStream, private val segments: Segments) : InputStream() {
        private val sealed = ByteArray(SEGMENT_BYTES + TAG_BYTES)
        private var plain = ByteArray(0)
        private var position = 0
        private var finished = false

        init {
            nextSegment()
        }

        private fun nextSegment() {
            val length = readUpTo(input, sealed)
            val last = length < sealed.size
            if (length < TAG_BYTES) throw SealException(false, "The sealed backup is cut short")
            plain = try {
                segments.next(sealed, length, last)
            } catch (e: AEADBadTagException) {
                throw SealException(false, "A part of the sealed backup does not open")
            } catch (e: GeneralSecurityException) {
                throw SealException(false, "A part of the sealed backup does not open")
            }
            position = 0
            if (last) {
                finished = true
                if (input.read() >= 0) throw SealException(false, "The sealed backup has more after its end")
            }
        }

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xff
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (position == plain.size) {
                if (finished) return -1
                nextSegment()
            }
            val count = minOf(len, plain.size - position)
            System.arraycopy(plain, position, b, off, count)
            position += count
            return count
        }

        override fun available(): Int = plain.size - position

        override fun close() = input.close()
    }
}
