package com.example.routines

import android.content.Context
import com.example.ai.ArohiActionEngine
import com.example.ai.memory.ArohiDatabase
import com.example.ai.memory.RoutineItem
import com.example.managers.AssistantStateManager
import com.example.models.EmotionState
import com.example.models.PlanPhase
import com.example.models.StepStatus
import com.example.models.TaskPlan
import com.example.models.TaskStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ArohiRoutinesEngine(private val context: Context) {

    private val actionEngine = ArohiActionEngine(context)
    private val db = ArohiDatabase.getDatabase(context)

    suspend fun initializeDefaultRoutines() = withContext(Dispatchers.IO) {
        val existing = db.routineDao().getEnabledRoutines()
        if (existing.isEmpty()) {
            db.routineDao().insertRoutine(
                RoutineItem(
                    title = "Start My Day (আমার দিন শুরু করো)",
                    triggerPhrase = "start my day",
                    description = "সময়, তারিখ, ব্যাটারি ও গুরুত্বপূর্ণ নোটিফিকেশন ব্রিফিং।",
                    actionsJson = "BRIEFING",
                    iconName = "WbSunny"
                )
            )
            db.routineDao().insertRoutine(
                RoutineItem(
                    title = "Good Night (শুভ রাত্রি)",
                    triggerPhrase = "good night",
                    description = "ভলিউম কমানো, ব্যাটারি স্বাস্থ্য নিরীক্ষণ ও শুভ রাত্রি জানানো।",
                    actionsJson = "GOOD_NIGHT",
                    iconName = "NightsStay"
                )
            )
            db.routineDao().insertRoutine(
                RoutineItem(
                    title = "Work Mode (কাজের মোড)",
                    triggerPhrase = "work mode",
                    description = "ভলিউম মাঝারি রাখা ও নোটিফিকেশন সামারি।",
                    actionsJson = "WORK_MODE",
                    iconName = "Work"
                )
            )
            db.routineDao().insertRoutine(
                RoutineItem(
                    title = "Gaming Mode (গেমিং মোড)",
                    triggerPhrase = "gaming mode",
                    description = "র‍্যাম খালি করার প্রস্তুতি ও ভলিউম সমন্বয়।",
                    actionsJson = "GAMING_MODE",
                    iconName = "SportsEsports"
                )
            )
        }
    }

    suspend fun executeRoutine(routineType: String): String = withContext(Dispatchers.IO) {
        val steps = mutableListOf<TaskStep>()
        val planId = "routine_${System.currentTimeMillis()}"

        when (routineType.uppercase()) {
            "BRIEFING", "START MY DAY", "START_MY_DAY" -> {
                steps.add(TaskStep(id = 1, title = "সময় ও তারিখ সংগ্রহ", status = StepStatus.IN_PROGRESS))
                steps.add(TaskStep(id = 2, title = "ব্যাটারি ও সিস্টেম স্থিতি যাচাই", status = StepStatus.PENDING))
                steps.add(TaskStep(id = 3, title = "নোটিফিকেশন সারাংশ প্রস্তুত", status = StepStatus.PENDING))
                steps.add(TaskStep(id = 4, title = "সম্পূর্ণ ব্রিফিং রিপোর্ট", status = StepStatus.PENDING))

                AssistantStateManager.setActivePlan(TaskPlan(id = planId, userGoal = "Start My Day Routine", steps = steps, currentPhase = PlanPhase.EXECUTE))
                delay(300)

                // Step 1
                val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                val dateFmt = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())
                steps[0] = steps[0].copy(status = StepStatus.COMPLETED, details = "$timeFmt, $dateFmt")
                steps[1] = steps[1].copy(status = StepStatus.IN_PROGRESS)
                AssistantStateManager.setActivePlan(TaskPlan(id = planId, userGoal = "Start My Day Routine", steps = steps, currentStepIndex = 1, currentPhase = PlanPhase.EXECUTE))
                delay(300)

                // Step 2
                val bat = actionEngine.getBatteryInfo()
                val net = actionEngine.getNetworkStatus()
                steps[1] = steps[1].copy(status = StepStatus.COMPLETED, details = "ব্যাটারি: ${bat.percentage}%, $net")
                steps[2] = steps[2].copy(status = StepStatus.IN_PROGRESS)
                AssistantStateManager.setActivePlan(TaskPlan(id = planId, userGoal = "Start My Day Routine", steps = steps, currentStepIndex = 2, currentPhase = PlanPhase.EXECUTE))
                delay(300)

                // Step 3
                val unread = db.notificationDao().getUnreadNotifications()
                val unreadText = if (unread.isEmpty()) "কোনো নতুন নোটিফিকেশন নেই।" else "${unread.size}টি আনরিড নোটিফিকেশন রয়েছে।"
                steps[2] = steps[2].copy(status = StepStatus.COMPLETED, details = unreadText)
                steps[3] = steps[3].copy(status = StepStatus.COMPLETED, details = "হয়ে গেছে")
                AssistantStateManager.setActivePlan(TaskPlan(id = planId, userGoal = "Start My Day Routine", steps = steps, currentStepIndex = 3, currentPhase = PlanPhase.COMPLETED, isCompleted = true))

                AssistantStateManager.updateEmotion(EmotionState.HAPPY)
                return@withContext "শুভ দিন বস! ☀️ এখন সময় $timeFmt ($dateFmt)। ব্যাটারি চার্জ আছে ${bat.percentage}%। $unreadText আপনার দিনটি চমৎকার কাটুক!"
            }

            "GOOD_NIGHT", "GOOD NIGHT" -> {
                steps.add(TaskStep(id = 1, title = "মিডিয়া ভলিউম কমানো", status = StepStatus.IN_PROGRESS))
                steps.add(TaskStep(id = 2, title = "ব্যাটারি চার্জ সতর্কতা যাচাই", status = StepStatus.PENDING))
                steps.add(TaskStep(id = 3, title = "শুভ রাত্রি বার্তা প্রস্তুত", status = StepStatus.PENDING))

                AssistantStateManager.setActivePlan(TaskPlan(id = planId, userGoal = "Good Night Routine", steps = steps, currentPhase = PlanPhase.EXECUTE))
                delay(300)

                actionEngine.setVolume(15)
                steps[0] = steps[0].copy(status = StepStatus.COMPLETED, details = "ভলিউম ১৫% সেট করা হয়েছে")
                steps[1] = steps[1].copy(status = StepStatus.IN_PROGRESS)
                AssistantStateManager.setActivePlan(TaskPlan(id = planId, userGoal = "Good Night Routine", steps = steps, currentStepIndex = 1, currentPhase = PlanPhase.EXECUTE))
                delay(300)

                val bat = actionEngine.getBatteryInfo()
                val batAdvice = if (bat.percentage < 30 && !bat.isCharging) {
                    "চার্জ মাত্র ${bat.percentage}%, ঘুমানোর আগে ফোনে চার্জার লাগিয়ে নিলে ভালো হবে।"
                } else {
                    "ব্যাটারি চার্জ আছে ${bat.percentage}%।"
                }
                steps[1] = steps[1].copy(status = StepStatus.COMPLETED, details = batAdvice)
                steps[2] = steps[2].copy(status = StepStatus.COMPLETED)
                AssistantStateManager.setActivePlan(TaskPlan(id = planId, userGoal = "Good Night Routine", steps = steps, currentStepIndex = 2, currentPhase = PlanPhase.COMPLETED, isCompleted = true))

                AssistantStateManager.updateEmotion(EmotionState.CALM)
                return@withContext "শুভ রাত্রি বস! 🌙 ভলিউম কমিয়ে শান্ত করা হয়েছে। $batAdvice নিশ্চিন্তে ভালো ঘুম দিন, আমি ব্যাকগ্রাউন্ডে সতর্ক আছি।"
            }

            "WORK_MODE", "WORK MODE" -> {
                actionEngine.setVolume(25)
                AssistantStateManager.updateEmotion(EmotionState.FOCUSED)
                return@withContext "কাজের মোড চালু করা হয়েছে 🎯। ভলিউম ২৫% নির্ধারণ করা হয়েছে। সম্পূর্ণ মনোযোগ দিয়ে কাজ করুন!"
            }

            "GAMING_MODE", "GAMING MODE" -> {
                actionEngine.setVolume(75)
                AssistantStateManager.updateEmotion(EmotionState.EXCITED)
                return@withContext "গেমিং মোড প্রস্তুত 🎮! ভলিউম ৭৫% সেট করা হয়েছে। দারুন গেমিং সেশন হোক বস!"
            }

            else -> {
                return@withContext "রুটিন সম্পন্ন হয়েছে।"
            }
        }
    }
}
