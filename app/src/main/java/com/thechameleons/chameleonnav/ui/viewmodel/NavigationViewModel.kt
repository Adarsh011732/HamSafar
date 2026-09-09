package com.thechameleons.chameleonnav.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thechameleons.chameleonnav.engine.FusionNavigationEngine
import com.thechameleons.chameleonnav.engine.OfflineRoutingEngine
import com.thechameleons.chameleonnav.hardware.RealGnssManager
import com.thechameleons.chameleonnav.hardware.RealSensorDataSource
import com.thechameleons.chameleonnav.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel for Chameleon Nav.
 * Directly integrates physical Android hardware sensors (IMU @ 50Hz)
 * and real GNSS satellite receiver with the AI/ML Fusion Engine.
 * 100% dedicated to real-world operation without demo simulations.
 */
class NavigationViewModel(application: Application) : AndroidViewModel(application) {

    private val sensorDataSource = RealSensorDataSource(application)
    private val gnssManager = RealGnssManager(application, viewModelScope)
    private val fusionEngine = FusionNavigationEngine()
    private val routingEngine = OfflineRoutingEngine()
    private val roadRoutingService = com.thechameleons.chameleonnav.engine.RoadRoutingService(application, routingEngine)

    val offlineDestinations: List<Destination> = routingEngine.offlineDestinations

    private val _activeRoute = MutableStateFlow<OfflineRoute?>(null)
    val activeRoute: StateFlow<OfflineRoute?> = _activeRoute.asStateFlow()

    private val _showDestinationPicker = MutableStateFlow(false)
    val showDestinationPicker: StateFlow<Boolean> = _showDestinationPicker.asStateFlow()

    private val _telemetryState = MutableStateFlow(
        TelemetryData(
            statusMessage = "Real Hardware Multi-Sensor Fusion Active (AI + INS + NHC + ZUPT + Map Matching + GNSS)."
        )
    )
    val telemetryState: StateFlow<TelemetryData> = _telemetryState.asStateFlow()

    private val _referencePath = MutableStateFlow<List<GeoPoint>>(emptyList())
    val referencePath: StateFlow<List<GeoPoint>> = _referencePath.asStateFlow()

    private val _estimatedPath = MutableStateFlow<List<GeoPoint>>(emptyList())
    val estimatedPath: StateFlow<List<GeoPoint>> = _estimatedPath.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _showReferencePath = MutableStateFlow(true)
    val showReferencePath: StateFlow<Boolean> = _showReferencePath.asStateFlow()

    private val _isForcedOutage = MutableStateFlow(false)
    val isForcedOutage: StateFlow<Boolean> = _isForcedOutage.asStateFlow()

    private var loopJob: Job? = null
    private var tickCount = 0L

