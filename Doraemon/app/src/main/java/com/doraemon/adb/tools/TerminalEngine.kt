package com.doraemon.adb.tools

import android.content.Context
import com.doraemon.adb.core.Shell
import com.doraemon.adb.core.ShellResult

/** aShell-style command runner. Each command goes through the active backend (ADB shell or su). */
object TerminalEngine {
    val quick = listOf(
        "getprop ro.build.version.release",
        "pm list packages -3",
        "dumpsys battery",
        "wm size",
        "settings list global",
        "df -h /data",
    )

    suspend fun run(ctx: Context, line: String): ShellResult {
        val r = Shell.exec(ctx, line.trim())
        return r.copy(out = r.out.take(20000))
    }
}
