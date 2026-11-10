package com.doraemon.adb.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ShellResult(val out: String, val code: Int) {
    val ok get() = code == 0
}

enum class Backend { AUTO, ADB, ROOT }

/** Unified command executor: wireless/USB ADB shell first, `su` as the pseudo-root fallback. */
object Shell {
    @Volatile var preferred = Backend.AUTO
    @Volatile private var rootCache: Boolean? = null

    fun quote(s: String) = "'" + s.replace("'", "'\\''") + "'"

    @JvmStatic fun hasRoot(): Boolean = rootCache ?: viaRoot("id").ok.also { rootCache = it }

    @JvmStatic fun execBlocking(ctx: Context, cmd: String, force: Backend? = null): ShellResult {
        val adb = AdbManager.get(ctx)
        val use = force ?: preferred
        val useAdb = when (use) {
            Backend.ADB -> true
            Backend.ROOT -> false
            Backend.AUTO -> adb.isConnected
        }
        return if (useAdb) viaAdb(adb, cmd) else viaRoot(cmd)
    }

    suspend fun exec(ctx: Context, cmd: String, force: Backend? = null): ShellResult =
        withContext(Dispatchers.IO) { execBlocking(ctx, cmd, force) }

    private fun viaAdb(adb: AdbManager, cmd: String): ShellResult {
        if (!adb.isConnected) return ShellResult("ADB not connected. Open Pair & Connect first.", -1)
        return try {
            adb.openStream("shell:$cmd; echo __RC=\$?").use { s ->
                val txt = s.openInputStream().bufferedReader().readText()
                val m = Regex("__RC=(\\d+)\\s*$").find(txt)
                ShellResult(txt.removeRange(m?.range ?: IntRange.EMPTY).trimEnd(), m?.groupValues?.get(1)?.toInt() ?: 0)
            }
        } catch (e: Exception) {
            ShellResult("ADB error: ${e.message}", -1)
        }
    }

    private fun viaRoot(cmd: String): ShellResult = try {
        val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        ShellResult(out.trimEnd(), p.waitFor())
    } catch (e: Exception) {
        ShellResult("No ADB connection and no root available.", -1)
    }
}
