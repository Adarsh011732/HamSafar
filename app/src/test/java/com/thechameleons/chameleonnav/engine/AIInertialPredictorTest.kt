package com.thechameleons.chameleonnav.engine

import com.thechameleons.chameleonnav.model.Vector3D
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AIInertialPredictorTest {

    private lateinit var predictor: AIInertialPredictor

    @Before
    fun setUp() {
        predictor = AIInertialPredictor()
    }

    @Test
    fun testStationaryDecaysSpeedToZero() {
        val stillAccel = Vector3D(0f, 0f, 0f)
        val stillGyro = Vector3D(0f, 0f, 0f)

        for (i in 0 until 10) {
            val res = predictor.predict(stillAccel, stillGyro, 0.05f, isZupt = true)
            assertEquals("Standstill should yield 0.0 km/h", 0f, res.predictedSpeedKmh, 0.001f)
        }
    }

    @Test
    fun testDynamicMotionInfersNonZeroSpeed() {
        val walkingAccel = Vector3D(0.8f, 1.2f, 0.5f)
        val walkingGyro = Vector3D(0.05f, 0.04f, 0.08f)

        var lastSpeed = 0f
        for (i in 0 until 20) {
            val res = predictor.predict(walkingAccel, walkingGyro, 0.05f, isZupt = false)
            lastSpeed = res.predictedSpeedKmh
        }

        assertTrue("Dynamic motion should produce non-zero speed", lastSpeed > 0f)
        assertTrue("Speed estimate should be reasonable (> 1.0 km/h)", lastSpeed >= 1.0f)
    }

    @Test
    fun testCentripetalAccelerationBoostsSpeed() {
        val turningAccel = Vector3D(1.5f, 2.5f, 0.2f)
        val turningGyro = Vector3D(0.0f, 0.0f, 0.35f) // High yaw rate

        var speed = 0f
        for (i in 0 until 25) {
            val res = predictor.predict(turningAccel, turningGyro, 0.05f, isZupt = false)
            speed = res.predictedSpeedKmh
        }

        assertTrue("Turning with lateral accel should infer vehicular speed (> 10 km/h)", speed > 10f)
    }

    @Test
    fun testWalkingSpeedDoesNotExceedHumanLimit() {
        val walkingAccel = Vector3D(0.8f, 1.2f, 0.4f)
        val walkingGyro = Vector3D(0.04f, 0.03f, 0.05f)

        var speed = 0f
        for (i in 0 until 40) {
            val res = predictor.predict(walkingAccel, walkingGyro, 0.05f, isZupt = false)
            speed = res.predictedSpeedKmh
        }

        assertTrue("Walking speed should be >= 2.0 km/h", speed >= 2.0f)
        assertTrue("Walking speed must not be artificially high (<= 8.0 km/h), actual: $speed", speed <= 8.0f)
    }

    @Test
    fun testMinorDisplacementInArbitraryAxisDoesNotTriggerForwardSpeed() {
        // Pure Z-axis lift or slight lateral Y displacement (< 0.40 m/s²) without walking cadence
        val verticalLiftAccel = Vector3D(0.05f, 0.05f, 0.32f)
        val handGyro = Vector3D(0.04f, 0.04f, 0.05f)

        for (i in 0 until 15) {
            predictor.predict(verticalLiftAccel, handGyro, 0.05f, isZupt = false)
        }
        val result = predictor.predict(verticalLiftAccel, handGyro, 0.05f, isZupt = false)
        assertEquals("Minor arbitrary axis displacement must stay in deadband at 0.0 km/h", 0f, result.predictedSpeedKmh, 0.01f)
    }
}
