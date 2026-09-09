package com.thechameleons.chameleonnav.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thechameleons.chameleonnav.model.Vector3D
import com.thechameleons.chameleonnav.ui.theme.*
import kotlin.math.abs

@Composable
fun SensorGaugeCard(
    title: String,
    sensorData: Vector3D,
    unit: String,
    maxRange: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(SurfaceLight)
            .border(1.dp, CardBorder, RoundedCornerShape(22.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "UNIT: $unit",
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            AxisBar(axis = "X", value = sensorData.x, maxRange = maxRange, accentColor = AccentCyan)
            AxisBar(axis = "Y", value = sensorData.y, maxRange = maxRange, accentColor = AccentEmerald)
            AxisBar(axis = "Z", value = sensorData.z, maxRange = maxRange, accentColor = AccentAmber)
        }
    }
}

@Composable
private fun AxisBar(
    axis: String,
    value: Float,
    maxRange: Float,
    accentColor: Color
) {
    val progress = (abs(value) / maxRange).coerceIn(0f, 1f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = axis,
            color = accentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.width(18.dp)
        )

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = accentColor,
            trackColor = CardBorderSubtle
        )

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = String.format(java.util.Locale.US, "%+6.2f", value),
            color = TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(46.dp)
        )
    }
}
