package com.acolello.sunsetwatcher

import android.content.Context
import android.location.Location
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

private const val PERIODIC_UPDATE_WORK = "periodic_sunset_update"
private const val UPDATE_WORK = "sunset_update"

// How often the sunset times are refreshed when the location hasn't changed. The cache holds
// yesterday through tomorrow, so this only needs to run about once a day; running more often
// leaves slack for when the system defers it.
private const val UPDATE_INTERVAL_HOURS = 6L

private val networkConstraint = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()

// WorkManager persists across reboots and app restarts, so this keeps the sunset times fresh
// without the app having to stay open.
fun schedulePeriodicUpdates(context: Context) {
    val request = PeriodicWorkRequestBuilder<SunsetUpdateWorker>(UPDATE_INTERVAL_HOURS, TimeUnit.HOURS)
        .setConstraints(networkConstraint)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        PERIODIC_UPDATE_WORK,
        ExistingPeriodicWorkPolicy.UPDATE,
        request
    )
}

// Refreshes the sunset times as soon as there is a network connection. If [location] is
// given it's used instead of looking up the current location.
fun updateSunsetTimesNow(context: Context, location: Location? = null) {
    val builder = OneTimeWorkRequestBuilder<SunsetUpdateWorker>()
        .setConstraints(networkConstraint)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)

    if (location != null) {
        builder.setInputData(workDataOf(KEY_LATITUDE to location.latitude, KEY_LONGITUDE to location.longitude))
    }

    WorkManager.getInstance(context).enqueueUniqueWork(UPDATE_WORK, ExistingWorkPolicy.REPLACE, builder.build())
}
