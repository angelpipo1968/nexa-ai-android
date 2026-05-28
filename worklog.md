# NEXA PRO AI - Work Log

---
Task ID: 1
Agent: Main Agent
Task: Fix hands-free (manos libre) voice mode that cuts off

Work Log:
- Cloned and analyzed NEXA PRO AI repo at /home/z/my-project/nexa-ai-android
- Discovered two parallel Android modules: `app/` (v4.0, actually built) and `android-native-app/` (v5.0, NOT in settings.gradle.kts)
- Identified 7 critical bugs causing hands-free cut-off:
  1. Bug 1 (CRITICAL): speak() → stopSpeaking() fires onSpeakingStateChanged(false), triggering unwanted listening restart → TTS and SpeechRecognizer conflict
  2. Bug 2 (CRITICAL): AUDIOFOCUS_LOSS_TRANSIENT kills TTS permanently → notifications/alarms break hands-free
  3. Bug 3: Post-TTS listening delay too short (300ms) → SpeechRecognizer errors on Samsung/Xiaomi/OPPO
  4. Bug 4: Barge-in parameters too aggressive → false interruptions from TTS audio bleed
  5. Bug 5: No speechStateLock synchronization → race conditions in TTS callbacks
  6. Bug 6: Recognition retry delay too short (1500ms) → restart loops
  7. Bug 7 (in android-native-app/): isPreparingToSpeak never reset → permanently breaks voice loop after first speak()

Fixes Applied (v5.1):
- app/SpeechManager.kt: Added isPreparingToSpeak flag (set before stopSpeaking, reset in TTS onStart)
- app/SpeechManager.kt: Added isPausedByFocusLoss flag for AUDIOFOCUS_LOSS_TRANSIENT handling
- app/SpeechManager.kt: Added speechStateLock synchronized block around onDone/onError/stopSpeaking
- app/SpeechManager.kt: Increased bargeInCooldownMs from 2500ms to 3500ms
- app/SpeechManager.kt: Increased vadZcrThreshold from 15.0 to 18.0
- app/SpeechManager.kt: Increased barge-in frame requirements from 8/3 to 12/5
- app/NexaViewModel.kt: Increased post-TTS listening delay from 300ms to 700ms
- app/NexaViewModel.kt: Increased recognition retry delay from 1500ms to 2000ms
- app/NexaViewModel.kt: Increased barge-in after-stop delay from 80ms to 200ms
- android-native-app/SpeechManager.kt: Fixed duplicate isPreparingToSpeak assignment
- android-native-app/SpeechManager.kt: Added isPreparingToSpeak = false reset in TTS onStart

Stage Summary:
- All v5.1 hands-free cut-off fixes applied to both modules
- The `messagesessages` bug in chat/route.ts was already fixed in the current code
- The `app/` module (the one that actually builds) now has all v5.1 fixes
- The `android-native-app/` module's isPreparingToSpeak bug is also fixed

---
Task ID: 2
Agent: Main Agent
Task: Fix geolocation - map not updating, location not working

Work Log:
- Analyzed the full geolocation pipeline: Android GPS → NexaViewModel → NexaRepository → Backend → AI
- Found 4 critical issues:
  1. CRITICAL: Android app obtains GPS coordinates but NEVER sends them to the backend.
     The backend uses ip-api.com which returns the Vercel CDN server location, not the user's.
  2. CRITICAL: Yandex Static Maps service is unreliable/blocked in many regions
  3. MEDIUM: resolveAddress() blocks the calling thread with CountDownLatch (ANR risk)
  4. MEDIUM: No Nominatim API fallback when Android Geocoder fails

