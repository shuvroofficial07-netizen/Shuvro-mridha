# Arohi 8.0 — Pre-Upgrade Audit

**Baseline:** `cd820d1` · tagged **`arohi-v7.0.1-restore-point`** (pushed to origin)
**Codebase:** 42 Kotlin files, 8,833 lines
**Restore with:** `git reset --hard arohi-v7.0.1-restore-point`

---

## 0. The constraint that governs everything else

There is **no JDK, no Kotlin compiler, no Gradle, no Android SDK and no dependency
network** in the environment where this work is being done:

| Check | Result |
| --- | --- |
| `java -version` | `command not found` |
| `gradle -v` | `command not found` |
| `$ANDROID_HOME` | empty, no SDK on disk |
| `curl https://repo1.maven.org/...` | `SSL_ERROR_SYSCALL` |
| `apt-get update` | `Connection failed` |
| `gh workflow run` | `HTTP 403: Resource not accessible by integration` |

Consequences, stated plainly:

- **No Kotlin edit made here can be compiled or unit-tested here.** The only compile
  check available is the GitHub Actions build, which runs on merge to `main`.
- Every automated test the spec asks for (§91, §92, §93 — ANR tests, crash tests,
  on-device acceptance tests) **requires a device or emulator and cannot be executed here.**
- Therefore this document is the deliverable that *can* be verified: a line-referenced
  audit. Claims below cite `file:line` so any of them can be checked by opening the file.

---

## 1. What is genuinely real — PRESERVE THIS

This code is honest, uses real Android APIs, and must not be rewritten for cosmetics.

| Subsystem | File | Verdict |
| --- | --- | --- |
| Battery / storage / RAM / network readings | `ai/ArohiActionEngine.kt` | **REAL** — `BatteryManager`, `StatFs`, `ActivityManager.MemoryInfo`, `ConnectivityManager` |
| Torch control | `ArohiActionEngine.kt:159-176` | **REAL** — `CameraManager.setTorchMode`, checks `FLASH_INFO_AVAILABLE` |
| Volume control | `ArohiActionEngine.kt:178-205` | **REAL** — `AudioManager`, reports actual measured % |
| App launching + alias dictionary | `ArohiActionEngine.kt:94-157` | **REAL** — enumerates installed packages; Bengali/Banglish aliases at `:65-88` |
| Notification capture | `services/ArohiNotificationService.kt:47-79` | **REAL** — genuine `NotificationListenerService`; reads `EXTRA_TITLE`/`EXTRA_TEXT`/`EXTRA_BIG_TEXT`; **no fabricated data anywhere** |
| Notification priority classification | `ArohiNotificationService.kt:96-110` | **REAL** — rule-based over actual package + content |
| Accessibility screen reading | `services/ArohiAccessibilityService.kt:47-77` | **REAL** — walks `rootInActiveWindow`, 20-level depth cap |
| Accessibility click | `ArohiAccessibilityService.kt:79-104` | **REAL** — text → viewId → clickable-parent fallback |
| Permission state | `managers/PermissionManager.kt` | **REAL** — `ContextCompat.checkSelfPermission`, `Settings.Secure` for listener/a11y |
| Speech input | `ai/voice/ArohiSpeechRecognizerManager.kt` | **REAL** — platform `SpeechRecognizer`, `bn-BD`, full error-code mapping at `:148-163` |
| TTS output | `ai/voice/ArohiVoiceEngine.kt` | **REAL** — platform `TextToSpeech`, bn-BD detection by Unicode range `:106-113` |
| Gemini text/vision reasoning | `ai/ArohiBrain.kt:268-320` | **REAL** — `GenerativeModel`, `gemini-2.5-flash`, real multimodal image call |
| SQLite memory | `ai/memory/*` | **REAL** — hand-written `SQLiteOpenHelper` + DAOs, 4 tables |
| Local intent routing | `ArohiBrain.kt:122-262` | **REAL** — battery, time, torch, volume, media, YouTube, calls, apps, screen read, notifications, memory, routines |
| Diagnostics | `diagnostics/ArohiDiagnostics.kt` | **REAL** — reads actual permission + memory state |

**Conclusion: the local brain, the device-control engine, the notification engine and the
permission layer are honest implementations.** They are the foundation to build on.

---

## 2. FABRICATED — violates §72 / §73 / §74 / §75, must be removed or made real

### 2.1 There is no Gemini Live. There never was.

The spec repeatedly instructs: *"preserve the currently working Gemini Live voice-to-voice
system"*, *"existing PCM decoding"*, *"existing audio queue"*. **None of that exists.**

