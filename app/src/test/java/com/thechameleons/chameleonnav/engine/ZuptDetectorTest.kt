package com.thechameleons.chameleonnav.engine

import com.thechameleons.chameleonnav.model.Vector3D
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ZuptDetectorTest {

    private lateinit var zupt: ZuptDetector

    @Before
    fun setUp() {
        zupt = ZuptDetector()
    }

    @Test
    fun testStationaryTriggersZuptAfterWindow() {
        val stillAccel = Vector3D(0.01f, 0.02f, 0.01f)
        val stillGyro = Vector3D(0.01f, 0.01f, 0.01f)

        // Samples 1 and 2: Still warming up
        assertFalse(zupt.process(stillAccel, stillGyro, 0.05f))
        assertFalse(zupt.process(stillAccel, stillGyro, 0.05f))

        // Sample 3 onwards: Confirmed standstill
        assertTrue(zupt.process(stillAccel, stillGyro, 0.05f))
        assertTrue(zupt.process(stillAccel, stillGyro, 0.05f))
        assertTrue(zupt.getStationaryDurationMs() > 0)
    }

    @Test
    fun testPhysicalMotionReleasesZuptImmediatelyWithoutDeadlock() {
        val stillAccel = Vector3D(0.01f, 0.01f, 0.01f)
        val stillGyro = Vector3D(0.01f, 0.01f, 0.01f)

        // First reach standstill
        for (i in 0 until 5) {
            zupt.process(stillAccel, stillGyro, 0.05f)
        }
        assertTrue("Should be stationary initially", zupt.process(stillAccel, stillGyro, 0.05f))

        // User starts moving (acceleration surge)
        val movingAccel = Vector3D(1.2f, 0.4f, 0.3f)
        val isZuptOnMotion = zupt.process(movingAccel, stillGyro, 0.05f)

        // Must instantly release standstill so speed can be calculated
        assertFalse("Motion must immediately release ZUPT", isZuptOnMotion)
        assertEquals("Stationary duration should reset to 0", 0L, zupt.getStationaryDurationMs())
    }

    @Test
    fun testRotationReleasesZupt() {
        val stillAccel = Vector3D(0.02f, 0.02f, 0.02f)
        val turningGyro = Vector3D(0.0f, 0.0f, 0.65f) // Turning 0.65 rad/s

        // High angular velocity should not allow ZUPT
        for (i in 0 until 5) {
            assertFalse(zupt.process(stillAccel, turningGyro, 0.05f))
        }
    }
}
