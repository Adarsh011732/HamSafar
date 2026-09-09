package com.thechameleons.chameleonnav.domain.interfaces

import com.thechameleons.chameleonnav.model.Vector3D

/**
 * Interface for AI/ML Inference engine.
 * In Phase 2, this will be implemented by TFLite / ONNX runtime models
 * to estimate instantaneous velocity, stride length, and vehicle dynamics
 * directly from raw 6-axis IMU features.
 */
interface AIInference {
    data class SpeedEstimate(
        val speedKmh: Float,
        val confidence: Float,
        val latencyMs: Int
    )

    fun estimateSpeed(
        accel: Vector3D,
        gyro: Vector3D,
        previousSpeedKmh: Float
    ): SpeedEstimate

    fun isModelLoaded(): Boolean
}
