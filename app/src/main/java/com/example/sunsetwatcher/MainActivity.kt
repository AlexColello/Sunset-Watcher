package com.example.sunsetwatcher

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.preference.PreferenceManager
import androidx.appcompat.app.AppCompatActivity


import kotlinx.android.synthetic.main.activity_main.*
import android.view.MotionEvent
import android.view.VelocityTracker
import android.util.Log
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class MainActivity : AppCompatActivity() {

    var mTotalVelocity = 0.0
    var mOffset = 0
    var CHANNEL_ID = "Sunrise Watcher"

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
        var helloTextView = findViewById(R.id.text_view_id) as TextView
        helloTextView.setText(mOffset.toString())
    }

    fun sendNotifictation() {
        // Create an explicit intent for an Activity in your app
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, 0)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle("My notification")
            .setContentText("Hello World!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // Set the intent that will fire when the user taps the notification
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            // notificationId is a unique int for each notification that you must define
            notify(0, builder.build())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mTotalVelocity = 0.0

        mOffset = getSavedOffset()

        displayTotal()

        createNotificationChannel()

        sendNotifictation()

    }

    private var mVelocityTracker: VelocityTracker? = null

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
                    Log.d("", "X velocity: ${getXVelocity(pointerId)}")
                    Log.d("", "Y velocity: ${getYVelocity(pointerId)}")

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

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

}
