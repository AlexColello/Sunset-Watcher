package com.acolello.sunsetwatcher

import android.app.Application

class SunsetWatcherApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(this)
    }
}
