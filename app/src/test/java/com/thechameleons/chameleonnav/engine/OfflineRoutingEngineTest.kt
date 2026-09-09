package com.thechameleons.chameleonnav.engine

import com.thechameleons.chameleonnav.model.Destination
import com.thechameleons.chameleonnav.model.GeoPoint
import com.thechameleons.chameleonnav.model.TurnType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OfflineRoutingEngineTest {

    private lateinit var routingEngine: OfflineRoutingEngine

    @Before
    fun setUp() {
        routingEngine = OfflineRoutingEngine()
    }

    @Test
    fun testOfflineDestinationsCatalogIsPopulatedWithPanIndiaSpots() {
        val dests = routingEngine.offlineDestinations
        assertTrue("Catalog should have offline landmarks across India, count: ${dests.size}", dests.size >= 40)

        // Check Pan-India Metros & Capitals
        assertTrue("Should contain Mumbai", dests.any { it.name.contains("Mumbai") })
        assertTrue("Should contain Bengaluru", dests.any { it.name.contains("Bengaluru") })
        assertTrue("Should contain Hyderabad", dests.any { it.name.contains("Hyderabad") })
        assertTrue("Should contain Chennai", dests.any { it.name.contains("Chennai") })
        assertTrue("Should contain Kolkata", dests.any { it.name.contains("Kolkata") })
        assertTrue("Should contain Jaipur", dests.any { it.name.contains("Jaipur") })
        assertTrue("Should contain Lucknow", dests.any { it.name.contains("Lucknow") })
        assertTrue("Should contain Ayodhya", dests.any { it.name.contains("Ayodhya") })
        assertTrue("Should contain Varanasi", dests.any { it.name.contains("Varanasi") })
        assertTrue("Should contain Goa", dests.any { it.name.contains("Goa") })
        assertTrue("Should contain Chandigarh", dests.any { it.name.contains("Chandigarh") })

        // Check local Ghaziabad / Meerut Road spots
        assertTrue("Should contain Shaheed Sthal", dests.any { it.name.contains("Shaheed Sthal") })
        assertTrue("Should contain Duhai RRTS", dests.any { it.name.contains("Duhai RRTS") })
        assertTrue("Should contain KIET", dests.any { it.name.contains("KIET") })

        // Check keywords for saheed sthal
        val shaheed = dests.first { it.name.contains("Shaheed Sthal") }
        assertTrue("Keywords should contain saheed", shaheed.keywords.contains("saheed"))
    }

    @Test
    fun testCalculateRouteAlongMeerutRoadCorridor() {
        // From Duhai RRTS to Shaheed Sthal
        val start = GeoPoint(28.7420, 77.4890) // Duhai RRTS
        val dest = Destination(
            id = "test_shaheed",
            name = "Shaheed Sthal",
            location = GeoPoint(28.6720, 77.4290)
        )

        val route = routingEngine.calculateRoute(start, dest)

        assertNotNull(route)
        assertEquals(dest, route.destination)
        assertTrue("Total distance should follow highway (~10-16 km), was: ${route.totalDistanceMeters}",
            route.totalDistanceMeters in 9000.0..18000.0)
        assertTrue("Waypoints should contain highway points, count was: ${route.waypoints.size}",
            route.waypoints.size >= 8)
        val mins = route.estimatedDurationSeconds / 60.0
        assertTrue("Duration along Meerut Road should be ~10-25 mins, was: $mins", mins in 8.0..30.0)
        assertTrue("Steps should be generated", route.steps.isNotEmpty())
        assertEquals(TurnType.START, route.steps.first().turnType)
        assertEquals(TurnType.REACHED, route.steps.last().turnType)
    }

    @Test
    fun testPanIndiaRouteFromDelhiToJaipur() {
        val start = GeoPoint(28.6129, 77.2295) // Delhi India Gate
        val dest = Destination(
            id = "test_jaipur",
            name = "Jaipur",
            location = GeoPoint(26.9124, 75.7873)
        )

        val route = routingEngine.calculateRoute(start, dest)

        assertNotNull(route)
        assertTrue("Distance to Jaipur should be ~250-320km, was: ${route.totalDistanceMeters}",
            route.totalDistanceMeters in 230000.0..360000.0)
        assertTrue("Should have highway intermediate waypoints, was: ${route.waypoints.size}",
            route.waypoints.size >= 4)
    }

    @Test
    fun testPanIndiaRouteFromGhaziabadToLucknow() {
        val start = GeoPoint(28.6720, 77.4290) // Ghaziabad Shaheed Sthal
        val dest = Destination(
            id = "test_lucknow",
            name = "Lucknow",
            location = GeoPoint(26.8467, 80.9462)
        )

        val route = routingEngine.calculateRoute(start, dest)

        assertNotNull(route)
        assertTrue("Distance to Lucknow should be ~480-600km, was: ${route.totalDistanceMeters}",
            route.totalDistanceMeters in 450000.0..650000.0)
        val hours = route.estimatedDurationSeconds / 3600.0
        assertTrue("Duration to Lucknow should be 5-8 hours for vehicles, was: $hours", hours in 4.5..8.5)
    }

    @Test
    fun testEvaluateProgressArrivalDetection() {
        val destLoc = GeoPoint(28.6720, 77.4290)
        val dest = Destination(id = "dest_1", name = "Shaheed Sthal", location = destLoc)
        val route = routingEngine.calculateRoute(GeoPoint(28.7420, 77.4890), dest)

        // When vehicle is within 15 meters of destination
        val arrivedPos = GeoPoint(28.67201, 77.42901)
        val progress = routingEngine.evaluateProgress(arrivedPos, 0f, route)

        assertEquals(TurnType.REACHED, progress.nextTurnType)
        assertTrue(progress.nextManeuver.contains("Arrived"))
    }
}
