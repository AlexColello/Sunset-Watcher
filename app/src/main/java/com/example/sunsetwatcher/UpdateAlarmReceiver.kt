package com.example.sunsetwatcher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.JobIntentService

class UpdateAlarmReceiver: BroadcastReceiver() {

    val UPDATE_JOB_ID = 1001

    override fun onReceive(context: Context, intent: Intent) {

        JobIntentService.enqueueWork(context, UpdateService::class.java, UPDATE_JOB_ID, intent)

        resultCode = Activity.RESULT_OK
    }
}