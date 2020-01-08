package com.example.sunsetwatcher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.JobIntentService

const val UPDATE_JOB_ID = 1001

class UpdateAlarmReceiver: BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        Log.d("update", "In UpdateAlarmReceiver")

        JobIntentService.enqueueWork(context, UpdateService::class.java, UPDATE_JOB_ID, intent)

        resultCode = Activity.RESULT_OK
    }
}