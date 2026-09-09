package com.thechameleons.chameleonnav.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thechameleons.chameleonnav.model.GnssStatus
import com.thechameleons.chameleonnav.model.NavigationMode
import com.thechameleons.chameleonnav.ui.theme.*

@Composable
fun StatusCard(
    gnssStatus: GnssStatus,
    navigationMode: NavigationMode,
    statusMessage: String,
    modifier: Modifier = Modifier
) {
    val isGnssAvailable = gnssStatus == GnssStatus.AVAILABLE
    val isDeadReckoning = navigationMode == NavigationMode.DEAD_RECKONING

    val (pillBg, pillText, pillLabel) = when {
        isDeadReckoning && gnssStatus == GnssStatus.DEGRADED -> Triple(PillAmberBg, PillAmberText, "Acquiring GNSS")
        isDeadReckoning -> Triple(PillBlueBg, PillBlueText, "Dead Reckoning")
        isGnssAvailable -> Triple(PillGreenBg, PillGreenText, "GNSS Locked")
        gnssStatus == GnssStatus.LOST -> Triple(PillRedBg, PillRedText, "GPS Denied")
        else -> Triple(PillAmberBg, PillAmberText, "Degraded")
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(SurfaceLight)
            .border(1.dp, CardBorder, RoundedCornerShape(22.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header Row: Tracking ID + Pill Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Tracking ID",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "EKF-INS-9DOF",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    )
                }

                // Minimalist Pill Badge (matches "In Transit" pill in reference image)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(pillBg)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = pillLabel,
                        color = pillText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Stepper Timeline (matches "Received ---- In Transit ---- Delivered" in reference image)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Step 1: IMU 9-Axis
                TimelineNode(
                    label = "9-DOF IMU",
                    sublabel = "50 Hz Active",
                    isCompleted = true,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .weight(0.8f)
                        .height(2.dp)
                        .background(AccentDark)
                )

                // Step 2: AI Velocity / EKF
                TimelineNode(
                    label = "AI Speed",
                    sublabel = if (isDeadReckoning) "Active" else "Standby",
                    isCompleted = true,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .weight(0.8f)
                        .height(2.dp)
                        .background(if (isGnssAvailable) AccentDark else CardBorder)
                )

                // Step 3: Road Map Match
                TimelineNode(
                    label = "Map Snap",
                    sublabel = "OSM Center",
                    isCompleted = isGnssAvailable,
                    modifier = Modifier.weight(1f)
                )
            }

            // Status message strip
            if (statusMessage.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBorderSubtle)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusMessage,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineNode(
    label: String,
    sublabel: String,
    isCompleted: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (isCompleted) AccentDark else SurfaceLight)
                .border(1.5.dp, if (isCompleted) AccentDark else CardBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = TextOnDark,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isCompleted) TextPrimary else TextMuted
        )
        Text(
            text = sublabel,
            fontSize = 9.sp,
            fontWeight = FontWeight.Normal,
            color = TextMuted
        )
    }
}
