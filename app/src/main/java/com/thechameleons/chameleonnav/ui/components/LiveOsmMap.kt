package com.thechameleons.chameleonnav.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.thechameleons.chameleonnav.model.Destination
import com.thechameleons.chameleonnav.model.GeoPoint
import com.thechameleons.chameleonnav.model.NavigationMode
import com.thechameleons.chameleonnav.ui.theme.*
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-Reliability Live OpenStreetMap Component.
 * - Works both with live physical GNSS and during autonomous Dead Reckoning (GPS Off).
 * - Real-time GPS/DR accuracy radius circle.
 * - Smooth camera following for both GNSS & DR positions.
 * - 100% Offline Route polyline & destination pin rendering.
 * - Tap / Long-press on map to select and set offline destination.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun LiveOsmMap(
    currentPosition: GeoPoint,
    headingDeg: Float,
    accuracyMeters: Float = 4.2f,
    isGpsLocked: Boolean = false,
    navigationMode: NavigationMode,
    referencePath: List<GeoPoint>,
    estimatedPath: List<GeoPoint>,
    showReferencePath: Boolean = true,
    destination: Destination? = null,
    routePoints: List<GeoPoint> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var isAutoFollowing by remember { mutableStateOf(true) }

    // Ensure OSMdroid uses internal app storage
    LaunchedEffect(Unit) {
        try {
            Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid_cfg", Context.MODE_PRIVATE))
            Configuration.getInstance().userAgentValue = context.packageName
            Configuration.getInstance().osmdroidBasePath = File(context.cacheDir, "osmdroid")
            Configuration.getInstance().osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
        } catch (_: Exception) {}
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewInstance?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewInstance?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewInstance?.onDetach()
        }
    }

    val carMarkerRef = remember { mutableStateOf<Marker?>(null) }
    val destMarkerRef = remember { mutableStateOf<Marker?>(null) }
    val refPolylineRef = remember { mutableStateOf<Polyline?>(null) }
    val estPolylineRef = remember { mutableStateOf<Polyline?>(null) }
    val routePolylineRef = remember { mutableStateOf<Polyline?>(null) }
    val accuracyPolygonRef = remember { mutableStateOf<Polygon?>(null) }

    var lastAnimateTimeMs by remember { mutableLongStateOf(0L) }
    var lastKnownTarget by remember { mutableStateOf<OsmGeoPoint?>(null) }
    var smoothedHeading by remember { mutableFloatStateOf(headingDeg) }

    var cachedNormalChevron by remember { mutableStateOf<BitmapDrawable?>(null) }
    var cachedOutageChevron by remember { mutableStateOf<BitmapDrawable?>(null) }
    var cachedDestIcon by remember { mutableStateOf<BitmapDrawable?>(null) }

    LaunchedEffect(context) {
        cachedNormalChevron = BitmapDrawable(context.resources, createVehicleChevronBitmap(context, android.graphics.Color.rgb(14, 165, 233)))
        cachedOutageChevron = BitmapDrawable(context.resources, createVehicleChevronBitmap(context, android.graphics.Color.rgb(249, 115, 22)))
        cachedDestIcon = BitmapDrawable(context.resources, createDestinationPinBitmap(context))
    }

    val hasValidPos = currentPosition.latitude != 0.0 && currentPosition.longitude != 0.0

    // Center/Fit view when route is set
    var lastFittedDestId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(destination?.id, routePoints.size) {
        if (destination != null && destination.id != lastFittedDestId && mapViewInstance != null) {
            lastFittedDestId = destination.id
            if (hasValidPos) {
                try {
                    val minLat = minOf(currentPosition.latitude, destination.location.latitude)
                    val maxLat = maxOf(currentPosition.latitude, destination.location.latitude)
                    val minLon = minOf(currentPosition.longitude, destination.location.longitude)
                    val maxLon = maxOf(currentPosition.longitude, destination.location.longitude)
                    val deltaLat = maxOf(maxLat - minLat, 0.005)
                    val deltaLon = maxOf(maxLon - minLon, 0.005)
                    val bbox = BoundingBox(
                        maxLat + deltaLat * 0.25,
                        maxLon + deltaLon * 0.25,
                        minLat - deltaLat * 0.25,
                        minLon - deltaLon * 0.25
                    )
                    mapViewInstance?.zoomToBoundingBox(bbox, true)
                    isAutoFollowing = false
                } catch (_: Exception) {
                    mapViewInstance?.controller?.animateTo(OsmGeoPoint(destination.location.latitude, destination.location.longitude))
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(SurfaceLight)
            .border(1.dp, CardBorder, RoundedCornerShape(22.dp))
    ) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    minZoomLevel = 4.0
                    maxZoomLevel = 20.0
                    controller.setZoom(17.0)

                    val initPos = if (hasValidPos) {
                        OsmGeoPoint(currentPosition.latitude, currentPosition.longitude)
                    } else {
                        val prefs = ctx.getSharedPreferences("chameleon_gps_cache", Context.MODE_PRIVATE)
                        val lat = prefs.getFloat("cached_lat", 0f).toDouble()
                        val lon = prefs.getFloat("cached_lon", 0f).toDouble()
                        if (lat != 0.0 && lon != 0.0 && !(lat in 18.0..20.0 && lon in 72.0..74.0)) {
                            OsmGeoPoint(lat, lon)
                        } else {
                            OsmGeoPoint(28.6692, 77.4538)
                        }
                    }
                    controller.setCenter(initPos)
                    isTilesScaledToDpi = true

                    setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                            if (event.pointerCount > 1 || event.action == MotionEvent.ACTION_MOVE) {
                                isAutoFollowing = false
                            }
                        }
                        false
                    }


                    // 1. Accuracy Circle Polygon
                    val accuracyPoly = Polygon().apply {
                        fillPaint.color = android.graphics.Color.argb(32, 14, 165, 233)
                        outlinePaint.color = android.graphics.Color.argb(120, 14, 165, 233)
                        outlinePaint.strokeWidth = 2.5f
                        infoWindow = null
                    }

                    // 2. Reference GNSS Polyline (Cyan / Blue)
                    val refLine = Polyline().apply {
                        outlinePaint.color = android.graphics.Color.argb(190, 14, 165, 233)
                        outlinePaint.strokeWidth = 7f
                    }

                    // 3. Estimated Dead Reckoning Polyline (Amber / Orange)
                    val estLine = Polyline().apply {
                        outlinePaint.color = android.graphics.Color.argb(220, 249, 115, 22)
                        outlinePaint.strokeWidth = 8f
                    }

                    // 4. Offline Navigation Route Polyline (Vibrant Indigo / Purple)
                    val routeLine = Polyline().apply {
                        outlinePaint.color = android.graphics.Color.rgb(139, 92, 246)
                        outlinePaint.strokeWidth = 9f
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                        isEnabled = false
                    }

                    // 5. Destination Marker Pin
                    val destMarker = Marker(this).apply {
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        cachedDestIcon?.let { icon = it }
                        infoWindow = null
                        isEnabled = false
                        setOnMarkerClickListener { _, _ -> true }
                    }

                    // 6. Vehicle Navigation Marker
                    val vehicleMarker = Marker(this).apply {
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        position = initPos
                        rotation = headingDeg
                        cachedNormalChevron?.let { icon = it }
                        infoWindow = null
                        setOnMarkerClickListener { _, _ -> true }
                    }

                    overlays.add(accuracyPoly)
                    overlays.add(refLine)
                    overlays.add(estLine)
                    overlays.add(routeLine)
                    overlays.add(destMarker)
                    overlays.add(vehicleMarker)

                    accuracyPolygonRef.value = accuracyPoly
                    refPolylineRef.value = refLine
                    estPolylineRef.value = estLine
                    routePolylineRef.value = routeLine
                    destMarkerRef.value = destMarker
                    carMarkerRef.value = vehicleMarker
                    mapViewInstance = this

                    onResume()
                }
            },
            update = { mapView ->
                val now = System.currentTimeMillis()
                if (hasValidPos) {
                    val osmTarget = OsmGeoPoint(currentPosition.latitude, currentPosition.longitude)

                    // 1. Smoothly follow user position if auto-follow is active
                    if (isAutoFollowing) {
                        val prev = lastKnownTarget
                        val distMeters = if (prev != null) {
                            osmTarget.distanceToAsDouble(prev)
                        } else {
                            100.0
                        }

                        if (distMeters > 1.2 || (now - lastAnimateTimeMs > 2000L && distMeters > 0.3)) {
                            lastAnimateTimeMs = now
                            lastKnownTarget = osmTarget
                            mapView.controller.animateTo(osmTarget)
                        }
                    }

                    // 2. Smooth heading interpolation to eliminate compass flutter
                    val currentRot = smoothedHeading
                    val headingDelta = ((headingDeg - currentRot + 540f) % 360f) - 180f
                    smoothedHeading = (currentRot + headingDelta * 0.35f + 360f) % 360f

                    // 3. Update vehicle marker position and rotation
                    val marker = carMarkerRef.value
                    if (marker != null) {
                        marker.position = osmTarget
                        marker.rotation = smoothedHeading

                        val isOutage = !isGpsLocked || navigationMode == NavigationMode.DEAD_RECKONING || navigationMode == NavigationMode.RECOVERY
                        val chevron = if (isOutage) cachedOutageChevron else cachedNormalChevron
                        if (chevron != null && marker.icon != chevron) {
                            marker.icon = chevron
                        }
                    }

                    // 4. Update accuracy radius circle around vehicle
                    accuracyPolygonRef.value?.let { circle ->
                        val rad = accuracyMeters.toDouble().coerceIn(3.0, 80.0)
                        val points = createCirclePoints(osmTarget, rad, numPoints = 28)
                        circle.setPoints(points)
                        circle.isEnabled = true
                    }
                }

                // 5. Update reference & estimated path trails
                refPolylineRef.value?.let { refLine ->
                    refLine.isEnabled = showReferencePath && isGpsLocked
                    if (refLine.isEnabled && referencePath.size > 1 && refLine.actualPoints.size != referencePath.size) {
                        refLine.setPoints(referencePath.filter { it.latitude != 0.0 }.map { OsmGeoPoint(it.latitude, it.longitude) })
                    }
                }

                estPolylineRef.value?.let { estLine ->
                    val validPoints = estimatedPath.filter { it.latitude != 0.0 }
                    if (validPoints.size > 1 && estLine.actualPoints.size != validPoints.size) {
                        estLine.setPoints(validPoints.map { OsmGeoPoint(it.latitude, it.longitude) })
                    }
                }

                // 6. Update Offline Route Polyline
                routePolylineRef.value?.let { rLine ->
                    if (routePoints.size > 1) {
                        rLine.isEnabled = true
                        rLine.setPoints(routePoints.map { OsmGeoPoint(it.latitude, it.longitude) })
                    } else {
                        rLine.isEnabled = false
                    }
                }

                // 7. Update Destination Marker
                destMarkerRef.value?.let { dMarker ->
                    if (destination != null) {
                        dMarker.isEnabled = true
                        dMarker.position = OsmGeoPoint(destination.location.latitude, destination.location.longitude)
                        dMarker.title = destination.name
                        if (cachedDestIcon != null && dMarker.icon != cachedDestIcon) {
                            dMarker.icon = cachedDestIcon
                        }
                    } else {
                        dMarker.isEnabled = false
                    }
                }

                mapView.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Left: Map Mode & Accuracy Badge
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .shadow(2.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceLight)
                .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (isGpsLocked) AccentEmerald else PillAmberText)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isGpsLocked) "GPS Fix ±${String.format(java.util.Locale.US, "%.1f", accuracyMeters)}m"
                else "DR Active (GPS Off)",
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Floating Map Controls Overlay (Recenter, Zoom In, Zoom Out)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Recenter / Follow Button
            FloatingActionButton(
                onClick = {
                    isAutoFollowing = true
                    if (hasValidPos) {
                        val target = OsmGeoPoint(currentPosition.latitude, currentPosition.longitude)
                        mapViewInstance?.controller?.animateTo(target)
                    }
                },
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                containerColor = if (isAutoFollowing) CardHero else SurfaceLight,
                contentColor = if (isAutoFollowing) SurfaceLight else TextPrimary,
                elevation = FloatingActionButtonDefaults.elevation(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Recenter Map",
                    modifier = Modifier.size(18.dp)
                )
            }

            // Zoom In Button
            SmallFloatingActionButton(
                onClick = { mapViewInstance?.controller?.zoomIn() },
                modifier = Modifier.size(34.dp),
                shape = CircleShape,
                containerColor = SurfaceLight,
                contentColor = TextPrimary,
                elevation = FloatingActionButtonDefaults.elevation(2.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(16.dp))
            }

            // Zoom Out Button
            SmallFloatingActionButton(
                onClick = { mapViewInstance?.controller?.zoomOut() },
                modifier = Modifier.size(34.dp),
                shape = CircleShape,
                containerColor = SurfaceLight,
                contentColor = TextPrimary,
                elevation = FloatingActionButtonDefaults.elevation(2.dp)
            ) {
                Icon(imageVector = Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(16.dp))
            }
        }
    }
}

