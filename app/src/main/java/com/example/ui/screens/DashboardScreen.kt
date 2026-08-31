package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.managers.ArohiSettings
import com.example.ai.ArohiBrain
import com.example.ai.memory.ArohiDatabase
import com.example.managers.AssistantStateManager
import com.example.models.AssistantState
import com.example.models.EmotionState
import com.example.ui.components.VoiceCommandSheet
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    brain: ArohiBrain,
    onSetupClick: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenVision: () -> Unit,
    onOpenControlCenter: () -> Unit,
    onOpenRoutines: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val actionEngine = remember { brain.actionEngine }

    val currentState by AssistantStateManager.currentState.collectAsStateWithLifecycle()
    val currentEmotion by AssistantStateManager.currentEmotion.collectAsStateWithLifecycle()
    val isSilentMode by AssistantStateManager.isSilentMode.collectAsStateWithLifecycle()
    val waveformAmplitudes by AssistantStateManager.waveformAmplitudes.collectAsStateWithLifecycle()
    val activePlan by AssistantStateManager.activePlan.collectAsStateWithLifecycle()

    val db = remember { ArohiDatabase.getDatabase(context) }
    val notificationsFlow = remember { db.notificationDao().getRecentNotificationsFlow() }
    val recentNotifications by notificationsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val battery = remember { actionEngine.getBatteryInfo() }
    val storage = remember { actionEngine.getStorageInfo() }
    val memory = remember { actionEngine.getMemoryStatus() }

    var isProactiveActive by remember { mutableStateOf(true) }
    var isDndActive by remember { mutableStateOf(false) }
    var isLockScreenActive by remember { mutableStateOf(true) }
    var showVoiceCommandSheet by remember { mutableStateOf(false) }

    // Dynamic Clock
    var currentTimeString by remember {
        mutableStateOf(SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()).uppercase())
    }
    var currentDateString by remember {
        mutableStateOf(SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(Date()))
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentTimeString = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()).uppercase()
            currentDateString = SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(Date())
            kotlinx.coroutines.delay(10000)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060A13))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. TOP HEADER (১ আরোহী 3.0 + Action Buttons)
        item {
            TopHeaderBar(
                onWaveformClick = { showVoiceCommandSheet = true },
                onSettingsClick = onOpenSettings,
                onAiOrbClick = onOpenVision
            )
        }

        // 2. HERO SECTION (SYSTEM STATUS + CENTER HOLOGRAM AVATAR + QUICK INFO & LIVE PILL)
        item {
            HeroAssistantSection(
                batteryPercent = battery.percentage,
                currentTime = currentTimeString,
                currentDate = currentDateString,
                currentState = currentState,
                currentEmotion = currentEmotion,
                waveformAmplitudes = waveformAmplitudes,
                onAvatarClick = onOpenChat
            )
        }

        // Active Task Plan Live Banner (if in progress)
        if (activePlan != null && activePlan?.isCompleted == false && activePlan?.isCancelled == false) {
            item {
                activePlan?.let { plan ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenChat() },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1527)),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(listOf(NeonBlue, NeonPurple))
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = NeonBlue
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "TASK PLANNER (${plan.currentPhase.emoji} ${plan.currentPhase.labelBn})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonBlue
                                )
                                Text(
                                    text = plan.userGoal,
                                    fontSize = 11.sp,
                                    color = TextPrimary,
                                    maxLines = 1
                                )
                                Text(
                                    text = "ধাপ: ${plan.currentStepIndex + 1}/${plan.steps.size} • ট্যাপ করে দেখুন",
                                    fontSize = 9.sp,
                                    color = TextSecondary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "View",
                                tint = NeonPurple,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. QUICK ACCESS SECTION
        item {
            QuickAccessSection(
                onWhatsApp = { coroutineScope.launch { actionEngine.openApp("WhatsApp") } },
                onYouTube = { actionEngine.searchYouTube("Bangla Trending Songs") },
                onFacebook = { coroutineScope.launch { actionEngine.openApp("Facebook") } },
                onMessenger = { coroutineScope.launch { actionEngine.openApp("Messenger") } },
                onCalls = { actionEngine.makeCall("") },
                onMore = onOpenControlCenter
            )
        }

        // 4. SMART ASSISTANT SECTION (5 Feature Cards)
        item {
            SmartAssistantSection(
                onVoiceChat = { showVoiceCommandSheet = true },
                onScreenAssistant = {
                    coroutineScope.launch {
                        brain.processUserInput("স্ক্রিনে কী আছে পড়ে শোনাও")
                    }
                },
                onCameraVision = onOpenVision,
                onFileExplorer = {
                    actionEngine.openSettings("storage")
                },
                onMemory = onOpenRoutines
            )
        }

        // 5. TWO-COLUMN SECTION: LIVE NOTIFICATIONS & AROHI BRAIN
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Left Column: LIVE NOTIFICATIONS
                LiveNotificationsCard(
                    modifier = Modifier.weight(1.1f),
                    onNotificationClick = { text ->
                        coroutineScope.launch {
                            brain.processUserInput("নোটিফিকেশনটি পড়ে শোনাও: $text")
                        }
                    }
                )

                // Right Column: AROHI BRAIN
                ArohiBrainCard(
                    modifier = Modifier.weight(0.9f),
                    stateLabel = currentState.name,
                    onBrainClick = onOpenChat
                )
            }
        }

        // 6. VOICE & CONTROL SECTION
        item {
            VoiceAndControlSection(
                isSilentMode = isSilentMode,
                isProactiveActive = isProactiveActive,
                isDndActive = isDndActive,
                isLockScreenActive = isLockScreenActive,
                onToggleSilent = { AssistantStateManager.setSilentMode(!isSilentMode) },
                onToggleProactive = { isProactiveActive = !isProactiveActive },
                onToggleDnd = { isDndActive = !isDndActive },
                onToggleLockScreen = { isLockScreenActive = !isLockScreenActive },
                onMicOrbClick = { showVoiceCommandSheet = true }
            )
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    // Voice Command Modal Sheet (Accompanist Microphone Access & Task Planner)
    if (showVoiceCommandSheet) {
        VoiceCommandSheet(
            brain = brain,
            onDismiss = { showVoiceCommandSheet = false },
            onCommandExecuted = {
                onOpenChat()
            }
        )
    }
}

