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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thechameleons.chameleonnav.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    showReferencePath: Boolean,
    showDebugData: Boolean,
    onToggleReferencePath: (Boolean) -> Unit,
    onToggleDebugData: (Boolean) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var showHardwareSheet by remember { mutableStateOf(false) }
    var showSystemSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: Specifications on Demand (Interactive Action Buttons)
        Text(
            text = "SPECIFICATIONS & ARCHITECTURE",
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(SurfaceLight)
                .border(1.dp, CardBorder, RoundedCornerShape(22.dp))
        ) {
            SettingsActionTile(
                icon = Icons.Default.Sensors,
                title = "Hardware Sensor Specifications",
                subtitle = "50Hz IMU, Gravity Isolation, ZUPT, NHC",
                onClick = { showHardwareSheet = true }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = CardBorderSubtle,
                thickness = 1.dp
            )

            SettingsActionTile(
                icon = Icons.Default.Info,
                title = "System & Project Information",
                subtitle = "SIH26168 build details, engine version & team",
                onClick = { showSystemSheet = true }
            )
        }

        // Section 2: Display & Visualization Options
        Text(
            text = "MAP & DISPLAY PREFERENCES",
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(SurfaceLight)
                .border(1.dp, CardBorder, RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingToggleRow(
                    title = "Show Reference GNSS Path",
                    subtitle = "Draws the blue baseline satellite trail on map",
                    checked = showReferencePath,
                    onCheckedChange = onToggleReferencePath
                )

                HorizontalDivider(color = CardBorderSubtle, thickness = 1.dp)

                SettingToggleRow(
                    title = "Live Sensor Telemetry",
                    subtitle = "Stream high-rate IMU and EKF data in Monitor",
                    checked = showDebugData,
                    onCheckedChange = onToggleDebugData
                )
            }
        }

        // Section 3: Reset Engine & Recalibrate
        Text(
            text = "ENGINE CONTROLS",
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(SurfaceLight)
                .border(1.dp, CardBorder, RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Zero-Bias Recalibration",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Clears accumulated dead reckoning trajectory, flushes EKF error states, and resets accelerometer/gyroscope zero-bias baseline.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Button(
                    onClick = onReset,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CardBorderSubtle,
                        contentColor = AccentDark
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Reset Trajectory & Recalibrate",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }

    // Modal Sheet 1: Hardware Sensor Specifications
    if (showHardwareSheet) {
        ModalBottomSheet(
            onDismissRequest = { showHardwareSheet = false },
            containerColor = SurfaceLight,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hardware Sensor Specifications",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showHardwareSheet = false }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Text(
                    text = "Physical smartphone hardware sensors directly connected to the dead reckoning multi-sensor fusion engine:",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                SpecDetailCard(
                    title = "IMU Sampling Frequency",
                    spec = "50 Hz (Hardware Interrupt Driven)",
                    detail = "Direct asynchronous sensor bus listener for low-latency sampling"
                )
                SpecDetailCard(
                    title = "Linear Acceleration",
                    spec = "Dynamic 3D Gravity Isolation",
                    detail = "Continuous dynamic coordinate frame rotation removing 9.81 m/s² gravity"
                )
                SpecDetailCard(
                    title = "Zero-Velocity Update",
                    spec = "GLRT Standstill Detector (ZUPT)",
                    detail = "Generalized Likelihood Ratio Test clamping velocity to zero during stops"
                )
                SpecDetailCard(
                    title = "Non-Holonomic Constraints",
                    spec = "NHC Lateral Drift Cancellation",
                    detail = "Body-frame pseudo-measurements eliminating sideways sliding"
                )
                SpecDetailCard(
                    title = "AI Velocity Model",
                    spec = "IO-VNBD 1D-CNN + Bi-GRU",
                    detail = "Pretrained neural network estimating forward velocity during GNSS outages"
                )
            }
        }
    }

    // Modal Sheet 2: System Information
    if (showSystemSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSystemSheet = false },
            containerColor = SurfaceLight,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "System Information",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showSystemSheet = false }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                InfoRowItem(label = "Project", value = "SIH26168 — Intelligent Dead Reckoning")
                InfoRowItem(label = "Team", value = "THE CHAMELEONS")
                InfoRowItem(label = "Engine Version", value = "2.2.0 (Real Hardware Release)")
                InfoRowItem(label = "Architecture", value = "AI (IO-VNBD) + INS + NHC + ZUPT + OSM")
                InfoRowItem(label = "Platform", value = "Android Native (Jetpack Compose + OSMdroid)")
                InfoRowItem(label = "Mission", value = "“When GPS Disappears, We Adapt.”")
            }
        }
    }
}

@Composable
private fun SettingsActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(CardBorderSubtle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AccentDark,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, color = TextMuted, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SurfaceLight,
                checkedTrackColor = AccentDark,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = CardBorderSubtle
            )
        )
    }
}

@Composable
private fun SpecDetailCard(
    title: String,
    spec: String,
    detail: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBorderSubtle)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(text = spec, color = AccentDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Text(text = detail, color = TextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun InfoRowItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBorderSubtle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(text = value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
