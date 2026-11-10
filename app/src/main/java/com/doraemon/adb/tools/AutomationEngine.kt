package com.doraemon.adb.tools

import android.content.Context
import com.doraemon.adb.core.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Script(val id: String, val name: String, val body: String, val onConnect: Boolean)

/** Geto-style shell macros. One command per line; `# comment` and `sleep <seconds>` are supported. */
object AutomationEngine {
    private fun sp(c: Context) = c.getSharedPreferences("doraemon_scripts", Context.MODE_PRIVATE)

    fun load(ctx: Context): List<Script> {
        val arr = JSONArray(sp(ctx).getString("s", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Script(o.getString("id"), o.getString("name"), o.getString("body"), o.optBoolean("onConnect"))
        }
    }

    private fun save(ctx: Context, list: List<Script>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("body", it.body).put("onConnect", it.onConnect))
        }
        sp(ctx).edit().putString("s", arr.toString()).apply()
    }

    fun upsert(ctx: Context, s: Script) = save(ctx, load(ctx).filter { it.id != s.id } + s)
    fun delete(ctx: Context, id: String) = save(ctx, load(ctx).filter { it.id != id })
    fun newId() = UUID.randomUUID().toString()

    @JvmStatic fun runScriptBlocking(ctx: Context, s: Script): String {
        val sb = StringBuilder()
        s.body.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { line ->
            if (line.startsWith("sleep ")) {
                Thread.sleep(((line.removePrefix("sleep ").toDoubleOrNull() ?: 1.0) * 1000).toLong())
            } else {
                sb.append("$ ").append(line).append('\n').append(Shell.execBlocking(ctx, line).out).append('\n')
            }
        }
        return sb.toString().trimEnd()
    }

    suspend fun run(ctx: Context, s: Script): String = withContext(Dispatchers.IO) { runScriptBlocking(ctx, s) }

    @JvmStatic fun runOnConnectBlocking(ctx: Context) {
        load(ctx).filter { it.onConnect }.forEach { runScriptBlocking(ctx, it) }
    }
}
