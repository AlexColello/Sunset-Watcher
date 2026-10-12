package com.acolello.sunsetwatcher

import kotlin.math.abs

// Formats a number of minutes like "45 min", "2 h" or "1 h 5 min", ignoring the sign.
fun formatDuration(minutes: Int): String {
    val hours = abs(minutes) / 60
    val remainder = abs(minutes) % 60
    return when {
        hours == 0 -> "$remainder min"
        remainder == 0 -> "$hours h"
        else -> "$hours h $remainder min"
    }
}