    init {
        // Pre-seed origin from persistent disk cache so app immediately opens at real user location
        gnssManager.getCachedLocation()?.let { cached ->
            fusionEngine.setOrigin(cached.position)
            _telemetryState.value = _telemetryState.value.copy(
                referencePosition = cached.position,
                estimatedPosition = cached.position,
                accuracyMeters = cached.accuracyMeters,
                altitudeMeters = cached.altitudeMeters
            )
            _referencePath.value = listOf(cached.position)
            _estimatedPath.value = listOf(cached.position)
        }

        // Start physical hardware sensors & GNSS listening immediately on app boot
        sensorDataSource.startListening()
        gnssManager.startListening()

        // Observe real GPS satellite constellation
        viewModelScope.launch {
            gnssManager.satellitesInView.collect { count ->
                _telemetryState.value = _telemetryState.value.copy(satellitesInView = count)
            }
        }

        viewModelScope.launch {
            gnssManager.satellitesUsedInFix.collect { used ->
                _telemetryState.value = _telemetryState.value.copy(satellitesUsedInFix = used)
            }
        }

        // Observe GPS lock state directly from hardware manager
        viewModelScope.launch {
            gnssManager.isGpsLocked.collect { locked ->
                val isLocked = locked && !_isForcedOutage.value
                _telemetryState.value = _telemetryState.value.copy(isGpsLocked = isLocked)
            }
        }

        // Observe real GPS fixes
        viewModelScope.launch {
            gnssManager.latestFix.collect { fix ->
                if (fix != null) {
                    val wasUninitialized = _telemetryState.value.estimatedPosition.latitude == 0.0 && _telemetryState.value.estimatedPosition.longitude == 0.0
                    val isLockedNow = fix.isFresh && !_isForcedOutage.value && gnssManager.isGpsLocked.value
                    fusionEngine.updateGnssFix(fix)

                    val currentEstimated = if (isLockedNow) {
                        fix.position
                    } else if (wasUninitialized) {
                        fix.position
                    } else {
                        _telemetryState.value.estimatedPosition
                    }

                    if (wasUninitialized || (isLockedNow && _referencePath.value.isEmpty())) {
                        _referencePath.value = listOf(fix.position)
                        _estimatedPath.value = listOf(currentEstimated)
                    }

                    _telemetryState.value = _telemetryState.value.copy(
                        referencePosition = fix.position,
                        estimatedPosition = currentEstimated,
                        altitudeMeters = fix.altitudeMeters,
                        accuracyMeters = fix.accuracyMeters,
                        isGpsLocked = isLockedNow,
                        statusMessage = if (isLockedNow) {
                            "GNSS Locked (${_telemetryState.value.satellitesUsedInFix} Sats). Multi-Sensor Fusion running."
                        } else {
                            "Autonomous Dead Reckoning Active (GPS Off / Denied)."
                        }
                    )
                }
            }
        }

        // Observe automated GPS outage detection
        viewModelScope.launch {
            gnssManager.isOutage.collect { isOutage ->
                if (isOutage || _isForcedOutage.value) {
                    onOutageTriggered()
                } else {
                    onGnssRestored()
                }
            }
        }

        // Start continuous 20Hz sensor fusion loop right away
        startNavigation()
    }

    fun startNavigation() {
        if (loopJob?.isActive == true) return

        sensorDataSource.startListening()
        gnssManager.startListening()

        _telemetryState.value = _telemetryState.value.copy(
            isNavigating = true,
            statusMessage = "Multi-Sensor Fusion Active (AI + INS + NHC + ZUPT + Map Matching + GNSS)."
        )

        loopJob = viewModelScope.launch {
            val dt = 0.05f // 50ms = 20Hz loop
            while (isActive) {
                stepFusion(dt)
                delay(50L)
            }
        }
    }

    fun stopNavigation() {
        loopJob?.cancel()
        loopJob = null

        _telemetryState.value = _telemetryState.value.copy(
            isNavigating = false,
            speedKmh = 0f,
            statusMessage = "Navigation paused."
        )
    }

    fun toggleForceOutage() {
        val newOutage = !_isForcedOutage.value
        _isForcedOutage.value = newOutage
        gnssManager.setForceOutage(newOutage)

        if (newOutage) {
            onOutageTriggered()
        } else {
            onGnssRestored()
        }
    }

    private fun onOutageTriggered() {
        fusionEngine.onOutageTriggered()
        _telemetryState.value = _telemetryState.value.copy(
            gnssStatus = GnssStatus.LOST,
            navigationMode = NavigationMode.DEAD_RECKONING,
            deadReckoningActive = true,
            isOutageActive = true,
            isRecoveryActive = false,
            bannerMessage = "⚠ GNSS SIGNAL LOST: AI + INS + NHC + ZUPT + Map Matching Active",
            bannerType = BannerType.WARNING,
            statusMessage = "GNSS Lost. Autonomous Multi-Sensor Fusion eliminating drift."
        )
    }

