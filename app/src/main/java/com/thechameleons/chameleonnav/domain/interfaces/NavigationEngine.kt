package com.thechameleons.chameleonnav.domain.interfaces

import com.thechameleons.chameleonnav.model.TelemetryData
import kotlinx.coroutines.flow.StateFlow

/**
 * High-level Navigation Engine orchestrator.
 */
interface NavigationEngine {
    val telemetryState: StateFlow<TelemetryData>

    fun startNavigation()
    fun stopNavigation()
    fun simulateGnssOutage()
    fun restoreGnss()
    fun setSimulationSpeed(multiplier: Float)
    fun reset()
}
