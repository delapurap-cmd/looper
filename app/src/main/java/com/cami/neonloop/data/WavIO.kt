package com.cami.neonloop.data

import com.cami.neonloop.audio.AudioEngine
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Lee y escribe WAV PCM 16-bit. Mono para loops, estéreo para export. */
object WavIO {

    fun write(file: File, samples: ShortArray, channels: Int = 1, sampleRate: Int = AudioEngine.SAMPLE_RATE) {
        file.parentFile?.mkdirs()
        val dataLen = samples.size * 2
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray()); h.putInt(36 + dataLen); h.put("WAVE".toByteArray())
            h.put("fmt ".toByteArray()); h.putInt(16); h.putShort(1); h.putShort(channels.toShort())
            h.putInt(sampleRate); h.putInt(sampleRate * channels * 2)
            h.putShort((channels * 2).toShort()); h.putShort(16)
            h.put("data".toByteArray()); h.putInt(dataLen)
            raf.write(h.array())
            val body = ByteBuffer.allocate(dataLen).order(ByteOrder.LITTLE_ENDIAN)
            samples.forEach { body.putShort(it) }
            raf.write(body.array())
        }
    }

    fun read(file: File): ShortArray {
        val bytes = file.readBytes()
        if (bytes.size <= 44) return ShortArray(0)
        val bb = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN)
        val out = ShortArray((bytes.size - 44) / 2)
        for (i in out.indices) out[i] = bb.short
        return out
    }
}
