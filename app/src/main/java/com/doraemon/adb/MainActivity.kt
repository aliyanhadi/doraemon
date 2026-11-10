package com.doraemon.adb

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.doraemon.adb.ui.screens.*
import com.doraemon.adb.ui.theme.DoraemonTheme

class MainActivity : ComponentActivity() {
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { DoraemonTheme { Root() } }
    }
}

@Composable
fun Root() {
    var screen by rememberSaveable { mutableStateOf<Gadget?>(null) }
    BackHandler(enabled = screen != null) { screen = null }
    val back = { screen = null }
    when (screen) {
        null -> DashboardScreen { screen = it }
        Gadget.PAIR -> PairScreen(back)
        Gadget.DEBLOAT -> DebloaterScreen(back)
        Gadget.APPS -> AppsScreen(back)
        Gadget.TERMINAL -> TerminalScreen(back)
        Gadget.PERF -> PerformanceScreen(back)
        Gadget.AUTO -> AutomationScreen(back)
        Gadget.STYLE -> CustomizeScreen(back)
        Gadget.FILES -> FilesScreen(back)
    }
}
