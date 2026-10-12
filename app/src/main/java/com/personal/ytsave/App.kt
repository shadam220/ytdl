package com.personal.ytsave

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Progress of active downloads" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        Downloader.init(this)
    }

    companion object {
        lateinit var instance: App
            private set
        const val CHANNEL_ID = "downloads"
    }
}
