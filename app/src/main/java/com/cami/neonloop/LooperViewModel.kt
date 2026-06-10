package com.cami.neonloop

import android.app.Application
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cami.neonloop.audio.*
import com.cami.neonloop.data.*
import com.cami.neonloop.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class TrackState { EMPTY, ARMED, RECORDING, PLAYING }

class LooperViewModel(app: Application) : AndroidViewModel(app) {
    private val engine = AudioEngine(app)
    val mixer = Mixer()
    private val pm = ProjectManager(app)
    private val detector = TransientDetector()

    var project = mutableStateOf(pm.newProject("Proyecto 1"))
    val trackStates = mutableStateListOf(*Array(8) { TrackState.EMPTY })
    var bpm = mutableStateOf(0f)
    var isPlaying = mutableStateOf(false)
    var selectedTrack = mutableStateOf(-1)   // para editor de pitch
    var mixerControlRevision = mutableStateOf(0)
    val trackLoopDurationsMs = mutableStateListOf(*Array(8) { 0 })
    val savedProjects = mutableStateListOf<Project>()
    /** Onda dibujable por pista: 240 picos normalizados 0..1 (null = sin loop). */
    val trackWaveforms = mutableStateListOf<FloatArray?>(*arrayOfNulls<FloatArray>(8))
    var waveformRevision = mutableStateOf(0)

    private val recordBuffer = ArrayList<Short>()
    private var recordingTrack = -1

    private fun toast(msg: String) = viewModelScope.launch(Dispatchers.Main) {
        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
    }

    // ---------- GRABACIÓN ----------
    /** Armar pista: empieza a escuchar; graba desde el PRIMER transiente. */
    fun armTrack(index: Int) {
        if (recordingTrack != -1) return
        val hasHeadphones = engine.hasExternalAudioOutput()
        engine.setMonitorGain(if (hasHeadphones) 1f else 0.18f)
        if (!hasHeadphones) toast("Sin audifonos: monitor bajo para evitar sobregrabacion")
        recordingTrack = index
        trackStates[index] = TrackState.ARMED
        recordBuffer.clear()
        detector.reset()
        var started = false
        var samplesBeforeChunk = 0L
        engine.startRecording(enableEchoCancel = !hasHeadphones) { chunk ->
            val hit = detector.process(chunk)
            if (!started && hit) {
                started = true
                trackStates[index] = TrackState.RECORDING
                val onset = detector.onsets.firstOrNull() ?: samplesBeforeChunk
                val offset = (onset - samplesBeforeChunk).toInt().coerceIn(0, chunk.size)
                recordBuffer.addAll(chunk.copyOfRange(offset, chunk.size).toList())
            } else if (started) {
                recordBuffer.addAll(chunk.toList())
            }
            samplesBeforeChunk += chunk.size
        }
    }

    /** Detiene grabación: limpia silencios, cuantiza, detecta BPM y suena. */
    fun stopRecording() {
        val index = recordingTrack
        if (index == -1) return
        engine.stopRecording()
        engine.setMonitorGain(1f)
        recordingTrack = -1

        viewModelScope.launch(Dispatchers.Default) {
            // 1. Quita silencio del inicio
            var raw = AudioTrimmer.trimStartToSound(recordBuffer.toShortArray())
            // 2. Quita el silencio del final (lo que tardaste en presionar parar)
            raw = AudioTrimmer.trimEndToSound(raw)
            if (raw.isEmpty()) { trackStates[index] = TrackState.EMPTY; return@launch }
            val onsets = detectOnsets(raw)

            // Primer loop define el BPM del proyecto
            if (bpm.value <= 0f) {
                val detected = BpmDetector.detect(onsets)
                bpm.value = if (detected > 0f) detected else 120f
                project.value.bpm = bpm.value
            }

            // 3. Redondea al beat más cercano: loop cerrado, sin hueco
            val quantized = Quantizer.quantizeLoop(raw, bpm.value)

            pm.trackFile(project.value, index).also { WavIO.write(it, quantized) }
            project.value.tracks[index].loopFile = "track_$index.wav"

            engine.setMonitorGain(1f)
            applyTrackProcessing(index, quantized)
            trackStates[index] = TrackState.PLAYING
            if (!isPlaying.value) play()
        }
    }

    private fun detectOnsets(audio: ShortArray): List<Long> {
        val d = TransientDetector()
        var start = 0
        while (start < audio.size) {
            val end = (start + 1024).coerceAtMost(audio.size)
            d.process(audio.copyOfRange(start, end))
            start = end
        }
        return d.onsets
    }

    /** Aplica pitch + warp y carga al mixer. */
    private fun applyTrackProcessing(index: Int, raw: ShortArray) {
        val t = project.value.tracks[index]
        var buf = raw
        if (t.pitchSemitones != 0f) buf = PitchShifter.pitchShift(buf, t.pitchSemitones)
        mixer.setTrackBuffer(index, buf)
        mixer.setRegion(index, t.abStart, t.abEnd)
        trackWaveforms[index] = buildWaveform(buf)
        waveformRevision.value++
        updateLoopDuration(index)
        mixer.slots[index].volume = t.volume
        mixer.slots[index].pan = t.pan
        mixer.slots[index].muted = t.muted
        mixer.slots[index].solo = t.solo
    }

