package com.example.ai.memory

data class MemoryItem(
    val id: Long = 0,
    val category: String,
    val key: String,
    val value: String,
    val timestamp: Long = System.currentTimeMillis()
)
