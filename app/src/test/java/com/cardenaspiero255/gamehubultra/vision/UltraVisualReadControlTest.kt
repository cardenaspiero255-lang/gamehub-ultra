package com.cardenaspiero255.gamehubultra.vision

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UltraVisualReadControlTest {
    @Test fun cancellationClosesBlockedProviderAndRejectsFurtherReads() {
        val entered = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val stopped = AtomicBoolean(false)
        val stream = object : InputStream() {
            override fun read(): Int { entered.countDown(); closed.await(2, TimeUnit.SECONDS); throw IOException("closed") }
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = read()
            override fun close() { closed.countDown() }
        }
        val control = UltraVisualReadControl()
        val reader = thread(start = true) {
            try {
                UltraBoundedImageBytes.read(control.attach(stream), activeCheck = control::ensureActive)
            } catch (_: IOException) {
                stopped.set(true)
            } catch (_: CancellationException) {
                stopped.set(true)
            }
        }
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        control.revoke()
        reader.join(2_000)
        assertFalse(reader.isAlive)
        assertTrue(stopped.get())
        assertTrue(control.isRevoked)
        assertFailsWith<CancellationException> { control.ensureActive() }
    }

    @Test fun revokingBeforeProviderRegistrationClosesLateStream() {
        val control = UltraVisualReadControl()
        control.revoke()
        var didClose = false
        val stream = object : ByteArrayInputStream(byteArrayOf(4)) {
            override fun close() {
                didClose = true
                super.close()
            }
        }
        assertFailsWith<CancellationException> { control.attach(stream) }
        assertTrue(didClose)
    }

    @Test fun repeatedRevocationIsIdempotentAndDoesNotCloseUnrelatedStream() {
        val control = UltraVisualReadControl()
        val stream = ByteArrayInputStream(byteArrayOf(1, 2))
        control.attach(stream)
        control.release(stream)
        control.revoke()
        control.revoke()
        assertTrue(control.isRevoked)
        assertEquals(1, stream.read())
    }
}
