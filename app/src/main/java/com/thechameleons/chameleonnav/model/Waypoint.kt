package com.thechameleons.chameleonnav.model

import kotlin.math.*

data class GeoPoint(
    val latitude: Double,
    val longitude: Double
) {
    fun formatCoordinates(): String {
        val latDir = if (latitude >= 0) "N" else "S"
        val lonDir = if (longitude >= 0) "E" else "W"
        return String.format(
            java.util.Locale.US,
            "%.4f° %s, %.4f° %s",
            abs(latitude),
            latDir,
            abs(longitude),
            lonDir
        )
    }

    fun distanceMeters(other: GeoPoint): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(other.latitude - this.latitude)
        val dLon = Math.toRadians(other.longitude - this.longitude)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(this.latitude)) * cos(Math.toRadians(other.latitude)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    fun bearingTo(other: GeoPoint): Float {
        val lat1 = Math.toRadians(this.latitude)
        val lat2 = Math.toRadians(other.latitude)
        val dLon = Math.toRadians(other.longitude - this.longitude)

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val brng = Math.toDegrees(atan2(y, x))
        return ((brng + 360.0) % 360.0).toFloat()
    }
}

data class RoadSegment(
    val id: String,
    val name: String,
    val start: GeoPoint,
    val end: GeoPoint,
    val speedLimitKmh: Float = 50f
) {
    val lengthMeters: Double by lazy { start.distanceMeters(end) }
    val azimuthDeg: Float by lazy { start.bearingTo(end) }
}

data class Waypoint(
    val id: String,
    val name: String,
    val location: GeoPoint,
    val description: String = ""
)
