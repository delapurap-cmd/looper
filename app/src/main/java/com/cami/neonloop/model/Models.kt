package com.cami.neonloop.model

import kotlinx.serialization.Serializable

@Serializable
data class Project(
    val id: String,
    var name: String,
    var bpm: Float = 0f,          // 0 = aún no detectado
    var beatsPerLoop: Int = 8,    // longitud del loop maestro en beats
    val tracks: MutableList<Track> = MutableList(8) { Track(index = it) }
)

@Serializable
data class Track(
    val index: Int,
    var loopFile: String? = null, // WAV crudo grabado
    var volume: Float = 1f,       // 0..1
    var pan: Float = 0f,          // -1..1
    var muted: Boolean = false,
    var solo: Boolean = false,
    var pitchSemitones: Float = 0f, // -12..+12
    var stretchToBpm: Boolean = true // warp automático al BPM del proyecto
)
