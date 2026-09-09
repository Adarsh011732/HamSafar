package com.thechameleons.chameleonnav.simulation

import com.thechameleons.chameleonnav.domain.interfaces.NavigationEngine
import com.thechameleons.chameleonnav.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

/**
 * High-fidelity deterministic simulation manager.
 * Drives the navigation loop, GNSS outage drift, and smooth recovery transitions.
 */
class DemoSimulationManager(
    private val coroutineScope: CoroutineScope
) : NavigationEngine {

    private val _telemetryState = MutableStateFlow(
        TelemetryData(
            speedKmh = 42.5f,
            headingDeg = 128f,
            accuracyMeters = 4.2f,
            confidencePct = 98,
            referencePosition = RouteProvider.routePoints.first(),
            estimatedPosition = RouteProvider.routePoints.first(),
            crossTrackDriftMeters = 0.0f,
            gnssStatus = GnssStatus.AVAILABLE,
            navigationMode = NavigationMode.GNSS_INS,
            statusMessage = "GNSS Fix Established. Normal INS fusion."
        )
    )
    override val telemetryState: StateFlow<TelemetryData> = _telemetryState.asStateFlow()

    // Trajectory history for rendering on the map canvas
    private val _referencePath = MutableStateFlow<List<GeoPoint>>(listOf(RouteProvider.routePoints.first()))
    val referencePath: StateFlow<List<GeoPoint>> = _referencePath.asStateFlow()

    private val _estimatedPath = MutableStateFlow<List<GeoPoint>>(listOf(RouteProvider.routePoints.first()))
    val estimatedPath: StateFlow<List<GeoPoint>> = _estimatedPath.asStateFlow()

    private var simulationJob: Job? = null
    private var distanceTraveledMeters: Double = 0.0
    private var simulationSpeedMultiplier: Float = 1.0f

    // Outage and recovery tracking
    private var isOutageActive = false
    private var outageDurationSec = 0f
    private var isRecovering = false
    private var recoveryProgress = 0f // 0.0 to 1.0
    private var driftAtRecoveryStart = 0f
    private var driftAngleAtRecoveryStart = 0.0

    // Simulation metrics
    private var baseSpeedKmh = 42.5f
    private var currentHeading = 128f
    private var previousHeading = 128f
    private var tickCount = 0L

    override fun startNavigation() {
        if (simulationJob?.isActive == true) return

        _telemetryState.value = _telemetryState.value.copy(
            isNavigating = true,
            statusMessage = if (isOutageActive) "Dead Reckoning active along route." else "Navigating with GNSS + INS fix."
        )

        simulationJob = coroutineScope.launch(Dispatchers.Default) {
            val tickIntervalMs = 50L // 20Hz update rate
            while (isActive) {
                val dt = (tickIntervalMs / 1000f) * simulationSpeedMultiplier
                stepSimulation(dt)
                delay(tickIntervalMs)
            }
        }
    }

    override fun stopNavigation() {
        simulationJob?.cancel()
        simulationJob = null
        _telemetryState.value = _telemetryState.value.copy(
            isNavigating = false,
            speedKmh = 0.0f,
            aiSpeedEstimateKmh = 0.0f,
            statusMessage = "Navigation paused."
        )
    }

    override fun simulateGnssOutage() {
        isOutageActive = true
        outageDurationSec = 0f
        isRecovering = false

        _telemetryState.value = _telemetryState.value.copy(
            gnssStatus = GnssStatus.LOST,
            navigationMode = NavigationMode.DEAD_RECKONING,
            deadReckoningActive = true,
            isOutageActive = true,
            isRecoveryActive = false,
            bannerMessage = "GNSS SIGNAL LOST: Switching to AI-Assisted Dead Reckoning...",
            bannerType = BannerType.WARNING,
            statusMessage = "GNSS Lost. Autonomous AI Dead Reckoning maintaining track."
        )
    }

    override fun restoreGnss() {
        if (!isOutageActive && !isRecovering) return

        isOutageActive = false
        isRecovering = true
        recoveryProgress = 0f
        driftAtRecoveryStart = _telemetryState.value.crossTrackDriftMeters

        _telemetryState.value = _telemetryState.value.copy(
            gnssStatus = GnssStatus.RESTORED,
            navigationMode = NavigationMode.RECOVERY,
            isOutageActive = false,
            isRecoveryActive = true,
            bannerMessage = "GNSS RESTORED: Synchronizing position & correcting drift...",
            bannerType = BannerType.INFO,
            statusMessage = "Synchronizing position... Drift correction active."
        )
    }

    override fun setSimulationSpeed(multiplier: Float) {
        simulationSpeedMultiplier = multiplier
    }

    override fun reset() {
        simulationJob?.cancel()
        simulationJob = null
        distanceTraveledMeters = 0.0
        isOutageActive = false
        isRecovering = false
        outageDurationSec = 0f
        recoveryProgress = 0f

        val startPoint = RouteProvider.routePoints.first()
        _referencePath.value = listOf(startPoint)
        _estimatedPath.value = listOf(startPoint)

        _telemetryState.value = TelemetryData(
            speedKmh = 0.0f,
            headingDeg = 128f,
            accuracyMeters = 4.2f,
            confidencePct = 98,
            referencePosition = startPoint,
            estimatedPosition = startPoint,
            crossTrackDriftMeters = 0.0f,
            gnssStatus = GnssStatus.AVAILABLE,
            navigationMode = NavigationMode.GNSS_INS,
            statusMessage = "Ready. Press START NAVIGATION.",
            bannerMessage = null,
            isNavigating = false,
            isOutageActive = false,
            isRecoveryActive = false
        )
    }

    private fun stepSimulation(dt: Float) {
        tickCount++

        // 1. Calculate vehicle speed with realistic micro-variations
        val speedVariation = sin(tickCount * 0.05f) * 1.5f + cos(tickCount * 0.12f) * 0.8f
        val currentSpeedKmh = (baseSpeedKmh + speedVariation).coerceIn(34f, 52f)
        val speedMetersPerSec = (currentSpeedKmh * 1000.0) / 3600.0

        // Advance distance along route
        distanceTraveledMeters += speedMetersPerSec * dt
        val sample = RouteProvider.getPositionAtDistance(distanceTraveledMeters)
        val refPos = sample.position

        previousHeading = currentHeading
        currentHeading = sample.headingDeg
        val yawRateDegPerSec = (currentHeading - previousHeading) / dt
        val yawRateRadPerSec = Math.toRadians(yawRateDegPerSec.toDouble()).toFloat()

        // 2. Dead Reckoning & Drift Dynamics
        var currentDriftMeters = 0f
        var estPos = refPos
        var confidence = 98
        var accuracy = 4.2f
        var covTrace = 0.042f

        if (isOutageActive) {
            outageDurationSec += dt

            // Realistic cross-track & along-track drift curve
            // Builds up gradually up to ~14 meters over 25 seconds
            val maxDrift = 14.0f
            val driftFactor = (1.0f - exp(-outageDurationSec / 12.0f))
            currentDriftMeters = driftFactor * maxDrift

            // Confidence drops gradually: 98% -> 94% -> 91% -> 88% -> 82%
            confidence = (98 - (outageDurationSec * 0.7f).roundToInt()).coerceAtLeast(78)
            accuracy = (4.2f + (outageDurationSec * 0.55f)).coerceAtMost(19.5f)
            covTrace = (0.042f + (outageDurationSec * 0.015f)).coerceAtMost(0.35f)

            // Drift offset perpendicular to heading (East/North displacement in meters)
            val driftHeading = Math.toRadians((currentHeading + 90.0) % 360.0)
            val metersToDegLat = 1.0 / 111139.0
            val metersToDegLon = 1.0 / (111139.0 * cos(Math.toRadians(refPos.latitude)))

            val dLat = currentDriftMeters * cos(driftHeading) * metersToDegLat
            val dLon = currentDriftMeters * sin(driftHeading) * metersToDegLon
            estPos = GeoPoint(refPos.latitude + dLat, refPos.longitude + dLon)

        } else if (isRecovering) {
            // Smooth recovery convergence over 2.0 seconds
            val recoveryDuration = 2.0f
            recoveryProgress += dt / recoveryDuration

            if (recoveryProgress >= 1.0f) {
                // Recovery complete!
                isRecovering = false
                recoveryProgress = 1.0f
                currentDriftMeters = 0f
                estPos = refPos
                confidence = 98
                accuracy = 4.2f
                covTrace = 0.042f

                _telemetryState.value = _telemetryState.value.copy(
                    gnssStatus = GnssStatus.AVAILABLE,
                    navigationMode = NavigationMode.GNSS_INS,
                    deadReckoningActive = false,
                    isRecoveryActive = false,
                    bannerMessage = "✓ NAVIGATION SYNCHRONIZED",
                    bannerType = BannerType.SUCCESS,
                    statusMessage = "All systems synchronized. GNSS + INS active."
                )
            } else {
                // Cubic ease-out interpolation
                val t = recoveryProgress.coerceIn(0f, 1f)
                val smoothFactor = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t)
                currentDriftMeters = driftAtRecoveryStart * (1.0f - smoothFactor)

                val driftHeading = Math.toRadians((currentHeading + 90.0) % 360.0)
                val metersToDegLat = 1.0 / 111139.0
                val metersToDegLon = 1.0 / (111139.0 * cos(Math.toRadians(refPos.latitude)))

                val dLat = currentDriftMeters * cos(driftHeading) * metersToDegLat
                val dLon = currentDriftMeters * sin(driftHeading) * metersToDegLon
                estPos = GeoPoint(refPos.latitude + dLat, refPos.longitude + dLon)

                confidence = (80 + (18 * smoothFactor).roundToInt()).coerceAtMost(98)
                accuracy = 4.2f + (1.0f - smoothFactor) * 8.0f
                covTrace = 0.042f + (1.0f - smoothFactor) * 0.15f
            }
        } else {
            // Normal GNSS+INS
            confidence = 98
            accuracy = 4.2f
            covTrace = 0.042f
            currentDriftMeters = 0f
            estPos = refPos
        }

        // 3. Realistic 6-Axis IMU sensor generation
        val ax = (speedVariation * 0.15f) + (sin(tickCount * 0.3f) * 0.03f)
        val ay = (speedMetersPerSec.toFloat() * yawRateRadPerSec * 0.5f) + (cos(tickCount * 0.4f) * 0.04f)
        val az = 9.81f + (sin(tickCount * 0.8f) * 0.12f)

        val gx = (sin(tickCount * 0.25f) * 0.008f)
        val gy = (cos(tickCount * 0.2f) * 0.012f)
        val gz = yawRateRadPerSec + (sin(tickCount * 0.6f) * 0.005f)

        // AI speed estimate (tight correlation to true speed with subtle neural latency / estimation error)
        val aiSpeedEstimate = currentSpeedKmh + (sin(tickCount * 0.1f) * 0.35f)

        // 4. Update path histories (sample every 5 ticks to keep memory efficient)
        if (tickCount % 5 == 0L) {
            val updatedRef = (_referencePath.value + refPos).takeLast(120)
            val updatedEst = (_estimatedPath.value + estPos).takeLast(120)
            _referencePath.value = updatedRef
            _estimatedPath.value = updatedEst
        }

        val totalDist = RouteProvider.totalRouteDistanceMeters
        val progress = (distanceTraveledMeters / totalDist).toFloat().coerceIn(0f, 1f)
        val matchedRoad = RouteProvider.demoWaypoints.getOrNull(sample.segmentIndex)?.name ?: "Kartavya Path"

        val simFusion = FusionState(
            isAiActive = true,
            aiModelName = "1D-CNN + Bi-GRU (IO-VNBD)",
            aiPredictedSpeedKmh = aiSpeedEstimate,
            isInsActive = true,
            isNhcApplied = true,
            isZuptActive = currentSpeedKmh < 1.0f,
            isMapMatched = true,
            matchedRoadName = matchedRoad,
            isGnssFused = !isOutageActive
        )

        // Update telemetry state
        _telemetryState.value = _telemetryState.value.copy(
            speedKmh = currentSpeedKmh,
            headingDeg = currentHeading,
            accuracyMeters = accuracy,
            confidencePct = confidence,
            referencePosition = refPos,
            estimatedPosition = estPos,
            crossTrackDriftMeters = currentDriftMeters,
            accelerometer = Vector3D(ax, ay, az),
            gyroscope = Vector3D(gx, gy, gz),
            aiSpeedEstimateKmh = aiSpeedEstimate,
            aiInferenceLatencyMs = 12 + (abs(sin(tickCount * 0.1)) * 3).roundToInt(),
            imuActive = true,
            ekfCovarianceTrace = covTrace,
            activeWaypointIndex = sample.segmentIndex,
            progressRatio = progress,
            fusion = simFusion
        )
    }
}
