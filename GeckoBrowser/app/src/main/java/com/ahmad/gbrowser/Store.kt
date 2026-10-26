package com.ahmad.gbrowser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Store(ctx: Context) {
    val prefs = ctx.getSharedPreferences("gb", Context.MODE_PRIVATE)

    data class Visit(val url: String, val title: String, val time: Long)

    // ---------- history ----------
    fun history(): List<Visit> {
        val out = ArrayList<Visit>()
        try {
            val arr = JSONArray(prefs.getString("history", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Visit(o.getString("u"), o.optString("t"), o.optLong("w")))
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun addVisit(url: String, title: String) {
        val list = history().toMutableList()
        if (list.isNotEmpty() && list[0].url == url) list.removeAt(0)
        list.add(0, Visit(url, title, System.currentTimeMillis()))
        while (list.size > 500) list.removeAt(list.size - 1)
        val arr = JSONArray()
        for (v in list) {
            arr.put(JSONObject().put("u", v.url).put("t", v.title).put("w", v.time))
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    fun clearHistory() {
        prefs.edit().remove("history").apply()
    }

    // ---------- open tabs ----------
    fun saveTabs(list: List<Pair<String, String>>, current: Int) {
        val arr = JSONArray()
        for ((u, t) in list) arr.put(JSONObject().put("u", u).put("t", t))
        prefs.edit().putString("tabs", arr.toString()).putInt("current", current).apply()
    }

    fun loadTabs(): Pair<List<Pair<String, String>>, Int> {
        val out = ArrayList<Pair<String, String>>()
        try {
            val arr = JSONArray(prefs.getString("tabs", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Pair(o.getString("u"), o.optString("t")))
            }
        } catch (e: Exception) {
        }
        return Pair(out, prefs.getInt("current", 0))
    }

    fun clearTabs() {
        prefs.edit().remove("tabs").remove("current").apply()
    }

    // ---------- settings ----------
    fun flag(key: String): Boolean = prefs.getBoolean(key, false)

    fun setFlag(key: String, v: Boolean) {
        prefs.edit().putBoolean(key, v).apply()
    }

    val engineNames = arrayOf("DuckDuckGo", "Google", "Bing")
    private val engineUrls = arrayOf(
        "https://duckduckgo.com/?q=",
        "https://www.google.com/search?q=",
        "https://www.bing.com/search?q="
    )

    fun engineIndex(): Int = prefs.getInt("engine", 0).coerceIn(0, 2)
    fun setEngine(i: Int) {
        prefs.edit().putInt("engine", i).apply()
    }

    fun searchUrl(): String = engineUrls[engineIndex()]
}