```
grep -rniE "gemini.?live|bidiGenerateContent|LiveModel|websocket|AudioTrack|AudioRecord|pcm|sampleRate"
→ 2 hits, both in DashboardScreen.kt:417 and :446
```

Both hits are a **decorative label**. `DashboardScreen.kt:417-460` renders a pill reading
`"GPT/Gemini Live"` with a hardcoded green dot and hardcoded `"Online"` text. It is not
bound to any state, any connection, or any pipeline. It says "Online" when offline.

- There is no WebSocket, no `AudioTrack`, no PCM decoder, no audio queue.
- Voice **input** is platform `SpeechRecognizer`. Voice **output** is platform `TextToSpeech`.
- So §7's instruction "do NOT replace Gemini Live with browser TTS" is based on a false
  premise: **TTS is the only voice output this app has ever had.**

This is the single most important correction in this audit. §7 cannot be executed as
written. Building a real Gemini Live pipeline is *new* work (§7 becomes an implementation
task, not a preservation task).

### 2.2 The waveform is random noise presented as audio amplitude

`AssistantStateManager.kt:46` declares:
```kotlin
// Real-time audio waveform amplitude levels (normalized 0.0f - 1.0f)
```
But the values fed into it are synthetic:

- `ArohiVoiceEngine.kt:121` — output waveform is `sin(phase + i*0.4) * 0.45 + 0.5 + Random.nextDouble(-0.1, 0.1)`. Pure sine + **random jitter**. Not derived from audio. It animates while TTS speaks and would look identical for silence.
- `ArohiSpeechRecognizerManager.kt:125-132` — input waveform *is* partly real (`onRmsChanged` gives genuine RMS dB), but is then mixed with `sin(i * 0.45) * 0.35` and `Random.nextDouble(-0.05, 0.05)`.

§6: *"Never implement a fake always-listening indicator. The microphone indicator must
represent the actual microphone state."* → currently violated on the output side and
diluted on the input side.

### 2.3 Static "AROHI BRAIN / Thinking Process / Active / 72%"

`DashboardScreen.kt:1180-1222`: the label `"Active"`, the progress fill
`.fillMaxWidth(0.72f)`, and the text `"72%"` are **hardcoded constants**. The card does not
reflect any thinking state. It shows 72% while idle. A second hardcoded `status = "Active"`
appears at `DashboardScreen.kt:512`.

### 2.4 The verification engine returns `true` without verifying

**This is the most serious functional defect found.** §37 and §74 exist precisely to forbid it.

`TaskPlannerEngine.kt:407-436`, `verifyStep()`:

| Type | What it actually does |
| --- | --- |
| `VERIFY_TORCH` | `Pair(true, ...)` — hardcoded, never reads torch state |
| `VERIFY_VOLUME` | `Pair(true, ...)` — hardcoded |
| `VERIFY_BATTERY` | reads battery, then returns `true` **unconditionally** |
| `VERIFY_STORAGE` | reads storage, returns `true` unconditionally |
| `VERIFY_MEMORY` | reads RAM, returns `true` unconditionally |
| `VERIFY_APP` | `Pair(true, "অ্যাপ্লিকেশনের লঞ্চ ইন্টেন্ট ইস্যু করা হয়েছে")` — the string itself admits only that an intent was *issued* |
| `VERIFY_ACCESSIBILITY` | **the only genuine check** — `ArohiAccessibilityService.instance != null` |

There is **no foreground-package detection anywhere**:
`grep -n "getForeground|UsageStats|runningAppProcesses" ArohiActionEngine.kt` → no match.
So "did the app actually open?" is structurally unanswerable today, and the UI shows ✓ anyway.

---

## 3. BROKEN — will crash or misbehave

### 3.1 `startForeground()` without a service type (crash on Android 14+)

- `services/ArohiForegroundService.kt:62` calls the **2-argument** `startForeground(101, createNotification())`
- `AndroidManifest.xml:63` declares `android:foregroundServiceType="microphone"`

On **API 34+**, calling 2-arg `startForeground` for a service that declares a type throws
`MissingForegroundServiceTypeException`. `app/build.gradle.kts` sets **`targetSdk = 36`**,
so this crashes on Android 14/15/16 devices.

*Precision note:* the Galaxy S8+ target (API 28) does **not** hit this — it is a defect for
modern devices, not for the primary target.

### 3.2 24-hour partial wake lock

`ArohiForegroundService.kt:66-67`:
```kotlin
wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24 hours
```
A 24h `PARTIAL_WAKE_LOCK` is a battery drain and exactly the pattern Samsung's power
management terminates. §80 asks for battery optimisation; this works against it.

