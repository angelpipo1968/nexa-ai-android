# Add project specific ProGuard rules here.
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Keep Gson classes
-keep class com.google.gson.** { *; }
-keep class com.google.gson.annotations.** { *; }
-keep class com.google.gson.internal.** { *; }
-keep class com.google.gson.reflect.** { *; }
-keep class com.google.gson.stream.** { *; }
-keep class com.nexa.ai.data.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep SSE event models
-keep class com.nexa.ai.data.StreamEvent { *; }
-keep class com.nexa.ai.data.StreamEvent$* { *; }

# Keep DataStore models
-keep class com.nexa.ai.data.PersistedUser { *; }
-keep class com.nexa.ai.data.PersistedCredential { *; }
-keep class com.nexa.ai.data.PersistedSession { *; }
-keep class com.nexa.ai.data.PersistedMessage { *; }
-keep class com.nexa.ai.data.UpdateInfo { *; }

# Keep ViewModel state
-keep class com.nexa.ai.viewmodel.NexaUiState { *; }
-keep class com.nexa.ai.viewmodel.Message { *; }
-keep class com.nexa.ai.viewmodel.ChatSession { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Audio focus & Bluetooth
-dontwarn android.media.AudioFocusRequest
-keep class android.media.AudioFocusRequest { *; }
-keep class android.media.AudioAttributes$Builder { *; }

# ─── Jsoup (Web Scraping) ────────────────────────────────────
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**
-keepclassmembers class org.jsoup.nodes.* { *; }
-keepclassmembers class org.jsoup.select.* { *; }

# ─── Hilt (Dependency Injection) ─────────────────────────────
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class com.nexa.ai.di.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ─── Web Search Models ───────────────────────────────────────
-keep class com.nexa.ai.web.SearchResult { *; }
-keep class com.nexa.ai.web.ScrapedContent { *; }
-keep class com.nexa.ai.web.NewsResult { *; }
-keep class com.nexa.ai.web.ProcessedResult { *; }

# ─── Memory Models ───────────────────────────────────────────
-keep class com.nexa.ai.memory.MemoryEntry { *; }
-keep class com.nexa.ai.memory.MemoryType { *; }
-keep class com.nexa.ai.memory.MemoryQuery { *; }
-keep class com.nexa.ai.memory.MemoryStats { *; }

# ─── Emotion Models ──────────────────────────────────────────
-keep class com.nexa.ai.ml.EmotionProfile { *; }
-keep class com.nexa.ai.ml.Emotion { *; }

# ─── User Profile Models ─────────────────────────────────────
-keep class com.nexa.ai.ml.UserProfile { *; }
-keep class com.nexa.ai.ml.CommunicationStyle { *; }
-keep class com.nexa.ai.ml.VocabularyLevel { *; }
-keep class com.nexa.ai.ml.ResponseLength { *; }
-keep class com.nexa.ai.ml.TechnicalLevel { *; }
-keep class com.nexa.ai.ml.FormalityLevel { *; }
-keep class com.nexa.ai.ml.InteractionPatterns { *; }

# ─── VoiceCommandsHandler ────────────────────────────────────
-keep class com.nexa.ai.viewmodel.VoiceCommandsHandler { *; }
-keep class com.nexa.ai.viewmodel.VoiceCommandsHandler$* { *; }

# ─── NexaApplication (Hilt) ──────────────────────────────────
-keep class com.nexa.ai.NexaApplication { *; }
-keep class com.nexa.ai.NexaApplication$* { *; }
-keep class com.nexa.ai.NexaApplication.** { *; }

# ─── Offline-First Models ───────────────────────────────────
-keep class com.nexa.ai.data.local.PendingMessageEntity { *; }
-keep class com.nexa.ai.data.local.CachedResponseEntity { *; }
-keep class com.nexa.ai.data.local.CachedSearchEntity { *; }
-keep class com.nexa.ai.data.NetworkMonitor { *; }
