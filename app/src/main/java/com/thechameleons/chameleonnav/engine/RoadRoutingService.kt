package com.thechameleons.chameleonnav.engine

import android.content.Context
import com.thechameleons.chameleonnav.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Real-World Vehicle Roadway & Highway Routing Service.
 * Powered by OpenStreetMap / OSRM (Contraction Hierarchies road network routing).
 *
 * Guarantees routes follow real roads, expressways, flyovers, and turns
 * rather than arbitrary straight lines or synthetic grid lines.
 *
 * Includes automatic disk & memory caching so once a vehicle route is loaded,
 * it remains 100% available offline for Dead Reckoning navigation.
 */
class RoadRoutingService(
    private val context: Context,
    private val offlineEngine: OfflineRoutingEngine = OfflineRoutingEngine()
) {
    private val memoryCache = ConcurrentHashMap<String, OfflineRoute>()
    private val cacheDir: File by lazy {
        File(context.cacheDir, "routes").apply { if (!exists()) mkdirs() }
    }

    private fun cacheKey(start: GeoPoint, destination: Destination): String {
        val sLat = (start.latitude * 100).roundToInt()
        val sLon = (start.longitude * 100).roundToInt()
        val dLat = (destination.location.latitude * 100).roundToInt()
        val dLon = (destination.location.longitude * 100).roundToInt()
        return "${sLat}_${sLon}_to_${destination.id}_${dLat}_${dLon}"
    }

    /**
     * Gets a cached vehicle route if available (either in memory or on disk).
     */
    fun getCachedRoute(start: GeoPoint, destination: Destination): OfflineRoute? {
        val key = cacheKey(start, destination)
        memoryCache[key]?.let { return it }

        // Try reading disk cache
        try {
            val file = File(cacheDir, "route_$key.json")
            if (file.exists()) {
                val jsonStr = file.readText()
                val parsed = parseRouteJson(jsonStr, destination)
                if (parsed != null) {
                    memoryCache[key] = parsed
                    return parsed
                }
            }
        } catch (_: Exception) {}

        return null
    }

    /**
     * Saves a calculated route to memory and persistent disk cache.
     */
    fun saveRouteToCache(start: GeoPoint, destination: Destination, route: OfflineRoute) {
        val key = cacheKey(start, destination)
        memoryCache[key] = route
        try {
            val file = File(cacheDir, "route_$key.json")
            val json = JSONObject().apply {
                put("dist", route.totalDistanceMeters)
                put("dur", route.estimatedDurationSeconds)
                val coordsArr = org.json.JSONArray()
                for (wp in route.waypoints) {
                    val ptArr = org.json.JSONArray()
                    ptArr.put(wp.latitude)
                    ptArr.put(wp.longitude)
                    coordsArr.put(ptArr)
                }
                put("coords", coordsArr)
            }
            file.writeText(json.toString())
        } catch (_: Exception) {}
    }

    /**
     * Fetches a genuine road/highway route for vehicles.
     * 1. Checks memory & disk cache.
     * 2. If connected, queries OSRM for exact highway & street geometry.
     * 3. Falls back to offline corridor routing if network unavailable.
     */
    suspend fun getVehicleRoute(start: GeoPoint, destination: Destination): OfflineRoute {
        // 1. Check cache first
        val cached = getCachedRoute(start, destination)
        if (cached != null) return cached

        // 2. Try fetching real road network route via OSRM
        val liveRoute = fetchFromOsrm(start, destination)
        if (liveRoute != null) {
            saveRouteToCache(start, destination, liveRoute)
            return liveRoute
        }

        // 3. Fallback to high-precision offline road engine
        val offlineRoute = offlineEngine.calculateRoute(start, destination)
        saveRouteToCache(start, destination, offlineRoute)
        return offlineRoute
    }

    /**
     * Queries OSRM for exact turn-by-turn road geometry.
     */
    suspend fun fetchFromOsrm(start: GeoPoint, destination: Destination): OfflineRoute? = withContext(Dispatchers.IO) {
        try {
            val sLon = String.format(java.util.Locale.US, "%.5f", start.longitude)
            val sLat = String.format(java.util.Locale.US, "%.5f", start.latitude)
            val dLon = String.format(java.util.Locale.US, "%.5f", destination.location.longitude)
            val dLat = String.format(java.util.Locale.US, "%.5f", destination.location.latitude)

            val urlStr = "https://router.project-osrm.org/route/v1/driving/$sLon,$sLat;$dLon,$dLat?overview=full&geometries=geojson&steps=true"
            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "HamSafar/1.0 (Android; OpenStreetMap)")
            }

            if (conn.responseCode != 200) return@withContext null

            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line)
            }
            reader.close()

            val root = JSONObject(sb.toString())
            if (root.optString("code") != "Ok") return@withContext null

            val routes = root.optJSONArray("routes") ?: return@withContext null
            if (routes.length() == 0) return@withContext null

            val firstRoute = routes.getJSONObject(0)
            val distMeters = firstRoute.optDouble("distance", 0.0)
            val durSec = firstRoute.optDouble("duration", 0.0)

            // Parse waypoints from GeoJSON coordinates: [[lon, lat], ...]
            val geom = firstRoute.optJSONObject("geometry") ?: return@withContext null
            val coords = geom.optJSONArray("coordinates") ?: return@withContext null
            val waypoints = ArrayList<GeoPoint>(coords.length())
            for (i in 0 until coords.length()) {
                val pt = coords.getJSONArray(i)
                val lon = pt.getDouble(0)
                val lat = pt.getDouble(1)
                waypoints.add(GeoPoint(lat, lon))
            }

            // Parse turn-by-turn road steps
            val steps = mutableListOf<RouteStep>()
            val legs = firstRoute.optJSONArray("legs")
            if (legs != null && legs.length() > 0) {
                val stepsArr = legs.getJSONObject(0).optJSONArray("steps")
                if (stepsArr != null) {
                    for (i in 0 until stepsArr.length()) {
                        val stepObj = stepsArr.getJSONObject(i)
                        val stepDist = stepObj.optDouble("distance", 0.0)
                        val streetName = stepObj.optString("name", "")
                        val manObj = stepObj.optJSONObject("maneuver")
                        val manType = manObj?.optString("type", "turn") ?: "turn"
                        val modifier = manObj?.optString("modifier", "") ?: ""

                        val turnType = when {
                            manType == "arrive" -> TurnType.REACHED
                            manType == "depart" -> TurnType.START
                            modifier.contains("sharp right") -> TurnType.TURN_RIGHT
                            modifier.contains("slight right") -> TurnType.SLIGHT_RIGHT
                            modifier.contains("right") -> TurnType.TURN_RIGHT
                            modifier.contains("sharp left") -> TurnType.TURN_LEFT
                            modifier.contains("slight left") -> TurnType.SLIGHT_LEFT
                            modifier.contains("left") -> TurnType.TURN_LEFT
                            modifier.contains("uturn") -> TurnType.U_TURN
                            else -> TurnType.STRAIGHT
                        }

                        val stepInstruction = when (turnType) {
                            TurnType.REACHED -> "Arrive at ${destination.name}"
                            TurnType.START -> if (streetName.isNotBlank()) "Start on $streetName" else "Start journey towards ${destination.name}"
                            TurnType.TURN_RIGHT -> if (streetName.isNotBlank()) "Turn right onto $streetName" else "Turn right"
                            TurnType.SLIGHT_RIGHT -> if (streetName.isNotBlank()) "Keep right onto $streetName" else "Bear right"
                            TurnType.TURN_LEFT -> if (streetName.isNotBlank()) "Turn left onto $streetName" else "Turn left"
                            TurnType.SLIGHT_LEFT -> if (streetName.isNotBlank()) "Keep left onto $streetName" else "Bear left"
                            TurnType.U_TURN -> "Make a U-turn"
                            TurnType.STRAIGHT -> if (streetName.isNotBlank()) "Continue on $streetName" else "Continue straight"
                        }

                        val loc = manObj?.optJSONArray("location")
                        val stepPoint = if (loc != null && loc.length() >= 2) {
                            GeoPoint(loc.getDouble(1), loc.getDouble(0))
                        } else {
                            waypoints.lastOrNull() ?: destination.location
                        }

                        steps.add(
                            RouteStep(
                                instruction = stepInstruction,
                                distanceMeters = stepDist,
                                targetPoint = stepPoint,
                                turnType = turnType,
                                streetName = streetName
                            )
                        )
                    }
                }
            }

            if (steps.isEmpty()) {
                steps.add(
                    RouteStep(
                        instruction = "Proceed to ${destination.name}",
                        distanceMeters = distMeters,
                        targetPoint = destination.location,
                        turnType = TurnType.START
                    )
                )
            }

            OfflineRoute(
                destination = destination,
                waypoints = waypoints,
                totalDistanceMeters = distMeters,
                estimatedDurationSeconds = durSec,
                steps = steps
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseRouteJson(jsonStr: String, destination: Destination): OfflineRoute? {
        return try {
            val root = JSONObject(jsonStr)
            val dist = root.getDouble("dist")
            val dur = root.getDouble("dur")
            val coords = root.getJSONArray("coords")
            val waypoints = ArrayList<GeoPoint>(coords.length())
            for (i in 0 until coords.length()) {
                val pt = coords.getJSONArray(i)
                waypoints.add(GeoPoint(pt.getDouble(0), pt.getDouble(1)))
            }
            OfflineRoute(
                destination = destination,
                waypoints = waypoints,
                totalDistanceMeters = dist,
                estimatedDurationSeconds = dur,
                steps = offlineEngine.generateGuidanceSteps(waypoints, destination)
            )
        } catch (_: Exception) {
            null
        }
    }
}
