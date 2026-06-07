package com.gomoku.app.util

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private lateinit var sp: SharedPreferences

    // 默认服务器地址 (硬编码, 首次安装自动使用)
    const val DEFAULT_HOST: String = "35.212.227.58:8080"

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences("gomoku_prefs", Context.MODE_PRIVATE)
        // 首次安装, 如果用户没设置过, 自动填入默认地址
        if (!sp.contains("server_host")) {
            sp.edit().putString("server_host", DEFAULT_HOST).apply()
        }
    }

    var nickname: String
        get() = sp.getString("nickname", "") ?: ""
        set(value) = sp.edit().putString("nickname", value).apply()

    var serverHost: String
        get() = sp.getString("server_host", "") ?: ""
        set(value) = sp.edit().putString("server_host", value).apply()

    var defaultTimeLimit: Int
        get() = sp.getInt("default_time_limit", 0)
        set(value) = sp.edit().putInt("default_time_limit", value).apply()

    fun httpBase(): String {
        val h = serverHost.trim()
        return if (h.startsWith("http://") || h.startsWith("https://")) h else "http://$h"
    }

    fun wsBase(): String {
        val h = serverHost.trim()
        if (h.isEmpty()) return ""
        val stripped = h.removePrefix("http://").removePrefix("https://").removePrefix("ws://").removePrefix("wss://")
        return if (stripped.startsWith("ws://") || stripped.startsWith("wss://")) stripped else "ws://$stripped"
    }
}
