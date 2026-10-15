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
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class UltraPersistentSpeechSourcePhase2RobolectricTest {
    private class InlineWorker : AbstractExecutorService() {
        private var done = false
        override fun execute(command: Runnable) = command.run()
        override fun shutdown() { done = true }
        override fun shutdownNow(): MutableList<Runnable> {
            done = true
            return mutableListOf()
        }
        override fun isShutdown(): Boolean = done
        override fun isTerminated(): Boolean = done
        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = done
    }

    @Test
    fun captureWorkerClosesItsPipeOnAudioReadFailureWithoutCrashing() {
        val pipe = ParcelFileDescriptor.createPipe()
        val audioRecord = Mockito.mock(AudioRecord::class.java)
        Mockito.`when`(audioRecord.recordingState).thenReturn(AudioRecord.RECORDSTATE_RECORDING)
        Mockito.`when`(
            audioRecord.read(
                Mockito.any(ByteArray::class.java),
                Mockito.eq(0),
                Mockito.eq(4096),
                Mockito.eq(AudioRecord.READ_BLOCKING)
            )
        ).thenReturn(-1)
        val constructor = UltraPersistentSpeechSource::class.java.getDeclaredConstructor(
            ParcelFileDescriptor::class.java,
            ParcelFileDescriptor::class.java,
            AudioRecord::class.java,
            ExecutorService::class.java,
            Integer.TYPE
        )
        constructor.isAccessible = true
        val source = constructor.newInstance(pipe[0], pipe[1], audioRecord, InlineWorker(), 4096)
        assertTrue(source.start())
        source.close()
        Mockito.verify(audioRecord).release()
    }

    @Test
    fun closeNeverWaitsForCaptureWorkerTerminationOnCallingThread() {
        val pipe = ParcelFileDescriptor.createPipe()
        val recorder = Mockito.mock(AudioRecord::class.java)
        val worker = Mockito.mock(ExecutorService::class.java)
        Mockito.`when`(recorder.recordingState)
            .thenReturn(AudioRecord.RECORDSTATE_RECORDING)
        val constructor = UltraPersistentSpeechSource::class.java.getDeclaredConstructor(
            ParcelFileDescriptor::class.java,
            ParcelFileDescriptor::class.java,
            AudioRecord::class.java,
            ExecutorService::class.java,
            Integer.TYPE
        )
        constructor.isAccessible = true
        val source = constructor.newInstance(pipe[0], pipe[1], recorder, worker, 4096)

        source.close()

        Mockito.verify(worker).shutdownNow()
        Mockito.verify(worker, Mockito.never()).awaitTermination(
            Mockito.anyLong(), Mockito.any(TimeUnit::class.java)
        )
        Mockito.verify(recorder).release()
    }

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
            Integer.TYPE
        )
        constructor.isAccessible = true
        val source = constructor.newInstance(pipe[0], pipe[1], audioRecord, worker, 4096)

        assertFalse(source.start())
        Mockito.verify(audioRecord).release()
        source.close()
    }
}
