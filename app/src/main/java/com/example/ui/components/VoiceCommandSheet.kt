package com.example.ui.components

import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.ArohiBrain
import com.example.ai.voice.ArohiSpeechRecognizerManager
import com.example.ui.theme.*
import com.example.utils.SettingsNavigator
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun VoiceCommandSheet(
    brain: ArohiBrain,
    onDismiss: () -> Unit,
    onCommandExecuted: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Accompanist Permission State for RECORD_AUDIO
    val micPermissionState = rememberPermissionState(
        permission = Manifest.permission.RECORD_AUDIO
    )

    val speechManager = remember { ArohiSpeechRecognizerManager(context) }
    val isListening by speechManager.isListening.collectAsState()
    val recognizedText by speechManager.recognizedText.collectAsState()
    val partialTranscript by speechManager.partialTranscript.collectAsState()
    val rmsLevel by speechManager.rmsLevel.collectAsState()
    val statusMessage by speechManager.statusMessage.collectAsState()

    var manualInput by remember { mutableStateOf("") }
    var isExecuting by remember { mutableStateOf(false) }

    // Start listening once permission is confirmed granted
    LaunchedEffect(micPermissionState.status.isGranted) {
        if (micPermissionState.status.isGranted) {
            speechManager.startListening { finalResult ->
                if (finalResult.isNotBlank()) {
                    manualInput = finalResult
                    isExecuting = true
                    coroutineScope.launch {
                        brain.processUserInput(finalResult)
                        onCommandExecuted(finalResult)
                        kotlinx.coroutines.delay(1200)
                        onDismiss()
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechManager.destroy()
        }
    }

    val sampleVoiceCommands = listOf(
        "টর্চ জ্বালাও এবং ভলিউম ৫০% করো ⚡",
        "ব্যাটারি চেক করো তারপর YouTube-এ গান চালাও 🎵",
        "ভলিউম মিউট করো এবং WhatsApp খোলো 💬",
        "Start My Day রুটিন চালাও ☀️",
        "Good Night রুটিন শুরু করো 🌙",
        "এই স্ক্রিনে কী আছে পড়ে শোনাও 👁️",
        "Recent notifications সামারি করো 📩"
    )

    ModalBottomSheet(
        onDismissRequest = {
            speechManager.stopListening()
            onDismiss()
        },
        containerColor = Color(0xFF090E1A),
        scrimColor = Color.Black.copy(alpha = 0.7f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color(0xFF334155), RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!micPermissionState.status.isGranted) {
                // =========================================================
                // ACCOMPANIST PERMISSION RATIONALE / REQUEST VIEW
                // =========================================================
                MicrophonePermissionRationaleContent(
                    shouldShowRationale = micPermissionState.status.shouldShowRationale,
                    onRequestPermission = {
                        micPermissionState.launchPermissionRequest()
                    },
                    onOpenSettings = {
                        SettingsNavigator.openAppSettings(context)
                    }
                )
            } else {
                // =========================================================
                // ACTIVE VOICE RECOGNITION & TASK PLANNER TRIGGER INTERFACE
                // =========================================================
                // 1. Header Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(Color(0xFF0F172A), Color(0xFF1E1B4B))
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(listOf(NeonBlue, NeonPurple)),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Voice Active",
                        tint = NeonBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ভয়েস টাস্ক প্ল্যানার সক্রিয়",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Hologram Pulsing Orb with RMS visualizer
                InteractiveVoiceOrb(
                    isListening = isListening,
                    rmsLevel = rmsLevel,
                    isExecuting = isExecuting,
                    onToggleListen = {
                        if (isListening) {
                            speechManager.stopListening()
                        } else {
                            speechManager.startListening { finalResult ->
                                if (finalResult.isNotBlank()) {
                                    manualInput = finalResult
                                    isExecuting = true
                                    coroutineScope.launch {
                                        brain.processUserInput(finalResult)
                                        onCommandExecuted(finalResult)
                                        kotlinx.coroutines.delay(1200)
                                        onDismiss()
                                    }
                                }
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Status Message / Live Transcript
                val displayTranscript = when {
                    isExecuting -> "টাস্ক প্ল্যানিং শুরু হচ্ছে ⚡..."
                    recognizedText.isNotBlank() -> recognizedText
                    partialTranscript.isNotBlank() -> partialTranscript
                    else -> statusMessage
                }

                Text(
                    text = displayTranscript,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isExecuting || recognizedText.isNotBlank()) NeonBlue else Color(0xFFE2E8F0),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "কথা বলুন (যেমন: 'টর্চ জ্বালাও এবং ভলিউম ৫০% করো')",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 4. Quick Multi-Step Voice Command Chips
                Text(
                    text = "দ্রুত কমান্ড নির্বাচন করুন:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFCBD5E1),
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sampleVoiceCommands) { cmd ->
                        Box(
                            modifier = Modifier
                                .background(
                                    color = Color(0xFF131D33),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    manualInput = cmd
                                    isExecuting = true
                                    speechManager.submitRecognizedCommand(cmd)
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = cmd,
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 5. Input Field + Send Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualInput,
                        onValueChange = { manualInput = it },
                        placeholder = {
                            Text(
                                "অথবা এখানে লিখে টাস্ক প্ল্যান করুন...",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF0F172A), RoundedCornerShape(14.dp)),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonBlue,
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (manualInput.isNotBlank()) {
                                val cmd = manualInput
                                isExecuting = true
                                speechManager.submitRecognizedCommand(cmd)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonBlue),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Run Plan",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "প্ল্যান",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// ACCOMPANIST MICROPHONE PERMISSION RATIONALE VIEW
// =========================================================================
@Composable
fun MicrophonePermissionRationaleContent(
    shouldShowRationale: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Glowing animated icon header
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .background(
                    brush = Brush.radialGradient(
                        listOf(Color(0x3300E5FF), Color(0x00000000))
                    ),
                    shape = CircleShape
                )
                .border(1.5.dp, NeonBlue.copy(alpha = 0.7f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Microphone Permission",
                tint = NeonBlue,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "মাইক্রোফোন অনুমতি প্রয়োজন",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "আরোহী ভয়েস কমান্ডের মাধ্যমে মাল্টি-স্টেপ টাস্ক প্ল্যানিং এবং ডিভাইস অ্যাকশন সম্পন্ন করতে মাইক্রোফোন অ্যাক্সেস প্রয়োজন।",
            fontSize = 12.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Feature cards
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(
                    listOf(Color(0x3300E5FF), Color(0x339D4EDD))
                )
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PermissionBulletPoint(
                    icon = Icons.Default.AutoAwesome,
                    iconColor = NeonBlue,
                    title = "ভয়েস মাল্টি-স্টেপ টাস্ক প্ল্যানার",
                    description = "মুখে কমান্ড দিন: যেমন 'টর্চ জ্বালাও এবং ভলিউম ৫০% করো'। আরোহী স্বয়ংক্রিয়ভাবে প্ল্যান তৈরি ও এক্সিকিউট করবে।"
                )
                PermissionBulletPoint(
                    icon = Icons.Default.GraphicEq,
                    iconColor = NeonPurple,
                    title = "বাংলা ও ইংরেজি ভয়েস ইন্টারঅ্যাকশন",
                    description = "স্মার্ট ন্যাচারাল স্পিচ শনাক্তকরণ ও রিয়েল-টাইম বাংলা ভয়েস রেসপন্স।"
                )
                PermissionBulletPoint(
                    icon = Icons.Default.Shield,
                    iconColor = NeonGreen,
                    title = "অন-ডিভাইস প্রাইভেসি সুরক্ষা",
                    description = "ভয়েস ডাটা সুরক্ষিত থাকে এবং কেবল আপনার নির্দেশ পালনেই ব্যবহৃত হয়।"
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action Buttons
        Button(
            onClick = onRequestPermission,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonBlue),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "মাইক্রোফোন অনুমতি দিন (Allow Microphone)",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        if (shouldShowRationale) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF334155), Color(0xFF475569))
                    )
                )
            ) {
                Text(
                    text = "অ্যাপ সেটিংসে যান (Open App Settings)",
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun PermissionBulletPoint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .background(iconColor.copy(alpha = 0.15f), CircleShape)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = description,
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 15.sp
            )
        }
    }
}

// =========================================================================
// INTERACTIVE VOICE ORB WITH RMS DYNAMICS
// =========================================================================
@Composable
fun InteractiveVoiceOrb(
    isListening: Boolean,
    rmsLevel: Float,
    isExecuting: Boolean,
    onToggleListen: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val currentScale = if (isListening) (pulseScale + rmsLevel * 0.25f).coerceIn(0.95f, 1.4f) else 1.0f

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(110.dp)
            .scale(currentScale)
            .clickable { onToggleListen() }
    ) {
        // Outer pulsing ring
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = (2.dp + (rmsLevel * 3).dp).toPx()
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(
                        if (isExecuting) NeonPurple else NeonBlue,
                        Color(0xFF9D4EDD),
                        if (isExecuting) NeonPurple else NeonBlue
                    )
                ),
                radius = size.minDimension / 2f - 6.dp.toPx(),
                style = Stroke(width = strokeWidth)
            )
        }

        // Inner glowing circle
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(76.dp)
                .background(
                    brush = Brush.radialGradient(
                        listOf(
                            if (isExecuting) Color(0xFF7928CA) else Color(0xFF0070F3),
                            Color(0xFF0F172A)
                        )
                    ),
                    shape = CircleShape
                )
                .border(
                    width = 2.dp,
                    color = if (isExecuting) NeonPurple else NeonBlue,
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = when {
                    isExecuting -> Icons.Default.AutoAwesome
                    isListening -> Icons.Default.GraphicEq
                    else -> Icons.Default.Mic
                },
                contentDescription = "Microphone",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