    private fun onGnssRestored() {
        val refPos = _telemetryState.value.referencePosition
        fusionEngine.onGnssRestored(refPos)

        _telemetryState.value = _telemetryState.value.copy(
            gnssStatus = GnssStatus.RESTORED,
            navigationMode = NavigationMode.RECOVERY,
            isOutageActive = false,
            isRecoveryActive = true,
            bannerMessage = "↻ GNSS RESTORED: Synchronizing position & correcting drift...",
            bannerType = BannerType.INFO,
            statusMessage = "Synchronizing position... Drift correction active."
        )
    }

    private fun stepFusion(dt: Float) {
        tickCount++

        val accel = sensorDataSource.accelerometer.value
        val gyro = sensorDataSource.gyroscope.value
        val compass = sensorDataSource.azimuthHeadingDeg.value

        val out = fusionEngine.step(dt, accel, gyro, compass)

        // Trajectory trail sampling (every 250ms)
        // Active when GPS is locked OR when Dead Reckoning is moving with a valid origin
        val hasValidPos = out.estimatedPosition.latitude != 0.0 && out.estimatedPosition.longitude != 0.0
        if (tickCount % 5L == 0L && hasValidPos) {
            if (_telemetryState.value.isGpsLocked) {
                _referencePath.value = (_referencePath.value + out.referencePosition).takeLast(200)
            }
            _estimatedPath.value = (_estimatedPath.value + out.estimatedPosition).takeLast(200)
        }

        val isLocked = _telemetryState.value.isGpsLocked
        val currentMode = if (out.isRecovering) {
            NavigationMode.RECOVERY
        } else if (_isForcedOutage.value || !isLocked) {
            NavigationMode.DEAD_RECKONING
        } else {
            NavigationMode.GNSS_INS
        }

        val currentGnssStatus = if (_isForcedOutage.value) {
            GnssStatus.LOST
        } else if (out.isRecovering) {
            GnssStatus.RESTORED
        } else if (isLocked) {
            GnssStatus.AVAILABLE
        } else if (gnssManager.isGpsEnabled.value) {
            GnssStatus.DEGRADED
        } else {
            GnssStatus.LOST
        }

        val bannerMsg = if (!out.isRecovering && _telemetryState.value.isRecoveryActive) {
            "✓ NAVIGATION SYNCHRONIZED"
        } else {
            _telemetryState.value.bannerMessage
        }

        val bannerType = if (!out.isRecovering && _telemetryState.value.isRecoveryActive) {
            BannerType.SUCCESS
        } else {
            _telemetryState.value.bannerType
        }

        val validEstPos = if (hasValidPos) out.estimatedPosition else _telemetryState.value.estimatedPosition
        val validRefPos = if (out.referencePosition.latitude != 0.0 && out.referencePosition.longitude != 0.0) {
            out.referencePosition
        } else {
            _telemetryState.value.referencePosition
        }

        // Real-time offline route progress tracking
        var routeProgress: OfflineRoutingEngine.RouteProgress? = null
        val currRoute = _activeRoute.value
        if (currRoute != null && validEstPos.latitude != 0.0) {
            routeProgress = routingEngine.evaluateProgress(validEstPos, out.speedKmh, currRoute)
        }

        _telemetryState.value = _telemetryState.value.copy(
            speedKmh = out.speedKmh,
            headingDeg = out.headingDeg,
            accuracyMeters = out.accuracyMeters,
            confidencePct = out.confidencePct,
            estimatedPosition = validEstPos,
            referencePosition = validRefPos,
            crossTrackDriftMeters = out.crossTrackDriftMeters,
            accelerometer = accel,
            gyroscope = gyro,
            aiSpeedEstimateKmh = out.fusionState.aiPredictedSpeedKmh,
            gnssStatus = currentGnssStatus,
            navigationMode = currentMode,
            isRecoveryActive = out.isRecovering,
            bannerMessage = bannerMsg,
            bannerType = bannerType,
            fusion = out.fusionState,
            activeRoute = currRoute,
            distanceToDestinationMeters = routeProgress?.remainingDistanceMeters ?: _telemetryState.value.distanceToDestinationMeters,
            bearingToDestinationDeg = if (currRoute != null && validEstPos.latitude != 0.0) validEstPos.bearingTo(currRoute.destination.location) else null,
            nextManeuverInstruction = routeProgress?.nextManeuver ?: _telemetryState.value.nextManeuverInstruction,
            nextManeuverDistanceMeters = routeProgress?.nextManeuverDistanceMeters ?: _telemetryState.value.nextManeuverDistanceMeters,
            nextTurnType = routeProgress?.nextTurnType ?: _telemetryState.value.nextTurnType
        )
    }

