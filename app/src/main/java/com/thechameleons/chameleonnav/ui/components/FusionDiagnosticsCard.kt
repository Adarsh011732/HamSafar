package com.thechameleons.chameleonnav.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thechameleons.chameleonnav.model.FusionState
import com.thechameleons.chameleonnav.ui.theme.*

@Composable
fun FusionDiagnosticsCard(
    fusion: FusionState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardDark)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MULTI-SENSOR FUSION PIPELINE",
                    color = AccentCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "AI+INS+NHC+ZUPT+MAP",
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Grid of 6 Fusion Status Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FusionPill(
                    title = "AI (IO-VNBD)",
                    status = String.format(java.util.Locale.US, "%.1f km/h", fusion.aiPredictedSpeedKmh),
                    isActive = fusion.isAiActive,
                    activeColor = AccentPurple,
                    modifier = Modifier.weight(1f)
                )
                FusionPill(
                    title = "INS (50Hz)",
                    status = String.format(java.util.Locale.US, "%.0f° Heading", fusion.insHeadingDeg),
                    isActive = fusion.isInsActive,
                    activeColor = AccentCyan,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FusionPill(
                    title = "NHC (Vy≈0)",
                    status = if (fusion.isNhcApplied) "DRIFT KILLED" else "STANDBY",
                    isActive = fusion.isNhcApplied,
                    activeColor = AccentEmerald,
                    modifier = Modifier.weight(1f)
                )
                FusionPill(
                    title = "ZUPT",
                    status = if (fusion.isZuptActive) "STOP CLAMPED" else "MOTION",
                    isActive = fusion.isZuptActive,
                    activeColor = AccentAmber,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FusionPill(
                    title = "MAP MATCH",
                    status = if (fusion.isMapMatched) "ROAD SNAPPED" else "FREE DR",
                    isActive = fusion.isMapMatched,
                    activeColor = AccentEmerald,
                    modifier = Modifier.weight(1f)
                )
                FusionPill(
                    title = "GNSS FUSION",
                    status = if (fusion.isGnssFused) "SAT LOCKED" else "DR FALLBACK",
                    isActive = fusion.isGnssFused,
                    activeColor = if (fusion.isGnssFused) AccentCyan else AccentRose,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun FusionPill(
    title: String,
    status: String,
    isActive: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceDark)
            .border(1.dp, if (isActive) activeColor.copy(alpha = 0.4f) else CardBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isActive) activeColor else TextMuted)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                color = TextSecondary,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = status,
            color = if (isActive) activeColor else TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black
        )
    }
}
