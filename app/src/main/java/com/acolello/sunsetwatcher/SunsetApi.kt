package com.acolello.sunsetwatcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

private const val TIMEOUT_MILLIS = 15_000

// Returns the sunset time from api.sunrise-sunset.org as an ISO 8601 string.
suspend fun fetchSunsetTime(latitude: Double, longitude: Double, date: LocalDate): String =
    withContext(Dispatchers.IO) {
        val url = URL("https://api.sunrise-sunset.org/json?lat=$latitude&lng=$longitude&date=$date&formatted=0")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS

        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Request for \"$url\" failed with ${connection.responseCode}")
            }
            val response = connection.inputStream.bufferedReader().use { it.readText() }

            val queryResults = JSONObject(response)
            if (queryResults.getString("status") != "OK") {
                throw IOException("Failed to parse result: \"$response\"")
            }
            queryResults.getJSONObject("results").getString("sunset")
        } finally {
            connection.disconnect()
        }
    }
