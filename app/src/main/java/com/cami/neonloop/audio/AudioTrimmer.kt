package com.cami.neonloop.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Limpia silencio accidental al inicio Y al final de la grabación.
 * - Inicio: el silencio antes del primer golpe.
 * - Final: el silencio entre que el músico suelta el instrumento
 *   y presiona "parar" (puede ser 0.5–2s). Se conserva la cola
 *   natural del decay del instrumento.
 */
object AudioTrimmer {

    fun trimStartToSound(audio: ShortArray): ShortArray {
        if (audio.size < 512) return audio

        val rms = windowRms(audio) ?: return audio
        val noise = noiseFloor(rms)
        val peak = rms.maxOrNull() ?: 0f
        if (peak < 0.015f) return audio

        val startThreshold = max(max(0.025f, noise * 5f), peak * 0.16f)
        val startWindow = rms.indexOfFirst { it >= startThreshold }
        if (startWindow < 0) return audio

        val start = findSampleStart(audio, startWindow * WINDOW, startThreshold * 0.45f)
        return audio.copyOfRange(start.coerceIn(0, audio.lastIndex), audio.size)
    }

    /**
     * Recorta el silencio del final, conservando el decay natural.
     * Umbral más bajo que el de inicio para no cortar colas de
     * notas sostenidas (guitarra, piano, voz).
     */
    fun trimEndToSound(audio: ShortArray): ShortArray {
        if (audio.size < 512) return audio

        val rms = windowRms(audio) ?: return audio
        val noise = noiseFloor(rms)
        val peak = rms.maxOrNull() ?: 0f
        if (peak < 0.015f) return audio

        // Umbral bajo: 1.2% del rango, o 3x el ruido de fondo, o 4% del pico
        val endThreshold = max(max(0.012f, noise * 3f), peak * 0.04f)
        val lastWindow = rms.indexOfLast { it >= endThreshold }
        if (lastWindow < 0) return audio

        // Conserva 120ms extra de release después del último sonido audible
        val releaseSamples = AudioEngine.SAMPLE_RATE * 120 / 1000
        val end = ((lastWindow + 1) * WINDOW + releaseSamples).coerceAtMost(audio.size)
        if (end >= audio.size) return audio
        return audio.copyOfRange(0, end)
    }

    // ---------- helpers ----------
    private const val WINDOW = 256

    private fun windowRms(audio: ShortArray): List<Float>? {
        val rms = ArrayList<Float>()
        var i = 0
        while (i + WINDOW <= audio.size) {
            var sum = 0.0
            for (j in i until i + WINDOW) {
                val s = audio[j] / 32768f
                sum += s * s
            }
            rms.add(sqrt(sum / WINDOW).toFloat())
            i += WINDOW
        }
        return rms.ifEmpty { null }
    }

    private fun noiseFloor(rms: List<Float>): Float {
        val sorted = rms.sorted()
        val quietCount = max(1, sorted.size / 5)
        return sorted.take(quietCount).average().toFloat()
    }

    private fun findSampleStart(audio: ShortArray, around: Int, threshold: Float): Int {
        val from = max(0, around - 1024)
        val amp = (threshold * 32768f).toInt().coerceAtLeast(400)
        for (i in from until audio.size) {
            if (abs(audio[i].toInt()) >= amp) return i
        }
        return around
    }
}