Fixes Applied (v5.1):
- NexaRepository.kt: Added latitude, longitude, city, country fields to ChatRequest
- NexaRepository.kt: Updated sendMessage() to accept and pass location data
- NexaViewModel.kt: Now sends GPS coordinates to backend via repository.sendMessage()
- validation.ts: Added latitude, longitude, city, country to chatSchema
- chat/route.ts: Prioritizes client GPS over ip-api, includes coordinates in context
- chat/route.ts: Added Nominatim timezone lookup from coordinates
- maps.ts: Replaced Yandex Static Maps with OpenStreetMap static tiles
- maps.ts: Added OpenStreetMap interactive link alongside Google Maps
- LocationStore.kt: Changed resolveAddress() from blocking to suspend function
- LocationStore.kt: Replaced CountDownLatch with suspendCancellableCoroutine
- LocationStore.kt: Added Nominatim reverse geocoding as final fallback
- LocationStore.kt: Added better logging for location resolution
- Synced all changes to android-native-app/ module

Stage Summary:
- GPS coordinates now flow from Android device → Backend → AI context
- Maps use reliable OpenStreetMap instead of Yandex
- Geocoding has 3-tier fallback: Android Geocoder → English Geocoder → Nominatim API
- No more thread blocking with CountDownLatch
- Location now includes coordinates in AI context for precise weather/queries
---
Task ID: 3
Agent: Main Agent
Task: Implement i18n framework for NEXA PRO Android app

Work Log:
- Analyzed current i18n state: NexaStrings.kt with 150 hardcoded bilingual maps, TranslatorScreen with 14 hardcoded English strings, VoiceCommandsHandler with ~50 inline bilingual strings
- Created values/strings.xml (English, 254 keys) and values-es/strings.xml (Spanish, 254 keys) for both app and android-native-app modules
- Created LocaleManager utility for centralized locale management (wrapContext, applyLanguage, toLocale)
- Refactored NexaStrings.kt: removed 145-line hardcoded maps, now loads from XML resources via getIdentifier()
- Added Context-based API: NexaStrings.get(context, key) with format string support
- Kept legacy NexaStrings.get(key, lang) API for backward compatibility
- Updated VoiceCommandsHandler: replaced all inline es("...", "...") patterns with NexaStrings.get(context, key) calls
- Updated TranslatorScreen: replaced 13 hardcoded English strings with stringResource(R.string.*)
- Fixed MarkdownText "Abrir enlace" → stringResource(R.string.open_link)
- Fixed ChatComponents "Generated image" → stringResource(R.string.generated_image)
- Fixed ChatScreen "Traductor" strings in android-native-app module
- Committed (aaee479) and pushed (c63d51c) to GitHub

Stage Summary:
- 17 files changed, +2002/-349 lines
- Both app and android-native-app modules fully updated
- To add new language: create values-XX/strings.xml with same keys
- New files: LocaleManager.kt (both modules), values-es/strings.xml (both modules)
- Modified: NexaStrings.kt, VoiceCommandsHandler.kt, TranslatorScreen.kt, MarkdownText.kt, ChatComponents.kt, ChatScreen.kt, strings.xml (both modules)

---
Task ID: 4a
Agent: Main Agent
Task: Sync ProGuard rules for Jsoup and all modules in android-native-app

Work Log:
- Discovered app/proguard-rules.pro already had complete rules (93 lines) including Jsoup, Hilt, Web, Memory, Emotion, Profile
- android-native-app/proguard-rules.pro was incomplete (47 lines, missing Jsoup, Hilt, Web, Memory, Emotion, Profile rules)
- Copied complete rules from app to android-native-app

Stage Summary:
- android-native-app/proguard-rules.pro synced: 47 → 99 lines
- Release builds of both modules now safe from R8 stripping Jsoup/Hilt/Web models

---
Task ID: 4b
Agent: Main Agent + subagent
Task: Implement offline-first architecture foundation

Work Log:
- Created OfflineMessageQueue.kt: Room table `pending_messages` with enqueue/dequeue, retry tracking, countFlow()
- Created OfflineCache.kt: Room tables `response_cache` (24h TTL) and `search_cache` (2h TTL) with eviction
- Created NetworkMonitor.kt: Hilt @Singleton bridge to NexaApplication.isNetworkAvailable StateFlow
- Updated MLDatabase.kt (app): added 3 entities, 3 DAOs, bumped version 3 → 4
- Updated NexaDatabase.kt (native-app): added 3 entities, 3 DAOs, bumped version 1 → 2
- Added offline-first ProGuard keep rules to both modules
- Committed (457040b) and pushed to GitHub

