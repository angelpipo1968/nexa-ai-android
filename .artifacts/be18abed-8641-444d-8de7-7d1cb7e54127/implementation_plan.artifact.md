# Implementation Plan - Fix Gradle Sync Error (KSP/Hilt incompatibility)

The project is experiencing a Gradle sync error: `Unable to load class 'com.google.devtools.ksp.gradle.KspTaskJvm'`. This is caused by an incompatibility between the KSP Gradle plugin and the Hilt Gradle plugin within the environment of Android Gradle Plugin (AGP) 9.2.1 and Gradle 9.4.1.

Specifically, AGP 9.0+ introduced "Built-in Kotlin" support and a new DSL. The current project configuration tries to opt-out of these features but uses mismatched versions of Kotlin, KSP, and Hilt, leading to the loading error for internal KSP classes that Hilt expects.

## User Review Required

> [!IMPORTANT]
> This plan involves modernizing the project to use AGP 9.0's "Built-in Kotlin" support. This is the recommended path for projects using AGP 9.x. It will involve removing the explicit `kotlin-android` plugin and updating KSP and Hilt to their 2026 stable versions.

## Proposed Changes

### Build Configuration

#### [MODIFY] [gradle.properties](file:///C:/Users/pipog/Downloads/nexa-ai-android-main/nexa-ai-android-main/gradle.properties)
- Enable built-in Kotlin: `android.builtInKotlin=true`
- Enable new DSL: `android.newDsl=true`

#### [MODIFY] [build.gradle.kts](file:///C:/Users/pipog/Downloads/nexa-ai-android-main/nexa-ai-android-main/build.gradle.kts) (root)
- Remove `id("org.jetbrains.kotlin.android")` as it is now built-in.
- Update `com.google.devtools.ksp` to `2.2.10-2.0.2` (matching AGP 9.0 default) or `2.3.9` if compatible.
- Update `com.google.dagger.hilt.android` to `2.59.2`.
- Align `org.jetbrains.kotlin.plugin.compose` version with the built-in Kotlin version (`2.2.10`).

#### [MODIFY] [build.gradle.kts](file:///C:/Users/pipog/Downloads/nexa-ai-android-main/nexa-ai-android-main/android-native-app/build.gradle.kts)
- Remove `id("org.jetbrains.kotlin.android")`.
- Update Hilt dependencies to `2.59.2`.

## Verification Plan

### Automated Tests
- Run Gradle Sync in Android Studio.
- Execute `./gradlew :android-native-app:assembleDebug` to verify the build.

### Manual Verification
- Verify that KSP generated files are correctly created in `build/generated/ksp`.
- Verify that Hilt injection works by running the app (if a device is available).
