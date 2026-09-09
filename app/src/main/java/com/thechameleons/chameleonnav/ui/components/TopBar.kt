package com.thechameleons.chameleonnav.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thechameleons.chameleonnav.ui.theme.*

@Composable
fun TopBar(
    satellitesInView: Int = 0,
    satellitesUsedInFix: Int = 0,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "status_pulse"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AppBackground)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // User / System Profile Section with Hamsafar Animated Logo
        Row(verticalAlignment = Alignment.CenterVertically) {
            HamsafarLogoView(
                size = 50.dp,
                showBorder = true
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "Dead Reckoning System",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "HamSafar",
                    color = TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
            }
        }

        // Right-hand minimalist status badge / bell button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceLight)
                .border(1.dp, CardBorder, RoundedCornerShape(24.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (satellitesUsedInFix > 0) GnssAvailableGreen.copy(alpha = pulseAlpha) else DeadReckoningOrange)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (satellitesUsedInFix > 0) "$satellitesUsedInFix/$satellitesInView Sats" else if (satellitesInView > 0) "$satellitesInView Sats" else "DR Active",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
