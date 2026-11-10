package com.doraemon.adb.tools

import android.content.Context
import com.doraemon.adb.core.Shell
import com.doraemon.adb.core.ShellResult

/** Hex-style system theming through `cmd overlay`, `cmd uimode`, `settings` and `wm`. */
object CustomizationEngine {
    private val safe = Regex("^[A-Za-z0-9._]+$")
    private val line = Regex("^\\[(.)]\\s+(\\S+)")

    suspend fun overlays(ctx: Context): List<Pair<String, Boolean>> =
        Shell.exec(ctx, "cmd overlay list").out.lines().mapNotNull {
            line.find(it.trim())?.let { m -> m.groupValues[2] to (m.groupValues[1] == "x") }
        }

    suspend fun setOverlay(ctx: Context, pkg: String, on: Boolean): ShellResult =
        if (!safe.matches(pkg)) ShellResult("Invalid overlay name.", -2)
        else Shell.exec(ctx, "cmd overlay ${if (on) "enable" else "disable"} --user 0 $pkg")

    suspend fun nightMode(ctx: Context, mode: String) =
        if (mode in setOf("yes", "no", "auto")) Shell.exec(ctx, "cmd uimode night $mode")
        else ShellResult("bad mode", -2)

    suspend fun fontScale(ctx: Context, s: Float) =
        Shell.exec(ctx, "settings put system font_scale ${s.coerceIn(0.7f, 1.6f)}")

    suspend fun density(ctx: Context, dpi: Int?) =
        Shell.exec(ctx, if (dpi == null) "wm density reset" else "wm density ${dpi.coerceIn(200, 800)}")

    suspend fun navMode(ctx: Context, mode: String) =
        if (mode in setOf("gestural", "threebutton", "twobutton"))
            Shell.exec(ctx, "cmd overlay enable com.android.internal.systemui.navbar.$mode")
        else ShellResult("bad mode", -2)
}
