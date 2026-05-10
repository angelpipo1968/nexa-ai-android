# ProGuard rules for Nexa AI
-keep class com.nexa.ai.data.** { *; }
-keep class com.nexa.ai.viewmodel.** { *; }
-keepattributes *Annotation*

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
