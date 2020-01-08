package com.example.sunsetwatcher

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.preference.PreferenceManager
import androidx.appcompat.app.AppCompatActivity


import android.view.MotionEvent
import android.view.VelocityTracker
import android.util.Log
import android.widget.TextView

import androidx.core.app.ActivityCompat
import androidx.core.app.JobIntentService
import androidx.core.content.ContextCompat
import java.time.OffsetDateTime
import java.util.*
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    var mTotalVelocity = 0.0
    private var mOffset: Long = 0L
    private var mSunsetTime: Long = 0L
    private var mNotificationTime: Long = 0L
    private var mVelocityTracker: VelocityTracker? = null
    val COARSE_LOCATION_PERMISSION_REQUEST = 777
    val BACKGROUND_LOCATION_PERMISSION_REQUEST = 666
    val VELOCITY_SCALAR: Double = 100.0
    val OFFSET_TIME_SCALAR: Double = 1.0

    override fun onStart(){
        super.onStart()
        inst = this
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mTotalVelocity = 0.0

        mOffset = getSavedOffset()
        mSunsetTime = getSavedSunsetTime()

        while(!checkPermissions()){}

        val updateIntent = Intent(this, UpdateService::class.java)

        JobIntentService.enqueueWork(this, UpdateService::class.java, UPDATE_JOB_ID, updateIntent!!)

        setupUpdateAlarm()
        updateUI()

    }

    fun getSavedOffset(): Long {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this /* Activity context */)
        val defaultValue = sharedPreferences.getLong(getString(R.string.offset_pref_name), 0L)

        return defaultValue

    }

    fun setSavedOffset(offset : Long) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this /* Activity context */)

        with (sharedPreferences.edit()) {
            putLong(getString(R.string.offset_pref_name), offset)
            commit()
        }
    }

    fun getSavedSunsetTime(): Long {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this /* Activity context */)
        val retval = sharedPreferences.getLong(getString(R.string.sunset_time_pref_name), -1L)

        return retval

    }

    fun setSavedSunsetTime(sunsetTime : Long) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this /* Activity context */)

        with (sharedPreferences.edit()) {
            putLong(getString(R.string.sunset_time_pref_name), sunsetTime)
            commit()
        }
    }

    fun setSavedSunsetTime(sunsetTime : String) {

        val datetime : OffsetDateTime = OffsetDateTime.parse(sunsetTime)
        val millis = datetime.toInstant().toEpochMilli()

        setSavedSunsetTime(millis)
    }

    fun convertVelocityToOffset(velocity : Double) : Long {
        return (velocity / VELOCITY_SCALAR).toLong()
    }

    fun updateUI() {

        updateNotificationTime()

        val offsetTextView = findViewById<TextView>(R.id.offset_text)
        offsetTextView.setText(mOffset.toString())

        val sunsetTextView = findViewById<TextView>(R.id.sunset_time_text)
        val sunsetTime = Date(mSunsetTime)
        sunsetTextView.setText(sunsetTime.toString())

        val notificationTextView = findViewById<TextView>(R.id.notification_time_text)
        val notificationTime = Date(mNotificationTime)
        notificationTextView.setText(notificationTime.toString())
    }

    fun setupUpdateAlarm(){

        val alarmMgr: AlarmManager = this.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent: PendingIntent = Intent(this, UpdateAlarmReceiver::class.java).let { intent ->
            PendingIntent.getBroadcast(this, 0, intent, 0)
        }

        // With setInexactRepeating(), you have to use one of the AlarmManager interval
        // constants--in this case, AlarmManager.INTERVAL_DAY.
        alarmMgr.setInexactRepeating(
            AlarmManager.RTC,
            Calendar.getInstance().getTimeInMillis(),
            AlarmManager.INTERVAL_HALF_HOUR,
            alarmIntent
        )
    }

    fun updateNotificationTime(){

        val alarmMgr: AlarmManager = this.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent: PendingIntent = Intent(this, NotificationAlarmReceiver::class.java).let { intent ->
            PendingIntent.getBroadcast(this, 0, intent, 0)
        }

        mSunsetTime = getSavedSunsetTime()
        val offsetMillis: Long = (mOffset * OFFSET_TIME_SCALAR).toLong() / 60 * 60 * 1000
        val millis = mSunsetTime + offsetMillis

        mNotificationTime = millis

        alarmMgr.setExact(
            AlarmManager.RTC_WAKEUP,
            millis,
            alarmIntent
        )
    }

    fun checkPermissions(): Boolean {

        var passed = true

        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {

            Log.d("location", "Requesting ACCESS_COARSE_LOCATION permissions.")
            // Permission is not granted
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                COARSE_LOCATION_PERMISSION_REQUEST)

            passed = false
        }

        if( Build.VERSION.SDK_INT >= 29 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {

            Log.d("location", "Requesting ACCESS_BACKGROUND_LOCATION permissions.")
            // Permission is not granted
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                BACKGROUND_LOCATION_PERMISSION_REQUEST
            )

            passed = false
        }

        return passed
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            COARSE_LOCATION_PERMISSION_REQUEST -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                    Log.d("permissions", "Permission granted for ACCESS_COARSE_LOCATION.")

                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Log.e("permissions", "Permission denied for ACCESS_COARSE_LOCATION.")
                    exitProcess(0)
                }
                return
            }

            BACKGROUND_LOCATION_PERMISSION_REQUEST -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                    Log.d("permissions", "Permission granted for ACCESS_BACKGROUND_LOCATION.")

                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.

                    if(Build.VERSION.SDK_INT >= 29) {
                        Log.e("permissions", "Permission denied for ACCESS_BACKGROUND_LOCATION.")
                        exitProcess(0)
                    }
                }
                return
            }

            // Add other 'when' lines to check for other
            // permissions this app might request.
            else -> {
                // Ignore all other requests.
            }
        }
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

                mTotalVelocity = mOffset * VELOCITY_SCALAR
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

                    mOffset = convertVelocityToOffset(mTotalVelocity)
                    updateUI()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Return a VelocityTracker object back to be re-used by others.
                mVelocityTracker?.recycle()
                mVelocityTracker = null

                mOffset = convertVelocityToOffset(mTotalVelocity)
                updateUI()
                setSavedOffset(mOffset)
            }
        }
        return true
    }

}

var inst : MainActivity? = null

fun instance(): MainActivity? {
    return inst
}