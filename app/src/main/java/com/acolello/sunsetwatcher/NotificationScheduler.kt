package com.acolello.sunsetwatcher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Date
import java.util.concurrent.TimeUnit

private val DAY_MILLIS = TimeUnit.DAYS.toMillis(1)

fun epochToString(millis: Long): String{
    val sunsetTime = Date(millis)
    return sunsetTime.toString()
}

fun calculateNotificationTime(sunsetTime: Long, offsetTime: Long): Long{
    val offsetMillis: Long = offsetTime * 1000
    return sunsetTime + offsetMillis
}

// Picks the sunset whose notification ([offsetSeconds] from it) is the next one after [now].
// Unknown sunsets are negative. Returns null if no sunset times are known at all.
fun chooseSunsetTime(sunsets: List<Long>, offsetSeconds: Long, now: Long): Long? {
    val known = sunsets.filter { it > 0 }.sorted()
    if (known.isEmpty()) return null

    known.firstOrNull { calculateNotificationTime(it, offsetSeconds) > now }?.let { return it }

    // Every cached sunset has passed, e.g. there's been no network for a couple of days.
    // Sunset only moves a few minutes from one day to the next, so shifting the latest one
    // forward by whole days is close enough until the cache is refreshed.
    val latest = known.last()
    val behind = now - calculateNotificationTime(latest, offsetSeconds)
    return latest + (behind / DAY_MILLIS + 1) * DAY_MILLIS
}

private fun getSunsetTimes(context: Context): List<Long> =
    listOf(
        R.string.sunset_yesterday_pref_name,
        R.string.sunset_today_pref_name,
        R.string.sunset_tomorrow_pref_name
    ).map { getSunsetTime(context, context.getString(it)) }

fun getBestSunsetTime(context: Context): Long? =
    chooseSunsetTime(getSunsetTimes(context), getOffset(context), System.currentTimeMillis())

// The next sunset, or the one happening right now.
fun getUpcomingSunsetTime(context: Context): Long? =
    chooseSunsetTime(getSunsetTimes(context), 0, System.currentTimeMillis())

fun getNotificationTime(context: Context): Long? {
    val sunsetTime = getBestSunsetTime(context) ?: return null
    return calculateNotificationTime(sunsetTime, getOffset(context))
}

fun updateNotificationAlarm(context: Context){
    val alarmMgr: AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val notificationIntent = Intent(context, NotificationAlarmReceiver::class.java).let { intent ->
        PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    alarmMgr.cancel(notificationIntent)

    val notificationTime = getNotificationTime(context)
    if (notificationTime == null) {
        Log.w("alarm", "No sunset times yet, not scheduling a notification")
        return
    }
    Log.d("alarm", "Scheduling notification for ${epochToString(notificationTime)}")

    // Exact alarms need the user to allow "Alarms & reminders" on Android 12+. Fall back to an
    // inexact alarm, which may arrive a few minutes late, if they haven't.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmMgr.canScheduleExactAlarms()) {
        alarmMgr.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            notificationTime,
            notificationIntent
        )
    } else {
        alarmMgr.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            notificationTime,
            notificationIntent
        )
    }
}
