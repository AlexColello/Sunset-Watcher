package com.example.sunsetwatcher

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import androidx.preference.PreferenceManager
import androidx.appcompat.app.AppCompatActivity


import android.view.MotionEvent
import android.view.VelocityTracker
import android.util.Log
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

import org.json.JSONObject
import androidx.core.app.ComponentActivity.ExtraData
import androidx.core.content.ContextCompat.getSystemService
import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import java.io.PrintWriter
import java.io.StringWriter
import java.util.*
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    var mTotalVelocity = 0.0
    var mOffset = 0
    private var mVelocityTracker: VelocityTracker? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    val COARSE_LOCATION_PERMISSION_REQUEST = 777
    val BACKGROUND_LOCATION_PERMISSION_REQUEST = 666

    override fun onStart(){
        super.onStart()
        inst = this
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mTotalVelocity = 0.0

        mOffset = getSavedOffset()

        displayTotal()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        updateTime()

    }

    fun parseQuery(queryResultString : String){

        val queryResults = JSONObject(queryResultString)

        if (queryResults.getString("status") == "OK"){
            val result = queryResults.getJSONObject("results")
            Log.d("request", "Found sunset time ${result.getString("sunset")}")
        } else {
            Log.e("request", "Failed to parse result: \"${queryResultString}\"")
        }
    }

    fun getSunsetTime(latitude : Double, longitude: Double){

        // Instantiate the RequestQueue.
        val queue = Volley.newRequestQueue(this)
        val url = "https://api.sunrise-sunset.org/json?lat=${latitude}&lng=${longitude}&date=today&formatted=0"

        // Request a string response from the provided URL.
        val stringRequest = StringRequest(
            Request.Method.GET, url,
            Response.Listener<String> { response ->
                parseQuery(response)
            },
            Response.ErrorListener { Log.e("request","Request for \"${url}\" failed.")})

        // Add the request to the RequestQueue.
        queue.add(stringRequest)
    }

    fun getSavedOffset(): Int {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this /* Activity context */)
        val defaultValue = sharedPreferences.getInt(getString(R.string.offset_pref_name), 0)

        return defaultValue

    }

    fun setSavedOffset(offset : Int) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this /* Activity context */)

        with (sharedPreferences.edit()) {
            putInt(getString(R.string.offset_pref_name), offset)
            commit()
        }
    }

    fun convertVelocityToOffset(velocity : Double) : Int{
        return (velocity / 1000).toInt()
    }

    fun displayTotal() {
        val helloTextView = findViewById(R.id.text_view_id) as TextView
        helloTextView.setText(mOffset.toString())
    }

    fun setAlarm(time : String){

        Log.d("time", time)

        val alarmMgr: AlarmManager = this.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        lateinit var alarmIntent: PendingIntent

        alarmIntent = Intent(this, AlarmReceiver::class.java).let { intent ->
            PendingIntent.getBroadcast(this, 0, intent, 0)
        }

        val calendar: Calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 14)
        }

        // With setInexactRepeating(), you have to use one of the AlarmManager interval
        // constants--in this case, AlarmManager.INTERVAL_DAY.
        alarmMgr.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            alarmIntent
        )
    }

    fun updateTime(){

        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {

            Log.d("location", "Requesting ACCESS_COARSE_LOCATION permissions.")
            // Permission is not granted
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                COARSE_LOCATION_PERMISSION_REQUEST)

        } else if( Build.VERSION.SDK_INT >= 29 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED){

            Log.d("location", "Requesting ACCESS_BACKGROUND_LOCATION permissions.")
            // Permission is not granted
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                BACKGROUND_LOCATION_PERMISSION_REQUEST)

        } else {

            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    run {
                        // Got last known location. In some rare situations this can be null.
                        if (location != null) {
                            Log.d(
                                "location",
                                "Latitude: ${location.latitude} Longitude: ${location.longitude}"
                            )
                            getSunsetTime(location.latitude, location.longitude)
                        } else {
                            Log.e("location", "Location is null")
                        }
                    }
                }.addOnFailureListener {
                    run {
                        val sw = StringWriter()
                        val pw = PrintWriter(sw)
                        it.printStackTrace(pw)
                        val stackTrace = sw.toString() // stack trace as a string
                        Log.e("location", stackTrace)
                    }
                }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            COARSE_LOCATION_PERMISSION_REQUEST -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                    updateTime()

                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Log.e("location", "Permission denied for ACCESS_COARSE_LOCATION.")
                    exitProcess(0)
                }
                return
            }

            BACKGROUND_LOCATION_PERMISSION_REQUEST -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                    updateTime()

                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.

                    if(Build.VERSION.SDK_INT >= 29) {
                        Log.e("location", "Permission denied for ACCESS_BACKGROUND_LOCATION.")
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

        updateTime()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Reset the velocity tracker back to its initial state.
                mVelocityTracker?.clear()
                // If necessary retrieve a new VelocityTracker object to watch the
                // velocity of a motion.
                mVelocityTracker = mVelocityTracker ?: VelocityTracker.obtain()
                // Add a user's movement to the tracker.
                mVelocityTracker?.addMovement(event)

                mTotalVelocity = mOffset * 1000.0
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
                    Log.d("velocity", "X velocity: ${getXVelocity(pointerId)}")
                    Log.d("velocity", "Y velocity: ${getYVelocity(pointerId)}")

                    mTotalVelocity += getYVelocity(pointerId)

                    mOffset = convertVelocityToOffset(mTotalVelocity)
                    displayTotal()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Return a VelocityTracker object back to be re-used by others.
                mVelocityTracker?.recycle()
                mVelocityTracker = null

                mOffset = convertVelocityToOffset(mTotalVelocity)
                displayTotal()
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