package com.example.ai.memory

data class RoutineItem(
    val id: Long = 0,
    val title: String,
    val triggerPhrase: String,
    val description: String,
    val actionsJson: String,
    val isEnabled: Boolean = true,
    val iconName: String = "Routine"
)
