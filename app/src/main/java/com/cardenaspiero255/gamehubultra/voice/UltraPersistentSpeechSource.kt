package com.cardenaspiero255.gamehubultra.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps one microphone capture open and feeds PCM audio into SpeechRecognizer
 * through EXTRA_AUDIO_SOURCE on Android 13+.
 *
 * The service checks RECORD_AUDIO before creating this source.
 */
internal class UltraPersistentSpeechSource private constructor(
    val readDescriptor: ParcelFileDescriptor,
    private val writeDescriptor: ParcelFileDescriptor,
    private val audioRecord: AudioRecord,
    private val worker: ExecutorService,
    private val bufferSize: Int
) : Closeable {
    companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val CHANNEL_COUNT = 1
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        @SuppressLint("MissingPermission")
        fun create(): UltraPersistentSpeechSource? {
            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                ENCODING
            )
            if (minBuffer <= 0) return null

            val pipe = runCatching { ParcelFileDescriptor.createPipe() }.getOrNull()
                ?: return null
            val read = pipe[0]
            val write = pipe[1]

            val recorder = runCatching {
                AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(ENCODING)
                            .setSampleRate(SAMPLE_RATE_HZ)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minBuffer * 2, 8_192))
                    .build()
            }.getOrElse {
                runCatching { read.close() }
                runCatching { write.close() }
                return null
            }

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                runCatching { read.close() }
                runCatching { write.close() }
                return null
            }

            return UltraPersistentSpeechSource(
                readDescriptor = read,
                writeDescriptor = write,
                audioRecord = recorder,
                worker = Executors.newSingleThreadExecutor(),
                bufferSize = maxOf(minBuffer, 4_096)
            )
        }
    }

    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    fun start(): Boolean {
        if (closed.get()) return false
        if (!started.compareAndSet(false, true)) return true

        val recordingStarted = runCatching {
            audioRecord.startRecording()
            audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING
        }.getOrDefault(false)

        if (!recordingStarted) {
            close()
            return false
        }

        worker.execute {
            val buffer = ByteArray(bufferSize)
            val output = ParcelFileDescriptor.AutoCloseOutputStream(writeDescriptor)
            try {
                while (!closed.get()) {
                    val read = audioRecord.read(
                        buffer,
                        0,
                        buffer.size,
                        AudioRecord.READ_BLOCKING
                    )
                    if (read > 0) {
                        output.write(buffer, 0, read)
                        output.flush()
                    } else if (read < 0) {
                        break
                    }
                }
            } catch (_: Exception) {
                // Closing the pipe/recorder intentionally interrupts this loop.
            } finally {
                runCatching { output.close() }
            }
        }
        return true
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching {
            if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.stop()
            }
        }
        runCatching { writeDescriptor.close() }
        runCatching { readDescriptor.close() }
        worker.shutdownNow()
        runCatching { worker.awaitTermination(500, TimeUnit.MILLISECONDS) }
        runCatching { audioRecord.release() }
    }
}
