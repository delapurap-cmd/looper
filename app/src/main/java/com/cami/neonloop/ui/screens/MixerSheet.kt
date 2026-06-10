package com.cami.neonloop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cami.neonloop.LooperViewModel
import com.cami.neonloop.ui.theme.Neon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixerSheet(vm: LooperViewModel, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Neon.Surface) {
        LazyColumn(Modifier.padding(16.dp)) {
            items((0..7).toList()) { i ->
                val t = vm.project.value.tracks[i]
                var vol by remember { mutableStateOf(t.volume) }
                var pan by remember { mutableStateOf(t.pan) }
                var muted by remember { mutableStateOf(t.muted) }
                var solo by remember { mutableStateOf(t.solo) }
                val color = Neon.trackColors[i]

                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("PISTA ${i + 1}", color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.weight(1f))
                        FilterChip(muted, onClick = { vm.toggleMute(i); muted = !muted }, label = { Text("M") })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(solo, onClick = { vm.toggleSolo(i); solo = !solo }, label = { Text("S") })
                    }
                    Slider(vol, { vol = it; vm.setVolume(i, it) },
                        colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color))
                    Slider(pan, { pan = it; vm.setPan(i, it) }, valueRange = -1f..1f,
                        colors = SliderDefaults.colors(thumbColor = Neon.TextDim, activeTrackColor = Neon.TextDim))
                }
            }
        }
    }
}
