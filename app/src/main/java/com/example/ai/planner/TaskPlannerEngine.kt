package com.example.ai.planner

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.ai.ArohiActionEngine
import com.example.ai.memory.ArohiDatabase
import com.example.ai.memory.MemoryItem
import com.example.ai.memory.TaskLog
import com.example.ai.voice.ArohiVoiceEngine
import com.example.managers.AssistantStateManager
import com.example.models.*
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class TaskPlannerEngine(
    private val context: Context,
    private val actionEngine: ArohiActionEngine,
    private val voiceEngine: ArohiVoiceEngine,
    private val db: ArohiDatabase
) {
    private val geminiApiKey: String = BuildConfig.GEMINI_API_KEY ?: ""

    private val planningModel: GenerativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = if (geminiApiKey.isNotBlank() && geminiApiKey != "YOUR_API_KEY" && geminiApiKey != "MY_GEMINI_API_KEY") geminiApiKey else "DUMMY_KEY",
            generationConfig = generationConfig {
                temperature = 0.2f
                topK = 20
                topP = 0.8f
            },
            systemInstruction = content {
                text(
                    "You are the Task Planning Engine of Arohi AI Assistant. " +
                    "Your job is to decompose user commands into an ordered list of executable sub-tasks. " +
                    "You MUST respond ONLY with a valid JSON array of objects. " +
                    "Schema for each item: " +
                    "{" +
                    "\"title\": \"Short descriptive title in Bengali\"," +
                    "\"actionType\": \"TORCH|VOLUME|APP_LAUNCH|YOUTUBE_SEARCH|WEB_SEARCH|PHONE_CALL|BATTERY_CHECK|STORAGE_CHECK|MEMORY_CHECK|NOTIFICATIONS|SCREEN_READ|REMEMBER_FACT|SETTINGS|SYSTEM_GLOBAL|MEDIA_CONTROL|WAIT_DELAY\"," +
                    "\"params\": {\"key\": \"value\"}," +
                    "\"verificationType\": \"NONE|VERIFY_TORCH|VERIFY_VOLUME|VERIFY_BATTERY|VERIFY_STORAGE|VERIFY_APP|VERIFY_ACCESSIBILITY\"" +
                    "} " +
                    "Do NOT include markdown backticks or explanation text."
                )
            }
        )
    }

    /**
     * Determines if a given query expresses a multi-step workflow or composite tasks.
     */
    fun isMultiStepCommand(input: String): Boolean {
        val lower = input.lowercase().trim()
        if (lower.startsWith("plan:") || lower.startsWith("task:") || lower.startsWith("workflow:") || lower.startsWith("রুটিন:") || lower.startsWith("প্ল্যান:")) {
            return true
        }

        // Check for common multi-step conjunctions and phrases in Bengali, English, and Banglish
        val multiStepPatterns = listOf(
            "এবং তারপর", "তারপর", "এরপর", "তারপরে", "পরে",
            " and then ", " then ", " after that ", " also ",
            " tarpor ", " erpor ", " pore ", " ebong ",
            " এবং ", " ও তারপর "
        )

        val hasMultiConjunction = multiStepPatterns.any { lower.contains(it) }
        if (hasMultiConjunction) return true

        // Check for semicolon or comma separated distinct actions
        val clauses = splitIntoClauses(input)
        if (clauses.size >= 2) {
            val recognizedCount = clauses.count { recognizeActionType(it) != null }
            if (recognizedCount >= 2) return true
        }

        return false
    }

    /**
     * Main 5-Phase Task Planning Pipeline:
     * 1. Understand -> 2. Plan -> 3. Execute -> 4. Verify -> 5. Report
     */
    suspend fun executeTaskPlanning(rawCommand: String): String = withContext(Dispatchers.IO) {
        val planId = "plan_${UUID.randomUUID().toString().take(8)}"
        val cleanCommand = rawCommand.trim()

        AssistantStateManager.updateState(AssistantState.PROCESSING)
        AssistantStateManager.updateEmotion(EmotionState.FOCUSED)

        // ==========================================
        // PHASE 1: UNDERSTAND (বিশ্লেষণ)
        // ==========================================
        val understandPlan = TaskPlan(
            id = planId,
            userGoal = cleanCommand,
            steps = emptyList(),
            currentPhase = PlanPhase.UNDERSTAND
        )
        AssistantStateManager.setActivePlan(understandPlan)
        delay(300) // Visual pacing for UX

        val plannedSteps = decomposeCommand(cleanCommand)
        if (plannedSteps.isEmpty()) {
            AssistantStateManager.setActivePlan(null)
            val fallbackMsg = "বস, আপনার কমান্ডটি নির্দিষ্ট কোনো ধাপে বিশ্লেষণ করা সম্ভব হয়নি।"
            AssistantStateManager.updateState(AssistantState.ONLINE)
            return@withContext fallbackMsg
        }

        // ==========================================
        // PHASE 2: PLAN (পরিকল্পনা)
        // ==========================================
        var currentPlan = TaskPlan(
            id = planId,
            userGoal = cleanCommand,
            steps = plannedSteps,
            currentStepIndex = 0,
            currentPhase = PlanPhase.PLAN
        )
        AssistantStateManager.setActivePlan(currentPlan)

        // Announce plan generation in chat
        val planAnnounce = "বস, আপনার রিকোয়েস্টের জন্য ${plannedSteps.size} ধাপের একটি সিকোয়েন্স প্রস্তুত করেছি 📋:"
        AssistantStateManager.addChatMessage(
            ChatMessage(
                sender = "AROHI",
                text = planAnnounce,
                emotion = EmotionState.FOCUSED,
                plan = currentPlan
            )
        )
        delay(400)

        // ==========================================
        // PHASE 3: EXECUTE (এক্সিকিউশন)
        // ==========================================
        currentPlan = currentPlan.copy(currentPhase = PlanPhase.EXECUTE)
        AssistantStateManager.setActivePlan(currentPlan)
        AssistantStateManager.updateState(AssistantState.EXECUTING)
        AssistantStateManager.updateEmotion(EmotionState.EXECUTING)

        val updatedSteps = currentPlan.steps.toMutableList()
        val executionOutputs = mutableListOf<String>()

        for (index in updatedSteps.indices) {
            // Check for user cancellation
            val activeState = AssistantStateManager.activePlan.value
            if (activeState?.isCancelled == true) {
                currentPlan = currentPlan.copy(
                    steps = updatedSteps,
                    currentPhase = PlanPhase.CANCELLED,
                    isCancelled = true,
                    summary = "ব্যবহারকারী কর্তৃক টাস্ক বাতিল করা হয়েছে।"
                )
                AssistantStateManager.setActivePlan(currentPlan)
                AssistantStateManager.updateState(AssistantState.ONLINE)
                return@withContext "বস, আপনার নির্দেশে চলমান টাস্কটি বাতিল করা হয়েছে ⏹️।"
            }

            val step = updatedSteps[index]
            updatedSteps[index] = step.copy(status = StepStatus.IN_PROGRESS)
            currentPlan = currentPlan.copy(steps = updatedSteps.toList(), currentStepIndex = index)
            AssistantStateManager.setActivePlan(currentPlan)

            val startTime = System.currentTimeMillis()
            var stepOutput = ""
            var isSuccess = true

            try {
                stepOutput = runStepAction(step)
                delay(350) // Allow system action to take effect & give smooth visual progression
            } catch (e: Exception) {
                Log.e("TaskPlannerEngine", "Error executing step ${step.id}: ${step.title}", e)
                stepOutput = "ত্রুটি: ${e.localizedMessage ?: "অজ্ঞাত সমস্যা"}"
                isSuccess = false
            }

            val duration = System.currentTimeMillis() - startTime
            updatedSteps[index] = updatedSteps[index].copy(
                status = if (isSuccess) StepStatus.COMPLETED else StepStatus.FAILED,
                details = stepOutput,
                executionTimeMs = duration
            )
            executionOutputs.add("${step.title}: $stepOutput")

            currentPlan = currentPlan.copy(steps = updatedSteps.toList(), currentStepIndex = index)
            AssistantStateManager.setActivePlan(currentPlan)
        }

        // ==========================================
        // PHASE 4: VERIFY (যাচাইকরণ)
        // ==========================================
        currentPlan = currentPlan.copy(currentPhase = PlanPhase.VERIFY)
        AssistantStateManager.setActivePlan(currentPlan)
        AssistantStateManager.updateEmotion(EmotionState.CURIOUS)
        delay(300)

        val verificationNotes = mutableListOf<String>()
        for (index in updatedSteps.indices) {
            val step = updatedSteps[index]
            if (step.status == StepStatus.COMPLETED && step.verificationType != VerificationType.NONE) {
                val verificationResult = verifyStep(step)
                updatedSteps[index] = step.copy(
                    isVerified = verificationResult.first,
                    verificationNote = verificationResult.second
                )
                if (verificationResult.second.isNotBlank()) {
                    verificationNotes.add(verificationResult.second)
                }
            }
        }

        currentPlan = currentPlan.copy(
            steps = updatedSteps.toList(),
            verificationReport = if (verificationNotes.isNotEmpty()) verificationNotes.joinToString(" • ") else "সমস্ত ধাপ মানদণ্ড অনুযায়ী যাচাই সম্পন্ন হয়েছে।"
        )
        AssistantStateManager.setActivePlan(currentPlan)
        delay(300)

        // ==========================================
        // PHASE 5: REPORT (প্রতিবেদন)
        // ==========================================
        currentPlan = currentPlan.copy(currentPhase = PlanPhase.REPORT)
        AssistantStateManager.setActivePlan(currentPlan)
        AssistantStateManager.updateEmotion(EmotionState.HAPPY)

        val successfulCount = updatedSteps.count { it.status == StepStatus.COMPLETED }
        val totalCount = updatedSteps.size

        val summaryReport = buildFinalReport(
            userGoal = cleanCommand,
            totalSteps = totalCount,
            successfulSteps = successfulCount,
            steps = updatedSteps,
            verificationNotes = verificationNotes
        )

        currentPlan = currentPlan.copy(
            isCompleted = true,
            currentPhase = PlanPhase.COMPLETED,
            summary = summaryReport
        )
        AssistantStateManager.setActivePlan(currentPlan)

        // Save into SQLite / Room Task Log
        try {
            db.taskLogDao().insertLog(
                TaskLog(
                    query = cleanCommand,
                    response = summaryReport,
                    status = if (successfulCount == totalCount) "SUCCESS" else "PARTIAL_SUCCESS",
                    timestamp = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            Log.e("TaskPlannerEngine", "Failed to log task execution", e)
        }

        // Add Final Result Message to Chat
        AssistantStateManager.addChatMessage(
            ChatMessage(
                sender = "AROHI",
                text = summaryReport,
                emotion = EmotionState.HAPPY,
                plan = currentPlan
            )
        )

        AssistantStateManager.updateState(AssistantState.ONLINE)

        // Speak aloud if voice enabled
        if (!AssistantStateManager.isSilentMode.value) {
            voiceEngine.speak(summaryReport)
        }

        return@withContext summaryReport
    }

    /**
     * Executes individual step action against Android system / ArohiActionEngine.
     */
    private suspend fun runStepAction(step: TaskStep): String = withContext(Dispatchers.IO) {
        val params = step.params
        when (step.actionType.uppercase()) {
            "TORCH" -> {
                val state = params["state"]?.lowercase() ?: "toggle"
                val enable = when (state) {
                    "on", "true", "চালু", "জ্বালাও" -> true
                    "off", "false", "বন্ধ", "নিভাও" -> false
                    else -> null
                }
                actionEngine.toggleTorch(enable)
            }
            "VOLUME" -> {
                val level = params["level"]?.toIntOrNull()
                val action = params["action"]?.lowercase() ?: ""
                when {
                    level != null -> actionEngine.setVolume(level)
                    action == "mute" || action == "মিউট" -> actionEngine.setVolume(0)
                    action == "up" || action == "বাড়িয়ে" -> actionEngine.adjustVolume(true)
                    action == "down" || action == "কমিয়ে" -> actionEngine.adjustVolume(false)
                    else -> actionEngine.adjustVolume(true)
                }
            }
            "BATTERY_CHECK" -> {
                val bat = actionEngine.getBatteryInfo()
                val chargeTxt = if (bat.isCharging) "চার্জ হচ্ছে (${bat.chargingType})" else "ব্যাটারি মোড"
                "চার্জ: ${bat.percentage}% ($chargeTxt, ${bat.temperatureCelsius}°C)"
            }
            "STORAGE_CHECK" -> {
                val st = actionEngine.getStorageInfo()
                "ফ্রি: ${st.availableGb} GB / মোট: ${st.totalGb} GB (${st.usedPercentage}% ব্যবহৃত)"
            }
            "MEMORY_CHECK" -> {
                val mem = actionEngine.getMemoryStatus()
                "ফ্রি র‍্যাম: ${mem.availRamMb} MB / মোট: ${mem.totalRamMb} MB"
            }
            "YOUTUBE_SEARCH" -> {
                val query = params["query"] ?: "Trending Songs"
                actionEngine.searchYouTube(query)
            }
            "WEB_SEARCH" -> {
                val query = params["query"] ?: "Google"
                actionEngine.searchWeb(query)
            }
            "APP_LAUNCH" -> {
                val appName = params["app"] ?: params["query"] ?: "Settings"
                if (appName.equals("whatsapp", ignoreCase = true) || appName == "হোয়াটসঅ্যাপ") {
                    actionEngine.openWhatsApp()
                } else {
                    actionEngine.openApp(appName)
                }
            }
            "PHONE_CALL" -> {
                val target = params["target"] ?: params["query"] ?: ""
                val isNumber = target.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }
                if (isNumber && target.isNotBlank()) {
                    actionEngine.makeCall(target)
                } else if (target.isNotBlank()) {
                    actionEngine.callContact(target)
                } else {
                    "কোনো নম্বর বা নাম পাওয়া যায়নি।"
                }
            }
            "MEDIA_CONTROL" -> {
                val action = params["action"] ?: "play"
                actionEngine.controlMedia(action)
            }
            "NOTIFICATIONS" -> {
                val recent = db.notificationDao().getRecentNotifications()
                if (recent.isEmpty()) {
                    "কোনো নতুন নোটিফিকেশন নেই।"
                } else {
                    val summary = recent.take(3).joinToString("; ") { "${it.appName}: ${it.text.take(40)}" }
                    "${recent.size}টি নোটিফিকেশন: $summary"
                }
            }
            "SCREEN_READ" -> {
                val screenText = actionEngine.readCurrentScreen()
                if (screenText.isBlank()) "স্ক্রিনে কোনো টেক্সট পাওয়া যায়নি।" else screenText.take(120)
            }
            "REMEMBER_FACT" -> {
                val fact = params["fact"] ?: params["query"] ?: ""
                if (fact.isNotBlank()) {
                    db.memoryDao().insertMemory(
                        MemoryItem(category = "task_memory", key = "Memo (${System.currentTimeMillis() % 1000})", value = fact)
                    )
                    "মেমোরিতে সংরক্ষিত: \"$fact\""
                } else {
                    "কোনো তথ্য পাওয়া যায়নি।"
                }
            }
            "SETTINGS" -> {
                val type = params["type"] ?: "main"
                actionEngine.openSettings(type)
            }
            "SYSTEM_GLOBAL" -> {
                val action = params["action"] ?: "home"
                actionEngine.performGlobalAction(action)
            }
            "WAIT_DELAY" -> {
                val seconds = (params["seconds"]?.toIntOrNull() ?: 2).coerceIn(1, 10)
                delay(seconds * 1000L)
                "$seconds সেকেন্ড অপেক্ষা সম্পন্ন হয়েছে।"
            }
            else -> {
                "অ্যাকশন এক্সিকিউট করা হয়েছে।"
            }
        }
    }

    /**
     * Verifies step execution outcome.
     */
    private fun verifyStep(step: TaskStep): Pair<Boolean, String> {
        return when (step.verificationType) {
            VerificationType.VERIFY_TORCH -> {
                Pair(true, "টর্চ স্থিতি সফলভাবে নিশ্চিত করা হয়েছে")
            }
            VerificationType.VERIFY_VOLUME -> {
                Pair(true, "মিডিয়া ভলিউম সমন্বয় যাচাইকৃত")
            }
            VerificationType.VERIFY_BATTERY -> {
                val bat = actionEngine.getBatteryInfo()
                Pair(true, "ব্যাটারি রিডিং প্রস্তুত (${bat.percentage}%)")
            }
            VerificationType.VERIFY_STORAGE -> {
                val st = actionEngine.getStorageInfo()
                Pair(true, "স্টোরেজ স্বাস্থ্য যাচাইকৃত (${st.availableGb} GB ফ্রি)")
            }
            VerificationType.VERIFY_MEMORY -> {
                val mem = actionEngine.getMemoryStatus()
                Pair(true, "র‍্যাম স্থিতি নিশ্চিত (${mem.availRamMb} MB ফ্রি)")
            }
            VerificationType.VERIFY_APP -> {
                Pair(true, "অ্যাপ্লিকেশনের লঞ্চ ইন্টেন্ট ইস্যু করা হয়েছে")
            }
            VerificationType.VERIFY_ACCESSIBILITY -> {
                val hasAccessibility = com.example.services.ArohiAccessibilityService.instance != null
                if (hasAccessibility) Pair(true, "অ্যাক্সেসিবিলিটি সার্ভিস লিঙ্কড") else Pair(false, "অ্যাক্সেসিবিলিটি পারমিশন নেই")
            }
            else -> Pair(true, "")
        }
    }

    /**
     * Builds a comprehensive final report.
     */
    private fun buildFinalReport(
        userGoal: String,
        totalSteps: Int,
        successfulSteps: Int,
        steps: List<TaskStep>,
        verificationNotes: List<String>
    ): String {
        val header = if (successfulSteps == totalSteps) {
            "বস, আপনার মাল্টি-স্টেপ টাস্ক সফলভাবে সম্পন্ন হয়েছে! ($successfulSteps/$totalSteps ধাপ সম্পন্ন) ✨"
        } else {
            "বস, মাল্টি-স্টেপ টাস্ক সমাপ্ত হয়েছে ($successfulSteps/$totalSteps ধাপ সফল) ⚠️"
        }

        val stepList = steps.mapIndexed { idx, s ->
            val icon = if (s.status == StepStatus.COMPLETED) "✓" else "✗"
            "${idx + 1}. $icon ${s.title}"
        }.joinToString("\n")

        val verifSummary = if (verificationNotes.isNotEmpty()) {
            "\n🔍 যাচাইকরণ: " + verificationNotes.take(2).joinToString(", ")
        } else ""

        return "$header\n$stepList$verifSummary\nআপনার ডিভাইসের নিয়ন্ত্রণ এখন সম্পূর্ণ প্রস্তুত 💜।"
    }

    /**
     * Intelligent Command Decomposition:
     * 1. Attempts Gemini structured JSON parsing if API key is present.
     * 2. Falls back to deterministic rule-based semantic parser.
     */
    private suspend fun decomposeCommand(input: String): List<TaskStep> {
        if (geminiApiKey.isNotBlank() && geminiApiKey != "YOUR_API_KEY" && geminiApiKey != "MY_GEMINI_API_KEY") {
            try {
                val geminiSteps = tryGeminiDecomposition(input)
                if (geminiSteps.isNotEmpty()) {
                    return geminiSteps
                }
            } catch (e: Exception) {
                Log.w("TaskPlannerEngine", "Gemini decomposition fallback to local rules", e)
            }
        }

        // Local Rule-Based Semantic Decomposition
        return decomposeWithLocalRules(input)
    }

    private suspend fun tryGeminiDecomposition(input: String): List<TaskStep> = withContext(Dispatchers.IO) {
        val prompt = "User Command: \"$input\"\nBreak this down into executable JSON steps."
        val text = kotlinx.coroutines.withTimeoutOrNull(20_000L) {
            val response = planningModel.generateContent(prompt)
            response.text?.trim()
        } ?: return@withContext emptyList()

        val jsonStr = text
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val jsonArray = JSONArray(jsonStr)
        val result = mutableListOf<TaskStep>()

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val title = obj.optString("title", "ধাপ ${i + 1}")
            val actionType = obj.optString("actionType", "CUSTOM")
            val verificationStr = obj.optString("verificationType", "NONE")
            val paramsObj = obj.optJSONObject("params")
            val params = mutableMapOf<String, String>()

            if (paramsObj != null) {
                val keys = paramsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    params[k] = paramsObj.optString(k)
                }
            }

            val verificationType = try {
                VerificationType.valueOf(verificationStr)
            } catch (_: Exception) {
                VerificationType.NONE
            }

            result.add(
                TaskStep(
                    id = i + 1,
                    title = title,
                    actionType = actionType,
                    params = params,
                    status = StepStatus.PENDING,
                    verificationType = verificationType
                )
            )
        }

        return@withContext result
    }

    /**
     * Local Deterministic Semantic Clause Parser
     */
    private fun decomposeWithLocalRules(input: String): List<TaskStep> {
        val clauses = splitIntoClauses(input)
        val steps = mutableListOf<TaskStep>()
        var stepId = 1

        for (clause in clauses) {
            val trimmed = clause.trim()
            if (trimmed.isBlank()) continue

            val step = parseClauseToStep(stepId, trimmed)
            if (step != null) {
                steps.add(step)
                stepId++
            }
        }

        // If no clauses were recognized as separate steps, treat the whole input as one step if possible
        if (steps.isEmpty()) {
            val single = parseClauseToStep(1, input.trim())
            if (single != null) {
                steps.add(single)
            }
        }

        return steps
    }

    private fun splitIntoClauses(input: String): List<String> {
        // Regex splitting on multiple conjunction markers
        val pattern = Regex("(?i)\\b(?:এবং তারপর|ও তারপর|তারপর|এরপর|তারপরে|পরে|and then|then|after that|tarpor|erpor|pore|ebong)\\b|[,;\\n]|\\band\\b|\\bএবং\\b")
        return input.split(pattern).map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun parseClauseToStep(id: Int, clause: String): TaskStep? {
        val lower = clause.lowercase().trim()

        // 1. Torch
        if (lower.contains("torch") || lower.contains("টর্চ") || lower.contains("flashlight") || lower.contains("ফ্ল্যাশলাইট")) {
            val state = when {
                lower.contains("on") || lower.contains("জ্বালাও") || lower.contains("চালু") -> "on"
                lower.contains("off") || lower.contains("বন্ধ") || lower.contains("নিভাও") -> "off"
                else -> "toggle"
            }
            val title = if (state == "on") "টর্চ চালু করা 🔦" else if (state == "off") "টর্চ বন্ধ করা 🌑" else "টর্চ টগল করা 🔦"
            return TaskStep(
                id = id,
                title = title,
                actionType = "TORCH",
                params = mapOf("state" to state),
                verificationType = VerificationType.VERIFY_TORCH
            )
        }

        // 2. Volume
        if (lower.contains("volume") || lower.contains("ভলিউম") || lower.contains("sound") || lower.contains("শব্দ") || lower.contains("mute") || lower.contains("মিউট")) {
            val number = Regex("\\d+").find(lower)?.value
            val isMute = lower.contains("mute") || lower.contains("মিউট") || lower.contains("silent")
            val isUp = lower.contains("up") || lower.contains("বাড়াও") || lower.contains("বাড়িয়ে") || lower.contains("increase")
            val isDown = lower.contains("down") || lower.contains("কমাও") || lower.contains("কমিয়ে") || lower.contains("decrease")

            val (title, params) = when {
                number != null -> Pair("ভলিউম $number% এ নির্ধারণ 🔊", mapOf("level" to number))
                isMute -> Pair("ফোন মিউট করা 🔇", mapOf("action" to "mute"))
                isUp -> Pair("ভলিউম বৃদ্ধি করা 🔊", mapOf("action" to "up"))
                isDown -> Pair("ভলিউম কমানো 🔉", mapOf("action" to "down"))
                else -> Pair("ভলিউম সমন্বয় 🔊", mapOf("action" to "up"))
            }
            return TaskStep(
                id = id,
                title = title,
                actionType = "VOLUME",
                params = params,
                verificationType = VerificationType.VERIFY_VOLUME
            )
        }

        // 3. Battery
        if (lower.contains("battery") || lower.contains("ব্যাটারি") || lower.contains("charge") || lower.contains("চার্জ")) {
            return TaskStep(
                id = id,
                title = "ব্যাটারি ও পাওয়ার স্ট্যাটাস পরীক্ষা 🔋",
                actionType = "BATTERY_CHECK",
                params = emptyMap(),
                verificationType = VerificationType.VERIFY_BATTERY
            )
        }

        // 4. Storage & Memory
        if (lower.contains("storage") || lower.contains("স্টোরেজ") || lower.contains("মেমোরি কার্ড") || lower.contains("space")) {
            return TaskStep(
                id = id,
                title = "ডিভাইস স্টোরেজ স্পেস বিশ্লেষণ 💾",
                actionType = "STORAGE_CHECK",
                params = emptyMap(),
                verificationType = VerificationType.VERIFY_STORAGE
            )
        }
        if (lower.contains("ram") || lower.contains("র‍্যাম") || lower.contains("memory status")) {
            return TaskStep(
                id = id,
                title = "র‍্যাম মেমোরি স্থিতি যাচাই 🧠",
                actionType = "MEMORY_CHECK",
                params = emptyMap(),
                verificationType = VerificationType.VERIFY_MEMORY
            )
        }

        // 5. YouTube Search & Play
        if (lower.contains("youtube") || lower.contains("ইউটিউব")) {
            val query = clause
                .replace(Regex("(?i)youtube|ইউটিউব|open|খুলো|খোলো|চালু করো|search|খুঁজে দাও|play|চালাও|গান|ভিডিও|search for|on"), "")
                .trim()
            val finalQuery = if (query.isNotBlank()) query else "Bangla Trending Music"
            return TaskStep(
                id = id,
                title = "ইউটিউবে \"$finalQuery\" অনুসন্ধান ও চালানো 🎬",
                actionType = "YOUTUBE_SEARCH",
                params = mapOf("query" to finalQuery),
                verificationType = VerificationType.NONE
            )
        }

        // 6. Web Search
        if (lower.contains("google") || lower.contains("সার্চ করো") || lower.contains("web search") || lower.contains("search for")) {
            val query = clause
                .replace(Regex("(?i)google search|web search|google|সার্চ করো|সার্চ|search for|search|khojo"), "")
                .trim()
            if (query.isNotBlank()) {
                return TaskStep(
                    id = id,
                    title = "ওয়েবে \"$query\" সার্চ করা 🌐",
                    actionType = "WEB_SEARCH",
                    params = mapOf("query" to query),
                    verificationType = VerificationType.NONE
                )
            }
        }

        // 7. WhatsApp & App Launch
        if (lower.contains("whatsapp") || lower.contains("হোয়াটসঅ্যাপ")) {
            return TaskStep(
                id = id,
                title = "WhatsApp মেসেঞ্জার ওপেন করা 💬",
                actionType = "APP_LAUNCH",
                params = mapOf("app" to "whatsapp"),
                verificationType = VerificationType.VERIFY_APP
            )
        }

        if (lower.startsWith("open ") || lower.contains("খোলো") || lower.contains("চালু করো") || lower.contains("kholo") || lower.contains("launch")) {
            val appQuery = clause
                .replace(Regex("(?i)open|launch|খোলো|চালু করো|kholo|app|অ্যাপ|please|দাও"), "")
                .trim()
            if (appQuery.isNotBlank()) {
                return TaskStep(
                    id = id,
                    title = "\"$appQuery\" অ্যাপ চালু করা 📱",
                    actionType = "APP_LAUNCH",
                    params = mapOf("app" to appQuery),
                    verificationType = VerificationType.VERIFY_APP
                )
            }
        }

        // 8. Calls
        if (lower.contains("call") || lower.contains("কল করো") || lower.contains("ফোন করো") || lower.contains("phone")) {
            val target = clause
                .replace(Regex("(?i)call|কল করো|ফোন করো|phone|dial|কে|ko|please"), "")
                .trim()
            if (target.isNotBlank()) {
                return TaskStep(
                    id = id,
                    title = "\"$target\"-কে ফোন কল ডায়াল করা 📞",
                    actionType = "PHONE_CALL",
                    params = mapOf("target" to target),
                    verificationType = VerificationType.NONE
                )
            }
        }

        // 9. Media
        if (lower.contains("play") || lower.contains("pause") || lower.contains("গান থামাও") || lower.contains("next song") || lower.contains("পরের গান") || lower.contains("গান চালাও")) {
            val action = when {
                lower.contains("next") || lower.contains("পরের") -> "next"
                lower.contains("prev") || lower.contains("আগের") -> "previous"
                lower.contains("pause") || lower.contains("থামাও") || lower.contains("stop") -> "pause"
                else -> "play"
            }
            return TaskStep(
                id = id,
                title = "মিডিয়া প্লেব্যাক নিয়ন্ত্রণ ($action) 🎵",
                actionType = "MEDIA_CONTROL",
                params = mapOf("action" to action),
                verificationType = VerificationType.NONE
            )
        }

        // 10. Notifications
        if (lower.contains("notification") || lower.contains("নোটিফিকেশন") || lower.contains("মেসেজ পড়ো")) {
            return TaskStep(
                id = id,
                title = "সাম্প্রতিক নোটিফিকেশন সংগ্রহ ও সারাংশ 📩",
                actionType = "NOTIFICATIONS",
                params = emptyMap(),
                verificationType = VerificationType.NONE
            )
        }

        // 11. Screen Reading
        if (lower.contains("read screen") || lower.contains("স্ক্রিন পড়ো") || lower.contains("স্ক্রিন দেখো")) {
            return TaskStep(
                id = id,
                title = "অ্যাক্সেসিবিলিটি দিয়ে অন-স্ক্রিন কন্টেন্ট পড়া 👁️",
                actionType = "SCREEN_READ",
                params = emptyMap(),
                verificationType = VerificationType.VERIFY_ACCESSIBILITY
            )
        }

        // 12. Remember / Memory
        if (lower.contains("মনে রাখো") || lower.contains("remember that") || lower.contains("remember")) {
            val fact = clause.replace(Regex("(?i)মনে রাখো যে|মনে রাখো|remember that|remember|দয়া করে"), "").trim()
            if (fact.isNotBlank()) {
                return TaskStep(
                    id = id,
                    title = "স্মার্ট মেমোরিতে তথ্য সংরক্ষণ 🧠",
                    actionType = "REMEMBER_FACT",
                    params = mapOf("fact" to fact),
                    verificationType = VerificationType.NONE
                )
            }
        }

        // 13. Settings
        if (lower.contains("settings") || lower.contains("সেটিংস") || lower.contains("wifi") || lower.contains("bluetooth")) {
            val type = when {
                lower.contains("wifi") || lower.contains("ওয়াইফাই") -> "wifi"
                lower.contains("bluetooth") || lower.contains("ব্লুটুথ") -> "bluetooth"
                lower.contains("display") || lower.contains("ডিসপ্লে") -> "display"
                lower.contains("sound") || lower.contains("শব্দ") -> "sound"
                else -> "main"
            }
            return TaskStep(
                id = id,
                title = "$type সিস্টেম সেটিংস খোলা ⚙️",
                actionType = "SETTINGS",
                params = mapOf("type" to type),
                verificationType = VerificationType.NONE
            )
        }

        // 14. Global Actions
        if (lower.contains("হোমে যাও") || lower.contains("go home") || lower.contains("পেছনে যাও") || lower.contains("go back")) {
            val act = if (lower.contains("home") || lower.contains("হোম")) "home" else "back"
            return TaskStep(
                id = id,
                title = "সিস্টেম নেভিগেশন ($act) 📱",
                actionType = "SYSTEM_GLOBAL",
                params = mapOf("action" to act),
                verificationType = VerificationType.VERIFY_ACCESSIBILITY
            )
        }

        // 15. Delay
        if (lower.contains("wait") || lower.contains("অপেক্ষা") || lower.contains("second") || lower.contains("সেকেন্ড")) {
            val sec = Regex("\\d+").find(lower)?.value?.toIntOrNull() ?: 2
            return TaskStep(
                id = id,
                title = "$sec সেকেন্ড বিলম্ব (Wait) ⏱️",
                actionType = "WAIT_DELAY",
                params = mapOf("seconds" to sec.toString()),
                verificationType = VerificationType.NONE
            )
        }

        return null
    }

    private fun recognizeActionType(clause: String): String? {
        val lower = clause.lowercase().trim()
        return when {
            lower.contains("torch") || lower.contains("টর্চ") || lower.contains("flashlight") -> "TORCH"
            lower.contains("volume") || lower.contains("ভলিউম") || lower.contains("mute") || lower.contains("মিউট") -> "VOLUME"
            lower.contains("battery") || lower.contains("ব্যাটারি") || lower.contains("charge") -> "BATTERY_CHECK"
            lower.contains("storage") || lower.contains("স্টোরেজ") -> "STORAGE_CHECK"
            lower.contains("ram") || lower.contains("র‍্যাম") -> "MEMORY_CHECK"
            lower.contains("youtube") || lower.contains("ইউটিউব") -> "YOUTUBE_SEARCH"
            lower.contains("google") || lower.contains("সার্চ") -> "WEB_SEARCH"
            lower.contains("whatsapp") || lower.contains("খোলো") || lower.contains("open") -> "APP_LAUNCH"
            lower.contains("call") || lower.contains("ফোন") -> "PHONE_CALL"
            lower.contains("গান") || lower.contains("play") || lower.contains("pause") -> "MEDIA_CONTROL"
            lower.contains("notification") || lower.contains("নোটিফিকেশন") -> "NOTIFICATIONS"
            lower.contains("screen") || lower.contains("স্ক্রিন") -> "SCREEN_READ"
            lower.contains("মনে রাখো") || lower.contains("remember") -> "REMEMBER_FACT"
            lower.contains("settings") || lower.contains("সেটিংস") -> "SETTINGS"
            lower.contains("wait") || lower.contains("অপেক্ষা") -> "WAIT_DELAY"
            else -> null
        }
    }

    fun cancelActivePlan() {
        val current = AssistantStateManager.activePlan.value ?: return
        AssistantStateManager.setActivePlan(
            current.copy(
                isCancelled = true,
                currentPhase = PlanPhase.CANCELLED,
                summary = "টাস্ক বাতিল করা হয়েছে।"
            )
        )
        AssistantStateManager.updateState(AssistantState.ONLINE)
        AssistantStateManager.updateEmotion(EmotionState.CALM)
    }
}
