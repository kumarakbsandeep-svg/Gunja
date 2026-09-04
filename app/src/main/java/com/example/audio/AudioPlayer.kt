package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue
import kotlin.math.sqrt

class AudioPlayer(
    private val sampleRate: Int = 24000,
    private val onAmplitudeChanged: (Float) -> Unit = {},
    private val onPlaybackStateChanged: (Boolean) -> Unit = {}
) {
    private val tag = "AudioPlayer"
    private val queue = LinkedBlockingQueue<ByteArray>()
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private var isPlaying = false

    private val minBufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    init {
        initAudioTrack()
    }

    private fun initAudioTrack() {
        try {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            audioTrack = AudioTrack(
                attributes,
                format,
                maxOf(minBufferSize * 2, 4096),
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize AudioTrack", e)
        }
    }

    fun startPlaybackLoop(scope: CoroutineScope) {
        if (playbackJob?.isActive == true) return

        playbackJob = scope.launch(Dispatchers.IO) {
            val shortBuffer = ShortArray(2048)

            while (isActive) {
                val chunk = queue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (chunk != null && chunk.isNotEmpty()) {
                    if (!isPlaying) {
                        isPlaying = true
                        onPlaybackStateChanged(true)
                    }

                    // Compute RMS amplitude for Zoya speaking animation
                    val sampleCount = chunk.size / 2
                    if (sampleCount <= shortBuffer.size) {
                        ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
                            .asShortBuffer().get(shortBuffer, 0, sampleCount)
                        var sum = 0.0
                        for (i in 0 until sampleCount) {
                            val sample = shortBuffer[i].toDouble()
                            sum += sample * sample
                        }
                        val rms = sqrt(sum / sampleCount)
                        val normalized = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
                        onAmplitudeChanged(normalized)
                    }

                    // Ensure track is playing
                    if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        try {
                            audioTrack?.play()
                        } catch (e: Exception) {
                            Log.e(tag, "Failed to resume audioTrack", e)
                        }
                    }

                    audioTrack?.write(chunk, 0, chunk.size)
                } else {
                    if (isPlaying && queue.isEmpty()) {
                        isPlaying = false
                        onPlaybackStateChanged(false)
                        onAmplitudeChanged(0f)
                    }
                }
            }
        }
    }

    fun playAudioChunk(data: ByteArray) {
        if (data.isNotEmpty()) {
            queue.offer(data)
        }
    }

    /**
     * Instantly ceases audio playback on interruption (clears buffers & flushes track).
     */
    fun interrupt() {
        queue.clear()
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(tag, "Failed to flush AudioTrack on interrupt", e)
        }
        isPlaying = false
        onPlaybackStateChanged(false)
        onAmplitudeChanged(0f)
    }

    fun release() {
        interrupt()
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(tag, "Error releasing AudioTrack", e)
        }
        audioTrack = null
    }
}
