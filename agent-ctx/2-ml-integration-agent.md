# Task 2 — On-Device ML Engine & SmartRouting Integration

## Agent: ML Integration Agent

## Summary
Completed all 7 TODO stubs in OnDeviceInferenceManager and integrated SmartRoutingManager into NexaViewModel for hybrid on-device/cloud AI inference.

## Files Modified
1. **OnDeviceInferenceManager.kt** — Full rewrite with Nexa SDK reflection loading + ML Kit integration
2. **SmartRoutingManager.kt** — Converted to Hilt @Singleton with enhanced initialization
3. **NexaViewModel.kt** — Added SmartRoutingManager field, init-time initialization, smart routing in fetchAiResponse(), shutdown in onCleared()
4. **Models.kt** — Added `onDeviceReady` and `inferenceMode` fields to NexaUiState

## Key Implementation Details

### OnDeviceInferenceManager (7 methods completed)
- **initialize()**: Loads Nexa SDK via Class.forName reflection (graceful ClassNotFoundException fallback); initializes ML Kit LanguageIdentification
- **loadModel()**: Reflection-based NexaEngine.loadModel() call; marks model as current even without SDK for routing
- **generateText()**: Reflection-based NexaEngine.generate(prompt, systemPrompt, maxTokens); returns null if no engine
- **analyzeImage()**: Reflection-based NexaEngine.analyzeImage(imageBase64, question); returns null if no vision model
- **detectLanguage()**: ML Kit LanguageIdentification with suspendCancellableCoroutine; returns ISO 639-1 code or null
- **isNPUAvailable()**: Checks hardware (Build.HARDWARE/SOC_MANUFACTURER) AND nexaEngine != null
- **shutdown()**: Reflection-based NexaEngine.shutdown(), closes ML Kit, nulls all references

### SmartRoutingManager (Hilt @Singleton)
- @Inject constructor with @ApplicationContext
- Added _onDeviceReady StateFlow
- initialize() now also loads MODEL_CHAT and updates readiness state
- Added generateOnDevice() and detectLanguage() convenience methods
- All existing routing logic preserved

### NexaViewModel Integration
- smartRoutingManager field constructed in ViewModel
- Initialized in init{} with viewModelScope.launch; updates _uiState.onDeviceReady
- fetchAiResponse() checks smart routing BEFORE cloud call
- On-device success path: adds message, triggers TTS, returns early
- Falls through to cloud if on-device returns null
- smartRoutingManager.shutdown() in onCleared()

### Models.kt
- `onDeviceReady: Boolean = false`
- `inferenceMode: String = "HYBRID"` (ONLINE | ON_DEVICE | HYBRID)
