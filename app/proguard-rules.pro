# Add project specific ProGuard rules here.
-keepattributes Signature
-keepattributes *Annotation*

# Keep Gson classes
-keep class com.google.gson.** { *; }
-keep class com.nexa.ai.data.** { *; }

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
