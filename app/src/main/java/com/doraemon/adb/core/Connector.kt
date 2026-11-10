package com.doraemon.adb.core

import android.content.Context
import android.os.Build
import com.doraemon.adb.tools.AutomationEngine

object Connector {
    /** Tries: explicit port -> mDNS auto-discovery (Android 11+) -> saved port -> USB-tcpip 5555. */
    @JvmStatic fun connect(ctx: Context, port: Int): Boolean {
        val m = AdbManager.get(ctx)
        if (m.isConnected) { DaemonState.postConnected(true); return true }
        val saved = Prefs.port(ctx)
        val attempts = buildList<Pair<Int, () -> Boolean>> {
            if (port > 0) add(port to { m.connect("127.0.0.1", port) })
            if (Build.VERSION.SDK_INT >= 30) add(0 to { m.autoConnect(ctx, 5000) })
            if (saved > 0 && saved != port) add(saved to { m.connect("127.0.0.1", saved) })
            add(5555 to { m.connect("127.0.0.1", 5555) })
        }
        for ((p, attempt) in attempts) {
            val ok = try { attempt() } catch (e: Throwable) {
                DaemonState.addLog("connect failed: ${e.javaClass.simpleName}"); false
            }
            if (ok) {
                if (p > 0) Prefs.setPort(ctx, p)
                DaemonState.postConnected(true)
                DaemonState.addLog("ADB connected")
                onConnected(ctx)
                return true
            }
        }
        DaemonState.postConnected(false)
        return false
    }

    /** One-time self-setup: lets the app toggle Wireless Debugging itself after reboots. */
    private fun onConnected(ctx: Context) {
        val r = Shell.execBlocking(ctx, "pm grant ${ctx.packageName} android.permission.WRITE_SECURE_SETTINGS")
        DaemonState.addLog(if (r.ok) "WRITE_SECURE_SETTINGS granted" else "self-grant: ${r.out.take(80)}")
        AutomationEngine.runOnConnectBlocking(ctx)
    }
}
