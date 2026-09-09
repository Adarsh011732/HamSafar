package com.thechameleons.chameleonnav.engine

import com.thechameleons.chameleonnav.hardware.RealGnssFix
import com.thechameleons.chameleonnav.model.GeoPoint
import com.thechameleons.chameleonnav.model.Vector3D
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FusionNavigationEngineTest {

    private lateinit var engine: FusionNavigationEngine

    @Before
    fun setUp() {
        engine = FusionNavigationEngine()
    }

    @Test
    fun testGnssFixUpdatesPositionAndSpeed() {
        val fix = RealGnssFix(
            position = GeoPoint(28.6139, 77.2090),
            altitudeMeters = 215.0,
            speedKmh = 35.0f,
            headingDeg = 90.0f,
            accuracyMeters = 3.5f,
            timestampMs = System.currentTimeMillis(),
            isFresh = true
        )

        engine.updateGnssFix(fix)

        val out = engine.step(
            dt = 0.05f,
            accel = Vector3D(0.5f, 0.2f, 0.1f),
            gyro = Vector3D(0.01f, 0.01f, 0.01f),
            compassAzimuthDeg = 90.0f
        )

        assertEquals(28.6139, out.estimatedPosition.latitude, 0.0001)
        assertEquals(77.2090, out.estimatedPosition.longitude, 0.0001)
        assertTrue("Speed should be non-zero from GNSS fix", out.speedKmh > 0f)
    }

    @Test
    fun testDeadReckoningDuringGnssOutageCalculatesSpeed() {
        // Initial fix
        val initialFix = RealGnssFix(
            position = GeoPoint(28.6139, 77.2090),
            altitudeMeters = 215.0,
            speedKmh = 25.0f,
            headingDeg = 45.0f,
            accuracyMeters = 4.0f,
            timestampMs = System.currentTimeMillis(),
            isFresh = true
        )
        engine.updateGnssFix(initialFix)

        // Outage begins (e.g. entered tunnel or urban canyon)
        engine.onOutageTriggered()

        // Provide continuous physical sensor motion
        val dynamicAccel = Vector3D(1.2f, 0.4f, 0.3f)
        val dynamicGyro = Vector3D(0.02f, 0.01f, 0.02f)

        var lastOutput = engine.step(0.05f, dynamicAccel, dynamicGyro, 45.0f)
        for (i in 0 until 15) {
            lastOutput = engine.step(0.05f, dynamicAccel, dynamicGyro, 45.0f)
        }

        assertTrue("Outage must be flagged as active", lastOutput.isOutageActive)
        assertTrue("Speed must be calculated during dead reckoning (> 0 km/h)", lastOutput.speedKmh > 0f)
        assertTrue("Confidence should be maintained by multi-sensor fusion (>= 80%)", lastOutput.confidencePct >= 80)
    }

    @Test
    fun testStandstillClampsSpeedToZeroWithoutLockingFutureMovement() {
        val stillAccel = Vector3D(0.01f, 0.01f, 0.01f)
        val stillGyro = Vector3D(0.01f, 0.01f, 0.01f)

        // 1. Enter standstill
        var out = engine.step(0.05f, stillAccel, stillGyro, 0f)
        for (i in 0 until 10) {
            out = engine.step(0.05f, stillAccel, stillGyro, 0f)
        }
        assertEquals("Standstill should clamp speed to exactly 0.0 km/h", 0f, out.speedKmh, 0.01f)
        assertTrue("ZUPT should be active during standstill", out.fusionState.isZuptActive)

        // 2. Start movement (trigger outage / DR)
        engine.onOutageTriggered()
        val movingAccel = Vector3D(1.5f, 0.8f, 0.2f)
        for (i in 0 until 10) {
            out = engine.step(0.05f, movingAccel, stillGyro, 0f)
        }

        // Must successfully calculate speed and NOT be deadlocked at 0
        assertTrue("Moving after standstill must produce positive speed (> 0 km/h)", out.speedKmh > 0f)
        assertFalse("ZUPT must release upon motion", out.fusionState.isZuptActive)
    }

    @Test
    fun testOutageDriftRemainsBoundedAndAccuracyDoesNotExplodeTo14m() {
        val initialFix = RealGnssFix(
            position = GeoPoint(28.6139, 77.2090),
            altitudeMeters = 215.0,
            speedKmh = 0.0f,
            headingDeg = 90.0f,
            accuracyMeters = 3.5f,
            timestampMs = System.currentTimeMillis(),
            isFresh = true
        )
        engine.updateGnssFix(initialFix)
        engine.onOutageTriggered()

        val movingAccel = Vector3D(0.9f, 1.2f, 0.4f)
        val movingGyro = Vector3D(0.02f, 0.01f, 0.02f)

        var lastOutput = engine.step(0.05f, movingAccel, movingGyro, 90.0f)
        // Run 60 steps = 3.0 seconds
        for (i in 0 until 60) {
            lastOutput = engine.step(0.05f, movingAccel, movingGyro, 90.0f)
        }

        assertTrue("Outage must be active", lastOutput.isOutageActive)
        assertTrue("Drift must remain bounded (< 3.0m), actual: ${lastOutput.crossTrackDriftMeters}", lastOutput.crossTrackDriftMeters < 3.0f)
        assertTrue("Accuracy must not explode to 14m (< 7.0m), actual: ${lastOutput.accuracyMeters}", lastOutput.accuracyMeters < 7.0f)
    }
}