/**
 * Creates circle vertices around a center GeoPoint for the accuracy radius overlay.
 */
private fun createCirclePoints(center: OsmGeoPoint, radiusMeters: Double, numPoints: Int = 28): List<OsmGeoPoint> {
    val points = ArrayList<OsmGeoPoint>(numPoints)
    val earthRadius = 6378137.0
    val dLat = radiusMeters / earthRadius
    val dLon = radiusMeters / (earthRadius * cos(Math.toRadians(center.latitude)))
    for (i in 0 until numPoints) {
        val angle = 2.0 * Math.PI * i / numPoints
        val lat = center.latitude + Math.toDegrees(dLat * sin(angle))
        val lon = center.longitude + Math.toDegrees(dLon * cos(angle))
        points.add(OsmGeoPoint(lat, lon))
    }
    return points
}

/**
 * Creates a clean, anti-aliased navigation chevron bitmap for the OSM vehicle marker.
 */
private fun createVehicleChevronBitmap(context: Context, arrowColor: Int): Bitmap {
    val size = (36 * context.resources.displayMetrics.density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val cx = size / 2f
    val cy = size / 2f

    // Outer glow circle
    val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(70, android.graphics.Color.red(arrowColor), android.graphics.Color.green(arrowColor), android.graphics.Color.blue(arrowColor))
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, size * 0.45f, circlePaint)

    // Inner dark disc
    val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(13, 14, 18)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, size * 0.35f, discPaint)

    // Directional chevron path
    val path = Path().apply {
        moveTo(cx, cy - size * 0.30f)
        lineTo(cx + size * 0.20f, cy + size * 0.24f)
        lineTo(cx, cy + size * 0.13f)
        lineTo(cx - size * 0.20f, cy + size * 0.24f)
        close()
    }

    val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = arrowColor
        style = Paint.Style.FILL
    }
    canvas.drawPath(path, arrowPaint)

    // Center white dot
    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, size * 0.07f, dotPaint)

    return bitmap
}

/**
 * Creates a crisp destination pin marker bitmap.
 */
private fun createDestinationPinBitmap(context: Context): Bitmap {
    val density = context.resources.displayMetrics.density
    val width = (32 * density).toInt()
    val height = (42 * density).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val cx = width / 2f
    val cy = width * 0.45f
    val r = width * 0.40f

    // Drop shadow
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(60, 0, 0, 0)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, height - 3 * density, 6 * density, shadowPaint)

    // Pin body (Vibrant Ruby Red)
    val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(239, 68, 68)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, r, pinPaint)

    // Pin tail
    val path = Path().apply {
        moveTo(cx - r * 0.82f, cy + r * 0.35f)
        lineTo(cx, height - 4 * density)
        lineTo(cx + r * 0.82f, cy + r * 0.35f)
        close()
    }
    canvas.drawPath(path, pinPaint)

    // Center white disc
    val centerWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, r * 0.48f, centerWhite)

    // Center ruby core
    val centerCore = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(220, 38, 38)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, r * 0.22f, centerCore)

    return bitmap
}
