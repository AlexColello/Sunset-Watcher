package com.acolello.sunsetwatcher

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

// Elevation of the sun's center at sunset, below the horizon because of atmospheric refraction
// and the sun's radius. This is the definition sunrise-sunset.org uses.
const val SUNSET_ELEVATION_DEGREES = -0.833

private const val MILLIS_PER_DAY = 86_400_000.0

// Angle of the sun's center above the horizon in degrees, ignoring refraction. Uses the NOAA
// solar calculator equations, which are accurate to well under a degree for current dates.
fun solarElevation(epochMillis: Long, latitude: Double, longitude: Double): Double {
    val julianDay = epochMillis / MILLIS_PER_DAY + 2440587.5
    val t = (julianDay - 2451545.0) / 36525.0

    val meanLongitude = (280.46646 + t * (36000.76983 + t * 0.0003032)).mod(360.0)
    val meanAnomaly = 357.52911 + t * (35999.05029 - 0.0001537 * t)
    val eccentricity = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)

    val equationOfCenter = sinDeg(meanAnomaly) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sinDeg(2 * meanAnomaly) * (0.019993 - 0.000101 * t) +
            sinDeg(3 * meanAnomaly) * 0.000289
    val omega = 125.04 - 1934.136 * t
    val apparentLongitude = meanLongitude + equationOfCenter - 0.00569 - 0.00478 * sinDeg(omega)

    val meanObliquity = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
    val obliquity = meanObliquity + 0.00256 * cosDeg(omega)
    val declination = Math.toDegrees(asin(sinDeg(obliquity) * sinDeg(apparentLongitude)))

    val y = tan(Math.toRadians(obliquity / 2)).let { it * it }
    val equationOfTimeMinutes = 4 * Math.toDegrees(
        y * sinDeg(2 * meanLongitude) -
                2 * eccentricity * sinDeg(meanAnomaly) +
                4 * eccentricity * y * sinDeg(meanAnomaly) * cosDeg(2 * meanLongitude) -
                0.5 * y * y * sinDeg(4 * meanLongitude) -
                1.25 * eccentricity * eccentricity * sinDeg(2 * meanAnomaly)
    )

    val utcMinutes = epochMillis.mod(MILLIS_PER_DAY.toLong()) / 60_000.0
    val trueSolarMinutes = (utcMinutes + equationOfTimeMinutes + 4 * longitude).mod(1440.0)
    val hourAngle = trueSolarMinutes / 4 - 180

    val cosZenith = sinDeg(latitude) * sinDeg(declination) +
            cosDeg(latitude) * cosDeg(declination) * cosDeg(hourAngle)
    return 90 - Math.toDegrees(acos(cosZenith.coerceIn(-1.0, 1.0)))
}

private fun sinDeg(degrees: Double) = sin(Math.toRadians(degrees))
private fun cosDeg(degrees: Double) = cos(Math.toRadians(degrees))
