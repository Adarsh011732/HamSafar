package com.thechameleons.chameleonnav.engine

import com.thechameleons.chameleonnav.model.GeoPoint
import com.thechameleons.chameleonnav.model.Vector3D
import kotlin.math.cos
import kotlin.math.sin

/**
 * Strapdown Inertial Navigation System (INS) Mechanization.
 * Solves attitude and geodetic coordinate integration at 50Hz.
 */
class InsMechanization {

    var currentHeadingDeg: Float = 0f
        private set

    fun updateAttitude(gyroZRadPerSec: Float, compassAzimuthDeg: Float, dt: Float): Float {
        val gyroYawDeg = Math.toDegrees(gyroZRadPerSec.toDouble()).toFloat() * dt
        var integrated = currentHeadingDeg + gyroYawDeg

        // Complementary filter with compass absolute reference
        if (compassAzimuthDeg > 0f) {
            var diff = (compassAzimuthDeg - integrated) % 360f
            if (diff > 180f) diff -= 360f
            if (diff < -180f) diff += 360f
            integrated += diff * 0.04f
        }

        currentHeadingDeg = (integrated % 360f + 360f) % 360f
        return currentHeadingDeg
    }

    fun setHeading(headingDeg: Float) {
        currentHeadingDeg = (headingDeg % 360f + 360f) % 360f
    }

    fun propagatePosition(
        startPos: GeoPoint,
        speedMs: Float,
        headingDeg: Float,
        dt: Float
    ): GeoPoint {
        val displacementMeters = speedMs * dt
        val headingRad = Math.toRadians(headingDeg.toDouble())

        val metersPerDegLat = 111139.0
        val metersPerDegLon = 111139.0 * cos(Math.toRadians(startPos.latitude))

        val dLat = (displacementMeters * cos(headingRad)) / metersPerDegLat
        val dLon = (displacementMeters * sin(headingRad)) / metersPerDegLon

        return GeoPoint(startPos.latitude + dLat, startPos.longitude + dLon)
    }
}
