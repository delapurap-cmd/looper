package com.cami.neonloop.audio

/**
 * Cierra el loop de forma musical:
 * 1. El audio ya viene SIN silencio al inicio ni al final (AudioTrimmer).
 * 2. Redondea el largo al múltiplo de beat MÁS CERCANO (no hacia arriba):
 *    - 3.8 beats → 4 beats (rellena 0.2 de silencio: el decay termina natural)
 *    - 4.3 beats → 4 beats (corta 0.3: era sobra de la toma)
 * Así el loop siempre cae en el ritmo, sin hueco al final.
 */
object Quantizer {

    fun quantizeLoop(
        audio: ShortArray,
        bpm: Float,
        sampleRate: Int = AudioEngine.SAMPLE_RATE
    ): ShortArray {
        if (bpm <= 0f || audio.isEmpty()) return audio
        val samplesPerBeat = sampleRate * 60f / bpm

        // Redondeo al beat más cercano (mínimo 1 beat)
        val beats = kotlin.math.round(audio.size / samplesPerBeat).coerceAtLeast(1f)
        val targetLen = (beats * samplesPerBeat).toInt().coerceAtLeast(1)

        val out = ShortArray(targetLen)
        val copyLen = kotlin.math.min(audio.size, targetLen)
        audio.copyInto(out, destinationOffset = 0, startIndex = 0, endIndex = copyLen)
        applyFades(out, copyLen)
        return out
    }

    /** Fades anti-click: inicio del loop y final del CONTENIDO (no del buffer). */
    private fun applyFades(buf: ShortArray, contentLen: Int, ms: Int = 8) {
        val n = (AudioEngine.SAMPLE_RATE * ms / 1000).coerceAtMost(contentLen / 2)
        if (n <= 0) return
        for (i in 0 until n) {
            val g = i.toFloat() / n
            buf[i] = (buf[i] * g).toInt().toShort()
            val tail = contentLen - 1 - i
            if (tail in buf.indices) buf[tail] = (buf[tail] * g).toInt().toShort()
        }
    }
}
