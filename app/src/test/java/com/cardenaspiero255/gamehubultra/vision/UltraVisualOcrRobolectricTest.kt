package com.cardenaspiero255.gamehubultra.vision

import android.content.ContentProvider
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
