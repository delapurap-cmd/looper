package com.cami.neonloop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Neon {
    val Bg = Color(0xFF0A0A0F)
    val Surface = Color(0xFF12121C)
    val Cyan = Color(0xFF00F0FF)
    val Magenta = Color(0xFFFF2BD6)
    val Green = Color(0xFF39FF14)
    val Amber = Color(0xFFFFB300)
    val Red = Color(0xFFFF3355)
    val TextDim = Color(0xFF7A7A90)
    val trackColors = listOf(
        Cyan, Magenta, Green, Amber,
        Color(0xFF7C4DFF), Color(0xFFFF6E40), Color(0xFF18FFFF), Color(0xFFEEFF41)
    )
}

@Composable
fun NeonLoopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Neon.Bg,
            surface = Neon.Surface,
            primary = Neon.Cyan,
            secondary = Neon.Magenta
        ),
        content = content
    )
}
