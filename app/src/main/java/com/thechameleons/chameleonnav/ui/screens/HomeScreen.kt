package com.thechameleons.chameleonnav.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thechameleons.chameleonnav.model.Destination
import com.thechameleons.chameleonnav.model.GeoPoint
import com.thechameleons.chameleonnav.model.TelemetryData
import com.thechameleons.chameleonnav.ui.components.ActiveRouteCard
import com.thechameleons.chameleonnav.ui.components.DestinationPickerSheet
import com.thechameleons.chameleonnav.ui.components.FullscreenMapDialog
import com.thechameleons.chameleonnav.ui.components.LiveOsmMap
import com.thechameleons.chameleonnav.ui.components.StatusCard
import com.thechameleons.chameleonnav.ui.theme.*
import java.util.Locale

@Composable
fun HomeScreen(
    telemetry: TelemetryData,
    referencePath: List<GeoPoint>,
    estimatedPath: List<GeoPoint>,
    showReferencePath: Boolean,
    onStartNavigation: () -> Unit,
    onStopNavigation: () -> Unit,
    onToggleForceOutage: () -> Unit,
    onRequestPermission: () -> Unit,
    onReset: () -> Unit,
    destinations: List<Destination> = emptyList(),
    showDestinationPicker: Boolean = false,
    onOpenDestinationPicker: () -> Unit = {},
    onCloseDestinationPicker: () -> Unit = {},
    onSelectDestination: (Destination) -> Unit = {},
    onClearRoute: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var isMapMaximized by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // -------------------------------------------------------------
        // 1. HERO BLACK CARD (matches "Your Balance" card in reference)
        // -------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(CardHero)
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Top Row: Label + Pill Action Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FORWARD SPEED",
                        color = TextOnDarkMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp
                    )

                    // Pill button (matches "Top Up" in reference image)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(SurfaceLight)
                            .clickable {
                                if (telemetry.isNavigating) onStopNavigation() else onStartNavigation()
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = if (telemetry.isNavigating) "Stop Nav" else "Start Nav",
                            color = AccentDark,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Big Hero Speed Metric
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = String.format(Locale.US, "%.1f", telemetry.speedKmh),
                        color = TextOnDark,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.5).sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "km/h",
                        color = TextOnDarkMuted,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                // Status info chip inside hero card
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (telemetry.isNavigating) GnssAvailableGreen else TextOnDarkMuted)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (telemetry.fusion.isZuptActive) "Stationary • Zero-Velocity Clamped"
                        else if (telemetry.isOutageActive) "GPS Blackout • AI Inertial Dead Reckoning Active"
                        else "Heading ${telemetry.headingDeg.toInt()}° • Satellites: ${telemetry.satellitesUsedInFix}/${telemetry.satellitesInView}",
                        color = TextOnDarkMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Bottom Row Pill Buttons (matches "New Shipping" & "Track Shipping" in reference)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Left Pill Button: Force GNSS Outage / Restore
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceLight)
                            .clickable { onToggleForceOutage() }
                            .padding(vertical = 12.dp, horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (telemetry.isOutageActive) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                                contentDescription = null,
                                tint = AccentDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (telemetry.isOutageActive) "Restore GPS" else "Force Outage",
                                color = AccentDark,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Right Pill Button: GNSS Lock & Precision Status
                    val isAcquiring = !telemetry.isGpsLocked && telemetry.gnssStatus == com.thechameleons.chameleonnav.model.GnssStatus.DEGRADED
                    val buttonText = when {
                        telemetry.isGpsLocked -> "GNSS Locked"
                        isAcquiring -> "Acquiring..."
                        else -> "GPS Off"
                    }
                    val buttonTint = when {
                        telemetry.isGpsLocked -> AccentEmerald
                        isAcquiring -> AccentAmber
                        else -> PillRedText
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceLight)
                            .padding(vertical = 12.dp, horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (telemetry.isGpsLocked) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed,
                                contentDescription = null,
                                tint = buttonTint,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = buttonText,
                                color = AccentDark,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Permission Alert Pill (if missing location permission)
        if (!telemetry.hasLocationPermission) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PillAmberBg)
                    .border(1.dp, PillAmberText.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .clickable { onRequestPermission() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Grant location permission for real-world GPS tracking",
                    color = PillAmberText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Allow",
                    color = PillAmberText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // -------------------------------------------------------------
        // 2. CURRENT TRACKING CARD (StatusCard with 3-Step Timeline)
        // -------------------------------------------------------------
        StatusCard(
            gnssStatus = telemetry.gnssStatus,
            navigationMode = telemetry.navigationMode,
            statusMessage = telemetry.statusMessage
        )

        // -------------------------------------------------------------
        // 3. OFFLINE DESTINATION & ROUTE GUIDANCE CARD
        // -------------------------------------------------------------
        ActiveRouteCard(
            activeRoute = telemetry.activeRoute,
            distanceToDestinationMeters = telemetry.distanceToDestinationMeters,
            etaSeconds = if (telemetry.speedKmh > 20f && telemetry.distanceToDestinationMeters != null)
                telemetry.distanceToDestinationMeters / (telemetry.speedKmh / 3.6)
            else telemetry.activeRoute?.estimatedDurationSeconds,
            nextManeuverInstruction = telemetry.nextManeuverInstruction,
            nextManeuverDistanceMeters = telemetry.nextManeuverDistanceMeters,
            nextTurnType = telemetry.nextTurnType,
            onOpenPicker = onOpenDestinationPicker,
            onClearRoute = onClearRoute
        )

        // -------------------------------------------------------------
        // 4. LIVE HIGH-ACCURACY OPENSTREETMAP CARD (WITH OFFLINE ROUTING)
        // -------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(270.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(SurfaceLight)
                .border(1.dp, CardBorder, RoundedCornerShape(22.dp))
        ) {
            LiveOsmMap(
                currentPosition = telemetry.estimatedPosition,
                headingDeg = telemetry.headingDeg,
                accuracyMeters = telemetry.accuracyMeters,
                isGpsLocked = telemetry.isGpsLocked,
                navigationMode = telemetry.navigationMode,
                referencePath = referencePath,
                estimatedPath = estimatedPath,
                showReferencePath = showReferencePath,
                destination = telemetry.activeRoute?.destination,
                routePoints = telemetry.activeRoute?.waypoints ?: emptyList(),
                modifier = Modifier.fillMaxSize()
            )

            // Maximize Map Button
            IconButton(
                onClick = { isMapMaximized = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(36.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(SurfaceLight)
                    .border(1.dp, CardBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "Maximize Map",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Fullscreen Interactive Navigation Dialog
        if (isMapMaximized) {
            FullscreenMapDialog(
                telemetry = telemetry,
                referencePath = referencePath,
                estimatedPath = estimatedPath,
                showReferencePath = showReferencePath,
                onDismiss = { isMapMaximized = false }
            )
        }

        // Modal Offline Destination Picker Bottom Sheet
        if (showDestinationPicker) {
            DestinationPickerSheet(
                currentPosition = telemetry.estimatedPosition,
                destinations = destinations,
                onSelectDestination = onSelectDestination,
                onDismiss = onCloseDestinationPicker
            )
        }

        // -------------------------------------------------------------
        // 4. ESSENTIAL TELEMETRY METRICS GRID (Relevant Info Only)
        // -------------------------------------------------------------
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Key Telemetry",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MinimalMetricCard(
                    title = "VELOCITY",
                    value = String.format(Locale.US, "%.1f", telemetry.speedKmh / 3.6f),
                    unit = "m/s",
                    modifier = Modifier.weight(1f)
                )
                MinimalMetricCard(
                    title = "EST. DRIFT",
                    value = String.format(Locale.US, "%.2f", telemetry.crossTrackDriftMeters),
                    unit = "m",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MinimalMetricCard(
                    title = "HEADING",
                    value = String.format(Locale.US, "%.0f°", telemetry.headingDeg),
                    unit = getCompassDirection(telemetry.headingDeg),
                    modifier = Modifier.weight(1f)
                )
                MinimalMetricCard(
                    title = if (telemetry.isGpsLocked) "GPS ACCURACY" else "DR ACCURACY",
                    value = String.format(Locale.US, "%.1f", telemetry.accuracyMeters),
                    unit = "m",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // -------------------------------------------------------------
        // 5. MINIMALIST RESET ACTION
        // -------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceLight)
                .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                .clickable { onReset() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Reset Trajectory",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun MinimalMetricCard(
    title: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceLight)
            .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column {
            Text(
                text = title,
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                if (unit.isNotBlank()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unit,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}

private fun getCompassDirection(deg: Float): String {
    val normalized = ((deg % 360) + 360) % 360
    return when {
        normalized >= 337.5 || normalized < 22.5 -> "N"
        normalized < 67.5 -> "NE"
        normalized < 112.5 -> "E"
        normalized < 157.5 -> "SE"
        normalized < 202.5 -> "S"
        normalized < 247.5 -> "SW"
        normalized < 292.5 -> "W"
        else -> "NW"
    }
}
