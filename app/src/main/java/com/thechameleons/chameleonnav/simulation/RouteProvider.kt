package com.thechameleons.chameleonnav.simulation

import com.thechameleons.chameleonnav.model.GeoPoint
import com.thechameleons.chameleonnav.model.Waypoint

object RouteProvider {

    val demoWaypoints: List<Waypoint> = listOf(
        Waypoint(
            id = "wp_start",
            name = "Start: India Gate",
            location = GeoPoint(28.6129, 77.2295),
            description = "Iconic monument & GNSS reference origin"
        ),
        Waypoint(
            id = "wp_a",
            name = "Point A: War Memorial",
            location = GeoPoint(28.6145, 77.2240),
            description = "Subtle curve entry into Kartavya corridor"
        ),
        Waypoint(
            id = "wp_b",
            name = "Point B: Kartavya Path",
            location = GeoPoint(28.6138, 77.2150),
            description = "High-speed arterial boulevard (Simulated GNSS Canyon)"
        ),
        Waypoint(
            id = "wp_c",
            name = "Point C: Vijay Chowk",
            location = GeoPoint(28.6143, 77.2085),
            description = "Government plaza intersection"
        ),
        Waypoint(
            id = "wp_d",
            name = "Point D: North Block",
            location = GeoPoint(28.6170, 77.2045),
            description = "Curved corridor with tree canopy attenuation"
        ),
        Waypoint(
            id = "wp_dest",
            name = "Destination: Rashtrapati Bhavan",
            location = GeoPoint(28.6144, 77.1995),
            description = "Final destination point"
        )
    )

    val routePoints: List<GeoPoint> = demoWaypoints.map { it.location }

    /**
     * Compute total route distance in meters.
     */
    val totalRouteDistanceMeters: Double by lazy {
        var total = 0.0
        for (i in 0 until routePoints.size - 1) {
            total += routePoints[i].distanceMeters(routePoints[i + 1])
        }
        total
    }

    /**
     * Interpolate position along the multi-segment route given distance in meters.
     */
    fun getPositionAtDistance(distanceMeters: Double): RouteSample {
        var remaining = distanceMeters % totalRouteDistanceMeters
        if (remaining < 0) remaining += totalRouteDistanceMeters

        for (i in 0 until routePoints.size - 1) {
            val p1 = routePoints[i]
            val p2 = routePoints[i + 1]
            val segDist = p1.distanceMeters(p2)

            if (remaining <= segDist || i == routePoints.size - 2) {
                val ratio = (remaining / segDist).coerceIn(0.0, 1.0)
                val lat = p1.latitude + ratio * (p2.latitude - p1.latitude)
                val lon = p1.longitude + ratio * (p2.longitude - p1.longitude)
                val heading = computeHeading(p1, p2)
                return RouteSample(
                    position = GeoPoint(lat, lon),
                    headingDeg = heading.toFloat(),
                    segmentIndex = i,
                    segmentProgress = ratio.toFloat()
                )
            }
            remaining -= segDist
        }

        val lastPoint = routePoints.last()
        return RouteSample(lastPoint, 270f, routePoints.size - 1, 1.0f)
    }

    private fun computeHeading(p1: GeoPoint, p2: GeoPoint): Double {
        val lat1 = Math.toRadians(p1.latitude)
        val lat2 = Math.toRadians(p2.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)

        val y = Math.sin(dLon) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
        val brng = Math.toDegrees(Math.atan2(y, x))
        return (brng + 360.0) % 360.0
    }

    data class RouteSample(
        val position: GeoPoint,
        val headingDeg: Float,
        val segmentIndex: Int,
        val segmentProgress: Float
    )
}
