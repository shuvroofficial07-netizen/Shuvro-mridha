package com.example.models

enum class AssistantState {
    ONLINE,
    LISTENING,
    THINKING,
    PROCESSING,
    SPEAKING,
    EXECUTING,
    SILENT,
    PAUSED,
    LIMITED,
    SERVICE_STOPPED,
    PERMISSION_REQUIRED,
    ERROR
}

enum class EmotionState(val labelBn: String, val emoji: String) {
    CALM("শান্ত", "🌸"),
    HAPPY("খুশি", "😊"),
    PLAYFUL("দুষ্টুমি", "😄"),
    CURIOUS("কৌতূহলী", "🧐"),
    FOCUSED("মনোযোগী", "🎯"),
    THINKING("ভাবছি", "🤔"),
    CONFUSED("অস্পষ্ট", "😅"),
    CONCERNED("সতর্ক", "😟"),
    EXCITED("উচ্ছ্বসিত", "✨"),
    SAD("বিষণ্ণ", "🥺"),
    ANNOYED("বিরক্ত", "😑"),
    SPEAKING("কথা বলছি", "🗣️"),
    LISTENING("শুনছি", "👂"),
    EXECUTING("কাজ করছি", "⚡"),
    SILENT("নীরব", "🤫"),
    LIMITED("সীমাবদ্ধ", "🔒"),
    ERROR("সমস্যা", "⚠️")
}

enum class ProactiveSensitivity {
    OFF,
    LOW,
    NORMAL,
    HIGH
}

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH
}

enum class StepStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    SKIPPED
}

enum class PlanPhase(val labelBn: String, val labelEn: String, val emoji: String) {
    UNDERSTAND("বিশ্লেষণ", "Understand", "🧠"),
    PLAN("পরিকল্পনা", "Plan", "📋"),
    EXECUTE("এক্সিকিউশন", "Execute", "⚡"),
    VERIFY("যাচাইকরণ", "Verify", "🔍"),
    REPORT("প্রতিবেদন", "Report", "📊"),
    COMPLETED("সম্পন্ন", "Completed", "✅"),
    CANCELLED("বাতিলকৃত", "Cancelled", "⏹️"),
    FAILED("ব্যর্থ", "Failed", "❌")
}

enum class VerificationType {
    NONE,
    VERIFY_TORCH,
    VERIFY_VOLUME,
    VERIFY_BATTERY,
    VERIFY_STORAGE,
    VERIFY_MEMORY,
    VERIFY_APP,
    VERIFY_NOTIFICATIONS,
    VERIFY_ACCESSIBILITY,
    VERIFY_MEDIA
}

data class TaskStep(
    val id: Int,
    val title: String,
    val actionType: String = "",
    val params: Map<String, String> = emptyMap(),
    val status: StepStatus = StepStatus.PENDING,
    val details: String = "",
    val verificationType: VerificationType = VerificationType.NONE,
    val verificationNote: String = "",
    val isVerified: Boolean = false,
    val executionTimeMs: Long = 0L
)

data class TaskPlan(
    val id: String,
    val userGoal: String,
    val steps: List<TaskStep>,
    val currentStepIndex: Int = 0,
    val currentPhase: PlanPhase = PlanPhase.COMPLETED,
    val isCompleted: Boolean = false,
    val isCancelled: Boolean = false,
    val isPaused: Boolean = false,
    val summary: String = "",
    val verificationReport: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class ActionConfirmation(
    val id: String,
    val title: String,
    val description: String,
    val riskLevel: RiskLevel,
    val actionType: String,
    val params: Map<String, String> = emptyMap()
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "USER" or "AROHI"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val emotion: EmotionState = EmotionState.CALM,
    val plan: TaskPlan? = null
)
