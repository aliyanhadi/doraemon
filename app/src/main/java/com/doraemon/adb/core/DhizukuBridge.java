package com.doraemon.adb.core;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IBinder;

import com.rosan.dhizuku.api.Dhizuku;
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.lang.reflect.Method;

/**
 * Device-Owner proxy through Dhizuku. Experimental: DevicePolicyManager calls go through a binder wrapper
 * and the hidden IDevicePolicyManager interface, whose signatures differ between Android versions.
 */
public final class DhizukuBridge {
    public interface Callback { void onResult(boolean granted); }

    private DhizukuBridge() { }

    public static boolean isInstalled(Context c) {
        try { c.getPackageManager().getPackageInfo("com.rosan.dhizuku", 0); return true; }
        catch (Exception e) { return false; }
    }

    public static boolean init(Context c) {
        try { return Dhizuku.init(c); } catch (Throwable t) { return false; }
    }

    public static boolean isGranted() {
        try { return Dhizuku.isPermissionGranted(); } catch (Throwable t) { return false; }
    }

    public static void requestPermission(final Callback cb) {
        try {
            Dhizuku.requestPermission(new DhizukuRequestPermissionListener() {
                @Override public void onRequestPermission(int grantResult) {
                    cb.onResult(grantResult == PackageManager.PERMISSION_GRANTED);
                }
            });
        } catch (Throwable t) { cb.onResult(false); }
    }

    /** Hide (soft-disable) or unhide a package as Device Owner. */
    public static boolean setApplicationHidden(Context c, String pkg, boolean hidden) {
        try {
            if (!init(c) || !isGranted()) return false;
            HiddenApiBypass.addHiddenApiExemptions("L");
            IBinder raw = (IBinder) Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class).invoke(null, "device_policy");
            IBinder wrapped = Dhizuku.binderWrapper(raw);
            Object dpm = Class.forName("android.app.admin.IDevicePolicyManager$Stub")
                    .getMethod("asInterface", IBinder.class).invoke(null, wrapped);
            ComponentName admin = Dhizuku.getOwnerComponent();
            String caller = admin.getPackageName();
            for (Method m : dpm.getClass().getMethods()) {
                if (!m.getName().equals("setApplicationHidden")) continue;
                int n = m.getParameterTypes().length;
                Object r = null;
                if (n == 4) r = m.invoke(dpm, admin, caller, pkg, hidden);
                else if (n == 5) r = m.invoke(dpm, admin, caller, pkg, hidden, false);
                else continue;
                return r instanceof Boolean && (Boolean) r;
            }
        } catch (Throwable t) {
            DaemonState.addLog("dhizuku: " + t.getClass().getSimpleName());
        }
        return false;
    }
}
