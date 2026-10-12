package com.acolello.sunsetwatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

// Reference times are from api.sunrise-sunset.org, which is where the app gets sunset times.
class SolarPositionTest {

    private fun elevation(time: String, latitude: Double, longitude: Double) =
        solarElevation(OffsetDateTime.parse(time).toInstant().toEpochMilli(), latitude, longitude)

    // The API puts sunset a little later than the standard -0.833° definition, with the sun
    // 0.25-0.4° lower, so the app positions the sun relative to the API's sunset instead.
    private fun assertSunset(time: String, latitude: Double, longitude: Double) {
        assertEquals(SUNSET_ELEVATION_DEGREES, elevation(time, latitude, longitude), 0.5)
    }

    @Test
    fun losAngelesSummerSolstice() {
        assertSunset("2026-06-22T03:09:04+00:00", 34.0522, -118.2437)
        // Solar noon: 90 - (34.05 - 23.44)
        assertEquals(79.4, elevation("2026-06-21T19:54:52+00:00", 34.0522, -118.2437), 0.2)
    }

    @Test
    fun londonWinterSolstice() {
        assertSunset("2026-12-21T15:55:33+00:00", 51.5074, -0.1278)
        // Solar noon: 90 - (51.51 + 23.44)
        assertEquals(15.05, elevation("2026-12-21T11:58:34+00:00", 51.5074, -0.1278), 0.2)
    }

    @Test
    fun sydneyEquinox() {
        assertSunset("2026-03-20T08:08:36+00:00", -33.8688, 151.2093)
    }

    @Test
    fun reykjavikSlowSunset() {
        assertSunset("2026-09-01T20:47:10+00:00", 64.1466, -21.9426)
    }

    @Test
    fun sunIsLowerAfterSunset() {
        val before = elevation("2026-06-22T02:09:04+00:00", 34.0522, -118.2437)
        val after = elevation("2026-06-22T04:09:04+00:00", 34.0522, -118.2437)
        assertTrue(before > 5)
        assertTrue(after < -5)
    }
}
