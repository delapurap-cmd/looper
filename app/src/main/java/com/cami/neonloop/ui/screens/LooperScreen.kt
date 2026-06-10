package com.cami.neonloop.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
    var showProjects by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(Neon.Bg).padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Header(vm)
        Spacer(Modifier.height(10.dp))

        // Slots verticales, ancho completo
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items((0..7).toList()) { i ->
                TrackSlot(
                    vm = vm,
                    index = i,
                    onPitch = { vm.selectedTrack.value = i; showPitch = true }
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        TransportBar(vm, onMixer = { showMixer = true }, onProjects = { showProjects = true })
    }

    if (showMixer) MixerSheet(vm) { showMixer = false }
    if (showPitch && vm.selectedTrack.value >= 0) PitchSheet(vm) { showPitch = false }
    if (showProjects) ProjectSheet(vm) { showProjects = false }
}

@Composable
private fun Header(vm: LooperViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("NEON", color = Neon.Cyan, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text("LOOP", color = Neon.Magenta, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            val bpm = vm.bpm.value
            Text(
                if (bpm > 0f) "${bpm.toInt()} BPM" else "-- BPM",
                color = if (bpm > 0f) Neon.Green else Neon.TextDim,
                fontSize = 16.sp, fontWeight = FontWeight.Bold
            )
            Text(vm.project.value.name, color = Neon.TextDim, fontSize = 10.sp)
        }
    }
}

/**
 * Slot de pista a lo ancho:
 * - Cabecera: nombre + controles M/S/♪/x
 * - Centro: onda grabada (zoom A-B opcional con ✓)
 * - Carril inferior: selectores A y B arrastrables
 */
