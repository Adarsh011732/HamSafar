package com.thechameleons.chameleonnav.hardware

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.thechameleons.chameleonnav.model.Vector3D
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Real hardware sensor integration layer.
 * Continuously samples physical accelerometer, gyroscope, and rotation vector sensors.
 * Features automatic watchdog re-registration to prevent sensor sleep on OEM devices.
 */
class RealSensorDataSource(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Always register raw hardware accelerometer for 100% reliable hardware interrupt delivery
    private val rawAccelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val linearAccelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val gyroSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _accelerometer = MutableStateFlow(Vector3D(0f, 0f, 0f))
    val accelerometer: StateFlow<Vector3D> = _accelerometer.asStateFlow()

    private val _gyroscope = MutableStateFlow(Vector3D(0f, 0f, 0f))
    val gyroscope: StateFlow<Vector3D> = _gyroscope.asStateFlow()

    private val _azimuthHeadingDeg = MutableStateFlow(0f)
    val azimuthHeadingDeg: StateFlow<Float> = _azimuthHeadingDeg.asStateFlow()

    private val _motionEnergy = MutableStateFlow(0f)
    val motionEnergy: StateFlow<Float> = _motionEnergy.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // Dynamic 3-axis gravity tracking (smooth filter)
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 9.81f
    private var isGravityInitialized = false

    // Filtering coefficients
    private val alpha = 0.65f
    private var filteredAx = 0f
    private var filteredAy = 0f
    private var filteredAz = 0f

    @Volatile
    private var isListening = false
    private var lastEventTimestampMs = System.currentTimeMillis()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var watchdogJob: Job? = null

    fun startListening() {
        if (isListening) return
        isListening = true
        registerSensors()
        startWatchdog()
    }

    private fun registerSensors() {
        try {
            sensorManager.unregisterListener(this)
            if (linearAccelSensor != null) {
                sensorManager.registerListener(this, linearAccelSensor, SensorManager.SENSOR_DELAY_GAME)
            } else if (rawAccelSensor != null) {
                sensorManager.registerListener(this, rawAccelSensor, SensorManager.SENSOR_DELAY_GAME)
            }
            gyroSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
            rotationSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        } catch (_: Exception) {}
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                delay(3000L)
                val elapsed = System.currentTimeMillis() - lastEventTimestampMs
                // If OEM power-manager throttled or stopped sensor events for > 3s, re-register
                if (isListening && elapsed > 3000L) {
                    withContext(Dispatchers.Main) {
                        registerSensors()
                    }
                }
            }
        }
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false
        watchdogJob?.cancel()
        watchdogJob = null
        try {
            sensorManager.unregisterListener(this)
        } catch (_: Exception) {}
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        lastEventTimestampMs = System.currentTimeMillis()

        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val rawX = event.values[0]
                val rawY = event.values[1]
                val rawZ = event.values[2]

                filteredAx = alpha * filteredAx + (1 - alpha) * rawX
                filteredAy = alpha * filteredAy + (1 - alpha) * rawY
                filteredAz = alpha * filteredAz + (1 - alpha) * rawZ

                val cleanX = if (abs(filteredAx) < 0.02f) 0f else filteredAx
                val cleanY = if (abs(filteredAy) < 0.02f) 0f else filteredAy
                val cleanZ = if (abs(filteredAz) < 0.02f) 0f else filteredAz

                _accelerometer.value = Vector3D(cleanX, cleanY, cleanZ)
                _motionEnergy.value = sqrt(cleanX * cleanX + cleanY * cleanY + cleanZ * cleanZ)
            }

            Sensor.TYPE_ACCELEROMETER -> {
                // If linear acceleration sensor is absent or sleeping, calculate dynamic 3D linear acceleration
                val rawX = event.values[0]
                val rawY = event.values[1]
                val rawZ = event.values[2]

                if (!isGravityInitialized) {
                    gravityX = rawX
                    gravityY = rawY
                    gravityZ = rawZ
                    isGravityInitialized = true
                } else {
                    val gAlpha = 0.96f // Standard gravity low-pass constant
                    gravityX = gAlpha * gravityX + (1 - gAlpha) * rawX
                    gravityY = gAlpha * gravityY + (1 - gAlpha) * rawY
                    gravityZ = gAlpha * gravityZ + (1 - gAlpha) * rawZ
                }

                val linX = rawX - gravityX
                val linY = rawY - gravityY
                val linZ = rawZ - gravityZ

                filteredAx = alpha * filteredAx + (1 - alpha) * linX
                filteredAy = alpha * filteredAy + (1 - alpha) * linY
                filteredAz = alpha * filteredAz + (1 - alpha) * linZ

                val cleanX = if (abs(filteredAx) < 0.02f) 0f else filteredAx
                val cleanY = if (abs(filteredAy) < 0.02f) 0f else filteredAy
                val cleanZ = if (abs(filteredAz) < 0.02f) 0f else filteredAz

                _accelerometer.value = Vector3D(cleanX, cleanY, cleanZ)
                _motionEnergy.value = sqrt(cleanX * cleanX + cleanY * cleanY + cleanZ * cleanZ)
            }

            Sensor.TYPE_GYROSCOPE -> {
                _gyroscope.value = Vector3D(
                    x = event.values[0],
                    y = event.values[1],
                    z = event.values[2]
                )
            }

            Sensor.TYPE_ROTATION_VECTOR -> {
                try {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)
                    val azimuthRad = orientationAngles[0]
                    var azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
                    if (azimuthDeg < 0) azimuthDeg += 360f
                    _azimuthHeadingDeg.value = azimuthDeg
                } catch (_: Exception) {}
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
