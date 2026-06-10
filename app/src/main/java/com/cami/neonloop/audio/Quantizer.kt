package com.cami.neonloop.audio

/**
 * Ajusta el largo de un loop grabado al múltiplo de beat más cercano,
 * cortando desde el primer transiente (sin silencio inicial).
 */
object Quantizer {
    fun quantizeLoop(
        audio: ShortArray,
        bpm: Float,
        onsets: List<Long>,
        sampleRate: Int = AudioEngine.SAMPLE_RATE
    ): ShortArray {
        if (bpm <= 0f || audio.isEmpty()) return audio
        val samplesPerBeat = sampleRate * 60f / bpm
        val tailSamples = sampleRate * 260 / 1000
        val lastOnset = onsets.lastOrNull()?.toInt()?.coerceIn(0, audio.lastIndex)

        val musicalEnd = if (lastOnset != null && onsets.size >= 2) {
            kotlin.math.max(lastOnset + samplesPerBeat.toInt(), lastOnset + tailSamples)
        } else {
            audio.size
        }
        val beats = kotlin.math.ceil(musicalEnd / samplesPerBeat).coerceAtLeast(1f)
        val targetLen = (beats * samplesPerBeat).toInt().coerceAtLeast(1)
        val copyLen = kotlin.math.min(audio.size, targetLen)
        val out = ShortArray(targetLen)
        audio.copyInto(out, destinationOffset = 0, startIndex = 0, endIndex = copyLen)
        applyFades(out)
        return out
    }

    fun quantize(
        audio: ShortArray,
        startOnset: Long,
        bpm: Float,
        sampleRate: Int = AudioEngine.SAMPLE_RATE
    ): ShortArray {
        if (bpm <= 0f) return audio
        val samplesPerBeat = (sampleRate * 60f / bpm)
        val start = startOnset.toInt().coerceIn(0, audio.size - 1)
        val rawLen = audio.size - start

        // Cierra hacia el siguiente beat: nunca corta el final de la toma.
        val beats = kotlin.math.ceil(rawLen / samplesPerBeat).coerceAtLeast(1f)
        val targetLen = (beats * samplesPerBeat).toInt().coerceAtLeast(1)
        val available = rawLen.coerceAtLeast(0)
        val out = ShortArray(targetLen)
        if (available > 0) {
            val copyLen = kotlin.math.min(available, targetLen)
            audio.copyInto(out, destinationOffset = 0, startIndex = start, endIndex = start + copyLen)
        }
        applyFades(out)
        return out
    }

    /** Fade in/out de 5ms para evitar clicks en el punto de loop. */
    private fun applyFades(buf: ShortArray, ms: Int = 5) {
        val n = (AudioEngine.SAMPLE_RATE * ms / 1000).coerceAtMost(buf.size / 2)
        for (i in 0 until n) {
            val g = i.toFloat() / n
            buf[i] = (buf[i] * g).toInt().toShort()
            buf[buf.size - 1 - i] = (buf[buf.size - 1 - i] * g).toInt().toShort()
        }
    }
}
