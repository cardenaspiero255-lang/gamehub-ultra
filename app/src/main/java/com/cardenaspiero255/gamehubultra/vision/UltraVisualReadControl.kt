package com.cardenaspiero255.gamehubultra.vision

import java.io.Closeable
import java.io.InputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Owned by one authorized OCR operation. revocation closes the underlying
 * provider stream from the UI thread, even while an IO worker is blocked.
 * ContentResolver's CancellationSignal is cancelled separately by the caller.
 */
internal class UltraVisualReadControl(
    private val cancelProvider: () -> Unit = {},
    private val isConsentCurrent: () -> Boolean = { true }
) {
    private val revoked = AtomicBoolean(false)
    private val activeStream = AtomicReference<InputStream?>(null)

    val isRevoked: Boolean get() = revoked.get()

    fun ensureActive() {
        // The UI StateFlow may not have observed a privacy erase yet.
        // Keep the immutable original authorization as the source of truth.
        val consentCurrent = runCatching { isConsentCurrent() }.getOrDefault(false)
        if (revoked.get() || !consentCurrent) {
            revoke()
            throw CancellationException("Image read consent revoked")
        }
    }

    fun attach(stream: InputStream): InputStream {
        if (!activeStream.compareAndSet(null, stream)) {
            closeQuietly(stream)
            throw IllegalStateException("Concurrent provider read not permitted")
        }
        // Attach the stream before the epoch re-check so revocation closes
        // a provider already starting to read.
        try {
            ensureActive()
        } catch (error: CancellationException) {
            activeStream.compareAndSet(stream, null)
            closeQuietly(stream)
            throw error
        }
        return stream
    }

    fun release(stream: InputStream) {
        activeStream.compareAndSet(stream, null)
    }

    fun revoke() {
        if (!revoked.compareAndSet(false, true)) return
        runCatching { cancelProvider() }
        activeStream.getAndSet(null)?.let(::closeQuietly)
    }

    private fun closeQuietly(resource: Closeable) {
        runCatching { resource.close() }
    }
}
