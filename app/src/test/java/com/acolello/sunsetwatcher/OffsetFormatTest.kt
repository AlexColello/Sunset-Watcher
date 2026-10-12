package com.acolello.sunsetwatcher

import org.junit.Assert.assertEquals
import org.junit.Test

class OffsetFormatTest {

    @Test
    fun minutesOnly() {
        assertEquals("45 min", formatDuration(-45))
        assertEquals("1 min", formatDuration(1))
    }

    @Test
    fun wholeHours() {
        assertEquals("2 h", formatDuration(120))
        assertEquals("4 h", formatDuration(-240))
    }

    @Test
    fun hoursAndMinutes() {
        assertEquals("1 h 5 min", formatDuration(-65))
    }
}
