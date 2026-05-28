# ============================================================
# NEXA AI - ProGuard Rules para Google Play
# Optimizado para Capacitor + WebView + Firebase
# ============================================================

# Preservar números de línea para debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================================
# Capacitor WebView - NO ofuscar interfaces JavaScript
# ============================================================
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class com.getcapacitor.** {
    *;
}
-keep class com.getcapacitor.** { *; }
-keep @com.getcapacitor.annotation.CapacitorPlugin class * {
    @com.getcapacitor.annotation.ActivityMethod <methods>;
    @com.getcapacitor.annotation.Permission <methods>;
    *;
}

# ============================================================
# Firebase Cloud Messaging
# ============================================================
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**

# ============================================================
# AndroidX y Soporte
# ============================================================
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-dontwarn androidx.**

# ============================================================
# WebView - Preservar comunicación JS
# ============================================================
-keepclassmembers class * extends android.webkit.WebViewClient {
    <methods>;
}
-keepclassmembers class * extends android.webkit.WebChromeClient {
    <methods>;
}
-keep class * extends android.webkit.WebView { *; }

# ============================================================
# Modelos y datos JSON
# ============================================================
-keep class com.nexa.ai.** { *; }
-keepclassmembers class com.nexa.ai.** {
    *;
}

# ============================================================
# Retrofit/OkHttp (si se usa)
# ============================================================
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# ============================================================
# Reglas generales
# ============================================================
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose
