package com.cami.neonloop.data

import com.cami.neonloop.audio.Mixer
import java.io.File

/**
 * Exporta la mezcla a WAV estéreo (offline, renderiza N repeticiones del loop).
 * NOTA MP3: Android no trae encoder MP3 nativo. Opciones (ver GUIA_IA.md):
 *  a) Exportar M4A/AAC con MediaCodec (nativo, recomendado)
 *  b) Agregar librería LAME para MP3 real
 */
object Exporter {
    fun exportWav(mixer: Mixer, outFile: File, loopRepeats: Int = 4) {
        mixer.resetPositions()
        val loopLen = mixer.slots.mapNotNull { it.buffer?.size }.maxOrNull() ?: return
        val totalFrames = loopLen * loopRepeats
        val all = ShortArray(totalFrames * 2)
        var written = 0
        while (written < totalFrames) {
            val chunk = mixer.nextStereoChunk(minOf(4096, totalFrames - written))
            chunk.copyInto(all, written * 2)
            written += chunk.size / 2
        }
        WavIO.write(outFile, all, channels = 2)
        mixer.resetPositions()
    }
}
