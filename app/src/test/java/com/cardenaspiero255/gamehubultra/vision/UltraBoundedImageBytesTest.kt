package com.cardenaspiero255.gamehubultra.vision

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class UltraBoundedImageBytesTest {
    @Test fun validUnknownLengthProviderStreamIsAcceptedWithoutDeclaredSize() {
        val bytes = ByteArray(87) { it.toByte() }
        val actual = UltraBoundedImageBytes.read(ByteArrayInputStream(bytes), limit = 128)
        assertContentEquals(bytes, actual)
    }

    @Test fun oversizedUnknownLengthProviderStreamFailsClosed() {
        val stream = ByteArrayInputStream(ByteArray(129) { 1 })
        assertFailsWith<IllegalArgumentException> {
            UltraBoundedImageBytes.read(stream, limit = 128)
        }
    }

    @Test fun emptyOrUnreasonableBoundsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            UltraBoundedImageBytes.read(ByteArrayInputStream(byteArrayOf()), limit = 128)
        }
        assertFailsWith<IllegalArgumentException> {
            UltraBoundedImageBytes.read(ByteArrayInputStream(byteArrayOf(1)), limit = 0)
        }
    }

    @Test fun oneByteChunksCannotBypassTheLimit() {
        val slow = object : InputStream() {
            private var emitted = 0
            override fun read(): Int = if (emitted++ < 11) 42 else -1
            override fun read(bytes: ByteArray, off: Int, len: Int): Int {
                val b = read()
                if (b < 0) return -1
                bytes[off] = b.toByte()
                return 1
            }
        }
        assertFailsWith<IllegalArgumentException> {
            UltraBoundedImageBytes.read(slow, limit = 10)
        }
    }
}
