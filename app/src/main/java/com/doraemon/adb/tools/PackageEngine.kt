package com.doraemon.adb.tools

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.Uri
import android.provider.OpenableColumns
import com.doraemon.adb.core.AdbManager
import com.doraemon.adb.core.Shell
import com.doraemon.adb.core.ShellResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PermItem(val name: String, val granted: Boolean, val dangerous: Boolean)

/** App-Manager-style inspector with per-scope permission grant/revoke and a split-APK installer. */
object PackageEngine {
    private val safe = Regex("^[A-Za-z0-9._]+$")
    val appOps = listOf(
        "SYSTEM_ALERT_WINDOW", "WRITE_SETTINGS", "REQUEST_INSTALL_PACKAGES",
        "GET_USAGE_STATS", "RUN_IN_BACKGROUND", "MANAGE_EXTERNAL_STORAGE",
    )

    @Suppress("DEPRECATION")
    suspend fun requestedPerms(ctx: Context, pkg: String): List<PermItem> = withContext(Dispatchers.IO) {
        val pm = ctx.packageManager
        val pi = runCatching { pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS) }.getOrNull()
            ?: return@withContext emptyList()
        val req = pi.requestedPermissions ?: return@withContext emptyList()
        val flags = pi.requestedPermissionsFlags ?: IntArray(req.size)
        req.mapIndexed { i, n ->
            val dangerous = runCatching {
                pm.getPermissionInfo(n, 0).protectionLevel and PermissionInfo.PROTECTION_MASK_BASE ==
                    PermissionInfo.PROTECTION_DANGEROUS
            }.getOrDefault(false)
            PermItem(n, flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0, dangerous)
        }.sortedWith(compareByDescending<PermItem> { it.dangerous }.thenBy { it.name })
    }

    suspend fun setPermission(ctx: Context, pkg: String, perm: String, grant: Boolean): ShellResult {
        if (!safe.matches(pkg) || !safe.matches(perm)) return ShellResult("Invalid name.", -2)
        return Shell.exec(ctx, "pm ${if (grant) "grant" else "revoke"} $pkg $perm")
    }

    suspend fun setAppOp(ctx: Context, pkg: String, op: String, allow: Boolean): ShellResult {
        if (!safe.matches(pkg) || op !in appOps) return ShellResult("Invalid name.", -2)
        return Shell.exec(ctx, "appops set $pkg $op ${if (allow) "allow" else "deny"}")
    }

    suspend fun appOpMode(ctx: Context, pkg: String, op: String): String {
        if (!safe.matches(pkg)) return "?"
        return Shell.exec(ctx, "appops get $pkg $op").out.substringAfter(":", "?").trim().ifEmpty { "default" }
    }

    private fun sizeOf(ctx: Context, uri: Uri): Long =
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst()) it.getLong(0) else -1L
        } ?: -1L

    /** Streams one or more APKs (base + splits) into a single install session over ADB. */
    suspend fun installApks(ctx: Context, uris: List<Uri>): ShellResult = withContext(Dispatchers.IO) {
        val adb = AdbManager.get(ctx)
        if (!adb.isConnected) return@withContext ShellResult("ADB connection required for installs.", -1)
        val create = Shell.execBlocking(ctx, "pm install-create")
        val sid = Regex("\\[(\\d+)]").find(create.out)?.groupValues?.get(1)
            ?: return@withContext create
        try {
            uris.forEachIndexed { i, u ->
                val size = sizeOf(ctx, u)
                if (size <= 0) return@withContext ShellResult("Cannot read size of file #${i + 1}", -1)
                adb.openStream("exec:cmd package install-write -S $size $sid split_$i.apk -").use { s ->
                    val out = s.openOutputStream()
                    ctx.contentResolver.openInputStream(u)!!.use { it.copyTo(out) }
                    out.flush()
                    s.openInputStream().bufferedReader().readText()
                }
            }
            Shell.execBlocking(ctx, "pm install-commit $sid")
        } catch (e: Exception) {
            Shell.execBlocking(ctx, "pm install-abandon $sid")
            ShellResult("Install failed: ${e.message}", -1)
        }
    }
}
