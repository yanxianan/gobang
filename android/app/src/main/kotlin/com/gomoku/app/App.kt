package com.gomoku.app

import android.app.Application
import com.gomoku.app.util.Prefs

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
    }
}
