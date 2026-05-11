# Add project specific ProGuard rules here.
-keepattributes Signature
-keepattributes *Annotation*

# Keep Gson classes
-keep class com.google.gson.** { *; }
-keep class com.nexa.ai.data.** { *; }

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
