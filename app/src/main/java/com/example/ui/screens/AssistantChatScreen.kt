package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.example.ai.ArohiBrain
import com.example.managers.AssistantStateManager
import com.example.models.*
import com.example.services.ArohiForegroundService
import com.example.ui.components.VoiceCommandSheet
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AssistantChatScreen(
    brain: ArohiBrain,
    onOpenVision: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val chatMessages by AssistantStateManager.chatMessages.collectAsStateWithLifecycle()
    val currentState by AssistantStateManager.currentState.collectAsStateWithLifecycle()
    val currentEmotion by AssistantStateManager.currentEmotion.collectAsStateWithLifecycle()
    val activePlan by AssistantStateManager.activePlan.collectAsStateWithLifecycle()
    val pendingConfirmation by AssistantStateManager.pendingConfirmation.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var showVoiceSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    val quickPrompts = listOf(
        "টর্চ জ্বালাও এবং ভলিউম ৫০% করো ⚡",
        "ব্যাটারি চেক করো তারপর ইউটিউবে গান চালাও 🎵",
        "ভলিউম মিউট করো এবং WhatsApp খোলো 💬",
        "Start My Day রুটিন চালাও ☀️",
        "আমার battery কত?",
        "Recent notifications সামারি করো 📩",
        "এই স্ক্রিনে কী আছে পড়ে শোনাও 👁️",
        "Good Night রুটিন চালাও 🌙",
        "How are you Arohi? 💜"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "AROHI TASK PLANNER & CHAT",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonBlue
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = currentEmotion.emoji,
                        fontSize = 16.sp
                    )
                }
                Text(
                    text = "State: ${currentState.name} • Emotion: ${currentEmotion.labelBn}",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }

            IconButton(
                onClick = onOpenVision,
                modifier = Modifier
                    .size(36.dp)
                    .background(SurfaceCard, CircleShape)
                    .border(1.dp, NeonPurple, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Camera Vision",
                    tint = NeonPurple,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Active Task Plan Progress Card (Real-Time Stepper)
        AnimatedVisibility(visible = activePlan != null && activePlan?.isCompleted == false && activePlan?.isCancelled == false) {
            activePlan?.let { plan ->
                TaskPlanLiveCard(
                    plan = plan,
                    onCancel = {
                        brain.taskPlannerEngine.cancelActivePlan()
                    }
                )
            }
        }

        // Dangerous Action Confirmation Banner
        AnimatedVisibility(visible = pendingConfirmation != null) {
            pendingConfirmation?.let { conf ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF261019)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(Rose500, Orange500)))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Rose500, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(conf.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Rose400)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(conf.description, fontSize = 11.sp, color = TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { AssistantStateManager.setPendingConfirmation(null) }) {
                                Text("বাতিল (Cancel)", color = TextSecondary, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        brain.executeConfirmedAction(conf)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Rose600),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("নিশ্চিত (Proceed)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Chat Message History List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(chatMessages) { message ->
                ChatBubble(message)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Suggestion Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickPrompts) { prompt ->
                Box(
                    modifier = Modifier
                        .background(SurfaceCard, RoundedCornerShape(20.dp))
                        .border(1.dp, BorderMedium, RoundedCornerShape(20.dp))
                        .clickable {
                            inputText = prompt
                            coroutineScope.launch {
                                brain.processUserInput(prompt)
                                inputText = ""
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = prompt,
                        fontSize = 11.sp,
                        color = TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("মাল্টি-স্টেপ বা একক কমান্ড বলুন (e.g. টর্চ জ্বালাও এবং ভলিউম ৫০% করো)...", fontSize = 11.sp, color = TextSecondary) },
                modifier = Modifier
                    .weight(1f)
                    .background(SurfaceCard, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonBlue,
                    unfocusedBorderColor = BorderMedium,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                maxLines = 3
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Accompanist Microphone Voice Command Trigger
            IconButton(
                onClick = {
                    showVoiceSheet = true
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = Color(0xFF1E293B),
                        shape = CircleShape
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(listOf(NeonBlue, NeonPurple)),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Command",
                    tint = NeonBlue,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        val query = inputText
                        inputText = ""
                        coroutineScope.launch {
                            brain.processUserInput(query)
                        }
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Brush.linearGradient(listOf(NeonBlue, NeonPurple)),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showVoiceSheet) {
        VoiceCommandSheet(
            brain = brain,
            onDismiss = { showVoiceSheet = false },
            onCommandExecuted = {
                inputText = ""
            }
        )
    }
}

@Composable
fun TaskPlanLiveCard(
    plan: TaskPlan,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(NeonBlue, NeonPurple)))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header with Phase
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "⚡ TASK PLANNER ENGINE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonBlue
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(NeonPurple.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .border(1.dp, NeonPurple, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${plan.currentPhase.emoji} ${plan.currentPhase.labelBn}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonPurple
                        )
                    }
                }

                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel Plan",
                        tint = Rose400,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "লক্ষ্য: ${plan.userGoal}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 5-Phase Horizontal Stepper Indicator
            PhaseStepperBar(currentPhase = plan.currentPhase)

            Spacer(modifier = Modifier.height(10.dp))

            // Steps
            plan.steps.forEachIndexed { idx, step ->
                StepRowItem(idx = idx, step = step)
            }
        }
    }
}

@Composable
fun PhaseStepperBar(currentPhase: PlanPhase) {
    val phases = listOf(
        PlanPhase.UNDERSTAND,
        PlanPhase.PLAN,
        PlanPhase.EXECUTE,
        PlanPhase.VERIFY,
        PlanPhase.REPORT
    )

    val currentPhaseIndex = when (currentPhase) {
        PlanPhase.UNDERSTAND -> 0
        PlanPhase.PLAN -> 1
        PlanPhase.EXECUTE -> 2
        PlanPhase.VERIFY -> 3
        PlanPhase.REPORT, PlanPhase.COMPLETED -> 4
        PlanPhase.CANCELLED, PlanPhase.FAILED -> -1
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(8.dp))
            .padding(vertical = 4.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        phases.forEachIndexed { index, phase ->
            val isActive = index == currentPhaseIndex
            val isDone = index < currentPhaseIndex || currentPhase == PlanPhase.COMPLETED

            val nodeBg = when {
                isActive -> NeonBlue
                isDone -> NeonGreen
                else -> BorderMedium
            }

            val textColor = when {
                isActive -> NeonBlue
                isDone -> NeonGreen
                else -> TextSecondary
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(14.dp)
                        .background(nodeBg.copy(alpha = if (isActive || isDone) 0.3f else 0.1f), CircleShape)
                        .border(1.dp, nodeBg, CircleShape)
                ) {
                    Text(
                        text = if (isDone) "✓" else "${index + 1}",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive || isDone) Color.White else TextSecondary
                    )
                }
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = phase.labelEn,
                    fontSize = 9.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = textColor
                )
            }

            if (index < phases.size - 1) {
                Text(">", fontSize = 8.sp, color = BorderMedium)
            }
        }
    }
}

