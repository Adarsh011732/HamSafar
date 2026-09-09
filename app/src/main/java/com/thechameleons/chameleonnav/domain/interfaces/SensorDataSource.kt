package com.thechameleons.chameleonnav.domain.interfaces

import com.thechameleons.chameleonnav.model.GeoPoint
import com.thechameleons.chameleonnav.model.Vector3D
import kotlinx.coroutines.flow.Flow

/**
 * Sensor DataSource interface.
 * Implemented by RealSensorManager in production and DemoSimulationManager in prototype.
 */
interface SensorDataSource {
    data class SensorPacket(
        val timestampNano: Long,
        val accelerometer: Vector3D,
        val gyroscope: Vector3D,
        val rawGnss: GeoPoint?,
        val gnssAccuracyMeters: Float?
    )

    fun observeSensorStream(): Flow<SensorPacket>
    fun start()
    fun stop()
}
