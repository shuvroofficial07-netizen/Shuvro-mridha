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
import com.example.ai.ArohiBrain
import com.example.ai.memory.ArohiDatabase
import com.example.ai.memory.MemoryItem
import com.example.ai.memory.RoutineItem
import com.example.routines.ArohiRoutinesEngine
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MemoryRoutinesScreen(brain: ArohiBrain) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { ArohiDatabase.getDatabase(context) }
    val routinesEngine = remember { ArohiRoutinesEngine(context) }

    LaunchedEffect(Unit) {
        routinesEngine.initializeDefaultRoutines()
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Memory, 1 = Routines
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    val memoriesFlow = remember { db.memoryDao().getAllMemoriesFlow() }
    val memories by memoriesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val routinesFlow = remember { db.routineDao().getAllRoutinesFlow() }
    val routines by routinesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val filteredMemories = remember(memories, searchQuery) {
        if (searchQuery.isBlank()) memories
        else memories.filter { it.key.contains(searchQuery, ignoreCase = true) || it.value.contains(searchQuery, ignoreCase = true) }
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
                    text = "MEMORY & ROUTINES VAULT",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonBlue
                )
                Text(
                    text = "Persistent User Context & Automation",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }

            if (selectedTab == 0) {
                IconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .size(36.dp)
                        .background(SurfaceCard, CircleShape)
                        .border(1.dp, NeonBlue, CircleShape)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Memory", tint = NeonBlue, modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tab Selector Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (selectedTab == 0) NeonPurple.copy(alpha = 0.3f) else SurfaceCard, RoundedCornerShape(12.dp))
                    .border(1.dp, if (selectedTab == 0) NeonPurple else BorderMedium, RoundedCornerShape(12.dp))
                    .clickable { selectedTab = 0 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = if (selectedTab == 0) NeonPink else TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "স্মার্ট মেমোরি (${memories.size})",
                        fontSize = 11.sp,
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTab == 0) TextPrimary else TextSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (selectedTab == 1) NeonPurple.copy(alpha = 0.3f) else SurfaceCard, RoundedCornerShape(12.dp))
                    .border(1.dp, if (selectedTab == 1) NeonPurple else BorderMedium, RoundedCornerShape(12.dp))
                    .clickable { selectedTab = 1 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = if (selectedTab == 1) NeonGreen else TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "রুটিন হাব (${routines.size})",
                        fontSize = 11.sp,
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTab == 1) TextPrimary else TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedTab == 0) {
            // Memory Tab Content
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("মেমোরি অনুসন্ধান করুন...", fontSize = 11.sp, color = TextSecondary) },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceCard, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonBlue,
                    unfocusedBorderColor = BorderMedium,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredMemories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("কোনো মেমোরি পাওয়া যায়নি।", fontSize = 12.sp, color = TextSecondary)
                        Text("উপরে + বাটনে ট্যাপ করে নতুন মেমোরি যুক্ত করুন।", fontSize = 10.sp, color = TextSecondary)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredMemories) { memory ->
                        MemoryItemCard(memory, onDelete = {
                            coroutineScope.launch {
                                db.memoryDao().deleteMemoryById(memory.id)
                            }
                        })
                    }
                }
            }
        } else {
            // Routines Tab Content
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(routines) { routine ->
                    RoutineCard(
                        routine = routine,
                        onRun = {
                            coroutineScope.launch {
                                brain.processUserInput("${routine.title} চালাও")
                            }
                        },
                        onToggle = { enabled ->
                            coroutineScope.launch {
                                db.routineDao().setRoutineEnabled(routine.id, enabled)
                            }
                        }
                    )
                }
            }
        }
    }

    // Add Memory Dialog
    if (showAddDialog) {
        var keyInput by remember { mutableStateOf("") }
        var valInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("নতুন মেমোরি যুক্ত করুন", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NeonBlue) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("শিরোনাম / ক্যাটাগরি (e.g. প্রিয় গান)", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                    )
                    OutlinedTextField(
                        value = valInput,
                        onValueChange = { valInput = it },
                        label = { Text("স্মরণীয় তথ্য (e.g. আমি রবীন্দ্রসঙ্গীত ভালোবাসি)", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (valInput.isNotBlank()) {
                            coroutineScope.launch {
                                db.memoryDao().insertMemory(
                                    MemoryItem(
                                        category = "custom",
                                        key = if (keyInput.isNotBlank()) keyInput else "User Fact",
                                        value = valInput
                                    )
                                )
                                showAddDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                ) {
                    Text("সংরক্ষণ করুন", fontSize = 11.sp, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("বাতিল", fontSize = 11.sp, color = TextSecondary)
                }
            },
            containerColor = SurfaceDark
        )
    }
}

@Composable
private fun MemoryItemCard(memory: MemoryItem, onDelete: () -> Unit) {
    val dateFmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(memory.timestamp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(BorderMedium, BorderLight)))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(memory.key, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonBlue)
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(NeonPurple.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(memory.category, fontSize = 8.sp, color = NeonPink)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(memory.value, fontSize = 12.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(dateFmt, fontSize = 9.sp, color = TextSecondary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Rose400, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun RoutineCard(
    routine: RoutineItem,
    onRun: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
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
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(SurfaceDark, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(routine.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(routine.description, fontSize = 10.sp, color = TextSecondary)
                    }
                }

                Switch(
                    checked = routine.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = NeonGreen,
                        uncheckedTrackColor = SurfaceDark
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onRun,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(NeonBlue, NeonPurple)))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = NeonBlue, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("রুটিন এখনই চালান (Run Routine)", fontSize = 10.sp, color = NeonBlue, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
