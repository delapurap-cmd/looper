package com.cami.neonloop

import android.app.Application
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
    private val engine = AudioEngine()
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

    private val recordBuffer = ArrayList<Short>()
    private var recordingTrack = -1

    // ---------- GRABACIÓN ----------
    /** Armar pista: empieza a escuchar; graba desde el PRIMER transiente. */
    fun armTrack(index: Int) {
        if (recordingTrack != -1) return
        recordingTrack = index
        trackStates[index] = TrackState.ARMED
        recordBuffer.clear()
        detector.reset()
        var started = false
        var samplesBeforeChunk = 0L
        engine.startRecording { chunk ->
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

    /** Detiene grabación: cuantiza, detecta BPM si es el primer loop, y suena. */
    fun stopRecording() {
        val index = recordingTrack
        if (index == -1) return
        engine.stopRecording()
        recordingTrack = -1

        viewModelScope.launch(Dispatchers.Default) {
            val raw = AudioTrimmer.trimStartToSound(recordBuffer.toShortArray())
            if (raw.isEmpty()) { trackStates[index] = TrackState.EMPTY; return@launch }
            val onsets = detectOnsets(raw)

            // Primer loop define el BPM del proyecto
            if (bpm.value <= 0f) {
                val detected = BpmDetector.detect(onsets)
                bpm.value = if (detected > 0f) detected else 120f
                project.value.bpm = bpm.value
            }

            val quantized = Quantizer.quantizeLoop(raw, bpm.value, onsets)

            pm.trackFile(project.value, index).also { WavIO.write(it, quantized) }
            project.value.tracks[index].loopFile = "track_$index.wav"

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
        trackLoopDurationsMs[index] = ((buf.size * 1000L) / AudioEngine.SAMPLE_RATE).toInt().coerceAtLeast(120)
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
        trackLoopDurationsMs[index] = 0
        project.value.tracks[index].loopFile = null
        trackStates[index] = TrackState.EMPTY
        mixerControlRevision.value++
    }

    // ---------- TRANSPORTE ----------
    fun play() { if (!isPlaying.value) { engine.startPlayback(mixer); isPlaying.value = true } }
    fun stop() { engine.stopPlayback(); isPlaying.value = false; mixer.resetPositions() }

    // ---------- MIXER ----------
    fun setVolume(i: Int, v: Float) { project.value.tracks[i].volume = v; mixer.slots[i].volume = v }
    fun setPan(i: Int, v: Float) { project.value.tracks[i].pan = v; mixer.slots[i].pan = v }
    fun toggleMute(i: Int) { val t = project.value.tracks[i]; t.muted = !t.muted; mixer.slots[i].muted = t.muted; mixerControlRevision.value++ }
    fun toggleSolo(i: Int) { val t = project.value.tracks[i]; t.solo = !t.solo; mixer.slots[i].solo = t.solo; mixerControlRevision.value++ }

    // ---------- PITCH ----------
    fun setPitch(i: Int, semitones: Float) {
        project.value.tracks[i].pitchSemitones = semitones
        reprocessTrack(i)
    }

    // ---------- PROYECTOS ----------
    fun saveProject() = viewModelScope.launch(Dispatchers.IO) { pm.save(project.value) }
    fun listProjects() = pm.listProjects()
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
                } else { mixer.setTrackBuffer(i, null); trackStates[i] = TrackState.EMPTY }
            }
        }
    }

    fun exportMix() = viewModelScope.launch(Dispatchers.IO) {
        val f = java.io.File(pm.exportDir, "${project.value.name}_mix.wav")
        Exporter.exportWav(mixer, f)
    }
}
