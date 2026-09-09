package com.thechameleons.chameleonnav.model

data class Vector3D(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
)

data class TelemetryData(
    val speedKmh: Float = 0.0f,
    val headingDeg: Float = 0.0f,
    val accuracyMeters: Float = 12.0f,
    val confidencePct: Int = 90,
    val referencePosition: GeoPoint = GeoPoint(0.0, 0.0),
    val estimatedPosition: GeoPoint = GeoPoint(0.0, 0.0),
    val altitudeMeters: Double = 0.0,
    val crossTrackDriftMeters: Float = 0.0f,
    val accelerometer: Vector3D = Vector3D(0f, 0f, 9.81f),
    val gyroscope: Vector3D = Vector3D(0f, 0f, 0f),
    val aiSpeedEstimateKmh: Float = 0.0f,
    val aiInferenceLatencyMs: Int = 10,
    val imuActive: Boolean = true,
    val deadReckoningActive: Boolean = true,
    val ekfCovarianceTrace: Float = 0.018f,
    val gnssStatus: GnssStatus = GnssStatus.LOST,
    val navigationMode: NavigationMode = NavigationMode.DEAD_RECKONING,
    val statusMessage: String = "Autonomous Dead Reckoning (Awaiting GPS Fix). Multi-Sensor Fusion running.",
    val bannerMessage: String? = null,
    val bannerType: BannerType = BannerType.INFO,
    val activeWaypointIndex: Int = 0,
    val progressRatio: Float = 0f,
    val isNavigating: Boolean = false,
    val isOutageActive: Boolean = true,
    val isRecoveryActive: Boolean = false,
    val satellitesInView: Int = 0,
    val satellitesUsedInFix: Int = 0,
    val isRealHardwareActive: Boolean = true,
    val isGpsLocked: Boolean = false,
    val motionType: String = "STATIONARY",
    val stepCount: Int = 0,
    val hasLocationPermission: Boolean = false,
    val fusion: FusionState = FusionState(),
    val activeRoute: OfflineRoute? = null,
    val distanceToDestinationMeters: Double? = null,
    val bearingToDestinationDeg: Float? = null,
    val nextManeuverInstruction: String? = null,
    val nextManeuverDistanceMeters: Double? = null,
    val nextTurnType: TurnType? = null
)

enum class BannerType {
    INFO,
    WARNING,
    SUCCESS
}
