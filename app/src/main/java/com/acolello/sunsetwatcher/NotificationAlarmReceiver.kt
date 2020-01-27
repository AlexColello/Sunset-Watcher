package com.acolello.sunsetwatcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.Activity
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import androidx.core.app.JobIntentService

const val NOTIFICATION_JOB_ID = 1001

class NotificationAlarmReceiver: BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("notification", "In NotificationAlarmReceiver")

        //this will update the UI with message
        //val inst = instance()

        //this will sound the alarm tone
        //this will sound the alarm once, if you wish to
        //raise alarm in loop continuously then use MediaPlayer and setLooping(true)
        var alarmUri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        if (alarmUri == null) {
            alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
        val ringtone = RingtoneManager.getRingtone(context, alarmUri)
        //ringtone.play()

        JobIntentService.enqueueWork(context, NotificationService::class.java, NOTIFICATION_JOB_ID, intent)

        updateNotificationAlarm(context)

        val main = instance()
        if(main != null) {
            main.updateUI()
        } else {
            Log.d("null", "Main is null in NotificationAlarmReceiver")
        }

        resultCode = Activity.RESULT_OK
    }
}