@Composable
private fun TrackSlot(vm: LooperViewModel, index: Int, onPitch: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val rev = vm.mixerControlRevision.value
    @Suppress("UNUSED_VARIABLE") val wrev = vm.waveformRevision.value

    val state = vm.trackStates[index]
    val color = Neon.trackColors[index]
    val t = vm.project.value.tracks[index]
    var zoomed by remember(index) { mutableStateOf(false) }

    val borderColor = when (state) {
        TrackState.EMPTY -> Neon.TextDim.copy(alpha = 0.25f)
        TrackState.ARMED -> Neon.Amber
        TrackState.RECORDING -> Neon.Red
        TrackState.PLAYING -> color
    }

    Column(
        Modifier
            .fillMaxWidth()
            .shadow(if (state == TrackState.PLAYING) 6.dp else 0.dp, RoundedCornerShape(16.dp), spotColor = color)
            .clip(RoundedCornerShape(16.dp))
            .background(Neon.Surface)
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // ---- Cabecera ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            val label = when (state) {
                TrackState.EMPTY -> "PISTA ${index + 1}"
                TrackState.ARMED -> "ARMADO"
                TrackState.RECORDING -> "● GRABANDO"
                TrackState.PLAYING -> "LOOP ${index + 1}"
            }
            Text(label, color = borderColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            if (state == TrackState.PLAYING) {
                SlotControl("M", if (t.muted) Neon.Amber else Neon.TextDim) { vm.toggleMute(index) }
                SlotControl("S", if (t.solo) Neon.Green else Neon.TextDim) { vm.toggleSolo(index) }
                SlotControl("♪", color, onPitch)
                SlotControl("✕", Neon.Red) { vm.clearTrack(index); zoomed = false }
            }
        }

        Spacer(Modifier.height(6.dp))

        // ---- Cuerpo: onda o zona de grabación ----
        val wave = vm.trackWaveforms[index]
        if (state == TrackState.PLAYING && wave != null) {
            Waveform(
                wave = wave, color = color,
                abStart = t.abStart, abEnd = t.abEnd, zoomed = zoomed,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            )
            Spacer(Modifier.height(4.dp))
            // ---- Carril A-B + botón de aprobación ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                ABRail(
                    abStart = t.abStart, abEnd = t.abEnd, color = color,
                    onChange = { a, b -> vm.setAB(index, a, b); if (zoomed) zoomed = false },
                    modifier = Modifier.weight(1f).height(30.dp)
                )
                Spacer(Modifier.width(8.dp))
                SlotControl(
                    if (zoomed) "⤢" else "✓",
                    if (zoomed) Neon.Amber else Neon.Green
                ) { zoomed = !zoomed }
            }
        } else {
            // Vacío / armado / grabando: zona táctil grande
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Neon.Bg)
                    .clickable {
                        when (state) {
                            TrackState.EMPTY -> vm.armTrack(index)
                            TrackState.ARMED, TrackState.RECORDING -> vm.stopRecording()
                            else -> {}
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val hint = when (state) {
                    TrackState.EMPTY -> "TOCA PARA GRABAR"
                    TrackState.ARMED -> "ESPERANDO PRIMER GOLPE…"
                    TrackState.RECORDING -> "TOCA PARA CERRAR EL LOOP"
                    else -> ""
                }
                Text(hint, color = borderColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

/** Dibuja la onda. Si zoomed=true, la región A-B se estira al ancho completo. */
@Composable
private fun Waveform(
    wave: FloatArray, color: Color,
    abStart: Float, abEnd: Float, zoomed: Boolean,
    modifier: Modifier
) {
    Canvas(modifier) {
        val n = wave.size
        if (n == 0) return@Canvas
        val midY = size.height / 2f

        // Rango visible: todo, o solo A-B si está aprobado el zoom
        val from = if (zoomed) abStart else 0f
        val to = if (zoomed) abEnd else 1f
        val span = (to - from).coerceAtLeast(0.001f)

        val bars = 120
        val barW = size.width / bars
        for (b in 0 until bars) {
            val frac = from + span * (b.toFloat() / bars)
            val idx = (frac * (n - 1)).toInt().coerceIn(0, n - 1)
            val h = (wave[idx] * midY).coerceAtLeast(1f)
            val x = b * barW + barW / 2f
            val inSelection = zoomed || (frac in abStart..abEnd)
            drawLine(
                color = if (inSelection) color else color.copy(alpha = 0.22f),
                start = Offset(x, midY - h),
                end = Offset(x, midY + h),
                strokeWidth = barW * 0.6f,
                cap = StrokeCap.Round
            )
        }
        // Marcadores A y B sobre la onda (solo en vista completa)
        if (!zoomed) {
            for (frac in listOf(abStart, abEnd)) {
                val x = frac * size.width
                drawLine(Color.White.copy(alpha = 0.85f), Offset(x, 0f), Offset(x, size.height), 2f)
            }
        }
    }
}

/** Carril inferior con dos manijas arrastrables: A (inicio) y B (fin). */
@Composable
private fun ABRail(
    abStart: Float, abEnd: Float, color: Color,
    onChange: (Float, Float) -> Unit,
    modifier: Modifier
) {
    var width by remember { mutableStateOf(1f) }
    var dragging by remember { mutableStateOf(0) } // 0=nada 1=A 2=B
    val density = LocalDensity.current
    val handleRadius = with(density) { 11.dp.toPx() }

    Canvas(
        modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val ax = abStart * width
                        val bx = abEnd * width
                        dragging = if (kotlin.math.abs(offset.x - ax) <= kotlin.math.abs(offset.x - bx)) 1 else 2
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val frac = (change.position.x / width).coerceIn(0f, 1f)
                        if (dragging == 1) {
                            onChange(frac.coerceAtMost(abEnd - 0.02f), abEnd)
                        } else if (dragging == 2) {
                            onChange(abStart, frac.coerceAtLeast(abStart + 0.02f))
                        }
                    },
                    onDragEnd = { dragging = 0 }
                )
            }
    ) {
        width = size.width
        val cy = size.height / 2f
        val ax = abStart * size.width
        val bx = abEnd * size.width

        // Línea base y tramo seleccionado
        drawLine(Neon.TextDim.copy(alpha = 0.35f), Offset(0f, cy), Offset(size.width, cy), 3f, StrokeCap.Round)
        drawLine(color, Offset(ax, cy), Offset(bx, cy), 5f, StrokeCap.Round)

        // Manijas A y B con sus letras
        drawHandle(ax, cy, handleRadius, color, "A")
        drawHandle(bx, cy, handleRadius, color, "B")
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHandle(
    x: Float, y: Float, radius: Float, color: Color, letter: String
) {
    drawCircle(Neon.Bg, radius, Offset(x, y))
    drawCircle(color, radius, Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
    drawContext.canvas.nativeCanvas.drawText(
        letter, x, y + radius * 0.4f,
        android.graphics.Paint().apply {
            this.color = android.graphics.Color.argb(
                255,
                (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()
            )
            textSize = radius * 1.1f
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
            isAntiAlias = true
        }
    )
}

@Composable
private fun SlotControl(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 6.dp)
            .size(30.dp)
            .clip(CircleShape)
            .background(Neon.Bg)
            .border(1.dp, color, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, fontWeight = FontWeight.Black, fontSize = 13.sp)
    }
}

@Composable
private fun TransportBar(vm: LooperViewModel, onMixer: () -> Unit, onProjects: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NeonButton(if (vm.isPlaying.value) "■" else "▶", Neon.Green) {
            if (vm.isPlaying.value) vm.stop() else vm.play()
        }
        NeonButton("MIX", Neon.Cyan, onMixer)
        NeonButton("📁", Neon.Magenta, onProjects)
    }
}

@Composable
fun NeonButton(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(58.dp)
            .shadow(8.dp, CircleShape, spotColor = color)
            .clip(CircleShape)
            .background(Neon.Surface)
            .border(2.dp, color, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(text, color = color, fontWeight = FontWeight.Bold) }
}
