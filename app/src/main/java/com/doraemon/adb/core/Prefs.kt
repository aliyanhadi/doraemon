package com.doraemon.adb.core

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("doraemon", Context.MODE_PRIVATE)
    @JvmStatic fun port(c: Context): Int = sp(c).getInt("port", 0)
    @JvmStatic fun setPort(c: Context, p: Int) = sp(c).edit().putInt("port", p).apply()
    @JvmStatic fun autoStart(c: Context): Boolean = sp(c).getBoolean("autostart", true)
    @JvmStatic fun setAutoStart(c: Context, b: Boolean) = sp(c).edit().putBoolean("autostart", b).apply()
}
