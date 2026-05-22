<div align="center">

# NEXA PRO v5.0

**Advanced AI Assistant for Android**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blueviolet?logo=kotlin)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-BOM_2024.12-blue?logo=android)](https://developer.android.com/jetpack/compose)
[![Min SDK](https://img.shields.io/badge/Min_SDK-26_(Android_8.0)-green)](https://developer.android.com/about/versions/oreo)
[![Target SDK](https://img.shields.io/badge/Target_SDK-35-green)](https://developer.android.com/about/versions)
[![License](https://img.shields.io/badge/License-MIT-orange)](LICENSE)

A feature-rich, privacy-first AI assistant that combines cloud LLM intelligence with on-device ML capabilities, voice interaction, IoT control, web search, and deep user profiling.

**37 Kotlin files | 22,673 lines of code | 18 core modules**

</div>

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Core Features](#core-features)
  - [AI Chat](#ai-chat)
  - [Voice System](#voice-system)
  - [Web Search & Scraping](#web-search--scraping)
  - [On-Device ML Engine](#on-device-ml-engine)
  - [Emotion Intelligence](#emotion-intelligence)
  - [Episodic Memory](#episodic-memory)
  - [User Profiling](#user-profiling)
  - [IoT & Smart Home](#iot--smart-home)
  - [Sensor Hub](#sensor-hub)
  - [Translator](#translator)
  - [Video Generation](#video-generation)
- [Module Inventory](#module-inventory)
- [Recent Improvements (v5.0)](#recent-improvements-v50)
- [Improvement Roadmap](#improvement-roadmap)
  - [High Priority](#high-priority)
  - [Medium Priority](#medium-priority)
  - [Future Vision](#future-vision)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [API Providers](#api-providers)
- [Permissions](#permissions)
- [Security & Privacy](#security--privacy)
- [License](#license)

---

## Overview

**NEXA PRO** is an ambitious Android AI assistant that goes beyond simple chat. It integrates multiple AI providers (Groq, Pollinations AI) with extensive on-device machine learning, natural voice interaction, IoT device management, environmental sensing, and web information retrieval.

The project follows **MVVM architecture** with a centralized ViewModel orchestrating 18 specialized modules, from speech processing to smart home control. It operates bilingually (English/Spanish) with full UI string internationalization.

### Key Stats

| Metric | Value |
|--------|-------|
| Language | Kotlin 2.0.21 |
| UI Framework | Jetpack Compose + Material 3 |
| Architecture | MVVM + StateFlow |
| Min SDK | 26 (Android 8.0) |
| Core Modules | 18 |
| AI Providers | 3 (Groq, Pollinations, Backend) |
| Voice Commands | 20+ |
| Intent Categories | 15 |
| Emotion Types Detected | 20 |
| IoT Automations | 8+ routines |
| Sensor Types | 12 |

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    JETPACK COMPOSE UI                     │
│  ChatScreen │ Settings │ Translator │ Lottery │ Crash    │
└──────────────────────────┬──────────────────────────────┘
                           │ StateFlow
┌──────────────────────────▼──────────────────────────────┐
│                   NexaViewModel                          │
│            (Central Orchestrator)                        │
└──┬────┬────┬────┬────┬────┬────┬────┬──────────────────┘
   │    │    │    │    │    │    │    │
┌──▼──┐┌▼───┐┌▼───┐┌▼───┐┌▼────┐┌▼───┐┌▼────┐┌────────▼┐
│Voice││IoT ││Sens││Vid ││Web  ││ ML ││Memo ││ Data    │
│Mgr ││Mgr ││Mgr ││Gen ││Srch ││Eng ││Mgr  ││ Layer   │
└──┬──┘└────┘└────┘└────┘└──┬──┘└──┬──┘└──┬──┘┌────────┐
   │                         │      │      │   │ Repo  │
┌──▼─────────────────────────▼──────▼──────▼───┤ Room   │
│        EXTERNAL SERVICES                        │ DataS │
│  Groq │ Pollinations │ DuckDuckGo │ ML Kit     └────────┘
│  TFLite │ OkHttp SSE │ Jsoup │ Gson
└─────────────────────────────────────────────────┘
```

### Architecture Pattern

- **Pattern**: MVVM (Model-View-ViewModel) with Unidirectional Data Flow
- **State Management**: Single `NexaUiState` data class exposed via `StateFlow`
- **Networking**: OkHttp + Server-Sent Events (SSE) for streaming AI responses
- **Local Storage**: Room Database (sessions/messages) + DataStore Preferences (settings)
- **No Dependency Injection**: Manual instantiation with Application context (planned upgrade to Hilt)

---

## Core Features

### AI Chat

- **Streaming Responses**: Real-time token streaming via SSE from Groq (Llama 3.3 70B) and Pollinations AI
- **Multi-Session**: Create, switch, delete, pin, rename, clone, archive, and share chat sessions
- **Markdown Rendering**: Full markdown support with code blocks, syntax highlighting, and HTML preview
- **Quick Actions**: Image generation, web page creation, logo design, code writing, vision analysis
- **"Surprise Me"**: Random creative prompts for inspiration
- **File Attachments**: Support for file attachment in conversations
- **PDF Export**: Export entire conversations to PDF documents
- **Copy/Share**: Copy or share individual messages

### Voice System

The most comprehensive voice system in any mobile AI assistant:

| Feature | Description |
|---------|-------------|
| **Speech-to-Text** | Android SpeechRecognizer with partial results and confidence scoring |
| **Text-to-Speech** | 6 voice types (3 male, 3 female), configurable speech rate |
| **Wake Word** | "Hey NEXA" / "Oye NEXA" via local audio spectral analysis |
| **Hands-Free Mode** | Continuous voice conversation loop with barge-in support |
| **Voice Commands** | 20+ commands (clear chat, new chat, export PDF, switch language/voice/theme, etc.) |
| **Bluetooth SCO** | Headset auto-routing and management |
| **Proximity Sensor** | Auto earpiece/speaker switching based on phone proximity |
| **Audio Focus** | Transient pause/resume with proper audio focus management |
| **Volume Boost** | Enhanced volume for hands-free mode |
| **Voice Visualization** | Real-time volume/activity feedback |
| **Multi-Language** | Spanish/English voice detection from audio spectral signatures |
| **Emotion from Voice** | Pitch, energy, and prosody-based emotion analysis |

### Web Search & Scraping

**NEW in v5.0** — Real-time information access without leaving the conversation.

| Capability | Description |
|------------|-------------|
| **DuckDuckGo Search** | API-based web search with instant answers and related topics |
| **HTML Scraping** | Full web page content extraction and cleaning |
| **News Search** | Dedicated news article search with category detection |
| **Fact-Checking** | Cross-source verification with confidence scoring |
| **Result Processing** | Automatic summarization, key point extraction, and formatting |
| **Voice Formatting** | Concise spoken summaries of web search results |
| **Cache System** | 5-minute result caching to avoid duplicate requests |
| **Language Detection** | Auto-detect Spanish/English content from scraped pages |

### On-Device ML Engine

All ML runs locally on the device — no data leaves the phone:

| Feature | Details |
|---------|---------|
| **Intent Classification** | 15 categories with confidence scoring and multi-intent detection |
| **Sentiment Analysis** | Valence-Arousal-Dominance (VAD) model with negation/intensifier handling |
| **Topic Tracking** | 12 topic categories with interest scoring and trend detection |
| **Preference Learning** | Response length, style, emotion, and activity pattern learning |
| **Coreference Resolution** | Pronoun/entity reference tracking across conversation |
| **Smart Reply** | Context-aware reply suggestions based on conversation state |
| **Entity Extraction** | Named entity recognition (dates, addresses, etc.) |
| **Language ID** | Automatic Spanish/English language identification |
| **Translation** | On-device phrase-based ES/EN translation |
| **Anomaly Detection** | Usage pattern monitoring with deviation alerts |
| **Federated Learning** | Privacy-preserving local model training simulation |

### Emotion Intelligence

**NEW in v5.0** — Deep emotional understanding with 20 emotion types:

| Emotion | Emoji | AI Response Tone |
|---------|-------|-----------------|
| Joy | 😊 | Warm and enthusiastic |
| Sadness | 😢 | Gentle and empathetic |
| Anger | 😠 | Calm and de-escalating |
| Fear | 😨 | Reassuring and supportive |
| Anxiety | 😰 | Calm and grounding |
| Confusion | 😕 | Clear and explanatory |
| Frustration | 😤 | Patient and solution-oriented |
| Excitement | 🤩 | Energetic and encouraging |
| Curiosity | 🧐 | Informative and detailed |
| Trust | 🤝 | Reliable and honest |
| + 10 more... | | |

**Features:**
- Bilingual emotion lexicon (English + Spanish)
- Negation and intensifier detection ("not happy" = negative)
- Valence-Arousal-Dominance (VAD) dimensional scoring
- Context-aware tone suggestions for AI responses
- Visual emoji feedback with confidence meters

### Episodic Memory

**NEW in v5.0** — Cross-session context retention with user consent:

```
Memory Types:
  FACT         → User stated a fact (name, preference)
  PREFERENCE   → User expressed a preference
  EVENT        → Something that happened
  CONTEXT      → Conversation context (topic, mood)
  DECISION     → A decision made during conversation
  REMINDER     → User asked to remember something
  PERSONAL     → Personal information shared
  SKILL_LEARNED → Something the AI learned about the user
```

- **Consent-based**: Explicit user consent required for memory storage
- **Smart Query**: Find relevant memories by keywords, type, emotion, or time
- **Auto-summarization**: Automatic memory consolidation when threshold reached
- **Memory Eviction**: LRU-based cleanup with importance scoring (max 500 memories)
- **Session Isolation**: Per-session memories with cross-session persistent facts

### User Profiling

**NEW in v5.0** — Deep personalization that improves over time:

| Profile Dimension | What It Tracks |
|-------------------|---------------|
| **Communication Style** | Casual, Formal, Technical, Creative, or Balanced |
| **Vocabulary Level** | Simple, Standard, Advanced, Technical, or Academic |
| **Response Length** | Brief, Medium, Detailed, or Comprehensive |
| **Technical Level** | Beginner to Expert |
| **Topic Interests** | Weighted interest scores across topics |
| **Interaction Patterns** | Time of day, session duration, feature frequency |
| **Formality Preference** | Informal, Mixed, or Formal |

The profile is built automatically from interaction patterns — no manual configuration needed.

### IoT & Smart Home

Full smart home management with BLE and WiFi Direct:

| Feature | Details |
|---------|---------|
| **BLE Scanning** | Bluetooth Low Energy device discovery |
| **WiFi Direct** | Peer-to-peer device connection |
| **Device Rooms** | Organize devices by room (living room, bedroom, kitchen, etc.) |
| **Scenes** | 7 pre-built scenes (cinema, night, reading, party, work, relax, morning) |
| **Automation Routines** | Good morning, good night, leave/arrive home, movie time |
| **Energy Monitoring** | Power consumption reports per device |
| **Scheduling** | Time-based, sunset, and sunrise triggers |
| **Voice Control** | Voice command processing for IoT operations |
| **Google Home/Alexa** | Integration stubs for smart speakers |

### Sensor Hub

12 environmental sensors with contextual AI suggestions:

| Sensor | Uses |
|--------|------|
| **Accelerometer** | Activity classification (still/walking/running/driving) |
| **Gyroscope** | Rotation and movement detection |
| **Light Sensor** | Dark mode auto-detection, sleep tracking |
| **Proximity** | Earpiece/speaker auto-switching |
| **GPS** | Location, geofencing, known places |
| **Pressure** | Altitude estimation |
| **Step Counter** | Fitness tracking |
| **Temperature/Humidity** | Environmental comfort |
| **Heart Rate** | Health monitoring zones |
| **Screen State** | Usage pattern tracking |
| **Headphone Detection** | Audio routing |
| **NFC** | Tag reading and automation triggers |

**AI-Driven Features:**
- Driving mode detection (5 confidence levels) with safety recommendations
- Sleep pattern detection (7 factors, 4 stages)
- Contextual suggestions (8 types: driving, good night, arrive home, battery, etc.)
- Known places with geofencing (home, work, gym)

### Translator

Real-time voice-to-voice translation supporting **20 languages**:

`Spanish, English, Portuguese, French, German, Italian, Chinese, Japanese, Korean, Russian, Arabic, Hindi, Turkish, Thai, Vietnamese, Indonesian, Dutch, Polish, Swedish, Ukrainian`

- Speech recognition in source language
- Auto-translation via MyMemory API
- Text-to-speech playback in target language
- Language swap with one tap
- Translation history (last 20 entries)

### Video Generation

Multi-provider AI video generation:

| Provider | Status |
|----------|--------|
| **Pollinations** | Free, no API key |
| **Runway ML** | Key required |
| **Stability AI** | Key required |
| **Luma** | Key required |
| **Kling** | Key required |

- 10 video styles: cinematic, anime, realistic, abstract, vintage, sci-fi, nature, slow motion, timelapse, watercolor
- Configurable duration (3s, 5s, 10s) and aspect ratio (16:9, 9:16, 1:1, 4:3)
- Progress tracking and gallery management

---

## Module Inventory

| Module | Package | Purpose | Status |
|--------|---------|---------|--------|
| NexaViewModel | `viewmodel` | Central orchestrator, all app logic | Stable |
| NexaRepository | `data` | SSE streaming to Groq/Pollinations/Backend | Stable |
| NexaDatabase | `data` | Room DB for sessions and messages | Stable |
| SettingsStore | `data` | DataStore for user preferences | Stable |
| SessionStore | `data` | Session persistence (Room + DataStore) | Stable |
| LocationStore | `data` | FusedLocation + Nominatim geocoding | Stable |
| UserStore | `data` | Local auth with password hashing | Stable |
| LotteryRepository | `data` | Lottery API calls | Stable |
| SpeechManager | `voice` | TTS + STT, audio focus, Bluetooth SCO | Stable |
| VoiceEnhancer | `voice` | Wake word, emotion analysis, language detection | Stable |
| NaturalConversationEngine | `voice` | Turn-taking, backchanneling, topic tracking | Stable |
| OnDeviceMLEngine | `ml` | Intent, sentiment, topic, preference learning | Stable |
| EnhancedEmotionAnalyzer | `ml` | 20-emotion detection with VAD scoring | **NEW v5.0** |
| UserProfileManager | `ml` | Deep user profiling and personalization | **NEW v5.0** |
| EpisodicMemoryManager | `memory` | Cross-session memory with consent | **NEW v5.0** |
| IoTManager | `iot` | BLE/WiFi Direct, rooms, scenes, automation | Stable |
| SensorManager | `sensors` | 12 sensor types, GPS, driving/sleep detection | Stable |
| VideoGenerator | `media` | Multi-provider video generation | Stable |
| WebSearchManager | `web` | DuckDuckGo search + HTML scraping + fact-checking | **NEW v5.0** |
| WebResultProcessor | `web` | Result summarization and formatting | **NEW v5.0** |

---

## Recent Improvements (v5.0)

### Commit: `fb55ad1` — Full English Translation + New AI Modules

**17 files changed | 1,716 lines added | 5 new modules created**

#### Translation (12 files)
- Translated all hardcoded Spanish strings to English across the entire codebase
- Added missing `translator` key to English strings map
- Bilingual voice responses properly localized in NexaViewModel
- UI strings in CrashActivity, TranslatorScreen, ChatScreen fully translated

#### New Modules

| Module | Lines | Key Feature |
|--------|-------|-------------|
| `WebSearchManager` | ~350 | DuckDuckGo API + HTML scraping + news search + fact-checking + cache |
| `WebResultProcessor` | ~250 | Result summarization, key point extraction, chat/voice formatting |
| `EpisodicMemoryManager` | ~350 | Cross-session memory with consent, auto-summarization, smart query |
| `EnhancedEmotionAnalyzer` | ~300 | 20 emotion types, bilingual lexicon, VAD scoring, tone suggestions |
| `UserProfileManager` | ~300 | Vocabulary analysis, style detection, topic interest tracking |

#### Dependencies
- Added `org.jsoup:jsoup:1.18.3` for web scraping support

---

## Improvement Roadmap

### High Priority

These are the most impactful improvements to implement next:

#### 1. Wire New Modules into NexaViewModel

The 5 new modules (WebSearchManager, WebResultProcessor, EpisodicMemoryManager, EnhancedEmotionAnalyzer, UserProfileManager) are created but **not yet integrated** into the main ViewModel. This is the single most impactful change.

```
Target: Connect NLP intent detection → WebSearchManager → ResultProcessor → AI response
        Connect EmotionAnalyzer → tone adjustment in prompts
        Connect MemoryManager → context injection in AI prompts
        Connect UserProfileManager → personalized prompt generation
```

#### 2. Dependency Injection (Hilt/Koin)

Current state: Manual instantiation with Application context. This creates tight coupling and makes testing difficult.

```
Target: Implement Hilt for Android
        - @HiltAndroidApp on Application
        - @HiltViewModel on NexaViewModel
        - @Inject for all managers
        - Enable proper unit testing with mocks
```

#### 3. ViewModel Modularization

The NexaViewModel is a 2,000+ line monolith handling all app logic. It should be split into focused use cases.

```
Target: Extract use cases:
        - ChatUseCase (send message, receive response)
        - VoiceUseCase (STT/TTS, commands, hands-free)
        - IoTUseCase (device control, automation)
        - SearchUseCase (web search, fact-checking)
        - MemoryUseCase (episodic memory, user profile)
```

#### 4. Unit & Integration Tests

Currently only test dependencies are declared — no actual test code exists.

```
Target: Minimum test coverage:
        - OnDeviceMLEngine: Intent classification, sentiment analysis
        - WebSearchManager: Search parsing, caching
        - EpisodicMemoryManager: Store, query, eviction
        - EnhancedEmotionAnalyzer: Emotion detection accuracy
        - NexaViewModel: Core chat flow, voice commands
```

#### 5. CI/CD Pipeline

No automation exists for building, testing, or deploying.

```
Target: GitHub Actions workflow:
        - Build on push/PR
        - Run unit tests
        - Lint with ktlint
        - Generate APK artifacts
        - Deploy to Firebase App Distribution
```

### Medium Priority

#### 6. ProGuard Rules for Jsoup

The new Jsoup dependency needs proper ProGuard/R8 rules for release builds.

#### 7. Offline-First Architecture

Currently requires network for all AI interactions. Implement local LLM fallback via TensorFlow Lite.

#### 8. End-to-End Encryption

Chat history is stored in Room DB without encryption. Implement SQLCipher for sensitive data.

#### 9. Multimodal Input

Support simultaneous voice + image input for contextual visual understanding.

#### 10. Accessibility

Add TalkBack support, content descriptions, and scalable UI for visually impaired users.

### Future Vision

The following features represent the long-term vision for NEXA PRO:

#### Deep Context Tracking
Follow complex conversations across hundreds of messages without losing the thread. Use attention mechanisms and hierarchical memory to maintain coherence over very long sessions.

#### Real-Time Data Access
Direct connections to live databases and APIs (weather, news, finance, sports, events). Eliminate assumptions and guarantee information precision with real-time verification.

#### Multimodal Generation
Beyond text — generate and read code, diagrams, charts, and graphics directly in the conversation interface. Create visual content without leaving the chat.

#### Dialect & Regional Support
Robust support for Spanish dialects and regional expressions (Mexican, Argentine, Colombian, etc.) and English variants. Improve naturalness for users across different Spanish-speaking regions.

#### Privacy & Ethics Dashboard
Automatic audit trail with explainable AI decisions. Show users why the AI responded a certain way, what data was used, and provide transparent privacy controls.

#### Emotional Continuity
Responses that reflect deep empathy and adapt tone to the user's emotional state across the entire conversation, not just individual messages.

#### Continuous Learning
Update knowledge and preferences with each interaction without requiring full model retraining. Implement lightweight on-device fine-tuning.

#### Document Processing
Read and analyze PDFs, Word documents, spreadsheets, and presentations directly in the conversation. Extract key information and answer questions about uploaded documents.

#### Chart & Graph Generation
Generate data visualizations (bar charts, line charts, pie charts, etc.) directly in the chat interface without external tools.

#### Database Querying
Execute SQL queries against connected databases and display results in formatted tables within the conversation.

#### API Interaction
Interact with any REST API on command — fetch data, trigger actions, and display structured results.

---

## Tech Stack

### Android

| Technology | Version | Purpose |
|------------|---------|---------|
| Kotlin | 2.0.21 | Primary language |
| Jetpack Compose | BOM 2024.12.01 | UI framework |
| Material 3 | BOM | Design system |
| Navigation Compose | 2.8.5 | In-app navigation |
| ViewModel + StateFlow | Lifecycle 2.8.7 | State management |
| Room | 2.6.1 | Local database |
| DataStore | 1.1.1 | Preferences storage |
| KSP | 2.0.21-1.0.27 | Annotation processing |

### Networking & Data

| Technology | Version | Purpose |
|------------|---------|---------|
| OkHttp | 4.12.0 | HTTP client |
| OkHttp SSE | 4.12.0 | Server-Sent Events streaming |
| Gson | 2.11.0 | JSON serialization |
| Jsoup | 1.18.3 | Web scraping |
| Coil | 2.7.0 | Image loading |

### AI & Machine Learning

| Technology | Version | Purpose |
|------------|---------|---------|
| Google ML Kit Language ID | 16.1.0 | Language identification |
| Google ML Kit Entity Extraction | 16.0.0-beta5 | Named entity recognition |
| Google ML Kit Smart Reply | 17.0.4 | Reply suggestions |
| Google ML Kit Translate | 17.0.3 | On-device translation |
| TensorFlow Lite | 2.16.1 | On-device ML inference |
| TFLite Support | 0.4.4 | TFLite utilities |

### Location & Sensors

| Technology | Version | Purpose |
|------------|---------|---------|
| Play Services Location | 21.3.0 | GPS + fused location |
| Coroutines Play Services | 1.9.0 | Async location |

### Build

| Technology | Version |
|------------|---------|
| Gradle | 8.12 |
| Java | 17 |
| Compile SDK | 35 |
| Min SDK | 26 |
| Target SDK | 35 |

---

## Project Structure

```
nexa-ai-android/
├── app/src/main/java/com/nexa/ai/
│   ├── MainActivity.kt              # Entry point, permissions, camera
│   ├── CrashActivity.kt             # Crash recovery screen
│   ├── CrashHandler.kt              # Global exception handler
│   ├── viewmodel/
│   │   ├── NexaViewModel.kt         # Central orchestrator (2000+ lines)
│   │   ├── Models.kt                # Data classes, enums, UI state
│   │   ├── NexaUiState.kt           # State definitions
│   │   └── AppLanguage.kt           # Language enum
│   ├── data/
│   │   ├── NexaRepository.kt        # Network: SSE streaming
│   │   ├── NexaDatabase.kt          # Room DB
│   │   ├── SettingsStore.kt         # DataStore preferences
│   │   ├── SessionStore.kt          # Session persistence
│   │   ├── LocationStore.kt         # GPS + geocoding
│   │   ├── UserStore.kt             # Local auth
│   │   └── LotteryRepository.kt     # Lottery API
│   ├── voice/
│   │   ├── SpeechManager.kt         # TTS + STT
│   │   ├── VoiceEnhancer.kt         # Wake word, emotions
│   │   └── NaturalConversationEngine.kt  # Turn-taking
│   ├── ml/
│   │   ├── OnDeviceMLEngine.kt      # Intent, sentiment, topics
│   │   ├── EnhancedEmotionAnalyzer.kt   # 20 emotions + VAD
│   │   └── UserProfileManager.kt    # Deep profiling
│   ├── memory/
│   │   └── EpisodicMemoryManager.kt # Cross-session memory
│   ├── web/
│   │   ├── WebSearchManager.kt      # Search + scraping + fact-check
│   │   └── WebResultProcessor.kt    # Summarization + formatting
│   ├── iot/
│   │   └── IoTManager.kt            # BLE, WiFi, scenes, automation
│   ├── sensors/
│   │   └── SensorManager.kt         # 12 sensors, GPS, driving/sleep
│   ├── media/
│   │   └── VideoGenerator.kt        # Multi-provider video gen
│   └── ui/
│       ├── NexaChatScreen.kt        # Navigation router
│       ├── ChatScreen.kt            # Main chat UI
│       ├── ChatComponents.kt        # Reusable chat components
│       ├── LoginScreen.kt           # Auth screens
│       ├── SettingsScreen.kt        # Settings UI
│       ├── LotteryScreen.kt         # Lottery UI
│       ├── TranslatorScreen.kt      # Live translator
│       ├── NexaStrings.kt           # Bilingual strings
│       ├── MarkdownText.kt          # Markdown renderer
│       ├── AdaptiveLayout.kt        # Responsive layouts
│       └── Theme.kt                 # Material 3 theming
├── src/                             # Next.js 15 web backend
├── android/                         # Capacitor wrapper
├── android-native-app/              # Older native variant
├── app/build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## Getting Started

### Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or newer
- **JDK** 17
- **Android SDK** with API Level 35
- A physical device or emulator with Android 8.0+

### Build & Run

```bash
# Clone the repository
git clone https://github.com/angelpipo1968/nexa-ai-android.git
cd nexa-ai-android

# Open in Android Studio
# File → Open → select the project root

# Build and run
./gradlew assembleDebug

# Or install directly
./gradlew installDebug
```

### Free Mode (No API Key Required)

NEXA PRO works out of the box using **Pollinations AI** — a free AI provider that requires no API key. Simply build and run.

### PRO Mode (Groq API)

For faster responses with Llama 3.3 70B:

1. Get a free API key at [console.groq.com](https://console.groq.com)
2. Open NEXA PRO → Settings → API Key
3. Enter your Groq API key
4. PRO mode activates automatically

---

## API Providers

| Provider | Model | Auth | Speed | Cost |
|----------|-------|------|-------|------|
| **Pollinations AI** | OpenAI | None | Normal | Free |
| **Groq** | Llama 3.3 70B | Free API key | 10x faster | Free tier |
| **NEXA Backend** | Various | Backend auth | Normal | N/A |

All providers use **Server-Sent Events (SSE)** for real-time streaming responses.

---

## Permissions

| Permission | Purpose |
|------------|---------|
| `INTERNET` | AI communication, web search |
| `RECORD_AUDIO` | Voice recognition |
| `ACCESS_FINE_LOCATION` | GPS positioning |
| `ACCESS_COARSE_LOCATION` | Approximate location |
| `BLUETOOTH` / `BLUETOOTH_CONNECT` | Headset + BLE scanning |
| `CAMERA` | Vision/image capture |
| `POST_NOTIFICATIONS` | Push notifications |
| `MODIFY_AUDIO_SETTINGS` | Audio routing |
| `FOREGROUND_SERVICE` | Background voice mode |
| `WAKE_LOCK` | Keep CPU on during voice |
| `ACCESS_NOTIFICATION_POLICY` | DND bypass for hands-free |

---

## Security & Privacy

### Current Measures
- **HTTPS only**: All network communication encrypted
- **Local auth**: Password hashing for user accounts
- **No data collection**: No analytics or tracking
- **Consent-based memory**: Episodic memory requires explicit consent
- **DataStore encryption**: Preferences stored securely
- **Crash logs**: Saved locally, never transmitted automatically

### Planned Improvements
- SQLCipher for encrypted chat database
- Biometric authentication (fingerprint/face)
- Automatic session timeout
- Data export and account deletion
- Privacy audit dashboard
- Explainable AI decision logging

---

## License

This project is licensed under the MIT License. See the LICENSE file for details.

---

<div align="center">

**Built with Kotlin, Jetpack Compose, and a lot of ambition.**

NEXA PRO — Your AI Assistant, Evolving.

</div>
