package com.cardenaspiero255.gamehubultra.vision

import android.content.ContentProvider
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import org.mockito.Mockito
import java.util.concurrent.CancellationException
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.content.res.AssetFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Exercises true ContentResolver/BMP decoding; substitutes OCR inference only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UltraVisualOcrRobolectricTest {
    private class ImageProvider(
        var bytes: ByteArray,
        var mime: String? = "image/png",
        var readable: Boolean = true
    ) : ContentProvider() {
        override fun onCreate(): Boolean = true
        override fun query(uri: Uri, projection: Array<String>?, selection: String?,
            selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null
        var typeLookups = 0
        override fun getType(uri: Uri): String? {
            typeLookups++
            return mime
        }
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?,
            selectionArgs: Array<String>?): Int = 0

        override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
            if (!readable) return null
            val temp = File.createTempFile("phase7-ocr-", ".png", context!!.cacheDir)
            temp.writeBytes(bytes)
            temp.deleteOnExit()
            return AssetFileDescriptor(
                ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY),
                0, AssetFileDescriptor.UNKNOWN_LENGTH
            )
        }
    }

    private fun register(provider: ImageProvider): Uri {
        val authority = "phase7.ocr.test"
        val app = RuntimeEnvironment.getApplication()
        provider.attachInfo(app, ProviderInfo().apply { this.authority = authority; exported = false })
        ShadowContentResolver.registerProviderInternal(authority, provider)
        return Uri.parse("content://$authority/selected.png")
    }

    private fun png(width: Int = 4, height: Int = 4): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private fun pngWithExifOrientation(orientation: Int): ByteArray {
        val original = png(3, 2)
        // Standard PNG eXIf chunk: TIFF little-endian IFD0 Orientation 0x0112.
        val tiff = byteArrayOf(
            0x49, 0x49, 0x2a, 0, 8, 0, 0, 0,
            1, 0, 0x12, 1, 3, 0, 1, 0, 0, 0,
            orientation.toByte(), 0, 0, 0, 0, 0, 0, 0
        )
        val type = "eXIf".toByteArray(Charsets.US_ASCII)
        val ihdrLength = java.nio.ByteBuffer.wrap(original, 8, 4).int
        val insertAt = 8 + 12 + ihdrLength
        val out = ByteArrayOutputStream()
        out.write(original, 0, insertAt)
        DataOutputStream(out).apply {
            writeInt(tiff.size)
            write(type)
            write(tiff)
            val crc = CRC32()
            crc.update(type)
            crc.update(tiff)
            writeInt(crc.value.toInt())
        }
        out.write(original, insertAt, original.size - insertAt)
        return out.toByteArray()
    }

    @Test fun pngEXIFOrientationIsHonoredBeforeLocalOCR() = runBlocking {
        val uri = register(ImageProvider(pngWithExifOrientation(
            ExifInterface.ORIENTATION_ROTATE_90
        )))
        var width = -1
        var height = -1
        val text = UltraVisualOcr.readApprovedImage(
            RuntimeEnvironment.getApplication(), uri,
            UltraVisualReadControl(), CancellationSignal(),
            recognizeOverride = { image ->
                width = image.width
                height = image.height
                "TEXTO LOCAL"
            }
        )
        assertEquals("TEXTO LOCAL", text)
        assertEquals(2, width)
        assertEquals(3, height)
    }

    @Test fun EXIFRotation90NormalizesBitmapDimensionsAndCornerPixels() {
        val source = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)
        source.setPixel(0, 0, 0xff123456.toInt())
        source.setPixel(2, 1, 0xffabcdef.toInt())
        val oriented = UltraVisualOcr.normalizeBitmapOrientation(
            source, ExifInterface.ORIENTATION_ROTATE_90
        )
        assertEquals(2, oriented.width)
        assertEquals(3, oriented.height)
        assertEquals(0xff123456.toInt(), oriented.getPixel(1, 0))
        assertEquals(0xffabcdef.toInt(), oriented.getPixel(0, 2))
    }

    @Test fun EXIFRotation180And270AreAppliedBeforeRecognition() {
        val source = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)
        val rotated180 = UltraVisualOcr.normalizeBitmapOrientation(
            source, ExifInterface.ORIENTATION_ROTATE_180
        )
        assertEquals(3, rotated180.width)
        assertEquals(2, rotated180.height)
        val rotated270 = UltraVisualOcr.normalizeBitmapOrientation(
            source, ExifInterface.ORIENTATION_ROTATE_270
        )
        assertEquals(2, rotated270.width)
        assertEquals(3, rotated270.height)
        assertTrue(UltraVisualOcr.normalizeBitmapOrientation(
            source, ExifInterface.ORIENTATION_NORMAL
        ) === source)
    }

    @Test fun EXIFFlippedAndTransposedImagesPreserveExactPixelCount() {
        val source = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)
        for (orientation in listOf(
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL,
            ExifInterface.ORIENTATION_FLIP_VERTICAL,
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_TRANSVERSE
        )) {
            val oriented = UltraVisualOcr.normalizeBitmapOrientation(source, orientation)
            assertEquals(8, oriented.width * oriented.height)
        }
    }

    @Test fun approvedContentProviderWithUnknownLengthCanBeRecognizedLocally() = runBlocking {
        val uri = register(ImageProvider(png()))
        var recognizedInput = false
        val result = UltraVisualOcr.readApprovedImage(
            RuntimeEnvironment.getApplication(), uri, UltraVisualReadControl(),
            CancellationSignal(),
            recognizeOverride = { image: Bitmap ->
                recognizedInput = image.width == 4 && image.height == 4
                "RESULTADO SOLO LOCAL"
            }
        )
        assertTrue(recognizedInput)
        assertEquals("RESULTADO SOLO LOCAL", result)
    }

    @Test fun invalidOrNonImageSchemeIsRejectedBeforeProviderAccess() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            UltraVisualOcr.readApprovedImage(
                RuntimeEnvironment.getApplication(), Uri.parse("https://evil.invalid/photo"),
                UltraVisualReadControl(), CancellationSignal(),
                recognizeOverride = { "NEVER" }
            )
        }
        // A lying text/html MIME is not sufficient to classify the pixels.
        // Reject truly non-image bytes through the bounded signature/decoder.
        val uri = register(ImageProvider(ByteArray(64) { 0x42 }, mime = "text/html"))
        assertFailsWith<IllegalArgumentException> {
            UltraVisualOcr.readApprovedImage(
                RuntimeEnvironment.getApplication(), uri,
                UltraVisualReadControl(), CancellationSignal(), recognizeOverride = { "NEVER" }
            )
        }
        Unit
    }

    @Test fun validPickerImageCanBeDecodedWithoutBlockingOnUntrustedMimeLookup() = runBlocking {
        val provider = ImageProvider(png(), mime = "text/plain")
        val uri = register(provider)
        val text = UltraVisualOcr.readApprovedImage(
            RuntimeEnvironment.getApplication(), uri,
            UltraVisualReadControl(), CancellationSignal(),
            recognizeOverride = { "INFERENCIA LOCAL" }
        )
        assertEquals("INFERENCIA LOCAL", text)
        assertEquals(0, provider.typeLookups, "No synchronous ContentResolver.getType before provider cancellation")
    }

    @Test fun invalidImagePixelsCannotReachRecognizer() = runBlocking {
        val uri = register(ImageProvider(byteArrayOf(1, 2, 3)))
        var invoked = false
        assertFailsWith<IllegalArgumentException> {
            UltraVisualOcr.readApprovedImage(
                RuntimeEnvironment.getApplication(), uri, UltraVisualReadControl(),
                CancellationSignal(), recognizeOverride = { invoked = true; "NEVER" }
            )
        }
        assertTrue(!invoked)
    }

    @Test fun spoofedImageMimeWithMalformedHeaderCannotReachDecoderOrMlKit() = runBlocking {
        // Robolectric BitmapFactory may synthesize bitmap dimensions for bytes
        // that the real Android decoder rejects. The production header gate
        // must reject this provider before any ML Kit singleton is accessed.
        val uri = register(ImageProvider(ByteArray(128) { 0x42 }))
        var invoked = false
        assertFailsWith<IllegalArgumentException> {
            UltraVisualOcr.readApprovedImage(
                RuntimeEnvironment.getApplication(), uri, UltraVisualReadControl(),
                CancellationSignal(), recognizeOverride = { invoked = true; "NEVER" }
            )
        }
        assertTrue(!invoked)
    }

    @Test fun revokedConsentBeforeReadingCannotInvokeProvider() = runBlocking {
        val uri = register(ImageProvider(png()))
        val control = UltraVisualReadControl()
        control.revoke()
        assertFailsWith<java.util.concurrent.CancellationException> {
            UltraVisualOcr.readApprovedImage(
                RuntimeEnvironment.getApplication(), uri, control,
                CancellationSignal(), recognizeOverride = { "NEVER" }
            )
        }
        Unit
    }

    @Test fun contentProviderCanRejectUnavailableFileWithoutAccidentalCertification() = runBlocking {
        val uri = register(ImageProvider(png(), readable = false))
        assertFailsWith<IllegalArgumentException> {
            UltraVisualOcr.readApprovedImage(
                RuntimeEnvironment.getApplication(), uri,
                UltraVisualReadControl(), CancellationSignal(), recognizeOverride = { "NEVER" }
            )
        }
        Unit
    }

    @Test fun largeAllowedImageIsDownsampledBeforeOCRToStayWithinMemoryBudget() = runBlocking {
        // 2,600 x 1,200 = 3.12MP: the size is permitted by the input
        // dimensions but would exceed the private OCR bitmap budget.
        // Robolectric's Bitmap.compress may emit >8 MiB even for a flat image.
        // Use a genuine tightly compressed PNG so this test isolates the
        // decoded-pixel budget instead of correctly tripping the byte budget.
        val compactPng = ByteArrayOutputStream().use { output ->
            // Produce an actual compressed RGBA PNG without java.awt/javax.imageio:
            // Android JVM test compilation does not expose desktop Java AWT APIs.
            val width = 2600
            val height = 1200
            val scanlines = ByteArray((width * 4 + 1) * height)
            val compressed = ByteArrayOutputStream().use { buffer ->
                java.util.zip.DeflaterOutputStream(buffer).use { it.write(scanlines) }
                buffer.toByteArray()
            }
            fun chunk(kind: String, bytes: ByteArray) {
                val kindBytes = kind.toByteArray(Charsets.US_ASCII)
                val crc = CRC32().apply { update(kindBytes); update(bytes) }
                DataOutputStream(output).apply {
                    writeInt(bytes.size)
                    write(kindBytes)
                    write(bytes)
                    writeInt(crc.value.toInt())
                }
            }
            val header = ByteArrayOutputStream().use { buffer ->
                DataOutputStream(buffer).use { data ->
                    data.writeInt(width)
                    data.writeInt(height)
                    data.writeByte(8) // Bit depth
                    data.writeByte(6) // RGBA
                    data.writeByte(0) // Compression
                    data.writeByte(0) // Filter
                    data.writeByte(0) // Interlace
                }
                buffer.toByteArray()
            }
            output.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
            chunk("IHDR", header)
            chunk("IDAT", compressed)
            chunk("IEND", byteArrayOf())
            output.toByteArray()
        }
        assertTrue(compactPng.size < 8 * 1024 * 1024)
        val uri = register(ImageProvider(compactPng))
        var analyzedPixels = Long.MAX_VALUE
        val text = UltraVisualOcr.readApprovedImage(
            RuntimeEnvironment.getApplication(), uri,
            UltraVisualReadControl(), CancellationSignal(),
            recognizeOverride = { bitmap ->
                analyzedPixels = bitmap.width.toLong() * bitmap.height
                "OCR LOCAL"
            }
        )
        assertEquals("OCR LOCAL", text)
        assertTrue(analyzedPixels in 1..2_500_000L)
    }


    private fun fakeRecognizer(task: com.google.android.gms.tasks.Task<Text>): TextRecognizer {
        val client = Mockito.mock(TextRecognizer::class.java)
        Mockito.`when`(client.process(Mockito.any(InputImage::class.java))).thenReturn(task)
        return client
    }

    @Test fun mlKitSuccessCapsOutputAndClosesRecognizerExactlyOnce() = runBlocking {
        val recognition = Mockito.mock(Text::class.java)
        Mockito.`when`(recognition.text).thenReturn("A".repeat(1100))
        val client = fakeRecognizer(Tasks.forResult(recognition))
        val uri = register(ImageProvider(png()))
        val text = UltraVisualOcr.readApprovedImage(
            RuntimeEnvironment.getApplication(), uri,
            UltraVisualReadControl(), CancellationSignal(),
            recognizerFactory = { client }
        )
        assertEquals("A".repeat(1000), text)
        Mockito.verify(client, Mockito.times(1)).close()
    }

    @Test fun mlKitAsyncFailurePropagatesAndClosesRecognizerExactlyOnce() = runBlocking {
        val client = fakeRecognizer(
            Tasks.forException<Text>(IllegalStateException("OCR inference failed"))
        )
        val uri = register(ImageProvider(png()))
        assertFailsWith<IllegalStateException> {
            UltraVisualOcr.readApprovedImage(
                RuntimeEnvironment.getApplication(), uri,
                UltraVisualReadControl(), CancellationSignal(),
                recognizerFactory = { client }
            )
        }
        Mockito.verify(client, Mockito.times(1)).close()
    }

    @Test fun mlKitCancelledTaskRevokesConsentAndClosesRecognizerExactlyOnce() = runBlocking {
        val client = fakeRecognizer(Tasks.forCanceled<Text>())
        val control = UltraVisualReadControl()
        val uri = register(ImageProvider(png()))
        assertFailsWith<CancellationException> {
            UltraVisualOcr.readApprovedImage(
                RuntimeEnvironment.getApplication(), uri,
                control, CancellationSignal(),
                recognizerFactory = { client }
            )
        }
        assertFailsWith<CancellationException> { control.ensureActive() }
        Mockito.verify(client, Mockito.times(1)).close()
    }

    @Test fun mlKitSynchronousFailureAlsoClosesRecognizerExactlyOnce() = runBlocking {
        val client = Mockito.mock(TextRecognizer::class.java)
        Mockito.`when`(client.process(Mockito.any(InputImage::class.java)))
            .thenThrow(IllegalArgumentException("Synchronous OCR failure"))
        val uri = register(ImageProvider(png()))
        assertFailsWith<IllegalArgumentException> {
            UltraVisualOcr.readApprovedImage(
                RuntimeEnvironment.getApplication(), uri,
                UltraVisualReadControl(), CancellationSignal(),
                recognizerFactory = { client }
            )
        }
        Mockito.verify(client, Mockito.times(1)).close()
    }

    @Test fun oversizedDecodedImageIsRejectedBeforeRecognition() = runBlocking {
        val uri = register(ImageProvider(png(width = 4097, height = 1)))
        assertFailsWith<IllegalArgumentException> {
            UltraVisualOcr.readApprovedImage(
                RuntimeEnvironment.getApplication(), uri,
                UltraVisualReadControl(), CancellationSignal(), recognizeOverride = { "NEVER" }
            )
        }
        Unit
    }
}
