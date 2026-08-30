package com.example.ai.memory

data class CapturedNotification(
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val sender: String,
    val title: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val priority: String = "NORMAL",
    val isRead: Boolean = false
)
