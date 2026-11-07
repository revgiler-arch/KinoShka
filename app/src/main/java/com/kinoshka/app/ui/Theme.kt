package com.kinoshka.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Kino {
    val Bg = Color(0xFF0E0D12)
    val Surface = Color(0xFF1A1820)
    val SurfaceHi = Color(0xFF26232E)
    val Accent = Color(0xFFF5A524) // янтарный, как свет проектора
    val OnAccent = Color(0xFF1A1520)
    val Text = Color(0xFFF2EFF5)
    val TextDim = Color(0xFFA7A1B0)
    val Good = Color(0xFF4CC38A)
    val Bad = Color(0xFFE5484D)
}

@Composable
fun KinoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Kino.Accent,
            onPrimary = Kino.OnAccent,
            background = Kino.Bg,
            onBackground = Kino.Text,
            surface = Kino.Surface,
            onSurface = Kino.Text,
            surfaceVariant = Kino.SurfaceHi,
            onSurfaceVariant = Kino.TextDim,
            error = Kino.Bad,
        ),
        content = content,
    )
}
