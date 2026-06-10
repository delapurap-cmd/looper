package com.cami.neonloop.audio

/**
 * Detecta onsets (golpes/transientes) por salto de energía RMS.
 * Se usa para: 1) empezar a grabar justo en el primer golpe,
 * 2) cortar el loop en un transiente (sin clicks ni silencio inicial).
 */
class TransientDetector(
    private val windowSize: Int = 512,
    private val threshold: Float = 2.5f,   // cuánto debe saltar la energía vs promedio
    private val minGapMs: Int = 100        // mínimo entre onsets
) {
    private val history = ArrayDeque<Float>()
    private val historyLen = 43            // ~0.5s de contexto
    private var samplesSinceOnset = Int.MAX_VALUE / 2

    val onsets = mutableListOf<Long>()     // posiciones absolutas en samples
    private var absolutePos = 0L

    /** Procesa un chunk; devuelve true si detectó onset dentro del chunk. */
    fun process(chunk: ShortArray): Boolean {
        var detected = false
        var i = 0
        while (i + windowSize <= chunk.size) {
            var sum = 0.0
            for (j in i until i + windowSize) {
                val s = chunk[j] / 32768f
                sum += s * s
            }
            val rms = kotlin.math.sqrt(sum / windowSize).toFloat()
            val avg = if (history.isEmpty()) 0f else history.average().toFloat()

            val minGapSamples = AudioEngine.SAMPLE_RATE * minGapMs / 1000
            if (avg > 0f && rms > avg * threshold && samplesSinceOnset > minGapSamples) {
                onsets.add(absolutePos + i + firstTransientOffset(chunk, i, windowSize, avg))
                samplesSinceOnset = 0
                detected = true
            }
            history.addLast(rms)
            if (history.size > historyLen) history.removeFirst()
            samplesSinceOnset += windowSize
            i += windowSize
        }
        absolutePos += chunk.size
        return detected
    }

    fun reset() { history.clear(); onsets.clear(); absolutePos = 0; samplesSinceOnset = Int.MAX_VALUE / 2 }

    private fun firstTransientOffset(chunk: ShortArray, start: Int, size: Int, noiseFloor: Float): Int {
        val minAmp = kotlin.math.max(0.03f, noiseFloor * 3f)
        for (i in start until start + size) {
            if (kotlin.math.abs(chunk[i] / 32768f) >= minAmp) return i - start
        }
        return 0
    }
}
