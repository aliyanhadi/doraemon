package com.doraemon.adb.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doraemon.adb.core.Shell
import com.doraemon.adb.tools.*
import com.doraemon.adb.ui.*
import com.doraemon.adb.ui.theme.Dora
import kotlinx.coroutines.launch

@Composable
fun TerminalScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lines = remember { mutableStateListOf("Doraemon terminal — commands run via ADB shell (or su if ADB is offline).") }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) { listState.animateScrollToItem(maxOf(0, lines.size - 1)) }

    ScreenFrame("Terminal", onBack) {
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
            LazyColumn(state = listState) { items(lines) { Mono(it) } }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(TerminalEngine.quick) { q -> AssistChip(onClick = { input = q }, label = { Text(q.take(26)) }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("shell command") })
            Spacer(Modifier.width(8.dp))
            Btn("Run", enabled = !busy && input.isNotBlank()) {
                val cmd = input; input = ""; lines += "$ $cmd"; busy = true
                scope.launch {
                    val r = TerminalEngine.run(ctx, cmd)
                    lines += r.out.ifEmpty { "(no output, exit ${r.code})" }
                    busy = false
                }
            }
        }
    }
}

@Composable
fun DebloaterScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<PkgItem>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var showUser by remember { mutableStateOf(false) }
    var sel by remember { mutableStateOf<PkgItem?>(null) }
    var msg by remember { mutableStateOf("") }
    fun load() { scope.launch { items = DebloaterEngine.list(ctx) } }
    LaunchedEffect(Unit) { load() }
    val shown = items.filter {
        (showUser || it.system) && (query.isBlank() || it.pkg.contains(query, true) || it.label.contains(query, true))
    }

    ScreenFrame("Debloater", onBack) {
        Panel {
            Text(
                "⚠ Removing core components (Play Services, telephony, SystemUI, Settings, providers) can leave a device unbootable. Protected packages are locked here. Removal is per-user and reversible with Restore.",
                color = Dora.Yellow, fontSize = 12.sp
            )
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search") })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(showUser, { showUser = it }); Spacer(Modifier.width(8.dp)); Text("Show user apps")
            Spacer(Modifier.weight(1f)); TextButton({ load() }) { Text("Refresh") }
        }
        if (msg.isNotEmpty()) Mono(msg)
        LazyColumn(Modifier.weight(1f)) {
            items(shown, key = { it.pkg }) { p ->
                Row(Modifier.fillMaxWidth().clickable { sel = p }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(p.label); Mono(p.pkg) }
                    Text(if (p.protectedPkg) "🔒" else when (p.state) { PkgState.ACTIVE -> ""; PkgState.DISABLED -> "⏸"; PkgState.REMOVED -> "🗑" })
                }
                HorizontalDivider(color = Dora.Panel2)
            }
        }
    }
    sel?.let { p ->
        AlertDialog(
            onDismissRequest = { sel = null },
            title = { Text(p.label) },
            text = {
                Column {
                    Mono(p.pkg)
                    Text(if (p.protectedPkg) "Protected system component — removal is blocked." else "Disable is a soft switch-off. Uninstall removes the app for user 0 only; Restore brings it back.")
                }
            },
            confirmButton = {
                Row {
                    if (!p.protectedPkg) {
                        TextButton({ scope.launch { msg = DebloaterEngine.disable(ctx, p.pkg).out; sel = null; load() } }) { Text("Disable") }
                        TextButton({ scope.launch { msg = DebloaterEngine.uninstall(ctx, p.pkg).out; sel = null; load() } }) { Text("Uninstall") }
                    }
                    TextButton({ scope.launch { msg = DebloaterEngine.restore(ctx, p.pkg).out; sel = null; load() } }) { Text("Restore") }
                }
            },
            dismissButton = { TextButton({ sel = null }) { Text("Close") } }
        )
    }
}

