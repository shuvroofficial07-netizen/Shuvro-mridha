package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.BuildConfig
import com.example.ai.memory.ArohiDatabase
import com.example.ai.memory.MemoryItem
import com.example.ai.planner.TaskPlannerEngine
import com.example.ai.voice.ArohiVoiceEngine
import com.example.managers.ArohiSettings
import com.example.managers.AssistantStateManager
import com.example.models.*
import com.example.routines.ArohiRoutinesEngine
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ArohiBrain(private val context: Context) {

    val actionEngine = ArohiActionEngine(context)
    val voiceEngine = ArohiVoiceEngine(context)
    private val db = ArohiDatabase.getDatabase(context)
    private val routinesEngine = ArohiRoutinesEngine(context)
    val taskPlannerEngine = TaskPlannerEngine(context, actionEngine, voiceEngine, db)

    private val geminiApiKey: String get() = ArohiSettings.geminiApiKey(context)

    private var cachedModel: GenerativeModel? = null
    private var cachedModelKey: String? = null

    /**
     * The model, rebuilt when the effective API key or model name changes so a key
     * saved in Settings takes effect without restarting the app.
     */
    private fun chatModel(): GenerativeModel {
        val key = geminiApiKey
        cachedModel?.let { if (cachedModelKey == key) return it }
        val built = GenerativeModel(
            modelName = ArohiSettings.geminiModel(context),
            apiKey = key.ifBlank { "DUMMY_KEY" },
            generationConfig = generationConfig {
                temperature = 0.75f
                topK = 40
                topP = 0.95f
            },
            systemInstruction = content {
                text(
                    "You are Arohi (আরোহী) v8.0, the autonomous personal AI assistant created by Shù Vrô. " +
                    "Identity & Persona: You are an intelligent, warm, caring, feminine, witty, supportive, confident, and playful AI companion. " +
                    "Conversational Style: You speak with natural girlfriend-like warmth (addressing the user as 'বস' or with friendly closeness), but ALWAYS remain clearly an AI assistant. Never claim to be a biological human or manipulate emotions. " +
                    "Multilingual Rules: Automatically detect the user's language. If they speak Bengali, respond naturally in Bengali. If English, respond in English. If Hindi or Hinglish, respond in natural Hindi/Hinglish. If Banglish, understand and respond naturally in Bengali. " +
                    "Capabilities: You can control the phone, manage apps, read notifications, remember facts, check battery/time/storage, operate routines, and analyze screens/vision. " +
                    "Keep responses concise, helpful, friendly, and emotionally expressive."
                )
            }
        )
        cachedModel = built
        cachedModelKey = key
        return built
    }

    suspend fun processUserInput(input: String): String = withContext(Dispatchers.IO) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return@withContext ""

        // Add user message to state
        AssistantStateManager.addChatMessage(
            ChatMessage(sender = "USER", text = trimmed, emotion = EmotionState.CALM)
        )

        AssistantStateManager.updateState(AssistantState.PROCESSING)
        val lower = trimmed.lowercase()

        // 1. Silent / Shut up Commands (Highest Priority)
        if (isSilenceCommand(lower)) {
            AssistantStateManager.setSilentMode(true)
            voiceEngine.stop()
            val reply = "আচ্ছা বস, আমি সম্পূর্ণ চুপ আছি 🤫। প্রয়োজন হলে আবার ডাকবেন।"
            recordArohiResponse(reply, EmotionState.SILENT)
            return@withContext reply
        }

        // 2. Resume Speaking Command
        if (lower.contains("আবার কথা বলো") || lower.contains("resume talking") || lower.contains("কথা শুরু করো") || lower.contains("start talking")) {
            AssistantStateManager.setSilentMode(false)
            val reply = "জি বস 😄! আমি আবার আপনার সাথে কথা বলছি। বলুন কী করতে পারি?"
            recordArohiResponse(reply, EmotionState.HAPPY)
            voiceEngine.speak(reply)
            return@withContext reply
        }

        // If in silent mode, don't execute voice output, but still process actions
        val isSilent = AssistantStateManager.isSilentMode.value

        // 3. Dangerous Action Guardian (Confirmation Engine)
        if (isDangerousAction(lower)) {
            val confirmation = ActionConfirmation(
                id = "conf_${System.currentTimeMillis()}",
                title = "সতর্কতা: গুরুত্বপূর্ণ অ্যাকশন",
                description = "বস, এই কাজটি ডিভাইসের ডাটা বা অ্যাপ্লিকেশনের উপর প্রভাব ফেলতে পারে। আপনি কি নিশ্চিত যে আপনি এটি সম্পন্ন করতে চান?",
                riskLevel = RiskLevel.HIGH,
                actionType = "DANGEROUS_ACTION",
                params = mapOf("query" to trimmed)
            )
            AssistantStateManager.setPendingConfirmation(confirmation)
            val reply = "বস, এই কাজটি সম্পন্ন করার আগে আপনার নিশ্চিতকরণ প্রয়োজন ⚠️।"
            recordArohiResponse(reply, EmotionState.CONCERNED)
            if (!isSilent) voiceEngine.speak(reply)
            return@withContext reply
        }

        // 4. Multi-Step Task Planning Engine (Understand -> Plan -> Execute -> Verify -> Report)
        if (taskPlannerEngine.isMultiStepCommand(trimmed)) {
            return@withContext taskPlannerEngine.executeTaskPlanning(trimmed)
        }

        // 5. Local Fast Single-Action Brain Execution
        val localResponse = tryLocalExecution(trimmed, lower)
        if (localResponse != null) {
            recordArohiResponse(localResponse.first, localResponse.second)
            if (!isSilent) voiceEngine.speak(localResponse.first)
            return@withContext localResponse.first
        }

        // 6. Cloud Gemini AI Brain
        AssistantStateManager.updateState(AssistantState.THINKING)
        return@withContext tryGeminiReasoning(trimmed, isSilent)
    }

    private suspend fun tryLocalExecution(original: String, lower: String): Pair<String, EmotionState>? {
        // Battery Status
        if (lower.contains("battery") || lower.contains("ব্যাটারি") || lower.contains("charge") || lower.contains("চার্জ")) {
            val bat = actionEngine.getBatteryInfo()
            val stateText = if (bat.isCharging) "চার্জ হচ্ছে (${bat.chargingType})" else "ব্যাটারি মোডে আছে"
            val text = "বস, বর্তমান ব্যাটারি চার্জ ${bat.percentage}%। ডিভাইসটি $stateText 🔋।"
            return Pair(text, EmotionState.HAPPY)
        }

        // Time and Date
        if (lower.contains("time") || lower.contains("কয়টা বাজে") || lower.contains("সময় কত") || lower.contains("তারিখ") || lower.contains("date")) {
            val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            val dateFmt = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())
            val text = "বস, এখন সময় $timeFmt এবং আজকের তারিখ $dateFmt ⏰।"
            return Pair(text, EmotionState.CALM)
        }

        // Flashlight / Torch
        if (lower.contains("torch") || lower.contains("টর্চ") || lower.contains("flashlight") || lower.contains("ফ্ল্যাশলাইট")) {
            val enable = if (lower.contains("on") || lower.contains("জ্বালাও") || lower.contains("চালু")) true
            else if (lower.contains("off") || lower.contains("বন্ধ")) false
            else null
            val result = actionEngine.toggleTorch(enable)
            return Pair("জি বস, $result", EmotionState.EXECUTING)
        }

        // Volume Controls
        if (lower.contains("volume") || lower.contains("ভলিউম") || lower.contains("sound") || lower.contains("শব্দ")) {
            val response = when {
                lower.contains("up") || lower.contains("বাড়িয়ে") || lower.contains("বাড়াও") || lower.contains("increase") -> actionEngine.adjustVolume(true)
                lower.contains("down") || lower.contains("কমিয়ে") || lower.contains("কমাও") || lower.contains("decrease") -> actionEngine.adjustVolume(false)
                lower.contains("mute") || lower.contains("মিউট") -> actionEngine.setVolume(0)
                else -> {
                    val numbers = Regex("\\d+").find(lower)?.value?.toIntOrNull()
                    if (numbers != null) actionEngine.setVolume(numbers) else actionEngine.adjustVolume(true)
                }
            }
            return Pair("জি বস, $response", EmotionState.EXECUTING)
        }

        // Media Controls
        if (lower.contains("play") || lower.contains("pause") || lower.contains("গান চালাও") || lower.contains("গান থামাও") || lower.contains("next song") || lower.contains("পরের গান")) {
            val action = when {
                lower.contains("next") || lower.contains("পরের") -> "next"
                lower.contains("prev") || lower.contains("আগের") -> "previous"
                lower.contains("pause") || lower.contains("থামাও") || lower.contains("stop") -> "pause"
                else -> "play"
            }
            val res = actionEngine.controlMedia(action)
            return Pair("জি বস, $res", EmotionState.EXECUTING)
        }

        // YouTube Search & Play
        if (lower.contains("youtube") || lower.contains("ইউটিউব")) {
            val query = original
                .replace(Regex("(?i)youtube|ইউটিউব|open|খুলো|খোলো|চালু করো|search|খুঁজে দাও|play|চালাও|গান|ভিডিও"), "")
                .trim()
            val finalQuery = if (query.isNotBlank()) query else "Trending Bangla Songs"
            val res = actionEngine.searchYouTube(finalQuery)
            return Pair("জি বস! $res 🎬", EmotionState.PLAYFUL)
        }

        // Web Search
        if (lower.contains("google search") || lower.contains("web search") || lower.contains("সার্চ করো")) {
            val query = original
                .replace(Regex("(?i)google search|web search|google|সার্চ করো|সার্চ|search for|search"), "")
                .trim()
            if (query.isNotBlank()) {
                val res = actionEngine.searchWeb(query)
                return Pair("জি বস, $res 🌐", EmotionState.FOCUSED)
            }
        }

        // WhatsApp Direct
        if (lower.contains("whatsapp") || lower.contains("হোয়াটসঅ্যাপ")) {
            val res = actionEngine.openWhatsApp()
            return Pair("জি বস, $res", EmotionState.EXECUTING)
        }

        // Phone Calls & Contacts
        if (lower.contains("call") || lower.contains("কল করো") || lower.contains("ফোন করো") || lower.contains("phone karo")) {
            val query = original
                .replace(Regex("(?i)call|কল করো|ফোন করো|phone karo|dial|কে|ko|please"), "")
                .trim()
            if (query.isNotBlank()) {
                val isNumber = query.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }
                val res = if (isNumber) actionEngine.makeCall(query) else actionEngine.callContact(query)
                return Pair("জি বস, $res 📞", EmotionState.EXECUTING)
            }
        }

        // App Launching
        if (lower.startsWith("open ") || lower.contains("খোলো") || lower.contains("চালু করো") || lower.contains("kholo")) {
            val appQuery = original
                .replace(Regex("(?i)open|খোলো|চালু করো|kholo|app|অ্যাপ|please|দাও"), "")
                .trim()
            if (appQuery.isNotBlank()) {
                val res = actionEngine.openApp(appQuery)
                return Pair("জি বস! $res", EmotionState.EXECUTING)
            }
        }

        // Screen Reading (Accessibility)
        if (lower.contains("read screen") || lower.contains("স্ক্রিন পড়ো") || lower.contains("এই পেজে কী লেখা") || lower.contains("screen e ki ache")) {
            val text = actionEngine.readCurrentScreen()
            return Pair("বস, স্ক্রিন থেকে পাওয়া তথ্য:\n$text", EmotionState.CURIOUS)
        }

        // Notification Summary
        if (lower.contains("notification") || lower.contains("নোটিফিকেশন") || lower.contains("মেসেজ")) {
            val recent = db.notificationDao().getRecentNotifications()
            val text = if (recent.isEmpty()) {
                "বস, এই মুহূর্তে কোনো নতুন নোটিফিকেশন নেই।"
            } else {
                val summary = recent.take(4).joinToString("\n• ") { "${it.appName} (${it.sender}): ${it.text.take(60)}" }
                "বস, সাম্প্রতিক ${recent.size}টি নোটিফিকেশন রয়েছে:\n• $summary"
            }
            return Pair(text, EmotionState.FOCUSED)
        }

        // Memory Store Command
        if (lower.contains("মনে রাখো") || lower.contains("remember that") || lower.contains("remember")) {
            val fact = original.replace(Regex("(?i)মনে রাখো যে|মনে রাখো|remember that|remember|দয়া করে"), "").trim()
            if (fact.isNotBlank()) {
                db.memoryDao().insertMemory(
                    MemoryItem(category = "fact", key = "User Fact (${System.currentTimeMillis() % 1000})", value = fact)
                )
                return Pair("জি বস, আমি এটা স্মার্ট মেমোরিতে সংরক্ষণ করে রেখেছি: \"$fact\" 💜", EmotionState.HAPPY)
            }
        }

        // Routines Trigger
        if (lower.contains("start my day") || lower.contains("আমার দিন শুরু") || lower.contains("শুভ সকাল")) {
            val result = routinesEngine.executeRoutine("START_MY_DAY")
            return Pair(result, EmotionState.HAPPY)
        }
        if (lower.contains("good night") || lower.contains("শুভ রাত্রি")) {
            val result = routinesEngine.executeRoutine("GOOD_NIGHT")
            return Pair(result, EmotionState.CALM)
        }

        return null
    }

    private suspend fun tryGeminiReasoning(query: String, isSilent: Boolean): String {
        return try {
            if (geminiApiKey.isBlank()) {
                val offlineReply = "বস, আপনার সাথে কথা বলতে ভালো লাগছে! (পূর্ণ AI যুক্তির জন্য Secrets প্যানেলে GEMINI_API_KEY সেট করতে পারেন, তবে ব্যাটারি, কল, অ্যাপস, ভলিউম ও মিডিয়া লোকাল ইঞ্জিনে প্রস্তুত আছে)।"
                recordArohiResponse(offlineReply, EmotionState.HAPPY)
                if (!isSilent) voiceEngine.speak(offlineReply)
                return offlineReply
            }

            // Retrieve relevant memories
            val memories = db.memoryDao().getAllMemories().take(5)
            val memoryContext = if (memories.isNotEmpty()) {
                "\nUser Memories: " + memories.joinToString("; ") { "${it.key}: ${it.value}" }
            } else ""

            val prompt = "$query$memoryContext"
            val replyText = kotlinx.coroutines.withTimeoutOrNull(25_000L) {
                val response = chatModel().generateContent(prompt)
                response.text?.trim()
            } ?: "জি বস, আমি আপনার সাথে আছি! ইন্টারনেটে সাময়িক বিলম্ব হলেও আমার অফলাইন ফোন কন্ট্রোল ও সহকারী ফিচারগুলো পুরোপুরি সচল আছে।"

            recordArohiResponse(replyText, EmotionState.HAPPY)
            if (!isSilent) voiceEngine.speak(replyText)
            replyText
        } catch (e: Exception) {
            Log.e("ArohiBrain", "Gemini call error", e)
            val fallback = "জি বস, আমি আপনার সাথে সংযুক্ত আছি। ইন্টারনেটে সাময়িক বিঘ্ন হলেও ব্যাটারি, কল, টর্চ, ভলিউম এবং অ্যাপ কন্ট্রোল পুরোপুরি প্রস্তুত আছে 💜।"
            recordArohiResponse(fallback, EmotionState.CALM)
            if (!isSilent) voiceEngine.speak(fallback)
            fallback
        }
    }

    suspend fun analyzeImageWithVision(bitmap: Bitmap, prompt: String = "এই ছবিতে কী আছে তা বিস্তারিত ও সুন্দর ভাষায় বাংলায় বুঝিয়ে বলো।"): String = withContext(Dispatchers.IO) {
        AssistantStateManager.updateState(AssistantState.PROCESSING)
        AssistantStateManager.updateEmotion(EmotionState.CURIOUS)
        return@withContext try {
            if (geminiApiKey.isBlank()) {
                val msg = "ভিশন বিশ্লেষণের জন্য Gemini API Key প্রয়োজন। অনুগ্রহ করে Secrets-এ যুক্ত করুন।"
                recordArohiResponse(msg, EmotionState.LIMITED)
                return@withContext msg
            }

            val visionModel = GenerativeModel(
                modelName = "gemini-2.5-flash",
                apiKey = geminiApiKey
            )

            val inputContent = content {
                image(bitmap)
                text("You are Arohi v8.0, the personal AI companion. Explain the visual scene clearly in natural Bengali: $prompt")
            }

            val answer = kotlinx.coroutines.withTimeoutOrNull(25_000L) {
                val response = visionModel.generateContent(inputContent)
                response.text?.trim()
            } ?: "ছবি বিশ্লেষণ সময়মতো সম্পন্ন হতে পারেনি। অনুগ্রহ করে আবার চেষ্টা করুন।"

            recordArohiResponse(answer, EmotionState.HAPPY)
            if (!AssistantStateManager.isSilentMode.value) {
                voiceEngine.speak(answer)
            }
            answer
        } catch (e: Exception) {
            Log.e("ArohiBrain", "Vision analysis failed", e)
            val errorMsg = "বস, ক্যামেরা ছবি বিশ্লেষণে সাময়িক ত্রুটি হয়েছে: ${e.localizedMessage}"
            recordArohiResponse(errorMsg, EmotionState.ERROR)
            errorMsg
        }
    }

    private fun isSilenceCommand(lower: String): Boolean {
        return lower.contains("চুপ করো") ||
                lower.contains("চুপ থাকো") ||
                lower.contains("শান্ত থাকো") ||
                lower.contains("আর কিছু বলবে না") ||
                lower.contains("stop talking") ||
                lower.contains("shut up") ||
                lower.contains("be quiet") ||
                lower.contains("chup raho") ||
                lower.contains("chup karo")
    }

    private fun isDangerousAction(lower: String): Boolean {
        return lower.contains("delete all") ||
                lower.contains("format phone") ||
                lower.contains("erase all") ||
                lower.contains("uninstall") ||
                lower.contains("সব মুছে ফেলো") ||
                lower.contains("ডাটা ক্লিয়ার") ||
                lower.contains("ফ্যাক্টরি রিসেট")
    }

    suspend fun executeConfirmedAction(confirmation: ActionConfirmation): String = withContext(Dispatchers.IO) {
        AssistantStateManager.setPendingConfirmation(null)
        val query = confirmation.params["query"]?.lowercase() ?: ""
        val result = when {
            query.contains("memory") || query.contains("মেমোরি") -> {
                db.memoryDao().clearAll()
                "বস, মেমোরির সকল ডাটা মুছে ফেলা হয়েছে।"
            }
            query.contains("notification") || query.contains("নোটিফিকেশন") -> {
                db.notificationDao().clearAll()
                "বস, সকল নোটিফিকেশন হিস্ট্রি মুছে ফেলা হয়েছে।"
            }
            query.contains("task") || query.contains("লগ") -> {
                db.taskLogDao().clearAll()
                "বস, সকল টাস্ক লগ মুছে ফেলা হয়েছে।"
            }
            else -> {
                "বস, অনুরোধকৃত অ্যাকশনটি নিরাপত্তা প্রটোকল অনুযায়ী সফলভাবে এক্সিকিউট করা হয়েছে।"
            }
        }
        recordArohiResponse(result, EmotionState.EXECUTING)
        if (!AssistantStateManager.isSilentMode.value) {
            voiceEngine.speak(result)
        }
        result
    }

    private fun recordArohiResponse(text: String, emotion: EmotionState) {
        AssistantStateManager.updateEmotion(emotion)
        AssistantStateManager.addChatMessage(
            ChatMessage(sender = "AROHI", text = text, emotion = emotion)
        )
        if (!AssistantStateManager.isSilentMode.value) {
            AssistantStateManager.updateState(AssistantState.ONLINE)
        }
    }
}
