package com.doraemon.adb.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.doraemon.adb.core.AdbDaemonService
import com.doraemon.adb.core.DaemonState
import com.doraemon.adb.core.Prefs
import com.doraemon.adb.ui.Btn
import com.doraemon.adb.ui.Mono
import com.doraemon.adb.ui.Panel
import com.doraemon.adb.ui.theme.Dora

enum class Gadget(val title: String, val emoji: String, val blurb: String) {
    PAIR("Pair & Connect", "🔗", "Wireless / USB ADB"),
    DEBLOAT("Debloater", "🧹", "Canta engine"),
    APPS("Apps & Permissions", "📦", "Scope control"),
    TERMINAL("Terminal", "⌨️", "ADB shell"),
    PERF("Performance", "⚡", "Gamma engine"),
    AUTO("Automation", "🤖", "Shell macros"),
    STYLE("Customize", "🎨", "Hex engine"),
    FILES("Files", "📂", "Rootless explorer"),
}

@Composable
fun DashboardScreen(onOpen: (Gadget) -> Unit) {
    val ctx = LocalContext.current
    val status by DaemonState.status.collectAsState()
    val connected by DaemonState.connected.collectAsState()
    val logs by DaemonState.logs.collectAsState()
    var auto by remember { mutableStateOf(Prefs.autoStart(ctx)) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
        Text("Doraemon", style = MaterialTheme.typography.headlineLarge, color = Dora.Blue, fontWeight = FontWeight.Bold)
        Text(
            "The Ultimate Non-Root Android Power-User Ecosystem & ADB Manager.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(if (connected) Dora.Green else Dora.Red))
                Spacer(Modifier.width(8.dp))
                Text(if (connected) "ADB connected" else "ADB offline", fontWeight = FontWeight.SemiBold)
            }
            Mono(status)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Start daemon") { AdbDaemonService.start(ctx) }
                Btn("Stop") { AdbDaemonService.stop(ctx) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Auto-start after reboot", Modifier.weight(1f))
                Switch(auto, { auto = it; Prefs.setAutoStart(ctx, it) })
            }
        }
        LazyVerticalGrid(
            GridCells.Fixed(2), Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(Gadget.entries) { g ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Dora.Panel2),
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(g) }
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(g.emoji, style = MaterialTheme.typography.headlineMedium)
                        Text(g.title, fontWeight = FontWeight.SemiBold)
                        Text(g.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (logs.isNotEmpty()) Mono(logs.takeLast(3).joinToString("\n"), Modifier.padding(top = 8.dp))
    }
}
