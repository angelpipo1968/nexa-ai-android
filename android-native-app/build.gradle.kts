plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Kotlin is built-in with AGP 9.0+
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.nexa.ai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nexa.ai"
        minSdk = 31
        targetSdk = 36
        versionCode = 52
        versionName = "5.2"
        buildConfigField("String", "API_BASE_URL", "\"https://www.nexa-ai.dev\"")
        
        // Enable multidex for Android < 14 compatibility
        multiDexEnabled = true
    }

    signingConfigs {
        create("release") {
            storeFile = file("../android/nexa-release.keystore")
            storePassword = "NexaAI2024!Release"
            keyAlias = "nexa"
            keyPassword = "NexaAI2024!Release"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("field") {
            initWith(getByName("release"))
            applicationIdSuffix = ".field"
            versionNameSuffix = "-field"
            isDebuggable = false
        }
    }

    // DISABLE ABI SPLITS — single universal APK
    // splits.abi is disabled to prevent 14+ .dex files packaging bug on Android 16
    splits {
        abi {
            isEnable = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = false  // FIX: useLegacyPackaging=true causes OOM on Android 16
        }
    }
    
    // dexOptions removed — obsolete since AGP 8.0+; the build system handles DEX optimization automatically
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.16.0")  // Updated for API 36
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    // Multidex for large method count
    implementation("androidx.multidex:multidex:2.0.1")

    // Compose — updated BOM for API 36
    implementation(platform("androidx.compose:compose-bom:2025.04.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class:1.3.1")
    implementation("androidx.compose.material3.adaptive:adaptive")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Network
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // CameraX — updated for API 36
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    // Encrypted SharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // ExoPlayer (video)
    implementation("androidx.media3:media3-exoplayer:1.3.0")
    implementation("androidx.media3:media3-ui:1.3.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Location
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-android-compiler:2.59.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")

    // Android Auto
    implementation("androidx.car.app:app:1.4.0")

    // Nexa SDK
    implementation("ai.nexa:core:0.0.24")

    // ML Kit
    implementation("com.google.mlkit:language-id:17.0.0")
    implementation("com.google.mlkit:translate:17.0.3")
}