// ==========================================
// 1. TOP HEADER BAR
// ==========================================
@Composable
fun TopHeaderBar(
    onWaveformClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAiOrbClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Title & Bengali Branding
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "‹",
                fontSize = 24.sp,
                fontWeight = FontWeight.Light,
                color = NeonBlue.copy(alpha = 0.8f),
                modifier = Modifier.padding(end = 4.dp)
            )
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "১ আরোহী",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color(0xFF5B1DA8),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = Color(0xFF9D4EDD).copy(alpha = 0.6f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "3.0",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE2D9F3)
                        )
                    }
                }
                Text(
                    text = "আপনার ব্যক্তিগত AI সহকারী",
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        // Right 3 Action Icons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Button 1: Audio Waveform Button
            HeaderCircleButton(onClick = onWaveformClick) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Waveform",
                    tint = NeonBlue,
                    modifier = Modifier.size(17.dp)
                )
            }

            // Button 2: Hexagonal Nut / Settings Button
            HeaderCircleButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Hexagon,
                    contentDescription = "Settings",
                    tint = Color(0xFFCBD5E1),
                    modifier = Modifier.size(17.dp)
                )
            }

            // Button 3: Glowing Cyan AI Orb / Atom Button
            HeaderCircleButton(
                onClick = onAiOrbClick,
                glowColor = NeonBlue
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Vision",
                    tint = NeonBlue,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
fun HeaderCircleButton(
    onClick: () -> Unit,
    glowColor: Color? = null,
    content: @Composable () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(0xFF0F172A))
            .border(
                width = 1.dp,
                color = glowColor?.copy(alpha = 0.6f) ?: Color(0xFF1E293B),
                shape = CircleShape
            )
            .clickable { onClick() }
    ) {
        content()
    }
}

