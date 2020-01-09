package com.example.sunsetwatcher

import android.content.Context
import androidx.preference.PreferenceManager
import java.time.OffsetDateTime

private var pOffset: Long = 0L
private var pSunsetTime: Long = 0L
private var pLoaded: Boolean = false

fun loadCache(context: Context){
    pOffset = getSavedOffset(context)
    pSunsetTime = getSavedSunsetTime(context)
    pLoaded = true
}

fun setSavedSunsetTime(sunsetTime : Long, context: Context) {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context /* Activity context */)

    with (sharedPreferences.edit()) {
        putLong(context.getString(R.string.sunset_time_pref_name), sunsetTime)
        commit()
    }

    pSunsetTime = sunsetTime
}

fun setSavedSunsetTime(sunsetTime : String, context: Context): Long {

    val datetime : OffsetDateTime = OffsetDateTime.parse(sunsetTime)
    val millis = datetime.toInstant().toEpochMilli()

    setSavedSunsetTime(millis, context)

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

private fun getSavedSunsetTime(context: Context): Long {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context /* Activity context */)
    val retval = sharedPreferences.getLong(context.getString(R.string.sunset_time_pref_name), -1L)

    return retval
}

fun getOffset(context: Context): Long{
    if(!pLoaded){
        loadCache(context)
    }
    return pOffset
}

fun getSunsetTime(context: Context): Long{
    if(!pLoaded){
        loadCache(context)
    }
    return pSunsetTime
}

fun setOffset(offset: Long){
    pOffset = offset
}