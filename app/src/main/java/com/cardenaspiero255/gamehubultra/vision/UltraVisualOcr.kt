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

    // Validate well-known signatures before bitmap decoding. Keep this gate
    // separate from the reported provider MIME (which is not trustworthy).
    private fun isSupportedBitmapHeader(data: ByteArray): Boolean {
        if (data.size < 16) return false
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        if (data.take(8).toByteArray().contentEquals(png)) return true
        if (data[0] == 0xff.toByte() && data[1] == 0xd8.toByte() &&
            data[2] == 0xff.toByte()) return true // JPEG
        if (matchesAsciiAt(data, 0, "RIFF") && matchesAsciiAt(data, 8, "WEBP")) return true
        if (matchesAsciiAt(data, 0, "GIF87a") || matchesAsciiAt(data, 0, "GIF89a")) return true
        if (matchesAsciiAt(data, 0, "BM")) return true
        // ISO BMFF (HEIF/HEIC/AVIF) brands accepted by Android BitmapFactory.
        if (matchesAsciiAt(data, 4, "ftyp") && listOf("heic", "heix", "hevc", "mif1", "msf1",
                "avif", "avis").any { matchesAsciiAt(data, 8, it) }) return true
        return false
    }

    suspend fun readApprovedImage(
        context: Context,
        uri: Uri,
        control: UltraVisualReadControl,
        signal: CancellationSignal,
        // Local inference seam for hermetic Robolectric tests; production omits it.
        recognizeOverride: (suspend (android.graphics.Bitmap) -> String)? = null
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
        // A truncated or spoofed provider stream must not reach native decoding or ML Kit.
        // Android Photo Picker accepts these platform bitmap formats; MIME alone is
        // insufficient because content providers can return arbitrary bytes.
        require(isSupportedBitmapHeader(imageBytes)) { "Selected image format is invalid" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
        require(bounds.outWidth in 1..MAX_SIDE && bounds.outHeight in 1..MAX_SIDE) {
            "The selected image dimensions are not supported"
        }
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: throw IllegalArgumentException("The selected data is not a valid image")
        control.ensureActive()
        if (recognizeOverride != null) {
            // Hermetic tests exercise the real ContentResolver and BitmapFactory
            // without bootstrapping Google's ML Kit singleton in Robolectric.
            val text = recognizeOverride(bitmap)
            control.ensureActive()
            return text.take(1000)
        }
        val image = InputImage.fromBitmap(bitmap, 0)
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
    // A separate helper (never self-recursive): do not confuse calls to this
    // parser from the previous method with recursion in the security review.
    private fun matchesAsciiAt(data: ByteArray, offset: Int, value: String): Boolean =
        data.size >= offset + value.length &&
            value.indices.all { i -> data[offset + i] == value[i].code.toByte() }

}
