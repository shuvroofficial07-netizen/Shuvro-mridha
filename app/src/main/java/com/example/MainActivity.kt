package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ai.ArohiBrain
import com.example.services.ArohiForegroundService
import com.example.ui.dialogs.CameraVisionDialog
import com.example.ui.screens.*
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        ArohiApp()
      }
    }
  }
}

@Composable
fun ArohiApp() {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  // Shared instance of Arohi Brain
  val brain = remember { ArohiForegroundService.brain ?: ArohiBrain(context) }

  var showSplash by remember { mutableStateOf(true) }
  var currentTabIndex by remember { mutableIntStateOf(0) }
  var showPermissionDialog by remember { mutableStateOf(false) }
  var showVisionDialog by remember { mutableStateOf(false) }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
          val intent = Intent(context, ArohiForegroundService::class.java)
          try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
              context.startForegroundService(intent)
            } else {
              context.startService(intent)
            }
          } catch (e: Exception) {
            Log.e("ArohiMain", "Foreground service start error", e)
          }
        }
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  if (showSplash) {
    SplashScreen(onBootComplete = { showSplash = false })
  } else if (showPermissionDialog) {
    PermissionSetupScreen(onDismiss = { showPermissionDialog = false })
  } else {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = BackgroundDark,
      bottomBar = {
        ArohiBottomNavigation(
          selectedTab = currentTabIndex,
          onTabSelected = { currentTabIndex = it }
        )
      }
    ) { innerPadding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
      ) {
        when (currentTabIndex) {
          0 -> DashboardScreen(
            brain = brain,
            onSetupClick = { showPermissionDialog = true },
            onOpenChat = { currentTabIndex = 1 },
            onOpenVision = { showVisionDialog = true },
            onOpenControlCenter = { currentTabIndex = 3 },
            onOpenRoutines = { currentTabIndex = 2 },
            onOpenSettings = { currentTabIndex = 4 }
          )
          1 -> AssistantChatScreen(
            brain = brain,
            onOpenVision = { showVisionDialog = true }
          )
          2 -> MemoryRoutinesScreen(
            brain = brain
          )
          3 -> ControlCenterScreen(
            brain = brain,
            onOpenVision = { showVisionDialog = true }
          )
          4 -> SettingsDiagnosticsScreen(
            onOpenPermissionSetup = { showPermissionDialog = true }
          )
        }

        if (showVisionDialog) {
          CameraVisionDialog(
            brain = brain,
            onDismiss = { showVisionDialog = false }
          )
        }
      }
    }
  }
}

@Composable
fun ArohiBottomNavigation(
  selectedTab: Int,
  onTabSelected: (Int) -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(Color(0xFF070B14))
      .padding(horizontal = 12.dp, vertical = 6.dp)
      .navigationBarsPadding()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(
          brush = Brush.verticalGradient(
            listOf(Color(0xFF0E1729), Color(0xFF090E1B))
          ),
          shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
        )
        .border(
          width = 1.dp,
          brush = Brush.horizontalGradient(
            listOf(
              Color(0x2600E5FF),
              Color(0x409D4EDD),
              Color(0x2600E5FF)
            )
          ),
          shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
        )
        .padding(horizontal = 6.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically
    ) {
      ArohiNavItem("Home", Icons.Default.Home, selectedTab == 0) { onTabSelected(0) }
      ArohiNavItem("Chat", Icons.Default.ChatBubble, selectedTab == 1) { onTabSelected(1) }
      ArohiNavItem("Routines", Icons.Default.CalendarToday, selectedTab == 2) { onTabSelected(2) }
      ArohiNavItem("Control Center", Icons.Default.GridView, selectedTab == 3) { onTabSelected(3) }
      ArohiNavItem("Settings", Icons.Default.Settings, selectedTab == 4) { onTabSelected(4) }
    }
  }
}

@Composable
private fun ArohiNavItem(
  label: String,
  icon: ImageVector,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  val activeColor = Color.White
  val activePillBg = Color(0xFF1E2D4A)
  val inactiveColor = Color(0xFF64748B)

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .clickable(
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        indication = null
      ) { onClick() }
      .padding(horizontal = 4.dp, vertical = 2.dp)
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .size(width = 44.dp, height = 28.dp)
        .then(
          if (isSelected) {
            Modifier.background(
              color = activePillBg,
              shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
            )
          } else Modifier
        )
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (isSelected) NeonBlue else inactiveColor,
        modifier = Modifier.size(18.dp)
      )
    }
    Spacer(modifier = Modifier.height(2.dp))
    Text(
      text = label,
      fontSize = 9.sp,
      color = if (isSelected) activeColor else inactiveColor,
      fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
    )
  }
}

// Keep Greeting for screenshot test compatibility
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(BackgroundDark),
    contentAlignment = Alignment.Center
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = "Arohi AI Assistant v8.0",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = NeonBlue
      )
      Text(
        text = "by Shù Vrô ($name)",
        fontSize = 12.sp,
        color = TextSecondary
      )
    }
  }
}
