package io.github.wkoller.pollenplot.domain.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.random.Random

class BackupSealTest {
    private val segment = BackupSeal.SEGMENT_BYTES

    /** The magic word, version, rounds, salt, nonce prefix and password check. */
    private val header = 52

    /** Few rounds, so the tests stay quick; the app uses [BackupSeal.ITERATIONS]. */
    private fun seal(data: ByteArray, password: String = "grevillea", iterations: Int = 1_000): ByteArray {
        val out = ByteArrayOutputStream()
        BackupSeal.seal(out, password, iterations = iterations).use { it.write(data) }
        return out.toByteArray()
    }

    private fun open(sealed: ByteArray, password: String = "grevillea"): ByteArray =
        BackupSeal.open(ByteArrayInputStream(sealed), password).use(InputStream::readBytes)

    private fun data(size: Int) = Random(size).nextBytes(size)

    private fun failure(block: () -> Unit): SealException {
        try {
            block()
        } catch (e: SealException) {
            return e
        }
        fail("The damaged backup was read")
        throw AssertionError()
    }

    @Test
    fun `what is sealed opens again at every size around a segment`() {
        listOf(0, 1, segment - 1, segment, segment + 1, 3 * segment + 17).forEach { size ->
            val data = data(size)
            assertArrayEquals("$size bytes", data, open(seal(data)))
        }
    }

    @Test
    fun `the file is a little larger than what it holds`() {
        val sealed = seal(data(3 * segment + 17))
        // The header and a 16-byte tag on each of the four segments.
        assertEquals(header + 3 * segment + 17 + 4 * 16, sealed.size)
        assertTrue(BackupSeal.isSealed(sealed))
        assertFalse(BackupSeal.isSealed("PK\u0003\u0004 a zip".toByteArray()))
    }

    @Test
    fun `small writes and large writes seal the same bytes`() {
        val data = data(2 * segment + 5)
        val out = ByteArrayOutputStream()
        BackupSeal.seal(out, "grevillea", iterations = 1_000).use { sealed -> data.forEach { sealed.write(it.toInt()) } }
        assertArrayEquals(data, open(out.toByteArray()))
    }

    @Test
    fun `each seal has its own salt and nonces`() {
        val data = data(100)
        assertNotEquals(seal(data).toList(), seal(data).toList())
    }

    @Test
    fun `a password with letters beyond English opens its backup`() {
        val data = data(segment + 9)
        assertArrayEquals(data, open(seal(data, "Wollemi-pine ü 🐝"), "Wollemi-pine ü 🐝"))
    }

    @Test
    fun `a wrong password is found before anything is read`() {
        val sealed = seal(data(3 * segment))
        val e = failure { BackupSeal.open(ByteArrayInputStream(sealed), "bottlebrush") }
        assertTrue(e.wrongPassword)
    }

    @Test
    fun `a backup cut short at a segment's end is refused`() {
        val sealed = seal(data(2 * segment + 100))
        // The last segment, of 100 bytes and its tag, is gone.
        val cut = sealed.copyOf(sealed.size - 116)
        assertFalse(failure { open(cut) }.wrongPassword)
    }

    @Test
    fun `a backup cut short inside a segment is refused`() {
        val sealed = seal(data(2 * segment + 100))
        assertFalse(failure { open(sealed.copyOf(sealed.size - 1)) }.wrongPassword)
        assertFalse(failure { open(sealed.copyOf(header + segment + 16 + 10)) }.wrongPassword)
    }

    @Test
    fun `a small backup cut short is damage, not a wrong password`() {
        // All of it fits in the first and only segment.
        val sealed = seal(data(500))
        assertFalse(failure { open(sealed.copyOf(sealed.size - 20)) }.wrongPassword)
        assertFalse(failure { open(sealed.copyOf(header)) }.wrongPassword)
        sealed[header + 3] = (sealed[header + 3].toInt() xor 1).toByte()
        assertFalse(failure { open(sealed) }.wrongPassword)
    }

    @Test
    fun `a changed byte later in the file is damage, not a wrong password`() {
        val sealed = seal(data(3 * segment))
        sealed[header + 2 * (segment + 16) + 5] = (sealed[header + 2 * (segment + 16) + 5].toInt() xor 1).toByte()
        assertFalse(failure { open(sealed) }.wrongPassword)
    }

    @Test
    fun `segments in another order are refused`() {
        val sealed = seal(data(3 * segment + 1))
        val one = header + (segment + 16)
        val two = one + (segment + 16)
        val swapped = sealed.copyOfRange(0, one) + sealed.copyOfRange(two, two + segment + 16) +
            sealed.copyOfRange(one, two) + sealed.copyOfRange(two + segment + 16, sealed.size)
        assertEquals(sealed.size, swapped.size)
        assertFalse(failure { open(swapped) }.wrongPassword)
    }

    @Test
    fun `bytes added after the end are refused`() {
        val sealed = seal(data(segment + 3))
        assertFalse(failure { open(sealed + byteArrayOf(0)) }.wrongPassword)
        // A whole extra segment's worth from another backup with the same password is refused too.
        val other = seal(data(segment + 3))
        assertFalse(failure { open(sealed + other.copyOfRange(header, other.size)) }.wrongPassword)
    }

    @Test
    fun `a changed header does not open`() {
        val sealed = seal(data(10))
        // The last byte of the nonce prefix, which every segment is authenticated with.
        sealed[35] = (sealed[35].toInt() xor 1).toByte()
        assertFalse(failure { open(sealed) }.wrongPassword)
        // Other rounds make another key, which does not match the password check.
        val rounds = seal(data(10))
        rounds[12] = (rounds[12].toInt() xor 1).toByte()
        assertTrue(failure { open(rounds) }.wrongPassword)
        val check = seal(data(10))
        check[header - 1] = (check[header - 1].toInt() xor 1).toByte()
        assertTrue(failure { open(check) }.wrongPassword)
    }

    @Test
    fun `a file that is not sealed is not opened`() {
        assertFalse(failure { open("PK\u0003\u0004 a zip and more bytes than a header holds".toByteArray()) }.wrongPassword)
        assertFalse(failure { open(ByteArray(10)) }.wrongPassword)
        // Too many rounds would keep the phone busy for minutes.
        val sealed = seal(data(10))
        sealed[9] = 0x7f
        assertFalse(failure { open(sealed) }.wrongPassword)
    }
}
