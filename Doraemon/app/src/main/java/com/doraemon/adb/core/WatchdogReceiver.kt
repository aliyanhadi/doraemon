package com.doraemon.adb.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.SystemClock

/** Restarts the daemon after boot / app update / periodic alarm, and nudges it on Wi-Fi changes. */
class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, ACTION_WATCHDOG -> {
                if (Prefs.autoStart(ctx)) {
                    try { AdbDaemonService.start(ctx) }
                    catch (e: Exception) { DaemonState.addLog("watchdog start blocked: ${e.javaClass.simpleName}") }
                }
                schedule(ctx)
            }
            WifiManager.NETWORK_STATE_CHANGED_ACTION -> AdbDaemonService.nudge()
        }
    }

    companion object {
        const val ACTION_WATCHDOG = "com.doraemon.adb.WATCHDOG"
        private const val INTERVAL = 15 * 60 * 1000L

        fun schedule(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(
                ctx, 0, Intent(ctx, WatchdogReceiver::class.java).setAction(ACTION_WATCHDOG),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + INTERVAL, INTERVAL, pi)
        }
    }
}
