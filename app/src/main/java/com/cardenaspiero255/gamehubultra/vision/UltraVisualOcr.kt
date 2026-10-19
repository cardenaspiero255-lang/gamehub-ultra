package com.cardenaspiero255.gamehubultra.vision

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.CancellationSignal
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** User-approved, in-process text recognition; no network request or persistence. */
internal object UltraVisualOcr {
    private const val MAX_SIDE = 4096

    suspend fun readApprovedImage(
        context: Context,
        uri: Uri,
        control: UltraVisualReadControl,
        signal: CancellationSignal,
        // Local inference seam for hermetic Robolectric tests; production omits it.
        recognizeOverride: (suspend (InputImage) -> String)? = null
    ): String {
        control.ensureActive()
        require(uri.scheme == "content" && !uri.authority.isNullOrBlank()) {
            "A system-selected image is required"
        }
        val resolver = context.contentResolver
        val declaredType = resolver.getType(uri)
        require(declaredType == null || declaredType.startsWith("image/")) {
            "The selected content must be an image"
        }
        // Photo-picker providers may expose an unknown AssetFileDescriptor length.
        // Read exactly once under a strict limit before decoding the image.
        val descriptor = resolver.openAssetFileDescriptor(uri, "r", signal)
            ?: throw IllegalArgumentException("The selected image is unavailable")
        val imageBytes = descriptor.use { provider ->
            control.ensureActive()
            val input = control.attach(provider.createInputStream())
            try {
                input.use { UltraBoundedImageBytes.read(it, activeCheck = control::ensureActive) }
            } finally {
                control.release(input)
            }
        }
        control.ensureActive()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
        require(bounds.outWidth in 1..MAX_SIDE && bounds.outHeight in 1..MAX_SIDE) {
            "The selected image dimensions are not supported"
        }
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: throw IllegalArgumentException("The selected data is not a valid image")
        control.ensureActive()
        val image = InputImage.fromBitmap(bitmap, 0)
        if (recognizeOverride != null) {
            val text = recognizeOverride(image)
            control.ensureActive()
            return text.take(1000)
        }
        return suspendCancellableCoroutine { continuation ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val closed = AtomicBoolean(false)
            // A non-recursive, idempotent release callback; cancellation
            // and task completion may race, but ML Kit closes exactly once.
            val releaseRecognizer: () -> Unit = {
                if (closed.compareAndSet(false, true)) recognizer.close()
            }
            continuation.invokeOnCancellation {
                control.revoke()
                releaseRecognizer()
            }
            try {
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        if (continuation.isActive) continuation.resume(result.text.take(1000))
                        releaseRecognizer()
                    }
                    .addOnFailureListener { error ->
                        if (continuation.isActive) continuation.resumeWithException(error)
                        releaseRecognizer()
                    }
                    .addOnCanceledListener {
                        if (continuation.isActive) continuation.cancel()
                        releaseRecognizer()
                    }
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
                releaseRecognizer()
            }
        }
    }
}
