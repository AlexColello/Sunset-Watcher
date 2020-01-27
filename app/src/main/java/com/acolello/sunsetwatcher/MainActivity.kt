package com.acolello.sunsetwatcher

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.util.Log
import android.widget.TextView

import androidx.core.app.ActivityCompat
import androidx.core.app.JobIntentService
import androidx.core.content.ContextCompat
import java.util.*
import kotlin.math.absoluteValue
import kotlin.system.exitProcess


class MainActivity : AppCompatActivity() {

    var mTotalVelocity = 0.0
    private var mVelocityTracker: VelocityTracker? = null
    val COARSE_LOCATION_PERMISSION_REQUEST = 777
    val BACKGROUND_LOCATION_PERMISSION_REQUEST = 666
    val VELOCITY_SCALAR: Double = 100.0

    private lateinit var mainDrawableView: MainView


    override fun onStart(){
        super.onStart()
        inst = this
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainDrawableView = MainView(this)

        setContentView(mainDrawableView)


        while(!checkPermissions()){
        }

        mTotalVelocity = 0.0
        loadCache(this)

        val updateIntent = Intent(this, UpdateService::class.java)
        JobIntentService.enqueueWork(this, UpdateService::class.java, UPDATE_JOB_ID, updateIntent)

        setupUpdateAlarm()
        updateUI()

    }

    private fun convertVelocityToOffset(velocity : Double) : Long {
        return (velocity / VELOCITY_SCALAR).toLong()
    }

    fun updateUI() {
        mainDrawableView.invalidate()
    }

    private fun setupUpdateAlarm(){

        val alarmMgr: AlarmManager = this.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent: PendingIntent = Intent(this, UpdateAlarmReceiver::class.java).let { intent ->
            PendingIntent.getBroadcast(this, 0, intent, 0)
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
        PendingIntent.getBroadcast(context, 0, intent, 0)
    }

    alarmMgr.cancel(notificationIntent)
    alarmMgr.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        notificationTime,
        notificationIntent
    )
}