Stage Summary:
- 10 files changed, +409/-4 lines
- New files: OfflineMessageQueue.kt, OfflineCache.kt, NetworkMonitor.kt (both modules)
- Modified: MLDatabase.kt (v4), NexaDatabase.kt (v2), proguard-rules.pro (both)
- Database now has 13 tables (was 10)
- Foundation ready for: offline message queuing, response caching, network-aware routing

---
Task ID: 5
Agent: Main Agent
Task: Connect OfflineMessageQueue, OfflineCache, and NetworkMonitor into the chat flow (Offline-First integration)

Work Log:
- Created OfflineManager.kt at data/OfflineManager.kt: orchestrator class that ties together the existing offline infrastructure
  - Observes NetworkMonitor.isOnline and PendingMessageDao.countFlow() reactively
  - enqueueIfOffline(): queues message when offline, returns true/false
  - flushPendingMessages(): dequeues and returns all pending messages when network restored
  - markFailed(): increments retry count and removes messages that exceeded max retries
  - cacheResponse(): stores AI responses in CachedResponseDao for offline viewing
  - getCachedResponses()/getCachedResponse(): retrieves cached responses
  - evictOldCache(): cleans up stale cache entries from both response_cache and search_cache
  - pendingCount/isOnline StateFlows exposed for UI binding
  - destroy() cancels internal CoroutineScope
- Modified NexaViewModel.kt:
  - Added offlineManager field (initialized with Application context)
  - Added observeOfflineState() call in init block
  - Added observeOfflineState() method: collects pendingCount for UI badge, flushes pending messages on network restore
  - Added offline check in sendMessage(): if !isOnline, queues message via OfflineManager and adds offline indicator message to chat
  - Added response caching in fetchAiResponse() after StreamEvent.Done: caches sessionId + lastUserMsg + fullResponse + provider
  - Added offlineManager.destroy() in onCleared()
- Modified Models.kt:
  - Added pendingMessageCount: Int = 0 to NexaUiState data class (under Offline section)

Stage Summary:
- 3 files changed: 1 new (OfflineManager.kt), 2 modified (NexaViewModel.kt, Models.kt)
- Messages are now queued when offline and auto-sent when connectivity is restored
- AI responses are cached for offline viewing
- UI can display pending message count badge via pendingMessageCount state

---
Task ID: 2 (ML Engine)
Agent: ML Integration Agent
Task: Complete On-Device ML engine and SmartRouting integration for NexaViewModel

Work Log:
- Rewrote OnDeviceInferenceManager.kt with full implementation of all 7 TODO stubs:
  1. initialize(): Loads Nexa SDK via reflection (Class.forName) for graceful fallback when SDK AAR is absent; initializes ML Kit LanguageIdentification
  2. loadModel(): Invokes NexaEngine.loadModel() via cached reflection method; marks model as current even if SDK unavailable (for routing purposes)
  3. generateText(): Invokes NexaEngine.generate() via reflection with prompt, systemPrompt, maxTokens; returns null if no engine
  4. analyzeImage(): Invokes NexaEngine.analyzeImage() via reflection with imageBase64 + question; returns null if no vision engine
  5. detectLanguage(): Uses ML Kit LanguageIdentification.getClient() with suspendCancellableCoroutine; returns ISO 639-1 code or null for "und"
  6. isNPUAvailable(): Checks both hardware (Build.HARDWARE contains "qcom" / SOC_MANUFACTURER == "Qualcomm") AND nexaEngine != null
  7. shutdown(): Invokes NexaEngine.shutdown() via reflection, closes ML Kit LanguageIdentifier, nulls all references

- Rewrote SmartRoutingManager.kt as a Hilt @Singleton with @Inject constructor:
  - Added @Singleton and @Inject annotations with @ApplicationContext qualifier
  - Added _onDeviceReady StateFlow exposed as onDeviceReady
  - Enhanced initialize() to load the chat model (MODEL_CHAT) after engine init and update _onDeviceReady
  - Added generateOnDevice() convenience method delegating to onDeviceManager
  - Added detectLanguage() convenience method delegating to onDeviceManager
  - All existing routing logic preserved (shouldUseOnDevice, isSimpleQuery, hasToolKeywords, etc.)

