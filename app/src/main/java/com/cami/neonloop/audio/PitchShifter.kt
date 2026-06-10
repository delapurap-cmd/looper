package com.cami.neonloop.audio

/**
 * Pitch shift y time-stretch sencillos (sin librerías externas).
 * - resample(): cambia pitch Y velocidad (rápido, base de todo)
 * - stretch(): cambia velocidad SIN cambiar pitch (OLA con crossfade)
 * - pitchShift(): cambia pitch SIN cambiar velocidad (resample + stretch inverso)
 *
 * Calidad: buena para loops musicales. Si después quieres calidad pro,
 * la GUIA_IA.md explica cómo cambiar a SoundTouch (JNI).
 */
object PitchShifter {

    /** Interpolación lineal. ratio > 1 = más agudo y más corto. */
    fun resample(input: ShortArray, ratio: Float): ShortArray {
        if (ratio == 1f) return input
        val outLen = (input.size / ratio).toInt().coerceAtLeast(1)
        val out = ShortArray(outLen)
        for (i in 0 until outLen) {
            val pos = i * ratio
            val i0 = pos.toInt().coerceAtMost(input.size - 1)
            val i1 = (i0 + 1).coerceAtMost(input.size - 1)
            val frac = pos - i0
            out[i] = (input[i0] * (1 - frac) + input[i1] * frac).toInt().toShort()
        }
        return out
    }

    /**
     * Time-stretch OLA: cambia duración sin cambiar pitch.
     * factor > 1 = más largo. Ventanas de 50ms con 50% de solape.
     */
    fun stretch(input: ShortArray, factor: Float): ShortArray {
        if (factor == 1f || input.isEmpty()) return input
        val win = (AudioEngine.SAMPLE_RATE * 0.05f).toInt()  // 50ms
        val hopIn = win / 2
        val hopOut = (hopIn * factor).toInt().coerceAtLeast(1)
        val outLen = (input.size * factor).toInt()
        val acc = FloatArray(outLen + win)
        val norm = FloatArray(outLen + win)

        var inPos = 0; var outPos = 0
        while (inPos + win <= input.size && outPos + win <= acc.size) {
            for (j in 0 until win) {
                // ventana Hann
                val w = 0.5f - 0.5f * kotlin.math.cos(2f * Math.PI.toFloat() * j / win)
                acc[outPos + j] += input[inPos + j] * w
                norm[outPos + j] += w
            }
            inPos += hopIn
            outPos += hopOut
        }
        val out = ShortArray(outLen)
        for (i in 0 until outLen) {
            val v = if (norm[i] > 0.001f) acc[i] / norm[i] else 0f
            out[i] = v.toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    /** Cambia pitch en semitonos manteniendo la duración. */
    fun pitchShift(input: ShortArray, semitones: Float): ShortArray {
        if (semitones == 0f) return input
        val ratio = Math.pow(2.0, semitones / 12.0).toFloat()
        val resampled = resample(input, ratio)          // pitch ok, duración mal
        return stretch(resampled, ratio)                 // corrige duración
    }

    /** Warp: ajusta un loop de bpmOrigen a bpmDestino sin cambiar pitch. */
    fun warpToBpm(input: ShortArray, fromBpm: Float, toBpm: Float): ShortArray {
        if (fromBpm <= 0f || toBpm <= 0f || fromBpm == toBpm) return input
        return stretch(input, fromBpm / toBpm)
    }
}
