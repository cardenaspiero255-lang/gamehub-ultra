package com.cardenaspiero255.gamehubultra.vision

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** User-approved, in-process text recognition; no network request or persistence. */
internal object UltraVisualOcr {
    private const val MAX_BYTES = 8L * 1024L * 1024L
    private const val MAX_SIDE = 4096

    suspend fun readApprovedImage(context: Context, uri: Uri): String {
        require(uri.scheme == "content" && !uri.authority.isNullOrBlank()) {
            "A system-selected image is required"
        }
        val resolver = context.contentResolver
        require(resolver.getType(uri)?.startsWith("image/") == true) {
            "The selected content must be an image"
        }
        val byteCount = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        require(byteCount in 1..MAX_BYTES) {
            "The selected image is too large or has an unknown size"
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val dimensionsRead = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
            true
        } ?: false
        require(dimensionsRead && options.outWidth in 1..MAX_SIDE &&
            options.outHeight in 1..MAX_SIDE
        ) { "The selected image dimensions are not supported" }

        val image = InputImage.fromFilePath(context, uri)
        return suspendCancellableCoroutine { continuation ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val closed = AtomicBoolean(false)
            fun closeOnce() {
                if (closed.compareAndSet(false, true)) recognizer.close()
            }
            continuation.invokeOnCancellation { closeOnce() }
            try {
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        if (continuation.isActive) continuation.resume(result.text.take(1000))
                        closeOnce()
                    }
                    .addOnFailureListener { error ->
                        if (continuation.isActive) continuation.resumeWithException(error)
                        closeOnce()
                    }
                    .addOnCanceledListener {
                        if (continuation.isActive) continuation.cancel()
                        closeOnce()
                    }
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
                closeOnce()
            }
        }
    }
}
