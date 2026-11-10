package com.doraemon.adb.core;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.doraemon.adb.R;

/**
 * Foreground watchdog. Keeps the on-device ADB link alive:
 *  - re-enables Wireless Debugging itself when Wi-Fi is up (needs WRITE_SECURE_SETTINGS, self-granted once via ADB)
 *  - reconnects whenever the link drops or the Wi-Fi network changes
 */
public class AdbDaemonService extends Service {
    private static final String CHANNEL = "doraemon_daemon";
    private static final Object LOCK = new Object();
    private static volatile boolean running;
    private Thread loop;
    private WatchdogReceiver wifiWatch;

    public static void start(Context c) {
        ContextCompat.startForegroundService(c, new Intent(c, AdbDaemonService.class));
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, AdbDaemonService.class));
    }

    public static void nudge() {
        synchronized (LOCK) { LOCK.notifyAll(); }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createChannel();
        Notification n = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Doraemon daemon")
                .setContentText("Keeping ADB connection alive")
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(1, n);
        }
        if (loop == null || !loop.isAlive()) {
            running = true;
            loop = new Thread(this::watch, "doraemon-watchdog");
            loop.start();
        }
        if (wifiWatch == null) {
            wifiWatch = new WatchdogReceiver();
            registerReceiver(wifiWatch, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
        }
        DaemonState.postStatus("Daemon running");
        return START_STICKY;
    }

    private void watch() {
        AdbManager mgr = AdbManager.get(this);
        while (running) {
            try {
                ensureWirelessDebugging();
                if (mgr.isConnected()) {
                    DaemonState.postConnected(true);
                    DaemonState.postStatus("Connected - watching");
                } else {
                    DaemonState.postConnected(false);
                    DaemonState.postStatus("Reconnecting...");
                    Connector.connect(this, 0);
                }
                synchronized (LOCK) { LOCK.wait(15000); }
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                DaemonState.addLog("watchdog: " + t.getClass().getSimpleName());
                try { Thread.sleep(5000); } catch (InterruptedException e) { return; }
            }
        }
    }

    private void ensureWirelessDebugging() {
        if (checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) return;
        if (!onWifi()) return;
        ContentResolver cr = getContentResolver();
        try {
            if (Settings.Global.getInt(cr, "adb_wifi_enabled", 0) == 0) {
                Settings.Global.putInt(cr, "development_settings_enabled", 1);
                Settings.Global.putInt(cr, "adb_enabled", 1);
                Settings.Global.putInt(cr, "adb_wifi_enabled", 1);
                DaemonState.addLog("Wireless debugging re-enabled");
            }
        } catch (SecurityException ignored) { }
    }

    private boolean onWifi() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        Network n = cm.getActiveNetwork();
        NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    private void createChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Daemon", NotificationManager.IMPORTANCE_LOW));
    }

    @Override
    public void onDestroy() {
        running = false;
        nudge();
        if (wifiWatch != null) { unregisterReceiver(wifiWatch); wifiWatch = null; }
        DaemonState.postStatus("Daemon stopped");
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
