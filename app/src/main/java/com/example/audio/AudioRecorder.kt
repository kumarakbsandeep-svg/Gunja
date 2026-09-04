package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class AudioRecorder(
    private val onAudioChunk: (ByteArray) -> Unit,
    private val onAmplitudeChanged: (Float) -> Unit
) {
    private val tag = "AudioRecorder"
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = maxOf(
        AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat),
        2048
    )

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRecording = false

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope) {
        if (isRecording) return

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(tag, "AudioRecord initialization failed")
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(2048)
                val shortBuffer = ShortArray(1024)

                while (isActive && isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        val validBytes = buffer.copyOf(read)
                        onAudioChunk(validBytes)

                        // Calculate RMS amplitude for visualizer
                        ByteBuffer.wrap(validBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortBuffer, 0, read / 2)
                        var sum = 0.0
                        for (i in 0 until read / 2) {
                            val sample = shortBuffer[i].toDouble()
                            sum += sample * sample
                        }
                        val rms = sqrt(sum / (read / 2))
                        val normalized = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
                        onAmplitudeChanged(normalized)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to start AudioRecord", e)
            stop()
        }
    }

    fun stop() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(tag, "Error stopping AudioRecord", e)
        }
        audioRecord = null
        onAmplitudeChanged(0f)
    }

    fun isRecordingActive(): Boolean = isRecording
}
