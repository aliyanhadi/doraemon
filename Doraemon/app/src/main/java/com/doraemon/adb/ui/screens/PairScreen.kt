package com.doraemon.adb.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.doraemon.adb.core.*
import com.doraemon.adb.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun PairScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val connected by DaemonState.connected.collectAsState()
    var host by remember { mutableStateOf("127.0.0.1") }
    var pairPort by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var port by remember { mutableStateOf(Prefs.port(ctx).takeIf { it > 0 }?.toString() ?: "") }
    var out by remember { mutableStateOf("") }
    val num = KeyboardOptions(keyboardType = KeyboardType.Number)

    ScreenFrame("Pair & Connect", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Panel {
                Text("1 · Pair (Android 11+)", fontWeight = FontWeight.SemiBold)
                Mono("Developer options → Wireless debugging → ON → \"Pair device with pairing code\". Keep that dialog visible (split-screen or pop-up) and type its port + code here.")
                OutlinedTextField(host, { host = it }, label = { Text("Host") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pairPort, { pairPort = it }, label = { Text("Pairing port") }, singleLine = true, keyboardOptions = num, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(code, { code = it }, label = { Text("Pairing code") }, singleLine = true, keyboardOptions = num, modifier = Modifier.fillMaxWidth())
                Btn("Pair", enabled = pairPort.isNotBlank() && code.isNotBlank()) {
                    scope.launch(Dispatchers.IO) {
                        out = try {
                            if (AdbManager.get(ctx).pair(host, pairPort.toInt(), code)) "Paired ✔ — now tap Connect." else "Pairing rejected."
                        } catch (e: Throwable) { "Pair failed: ${e.message}" }
                    }
                }
            }
            Panel {
                Text("2 · Connect", fontWeight = FontWeight.SemiBold)
                Mono("Leave the port empty to auto-discover it (Android 11+). USB ADB (Android 8–10): run `adb tcpip 5555` once from a PC, then tap Connect.")
                OutlinedTextField(port, { port = it }, label = { Text("Connect port (optional)") }, singleLine = true, keyboardOptions = num, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Connect") {
                        scope.launch(Dispatchers.IO) {
                            out = "Connecting…"
                            out = if (Connector.connect(ctx, port.toIntOrNull() ?: 0)) "Connected ✔" else "Could not connect. Is Wireless debugging on and the device paired?"
                        }
                    }
                    Btn("Start daemon") { AdbDaemonService.start(ctx) }
                }
                Mono(if (connected) "State: connected" else "State: offline")
            }
            Panel {
                Text("Fallback bridges", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Check root") {
                        scope.launch(Dispatchers.IO) { out = if (Shell.hasRoot()) "su available ✔" else "No root (normal)." }
                    }
                    Btn("Dhizuku") {
                        scope.launch(Dispatchers.IO) {
                            out = when {
                                !DhizukuBridge.isInstalled(ctx) -> "Dhizuku app not installed."
                                !DhizukuBridge.init(ctx) -> "Dhizuku service not running."
                                DhizukuBridge.isGranted() -> "Dhizuku permission granted ✔"
                                else -> { DhizukuBridge.requestPermission { ok -> out = if (ok) "Dhizuku granted ✔" else "Dhizuku denied." }; "Requesting…" }
                            }
                        }
                    }
                }
            }
            if (out.isNotEmpty()) Mono(out)
        }
    }
}
