package com.thechameleons.chameleonnav.model

enum class GnssStatus(val label: String) {
    AVAILABLE("AVAILABLE"),
    DEGRADED("DEGRADED"),
    LOST("LOST"),
    RESTORED("RESTORED")
}

enum class NavigationMode(val label: String, val subtitle: String) {
    GNSS_INS(
        label = "GNSS + INS",
        subtitle = "Optimal Satellite Fix & INS Fusion"
    ),
    DEGRADED(
        label = "DEGRADED",
        subtitle = "Multipath Interference / Low Satellites"
    ),
    DEAD_RECKONING(
        label = "DEAD RECKONING",
        subtitle = "AI + INS + NHC + ZUPT + Map Matching"
    ),
    RECOVERY(
        label = "RECOVERY",
        subtitle = "Smooth Drift Correction Active"
    )
}
