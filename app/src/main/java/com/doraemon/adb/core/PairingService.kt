package com.doraemon.adb.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.doraemon.adb.R
import kotlin.concurrent.thread

/**
 * Shizuku-style pairing. While this service runs it watches mDNS for the "_adb-tls-pairing._tcp" service that
 * Android advertises only while the "Pair device with pairing code" dialog is open. When it appears, the
 * notification gets an inline "Enter pairing code" box, so you never have to leave the Settings dialog.
 */
class PairingService : Service() {
    private lateinit var nsd: NsdManager
    private var listener: NsdManager.DiscoveryListener? = null
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var resolving = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel(this)
        val n = build("Doraemon pairing", "Open Wireless debugging → Pair device with pairing code", null)
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(NOTIF_ID, n)
        nsd = getSystemService(Context.NSD_SERVICE) as NsdManager
        if (listener == null) startDiscovery()
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stopSelf() }, 5 * 60_000L)   // give up after 5 minutes
        return START_NOT_STICKY
    }

    private fun startDiscovery() {
        val l = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(type: String, code: Int) { DaemonState.addLog("pairing discovery failed ($code)") }
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
            override fun onDiscoveryStarted(type: String) { DaemonState.addLog("waiting for pairing dialog") }
            override fun onDiscoveryStopped(type: String) {}
            override fun onServiceFound(info: NsdServiceInfo) { resolve(info) }
            override fun onServiceLost(info: NsdServiceInfo) {
                notify(build("Doraemon pairing", "Pairing dialog closed. Tap \"Pair device with pairing code\" again.", null))
            }
        }
        listener = l
        nsd.discoverServices("_adb-tls-pairing._tcp", NsdManager.PROTOCOL_DNS_SD, l)
    }

    @Suppress("DEPRECATION")
    private fun resolve(info: NsdServiceInfo) {
        if (resolving) { handler.postDelayed({ resolve(info) }, 300); return }
        resolving = true
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(i: NsdServiceInfo, code: Int) { resolving = false }
            override fun onServiceResolved(i: NsdServiceInfo) {
                resolving = false
                DaemonState.addLog("pairing port ${i.port}")
                notify(build("Pairing code needed", "Port ${i.port} found. Type the 6-digit code below.", i.port))
            }
        })
    }

    private fun build(title: String, text: String, port: Int?): Notification {
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title).setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
        if (port != null) {
            val remote = RemoteInput.Builder(KEY).setLabel("Pairing code").build()
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(
                this, 1, Intent(this, PairingReceiver::class.java).putExtra(EXTRA_PORT, port), flags
            )
            b.addAction(NotificationCompat.Action.Builder(R.drawable.ic_launcher, "Enter pairing code", pi).addRemoteInput(remote).build())
        }
        return b.build()
    }

    private fun notify(n: Notification) =
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, n)

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL = "doraemon_pairing"
        const val KEY = "pairing_code"
        const val EXTRA_PORT = "port"
        const val NOTIF_ID = 2
        const val RESULT_ID = 3

        fun ensureChannel(ctx: Context) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Pairing", NotificationManager.IMPORTANCE_HIGH))
        }

        fun start(ctx: Context) =
            ContextCompat.startForegroundService(ctx, Intent(ctx, PairingService::class.java))

        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, PairingService::class.java)) }
    }
}

/** Receives the code typed into the notification, pairs, connects and starts the daemon. */
class PairingReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(PairingService.KEY)?.toString()?.trim()
        val port = intent.getIntExtra(PairingService.EXTRA_PORT, 0)
        val pending = goAsync()
        thread {
            val msg = try {
                if (code.isNullOrEmpty() || port <= 0) "No code or port received."
                else if (!AdbManager.get(ctx).pair("127.0.0.1", port, code)) "Pairing rejected - check the code."
                else if (Connector.connect(ctx, 0)) {
                    AdbDaemonService.start(ctx)
                    "Paired and connected ✔"
                } else "Paired ✔. Turn Wireless debugging on, then tap Connect in Doraemon."
            } catch (e: Throwable) {
                "Pairing failed: ${e.message}"
            }
            DaemonState.addLog(msg)
            PairingService.stop(ctx)
            val n = NotificationCompat.Builder(ctx, PairingService.CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Doraemon").setContentText(msg)
                .setAutoCancel(true).build()
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(PairingService.RESULT_ID, n)
            pending.finish()
        }
    }
}
