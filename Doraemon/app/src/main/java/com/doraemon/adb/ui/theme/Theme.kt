package com.doraemon.adb.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Dora {
    val Blue = Color(0xFF0096E0)
    val Red = Color(0xFFE60012)
    val Yellow = Color(0xFFFFD500)
    val Green = Color(0xFF3DDC84)
    val Oled = Color(0xFF000000)
    val Panel = Color(0xFF0B1823)
    val Panel2 = Color(0xFF12283A)
}

private val scheme = darkColorScheme(
    primary = Dora.Blue, onPrimary = Color.White,
    secondary = Dora.Yellow, onSecondary = Color.Black,
    tertiary = Dora.Red,
    background = Dora.Oled, onBackground = Color.White,
    surface = Dora.Panel, onSurface = Color.White,
    surfaceVariant = Dora.Panel2, onSurfaceVariant = Color(0xFFB8D4E8),
    error = Dora.Red,
)

@Composable
fun DoraemonTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)
