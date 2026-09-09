package com.thechameleons.chameleonnav.model

data class FusionState(
    // 1. AI (IO-VNBD Neural Odometry)
    val isAiActive: Boolean = true,
    val aiPredictedSpeedKmh: Float = 0.0f,
    val aiModelName: String = "1D-CNN + Bi-GRU (IO-VNBD)",
    val aiDataset: String = "IO-VNBD Vehicular Benchmark",
    val aiInferenceLatencyMs: Int = 10,

    // 2. INS (Inertial Navigation System Mechanization)
    val isInsActive: Boolean = true,
    val insHeadingDeg: Float = 0.0f,
    val forwardAccelFiltered: Float = 0.0f,

    // 3. NHC (Non-Holonomic Constraints)
    val isNhcApplied: Boolean = true,
    val lateralVelocityVyClamped: Float = 0.0f,
    val verticalVelocityVzClamped: Float = 0.0f,

    // 4. ZUPT (Zero Velocity Update)
    val isZuptActive: Boolean = false,
    val stationaryDurationMs: Long = 0L,

    // 5. Map Matching
    val isMapMatched: Boolean = false,
    val matchedRoadName: String = "Kartavya Path Corridor",
    val crossTrackErrorMeters: Float = 0.0f,
    val headingAlignmentOffsetDeg: Float = 0.0f,

    // 6. GNSS Fusion & EKF
    val isGnssFused: Boolean = true,
    val ekfCovarianceTrace: Float = 0.018f,
    val innovationResidualMeters: Float = 0.0f
)

data class FusionEngineOutput(
    val estimatedPosition: GeoPoint,
    val referencePosition: GeoPoint,
    val speedKmh: Float,
    val headingDeg: Float,
    val accuracyMeters: Float,
    val confidencePct: Int,
    val crossTrackDriftMeters: Float,
    val fusionState: FusionState,
    val isOutageActive: Boolean,
    val isRecovering: Boolean
)