- Modified NexaViewModel.kt to integrate SmartRoutingManager:
  - Added private val smartRoutingManager field (constructed with context as Application)
  - Added smartRoutingManager.initialize() call in init block with viewModelScope.launch; updates _uiState.onDeviceReady
  - Added smart routing logic in fetchAiResponse() BEFORE repository.sendMessage():
    - Gets last user message content and calls shouldUseOnDevice()
    - If routing says on-device and engine is ready, calls generateOnDevice() with prompt and systemPrompt
    - If on-device returns non-null result, adds assistant message and triggers TTS, then returns
    - Falls through to cloud inference if on-device fails or returns null
  - Added smartRoutingManager.shutdown() in onCleared()

- Modified Models.kt to add on-device ML state fields to NexaUiState:
  - Added onDeviceReady: Boolean = false
  - Added inferenceMode: String = "HYBRID" (values: ONLINE, ON_DEVICE, HYBRID)

Stage Summary:
- 4 files modified: OnDeviceInferenceManager.kt, SmartRoutingManager.kt, NexaViewModel.kt, Models.kt
- All 7 TODO stubs in OnDeviceInferenceManager now fully implemented
- SmartRoutingManager is now a Hilt singleton and properly connected to ViewModel
- NexaViewModel uses smart routing: tries on-device first for simple/offline queries, falls back to cloud
- ML Kit language detection integrated via suspendCancellableCoroutine
- Nexa SDK loaded via reflection for compile-time safety
---
Task ID: 4
Agent: Main Agent
Task: Implement Episodic Memory integration and Enhanced Translator for the Nexa AI Android app

Work Log:
- Created memory package at com.nexa.ai.memory/
  - EpisodicMemoryManager.kt: Full episodic memory system with SharedPreferences + Gson storage
    - MemoryEntry data class with category, importance, timestamp, sessionId, accessCount
    - MemoryCategory enum: PERSONAL, PREFERENCE, LOCATION, EVENT, SKILL, CONVERSATION, EMOTION, GENERAL
    - UserProfile data class: name, preferredLanguage, location, occupation, interests, communicationStyle
    - storeMemory(): duplicate detection via Jaccard similarity, importance-boosting on repeat
    - searchMemories(): relevance scoring with word match, importance weight, recency decay
    - extractMemoriesFromMessage(): auto-extracts name, preferences, location, occupation via regex (Spanish + English patterns)
    - buildMemoryContext(): builds AI context string from profile, relevant memories, and stored facts
    - storeFact()/getFacts(): simple fact storage with deduplication
    - updateProfile(): thread-safe profile updates with interaction tracking
    - MAX_MEMORIES=100 with importance-weighted trimming

- Created translator package at com.nexa.ai.translator/
  - LanguageDetector.kt: ML Kit Language Identification wrapper
    - detect(): suspend function returning ISO 639-1 language code
    - SUPPORTED_LANGUAGES map with 24 languages
    - getDisplayName(): language code to display name mapping
  - BilingualConversationManager.kt: Real-time bilingual conversation translation
    - configure(): sets up bidirectional ML Kit translators with model download
    - translate(): auto-detects source language and translates to opposite
    - translateFrom(): translates from a specific source language
    - shutdown(): cleans up translator resources

- Modified Models.kt:
  - Added userProfileName: String = "" to NexaUiState
  - Added bilingualModeEnabled: Boolean = false to NexaUiState
  - Added bilingualLangA: String = "es" to NexaUiState
  - Added bilingualLangB: String = "en" to NexaUiState

- Modified NexaViewModel.kt:
  - Added memoryManager field (EpisodicMemoryManager initialized with Application context)
  - Modified buildSystemPrompt(): added memory context injection after location context
    - Retrieves last user message and builds relevant memory context
    - Appends "USER CONTEXT FROM MEMORY:" section with profile, memories, facts
  - Modified sendMessage(): added episodic memory extraction after adding user message
    - Launches coroutine on Dispatchers.IO to extract memories
    - Updates userProfileName in UI state when name is detected
  - Added two new voice commands:
    - "recuerda/recordar/remember this/memoriza": stores fact and memory entry, responds with confirmation
    - "qué sabes de mí/what do you know about me/quién soy": reads user profile and top 5 memories aloud

