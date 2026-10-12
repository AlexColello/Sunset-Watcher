package com.acolello.sunsetwatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

class NotificationSchedulerTest {

    private val day = TimeUnit.DAYS.toMillis(1)
    private val minute = TimeUnit.MINUTES.toMillis(1)

    private val today = 1_760_000_000_000L
    private val sunsets = listOf(today - day, today, today + day)

    @Test
    fun noKnownSunsets() {
        assertNull(chooseSunsetTime(listOf(-1L, -1L, -1L), 0, today))
    }

    @Test
    fun picksTodayBeforeSunset() {
        assertEquals(today, chooseSunsetTime(sunsets, 0, today - 10 * minute))
    }

    @Test
    fun picksTomorrowAfterSunset() {
        assertEquals(today + day, chooseSunsetTime(sunsets, 0, today + minute))
    }

    @Test
    fun offsetBeforeSunsetCanSkipToTomorrow() {
        // 30 minutes before sunset, the notification for 45 minutes before has already gone off.
        val offset = -45L * 60
        assertEquals(today + day, chooseSunsetTime(sunsets, offset, today - 30 * minute))
    }

    @Test
    fun offsetAfterSunsetKeepsToday() {
        // 10 minutes after sunset, the notification for an hour after hasn't gone off yet.
        val offset = 60L * 60
        assertEquals(today, chooseSunsetTime(sunsets, offset, today + 10 * minute))
    }

    @Test
    fun ignoresUnknownSunsets() {
        assertEquals(today, chooseSunsetTime(listOf(-1L, today, -1L), 0, today - minute))
    }

    @Test
    fun extrapolatesWhenCacheIsStale() {
        // Days after the last cached sunset, use the same time of day.
        val lastCached = today + day
        val now = lastCached + 3 * day - minute
        assertEquals(lastCached + 3 * day, chooseSunsetTime(sunsets, 0, now))
        assertEquals(lastCached + 4 * day, chooseSunsetTime(sunsets, 0, now + 2 * minute))
    }
}
