package com.doraemon.adb.tools

import android.content.Context
import com.doraemon.adb.core.Backend
import com.doraemon.adb.core.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun gov(g: String) =
    "for f in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo $g > \$f; done"

private fun anim(s: String) = listOf(
    "settings put global window_animation_scale $s",
    "settings put global transition_animation_scale $s",
    "settings put global animator_duration_scale $s",
)

/** Gamma-style profile switcher. ADB commands work everywhere; governor writes only apply when root is present. */
enum class Profile(val label: String, val adb: List<String>, val root: List<String>) {
    ECO("Eco", listOf("settings put global low_power 1") + anim("0.5") +
        "cmd power set-fixed-performance-mode-enabled false", listOf(gov("powersave"))),
    BALANCED("Balanced", listOf("settings put global low_power 0") + anim("1") +
        "cmd power set-fixed-performance-mode-enabled false", listOf(gov("schedutil"))),
    TURBO("Turbo", listOf("settings put global low_power 0") + anim("0.5") +
        "cmd power set-fixed-performance-mode-enabled true", listOf(gov("performance"))),
}

object PerformanceEngine {
    suspend fun apply(ctx: Context, p: Profile): String {
        val sb = StringBuilder("Profile: ${p.label}\n")
        p.adb.forEach { c ->
            val r = Shell.exec(ctx, c)
            sb.append(if (r.ok) "ok   " else "skip ").append(c.take(60)).append('\n')
        }
        if (withContext(Dispatchers.IO) { Shell.hasRoot() }) p.root.forEach { c ->
            sb.append(if (Shell.exec(ctx, c, Backend.ROOT).ok) "root ok  governor\n" else "root fail governor\n")
        } else sb.append("(no root: CPU governor untouched)\n")
        return sb.toString()
    }

    suspend fun stats(ctx: Context): String {
        val bat = Shell.exec(ctx, "dumpsys battery").out
        val temp = Regex("temperature:\\s*(\\d+)").find(bat)?.groupValues?.get(1)?.toFloatOrNull()?.div(10)
        val level = Regex("level:\\s*(\\d+)").find(bat)?.groupValues?.get(1)
        val load = Shell.exec(ctx, "cat /proc/loadavg").out
        return "Battery: ${level ?: "?"}%  Temp: ${temp ?: "?"} °C\nLoad avg: $load"
    }
}
