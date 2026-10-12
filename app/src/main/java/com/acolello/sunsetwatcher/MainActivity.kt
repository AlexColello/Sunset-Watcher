package com.acolello.sunsetwatcher

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import android.graphics.Color
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.util.Log
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.acolello.sunsetui.SunsetSceneView
import java.util.Date
import kotlin.math.roundToInt


class MainActivity : AppCompatActivity() {

    private lateinit var scene: SunsetSceneView
    private lateinit var offsetText: TextView
    private lateinit var offsetCaption: TextView
    private lateinit var reminderText: TextView

    // Location and notifications are requested together. Background location has to be
    // requested on its own afterwards, Android 11+ ignores it when bundled with the others.
    private val foregroundPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                Log.d("permissions", "Permission granted for ACCESS_COARSE_LOCATION.")
                startUpdates()
                requestBackgroundLocation()
            } else {
                Log.e("permissions", "Permission denied for ACCESS_COARSE_LOCATION.")
                showLocationRequiredDialog()
            }
        }

    private val backgroundPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d("permissions", "ACCESS_BACKGROUND_LOCATION granted: $granted")
            // Re-register so updates start arriving while the app is closed.
            if (granted) startLocationTracking(this)
            requestExactAlarms()
        }

    // Redraw when the background worker saves new sunset times. Kept in a field because
    // SharedPreferences only holds a weak reference to its listeners.
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> updateUI() }

    override fun onStart(){
        super.onStart()
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(preferenceListener)
        updateUI()
    }

    override fun onStop() {
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(preferenceListener)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw the sky behind the status and navigation bars, with light icons over it.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContentView(R.layout.activity_main)

        scene = findViewById(R.id.sunset_scene)
        offsetText = findViewById(R.id.offset_text)
        offsetCaption = findViewById(R.id.offset_caption)
        reminderText = findViewById(R.id.reminder_text)

        loadCache(this)
        scene.setOffset((getOffset(this) / 60.0).roundToInt())
        scene.onOffsetChanged = { minutes, settled ->
            setOffset(minutes * 60L)
            updateText()
            if (settled) {
                setSavedOffset(minutes * 60L, this)
                updateNotificationAlarm(this)
            }
        }
        updateUI()

        requestForegroundPermissions()
    }

    private fun startUpdates() {
        schedulePeriodicUpdates(this)
        updateSunsetTimesNow(this)
        startLocationTracking(this)
    }

    fun updateUI() {
        scene.elevationAt = sunElevationModel()
        updateText()
    }

    // The sun's elevation at each offset from the upcoming sunset where the user is. Shifted so
    // it's exactly SUNSET_ELEVATION_DEGREES at the sunset time from the API, which can differ by
    // a fraction of a degree, so the sun always touches the horizon at "At sunset".
    private fun sunElevationModel(): ((Float) -> Double)? {
        val location = getSavedLocation(this) ?: return null
        val sunset = getUpcomingSunsetTime(this) ?: return null
        val elevationAt = { time: Long -> solarElevation(time, location.latitude, location.longitude) }
        val correction = SUNSET_ELEVATION_DEGREES - elevationAt(sunset)
        return { minutes -> elevationAt(sunset + (minutes * 60_000).toLong()) + correction }
    }

    private fun updateText() {
        val minutes = (getOffset(this) / 60.0).roundToInt()

        offsetText.text = if (minutes == 0) getString(R.string.at_sunset) else formatDuration(minutes)
        offsetCaption.text = when {
            minutes < 0 -> getString(R.string.before_sunset)
            minutes > 0 -> getString(R.string.after_sunset)
            else -> getString(R.string.sun_on_horizon)
        }

        val notificationTime = getNotificationTime(this)
        reminderText.text = if (notificationTime == null) {
            getString(R.string.finding_sunset)
        } else {
            val time = DateFormat.getTimeFormat(this).format(Date(notificationTime))
            when {
                DateUtils.isToday(notificationTime) -> getString(R.string.reminder_today, time)
                DateUtils.isToday(notificationTime - DateUtils.DAY_IN_MILLIS) -> getString(R.string.reminder_tomorrow, time)
                else -> getString(R.string.reminder_on, DateUtils.formatDateTime(this, notificationTime,
                    DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_TIME))
            }
        }

        scene.contentDescription = listOf(offsetText.text, offsetCaption.text, reminderText.text)
            .joinToString(", ")
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestForegroundPermissions() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter { !hasPermission(it) }
        if (missing.isEmpty()) {
            startUpdates()
            requestBackgroundLocation()
        } else {
            Log.d("permissions", "Requesting $missing")
            foregroundPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ||
            askedBefore(R.string.asked_background_location_pref_name)) {
            requestExactAlarms()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.background_location_title)
            .setMessage(R.string.background_location_message)
            .setPositiveButton(R.string.continue_button) { _, _ ->
                backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            .setNegativeButton(R.string.not_now_button) { _, _ -> requestExactAlarms() }
            .setCancelable(false)
            .show()
    }

    private fun requestExactAlarms() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val alarmMgr = getSystemService(AlarmManager::class.java)
        if (alarmMgr.canScheduleExactAlarms() || askedBefore(R.string.asked_exact_alarm_pref_name)) return

        AlertDialog.Builder(this)
            .setTitle(R.string.exact_alarm_title)
            .setMessage(R.string.exact_alarm_message)
            .setPositiveButton(R.string.continue_button) { _, _ ->
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:$packageName".toUri()))
            }
            .setNegativeButton(R.string.not_now_button, null)
            .show()
    }

    // Returns whether the user has already been asked, and remembers that they have now.
    private fun askedBefore(prefName: Int): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val key = getString(prefName)
        val asked = prefs.getBoolean(key, false)
        prefs.edit { putBoolean(key, true) }
        return asked
    }

    private fun showLocationRequiredDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.location_required_title)
            .setMessage(R.string.location_required_message)
            .setPositiveButton(R.string.open_settings_button) { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
            }
            .setNegativeButton(R.string.close_button) { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }
}
