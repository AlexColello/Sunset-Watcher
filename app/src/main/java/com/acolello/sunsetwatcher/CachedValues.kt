package com.acolello.sunsetwatcher

import android.content.Context
import androidx.preference.PreferenceManager
import java.time.OffsetDateTime

private var pOffset: Long = 0L
private var pLoaded: Boolean = false
private var pTimeMap: HashMap<String, Long> = hashMapOf()

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