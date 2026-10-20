package com.cardenaspiero255.gamehubultra.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.os.CancellationSignal
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** User-approved, in-process text recognition; no network request or persistence. */
internal object UltraVisualOcr {
    private const val MAX_SIDE = 4096
    // Source + orientation copy stay within roughly 20 MiB for ARGB_8888.
    private const val MAX_DECODED_PIXELS = 2_500_000L
    private const val MAX_DECODED_SIDE = 2048
    // Avoid tying recognizer completion to an Activity/main-looper lifetime.
    private val callbackExecutor = Executor { task -> task.run() }

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
        recognizeOverride: (suspend (android.graphics.Bitmap) -> String)? = null,
        // Internal test seam: production still resolves the real on-device ML Kit client.
        recognizerFactory: () -> TextRecognizer = {
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }
    ): String {
        control.ensureActive()
        require(uri.scheme == "content" && !uri.authority.isNullOrBlank()) {
            "A system-selected image is required"
        }
        val resolver = context.contentResolver
        // Do not invoke ContentResolver.getType(uri): some remote providers
        // block synchronously and do not accept an Android CancellationSignal.
        // The bounded file read, binary signature and bitmap decoder below
        // validate the actual image payload instead of provider metadata.
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
        // Decode downsampled instead of allocating the full potentially
        // 64-MiB bitmap before rotating it into another 64-MiB copy.
        var sample = 1
        while (bounds.outWidth.toLong() / sample *
                (bounds.outHeight.toLong() / sample) > MAX_DECODED_PIXELS ||
            maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > MAX_DECODED_SIDE
        ) sample *= 2
        control.ensureActive()
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(
            imageBytes, 0, imageBytes.size, decodeOptions
        ) ?: throw IllegalArgumentException("The selected data is not a valid image")
        require(bitmap.width.toLong() * bitmap.height <= MAX_DECODED_PIXELS &&
            maxOf(bitmap.width, bitmap.height) <= MAX_DECODED_SIDE) {
            "Image exceeds the local OCR pixel budget"
        }
        control.ensureActive()
        // Normalize the EXIF orientation of JPEG/HEIF photographs before both
        // hermetic OCR and the ML Kit path. The image data never leaves memory.
        val oriented = normalizeBitmapOrientation(bitmap, exifOrientation(imageBytes))
        control.ensureActive()
        if (recognizeOverride != null) {
            // Hermetic tests exercise ContentResolver, BitmapFactory and EXIF
            // without bootstrapping the Google ML Kit singleton in Robolectric.
            val text = recognizeOverride(oriented)
            control.ensureActive()
            return text.take(1000)
        }
        val image = InputImage.fromBitmap(oriented, 0)
        return suspendCancellableCoroutine { continuation ->
            val recognizer = recognizerFactory()
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
                    .addOnSuccessListener(callbackExecutor) { result ->
                        if (continuation.isActive) continuation.resume(result.text.take(1000))
                        releaseRecognizer()
                    }
                    .addOnFailureListener(callbackExecutor) { error ->
                        if (continuation.isActive) continuation.resumeWithException(error)
                        releaseRecognizer()
                    }
                    .addOnCanceledListener(callbackExecutor) {
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

    private fun exifOrientation(data: ByteArray): Int {
        // Android ExifInterface reads orientation from JPEG, HEIF/AVIF,
        // PNG eXIf and WebP EXIF chunks. Do not assume decoded PNG/WebP pixels
        // are upright; the entire stream is already bounded to 8 MiB.
        return runCatching {
            ByteArrayInputStream(data).use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    }

    /**
     * Explicit pixel mapping makes orientation deterministic in Android and
     * Robolectric alike (the bitmap Matrix shadow can silently drop pixels).
     * One scratch row, one output bitmap; never buffer a second full image.
     */
    internal fun normalizeBitmapOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val swapAxes = orientation in listOf(
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_TRANSVERSE,
            ExifInterface.ORIENTATION_ROTATE_270
        )
        if (orientation !in ExifInterface.ORIENTATION_FLIP_HORIZONTAL..
            ExifInterface.ORIENTATION_ROTATE_270) return bitmap
        val width = bitmap.width
        val height = bitmap.height
        val outWidth = if (swapAxes) height else width
        val outHeight = if (swapAxes) width else height
        val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val row = IntArray(width)
        for (y in 0 until height) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1)
            val reverseX = orientation in listOf(
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL,
                ExifInterface.ORIENTATION_ROTATE_180,
                ExifInterface.ORIENTATION_TRANSVERSE,
                ExifInterface.ORIENTATION_ROTATE_270
            )
            if (reverseX) row.reverse()
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL ->
                    output.setPixels(row, 0, width, 0, y, width, 1)
                ExifInterface.ORIENTATION_ROTATE_180,
                ExifInterface.ORIENTATION_FLIP_VERTICAL ->
                    output.setPixels(row, 0, width, 0, height - 1 - y, width, 1)
                ExifInterface.ORIENTATION_TRANSPOSE,
                ExifInterface.ORIENTATION_ROTATE_270 ->
                    output.setPixels(row, 0, 1, y, 0, 1, width)
                ExifInterface.ORIENTATION_ROTATE_90,
                ExifInterface.ORIENTATION_TRANSVERSE ->
                    output.setPixels(row, 0, 1, height - 1 - y, 0, 1, width)
            }
        }
        return output
    }

}
