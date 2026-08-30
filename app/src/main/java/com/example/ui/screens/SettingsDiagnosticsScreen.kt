package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.diagnostics.ArohiDiagnostics
import com.example.diagnostics.DiagnosticItem
import com.example.diagnostics.DiagnosticStatus
import com.example.diagnostics.DiagnosticsReport
import com.example.managers.AssistantStateManager
import com.example.models.ProactiveSensitivity
import com.example.ui.theme.*
import com.example.utils.SettingsNavigator
import kotlinx.coroutines.launch

@Composable
fun SettingsDiagnosticsScreen(onOpenPermissionSetup: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val diagnostics = remember { ArohiDiagnostics(context) }

    var report by remember { mutableStateOf<DiagnosticsReport?>(null) }
    var isRunningDiag by remember { mutableStateOf(false) }

    val isSilentMode by AssistantStateManager.isSilentMode.collectAsStateWithLifecycle()
    val isPrivateMode by AssistantStateManager.isPrivateMode.collectAsStateWithLifecycle()
    val proactiveSensitivity by AssistantStateManager.proactiveSensitivity.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        isRunningDiag = true
        report = diagnostics.runFullDiagnostics()
        isRunningDiag = false
    }

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
                        text = "SETTINGS & SYSTEM HEALTH",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonBlue
                    )
                    Text(
                        text = "Privacy, Telemetry & Diagnostics",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }

                Button(
                    onClick = onOpenPermissionSetup,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(NeonBlue, NeonPurple))),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Permissions", fontSize = 10.sp, color = NeonBlue)
                }
            }
        }

        // Diagnostics Card
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
                            Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("সিস্টেম ডায়াগনস্টিক রিপোর্ট", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }

                        if (isRunningDiag) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = NeonBlue)
                        } else {
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        isRunningDiag = true
                                        report = diagnostics.runFullDiagnostics()
                                        isRunningDiag = false
                                    }
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("পুনরায় চালান (Run)", fontSize = 10.sp, color = NeonBlue)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    report?.let { r ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DiagBadge("READY", r.readyCount, NeonGreen, Modifier.weight(1f))
                            DiagBadge("LIMITED", r.limitedCount, Orange400, Modifier.weight(1f))
                            DiagBadge("ERROR", r.errorCount, Rose500, Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Subsystems Detailed List
        report?.let { r ->
            item {
                Text("SUBSYSTEM AUDIT (${r.items.size} COMPONENTS)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            }
            items(r.items) { item ->
                DiagnosticItemCard(item) {
                    // Open relevant settings page
                    when {
                        item.title.contains("নোটিফিকেশন") -> SettingsNavigator.openSystemSettings(context, "NOTIFICATION_ACCESS")
                        item.title.contains("অ্যাক্সেসিবিলিটি") -> SettingsNavigator.openSystemSettings(context, "ACCESSIBILITY")
                        else -> onOpenPermissionSetup()
                    }
                }
            }
        }

        // Privacy Center
        item {
            Text("PRIVACY & CONFIDENTIALITY CENTER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(BorderMedium, BorderLight)))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Private Mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("প্রাইভেট মোড (Private Mode)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("ব্যক্তিগত নোটিফিকেশন প্রিভিউ ও কোয়েরি গোপন রাখবে।", fontSize = 9.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = isPrivateMode,
                            onCheckedChange = { AssistantStateManager.setPrivateMode(it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = NeonPurple)
                        )
                    }

                    HorizontalDivider(color = BorderLight)

                    // Silent Mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("নীরব মোড (Silent / Quiet Mode)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("আরোহীর ভয়েস স্পিচ সম্পূর্ণ বন্ধ থাকবে (টেক্সট উত্তর দৃশ্যমান থাকবে)।", fontSize = 9.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = isSilentMode,
                            onCheckedChange = { AssistantStateManager.setSilentMode(it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = NeonPink)
                        )
                    }
                }
            }
        }

        // Proactive Mode Sensitivity
        item {
            Text("PROACTIVE ASSISTANT SENSITIVITY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProactiveSensitivity.values().forEach { sens ->
                    val isSelected = proactiveSensitivity == sens
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (isSelected) NeonBlue.copy(alpha = 0.25f) else SurfaceCard, RoundedCornerShape(10.dp))
                            .border(1.dp, if (isSelected) NeonBlue else BorderMedium, RoundedCornerShape(10.dp))
                            .clickable { AssistantStateManager.setProactiveSensitivity(sens) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = sens.name,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) NeonBlue else TextPrimary
                        )
                    }
                }
            }
        }

        // About Arohi
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(NeonPurple, NeonBlue)))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Arohi AI Assistant", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                    Text("by Shù Vrô", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonBlue)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Version: v7.0.1 • Autonomous Personal AI Operating Layer", fontSize = 9.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Target Device: Samsung Galaxy S8+ (Android 9/API 28, 4GB RAM) Optimized", fontSize = 9.sp, color = NeonGreen)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DiagBadge(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$count", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 9.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun DiagnosticItemCard(item: DiagnosticItem, onFix: () -> Unit) {
    val statusColor = when (item.status) {
        DiagnosticStatus.READY -> NeonGreen
        DiagnosticStatus.LIMITED -> Orange400
        DiagnosticStatus.ERROR -> Rose500
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(BorderMedium, BorderLight)))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(item.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Box(
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(item.status.name, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = statusColor)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(item.detail, fontSize = 10.sp, color = TextSecondary)

            if (item.recommendation.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item.recommendation, fontSize = 9.sp, color = Orange400, modifier = Modifier.weight(1f))
                    TextButton(onClick = onFix, contentPadding = PaddingValues(0.dp)) {
                        Text("FIX / GRANT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = NeonBlue)
                    }
                }
            }
        }
    }
}