@Composable
fun AppsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<PkgItem>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<PkgItem?>(null) }
    var perms by remember { mutableStateOf<List<PermItem>>(emptyList()) }
    var ops by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var msg by remember { mutableStateOf("") }
    val audit = remember { mutableStateListOf<String>() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch { msg = "Installing…"; msg = PackageEngine.installApks(ctx, uris).out.ifEmpty { "Done" } }
    }
    suspend fun refresh(p: PkgItem) {
        perms = PackageEngine.requestedPerms(ctx, p.pkg)
        ops = PackageEngine.appOps.associateWith { PackageEngine.appOpMode(ctx, p.pkg, it) }
    }
    LaunchedEffect(Unit) { apps = DebloaterEngine.list(ctx).filter { !it.system && it.state != PkgState.REMOVED } }
    LaunchedEffect(picked) { picked?.let { refresh(it) } }

    val p = picked
    ScreenFrame(p?.label ?: "Apps & Permissions", { if (p != null) picked = null else onBack() }) {
        if (p == null) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search user apps") })
            Btn("Install APK / split APKs (needs ADB)") { picker.launch(arrayOf("*/*")) }
            if (msg.isNotEmpty()) Mono(msg)
            LazyColumn(Modifier.weight(1f)) {
                items(apps.filter { query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true) }, key = { it.pkg }) { a ->
                    Column(Modifier.fillMaxWidth().clickable { picked = a }.padding(vertical = 8.dp)) { Text(a.label); Mono(a.pkg) }
                    HorizontalDivider(color = Dora.Panel2)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                item { Mono(p.pkg); Spacer(Modifier.height(8.dp)); Text("Runtime permissions", fontWeight = FontWeight.SemiBold, color = Dora.Yellow) }
                items(perms.filter { it.dangerous }, key = { it.name }) { pm ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Mono(pm.name.removePrefix("android.permission."), Modifier.weight(1f))
                        Switch(pm.granted, { g ->
                            scope.launch {
                                val r = PackageEngine.setPermission(ctx, p.pkg, pm.name, g)
                                audit += "${if (g) "GRANT" else "REVOKE"} ${pm.name.substringAfterLast('.')} → ${if (r.ok) "ok" else r.out.take(50)}"
                                refresh(p)
                            }
                        })
                    }
                }
                item { Spacer(Modifier.height(8.dp)); Text("Special access (AppOps)", fontWeight = FontWeight.SemiBold, color = Dora.Yellow) }
                items(PackageEngine.appOps) { op ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Mono(op); Mono("mode: ${ops[op] ?: "…"}", color = Color.Gray) }
                        TextButton({ scope.launch { val r = PackageEngine.setAppOp(ctx, p.pkg, op, true); audit += "ALLOW $op → ${r.ok}"; refresh(p) } }) { Text("Allow") }
                        TextButton({ scope.launch { val r = PackageEngine.setAppOp(ctx, p.pkg, op, false); audit += "DENY $op → ${r.ok}"; refresh(p) } }) { Text("Deny") }
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Mono("Other declared permissions: ${perms.count { !it.dangerous }} (install-time, not user-scopable)")
                    if (audit.isNotEmpty()) { Text("Scope audit", fontWeight = FontWeight.SemiBold, color = Dora.Yellow); audit.takeLast(10).forEach { Mono(it) } }
                }
            }
        }
    }
}

@Composable
fun FilesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var path by remember { mutableStateOf("/sdcard") }
    var entries by remember { mutableStateOf<List<String>>(emptyList()) }
    var info by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(path) {
        val r = Shell.exec(ctx, "ls -1ap ${Shell.quote(path)}")
        entries = if (r.ok) r.out.lines().filter { it.isNotBlank() && it != "./" && it != "../" } else emptyList()
        info = if (r.ok) "" else r.out
    }
    ScreenFrame("Files", onBack) {
        Mono(path)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf("/sdcard", "/sdcard/Android/data", "/data/local/tmp", "/system", "/")) { p ->
                AssistChip(onClick = { path = p }, label = { Text(p) })
            }
        }
        TextButton({ path = path.trimEnd('/').substringBeforeLast('/').ifEmpty { "/" } }) { Text("⬆ Up") }
        if (info.isNotEmpty()) Mono(info)
        LazyColumn(Modifier.weight(1f)) {
            items(entries) { e ->
                Text(
                    (if (e.endsWith("/")) "📁 " else "📄 ") + e.trimEnd('/'),
                    Modifier.fillMaxWidth().clickable {
                        if (e.endsWith("/")) path = path.trimEnd('/') + "/" + e.trimEnd('/')
                        else scope.launch { info = Shell.exec(ctx, "ls -l ${Shell.quote(path.trimEnd('/') + "/" + e)}").out }
                    }.padding(vertical = 8.dp)
                )
            }
        }
    }
}
