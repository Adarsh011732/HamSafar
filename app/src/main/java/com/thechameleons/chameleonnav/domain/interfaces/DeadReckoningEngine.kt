package com.thechameleons.chameleonnav.domain.interfaces

import com.thechameleons.chameleonnav.model.GeoPoint
import com.thechameleons.chameleonnav.model.Vector3D

/**
 * Interface for Inertial Navigation & Dead Reckoning filter.
 * In Phase 2, this will be implemented by an Extended Kalman Filter (EKF)
 * or Unscented Kalman Filter (UKF) integrating strapdown IMU equations.
 */
interface DeadReckoningEngine {
    data class StateEstimate(
        val position: GeoPoint,
        val speedKmh: Float,
        val headingDeg: Float,
        val covarianceTrace: Float,
        val driftMeters: Float
    )

    fun reset(initialPosition: GeoPoint, initialHeadingDeg: Float)
    fun predict(dtSeconds: Float, accel: Vector3D, gyro: Vector3D, aiSpeedKmh: Float): StateEstimate
    fun updateGnss(gnssPosition: GeoPoint, speedKmh: Float, headingDeg: Float): StateEstimate
}
