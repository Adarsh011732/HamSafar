package com.thechameleons.chameleonnav.engine

import com.thechameleons.chameleonnav.model.GeoPoint
import com.thechameleons.chameleonnav.model.RoadSegment
import kotlin.math.*

/**
 * Map Matching Engine.
 * Snaps unconstrained dead-reckoning positions to the nearest topological road corridor vectors.
 * Corrects cross-track orthogonal drift and aligns vehicle heading with road azimuth.
 */
class MapMatchingEngine {

    // Arterial road network segments (e.g. New Delhi Kartavya Corridor & surrounding arterials)
    val roadSegments: List<RoadSegment> = listOf(
        RoadSegment("seg_1", "India Gate Outer Ring", GeoPoint(28.6129, 77.2295), GeoPoint(28.6145, 77.2240)),
        RoadSegment("seg_2", "War Memorial Approach", GeoPoint(28.6145, 77.2240), GeoPoint(28.6138, 77.2150)),
        RoadSegment("seg_3", "Kartavya Path Central", GeoPoint(28.6138, 77.2150), GeoPoint(28.6143, 77.2085)),
        RoadSegment("seg_4", "Vijay Chowk Plaza", GeoPoint(28.6143, 77.2085), GeoPoint(28.6170, 77.2045)),
        RoadSegment("seg_5", "Raisina Hill Avenue", GeoPoint(28.6170, 77.2045), GeoPoint(28.6144, 77.1995))
    )

    data class MatchResult(
        val snappedPosition: GeoPoint,
        val matchedRoadName: String,
        val crossTrackOffsetMeters: Float,
        val alignedHeadingDeg: Float,
        val isSnapped: Boolean
    )

    /**
     * Projects point p onto segment [A, B] and returns snapped coordinate and orthogonal distance.
     */
    fun match(currentPos: GeoPoint, currentHeading: Float, maxSnappingThresholdMeters: Float = 25.0f): MatchResult {
        var bestSegment: RoadSegment? = null
        var bestSnappedPos: GeoPoint = currentPos
        var minDistanceMeters = Double.MAX_VALUE

        for (segment in roadSegments) {
            val (snapped, dist) = projectPointOntoSegment(currentPos, segment.start, segment.end)
            if (dist < minDistanceMeters) {
                minDistanceMeters = dist
                bestSegment = segment
                bestSnappedPos = snapped
            }
        }

        if (bestSegment != null && minDistanceMeters <= maxSnappingThresholdMeters) {
            // Align heading with road segment azimuth
            val roadAzimuth = bestSegment.azimuthDeg
            var headingDiff = abs(currentHeading - roadAzimuth) % 360f
            if (headingDiff > 180f) headingDiff = 360f - headingDiff

            val alignedHeading = if (headingDiff < 70f) {
                // Moving in same direction as road vector
                (currentHeading * 0.7f + roadAzimuth * 0.3f)
            } else if (headingDiff > 110f) {
                // Moving in opposite direction
                val reverseAzimuth = (roadAzimuth + 180f) % 360f
                (currentHeading * 0.7f + reverseAzimuth * 0.3f)
            } else {
                currentHeading
            }

            return MatchResult(
                snappedPosition = bestSnappedPos,
                matchedRoadName = bestSegment.name,
                crossTrackOffsetMeters = minDistanceMeters.toFloat(),
                alignedHeadingDeg = alignedHeading,
                isSnapped = true
            )
        }

        return MatchResult(
            snappedPosition = currentPos,
            matchedRoadName = "Unmatched Off-Road",
            crossTrackOffsetMeters = 0f,
            alignedHeadingDeg = currentHeading,
            isSnapped = false
        )
    }

    private fun projectPointOntoSegment(p: GeoPoint, a: GeoPoint, b: GeoPoint): Pair<GeoPoint, Double> {
        val latScale = 111139.0
        val lonScale = 111139.0 * cos(Math.toRadians(a.latitude))

        // Convert to local Cartesian meters relative to A
        val px = (p.longitude - a.longitude) * lonScale
        val py = (p.latitude - a.latitude) * latScale

        val bx = (b.longitude - a.longitude) * lonScale
        val by = (b.latitude - a.latitude) * latScale

        val segLengthSq = bx * bx + by * by
        if (segLengthSq < 1e-6) return Pair(a, p.distanceMeters(a))

        // Projection factor t in [0, 1]
        val t = ((px * bx + py * by) / segLengthSq).coerceIn(0.0, 1.0)

        val snappedLat = a.latitude + t * (b.latitude - a.latitude)
        val snappedLon = a.longitude + t * (b.longitude - a.longitude)
        val snappedPos = GeoPoint(snappedLat, snappedLon)

        val dist = p.distanceMeters(snappedPos)
        return Pair(snappedPos, dist)
    }
}
