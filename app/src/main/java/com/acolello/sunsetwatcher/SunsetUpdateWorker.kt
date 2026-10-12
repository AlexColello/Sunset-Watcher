package com.acolello.sunsetwatcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import org.json.JSONException
import java.io.IOException
import java.time.LocalDate

const val KEY_LATITUDE = "latitude"
const val KEY_LONGITUDE = "longitude"

// Looks up the sunset times around today for the current location and reschedules the
// notification. Runs periodically and whenever the location changes significantly.
class SunsetUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e("update", "Location permission not granted")
            return Result.success()
        }

        // Location requests are dropped when Play services updates, so renew it every run.
        startLocationTracking(context)

        val location = inputLocation() ?: findLocation(context) ?: getSavedLocation(context)
        if (location == null) {
            Log.e("location", "No location available")
            return Result.retry()
        }
        Log.d("location", "Latitude: ${location.latitude} Longitude: ${location.longitude}")

        val today = LocalDate.now()
        val days = listOf(
            today.minusDays(1) to R.string.sunset_yesterday_pref_name,
            today to R.string.sunset_today_pref_name,
            today.plusDays(1) to R.string.sunset_tomorrow_pref_name
        )

        val sunsets = try {
            coroutineScope {
                days.map { (date, _) ->
                    async { fetchSunsetTime(location.latitude, location.longitude, date) }
                }.awaitAll()
            }
        } catch (e: IOException) {
            Log.e("request", "Failed to get sunset times", e)
            return Result.retry()
        } catch (e: JSONException) {
            Log.e("request", "Failed to parse sunset times", e)
            return Result.retry()
        }

        for ((day, sunset) in days.zip(sunsets)) {
            Log.d("request", "Found sunset time $sunset for ${day.first}")
            setSavedSunsetTime(sunset, context, context.getString(day.second))
        }
        setSavedLocation(context, location.latitude, location.longitude)

        updateNotificationAlarm(context)
        return Result.success()
    }

    private fun inputLocation(): Location? {
        if (!inputData.hasKeyWithValueOfType(KEY_LATITUDE, Double::class.javaObjectType)) return null
        return Location("input").apply {
            latitude = inputData.getDouble(KEY_LATITUDE, 0.0)
            longitude = inputData.getDouble(KEY_LONGITUDE, 0.0)
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    private suspend fun findLocation(context: Context): Location? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        return try {
            // The last known location is null if nothing on the device has asked for a
            // location recently (e.g. after a reboot), so ask for a fresh one instead.
            client.lastLocation.await()
                ?: client.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    CancellationTokenSource().token
                ).await()
        } catch (e: CancellationException) {
            // The work was replaced or stopped, let WorkManager handle it.
            throw e
        } catch (e: Exception) {
            // e.g. a SecurityException when in the background without background location.
            Log.w("location", "Could not get location", e)
            null
        }
    }
}
