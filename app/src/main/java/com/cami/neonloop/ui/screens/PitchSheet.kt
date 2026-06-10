package com.cami.neonloop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cami.neonloop.LooperViewModel
import com.cami.neonloop.ui.theme.Neon
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PitchSheet(vm: LooperViewModel, onClose: () -> Unit) {
    val i = vm.selectedTrack.value
    val t = vm.project.value.tracks[i]
    var semis by remember { mutableStateOf(t.pitchSemitones) }
    val color = Neon.trackColors[i]

    ModalBottomSheet(onDismissRequest = onClose, containerColor = Neon.Surface) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PITCH · PISTA ${i + 1}", color = color, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                "${if (semis >= 0) "+" else ""}${semis.roundToInt()} st",
                color = color, fontSize = 40.sp, fontWeight = FontWeight.Black
            )
            Slider(
                semis, { semis = it.roundToInt().toFloat() },
                valueRange = -12f..12f, steps = 23,
                colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color)
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = { semis = 0f }) { Text("RESET") }
                Button(
                    onClick = { vm.setPitch(i, semis); onClose() },
                    colors = ButtonDefaults.buttonColors(containerColor = color)
                ) { Text("APLICAR", color = Neon.Bg, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
