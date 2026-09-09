package com.thechameleons.chameleonnav.model

/**
 * Represents a user-selected destination for offline navigation.
 */
data class Destination(
    val id: String,
    val name: String,
    val location: GeoPoint,
    val category: String = "Landmark",
    val description: String = "",
    val keywords: List<String> = emptyList()
)

/**
 * Types of navigational maneuvers for turn-by-turn guidance.
 */
enum class TurnType {
    START,
    STRAIGHT,
    TURN_LEFT,
    TURN_RIGHT,
    SLIGHT_LEFT,
    SLIGHT_RIGHT,
    U_TURN,
    REACHED
}

/**
 * Individual leg / step in an offline route.
 */
data class RouteStep(
    val instruction: String,
    val distanceMeters: Double,
    val targetPoint: GeoPoint,
    val turnType: TurnType,
    val streetName: String = ""
)

/**
 * A calculated 100% offline route from current position to a chosen destination.
 */
data class OfflineRoute(
    val destination: Destination,
    val waypoints: List<GeoPoint>,
    val totalDistanceMeters: Double,
    val estimatedDurationSeconds: Double,
    val steps: List<RouteStep>
) {
    fun formatDistance(): String {
        return if (totalDistanceMeters >= 1000.0) {
            String.format(java.util.Locale.US, "%.1f km", totalDistanceMeters / 1000.0)
        } else {
            String.format(java.util.Locale.US, "%.0f m", totalDistanceMeters)
        }
    }

    fun formatDuration(): String {
        val minutes = (estimatedDurationSeconds / 60.0).toInt()
        return when {
            minutes < 1 -> "< 1 min"
            minutes < 60 -> "$minutes min"
            else -> {
                val hours = minutes / 60
                val remMins = minutes % 60
                "${hours}h ${remMins}m"
            }
        }
    }
}
