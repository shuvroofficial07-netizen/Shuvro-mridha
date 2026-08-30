package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.managers.PermissionManager
import com.example.ui.theme.*
import com.example.utils.SettingsNavigator
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionSetupScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val micPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    var hasContacts by remember { mutableStateOf(false) }
    var hasPhone by remember { mutableStateOf(false) }
    var hasNotifications by remember { mutableStateOf(false) }
    var hasAccessibility by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasContacts = PermissionManager.hasContactsPermission(context)
                hasPhone = PermissionManager.hasPhonePermission(context)
                hasNotifications = PermissionManager.hasNotificationAccess(context)
                hasAccessibility = PermissionManager.hasAccessibilityAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasContacts = it }
    val phoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPhone = it }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BackgroundDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "AROHI SETUP & PERMISSION CENTER",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)
            )

            Text(
                text = "To unlock full capabilities and voice task planning, Arohi needs the following permissions enabled.",
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            PermissionSetupRow(
                name = "Microphone (Accompanist)",
                granted = micPermissionState.status.isGranted
            ) {
                micPermissionState.launchPermissionRequest()
            }
            PermissionSetupRow(name = "Contacts", granted = hasContacts) { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) }
            PermissionSetupRow(name = "Phone & Calls", granted = hasPhone) { phoneLauncher.launch(Manifest.permission.CALL_PHONE) }
            PermissionSetupRow(name = "Notification Access", granted = hasNotifications) {
                SettingsNavigator.openSystemSettings(context, "NOTIFICATION_ACCESS")
            }
            PermissionSetupRow(name = "Accessibility Service", granted = hasAccessibility) {
                SettingsNavigator.openSystemSettings(context, "ACCESSIBILITY")
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("FINISH SETUP", color = BackgroundDark, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
fun PermissionSetupRow(name: String, granted: Boolean, onRequest: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, color = TextPrimary, fontWeight = FontWeight.Medium)
        if (granted) {
            Text("VERIFIED ✔", color = Emerald500, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        } else {
            TextButton(
                onClick = onRequest,
                colors = ButtonDefaults.textButtonColors(contentColor = Orange400)
            ) {
                Text("MISSING - GRANT", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
