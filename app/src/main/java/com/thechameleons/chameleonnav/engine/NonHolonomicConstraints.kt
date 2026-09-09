package com.thechameleons.chameleonnav.engine

import com.thechameleons.chameleonnav.model.Vector3D
import kotlin.math.abs

/**
 * Non-Holonomic Constraints (NHC) for Land Vehicles.
 * In a wheeled vehicle traveling on a road surface without skidding:
 * 1. Lateral velocity (v_y) orthogonal to heading must be approximately zero (no sideways sliding).
 * 2. Vertical velocity (v_z) normal to road plane must be approximately zero (no jumping/flying).
 *
 * Enforcing NHC radically eliminates lateral IMU divergence and keeps dead reckoning bounded.
 */
class NonHolonomicConstraints {

    data class NhcResult(
        val forwardVelocityVx: Float,
        val clampedLateralVelocityVy: Float,
        val clampedVerticalVelocityVz: Float,
        val isApplied: Boolean
    )

    fun apply(
        rawVelocity: Vector3D,
        isVehicleMoving: Boolean
    ): NhcResult {
        if (!isVehicleMoving) {
            return NhcResult(0f, 0f, 0f, true)
        }

        // Forward velocity is preserved along the vehicle's primary travel axis
        val vx = rawVelocity.x.coerceAtLeast(0f)

        // Lateral and vertical velocities are clamped to near-zero (NHC pseudo-measurements)
        val clampedVy = rawVelocity.y * 0.05f // 95% suppression of lateral sliding
        val clampedVz = rawVelocity.z * 0.02f // 98% suppression of vertical bounce

        return NhcResult(
            forwardVelocityVx = vx,
            clampedLateralVelocityVy = clampedVy,
            clampedVerticalVelocityVz = clampedVz,
            isApplied = true
        )
    }
}