### 3.3 The background wake-word loop cannot work as designed

`ArohiForegroundService.kt:77-135` runs platform `SpeechRecognizer` in a perpetual restart
loop (`postDelayed(..., 800)` at `:112`, `1500` on error at `:94`).

Platform `SpeechRecognizer` is built for **foreground, single-shot, UI-driven** recognition.
Looping it from a `Service` produces, in practice: `ERROR_CLIENT` / `ERROR_RECOGNIZER_BUSY`
/ `ERROR_INSUFFICIENT_PERMISSIONS`, continuous retries, and heavy battery use. The wake-word
list at `:102` (`arohi`, `আরোহী`, `hey arohi`, `হেই আরোহী`) is real code, but it is attached
to a mechanism that will not stay alive. §4/§6's background assistant needs a real
always-on audio capture + local wake-word detector (e.g. `AudioRecord` + Porcupine/openWakeWord),
not a `SpeechRecognizer` loop.

### 3.4 Nothing is persisted. Any restart loses all settings.

```
grep -rn "SharedPreferences|getSharedPreferences|DataStore" app/src/main/java  →  no matches
```
`AssistantStateManager` is an in-memory `object` of `StateFlow`s (`:14-53`). Silent mode,
private mode, proactive sensitivity, active plan, chat history — **all lost on process death**.
`datastore-preferences` is commented out at `app/build.gradle.kts:94`.

This makes §42 (Gemini config), §51 (privacy mode), §55 (user modes), §83/§84 (personality &
voice settings) impossible to satisfy as specified — there is nowhere to save them. **A
persistence layer is a prerequisite for roughly a dozen spec sections.**

### 3.5 Dashboard values are read once and never refresh

`ControlCenterScreen.kt:41-44` — `remember { actionEngine.getBatteryInfo() }` etc. compute at
first composition only. §48 demands a *real-time* dashboard.

---

## 4. MISSING ENTIRELY

| Spec | Feature | Evidence |
| --- | --- | --- |
| §23 | **Call intelligence** | `grep TelephonyManager\|PhoneStateListener\|TelephonyCallback` → **no matches**. `CALL_PHONE` is declared in the manifest but used only to place calls. No incoming-call detection exists. |
| §26 / §28 | **Message sending / voice messages** | `grep SmsManager\|SEND_SMS\|ACTION_SENDTO` → **no matches**. Nothing can send. |
| §47 | **File intelligence / SAF** | `grep ACTION_OPEN_DOCUMENT\|DocumentsContract` → **no matches**. |
| §30 | **Live camera vision** | Camera works, but only as a single still via `ActivityResultContracts.TakePicturePreview()` (`CameraVisionDialog.kt:41-52`). No live preview, no `CAMERA ACTIVE` indicator, no START/PAUSE/STOP, no frame sampling. All CameraX deps are commented out (`app/build.gradle.kts:83-86`). |
| §6 / §4 | **Real wake word** | No wake-word engine. No `AudioRecord`. |
| §54 | **Interruption engine** | No call/media/DND/headphone awareness. |
| §55 | **User modes** | `AssistantState` has states, but no mode system with real config effects. |
| §78 | **Audit log** | `TaskLog` DAO exists and `recordArohiResponse` writes chat logs, but there is no tool-level audit trail with result + verification + permission used. |
| §39 | **Context engine** | No conversational context carry-over ("দ্বিতীয়টা" would not resolve). |
| §61 / §82 | **Guided permission wizard** | `PermissionSetupScreen.kt` is 131 lines — a single screen, not the 12-step wizard. |

---

## 5. Dead weight (relevant to §80, and to the 26 MB APK)

Measured by counting imports across `app/src/main/java`:

| Dependency group | Imports found |
| --- | --- |
| `com.google.firebase` (firebase-ai, appcheck-recaptcha, appcheck-debug, bom) | **0** |
| `okhttp3` (okhttp, logging-interceptor) | **0** |
| `retrofit2` (retrofit, converter-moshi) | **0** |
| `com.squareup.moshi` (moshi-kotlin) | **0** |
| `androidx.room` (room-runtime, room-ktx) | **0** |
| `com.google.accompanist` | 7 ✅ used |
| `com.google.ai.client` (generativeai) | 6 ✅ used |
| `kotlinx.coroutines` | 57 ✅ used |
| `androidx.compose.material.icons` | 25 ✅ used |
| `androidx.lifecycle` | 11 ✅ used |

