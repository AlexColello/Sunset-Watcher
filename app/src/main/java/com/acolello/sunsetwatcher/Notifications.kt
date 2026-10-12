package com.acolello.sunsetwatcher

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

fun createNotificationChannel(context: Context) {
    val name = context.getString(R.string.channel_name)
    val descriptionText = context.getString(R.string.channel_description)
    val importance = NotificationManager.IMPORTANCE_DEFAULT
    val id = context.getString(R.string.channel_id)
    val channel = NotificationChannel(id, name, importance).apply {
        description = descriptionText
    }
    // Register the channel with the system. Does nothing if it already exists.
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

fun sendSunsetNotification(context: Context) {
    // Android 13+ requires the user to grant POST_NOTIFICATIONS.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED) {
        Log.w("notification", "Notification permission not granted")
        return
    }

    // Create an explicit intent for an Activity in your app
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

    val builder = NotificationCompat.Builder(context, context.getString(R.string.channel_id))
        .setSmallIcon(R.drawable.notification_icon)
        .setContentTitle("Sunset is soon!")
        .setContentText("Go out and enjoy the sunset!")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        // Set the intent that will fire when the user taps the notification
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)

    // notificationId is a unique int for each notification that you must define
    NotificationManagerCompat.from(context).notify(0, builder.build())
}
