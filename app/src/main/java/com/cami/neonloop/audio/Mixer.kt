package com.cami.neonloop.audio

/**
 * Mezcla las 8 pistas en tiempo real. Cada pista guarda su buffer ya
 * procesado y se loopea SOLO dentro de la región A-B seleccionada.
 * Salida estéreo intercalada (L,R,L,R...).
 */
class Mixer {
    data class Slot(
        var buffer: ShortArray? = null,
        var pos: Int = 0,
        var startPos: Int = 0,     // sample A
        var endPos: Int = 0,       // sample B (0 = hasta el final)
        var volume: Float = 1f,
        var pan: Float = 0f,
        var muted: Boolean = false,
        var solo: Boolean = false
    )

    val slots = Array(8) { Slot() }
    var masterVolume = 1f

    private fun anySolo() = slots.any { it.solo && it.buffer != null }

    private fun regionEnd(s: Slot, bufSize: Int): Int =
        if (s.endPos in (s.startPos + 1)..bufSize) s.endPos else bufSize

    @Synchronized
    fun nextStereoChunk(frames: Int): ShortArray {
        val out = ShortArray(frames * 2)
        val soloMode = anySolo()
        for (f in 0 until frames) {
            var l = 0f; var r = 0f
            for (s in slots) {
                val buf = s.buffer ?: continue
                if (buf.isEmpty()) continue
                val start = s.startPos.coerceIn(0, buf.size - 1)
                val end = regionEnd(s, buf.size)
                if (s.pos < start || s.pos >= end) s.pos = start
                if (s.muted || (soloMode && !s.solo)) {
                    s.pos = if (s.pos + 1 >= end) start else s.pos + 1
                    continue
                }
                val sample = buf[s.pos] / 32768f * s.volume
                val panL = kotlin.math.sqrt((1f - s.pan) / 2f)
                val panR = kotlin.math.sqrt((1f + s.pan) / 2f)
                l += sample * panL
                r += sample * panR
                s.pos = if (s.pos + 1 >= end) start else s.pos + 1
            }
            out[f * 2] = (l * masterVolume * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            out[f * 2 + 1] = (r * masterVolume * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    @Synchronized
    fun setTrackBuffer(index: Int, buffer: ShortArray?) {
        val s = slots[index]
        s.buffer = buffer
        s.startPos = 0
        s.endPos = buffer?.size ?: 0
        s.pos = 0
    }

    /** Define la región A-B en fracciones 0..1 del buffer. */
    @Synchronized
    fun setRegion(index: Int, startFrac: Float, endFrac: Float) {
        val s = slots[index]
        val buf = s.buffer ?: return
        val a = (startFrac.coerceIn(0f, 1f) * buf.size).toInt()
        val b = (endFrac.coerceIn(0f, 1f) * buf.size).toInt()
        s.startPos = kotlin.math.min(a, b).coerceIn(0, buf.size - 1)
        s.endPos = kotlin.math.max(a, b).coerceIn(s.startPos + 1, buf.size)
        // Mínimo 50ms para no hacer un zumbido
        val minLen = AudioEngine.SAMPLE_RATE / 20
        if (s.endPos - s.startPos < minLen) s.endPos = (s.startPos + minLen).coerceAtMost(buf.size)
        s.pos = s.startPos
    }

    @Synchronized
    fun resetPositions() { slots.forEach { it.pos = it.startPos } }
}