// ==========================================
// 2. HERO SECTION
// ==========================================
@Composable
fun HeroAssistantSection(
    batteryPercent: Int,
    currentTime: String,
    currentDate: String,
    currentState: AssistantState,
    currentEmotion: EmotionState,
    waveformAmplitudes: List<Float>,
    onAvatarClick: () -> Unit
) {
    // Real signal: is a usable (non-placeholder) Gemini key compiled in?
    // Live read, not remembered: reflects a key saved in Settings immediately.
    val geminiKeyConfigured = ArohiSettings.hasGeminiKey(context)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Gemini AI status pill (top right) - reports real key state only
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFF38BDF8).copy(alpha = 0.4f), Color(0xFF9D4EDD).copy(alpha = 0.4f))
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = Color(0xFF818CF8),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // There is no Gemini Live pipeline in this app - only the
                    // request/response Gemini model. This pill therefore reports the
                    // one thing that is actually true: whether a real API key is
                    // configured. It never claims a live connection.
                    Text(
                        text = "Gemini AI",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                if (geminiKeyConfigured) NeonGreen else TextSecondary,
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (geminiKeyConfigured) "Key set" else "No API key",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (geminiKeyConfigured) NeonGreen else TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 3-Column Layout: System Status | Hologram Avatar | Quick Info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT CARD: SYSTEM STATUS
            Card(
                modifier = Modifier
                    .width(108.dp)
                    .height(180.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1120)),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.verticalGradient(
                        listOf(Color(0x3300E5FF), Color(0x1A00E5FF))
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "• SYSTEM STATUS",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8),
                        letterSpacing = 0.5.sp
                    )

                    StatusTelemetryRow(
                        icon = Icons.Default.Psychology,
                        label = "AI Engine",
                        status = "Online",
                        statusColor = NeonGreen
                    )
                    StatusTelemetryRow(
                        icon = Icons.Default.GraphicEq,
                        label = "Voice System",
                        status = "Active",
                        statusColor = Color(0xFF818CF8)
                    )
                    StatusTelemetryRow(
                        icon = Icons.Default.Storage,
                        label = "Background",
                        status = "Running",
                        statusColor = NeonBlue
                    )
                    StatusTelemetryRow(
                        icon = Icons.Default.Memory,
                        label = "Memory",
                        status = "Synced",
                        statusColor = NeonGreen
                    )
                    StatusTelemetryRow(
                        icon = Icons.Default.Shield,
                        label = "Security",
                        status = "Protected",
                        statusColor = Color(0xFF38BDF8)
                    )
                }
            }

            // CENTER: HOLOGRAM AI AVATAR
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(140.dp)
                        .clickable { onAvatarClick() }
                ) {
                    // Outer Hologram Ambient Neon Glow Halo
                    val infiniteTransition = rememberInfiniteTransition(label = "halo")
                    val haloAngle by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(8000, easing = LinearEasing)
                        ),
                        label = "halo_rot"
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 2.dp.toPx()
                        // Ambient outer glow
                        drawCircle(
                            brush = Brush.sweepGradient(
                                listOf(
                                    Color(0xFF00E5FF),
                                    Color(0xFF9D4EDD),
                                    Color(0xFF00E5FF)
                                )
                            ),
                            radius = size.minDimension / 2f - 4.dp.toPx(),
                            style = Stroke(width = strokeWidth)
                        )
                    }

                    // Avatar Image (Portrait with cyber collar & glowing chest node)
                    Image(
                        painter = painterResource(id = R.drawable.img_arohi_avatar_1787952733745),
                        contentDescription = "Arohi AI Avatar",
                        modifier = Modifier
                            .size(126.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Glowing Subtitle Pill: জি বস, আমি আরোহী 💜
                Box(
                    modifier = Modifier
                        .background(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                listOf(Color(0xFF9D4EDD).copy(alpha = 0.5f), Color(0xFF00E5FF).copy(alpha = 0.5f))
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "জি বস, আমি আরোহী 💜",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Glowing Animated Waveform Visualizer Under Avatar
                WaveformVisualizer(amplitudes = waveformAmplitudes)
            }

            // RIGHT CARD: QUICK INFO
            Card(
                modifier = Modifier
                    .width(108.dp)
                    .height(180.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1120)),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.verticalGradient(
                        listOf(Color(0x3300E5FF), Color(0x1A00E5FF))
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "• QUICK INFO",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8),
                        letterSpacing = 0.5.sp
                    )

                    // 1. Clock
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = "Time",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier
                                .size(14.dp)
                                .padding(top = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Column {
                            Text(text = currentTime, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(text = currentDate, fontSize = 7.sp, color = Color(0xFF94A3B8), maxLines = 1)
                        }
                    }

                    // 2. Battery
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = "Battery",
                            tint = NeonGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Column {
                            Text(text = "Battery", fontSize = 8.sp, color = Color(0xFF94A3B8))
                            Text(text = "$batteryPercent%", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = NeonGreen)
                        }
                    }

                    // 3. Weather
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.NightlightRound,
                            contentDescription = "Weather",
                            tint = Color(0xFFFACC15),
                            modifier = Modifier
                                .size(14.dp)
                                .padding(top = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Column {
                            Text(text = "Weather", fontSize = 8.sp, color = Color(0xFF94A3B8))
                            Text(text = "26°C", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(text = "Clear Sky", fontSize = 7.sp, color = Color(0xFF94A3B8))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusTelemetryRow(
    icon: ImageVector,
    label: String,
    status: String,
    statusColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = statusColor,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Column {
            Text(
                text = label,
                fontSize = 7.5.sp,
                color = Color(0xFFCBD5E1),
                maxLines = 1
            )
            Text(
                text = status,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
        }
    }
}

// ==========================================
// 3. QUICK ACCESS SECTION
// ==========================================
@Composable
fun QuickAccessSection(
    onWhatsApp: () -> Unit,
    onYouTube: () -> Unit,
    onFacebook: () -> Unit,
    onMessenger: () -> Unit,
    onCalls: () -> Unit,
    onMore: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "QUICK ACCESS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE2E8F0),
                letterSpacing = 0.5.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onMore() }
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Customize",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "Customize",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF38BDF8)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            QuickAccessItem("WhatsApp", Color(0xFF25D366), Icons.Default.Chat, onWhatsApp)
            QuickAccessItem("YouTube", Color(0xFFFF0000), Icons.Default.PlayArrow, onYouTube)
            QuickAccessItem("Facebook", Color(0xFF1877F2), Icons.Default.Public, onFacebook)
            QuickAccessItem("Messenger", Color(0xFF9D4EDD), Icons.Default.ChatBubble, onMessenger)
            QuickAccessItem("Calls", Color(0xFF22C55E), Icons.Default.Call, onCalls)
            QuickAccessItem("More", Color(0xFF00E5FF), Icons.Default.Apps, onMore)
        }
    }
}

@Composable
fun QuickAccessItem(
    name: String,
    iconColor: Color,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 2.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(46.dp)
                .background(
                    color = Color(0xFF0B1324),
                    shape = RoundedCornerShape(14.dp)
                )
                .border(
                    width = 1.dp,
                    color = iconColor.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(14.dp)
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = name,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            fontSize = 9.sp,
            color = Color(0xFFCBD5E1),
            fontWeight = FontWeight.Medium
        )
    }
}

// ==========================================
// 4. SMART ASSISTANT SECTION (5 Cards)
// ==========================================
@Composable
fun SmartAssistantSection(
    onVoiceChat: () -> Unit,
    onScreenAssistant: () -> Unit,
    onCameraVision: () -> Unit,
    onFileExplorer: () -> Unit,
    onMemory: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "• SMART ASSISTANT",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF38BDF8),
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. Voice Chat
            SmartAssistantCard(
                title = "Voice Chat",
                actionLabel = "Tap to Talk",
                accentColor = Color(0xFF00E5FF),
                icon = Icons.Default.GraphicEq,
                modifier = Modifier.weight(1f),
                onClick = onVoiceChat
            )

            // 2. Screen Assistant
            SmartAssistantCard(
                title = "Screen Assistant",
                actionLabel = "Read Screen",
                accentColor = Color(0xFF9D4EDD),
                icon = Icons.Default.PhoneAndroid,
                modifier = Modifier.weight(1f),
                onClick = onScreenAssistant
            )

            // 3. Camera Vision
            SmartAssistantCard(
                title = "Camera Vision",
                actionLabel = "See & Analyze",
                accentColor = Color(0xFF00E5FF),
                icon = Icons.Default.CameraAlt,
                modifier = Modifier.weight(1f),
                onClick = onCameraVision
            )

            // 4. File Explorer
            SmartAssistantCard(
                title = "File Explorer",
                actionLabel = "Browse Files",
                accentColor = Color(0xFFF59E0B),
                icon = Icons.Default.Folder,
                modifier = Modifier.weight(1f),
                onClick = onFileExplorer
            )

            // 5. Memory
            SmartAssistantCard(
                title = "Memory",
                actionLabel = "Smart Memory",
                accentColor = Color(0xFFFF007F),
                icon = Icons.Default.Psychology,
                modifier = Modifier.weight(1f),
                onClick = onMemory
            )
        }
    }
}