Stage Summary:
- 5 files: 3 new (EpisodicMemoryManager.kt, LanguageDetector.kt, BilingualConversationManager.kt), 2 modified (Models.kt, NexaViewModel.kt)
- AI now has persistent memory of user facts, preferences, and personal info across sessions
- Memory context automatically injected into every AI request for personalized responses
- Bilingual conversation manager ready for UI integration (TranslatorScreen enhancement)
- Two new voice commands for memory interaction (remember + recall)
- All dependencies (ML Kit language-id:17.0.0, translate:17.0.3, gson:2.11.0) already in build.gradle.kts

---
Task ID: 3
Agent: Feature Agent
Task: Implement Home Screen Widgets, Smart Notifications, and App Shortcuts for the Nexa AI Android app

Work Log:
- Created widget package at com.nexa.ai.widget/ with 3 home screen widgets:
  - NexaVoiceWidget.kt: Big mic button widget that opens app in voice mode via PendingIntent
    - ACTION_START_VOICE intent with EXTRA_VOICE_MODE extra
    - Single widget click target on mic button ImageView
  - NexaChatWidget.kt: Quick chat text input widget with "NEXA PRO" title and send button
    - ACTION_QUICK_CHAT intent opens app for quick chat
    - Send button (ImageButton) as click target
  - NexaWeatherWidget.kt: Weather display widget with location-based weather fetching
    - Caches last weather data in SharedPreferences (temp, condition, city)
    - Fetches fresh data from https://www.nexa-ai.dev/api/weather in coroutine
    - Falls back to cached data if network unavailable
    - Updates widget views asynchronously after fetch