    fun reprocessTrack(index: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            val f = pm.trackFile(project.value, index)
            if (f.exists()) applyTrackProcessing(index, WavIO.read(f))
        }
    }

    fun clearTrack(index: Int) {
        mixer.setTrackBuffer(index, null)
        trackWaveforms[index] = null
        waveformRevision.value++
        project.value.tracks[index].abStart = 0f
        project.value.tracks[index].abEnd = 1f
        trackLoopDurationsMs[index] = 0
        project.value.tracks[index].loopFile = null
        trackStates[index] = TrackState.EMPTY
        mixerControlRevision.value++
    }

    // ---------- TRANSPORTE ----------
    fun play() { if (!isPlaying.value) { engine.setMonitorGain(1f); engine.startPlayback(mixer); isPlaying.value = true } }
    fun stop() { engine.setMonitorGain(1f); engine.stopPlayback(); isPlaying.value = false; mixer.resetPositions() }

    // ---------- MIXER ----------
    fun setVolume(i: Int, v: Float) { project.value.tracks[i].volume = v; mixer.slots[i].volume = v }
    fun setPan(i: Int, v: Float) { project.value.tracks[i].pan = v; mixer.slots[i].pan = v }
    fun toggleMute(i: Int) { val t = project.value.tracks[i]; t.muted = !t.muted; mixer.slots[i].muted = t.muted; mixerControlRevision.value++ }
    fun toggleSolo(i: Int) { val t = project.value.tracks[i]; t.solo = !t.solo; mixer.slots[i].solo = t.solo; mixerControlRevision.value++ }

    // ---------- SELECTOR A-B ----------
    /** El usuario mueve A o B en el carril: el audio loopea solo esa región. */
    fun setAB(index: Int, start: Float, end: Float) {
        val t = project.value.tracks[index]
        t.abStart = start.coerceIn(0f, 1f)
        t.abEnd = end.coerceIn(t.abStart, 1f)
        mixer.setRegion(index, t.abStart, t.abEnd)
        updateLoopDuration(index)
    }

    private fun updateLoopDuration(index: Int) {
        val s = mixer.slots[index]
        val len = (s.endPos - s.startPos).coerceAtLeast(1)
        trackLoopDurationsMs[index] = ((len * 1000L) / AudioEngine.SAMPLE_RATE).toInt().coerceAtLeast(120)
    }

    /** Reduce el audio a 240 picos para dibujar la onda. */
    private fun buildWaveform(buf: ShortArray, bins: Int = 240): FloatArray {
        if (buf.isEmpty()) return FloatArray(bins)
        val out = FloatArray(bins)
        val step = (buf.size.toFloat() / bins).coerceAtLeast(1f)
        var maxPeak = 1f
        for (b in 0 until bins) {
            val from = (b * step).toInt().coerceAtMost(buf.size - 1)
            val to = ((b + 1) * step).toInt().coerceAtMost(buf.size)
            var peak = 0
            for (i in from until to) {
                val a = kotlin.math.abs(buf[i].toInt())
                if (a > peak) peak = a
            }
            out[b] = peak.toFloat()
            if (peak > maxPeak) maxPeak = peak.toFloat()
        }
        for (b in 0 until bins) out[b] = out[b] / maxPeak
        return out
    }

    // ---------- PITCH ----------
    fun setPitch(i: Int, semitones: Float) {
        project.value.tracks[i].pitchSemitones = semitones
        reprocessTrack(i)
    }

    // ---------- PROYECTOS ----------
    fun refreshProjects() = viewModelScope.launch(Dispatchers.IO) {
        val list = pm.listProjects()
        savedProjects.clear()
        savedProjects.addAll(list)
    }

    fun renameProject(name: String) {
        if (name.isNotBlank()) project.value.name = name.trim()
    }

    fun saveProject() = viewModelScope.launch(Dispatchers.IO) {
        pm.save(project.value)
        refreshProjects()
        toast("Proyecto \"${project.value.name}\" guardado")
    }

    fun deleteProject(id: String) = viewModelScope.launch(Dispatchers.IO) {
        pm.delete(id)
        refreshProjects()
        toast("Proyecto borrado")
    }

    fun newProjectAndLoad(name: String = "Proyecto ${savedProjects.size + 1}") {
        stop()
        project.value = pm.newProject(name)
        bpm.value = 0f
        for (i in 0..7) {
            mixer.setTrackBuffer(i, null)
            trackWaveforms[i] = null
            trackLoopDurationsMs[i] = 0
            trackStates[i] = TrackState.EMPTY
        }
        waveformRevision.value++
        mixerControlRevision.value++
    }

    fun loadProject(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val p = pm.load(id) ?: return@launch
            stop()
            project.value = p
            bpm.value = p.bpm
            p.tracks.forEachIndexed { i, t ->
                if (t.loopFile != null) {
                    val f = pm.trackFile(p, i)
                    if (f.exists()) { applyTrackProcessing(i, WavIO.read(f)); trackStates[i] = TrackState.PLAYING }
                    else trackStates[i] = TrackState.EMPTY
                } else {
                    mixer.setTrackBuffer(i, null)
                    trackWaveforms[i] = null
                    trackLoopDurationsMs[i] = 0
                    trackStates[i] = TrackState.EMPTY
                }
            }
            toast("Proyecto \"${p.name}\" cargado")
        }
    }

    // ---------- EXPORT ----------
    fun exportMix() = viewModelScope.launch(Dispatchers.IO) {
        val hasLoops = mixer.slots.any { it.buffer != null }
        if (!hasLoops) { toast("No hay loops para exportar"); return@launch }

        val wasPlaying = isPlaying.value
        if (wasPlaying) stop()

        val safeName = project.value.name.replace(Regex("[^a-zA-Z0-9_\\- ]"), "_")
        val f = java.io.File(pm.exportDir, "${safeName}_mix.wav")
        Exporter.exportWav(mixer, f)
        toast("Exportado: ${f.name}\n(Android/data/com.cami.neonloop/files/exports)")

        if (wasPlaying) play()
    }
}
