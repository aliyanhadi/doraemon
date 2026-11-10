package com.doraemon.adb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doraemon.adb.ui.theme.Dora

@Composable
fun ScreenFrame(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().navigationBarsPadding().imePadding().padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("◀ Back") }
            Text(title, style = MaterialTheme.typography.titleLarge, color = Dora.Blue)
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), content = content)
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
fun Mono(text: String, modifier: Modifier = Modifier, color: Color = Color(0xFFBFE6FF)) =
    Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = color, modifier = modifier)

@Composable
fun Btn(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) =
    Button(onClick = onClick, enabled = enabled, modifier = modifier) { Text(label) }
