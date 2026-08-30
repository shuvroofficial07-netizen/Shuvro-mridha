package com.example.ai.memory

data class TaskLog(
    val id: Long = 0,
    val query: String,
    val response: String,
    val status: String = "SUCCESS",
    val timestamp: Long = System.currentTimeMillis()
)
