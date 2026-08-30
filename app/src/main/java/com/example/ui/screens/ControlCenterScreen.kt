package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.ArohiActionEngine
import com.example.ai.ArohiBrain
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ControlCenterScreen(
    brain: ArohiBrain,
    onOpenVision: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val actionEngine = remember { brain.actionEngine }

    var isTorchOn by remember { mutableStateOf(false) }
    var volumePct by remember { mutableFloatStateOf(50f) }
    var statusMessage by remember { mutableStateOf("ডিভাইস নিয়ন্ত্রণ হাব প্রস্তুত ✔") }
    var screenReadingText by remember { mutableStateOf<String?>(null) }

    val battery = remember { actionEngine.getBatteryInfo() }
    val storage = remember { actionEngine.getStorageInfo() }
    val memory = remember { actionEngine.getMemoryStatus() }
    val network = remember { actionEngine.getNetworkStatus() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CONTROL CENTER",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonBlue
                    )
                    Text(
                        text = "Universal Device Operating Layer",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
                Box(
                    modifier = Modifier
                        .background(SurfaceCard, RoundedCornerShape(8.dp))
                        .border(1.dp, NeonGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(network, fontSize = 9.sp, color = NeonGreen)
                }
            }
        }

        // Status Toast Feedback
        if (statusMessage.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(NeonBlue.copy(alpha = 0.3f), NeonPurple.copy(alpha = 0.3f))))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = NeonBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(statusMessage, fontSize = 11.sp, color = TextPrimary)
                    }
                }
            }
        }

        // Screen Reading Modal / Expandable Card
        if (screenReadingText != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131F37)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(NeonBlue, NeonPurple)))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📱 ACTIVE SCREEN TEXT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonBlue)
                            IconButton(onClick = { screenReadingText = null }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = screenReadingText ?: "",
                            fontSize = 11.sp,
                            color = TextPrimary,
                            maxLines = 10
                        )
                    }
                }
            }
        }

        // Hardware Controls Grid
        item {
            Text("HARDWARE & UTILITIES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Torch Tile
                ControlTile(
                    title = "টর্চ লাইট",
                    subtitle = if (isTorchOn) "চালু আছে 🔦" else "বন্ধ 🌑",
                    icon = Icons.Default.FlashlightOn,
                    isActive = isTorchOn,
                    modifier = Modifier.weight(1f),
                    activeColor = Orange400
                ) {
                    isTorchOn = !isTorchOn
                    statusMessage = actionEngine.toggleTorch(isTorchOn)
                }

                // Camera Vision Tile
                ControlTile(
                    title = "ক্যামেরা ভিশন",
                    subtitle = "AI দিয়ে দেখুন ✨",
                    icon = Icons.Default.CameraAlt,
                    isActive = false,
                    modifier = Modifier.weight(1f),
                    activeColor = NeonPurple
                ) {
                    onOpenVision()
                }

                // Screen Reader Tile
                ControlTile(
                    title = "স্ক্রিন রিডার",
                    subtitle = "টেক্সট পড়ুন 📖",
                    icon = Icons.Default.Smartphone,
                    isActive = false,
                    modifier = Modifier.weight(1f),
                    activeColor = NeonGreen
                ) {
                    screenReadingText = actionEngine.readCurrentScreen()
                    statusMessage = "স্ক্রিন টেক্সট রিড করা হয়েছে।"
                }
            }
        }

        // Volume Controller
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(BorderMedium, BorderLight)))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = NeonBlue, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("মিডিয়া ভলিউম", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Text("${volumePct.toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonBlue)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = volumePct,
                        onValueChange = { volumePct = it },
                        onValueChangeFinished = {
                            statusMessage = actionEngine.setVolume(volumePct.toInt())
                        },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonBlue,
                            activeTrackColor = NeonPurple,
                            inactiveTrackColor = SurfaceDark
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                volumePct = 0f
                                statusMessage = actionEngine.setVolume(0)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("মিউট (0%)", fontSize = 10.sp, color = TextSecondary)
                        }

                        Button(
                            onClick = {
                                volumePct = 50f
                                statusMessage = actionEngine.setVolume(50)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("৫০%", fontSize = 10.sp, color = TextPrimary)
                        }

                        Button(
                            onClick = {
                                volumePct = 100f
                                statusMessage = actionEngine.setVolume(100)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("সর্বোচ্চ (100%)", fontSize = 10.sp, color = NeonBlue)
                        }
                    }
                }
            }
        }

        // Media Controls
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(BorderMedium, BorderLight)))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("MEDIA PLAYBACK", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { statusMessage = actionEngine.controlMedia("previous") },
                            modifier = Modifier.background(SurfaceDark, CircleShape)
                        ) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = TextPrimary)
                        }

                        IconButton(
                            onClick = { statusMessage = actionEngine.controlMedia("play") },
                            modifier = Modifier
                                .size(52.dp)
                                .background(Brush.linearGradient(listOf(NeonBlue, NeonPurple)), CircleShape)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = Color.Black, modifier = Modifier.size(30.dp))
                        }

                        IconButton(
                            onClick = { statusMessage = actionEngine.controlMedia("next") },
                            modifier = Modifier.background(SurfaceDark, CircleShape)
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = TextPrimary)
                        }
                    }
                }
            }
        }

        // System Accessibility Shortcuts
        item {
            Text("ACCESSIBILITY ACTIONS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SystemActionButton("হোম (Home)", Icons.Default.Home, Modifier.weight(1f)) {
                    statusMessage = actionEngine.performGlobalAction("home")
                }
                SystemActionButton("ব্যাক (Back)", Icons.Default.ArrowBack, Modifier.weight(1f)) {
                    statusMessage = actionEngine.performGlobalAction("back")
                }
                SystemActionButton("রিসেন্ট", Icons.Default.ViewAgenda, Modifier.weight(1f)) {
                    statusMessage = actionEngine.performGlobalAction("recents")
                }
                SystemActionButton("লক", Icons.Default.Lock, Modifier.weight(1f)) {
                    statusMessage = actionEngine.performGlobalAction("lock")
                }
            }
        }

        // Settings Pages Direct Launcher
        item {
            Text("SYSTEM SETTINGS DIRECT ACCESS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsPill("Wi-Fi", Icons.Default.Wifi, Modifier.weight(1f)) {
                    actionEngine.openSettings("wifi")
                }
                SettingsPill("Bluetooth", Icons.Default.Bluetooth, Modifier.weight(1f)) {
                    actionEngine.openSettings("bluetooth")
                }
                SettingsPill("Display", Icons.Default.Brightness6, Modifier.weight(1f)) {
                    actionEngine.openSettings("display")
                }
                SettingsPill("Sound", Icons.Default.VolumeUp, Modifier.weight(1f)) {
                    actionEngine.openSettings("sound")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsPill("Battery", Icons.Default.BatteryChargingFull, Modifier.weight(1f)) {
                    actionEngine.openSettings("battery")
                }
                SettingsPill("Storage", Icons.Default.SdCard, Modifier.weight(1f)) {
                    actionEngine.openSettings("storage")
                }
                SettingsPill("Apps", Icons.Default.Apps, Modifier.weight(1f)) {
                    actionEngine.openSettings("apps")
                }
                SettingsPill("Files", Icons.Default.Folder, Modifier.weight(1f)) {
                    actionEngine.openFileBrowser()
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ControlTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = NeonBlue,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (isActive) activeColor.copy(alpha = 0.2f) else SurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                if (isActive) listOf(activeColor, activeColor) else listOf(BorderMedium, BorderLight)
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(if (isActive) activeColor else SurfaceDark, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isActive) Color.Black else activeColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
            Text(subtitle, fontSize = 9.sp, color = if (isActive) activeColor else TextSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun SystemActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard),
        contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = NeonBlue, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, fontSize = 9.sp, color = TextPrimary, maxLines = 1)
        }
    }
}

@Composable
private fun SettingsPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .background(SurfaceCard, RoundedCornerShape(10.dp))
            .border(1.dp, BorderLight, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, color = TextPrimary, maxLines = 1)
    }
}
