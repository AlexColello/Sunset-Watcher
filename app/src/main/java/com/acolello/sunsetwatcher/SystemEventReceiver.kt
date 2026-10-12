package com.acolello.sunsetwatcher

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

// Restores everything that doesn't survive a reboot or app update, and reacts to changes that
// affect when the notification should go off.
class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("system", "Received ${intent.action}")

        when (intent.action) {
            // Alarms and location requests are cleared on reboot. WorkManager reschedules itself.
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                startLocationTracking(context)
                schedulePeriodicUpdates(context)
                updateNotificationAlarm(context)
            }
            // A new time zone usually means the user travelled somewhere.
            Intent.ACTION_TIMEZONE_CHANGED -> {
                updateNotificationAlarm(context)
                updateSunsetTimesNow(context)
            }
            // Reschedule, which also switches between exact and inexact alarms.
            Intent.ACTION_TIME_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                updateNotificationAlarm(context)
            }
        }
    }
}
