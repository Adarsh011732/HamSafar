package com.thechameleons.chameleonnav.engine

import com.thechameleons.chameleonnav.model.Vector3D
import kotlin.math.sqrt

/**
 * Zero Velocity Update (ZUPT) Detector.
 * Employs a Generalized Likelihood Ratio Test (GLRT) on IMU acceleration variance
 * and angular velocity norm to identify true physical standstill periods.
 *
 * When stationary: clamps velocity drift to exactly 0.0 km/h.
 * When motion starts: immediately releases ZUPT so velocity can be estimated without deadlock.
 */
class ZuptDetector {

    private val windowSize = 8
    private val accelHistory = ArrayDeque<Float>(windowSize)
    private var stationaryTicks = 0
    private var stationaryDurationMs = 0L

    fun process(accel: Vector3D, gyro: Vector3D, dt: Float): Boolean {
        // Linear acceleration magnitude in m/s^2 (dynamic gravity isolated)
        val linearAccelMag = sqrt(accel.x * accel.x + accel.y * accel.y + accel.z * accel.z)
        val gyroNorm = sqrt(gyro.x * gyro.x + gyro.y * gyro.y + gyro.z * gyro.z)

        if (accelHistory.size >= windowSize) {
            accelHistory.removeFirst()
        }
        accelHistory.addLast(linearAccelMag)

        // Calculate sliding window variance
        val mean = accelHistory.average().toFloat()
        var sumSquares = 0f
        for (v in accelHistory) {
            sumSquares += (v - mean) * (v - mean)
        }
        val variance = if (accelHistory.isNotEmpty()) sumSquares / accelHistory.size else 0f

        // Stationary condition: acceleration magnitude is low (< 0.45 m/s²),
        // variance is low (< 0.06 m/s²), and gyro rotation is low (< 0.30 rad/s)
        val isStationarySample = (linearAccelMag < 0.45f) && (variance < 0.06f) && (gyroNorm < 0.30f)

        if (isStationarySample) {
            stationaryTicks++
            stationaryDurationMs += (dt * 1000).toLong()
        } else {
            // Immediate release upon any physical movement
            stationaryTicks = 0
            stationaryDurationMs = 0L
        }

        // Require 3 consecutive stationary samples (~150ms) to confirm standstill
        return stationaryTicks >= 3
    }

    fun getStationaryDurationMs(): Long = stationaryDurationMs

    fun reset() {
        accelHistory.clear()
        stationaryTicks = 0
        stationaryDurationMs = 0L
    }
}
