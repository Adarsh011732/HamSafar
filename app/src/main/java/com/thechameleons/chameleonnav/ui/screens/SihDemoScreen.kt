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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thechameleons.chameleonnav.model.GnssStatus
import com.thechameleons.chameleonnav.model.NavigationMode
import com.thechameleons.chameleonnav.ui.theme.*

@Composable
fun SihDemoScreen(
    gnssStatus: GnssStatus,
    navigationMode: NavigationMode,
    isNavigating: Boolean,
    onRecalibrate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // -------------------------------------------------------------
        // 1. TOP PIPELINE STATUS CARD (matches Top Card in reference)
        // -------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(SurfaceLight)
                .border(1.dp, CardBorder, RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CardBorderSubtle),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoMode,
                                contentDescription = null,
                                tint = AccentDark,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Pipeline ID",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "IO-VNBD-v2",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Soft Blue Pill (matches "In Transit" pill in reference image)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isNavigating) PillBlueBg else PillAmberBg)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isNavigating) "In Execution" else "Ready",
                            color = if (isNavigating) PillBlueText else PillAmberText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Horizontal Stepper Timeline
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PipelineStepperNode(
                        label = "IMU Preprocessing",
                        sublabel = "100 Hz",
                        isCompleted = true,
                        modifier = Modifier.weight(1f)
                    )

                    Box(
                        modifier = Modifier
                            .weight(0.8f)
                            .height(2.dp)
                            .background(AccentDark)
                    )

                    PipelineStepperNode(
                        label = "Neural Bi-GRU",
                        sublabel = "Speed Head",
                        isCompleted = true,
                        modifier = Modifier.weight(1f)
                    )

                    Box(
                        modifier = Modifier
                            .weight(0.8f)
                            .height(2.dp)
                            .background(if (isNavigating) AccentDark else CardBorder)
                    )

                    PipelineStepperNode(
                        label = "EKF Kinematics",
                        sublabel = "Map Snap",
                        isCompleted = isNavigating,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // 2. PRIMARY BLACK PILL BUTTON (matches "Track Shipping" in reference)
        // -------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(AccentDark)
                .clickable { onRecalibrate() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = TextOnDark,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Recalibrate IMU Zero-Bias",
                    color = TextOnDark,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // -------------------------------------------------------------
        // 3. ARCHITECTURE DETAILS CARD (matches "Delivery Details" in reference)
        // -------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(SurfaceLight)
                .border(1.dp, CardBorder, RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        tint = AccentDark,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pipeline Architecture",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(color = CardBorderSubtle, thickness = 1.dp)

                DetailRow(label = "GNSS State", value = gnssStatus.name)
                DetailRow(label = "Navigation Mode", value = navigationMode.label)
                DetailRow(label = "Model Architecture", value = "1D-CNN + Bi-GRU + Attention")
                DetailRow(label = "Input Dimensions", value = "10 Channels (9-Axis + GNSS Flag)")
                DetailRow(label = "Filter Framework", value = "9-State Error-State EKF")
                DetailRow(label = "Kinematic Constraints", value = "ZUPT Clamping + NHC Bounds")
                DetailRow(label = "Sampling Grid", value = "100 Hz Uniform Resampling")
                DetailRow(label = "Inference Latency", value = "< 4.5 ms on Mobile Edge")
                DetailRow(label = "Drift Rate", value = "< 0.38% of Distance")
            }
        }

        // -------------------------------------------------------------
        // 4. SENSOR HARDWARE NODE CARD (matches "Mr John" contact card in reference)
        // -------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceLight)
                .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(CardHero),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            tint = TextOnDark,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "6-Axis IMU + Magnetometer",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Hardware Sampling @ 50 Hz",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(PillGreenBg)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "Online",
                        color = PillGreenText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // 5. VERTICAL EXECUTION TIMELINE (matches Bottom Timeline in reference)
        // -------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(SurfaceLight)
                .border(1.dp, CardBorder, RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Execution Steps",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                VerticalStepItem(
                    stepTime = "0 ms",
                    stepTitle = "Signal Alignment",
                    stepDescription = "Uniform 100 Hz resampling and phone-to-vehicle chassis alignment.",
                    isLast = false
                )

                VerticalStepItem(
                    stepTime = "+2 ms",
                    stepTitle = "Neural Speed Estimation",
                    stepDescription = "1D-CNN extracts frequency features; Bi-GRU predicts forward speed.",
                    isLast = false
                )

                VerticalStepItem(
                    stepTime = "+3 ms",
                    stepTitle = "Kinematic Constraints",
                    stepDescription = "GLRT detector triggers ZUPT during stops; NHC enforces lateral bounds.",
                    isLast = false
                )

                VerticalStepItem(
                    stepTime = "+4 ms",
                    stepTitle = "EKF State Update",
                    stepDescription = "Fused velocity injected into 9-state covariance mechanization.",
                    isLast = false
                )

                VerticalStepItem(
                    stepTime = "+5 ms",
                    stepTitle = "Vector Map Snapping",
                    stepDescription = "Spline projection onto verified OpenStreetMap road centerlines.",
                    isLast = true
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun PipelineStepperNode(
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
                .size(20.dp)
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
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isCompleted) TextPrimary else TextMuted
        )
        Text(
            text = sublabel,
            fontSize = 9.sp,
            color = TextMuted
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal
        )
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun VerticalStepItem(
    stepTime: String,
    stepTitle: String,
    stepDescription: String,
    isLast: Boolean
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stepTime,
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(48.dp)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(AccentDark)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(34.dp)
                        .background(CardBorder)
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        Column {
            Text(
                text = stepTitle,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stepDescription,
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}
