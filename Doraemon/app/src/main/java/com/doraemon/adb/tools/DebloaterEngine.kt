package com.doraemon.adb.tools

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.doraemon.adb.core.AdbManager
import com.doraemon.adb.core.DhizukuBridge
import com.doraemon.adb.core.Shell
import com.doraemon.adb.core.ShellResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class PkgState { ACTIVE, DISABLED, REMOVED }

data class PkgItem(
    val pkg: String,
    val label: String,
    val system: Boolean,
    val state: PkgState,
    val protectedPkg: Boolean,
)

/** Canta-style debloater. Uninstalls are per-user (`--user 0`) and fully reversible via Restore. */
object DebloaterEngine {
    private val pkgRegex = Regex("^[A-Za-z0-9._]+$")

    private val protectedExact = setOf(
        "android", "com.android.systemui", "com.android.settings", "com.android.phone",
        "com.android.server.telecom", "com.android.shell", "com.android.vending",
        "com.android.permissioncontroller", "com.android.packageinstaller", "com.android.bluetooth",
        "com.android.nfc", "com.android.location.fused", "com.android.webview",
        "com.google.android.webview", "com.google.android.gms", "com.google.android.gsf",
        "com.google.android.packageinstaller", "com.google.android.permissioncontroller",
        "com.android.inputdevices", "com.android.keychain", "com.android.certinstaller",
        "com.android.documentsui", "com.android.externalstorage", "com.android.mtp",
        "com.android.networkstack", "com.android.captiveportallogin", "com.android.emergency",
        "com.android.launcher3", "com.android.dynsystem", "com.android.managedprovisioning",
        "com.rosan.dhizuku", "com.doraemon.adb",
    )
    private val protectedPrefix = listOf(
        "com.android.providers.", "com.android.internal.", "com.android.cellbroadcast",
        "com.android.networkstack", "com.android.wifi", "com.android.ims", "com.android.se",
        "com.android.cts", "com.qualcomm.qti.telephony", "com.mediatek.ims",
    )

    fun isProtected(p: String) = p in protectedExact || protectedPrefix.any { p.startsWith(it) }

    suspend fun list(ctx: Context): List<PkgItem> = withContext(Dispatchers.IO) {
        val pm = ctx.packageManager
        @Suppress("DEPRECATION")
        pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
            .map { ai ->
                val removed = ai.flags and ApplicationInfo.FLAG_INSTALLED == 0
                PkgItem(
                    pkg = ai.packageName,
                    label = runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(ai.packageName),
                    system = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    state = when { removed -> PkgState.REMOVED; !ai.enabled -> PkgState.DISABLED; else -> PkgState.ACTIVE },
                    protectedPkg = isProtected(ai.packageName),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private suspend fun guard(p: String, block: suspend () -> ShellResult): ShellResult = when {
        !pkgRegex.matches(p) -> ShellResult("Invalid package name.", -2)
        isProtected(p) -> ShellResult("Blocked: $p is a protected system component.", -2)
        else -> withContext(Dispatchers.IO) { block() }
    }

    suspend fun disable(ctx: Context, p: String) = guard(p) {
        if (!AdbManager.get(ctx).isConnected && DhizukuBridge.setApplicationHidden(ctx, p, true))
            ShellResult("Hidden via Dhizuku", 0)
        else Shell.exec(ctx, "pm disable-user --user 0 $p")
    }

    suspend fun uninstall(ctx: Context, p: String) = guard(p) {
        Shell.exec(ctx, "pm uninstall -k --user 0 $p")
    }

    suspend fun restore(ctx: Context, p: String): ShellResult = withContext(Dispatchers.IO) {
        restoreBlocking(ctx, p)
    }

    private suspend fun restoreBlocking(ctx: Context, p: String): ShellResult {
        if (!pkgRegex.matches(p)) return ShellResult("Invalid package name.", -2)
        if (!AdbManager.get(ctx).isConnected && DhizukuBridge.setApplicationHidden(ctx, p, false))
            return ShellResult("Unhidden via Dhizuku", 0)
        val a = Shell.exec(ctx, "cmd package install-existing $p")
        val b = Shell.exec(ctx, "pm enable $p")
        return ShellResult((a.out + "\n" + b.out).trim(), if (a.ok || b.ok) 0 else a.code)
    }
}
