package com.cami.neonloop.audio

/**
 * Mezcla las 8 pistas en tiempo real. Cada pista guarda su buffer ya
 * procesado (cuantizado + pitch + warp) y se loopea por posición.
 * Salida estéreo intercalada (L,R,L,R...).
 */
class Mixer {
    data class Slot(
        var buffer: ShortArray? = null,
        var pos: Int = 0,
        var volume: Float = 1f,
        var pan: Float = 0f,
        var muted: Boolean = false,
        var solo: Boolean = false
    )

    val slots = Array(8) { Slot() }
    var masterVolume = 1f

    /** Largo del loop maestro (pista más larga); las demás se repiten dentro. */
    private fun anySolo() = slots.any { it.solo && it.buffer != null }

    @Synchronized
    fun nextStereoChunk(frames: Int): ShortArray {
        val out = ShortArray(frames * 2)
        val soloMode = anySolo()
        for (f in 0 until frames) {
            var l = 0f; var r = 0f
            for (s in slots) {
                val buf = s.buffer ?: continue
                if (buf.isEmpty()) continue
                if (s.muted || (soloMode && !s.solo)) { s.pos = (s.pos + 1) % buf.size; continue }
                val sample = buf[s.pos] / 32768f * s.volume
                val panL = kotlin.math.sqrt((1f - s.pan) / 2f)
                val panR = kotlin.math.sqrt((1f + s.pan) / 2f)
                l += sample * panL
                r += sample * panR
                s.pos = (s.pos + 1) % buf.size
            }
            out[f * 2] = (l * masterVolume * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            out[f * 2 + 1] = (r * masterVolume * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    @Synchronized
    fun setTrackBuffer(index: Int, buffer: ShortArray?) {
        slots[index].buffer = buffer
        slots[index].pos = 0
    }

    @Synchronized
    fun resetPositions() { slots.forEach { it.pos = 0 } }
}