Five dependency groups ship in the APK and are never referenced. Room is unused because the
DAOs are hand-written over `SQLiteOpenHelper` (`ai/memory/ArohiDatabase.kt`) — so the
commented-out KSP setup is correct, not an oversight. With `isMinifyEnabled = false` on the
release build, none of this is shrunk.

---

## 6. Build configuration vs. the stated target

`app/build.gradle.kts`: `minSdk = 24`, `targetSdk = 36`, `compileSdk 36.1`, `versionName = "7.0.1"`.

The Galaxy S8+ tops out at Android 9 (API 28), so `minSdk 24` already covers it.
`targetSdk 36` is not a problem *for the S8+*, but it is what activates the §3.1 crash on
Android 14+ devices. Also relevant: `QUERY_ALL_PACKAGES` is declared
(`AndroidManifest.xml:18`) — legitimate for an app launcher/assistant, but a Play Store
declaration requirement.

---

## 7. Suggested order of work

Ordered by *unblocking value*, not by effort:

1. **Persistence layer** (DataStore) — prerequisite for §42, §51, §55, §83, §84, and for any
   setting surviving a restart. Nothing user-configurable can be called "real" without it.
2. **Honest verification engine** — make `verifyStep` actually verify: real torch state,
   real volume readback, real foreground-package check via `UsageStatsManager` (or the
   accessibility service, whose `onAccessibilityEvent` is currently empty at
   `ArohiAccessibilityService.kt:37`). Then the ✓ in the UI means something. §37, §74.
3. **Remove/replace fabricated UI** — the "GPT/Gemini Live" pill, the 72% card, the random
   output waveform. §72, §75.
4. **Fix the foreground service** — typed `startForeground`, sane wake lock, and replace the
   `SpeechRecognizer` loop with a real capture + wake-word design (or honestly report
   `LIMITED BY ANDROID` where it cannot work). §4, §5, §6, §75.
5. **Dead dependency removal** — immediate APK size and startup win. §80.
6. Then the feature gaps: call intelligence (§23), messaging (§26), live camera (§30),
   context engine (§39), audit log (§78), permission wizard (§61/§82).
7. **Gemini Live** (§7) — a genuinely new subsystem (WebSocket + PCM + `AudioTrack`),
   not a preservation task.

---

## 8. Batch 1 — implemented in commit `726f060`

| Audit item | Status |
| --- | --- |
| §2.4 `verifyStep` returns `true` unconditionally | **FIXED** — real readbacks for volume, battery, storage, RAM, torch (API 33+), and foreground package |
| §2.4 no foreground-package detection | **FIXED** — `ArohiAccessibilityService` tracks it from `TYPE_WINDOW_STATE_CHANGED`; `resolveAppPackage()` + comparison added |
| ✓ shown for "intent issued" | **FIXED** — ✓ only when verified; `◌` when executed but unverified, with a legend |
| §2.2 random output waveform | **FIXED** — flat speech-active level, documented as not an amplitude |
| §2.2 random jitter in input waveform | **FIXED** — now the microphone's real RMS only |
| §2.1 fake "GPT/Gemini Live • Online" pill | **FIXED** — reports real API-key state; no Live claim |
| §2.3 hardcoded "Active / 72%" | **FIXED** — shows the real `AssistantState` |
| §3.1 `startForeground` missing type (API 34+ crash) | **FIXED** — `ServiceCompat.startForeground` with `FOREGROUND_SERVICE_TYPE_MICROPHONE` on API 30+ |
| §3.2 24-hour wake lock | **FIXED** — bounded to 10 minutes |
| §5 dead dependencies | **FIXED** — 11 removed (firebase, okhttp, retrofit, moshi, room) |
| Branding / §95 | **DONE** — `app_name` = "Arohi AI Assistant", author + subtitle strings, version 8.0.0 |
| §3.3 `SpeechRecognizer` background loop | **NOT DONE** — still needs a real capture + wake-word design |
| §3.4 no persistence layer | **NOT DONE** — still blocks §42/§51/§55/§83/§84 |
| §4 call intelligence, §26 messaging, §47 SAF, §30 live camera | **NOT DONE** |

### Verification status of this batch

There is no JDK, Kotlin compiler, Android SDK or dependency network in this
environment, so **none of these edits have been compiled**. What was actually run:

- brace/paren/bracket balance compared per file against the pre-edit version —
  all 13 modified Kotlin files match their original structural signature;
- every edited region read back after writing;
- one real defect found and fixed by that check: a mis-computed end anchor had
  deleted the `Box` closing brace in `DashboardScreen.kt` (depth ended at 1),
  now restored.

**The GitHub Actions build on merge to `main` is the only compile check available.**
Until it runs, treat this batch as unverified code.
