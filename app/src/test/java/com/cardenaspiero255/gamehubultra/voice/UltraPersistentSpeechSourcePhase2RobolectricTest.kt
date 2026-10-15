package com.cardenaspiero255.gamehubultra.voice

import android.media.AudioRecord
import android.os.ParcelFileDescriptor
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class UltraPersistentSpeechSourcePhase2RobolectricTest {
    @Test
    fun rejectedCaptureWorkerDoesNotCrashAndClosesAudioRecord() {
        val pipe = ParcelFileDescriptor.createPipe()
        val audioRecord = Mockito.mock(AudioRecord::class.java)
        Mockito.`when`(audioRecord.recordingState).thenReturn(AudioRecord.RECORDSTATE_RECORDING)
        val worker: ExecutorService = Executors.newSingleThreadExecutor().also { it.shutdownNow() }
        val constructor = UltraPersistentSpeechSource::class.java.getDeclaredConstructor(
            ParcelFileDescriptor::class.java,
            ParcelFileDescriptor::class.java,
            AudioRecord::class.java,
            ExecutorService::class.java,
            Int::class.javaPrimitiveType
        )
        constructor.isAccessible = true
        val source = constructor.newInstance(pipe[0], pipe[1], audioRecord, worker, 4096)

        assertFalse(source.start())
        Mockito.verify(audioRecord).release()
        source.close()
    }
}
