package com.thechameleons.chameleonnav.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.thechameleons.chameleonnav.model.*
import com.thechameleons.chameleonnav.ui.theme.*
import java.util.Locale

/**
 * Fullscreen Immersive Map Navigation Window.
 * Provides unrestricted edge-to-edge map browsing, effortless touch scrolling,
 * pinch-to-zoom, and persistent turn-by-turn guidance HUD for vehicles.
 */
@Composable
fun FullscreenMapDialog(
    telemetry: TelemetryData,
    referencePath: List<GeoPoint>,
    estimatedPath: List<GeoPoint>,
    showReferencePath: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeRoute = telemetry.activeRoute
    val hasRoute = activeRoute != null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // 1. Edge-to-Edge Fullscreen Live OSM Map
            LiveOsmMap(
                currentPosition = telemetry.estimatedPosition,
                headingDeg = telemetry.headingDeg,
                accuracyMeters = telemetry.accuracyMeters,
                isGpsLocked = telemetry.isGpsLocked,
                navigationMode = telemetry.navigationMode,
                referencePath = referencePath,
                estimatedPath = estimatedPath,
                showReferencePath = showReferencePath,
                destination = activeRoute?.destination,
                routePoints = activeRoute?.waypoints ?: emptyList(),
                modifier = Modifier.fillMaxSize()
            )

            // 2. Floating Top Turn-by-Turn Guidance Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Minimize Button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(SurfaceLight)
                        .border(1.dp, CardBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = "Minimize Map",
                        tint = TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Turn Banner (if destination selected) or GPS Status Header
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .shadow(6.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (hasRoute) CardHero else SurfaceLight)
                        .border(1.dp, if (hasRoute) CardHero else CardBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    if (hasRoute) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val turnIcon = when (telemetry.nextTurnType) {
                                TurnType.TURN_RIGHT, TurnType.SLIGHT_RIGHT -> Icons.AutoMirrored.Filled.ArrowForward
                                TurnType.TURN_LEFT, TurnType.SLIGHT_LEFT -> Icons.AutoMirrored.Filled.ArrowBack
                                TurnType.U_TURN -> Icons.Default.Refresh
                                TurnType.REACHED -> Icons.Default.Place
                                else -> Icons.Default.Navigation
                            }
                            Icon(
                                imageVector = turnIcon,
                                contentDescription = null,
                                tint = TextOnDark,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                val distM = telemetry.nextManeuverDistanceMeters ?: 0.0
                                val distStr = if (distM >= 1000) String.format(Locale.US, "In %.1f km", distM / 1000)
                                else String.format(Locale.US, "In %.0f m", distM)
                                Text(
                                    text = distStr,
                                    color = TextOnDarkMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = telemetry.nextManeuverInstruction ?: "Navigate to ${activeRoute?.destination?.name}",
                                    color = TextOnDark,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (telemetry.isGpsLocked) GnssAvailableGreen else DeadReckoningOrange)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "HamSafar Fullscreen Cockpit",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (telemetry.isGpsLocked) "GPS Locked (${telemetry.satellitesUsedInFix}/${telemetry.satellitesInView} Sats)"
                                    else "Dead Reckoning Active",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            // 3. Floating Bottom Telemetry Navigation HUD
            if (hasRoute) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .align(Alignment.BottomCenter)
                        .shadow(8.dp, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                        .background(SurfaceLight)
                        .border(1.dp, CardBorder, RoundedCornerShape(24.dp))
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Remaining Distance
                        Column {
                            Text("REMAINING", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            val remM = telemetry.distanceToDestinationMeters ?: activeRoute?.totalDistanceMeters ?: 0.0
                            val remText = if (remM >= 1000.0) String.format(Locale.US, "%.1f km", remM / 1000.0)
                            else String.format(Locale.US, "%.0f m", remM)
                            Text(remText, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }

                        // Vehicle Speed
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("SPEED", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(
                                String.format(Locale.US, "%.0f km/h", telemetry.speedKmh),
                                color = AccentDark,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Estimated Arrival Time
                        Column(horizontalAlignment = Alignment.End) {
                            Text("EST. TIME", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            val durSec = if (telemetry.speedKmh > 20f && telemetry.distanceToDestinationMeters != null) {
                                telemetry.distanceToDestinationMeters / (telemetry.speedKmh / 3.6)
                            } else {
                                activeRoute?.estimatedDurationSeconds ?: 0.0
                            }
                            val mins = (durSec / 60.0).toInt()
                            val timeStr = when {
                                mins < 1 -> "< 1 min"
                                mins < 60 -> "$mins min"
                                else -> "${mins / 60}h ${mins % 60}m"
                            }
                            Text(timeStr, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