    fun openDestinationPicker() {
        _showDestinationPicker.value = true
    }

    fun closeDestinationPicker() {
        _showDestinationPicker.value = false
    }

    fun selectDestination(destination: Destination) {
        val currentPos = if (_telemetryState.value.estimatedPosition.latitude != 0.0) {
            _telemetryState.value.estimatedPosition
        } else {
            GeoPoint(28.6139, 77.2090) // Default fallback origin
        }

        // 1. Immediately provide an offline corridor route or cached route
        val initialRoute = roadRoutingService.getCachedRoute(currentPos, destination)
            ?: routingEngine.calculateRoute(currentPos, destination)
        _activeRoute.value = initialRoute
        _showDestinationPicker.value = false
        applyRouteToTelemetry(initialRoute, currentPos, destination)

        // 2. Query real-world road engine in background to get 100% turn-by-turn road geometry & actual vehicle driving duration
        viewModelScope.launch {
            try {
                val liveRoute = roadRoutingService.getVehicleRoute(currentPos, destination)
                _activeRoute.value = liveRoute
                applyRouteToTelemetry(liveRoute, currentPos, destination)
            } catch (_: Exception) {}
        }
    }

    private fun applyRouteToTelemetry(route: OfflineRoute, currentPos: GeoPoint, destination: Destination) {
        val progress = routingEngine.evaluateProgress(currentPos, _telemetryState.value.speedKmh, route)
        _telemetryState.value = _telemetryState.value.copy(
            activeRoute = route,
            distanceToDestinationMeters = progress.remainingDistanceMeters,
            bearingToDestinationDeg = currentPos.bearingTo(destination.location),
            nextManeuverInstruction = progress.nextManeuver,
            nextManeuverDistanceMeters = progress.nextManeuverDistanceMeters,
            nextTurnType = progress.nextTurnType
        )
    }

    fun clearRoute() {
        _activeRoute.value = null
        _telemetryState.value = _telemetryState.value.copy(
            activeRoute = null,
            distanceToDestinationMeters = null,
            bearingToDestinationDeg = null,
            nextManeuverInstruction = null,
            nextManeuverDistanceMeters = null,
            nextTurnType = null
        )
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun toggleReferencePath(show: Boolean) {
        _showReferencePath.value = show
    }

    fun reset() {
        fusionEngine.reset()
        _referencePath.value = emptyList()
        _estimatedPath.value = emptyList()
        _isForcedOutage.value = false
        gnssManager.setForceOutage(false)
        clearRoute()

        _telemetryState.value = _telemetryState.value.copy(
            crossTrackDriftMeters = 0f,
            isOutageActive = false,
            isRecoveryActive = false,
            bannerMessage = "Engine Reset. Hardware Sensors Active.",
            bannerType = BannerType.INFO
        )
    }

    fun setLocationPermissionGranted(granted: Boolean) {
        _telemetryState.value = _telemetryState.value.copy(hasLocationPermission = granted)
        if (granted) {
            gnssManager.startListening()
        }
    }

    fun checkLocationProviders() {
        gnssManager.checkAndRegisterLocationListeners()
    }

    fun resetNavigation() = reset()

    override fun onCleared() {
        super.onCleared()
        sensorDataSource.stopListening()
        gnssManager.stopListening()
        loopJob?.cancel()
    }
}
