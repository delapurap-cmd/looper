package com.cami.neonloop.audio

import android.annotation.SuppressLint
import android.media.*
import kotlinx.coroutines.*

/**
 * Motor central: graba con AudioRecord y reproduce con AudioTrack.
 * Todo a 44100 Hz, mono, PCM 16-bit (simple y suficiente para loops).
 */
class AudioEngine {
    companion object { const val SAMPLE_RATE = 44100 }

    private var record: AudioRecord? = null
    private var playTrack: AudioTrack? = null
    private var recordJob: Job? = null
    private var playJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Graba hasta que se llame stopRecording(). Devuelve samples vía callback. */
    @SuppressLint("MissingPermission")
    fun startRecording(onChunk: (ShortArray) -> Unit) {
        val bufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        ) * 2
        record = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize
        ).also { it.startRecording() }

        recordJob = scope.launch {
            val buf = ShortArray(1024)
            while (isActive) {
                val n = record?.read(buf, 0, buf.size) ?: break
                if (n > 0) onChunk(buf.copyOf(n))
            }
        }
    }

    fun stopRecording() {
        recordJob?.cancel()
        record?.run { stop(); release() }
        record = null
    }

    /** Reproduce en loop el buffer mezclado que entrega [mixer] frame a frame. */
    fun startPlayback(mixer: Mixer) {
        val bufSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT
        ) * 2
        playTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(bufSize)
            .build().also { it.play() }

        playJob = scope.launch {
            val frames = 1024
            while (isActive) {
                val stereo = mixer.nextStereoChunk(frames) // ShortArray frames*2
                playTrack?.write(stereo, 0, stereo.size)
            }
        }
    }

    fun stopPlayback() {
        playJob?.cancel()
        playTrack?.run { stop(); release() }
        playTrack = null
    }
}
