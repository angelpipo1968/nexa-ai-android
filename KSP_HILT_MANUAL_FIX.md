# 🛠️ MANUAL: Corregir conflicto KSP + Hilt

## PROBLEMA
El error indica que KSP y Hilt están en diferentes scopes:
- KSP está en el sub-proyecto `:android-native-app`
- Hilt está en el scope raíz

## SOLUCIÓN

### 1. Modificar android/build.gradle
```gradle
plugins {
    id 'com.android.application' version '8.1.4' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.10' apply false
    id 'com.google.devtools.ksp' version '1.9.10-1.0.13' apply true  // MOVER A RAÍZ
    id 'dagger.hilt.android.plugin' version '2.48.1' apply true      // MOVER A RAÍZ
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

subprojects {
    afterEvaluate { project ->
        if (project.hasProperty('android')) {
            android {
                compileOptions {
                    sourceCompatibility JavaVersion.VERSION_17
                    targetCompatibility JavaVersion.VERSION_17
                }
                kotlinOptions {
                    jvmTarget = '17'
                }
            }
        }
    }
}
```

### 2. Modificar android/app/build.gradle
```gradle
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
    // QUITAR: id 'com.google.devtools.ksp'
    // QUITAR: id 'dagger.hilt.android.plugin'
}

android {
    namespace 'com.nexa.ai'
    compileSdk 34
    
    defaultConfig {
        applicationId 'com.nexa.ai'
        minSdk 21
        targetSdk 34
        versionCode 1
        versionName '1.0'
        
        testInstrumentationRunner 'androidx.test.runner.AndroidJUnitRunner'
        vectorDrawables {
            useSupportLibrary true
        }
    }
    
    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = '17'
    }
    
    buildFeatures {
        viewBinding true
    }
    
    packaging {
        resources {
            excludes += '/META-INF/{AL2.0,LGPL2.1}'
        }
    }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    implementation 'androidx.lifecycle:lifecycle-livedata-ktx:2.6.2'
    implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2'
    implementation 'androidx.navigation:navigation-fragment-ktx:2.7.5'
    implementation 'androidx.navigation:navigation-ui-ktx:2.7.5'
    
    // KSP y HILL en el scope correcto
    ksp 'com.google.devtools.ksp:symbol-processing-api:1.9.10-1.0.13'
    implementation 'com.google.dagger:hilt-android:2.48.1'
    ksp 'com.google.dagger:hilt-compiler:2.48.1'
    
    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
}
```

### 3. Limpiar y reconstruir
```cmd
cd android
gradlew clean
gradlew --refresh-dependencies
gradlew build
```

### 4. Si aún falla, intenta estas soluciones alternativas:

#### **Opción A: Usar solo KSP (sin Hilt)**
```gradle
// En android/build.gradle
plugins {
    id 'com.android.application' version '8.1.4' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.10' apply false
    id 'com.google.devtools.ksp' version '1.9.10-1.0.13' apply true
    // QUITAR: id 'dagger.hilt.android.plugin'
}
```

#### **Opción B: Usar KAPT en lugar de KSP**
```gradle
// En android/build.gradle
plugins {
    id 'com.android.application' version '8.1.4' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.10' apply false
    id 'org.jetbrains.kotlin.kapt' version '1.9.10' apply true
    id 'dagger.hilt.android.plugin' version '2.48.1' apply true
}

// En android/app/build.gradle
dependencies {
    implementation 'com.google.dagger:hilt-android:2.48.1'
    kapt 'com.google.dagger:hilt-compiler:2.48.1'
}
```

#### **Opción C: Crear proyecto simple (sin Hilt)**
Si no necesitas Hilt, puedes crear un proyecto más simple:
```gradle
// android/app/build.gradle simplificado
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.nexa.ai'
    compileSdk 34
    
    defaultConfig {
        applicationId 'com.nexa.ai'
        minSdk 21
        targetSdk 34
        versionCode 1
        versionName '1.0'
    }
    
    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = '17'
    }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
}
```

## 🎉 ¡LISTO!

Después de aplicar estas correcciones, el Gradle Sync debería funcionar correctamente y podrás ejecutar la aplicación en Android Studio.

**¿Necesitas ayuda con algún paso específico?**