- Created widget layout XMLs in res/layout/:
  - widget_voice.xml: Dark background (#1A1A2E), 72dp green mic button, "NEXA VOICE" label
  - widget_chat.xml: Dark background, "NEXA PRO" title, "Tap to chat..." hint, green send button
  - widget_weather.xml: Dark background, large 36sp temperature, green condition text, grey city text

- Created widget info XMLs in res/xml/:
  - widget_voice_info.xml: 2x2 cells, 110dp min, no auto-update
  - widget_chat_info.xml: 3x2 cells, 180x110dp min, no auto-update
  - widget_weather_info.xml: 3x2 cells, 180x110dp min, 30min auto-update (1800000ms)

- Created notification package at com.nexa.ai.notification/ with 3 components:
  - NexaNotificationManager.kt: Central notification management object
    - 3 notification channels: nexa_reminders (HIGH), nexa_weather (DEFAULT), nexa_summary (DEFAULT)
    - scheduleReminder(): Uses AlarmManager with exact/inexact alarm based on SDK level
    - showReminderNotification(): High-priority notification with PendingIntent to MainActivity
    - scheduleMorningSummary(): Daily repeating alarm at 7:00 AM via setInexactRepeating
  - ReminderReceiver.kt: BroadcastReceiver that shows reminder notification on alarm trigger
  - MorningSummaryService.kt: Service that fetches weather and shows morning summary notification
    - Fetches weather from https://www.nexa-ai.dev/api/weather
    - Shows BigTextStyle notification with weather-based greeting
    - Falls back to generic greeting on network error

- Created shortcuts package at com.nexa.ai.shortcuts/:
  - AppLauncherManager.kt: Voice-driven app launcher with 30+ app mappings (Spanish + English names)
    - openApp(): Direct/fuzzy package name match → launch intent → Play Store fallback
    - setAlarm(): Uses AlarmClock.ACTION_SET_ALARM with hour/minute/message
    - setTimer(): Uses AlarmClock.ACTION_SET_TIMER with duration/message
    - makeCall(): Uses ACTION_DIAL intent with phone number
    - openUrl(): Uses ACTION_VIEW with URI parsing
    - Bilingual app name dictionary: WhatsApp, YouTube, Spotify, Instagram, etc. in Spanish + English

- Updated AndroidManifest.xml:
  - Added SCHEDULE_EXACT_ALARM and USE_EXACT_ALARM permissions
  - Added 3 widget receivers with APPWIDGET_UPDATE intent filters and meta-data
  - Added ReminderReceiver broadcast receiver
  - Added MorningSummaryService with BIND_JOB_SERVICE permission

- Modified NexaViewModel.kt:
  - Added appLauncher field (AppLauncherManager initialized with Application context)
  - Added notification channel initialization in init block: NexaNotificationManager.createChannels(context)
  - Added smartRoutingManager.updateNetworkStatus(isOnline) in observeNetwork()
  - Added 5 new voice commands in sendMessage():
    - "abre/open/abrir" + app name: Opens app via AppLauncherManager, speaks confirmation
    - "alarma/alarm" + time: Sets alarm with parsed hour:minute, supports hour-only format
    - "llama a/llamar a/call" + contact: Opens dialer with phone number or contact name
    - "recuérdame/recuerdame/remind me/recordatorio" + text: Schedules reminder notification 1 min from now
    - "temporizador/timer/cuenta atrás" + duration: Sets timer with parsed minutes/seconds

- Modified Models.kt:
  - Added morningSummaryEnabled: Boolean = false to NexaUiState data class

Stage Summary:
- 13 files: 7 new (3 widget Kotlin, 3 notification Kotlin, 1 shortcuts Kotlin), 6 modified/created (3 layout XML, 3 info XML), 3 modified (AndroidManifest.xml, NexaViewModel.kt, Models.kt)
- 3 home screen widgets available: Voice mic, Quick chat, Weather
- Smart notifications with 3 channels: Reminders, Weather alerts, Morning summary
- 5 new voice commands: open apps, set alarms, make calls, set reminders, set timers
- All features bilingual (Spanish + English) for voice commands and app name resolution
- Weather widget auto-refreshes every 30 minutes with cached fallback

---
Task ID: 10
Agent: Refactoring Agent
Task: Extract business logic from NexaViewModel into UseCase classes to reduce ViewModel size and improve testability

Work Log:
- Analyzed NexaViewModel.kt (1593 lines) to identify extractable business logic domains
- Reviewed existing codebase: Models.kt, VoiceCommandsHandler.kt, NexaRepository.kt, LocationStore.kt, ReminderReceiver.kt
- Created usecase package at com.nexa.ai.usecase/
- Created VoiceCommandUseCase.kt:
  - VoiceCommand sealed class with 24 command types + None fallback
  - parseCommand(): Parses raw voice transcription text into typed VoiceCommand instances
  - Supports bilingual commands (Spanish + English) for all 24 command types
  - Command types: ClearChat, ExportPdf, StopHandsFree, SwitchLanguage, ChangeVoice, NewChat, RepeatLast, Silence, ShowHelp, ReadLast, ChangeTheme, OpenSettings, CreateImage (with prompt extraction), CreateWebsite, Share, OpenCamera, WriteCode, OpenApp (with app name extraction), SetAlarm (with time parsing HH:mm and hour-only), MakeCall (with contact extraction), SetReminder (with text extraction), SetTimer (with minute/second parsing), RememberFact (with fact extraction), WhatDoYouKnow
  - getHelpText(): Returns localized help text listing available commands
  - Pure Kotlin — no Android dependencies, fully unit-testable
- Created ChatUseCase.kt:
  - createUserMessage(): Creates user Message with optional attachment (📎 prefix)
  - createAssistantPlaceholder(): Creates streaming assistant Message placeholder
  - generateAssistantId(): Generates unique "a-{timestamp}" IDs
  - generateTitle(): Truncates first message to 30 chars with ellipsis
  - formatForApi(): Converts UI Message list to API ChatMessage list
  - buildLocationParams(): Extracts location data into LocationParams data class
  - isValidMessage(): Validates message has content or attachment
  - isSendCooldownPassed(): Checks send cooldown (default 1500ms)
  - LocationParams data class: latitude, longitude, city, country (all nullable)
  - Pure Kotlin — no Android dependencies, fully unit-testable
- Created NotificationUseCase.kt:
  - scheduleReminder(): Schedules AlarmManager notification with SDK compatibility (S+ exact alarm permission check, FLAG_IMMUTABLE)
  - parseVoiceTime(): Parses time strings from voice commands ("a las 7:30", "at 3pm", "5 de la tarde") → epoch millis
  - parseTimerDuration(): Parses timer durations from voice commands ("5 minutos", "30 seconds", "1 minute 30 seconds") → total seconds
  - getNextTimeMillis(): Private helper that returns next occurrence of a time (auto-rolls to tomorrow if already passed)
  - Requires Context for AlarmManager access (can be tested with mocked Context)

Stage Summary:
- 3 new files created in com.nexa.ai.usecase/ package
- VoiceCommandUseCase: 24 voice command types extracted from inline ViewModel logic
- ChatUseCase: 8 message/session utility functions extracted from ViewModel
- NotificationUseCase: 3 notification scheduling/parsing functions extracted from ViewModel
- All UseCases are independently testable without ViewModel dependencies
- NexaViewModel.kt can now delegate to these UseCases instead of containing the logic inline
- Next step: Wire UseCases into NexaViewModel constructor and replace inline logic with delegated calls

---
Task ID: 8 & 9
Agent: Test & Accessibility Agent
Task: Create unit tests for key components and add TalkBack accessibility support

Work Log:
- Created unit test directory structure under src/test/java/com/nexa/ai/ (memory, ml, shortcuts, translator)
- Wrote EpisodicMemoryManagerTest.kt (9 tests):
  - store and retrieve memory (Mockito-verified SharedPreferences.put)
  - extract name from Spanish message ("Me llamo Carlos")
  - extract name from English message ("My name is Alice")
  - extract preference from Spanish message ("Me gusta la música rock")
  - extract location from message ("Vivo en Madrid")
  - no extraction from assistant messages
  - user profile starts empty
  - store and retrieve facts
  - duplicate facts not stored
- Wrote SmartRoutingManagerTest.kt (4 tests):
  - simple greeting patterns detected (14 patterns, Spanish + English)
  - tool keywords detected (6 complex queries matched against 7 keywords)
  - RoutingDecision data class has correct fields
  - offline routing with fallback message
- Wrote AppLauncherManagerTest.kt (3 tests):
  - common Spanish app names map correctly (6 mappings verified)
  - common English app names map correctly (5 mappings verified)
  - unknown app returns null
- Wrote LanguageDetectorTest.kt (4 tests):
  - common languages are supported (10 required codes verified)
  - display names are correct (es→Español, en→English, fr→Français, de→Deutsch)
  - unknown code returns uppercase code ("xx" → "XX")
  - at least 20 languages supported (24 in SUPPORTED_LANGUAGES)
- Created AccessibilityModifiers.kt with 7 reusable Compose modifier extensions:
  - accessibleButton(description): announces as Role.Button
  - accessibleImage(description): announces as Role.Image
  - accessibleChatMessage(role, content): bilingual role-aware descriptions (user→"Tu mensaje:", assistant→"Respuesta de Nexa:")
  - accessibleHeading(): marks as heading for screen reader navigation
  - accessibleToggle(description, isOn): switch with state (activado/desactivado)
  - accessibleTextField(label, text): textbox with label and current text
  - accessibleListItem(position, total, description): list item with position context
- Added Mockito test dependencies to build.gradle.kts:
  - org.mockito:mockito-core:5.11.0
  - org.mockito.kotlin:mockito-kotlin:5.2.1

Stage Summary:
- 6 files created: 4 test files (20 tests total), 1 accessibility utility, 1 build config update
- Test coverage: EpisodicMemoryManager (9), SmartRoutingManager (4), AppLauncherManager (3), LanguageDetector (4)
- AccessibilityModifiers ready for integration into ChatScreen, ChatComponents, and other UI composables
- All tests use pure JVM testing (no Android device required)
