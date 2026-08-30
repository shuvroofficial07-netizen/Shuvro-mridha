package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Facebook
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

/**
 * Arohi Support Center.
 *
 * Every entry opens a real deep link through Android's normal intent mechanism.
 * Nothing here is decorative: if no app can handle the link the user is told so,
 * rather than being shown a success message for an action that never happened.
 */

private const val SUPPORT_FACEBOOK = "https://www.facebook.com/shuvromridha77"
private const val SUPPORT_WHATSAPP = "https://wa.me/8801915551436"
private const val SUPPORT_TELEGRAM = "https://t.me/Shuvrojr07"

@Composable
fun SupportScreen() {
    val context = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "AROHI SUPPORT CENTER",
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = NeonBlue
        )
        Text(
            text = "Arohi AI Assistant by Shù Vrô",
            fontSize = 11.sp,
            color = TextSecondary
        )
        Text(
            text = "সরাসরি যোগাযোগ করুন। নিচের যেকোনো মাধ্যমে মেসেজ দিলে সাপোর্ট টিম উত্তর দেবে।",
            fontSize = 10.sp,
            color = TextSecondary
        )

        SupportLinkCard(
            title = "Facebook Support",
            subtitle = SUPPORT_FACEBOOK,
            detail = "@shuvromridha77",
            icon = Icons.Default.Facebook,
            accent = Color(0xFF1877F2)
        ) { status = openSupportLink(context, SUPPORT_FACEBOOK) }

        SupportLinkCard(
            title = "WhatsApp Support",
            subtitle = SUPPORT_WHATSAPP,
            detail = "+880 1915-551436",
            icon = Icons.Default.Chat,
            accent = NeonGreen
        ) { status = openSupportLink(context, SUPPORT_WHATSAPP) }

        SupportLinkCard(
            title = "Telegram Support",
            subtitle = SUPPORT_TELEGRAM,
            detail = "@Shuvrojr07",
            icon = Icons.Default.SupportAgent,
            accent = Color(0xFF2AABEE)
        ) { status = openSupportLink(context, SUPPORT_TELEGRAM) }

        status?.let {
            Text(
                text = it,
                fontSize = 10.sp,
                color = if (it.startsWith("ত্রুটি")) NeonPurple else NeonGreen
            )
        }
    }
}

/**
 * Opens a support link with the real Android intent system and reports what
 * actually happened.
 */
private fun openSupportLink(context: Context, url: String): String {
    return try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        "লিংক খোলা হয়েছে।"
    } catch (e: Exception) {
        "ত্রুটি: এই লিংক খোলার মতো কোনো অ্যাপ পাওয়া যায়নি (${e.javaClass.simpleName})"
    }
}

@Composable
private fun SupportLinkCard(
    title: String,
    subtitle: String,
    detail: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .background(accent.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(detail, fontSize = 10.sp, color = accent, fontWeight = FontWeight.Medium)
                Text(subtitle, fontSize = 9.sp, color = TextSecondary)
            }
            Text("OPEN", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = NeonBlue)
        }
    }
}
