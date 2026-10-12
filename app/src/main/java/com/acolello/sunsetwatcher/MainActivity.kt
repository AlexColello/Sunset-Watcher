package com.acolello.sunsetwatcher

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.JobIntentService
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import java.util.*


class MainActivity : AppCompatActivity() {

    var mTotalVelocity = 0.0
    private var mVelocityTracker: VelocityTracker? = null
    val VELOCITY_SCALAR: Double = 100.0

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
            requestExactAlarms()
        }

    override fun onStart(){
        super.onStart()
        inst = this
    }

    override fun onStop() {
        inst = null
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        mTotalVelocity = 0.0
        loadCache(this)
        updateUI()

        requestForegroundPermissions()
    }

    private fun startUpdates() {
        val updateIntent = Intent(this, UpdateService::class.java)
        JobIntentService.enqueueWork(this, UpdateService::class.java, UPDATE_JOB_ID, updateIntent)

        setupUpdateAlarm()
    }

    private fun convertVelocityToOffset(velocity : Double) : Long {
        return (velocity / VELOCITY_SCALAR).toLong()
    }

    fun updateUI() {

        val mainView = findViewById<MainView>(R.id.main_view)
        mainView.invalidate()

    }

    private fun setupUpdateAlarm(){

        val alarmMgr: AlarmManager = this.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent: PendingIntent = Intent(this, UpdateAlarmReceiver::class.java).let { intent ->
            PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        }

        // With setInexactRepeating(), you have to use one of the AlarmManager interval
        // constants--in this case, AlarmManager.INTERVAL_DAY.
        alarmMgr.setInexactRepeating(
            AlarmManager.RTC,
            Calendar.getInstance().timeInMillis,
            AlarmManager.INTERVAL_FIFTEEN_MINUTES,
            alarmIntent
        )
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

    override fun onTouchEvent(event: MotionEvent): Boolean {

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Reset the velocity tracker back to its initial state.
                mVelocityTracker?.clear()
                // If necessary retrieve a new VelocityTracker object to watch the
                // velocity of a motion.
                mVelocityTracker = mVelocityTracker ?: VelocityTracker.obtain()
                // Add a user's movement to the tracker.
                mVelocityTracker?.addMovement(event)

                mTotalVelocity = getOffset(this) * VELOCITY_SCALAR
            }
            MotionEvent.ACTION_MOVE -> {
                mVelocityTracker?.apply {
                    val pointerId: Int = event.getPointerId(event.actionIndex)
                    addMovement(event)
                    // When you want to determine the velocity, call
                    // computeCurrentVelocity(). Then call getXVelocity()
                    // and getYVelocity() to retrieve the velocity for each pointer ID.
                    computeCurrentVelocity(1000)
                    // Log velocity of pixels per second
                    // Best practice to use VelocityTrackerCompat where possible.
                    //Log.d("velocity", "X velocity: ${getXVelocity(pointerId)}")
                    //Log.d("velocity", "Y velocity: ${getYVelocity(pointerId)}")

                    mTotalVelocity += getYVelocity(pointerId)

                    setOffset(convertVelocityToOffset(mTotalVelocity))
                    updateUI()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Return a VelocityTracker object back to be re-used by others.
                mVelocityTracker?.recycle()
                mVelocityTracker = null

                val offset = convertVelocityToOffset(mTotalVelocity)
                setSavedOffset(offset, this)

                updateUI()
                updateNotificationAlarm(this)

            }
        }
        return true
    }

}

private var inst : MainActivity? = null

fun instance(): MainActivity? {
    return inst
}

fun epochToString(millis: Long): String{
    val sunsetTime = Date(millis)
    return sunsetTime.toString()
}

fun getBestSunsetTime(context: Context): Long{
    val yesterday = getSunsetTime(context, context.getString(R.string.sunset_yesterday_pref_name))
    val today = getSunsetTime(context, context.getString(R.string.sunset_today_pref_name))
    val tomorrow = getSunsetTime(context, context.getString(R.string.sunset_tomorrow_pref_name))
    val offsetMillis = getOffset(context)

    val currentTime = Calendar.getInstance().timeInMillis

    val yesterdayAlarm = calculateNotificationTime(yesterday, offsetMillis)
    val todayAlarm = calculateNotificationTime(today, offsetMillis)
    val tomorrowAlarm = calculateNotificationTime(tomorrow, offsetMillis)

    if(yesterdayAlarm > currentTime){
        return yesterday
    } else if (todayAlarm > currentTime){
        return today
    } else if (tomorrowAlarm > currentTime){
        return tomorrow
    } else {
        Log.e("time", "None of the possible alarm times are in the future")
        return today
    }
}

fun getNotificationTime(context: Context): Long{
    val sunsetTime = getBestSunsetTime(context)
    val offset = getOffset(context)
    return calculateNotificationTime(sunsetTime, offset)
}

fun calculateNotificationTime(sunsetTime: Long, offsetTime: Long): Long{
    val offsetMillis: Long = offsetTime * 1000
    return sunsetTime + offsetMillis
}

fun updateNotificationAlarm(context: Context){
    updateNotificationAlarm(context, getNotificationTime(context))
}

fun updateNotificationAlarm(context: Context, notificationTime: Long){

    val alarmMgr: AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val notificationIntent = Intent(context, NotificationAlarmReceiver::class.java).let { intent ->
        PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    alarmMgr.cancel(notificationIntent)

    // Without sunset data the "best" time is in the past, and an alarm in the past fires
    // immediately and then reschedules itself again, spamming notifications.
    if (notificationTime <= System.currentTimeMillis()) {
        Log.w("alarm", "Not scheduling notification for past time ${epochToString(notificationTime)}")
        return
    }

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