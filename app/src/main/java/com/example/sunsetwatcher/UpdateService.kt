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

        Log.d("update", "Updating sunset time.")

        val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        if(instance()!!.checkPermissions()) {

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

    private fun processQueryResponse(queryResultString : String){

        val queryResults = JSONObject(queryResultString)

        if (queryResults.getString("status") == "OK"){
            val result = queryResults.getJSONObject("results")
            val timeString = result.getString("sunset")

            Log.d("request", "Found sunset time $timeString")

            val main = instance()!!

            main.setSavedSunsetTime(timeString)
            main.updateNotificationTime()

        } else {
            Log.e("request", "Failed to parse result: \"${queryResultString}\"")
        }
    }

    private fun getSunsetTime(latitude : Double, longitude: Double){

        // Instantiate the RequestQueue.
        val queue = Volley.newRequestQueue(this)
        val url = "https://api.sunrise-sunset.org/json?lat=${latitude}&lng=${longitude}&date=today&formatted=0"

        // Request a string response from the provided URL.
        val stringRequest = StringRequest(
            Request.Method.GET, url,
            Response.Listener<String> { response ->
                processQueryResponse(response)
            },
            Response.ErrorListener { Log.e("request","Request for \"${url}\" failed.")})

        // Add the request to the RequestQueue.
        queue.add(stringRequest)
    }
}

