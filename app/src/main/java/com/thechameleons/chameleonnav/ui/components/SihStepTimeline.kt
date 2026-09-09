package com.thechameleons.chameleonnav.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thechameleons.chameleonnav.model.GnssStatus
import com.thechameleons.chameleonnav.model.NavigationMode
import com.thechameleons.chameleonnav.ui.theme.*

data class DemoStep(
    val index: Int,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color
)

val SIH_STEPS = listOf(
    DemoStep(1, "GNSS AVAILABLE", "Satellite constellation locked (Normal fix)", Icons.Default.GpsFixed, AccentEmerald),
    DemoStep(2, "NAVIGATION", "Vehicle in transit, INS+GNSS fusion online", Icons.Default.DirectionsCar, AccentCyan),
    DemoStep(3, "GNSS LOST", "Signal blockage: Tunnel, flyover or electronic jam", Icons.Default.GpsOff, GnssLostRed),
    DemoStep(4, "DEAD RECKONING", "AI-ML inertial speed & strapdown attitude integration", Icons.Default.Sensors, DeadReckoningOrange),
    DemoStep(5, "GNSS RESTORED", "Fresh pseudorange fixes acquired", Icons.Default.Sync, RecoveryBlue),
    DemoStep(6, "RECOVERY", "Drift correction filter smoothly synchronizing position", Icons.Default.TrendingFlat, RecoveryBlue),
    DemoStep(7, "GNSS + INS", "Seamless transition back to ground truth fusion", Icons.Default.CheckCircle, AccentEmerald)
)

@Composable
fun SihStepTimeline(
    gnssStatus: GnssStatus,
    navigationMode: NavigationMode,
    isNavigating: Boolean,
    modifier: Modifier = Modifier
) {
    val activeStepIndex = when {
        !isNavigating -> 1
        navigationMode == NavigationMode.GNSS_INS && isNavigating -> 2
        gnssStatus == GnssStatus.LOST && navigationMode == NavigationMode.DEAD_RECKONING -> 4
        navigationMode == NavigationMode.RECOVERY -> 6
        gnssStatus == GnssStatus.AVAILABLE && !isNavigating -> 7
        else -> 2
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardDark)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "SIH 2026 LIVE DEMONSTRATION FLOW",
            color = AccentCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(2.dp))

        SIH_STEPS.forEach { step ->
            val isActive = step.index == activeStepIndex
            val isPast = step.index < activeStepIndex

            val infiniteTransition = rememberInfiniteTransition(label = "step_pulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(700, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "step_alpha"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isActive) step.color.copy(alpha = 0.15f)
                        else if (isPast) SurfaceDark.copy(alpha = 0.5f)
                        else SurfaceDark
                    )
                    .border(
                        1.dp,
                        if (isActive) step.color else if (isPast) CardBorder else Color.Transparent,
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Step Number or Icon
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) step.color.copy(alpha = pulseAlpha)
                            else if (isPast) step.color.copy(alpha = 0.4f)
                            else CardBorder
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = step.icon,
                        contentDescription = null,
                        tint = if (isActive || isPast) Color.White else TextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = step.title,
                        color = if (isActive) step.color else if (isPast) TextPrimary else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = if (isActive) FontWeight.Black else FontWeight.Bold
                    )
                    Text(
                        text = step.subtitle,
                        color = if (isActive) TextSecondary else TextMuted,
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                }

                if (isActive) {
                    Text(
                        text = "ACTIVE",
                        color = step.color,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}
