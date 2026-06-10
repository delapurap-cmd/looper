package com.cami.neonloop.audio

/**
 * Estima BPM analizando los intervalos entre onsets.
 * Rango válido: 60–180 BPM. Devuelve 0 si no hay suficientes onsets.
 */
object BpmDetector {
    fun detect(onsets: List<Long>, sampleRate: Int = AudioEngine.SAMPLE_RATE): Float {
        if (onsets.size < 2) return 0f
        val intervals = onsets.zipWithNext { a, b -> (b - a).toFloat() / sampleRate }
            .filter { it in 0.2f..2.0f } // descarta intervalos absurdos
        if (intervals.isEmpty()) return 0f

        // Histograma de BPM candidatos (cada intervalo y sus múltiplos)
        val votes = HashMap<Int, Float>()
        for (iv in intervals) {
            for (mult in listOf(0.5f, 1f, 2f)) {
                var bpm = 60f / (iv * mult)
                while (bpm < 60f) bpm *= 2
                while (bpm > 180f) bpm /= 2
                val key = bpm.toInt()
                votes[key] = (votes[key] ?: 0f) + 1f
                votes[key - 1] = (votes[key - 1] ?: 0f) + 0.5f
                votes[key + 1] = (votes[key + 1] ?: 0f) + 0.5f
            }
        }
        return votes.maxByOrNull { it.value }?.key?.toFloat() ?: 0f
    }
}
