package com.acolello.sunsetwatcher

import android.content.Context
import androidx.preference.PreferenceManager
import android.location.Location
import androidx.core.content.edit
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap

private var pOffset: Long = 0L
private var pLoaded: Boolean = false
// Written from background workers and read from the UI thread.
private val pTimeMap = ConcurrentHashMap<String, Long>()

fun loadCache(context: Context){
    pOffset = getSavedOffset(context)
    pLoaded = true

    val sunsetPreferences = listOf(
        context.getString(R.string.sunset_yesterday_pref_name),
        context.getString(R.string.sunset_today_pref_name),
        context.getString(R.string.sunset_tomorrow_pref_name)
    )

    for(day in sunsetPreferences){
        pTimeMap[day] = getSavedSunsetTime(context, day)
    }
}

fun setSavedSunsetTime(sunsetTime : Long, context: Context, name: String) {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context /* Activity context */)

    with (sharedPreferences.edit()) {
        putLong(name, sunsetTime)
        commit()
    }

    pTimeMap[name] = sunsetTime

}

fun setSavedSunsetTime(sunsetTime : String, context: Context, name: String): Long {

    val datetime: OffsetDateTime = OffsetDateTime.parse(sunsetTime)
    val millis: Long = datetime.toInstant().toEpochMilli()

    setSavedSunsetTime(millis, context, name)

    return millis
}

private fun getSavedOffset(context: Context): Long {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context /* Activity context */)
    val defaultValue = sharedPreferences.getLong(context.getString(R.string.offset_pref_name), 0L)

    return defaultValue
}

fun setSavedOffset(offset : Long, context: Context) {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context /* Activity context */)

    with (sharedPreferences.edit()) {
        putLong(context.getString(R.string.offset_pref_name), offset)
        commit()
    }

    pOffset = offset
}

private fun getSavedSunsetTime(context: Context, name: String): Long {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context /* Activity context */)
    val retval = sharedPreferences.getLong(name, -1L)

    return retval
}

fun getOffset(context: Context): Long{
    if(!pLoaded){
        loadCache(context)
    }
    return pOffset
}

fun getSunsetTime(context: Context, name: String): Long{
    if(!pLoaded){
        loadCache(context)
    }
    return pTimeMap[name]!!
}

fun setOffset(offset: Long){
    pOffset = offset
}

private const val LATITUDE_PREF = "SunsetLatitude"
private const val LONGITUDE_PREF = "SunsetLongitude"

// The location the saved sunset times were looked up for.
fun getSavedLocation(context: Context): Location? {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    if (!sharedPreferences.contains(LATITUDE_PREF)) return null

    return Location("saved").apply {
        latitude = Double.fromBits(sharedPreferences.getLong(LATITUDE_PREF, 0L))
        longitude = Double.fromBits(sharedPreferences.getLong(LONGITUDE_PREF, 0L))
    }
}

fun setSavedLocation(context: Context, latitude: Double, longitude: Double) {
    PreferenceManager.getDefaultSharedPreferences(context).edit {
        putLong(LATITUDE_PREF, latitude.toRawBits())
        putLong(LONGITUDE_PREF, longitude.toRawBits())
    }
}
