package com.doraemon.adb.core

import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Observable daemon state shared by the Java service and the Compose UI. */
object DaemonState {
    val status = MutableStateFlow("Daemon stopped")
    val connected = MutableStateFlow(false)
    val logs = MutableStateFlow<List<String>>(emptyList())
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    @JvmStatic fun postStatus(s: String) { status.value = s }
    @JvmStatic fun postConnected(b: Boolean) { connected.value = b }
    @JvmStatic fun addLog(m: String) {
        logs.value = (logs.value + "${fmt.format(Date())} $m").takeLast(80)
    }
}
