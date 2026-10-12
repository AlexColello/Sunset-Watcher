package com.acolello.sunsetwatcher

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.concurrent.TimeUnit

// How far the user has to move before the sunset times are looked up again. Sunset moves by
// about 4 minutes per degree of longitude, so at mid latitudes 10 km is roughly 30 seconds.
const val SIGNIFICANT_DISTANCE_METERS = 10_000f

// Low power (city level, from wifi and cell towers) is all that's needed to notice the user
// moving between towns, and it uses very little battery.
private val locationRequest = LocationRequest.Builder(Priority.PRIORITY_LOW_POWER, TimeUnit.MINUTES.toMillis(30))
    .setMinUpdateIntervalMillis(TimeUnit.MINUTES.toMillis(10))
    .setMinUpdateDistanceMeters(SIGNIFICANT_DISTANCE_METERS / 4)
    .build()

// Asks for location updates to be delivered to [LocationUpdateReceiver], even while the app
// isn't running. Updates only arrive in the background if the user allowed background location.
fun startLocationTracking(context: Context) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        != PackageManager.PERMISSION_GRANTED) {
        return
    }

    // Must be mutable so that location services can add the location to the intent.
    val intent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, LocationUpdateReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )

    LocationServices.getFusedLocationProviderClient(context)
        .requestLocationUpdates(locationRequest, intent)
        .addOnFailureListener { Log.e("location", "Failed to request location updates", it) }
}

// Whether [location] is far enough from where the saved sunset times are for to look them up again.
fun movedSignificantly(context: Context, location: Location): Boolean {
    val saved = getSavedLocation(context) ?: return true
    return saved.distanceTo(location) > SIGNIFICANT_DISTANCE_METERS
}

class LocationUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val location = LocationResult.extractResult(intent)?.lastLocation ?: return

        if (movedSignificantly(context, location)) {
            Log.d("location", "Moved to ${location.latitude}, ${location.longitude}, updating sunset times")
            updateSunsetTimesNow(context, location)
        }
    }
}
