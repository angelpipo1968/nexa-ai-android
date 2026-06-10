# Fix Unresolved reference 'kotlinOptions' in AGP 9.2.1

The project is using Android Gradle Plugin (AGP) 9.2.1, which includes built-in Kotlin support. In AGP 9.0+, the `kotlinOptions` block inside the `android` extension has been removed. Kotlin compiler options should now be configured using the `kotlin.compilerOptions` DSL at the top level of the module-level `build.gradle.kts` file.

## Proposed Changes

### [android-native-app](file:///C:/Users/pipog/Downloads/nexa-ai-android-main/nexa-ai-android-main/android-native-app)

#### [MODIFY] [build.gradle.kts](file:///C:/Users/pipog/Downloads/nexa-ai-android-main/nexa-ai-android-main/android-native-app/build.gradle.kts)

- Remove the `kotlinOptions` block from the `android` extension.
- Since `compileOptions.targetCompatibility` is already set to `JavaVersion.VERSION_17`, the Kotlin `jvmTarget` will default to 17 automatically with built-in Kotlin. Thus, explicitly setting it is redundant but can be moved to the new DSL if desired. I will remove it for simplicity as recommended by the migration guide.

## Verification Plan

### Automated Tests
- Run Gradle sync to ensure the "Unresolved reference 'kotlinOptions'" error is resolved.
- Run `./gradlew :android-native-app:assembleDebug` to verify the build still succeeds and correctly targets JVM 17.

### Manual Verification
- Verify in Android Studio that the project syncs successfully.