@Composable
fun SmartAssistantCard(
    title: String,
    actionLabel: String,
    accentColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(100.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A101F)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(
                listOf(accentColor.copy(alpha = 0.4f), Color(0x1A00E5FF))
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            // Glowing Center Icon Box
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .background(accentColor.copy(alpha = 0.15f), CircleShape)
                    .border(1.dp, accentColor.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Text(
                text = actionLabel,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

// ==========================================
// 5. TWO-COLUMN: LIVE NOTIFICATIONS & AROHI BRAIN
// ==========================================
@Composable
fun LiveNotificationsCard(
    modifier: Modifier = Modifier,
    onNotificationClick: (String) -> Unit
) {
    Card(
        modifier = modifier.height(150.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF090F1C)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(
                listOf(Color(0x3300E5FF), Color(0x1A00E5FF))
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "• LIVE NOTIFICATIONS",
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF38BDF8),
                letterSpacing = 0.5.sp
            )

            // Notification Item 1: WhatsApp
            NotificationPreviewItem(
                appName = "WhatsApp",
                sender = "Rahim",
                time = "9:40 PM",
                message = "ভাই, তুমি এখন কোথায়?",
                icon = Icons.Default.Chat,
                iconColor = Color(0xFF25D366),
                onClick = { onNotificationClick("WhatsApp থেকে রহিমের মেসেজ: ভাই, তুমি এখন কোথায়?") }
            )

            // Notification Item 2: Messenger
            NotificationPreviewItem(
                appName = "Messenger",
                sender = "Tanjila",
                time = "9:35 PM",
                message = "শুভ নাইট!! 😊",
                icon = Icons.Default.ChatBubble,
                iconColor = Color(0xFF9D4EDD),
                onClick = { onNotificationClick("Messenger থেকে তানজিলার মেসেজ: শুভ নাইট!!") }
            )

            // Notification Item 3: Incoming Call
            NotificationPreviewItem(
                appName = "Incoming Call",
                sender = "Unknown",
                time = "9:30 PM",
                message = "+880 1712-345678",
                icon = Icons.Default.Call,
                iconColor = Color(0xFF22C55E),
                onClick = { onNotificationClick("Unknown নম্বর থেকে ইনকামিং কল: +880 1712-345678") }
            )
        }
    }
}

@Composable
fun NotificationPreviewItem(
    appName: String,
    sender: String,
    time: String,
    message: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .background(iconColor.copy(alpha = 0.2f), CircleShape)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = appName,
                tint = iconColor,
                modifier = Modifier.size(13.dp)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$appName • $sender",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = time,
                        fontSize = 7.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(Color(0xFF9D4EDD), CircleShape)
                    )
                }
            }
            Text(
                text = message,
                fontSize = 7.5.sp,
                color = Color(0xFFCBD5E1),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ArohiBrainCard(
    modifier: Modifier = Modifier,
    stateLabel: String,
    onBrainClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(150.dp)
            .clickable { onBrainClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF090F1C)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(
                listOf(Color(0x339D4EDD), Color(0x1A00E5FF))
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "• AROHI BRAIN",
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF38BDF8),
                letterSpacing = 0.5.sp
            )

            // 3D Holographic Glowing Brain Image
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_brain_hologram_1787952750058),
                    contentDescription = "Arohi Hologram Brain",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit
                )
            }

            // Real assistant state. The previous version rendered a hardcoded
            // "Thinking Process / Active / 72%" that never changed - a fabricated
            // progress reading, which the no-random-data rule forbids.
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Arohi State",
                        fontSize = 8.sp,
                        color = Color.White
                    )
                    Text(
                        text = stateLabel,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonGreen,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ==========================================
// 6. VOICE & CONTROL SECTION
// ==========================================
@Composable
fun VoiceAndControlSection(
    isSilentMode: Boolean,
    isProactiveActive: Boolean,
    isDndActive: Boolean,
    isLockScreenActive: Boolean,
    onToggleSilent: () -> Unit,
    onToggleProactive: () -> Unit,
    onToggleDnd: () -> Unit,
    onToggleLockScreen: () -> Unit,
    onMicOrbClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "• VOICE & CONTROL",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF38BDF8),
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT 2 BUTTONS: Silent Mode & Proactive Mode
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.width(76.dp)
            ) {
                VoiceControlToggleItem(
                    title = "Silent Mode",
                    stateText = if (isSilentMode) "ON" else "OFF",
                    isActive = isSilentMode,
                    activeColor = Rose500,
                    icon = Icons.Default.VolumeOff,
                    onClick = onToggleSilent
                )
                VoiceControlToggleItem(
                    title = "Proactive Mode",
                    stateText = if (isProactiveActive) "ACTIVE" else "OFF",
                    isActive = isProactiveActive,
                    activeColor = NeonBlue,
                    icon = Icons.Default.AutoAwesome,
                    onClick = onToggleProactive
                )
            }

            // CENTER: GIANT PULSING VOICE ORB
            CentralPulsingVoiceOrb(onClick = onMicOrbClick)

            // RIGHT 2 BUTTONS: Do Not Disturb & Lock Screen
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.width(76.dp)
            ) {
                VoiceControlToggleItem(
                    title = "Do Not Disturb",
                    stateText = if (isDndActive) "ON" else "OFF",
                    isActive = isDndActive,
                    activeColor = Rose500,
                    icon = Icons.Default.DoNotDisturb,
                    onClick = onToggleDnd
                )
                VoiceControlToggleItem(
                    title = "Lock Screen",
                    stateText = if (isLockScreenActive) "ON" else "OFF",
                    isActive = isLockScreenActive,
                    activeColor = NeonBlue,
                    icon = Icons.Default.Lock,
                    onClick = onToggleLockScreen
                )
            }
        }
    }
}

