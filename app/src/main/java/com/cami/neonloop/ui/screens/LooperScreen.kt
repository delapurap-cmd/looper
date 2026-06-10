package com.cami.neonloop.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cami.neonloop.LooperViewModel
import com.cami.neonloop.TrackState
import com.cami.neonloop.ui.theme.Neon

@Composable
fun LooperScreen(vm: LooperViewModel) {
    var showMixer by remember { mutableStateOf(false) }
    var showPitch by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(Neon.Bg).padding(16.dp)
    ) {
        Header(vm)
        Spacer(Modifier.height(16.dp))

        // Pads de 8 pistas (grid 2x4)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items((0..7).toList()) { i ->
                TrackPad(
                    index = i,
                    state = vm.trackStates[i],
                    color = Neon.trackColors[i],
                    muted = vm.project.value.tracks[i].muted,
                    solo = vm.project.value.tracks[i].solo,
                    controlRevision = vm.mixerControlRevision.value,
                    loopDurationMs = vm.trackLoopDurationsMs[i],
                    isPlaying = vm.isPlaying.value,
                    onTap = {
                        when (vm.trackStates[i]) {
                            TrackState.EMPTY -> vm.armTrack(i)
                            TrackState.ARMED, TrackState.RECORDING -> vm.stopRecording()
                            TrackState.PLAYING -> { vm.selectedTrack.value = i; showPitch = true }
                        }
                    },
                    onMute = { vm.toggleMute(i) },
                    onSolo = { vm.toggleSolo(i) },
                    onClear = { vm.clearTrack(i) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        TransportBar(vm, onMixer = { showMixer = true })
    }

    if (showMixer) MixerSheet(vm) { showMixer = false }
    if (showPitch && vm.selectedTrack.value >= 0) PitchSheet(vm) { showPitch = false }
}

@Composable
private fun Header(vm: LooperViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("NEON", color = Neon.Cyan, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text("LOOP", color = Neon.Magenta, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.weight(1f))
        val bpm = vm.bpm.value
        Text(
            if (bpm > 0f) "${bpm.toInt()} BPM" else "-- BPM",
            color = if (bpm > 0f) Neon.Green else Neon.TextDim,
            fontSize = 18.sp, fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TrackPad(
    index: Int, state: TrackState, color: Color,
    muted: Boolean, solo: Boolean, controlRevision: Int,
    loopDurationMs: Int, isPlaying: Boolean,
    onTap: () -> Unit, onMute: () -> Unit, onSolo: () -> Unit, onClear: () -> Unit
) {
    @Suppress("UNUSED_VARIABLE")
    val revision = controlRevision
    val transition = rememberInfiniteTransition(label = "loop-$index")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = loopDurationMs.coerceAtLeast(120),
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "loop-progress-$index"
    )
    val edgePulse = if (state == TrackState.PLAYING && isPlaying && loopDurationMs > 0) {
        when {
            progress < 0.08f -> 1f - (progress / 0.08f)
            progress > 0.92f -> (progress - 0.92f) / 0.08f
            else -> 0f
        }
    } else 0f
    val (borderColor, label) = when (state) {
        TrackState.EMPTY -> Neon.TextDim.copy(alpha = 0.3f) to "REC"
        TrackState.ARMED -> Neon.Amber to "ARMADO"
        TrackState.RECORDING -> Neon.Red to "● GRABANDO"
        TrackState.PLAYING -> color to "LOOP ${index + 1}"
    }
    Box(
        Modifier
            .aspectRatio(1.4f)
            .shadow((8 + edgePulse * 22).dp, RoundedCornerShape(20.dp), spotColor = color)
            .clip(RoundedCornerShape(20.dp))
            .background(Neon.Surface)
            .border((2 + edgePulse * 3).dp, borderColor.copy(alpha = 0.65f + edgePulse * 0.35f), RoundedCornerShape(20.dp))
            .clickable { onTap() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = borderColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        if (state == TrackState.PLAYING) {
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PadControl("M", if (muted) Neon.Amber else Neon.TextDim, onMute)
                PadControl("S", if (solo) Neon.Green else Neon.TextDim, onSolo)
                PadControl("x", Neon.Red, onClear)
            }
        }
    }
}

@Composable
private fun PadControl(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Neon.Bg)
            .border(1.dp, color, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, fontWeight = FontWeight.Black, fontSize = 12.sp)
    }
}

@Composable
private fun TransportBar(vm: LooperViewModel, onMixer: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NeonButton(if (vm.isPlaying.value) "■" else "▶", Neon.Green) {
            if (vm.isPlaying.value) vm.stop() else vm.play()
        }
        NeonButton("MIX", Neon.Cyan, onMixer)
        NeonButton("💾", Neon.Magenta) { vm.saveProject() }
        NeonButton("WAV", Neon.Amber) { vm.exportMix() }
    }
}

@Composable
fun NeonButton(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(64.dp)
            .shadow(8.dp, CircleShape, spotColor = color)
            .clip(CircleShape)
            .background(Neon.Surface)
            .border(2.dp, color, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(text, color = color, fontWeight = FontWeight.Bold) }
}
