package com.cami.neonloop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cami.neonloop.LooperViewModel
import com.cami.neonloop.ui.theme.Neon

/**
 * Gestor de proyectos: nombre, guardar, nuevo, exportar WAV,
 * y lista de proyectos guardados (cargar / borrar).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectSheet(vm: LooperViewModel, onClose: () -> Unit) {
    var name by remember { mutableStateOf(vm.project.value.name) }

    LaunchedEffect(Unit) { vm.refreshProjects() }

    ModalBottomSheet(onDismissRequest = onClose, containerColor = Neon.Surface) {
        Column(Modifier.padding(20.dp)) {
            Text("PROYECTOS", color = Neon.Cyan, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Spacer(Modifier.height(16.dp))

            // Nombre del proyecto actual
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; vm.renameProject(it) },
                label = { Text("Nombre del proyecto") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Neon.Cyan,
                    focusedLabelColor = Neon.Cyan,
                    cursorColor = Neon.Cyan
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            // Acciones principales
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionChip("💾 GUARDAR", Neon.Magenta) { vm.saveProject() }
                ActionChip("✚ NUEVO", Neon.Green) { vm.newProjectAndLoad(); name = vm.project.value.name }
                ActionChip("⬇ WAV", Neon.Amber) { vm.exportMix() }
            }

            Spacer(Modifier.height(20.dp))
            Text("GUARDADOS", color = Neon.TextDim, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))

            if (vm.savedProjects.isEmpty()) {
                Text("Nada guardado todavía", color = Neon.TextDim, fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 12.dp))
            } else {
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(vm.savedProjects, key = { it.id }) { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Neon.Bg)
                                .border(1.dp, Neon.TextDim.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, color = Neon.Cyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                val bpmTxt = if (p.bpm > 0f) "${p.bpm.toInt()} BPM" else "sin BPM"
                                val loops = p.tracks.count { it.loopFile != null }
                                Text("$bpmTxt · $loops loops", color = Neon.TextDim, fontSize = 11.sp)
                            }
                            TextButton(onClick = { vm.loadProject(p.id); name = p.name; onClose() }) {
                                Text("CARGAR", color = Neon.Green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            TextButton(onClick = { vm.deleteProject(p.id) }) {
                                Text("✕", color = Neon.Red, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ActionChip(text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Neon.Bg)
            .border(1.5.dp, color, RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
