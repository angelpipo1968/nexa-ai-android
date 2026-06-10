# 🛠️ MANUAL: Solucionar error KSP TaskJvm

## PROBLEMA
El error indica que KSP (Kotlin Symbol Processing) no está disponible:
```
Unable to load class 'com.google.devtools.ksp.gradle.KspTaskJvm'
```

## CAUSAS COMUNES
1. Red interrumpida durante descarga de KSP
2. Caché de Gradle corrupto
3. Versiones incompatibles
4. Java JDK incorrecto

## SOLUCIONES

### Solución 1: Usar KAPT en lugar de KSP (RECOMENDADO)

#### Modificar android/build.gradle:
```gradle
plugins {
    id 'com.android.application' version '8.1.4' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.10' apply false
    id 'org.jetbrains.kotlin.kapt' version '1.9.10' apply true  // CAMBIAR KSP POR KAPT
    id 'dagger.hilt.android.plugin' version '2.48.1' apply true
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
```

#### Modificar android/app/build.gradle:
```gradle
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
    id 'org.jetbrains.kotlin.kapt'  // AÑADIR KAPT
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
    
    // KAPT EN LUGAR DE KSP
    kapt 'com.google.dagger:hilt-compiler:2.48.1'
    implementation 'com.google.dagger:hilt-android:2.48.1'
    
    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
}
```

### Solución 2: Eliminar Hilt por completo

Si no necesitas Hilt, puedes eliminarlo por completo:

#### android/build.gradle:
```gradle
plugins {
    id 'com.android.application' version '8.1.4' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.10' apply false
}
```

#### android/app/build.gradle:
```gradle
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
        
        testInstrumentationRunner 'androidx.test.runner.AndroidJUnitRunner'
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
    
    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
}
```

### Solución 3: Forzar descarga de dependencias

En Android Studio:
1. File > Settings > Build, Execution, Deployment > Build Tools > Gradle
2. Desactiva "Offline work"
3. File > Invalidate Caches / Restart
4. Espera a que se reinicie
5. Build > Clean Project
6. Build > Rebuild Project

### Solución 4: Matar Gradle Daemons

```cmd
# En Windows (Command Prompt)
cd android
gradlew --stop
gradlew clean
gradlew build
```

### Solución 5: Verificar Java JDK

```cmd
# Verificar versión de Java
java -version
javac -version

# Debe ser JDK 17 o superior
```

## 🎉 ¡LISTO!

Después de aplicar estas correcciones, el Gradle Sync debería funcionar correctamente.

**¿Necesitas ayuda con algún paso específico?**