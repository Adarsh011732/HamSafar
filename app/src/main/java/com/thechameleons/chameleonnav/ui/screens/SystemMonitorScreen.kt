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
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thechameleons.chameleonnav.model.TelemetryData
import com.thechameleons.chameleonnav.ui.components.SensorGaugeCard
import com.thechameleons.chameleonnav.ui.theme.*

@Composable
fun SystemMonitorScreen(
    telemetry: TelemetryData,
    onReset: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedMonitorView by remember { mutableIntStateOf(0) } // 0 = Live Telemetry, 1 = Fusion Pipeline
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Segmented Pill Switcher: Live Telemetry vs Fusion Pipeline
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(SurfaceLight)
                .border(1.dp, CardBorder, RoundedCornerShape(32.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Pill 1: Live Telemetry
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (selectedMonitorView == 0) AccentDark else Color.Transparent)
                    .clickable { selectedMonitorView = 0 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        tint = if (selectedMonitorView == 0) TextOnDark else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Live Telemetry",
                        color = if (selectedMonitorView == 0) TextOnDark else TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Pill 2: Fusion Pipeline
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (selectedMonitorView == 1) AccentDark else Color.Transparent)
                    .clickable { selectedMonitorView = 1 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoMode,
                        contentDescription = null,
                        tint = if (selectedMonitorView == 1) TextOnDark else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Fusion Pipeline",
                        color = if (selectedMonitorView == 1) TextOnDark else TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Body based on selected sub-view
        if (selectedMonitorView == 0) {
            // Live Sensor Telemetry View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section 1: AI Velocity Inference (IO-VNBD Dataset)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(SurfaceLight)
                        .border(1.dp, CardBorder, RoundedCornerShape(22.dp))
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "AI VELOCITY INFERENCE",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CardBorderSubtle)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "1D-CNN + Bi-GRU",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MonitorPill(
                                label = "DATASET BENCHMARK",
                                value = "IO-VNBD Vehicular",
                                icon = Icons.Default.Psychology,
                                color = AccentDark,
                                modifier = Modifier.weight(1f)
                            )
                            MonitorPill(
                                label = "AI SPEED ESTIMATE",
                                value = String.format(java.util.Locale.US, "%.1f km/h", telemetry.fusion.aiPredictedSpeedKmh),
                                icon = Icons.Default.Speed,
                                color = AccentDark,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MonitorPill(
                                label = "INFERENCE LATENCY",
                                value = "${telemetry.fusion.aiInferenceLatencyMs} ms",
                                icon = Icons.Default.Timer,
                                color = AccentEmerald,
                                modifier = Modifier.weight(1f)
                            )
                            MonitorPill(
                                label = "SPEED ERROR DELTA",
                                value = String.format(java.util.Locale.US, "±%.2f km/h", kotlin.math.abs(telemetry.speedKmh - telemetry.fusion.aiPredictedSpeedKmh)),
                                icon = Icons.Default.Analytics,
                                color = AccentDark,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Section 2: Non-Holonomic Constraints (NHC) & ZUPT Diagnostics
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(SurfaceLight)
                        .border(1.dp, CardBorder, RoundedCornerShape(22.dp))
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "KINEMATIC CONSTRAINTS (NHC & ZUPT)",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MonitorPill(
                                label = "NHC (LATERAL Vy≈0)",
                                value = if (telemetry.fusion.isNhcApplied) "APPLIED (Drift Free)" else "STANDBY",
                                icon = Icons.Default.Shield,
                                color = if (telemetry.fusion.isNhcApplied) AccentEmerald else TextMuted,
                                modifier = Modifier.weight(1f)
                            )
                            val isStationary = telemetry.fusion.isZuptActive || telemetry.speedKmh < 0.5f
                            MonitorPill(
                                label = "ZUPT STATIONARITY",
                                value = if (isStationary) "STANDSTILL (Clamped)" else "IN MOTION",
                                icon = Icons.Default.PanTool,
                                color = if (isStationary) AccentEmerald else TextMuted,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MonitorPill(
                                label = "MAP MATCHING",
                                value = if (telemetry.fusion.isMapMatched) "SNAPPED" else "OFF-ROAD",
                                icon = Icons.AutoMirrored.Filled.AltRoute,
                                color = if (telemetry.fusion.isMapMatched) AccentEmerald else TextMuted,
                                modifier = Modifier.weight(1f)
                            )
                            MonitorPill(
                                label = "CROSS-TRACK OFFSET",
                                value = String.format(java.util.Locale.US, "%.1f m", telemetry.fusion.crossTrackErrorMeters),
                                icon = Icons.Default.Grain,
                                color = AccentDark,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Section 3: 3-Axis Accelerometer Telemetry
                SensorGaugeCard(
                    title = "3-AXIS ACCELEROMETER (AX, AY, AZ)",
                    sensorData = telemetry.accelerometer,
                    unit = "m/s²",
                    maxRange = 15f,
                    accentColor = AccentDark
                )

                // Section 4: 3-Axis Gyroscope Telemetry
                SensorGaugeCard(
                    title = "3-AXIS GYROSCOPE (GX, GY, GZ)",
                    sensorData = telemetry.gyroscope,
                    unit = "rad/s",
                    maxRange = 2f,
                    accentColor = AccentDark
                )

                // Section 5: GNSS Satellite Constellation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(SurfaceLight)
                        .border(1.dp, CardBorder, RoundedCornerShape(22.dp))
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "PHYSICAL GNSS CONSTELLATION",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MonitorPill(
                                label = "SATELLITES IN VIEW",
                                value = "${telemetry.satellitesInView} Satellites",
                                icon = Icons.Default.SatelliteAlt,
                                color = AccentDark,
                                modifier = Modifier.weight(1f)
                            )
                            MonitorPill(
                                label = "SATELLITES IN FIX",
                                value = "${telemetry.satellitesUsedInFix} Locked",
                                icon = Icons.Default.GpsFixed,
                                color = if (telemetry.satellitesUsedInFix >= 4) AccentEmerald else PillAmberText,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MonitorPill(
                                label = "HORIZONTAL ACCURACY",
                                value = String.format(java.util.Locale.US, "%.1f m", telemetry.accuracyMeters),
                                icon = Icons.Default.MyLocation,
                                color = AccentEmerald,
                                modifier = Modifier.weight(1f)
                            )
                            MonitorPill(
                                label = "EKF COVARIANCE TRACE",
                                value = String.format(java.util.Locale.US, "%.4f", telemetry.fusion.ekfCovarianceTrace),
                                icon = Icons.Default.Grain,
                                color = AccentDark,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        } else {
            // Fusion Pipeline Architecture View
            SihDemoScreen(
                gnssStatus = telemetry.gnssStatus,
                navigationMode = telemetry.navigationMode,
                isNavigating = telemetry.isNavigating,
                onRecalibrate = onReset,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun MonitorPill(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CardBorderSubtle)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(13.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
