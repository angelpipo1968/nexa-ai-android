# Implementation Plan - Fix 'Unresolved reference: kotlinOptions'

The project is using Android Gradle Plugin (AGP) 9.2.1, which introduces built-in Kotlin support. In this version, the `kotlin-android` plugin is no longer required, and the legacy `android.kotlinOptions {}` DSL has been removed.

## User Review Required

> [!IMPORTANT]
> This change removes the legacy `kotlinOptions` block. The Kotlin JVM target will now be automatically synchronized with the `android.compileOptions.targetCompatibility` setting (currently set to Java 17), which is the standard behavior in AGP 9.0+.

## Proposed Changes

### [Build Configuration]

#### [MODIFY] [android-native-app/build.gradle.kts](file:///C:/Users/pipog/Downloads/nexa-ai-android-main/nexa-ai-android-main/android-native-app/build.gradle.kts)
- Remove the `kotlinOptions` block.
- The `jvmTarget = "17"` configuration is redundant as it matches `targetCompatibility = JavaVersion.VERSION_17`.

#### [MODIFY] [build.gradle.kts](file:///C:/Users/pipog/Downloads/nexa-ai-android-main/nexa-ai-android-main/build.gradle.kts) (Root)
- Remove `id("org.jetbrains.kotlin.kapt")` as it is incompatible with AGP 9.0+ built-in Kotlin and the project is already using KSP.

## Verification Plan

### Automated Tests
- Run `./gradlew :android-native-app:assembleDebug` to verify the project builds without the unresolved reference error.
- Run `gradle sync` (via IDE) to ensure the DSL is correctly recognized.

### Manual Verification
- Verify that no other "Unresolved reference" errors appear in the build logs related to Kotlin configuration.
