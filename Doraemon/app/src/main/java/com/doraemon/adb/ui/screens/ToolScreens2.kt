package com.doraemon.adb.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.doraemon.adb.tools.*
import com.doraemon.adb.ui.*
import com.doraemon.adb.ui.theme.Dora
import kotlinx.coroutines.launch

@Composable
fun PerformanceScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var out by remember { mutableStateOf("") }
    var stats by remember { mutableStateOf("") }
    ScreenFrame("Performance", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Panel {
                Text("Profiles", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Profile.entries.forEach { p -> Btn(p.label, Modifier.weight(1f)) { scope.launch { out = "Applying…"; out = PerformanceEngine.apply(ctx, p) } } }
                }
                Mono("ADB profiles change battery saver, animation scales and fixed-performance mode (Android 12+). CPU governor writes only apply on rooted devices.")
            }
            Panel {
                Btn("Refresh stats") { scope.launch { stats = PerformanceEngine.stats(ctx) } }
                if (stats.isNotEmpty()) Mono(stats)
            }
            if (out.isNotEmpty()) Mono(out)
        }
    }
}

@Composable
fun AutomationScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var scripts by remember { mutableStateOf(AutomationEngine.load(ctx)) }
    var editing by remember { mutableStateOf<Script?>(null) }
    var out by remember { mutableStateOf("") }

    ScreenFrame("Automation", onBack) {
        Btn("＋ New script") { editing = Script(AutomationEngine.newId(), "", "# one command per line\nsleep 1\n", false) }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(scripts, key = { it.id }) { s ->
                Panel {
                    Text(s.name.ifBlank { "(unnamed)" } + if (s.onConnect) "  · runs on connect" else "", fontWeight = FontWeight.SemiBold)
                    Mono(s.body.lines().take(3).joinToString("\n"))
                    Row {
                        TextButton({ scope.launch { out = "Running…"; out = AutomationEngine.run(ctx, s) } }) { Text("Run") }
                        TextButton({ editing = s }) { Text("Edit") }
                        TextButton({ AutomationEngine.delete(ctx, s.id); scripts = AutomationEngine.load(ctx) }) { Text("Delete") }
                    }
                }
            }
            item { if (out.isNotEmpty()) Mono(out) }
        }
    }
    editing?.let { e ->
        var name by remember(e.id) { mutableStateOf(e.name) }
        var body by remember(e.id) { mutableStateOf(e.body) }
        var onConnect by remember(e.id) { mutableStateOf(e.onConnect) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Script") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(body, { body = it }, label = { Text("Commands") }, minLines = 5, maxLines = 10)
                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(onConnect, { onConnect = it }); Spacer(Modifier.width(8.dp)); Text("Run when ADB connects") }
                }
            },
            confirmButton = {
                TextButton({ AutomationEngine.upsert(ctx, Script(e.id, name, body, onConnect)); scripts = AutomationEngine.load(ctx); editing = null }) { Text("Save") }
            },
            dismissButton = { TextButton({ editing = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun CustomizeScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var out by remember { mutableStateOf("") }
    var font by remember { mutableStateOf(1f) }
    var dpi by remember { mutableStateOf("") }
    var overlays by remember { mutableStateOf<List<Pair<String, Boolean>>>(emptyList()) }

    ScreenFrame("Customize", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Panel {
                Text("Dark mode", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("yes" to "Dark", "no" to "Light", "auto" to "Auto").forEach { (m, l) ->
                        Btn(l) { scope.launch { out = CustomizationEngine.nightMode(ctx, m).out.ifEmpty { "ok" } } }
                    }
                }
            }
            Panel {
                Text("Font scale: ${"%.2f".format(font)}", fontWeight = FontWeight.SemiBold)
                Slider(font, { font = it }, valueRange = 0.85f..1.4f)
                Btn("Apply") { scope.launch { out = CustomizationEngine.fontScale(ctx, font).out.ifEmpty { "ok" } } }
            }
            Panel {
                Text("Display density (dpi)", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(dpi, { dpi = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Apply", enabled = dpi.toIntOrNull() != null) { scope.launch { out = CustomizationEngine.density(ctx, dpi.toInt()).out.ifEmpty { "ok" } } }
                    Btn("Reset") { scope.launch { out = CustomizationEngine.density(ctx, null).out.ifEmpty { "reset" } } }
                }
            }
            Panel {
                Text("Navigation", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("gestural" to "Gesture", "threebutton" to "3-button").forEach { (m, l) ->
                        Btn(l) { scope.launch { out = CustomizationEngine.navMode(ctx, m).out.ifEmpty { "ok" } } }
                    }
                }
            }
            Panel {
                Text("System overlays", fontWeight = FontWeight.SemiBold)
                Btn("Load overlays") { scope.launch { overlays = CustomizationEngine.overlays(ctx) } }
                overlays.forEach { (pkg, on) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Mono(pkg, Modifier.weight(1f))
                        Switch(on, { v ->
                            scope.launch { out = CustomizationEngine.setOverlay(ctx, pkg, v).out.ifEmpty { "ok" }; overlays = CustomizationEngine.overlays(ctx) }
                        })
                    }
                }
            }
            if (out.isNotEmpty()) Mono(out)
        }
    }
}
