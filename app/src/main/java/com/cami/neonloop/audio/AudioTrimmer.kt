package com.cami.neonloop.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Limpia silencio accidental antes de grabar.
 * Es una segunda pasada: no depende del detector en vivo.
 */
object AudioTrimmer {
    fun trimStartToSound(audio: ShortArray): ShortArray {
        if (audio.size < 512) return audio

        val window = 256
        val rms = ArrayList<Float>()
        var i = 0
        while (i + window <= audio.size) {
            var sum = 0.0
            for (j in i until i + window) {
                val s = audio[j] / 32768f
                sum += s * s
            }
            rms.add(sqrt(sum / window).toFloat())
            i += window
        }
        if (rms.isEmpty()) return audio

        val sorted = rms.sorted()
        val quietCount = max(1, sorted.size / 5)
        val noise = sorted.take(quietCount).average().toFloat()
        val peak = rms.maxOrNull() ?: 0f
        if (peak < 0.015f) return audio

        val startThreshold = max(max(0.025f, noise * 5f), peak * 0.16f)

        val startWindow = rms.indexOfFirst { it >= startThreshold }
        if (startWindow < 0) return audio

        val start = findSampleStart(audio, startWindow * window, startThreshold * 0.45f)
        return audio.copyOfRange(start.coerceIn(0, audio.lastIndex), audio.size)
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
