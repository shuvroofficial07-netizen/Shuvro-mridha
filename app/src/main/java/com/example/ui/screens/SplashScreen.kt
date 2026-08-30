package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onBootComplete: () -> Unit) {
    var bootStep by remember { mutableIntStateOf(0) }
    val infiniteTransition = rememberInfiniteTransition(label = "hologram")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    LaunchedEffect(Unit) {
        delay(400)
        bootStep = 1 // Neural Kernel
        delay(450)
        bootStep = 2 // Local Action Engine
        delay(450)
        bootStep = 3 // Smart Room Database
        delay(450)
        bootStep = 4 // Ready
        delay(700)
        onBootComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .clickable { onBootComplete() }, // Tap to skip
        contentAlignment = Alignment.Center
    ) {
        // Holographic Background Glow
        Box(
            modifier = Modifier
                .size(340.dp)
                .scale(pulseScale)
                .blur(80.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            NeonPurple.copy(alpha = 0.45f),
                            NeonBlue.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // Orbital Rings & Hologram Core
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(160.dp)
            ) {
                // Outer Orbit Ring
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(rotationAngle)
                ) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(NeonBlue, NeonPurple, NeonPink, NeonBlue)
                        ),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // Inner Orbit Ring
                Canvas(
                    modifier = Modifier
                        .size(120.dp)
                        .rotate(-rotationAngle * 1.5f)
                ) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(NeonGreen, NeonBlue, NeonPurple, NeonGreen)
                        ),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }

                // Center AI Core Orb
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(80.dp)
                        .scale(pulseScale)
                        .background(
                            Brush.radialGradient(
                                listOf(NeonPurple, Color(0xFF1E1035), BackgroundDark)
                            ),
                            CircleShape
                        )
                        .border(2.dp, NeonBlue, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "Arohi AI Core",
                        tint = NeonBlue,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Branding Header
            Text(
                text = "Arohi AI Assistant",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                letterSpacing = 1.2.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "by ",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
                Text(
                    text = "Shù Vrô",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonBlue
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(NeonPurple.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                        .border(1.dp, NeonPurple.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "v7.0.1",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonPink
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Autonomous Personal AI Operating Layer",
                fontSize = 11.sp,
                color = TextSecondary.copy(alpha = 0.8f),
                letterSpacing = 0.8.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Initialization Step Card
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .background(SurfaceDark.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                    .border(1.dp, BorderMedium, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BootStepRow("Neural Cognitive Brain Initialized", bootStep >= 1)
                BootStepRow("Universal Phone Action Engine Ready", bootStep >= 2)
                BootStepRow("Smart SQLite Memory Vault Connected", bootStep >= 3)
                BootStepRow("Arohi Operating System v7.0.1 Ready", bootStep >= 4)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "ট্যাপ করে সরাসরি চালু করুন",
                fontSize = 11.sp,
                color = TextSecondary.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun BootStepRow(label: String, completed: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (completed) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = NeonGreen,
                modifier = Modifier.size(16.dp)
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = NeonBlue
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (completed) TextPrimary else TextSecondary,
            fontWeight = if (completed) FontWeight.Medium else FontWeight.Normal
        )
    }
}