@Composable
fun StepRowItem(idx: Int, step: TaskStep) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        val (statusIcon, statusColor) = when (step.status) {
            StepStatus.COMPLETED -> Icons.Default.CheckCircle to NeonGreen
            StepStatus.IN_PROGRESS -> Icons.Default.Sync to NeonBlue
            StepStatus.FAILED -> Icons.Default.Error to Rose500
            StepStatus.PENDING -> Icons.Default.RadioButtonUnchecked to TextSecondary
            StepStatus.SKIPPED -> Icons.Default.RemoveCircleOutline to Orange400
        }

        Icon(
            imageVector = statusIcon,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier
                .size(15.dp)
                .padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${idx + 1}. ${step.title}",
                    fontSize = 11.sp,
                    color = if (step.status == StepStatus.IN_PROGRESS) NeonBlue else TextPrimary,
                    fontWeight = if (step.status == StepStatus.IN_PROGRESS) FontWeight.Bold else FontWeight.Medium
                )
                if (step.executionTimeMs > 0) {
                    Text(
                        text = "⏱ ${step.executionTimeMs}ms",
                        fontSize = 8.sp,
                        color = TextSecondary
                    )
                }
            }

            if (step.details.isNotBlank()) {
                Text(
                    text = step.details,
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }

            if (step.isVerified && step.verificationNote.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = "Verified",
                        tint = NeonPurple,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = step.verificationNote,
                        fontSize = 9.sp,
                        color = NeonPurple
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.sender == "USER"
    val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 2.dp)
        ) {
            if (!isUser) {
                Text("আরোহী v8.0 ${message.emotion.emoji}", fontSize = 10.sp, color = NeonBlue, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(timeFmt, fontSize = 9.sp, color = TextSecondary)
            if (isUser) {
                Spacer(modifier = Modifier.width(6.dp))
                Text("আপনি (You)", fontSize = 10.sp, color = NeonGreen, fontWeight = FontWeight.Bold)
            }
        }

        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(
                    if (isUser) Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A)))
                    else Brush.linearGradient(listOf(Color(0xFF1E1035), Color(0xFF131C31))),
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .border(
                    1.dp,
                    if (isUser) BorderMedium else NeonPurple.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .padding(12.dp)
        ) {
            Column {
                Text(
                    text = message.text,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                // Inline Plan Visualizer in Chat History
                message.plan?.let { plan ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(BorderMedium, BorderLight)))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📋 সিকোয়েন্স রিপোর্ট (${plan.steps.size} ধাপ)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonBlue
                                )
                                Text(
                                    text = "${plan.currentPhase.emoji} ${plan.currentPhase.labelEn}",
                                    fontSize = 9.sp,
                                    color = NeonGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            plan.steps.forEachIndexed { idx, step ->
                                StepRowItem(idx = idx, step = step)
                            }
                        }
                    }
                }
            }
        }
    }
}

