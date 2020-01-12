package com.example.sunsetwatcher

import android.content.Intent
import android.location.Location
import android.util.Log
import androidx.core.app.JobIntentService
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.json.JSONObject
import java.io.PrintWriter
import java.io.StringWriter


class UpdateService : JobIntentService(){

    override fun onHandleWork(intent: Intent) {
        updateSunsetTime()
    }

    private fun updateSunsetTime(){

        //Log.d("update", "Updating sunset time.")

        val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                run {
                    // Got last known location. In some rare situations this can be null.
                    if (location != null) {
                        Log.d(
                            "location",
                            "Latitude: ${location.latitude} Longitude: ${location.longitude}"
                        )
                        querySunsetTime(location.latitude, location.longitude, "yesterday")
                        querySunsetTime(location.latitude, location.longitude, "today")
                        querySunsetTime(location.latitude, location.longitude, "tomorrow")
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

    private fun processQueryResponse(queryResultString : String, date: String){

        val queryResults = JSONObject(queryResultString)

        if (queryResults.getString("status") == "OK"){
            val result = queryResults.getJSONObject("results")
            val timeString = result.getString("sunset")

            var prefString = ""
            when(date) {
                "yesterday" -> prefString = this.getString(R.string.sunset_yesterday_pref_name)
                "today" -> prefString = this.getString(R.string.sunset_today_pref_name)
                "tomorrow" -> prefString = this.getString(R.string.sunset_tomorrow_pref_name)
                else -> Log.e("time", "Could not convert date string '$date' to preference string.")
            }

            Log.d("request", "Found sunset time $timeString for $date")

            setSavedSunsetTime(timeString, this, prefString)
            updateNotificationAlarm(this)

            val main = instance()
            main?.updateUI()

        } else {
            Log.e("request", "Failed to parse result: \"${queryResultString}\"")
        }
    }

    private fun querySunsetTime(latitude : Double, longitude: Double, date: String){

        // Instantiate the RequestQueue.
        val queue = Volley.newRequestQueue(this)
        val url = "https://api.sunrise-sunset.org/json?lat=${latitude}&lng=${longitude}&date=$date&formatted=0"

        // Request a string response from the provided URL.
        val stringRequest = StringRequest(
            Request.Method.GET, url,
            Response.Listener<String> { response ->
                processQueryResponse(response, date)
            },
            Response.ErrorListener { Log.e("request","Request for \"${url}\" failed.")})

        // Add the request to the RequestQueue.
        queue.add(stringRequest)
    }
}

