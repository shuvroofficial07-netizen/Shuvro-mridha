package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.ArohiBrain
import com.example.ai.memory.ArohiDatabase
import com.example.ai.memory.CapturedNotification
import com.example.services.ArohiNotificationService
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InboxScreen(brain: ArohiBrain) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { ArohiDatabase.getDatabase(context) }
    val notificationsFlow = remember { db.notificationDao().getRecentNotificationsFlow() }
    val notifications by notificationsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var selectedFilter by remember { mutableStateOf("ALL") }
    val isListenerConnected = ArohiNotificationService.isConnected

    val filteredList = remember(notifications, selectedFilter) {
        when (selectedFilter) {
            "HIGH" -> notifications.filter { it.priority == "HIGH" }
            "MESSAGES" -> notifications.filter {
                val p = it.packageName.lowercase()
                p.contains("whatsapp") || p.contains("orca") || p.contains("messaging") || p.contains("telegram")
            }
            "CALLS" -> notifications.filter {
                val p = it.packageName.lowercase()
                p.contains("dialer") || p.contains("telecom") || p.contains("incall")
            }
            else -> notifications
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "NOTIFICATION INTELLIGENCE",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonBlue
                )
                Text(
                    text = if (isListenerConnected) "Live Listener Active ✔" else "Notification Listener Inactive",
                    fontSize = 10.sp,
                    color = if (isListenerConnected) NeonGreen else Orange400
                )
            }

            if (notifications.isNotEmpty()) {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            db.notificationDao().clearAll()
                        }
                    }
                ) {
                    Text("Clear All", fontSize = 11.sp, color = Rose400)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // AI Notification Summarizer Button
        Button(
            onClick = {
                if (notifications.isNotEmpty()) {
                    val summaryInput = "সাম্প্রতিক নোটিফিকেশনগুলোর সারাংশ পড়ে সুন্দর করে বাংলায় বলো:\n" +
                            notifications.take(6).joinToString("\n") { "${it.appName} (${it.sender}): ${it.text}" }
                    coroutineScope.launch {
                        brain.processUserInput(summaryInput)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard),
            border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(NeonBlue, NeonPurple)))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NeonBlue, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Gemini AI দিয়ে সমস্ত নোটিফিকেশন সামারি শুনুন",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Pills
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf(
                "ALL" to "সকল (${notifications.size})",
                "HIGH" to "জরুরি (High)",
                "MESSAGES" to "মেসেজ (WhatsApp/SMS)",
                "CALLS" to "কল হিস্ট্রি"
            )
            items(filters) { (key, label) ->
                val isSelected = selectedFilter == key
                Box(
                    modifier = Modifier
                        .background(if (isSelected) NeonPurple.copy(alpha = 0.3f) else SurfaceCard, RoundedCornerShape(16.dp))
                        .border(1.dp, if (isSelected) NeonPurple else BorderMedium, RoundedCornerShape(16.dp))
                        .clickable { selectedFilter = key }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        color = if (isSelected) NeonBlue else TextPrimary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.NotificationsNone,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("কোনো নোটিফিকেশন পাওয়া যায়নি।", fontSize = 12.sp, color = TextSecondary)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredList) { notif ->
                    NotificationCard(notif) {
                        // Click to read aloud with Arohi
                        val prompt = "${notif.appName} থেকে ${notif.sender}-এর নোটিফিকেশনটি পড়ে শোনাও: ${notif.text}"
                        coroutineScope.launch {
                            brain.processUserInput(prompt)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationCard(item: CapturedNotification, onClick: () -> Unit) {
    val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(item.timestamp))

    val appColor = when {
        item.packageName.contains("whatsapp") -> Color(0xFF25D366)
        item.packageName.contains("orca") -> NeonPurple
        item.packageName.contains("youtube") -> Color(0xFFFF0000)
        item.packageName.contains("dialer") -> NeonGreen
        item.priority == "HIGH" -> Rose500
        else -> NeonBlue
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(BorderMedium, BorderLight)))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(appColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${item.appName} • ${item.sender}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(timeFmt, fontSize = 9.sp, color = TextSecondary)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.text,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 2
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = "Read Aloud",
                tint = NeonBlue,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
