package com.thechameleons.chameleonnav.hardware

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import com.thechameleons.chameleonnav.model.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RealGnssFix(
    val position: GeoPoint,
    val speedKmh: Float,
    val headingDeg: Float,
    val accuracyMeters: Float,
    val altitudeMeters: Double,
    val timestampMs: Long,
    val isFresh: Boolean
)

/**
 * Real GNSS hardware subsystem with Persistent Location Caching & Multi-Provider Architecture.
 * - Dynamically hooks GPS, Network, and Passive providers on startup and provider toggle.
 * - Responds instantly to LocationManager.PROVIDERS_CHANGED_ACTION broadcasts.
 * - Stores authentic GPS fixes in SharedPreferences (purging any false/poisoned fallback coordinates).
 * - Defaults to user's region (Delhi NCR) on a pristine cold boot if GPS is off and no cache exists.
 */
class RealGnssManager(
    private val context: Context,
    private val scope: CoroutineScope
) : LocationListener {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val prefs = context.getSharedPreferences("chameleon_gps_cache", Context.MODE_PRIVATE)

    private val _latestFix = MutableStateFlow<RealGnssFix?>(null)
    val latestFix: StateFlow<RealGnssFix?> = _latestFix.asStateFlow()

    private val _satellitesInView = MutableStateFlow(0)
    val satellitesInView: StateFlow<Int> = _satellitesInView.asStateFlow()

    private val _satellitesUsedInFix = MutableStateFlow(0)
    val satellitesUsedInFix: StateFlow<Int> = _satellitesUsedInFix.asStateFlow()

    private val _isOutage = MutableStateFlow(true)
    val isOutage: StateFlow<Boolean> = _isOutage.asStateFlow()

    private val _isGpsLocked = MutableStateFlow(false)
    val isGpsLocked: StateFlow<Boolean> = _isGpsLocked.asStateFlow()

    private val _isGpsEnabled = MutableStateFlow(false)
    val isGpsEnabled: StateFlow<Boolean> = _isGpsEnabled.asStateFlow()

    private var gnssStatusCallback: GnssStatus.Callback? = null
    private var lastFixElapsedRealtimeMs: Long = 0L
    private var watchdogJob: Job? = null
    private var isListening = false
    private var forceOutageTest = false

    private var isGpsRegistered = false
    private var isNetworkRegistered = false
    private var isPassiveRegistered = false
    private var isReceiverRegistered = false

    // Default coordinate for Delhi NCR (near Delhi-Meerut Road / Ghaziabad corridor)
    companion object {
        const val DEFAULT_DELHI_LAT = 28.6692
        const val DEFAULT_DELHI_LON = 77.4538
        private const val TAG = "RealGnssManager"
    }

    private val providerReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                Log.d(TAG, "Location providers changed broadcast received")
                checkAndRegisterLocationListeners()
            }
        }
    }

    init {
        // 1. Cleanse any corrupted/legacy Bombay gateway coordinates
        val cachedLat = prefs.getFloat("cached_lat", 0f).toDouble()
        val cachedLon = prefs.getFloat("cached_lon", 0f).toDouble()
        if (cachedLat in 18.0..20.0 && cachedLon in 72.0..74.0) {
            Log.i(TAG, "Purging false Mumbai coordinates from SharedPreferences")
            prefs.edit().clear().apply()
        }

        // 2. Pre-seed latestFix with authentic cached location or Delhi NCR default
        val initialCached = getCachedLocation() ?: RealGnssFix(
            position = GeoPoint(DEFAULT_DELHI_LAT, DEFAULT_DELHI_LON),
            speedKmh = 0f,
            headingDeg = 0f,
            accuracyMeters = 20f,
            altitudeMeters = 210.0,
            timestampMs = System.currentTimeMillis(),
            isFresh = false
        )
        _latestFix.value = initialCached
    }

    fun setForceOutage(force: Boolean) {
        forceOutageTest = force
        if (force) {
            _isOutage.value = true
            _isGpsLocked.value = false
        }
    }

    fun getCachedLocation(): RealGnssFix? {
        if (!prefs.getBoolean("has_cached_location", false)) return null
        val lat = prefs.getFloat("cached_lat", 0f).toDouble()
        val lon = prefs.getFloat("cached_lon", 0f).toDouble()
        val acc = prefs.getFloat("cached_accuracy", 12f)
        val alt = prefs.getFloat("cached_alt", 0f).toDouble()
        if (lat == 0.0 && lon == 0.0) return null
        // Reject legacy Mumbai IP coordinates
        if (lat in 18.0..20.0 && lon in 72.0..74.0) return null

        return RealGnssFix(
            position = GeoPoint(lat, lon),
            speedKmh = 0f,
            headingDeg = 0f,
            accuracyMeters = acc,
            altitudeMeters = alt,
            timestampMs = System.currentTimeMillis(),
            isFresh = false
        )
    }

    private fun saveLastKnownLocation(lat: Double, lon: Double, accuracy: Float, altitude: Double) {
        if (lat == 0.0 && lon == 0.0) return
        // Do not cache bogus coordinates
        if (lat in 18.0..20.0 && lon in 72.0..74.0 && accuracy > 30f) return

        prefs.edit()
            .putFloat("cached_lat", lat.toFloat())
            .putFloat("cached_lon", lon.toFloat())
            .putFloat("cached_accuracy", accuracy)
            .putFloat("cached_alt", altitude.toFloat())
            .putBoolean("has_cached_location", true)
            .apply()
    }

    @SuppressLint("MissingPermission")
    fun checkAndRegisterLocationListeners() {
        try {
            val gpsEnabled = runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
            val netEnabled = runCatching { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
            val passiveEnabled = runCatching { locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER) }.getOrDefault(false)

            _isGpsEnabled.value = gpsEnabled

            // 1. Register GPS provider updates
            if (gpsEnabled && !isGpsRegistered) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0.0f,
                    this,
                    context.mainLooper
                )
                isGpsRegistered = true
                Log.d(TAG, "GPS Provider registered successfully")
            } else if (!gpsEnabled && isGpsRegistered) {
                isGpsRegistered = false
            }

            // 2. Register Network provider updates for fast coarse lock
            if (netEnabled && !isNetworkRegistered) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000L,
                    0.0f,
                    this,
                    context.mainLooper
                )
                isNetworkRegistered = true
                Log.d(TAG, "Network Provider registered successfully")
            } else if (!netEnabled && isNetworkRegistered) {
                isNetworkRegistered = false
            }

            // 3. Register Passive provider (listens to updates from other apps like Google Maps)
            if (passiveEnabled && !isPassiveRegistered) {
                runCatching {
                    locationManager.requestLocationUpdates(
                        LocationManager.PASSIVE_PROVIDER,
                        1000L,
                        0.0f,
                        this,
                        context.mainLooper
                    )
                    isPassiveRegistered = true
                }
            }

            // 4. Register GnssStatus callback for satellite tracking
            if (gpsEnabled && gnssStatusCallback == null) {
                registerGnssCallback()
            }

            // 5. Query immediate last known location from all system providers
            if (gpsEnabled || netEnabled) {
                val lastGps = if (gpsEnabled) runCatching { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull() else null
                val lastNet = if (netEnabled) runCatching { locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull() else null
                val lastFused = runCatching { locationManager.getLastKnownLocation("fused") }.getOrNull()
                val lastPassive = runCatching { locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER) }.getOrNull()

                val bestFix = lastGps ?: lastNet ?: lastFused ?: lastPassive
                if (bestFix != null && (System.currentTimeMillis() - bestFix.time < 300000L || _latestFix.value == null)) {
                    onLocationChanged(bestFix)
                }
            }

            if (!gpsEnabled) {
                _isGpsLocked.value = false
                _isOutage.value = true
                _satellitesInView.value = 0
                _satellitesUsedInFix.value = 0
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission missing: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering location listeners", e)
        }
    }

    private fun registerGnssCallback() {
        try {
            gnssStatusCallback = object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    val count = status.satelliteCount
                    var used = 0
                    for (i in 0 until count) {
                        if (status.usedInFix(i)) {
                            used++
                        }
                    }
                    _satellitesInView.value = count
                    _satellitesUsedInFix.value = used

                    if (used >= 4 && !forceOutageTest) {
                        _isGpsLocked.value = true
                        _isOutage.value = false
                    }
                }
            }
            locationManager.registerGnssStatusCallback(context.mainExecutor, gnssStatusCallback!!)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register GnssStatusCallback: ${e.message}")
        }
    }

    fun startListening() {
        if (isListening) {
            checkAndRegisterLocationListeners()
            return
        }
        isListening = true

        // Register broadcast receiver for provider changes (Location turned ON/OFF in settings)
        if (!isReceiverRegistered) {
            try {
                context.registerReceiver(
                    providerReceiver,
                    IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
                )
                isReceiverRegistered = true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register provider receiver: ${e.message}")
            }
        }

        // Register providers
        checkAndRegisterLocationListeners()

        // Start outage & re-registration watchdog loop
        startWatchdog()
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false
        watchdogJob?.cancel()
        watchdogJob = null

        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(providerReceiver)
                isReceiverRegistered = false
            } catch (_: Exception) {}
        }

        try {
            locationManager.removeUpdates(this)
            isGpsRegistered = false
            isNetworkRegistered = false
            isPassiveRegistered = false

            gnssStatusCallback?.let {
                locationManager.unregisterGnssStatusCallback(it)
                gnssStatusCallback = null
            }
        } catch (_: Exception) {}
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(1200L)
                val now = SystemClock.elapsedRealtime()
                val gpsEnabled = runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
                _isGpsEnabled.value = gpsEnabled

                if (forceOutageTest) {
                    _isOutage.value = true
                    _isGpsLocked.value = false
                } else if (!gpsEnabled) {
                    _isOutage.value = true
                    _isGpsLocked.value = false
                } else {
                    // GPS is enabled in settings: ensure listeners are hooked up
                    if (!isGpsRegistered) {
                        checkAndRegisterLocationListeners()
                    }

                    if (_satellitesUsedInFix.value >= 4) {
                        _isOutage.value = false
                        _isGpsLocked.value = true
                    } else if (lastFixElapsedRealtimeMs > 0L && (now - lastFixElapsedRealtimeMs < 6000L)) {
                        // Fix received recently
                        _isOutage.value = false
                        _isGpsLocked.value = true
                    } else if (lastFixElapsedRealtimeMs > 0L && (now - lastFixElapsedRealtimeMs > 10000L) && _satellitesUsedInFix.value < 3) {
                        // Prolonged outage (>10s without fix or sats)
                        _isOutage.value = true
                        _isGpsLocked.value = false
                    }
                }
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        if (forceOutageTest) return

        lastFixElapsedRealtimeMs = SystemClock.elapsedRealtime()

        val speedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0.0f
        val heading = if (location.hasBearing()) location.bearing else 0.0f
        val accuracy = if (location.hasAccuracy()) location.accuracy else 8.0f
        val altitude = if (location.hasAltitude()) location.altitude else 210.0

        saveLastKnownLocation(location.latitude, location.longitude, accuracy, altitude)

        _latestFix.value = RealGnssFix(
            position = GeoPoint(location.latitude, location.longitude),
            speedKmh = speedKmh,
            headingDeg = heading,
            accuracyMeters = accuracy,
            altitudeMeters = altitude,
            timestampMs = location.time,
            isFresh = true
        )

        // Fresh fix established: seamlessly switch to GNSS locked
        _isOutage.value = false
        _isGpsLocked.value = true
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onProviderEnabled(provider: String) {
        Log.d(TAG, "onProviderEnabled: $provider")
        checkAndRegisterLocationListeners()
    }

    override fun onProviderDisabled(provider: String) {
        Log.d(TAG, "onProviderDisabled: $provider")
        if (provider == LocationManager.GPS_PROVIDER) {
            isGpsRegistered = false
            _isGpsLocked.value = false
            _isOutage.value = true
            _satellitesInView.value = 0
            _satellitesUsedInFix.value = 0
            _isGpsEnabled.value = false
        } else if (provider == LocationManager.NETWORK_PROVIDER) {
            isNetworkRegistered = false
        }
    }
}
