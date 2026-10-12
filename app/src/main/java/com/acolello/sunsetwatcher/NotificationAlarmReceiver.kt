package com.acolello.sunsetwatcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationAlarmReceiver: BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("notification", "In NotificationAlarmReceiver")

        sendSunsetNotification(context)

        // Schedule the next one.
        updateNotificationAlarm(context)
    }
}