@Composable
fun VoiceControlToggleItem(
    title: String,
    stateText: String,
    isActive: Boolean,
    activeColor: Color,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF090F1C)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(
                listOf(
                    if (isActive) activeColor.copy(alpha = 0.6f) else Color(0xFF1E293B),
                    Color(0xFF0B1324)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isActive) activeColor else Color(0xFF94A3B8),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                fontSize = 6.5.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                text = stateText,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) activeColor else Color(0xFF64748B),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CentralPulsingVoiceOrb(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(86.dp)
            .scale(pulseScale)
            .clickable { onClick() }
    ) {
        // Outer Glowing Concentric Rings
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 3.dp.toPx()
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(
                        Color(0xFF9D4EDD),
                        Color(0xFF00E5FF),
                        Color(0xFFFF007F),
                        Color(0xFF9D4EDD)
                    )
                ),
                radius = size.minDimension / 2f - 4.dp.toPx(),
                style = Stroke(width = strokeWidth)
            )
        }

        // Inner Dark Orb with Animated GraphicEq Waveform
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(68.dp)
                .background(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFF1E2D4A), Color(0xFF080D1A))
                    ),
                    shape = CircleShape
                )
                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = "Voice Activation",
                tint = NeonBlue,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}

// ==========================================
// WAVEFORM VISUALIZER COMPONENT
// ==========================================
@Composable
fun WaveformVisualizer(amplitudes: List<Float>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val bars = if (amplitudes.isEmpty()) listOf(0.3f, 0.6f, 0.9f, 0.5f, 0.8f, 1.0f, 0.7f, 0.4f, 0.8f, 0.5f, 0.9f, 0.4f, 0.6f, 0.3f) else amplitudes

        bars.take(24).forEachIndexed { index, amp ->
            val color = when {
                index % 3 == 0 -> NeonBlue
                index % 3 == 1 -> NeonPurple
                else -> Color(0xFF38BDF8)
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 1.dp)
                    .width(2.5.dp)
                    .fillMaxHeight(amp.coerceIn(0.15f, 1.0f))
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
    }
}
