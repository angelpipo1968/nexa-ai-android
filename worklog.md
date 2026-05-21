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
