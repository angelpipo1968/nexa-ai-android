@echo off
echo 🔧 SOLUCIÓN DEFINITIVA: Error KSP TaskJvm
echo El problema es que KSP no está disponible en el classpath
echo.

:: Verificar directorio
if not exist "android" (
    echo ❌ No se encontró directorio android
    pause
    exit /b 1
)

echo ✅ Directorio android encontrado
echo.

:: Solución 1: Forzar descarga de KSP
echo 📥 Solución 1: Forzar descarga de dependencias KSP...
echo.

:: Limpiar caché de Gradle
echo 🧹 Limpiando caché de Gradle...
cd android

if exist ".gradle" rmdir /s /q ".gradle"
if exist "build" rmdir /s /q "build"
if exist "app\build" rmdir /s /q "app\build"

echo ✅ Caché limpiado
echo.

:: Solución 2: Modificar build.gradle para usar KAPT en lugar de KSP
echo 🛠️ Solución 2: Reemplazar KSP con KAPT (solución más estable)...
echo.

:: Crear backup
copy "build.gradle" "build.gradle.ksp_backup"
copy "app\build.gradle" "app\build.gradle.ksp_backup"

echo ✅ Backups creados
echo.

:: Modificar build.gradle raíz para usar KAPT
(
echo.
echo plugins {
echo     id 'com.android.application' version '8.1.4' apply false
echo     id 'org.jetbrains.kotlin.android' version '1.9.10' apply false
echo     id 'org.jetbrains.kotlin.kapt' version '1.9.10' apply true      ^<! USAR KAPT EN LUGAR DE KSP !^>
echo     id 'dagger.hilt.android.plugin' version '2.48.1' apply true
echo }
echo.
echo allprojects {
echo     repositories {
echo         google()
echo         mavenCentral()
echo     }
echo }
echo.
echo subprojects {
echo     afterEvaluate { project ->
echo         if (project.hasProperty('android')) {
echo             android {
echo                 compileOptions {
echo                     sourceCompatibility JavaVersion.VERSION_17
echo                     targetCompatibility JavaVersion.VERSION_17
echo                 }
echo                 kotlinOptions {
echo                     jvmTarget = '17'
echo                 }
echo             }
echo         }
echo     }
echo }
) > "build.gradle.new"

move /y "build.gradle.new" "build.gradle"

echo ✅ build.gradle modificado para usar KAPT
echo.

:: Modificar app/build.gradle para usar KAPT
(
echo plugins {
echo     id 'com.android.application'
echo     id 'org.jetbrains.kotlin.android'
echo     id 'org.jetbrains.kotlin.kapt'                             ^<! AÑADIR KAPT !^>
echo }
echo.
echo android {
echo     namespace 'com.nexa.ai'
echo     compileSdk 34
echo.
echo     defaultConfig {
echo         applicationId 'com.nexa.ai'
echo         minSdk 21
echo         targetSdk 34
echo         versionCode 1
echo         versionName '1.0'
echo.
echo         testInstrumentationRunner 'androidx.test.runner.AndroidJUnitRunner'
echo         vectorDrawables {
echo             useSupportLibrary true
echo         }
echo     }
echo.
echo     buildTypes {
echo         release {
echo             minifyEnabled false
echo             proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
echo         }
echo     }
echo.
echo     compileOptions {
echo         sourceCompatibility JavaVersion.VERSION_17
echo         targetCompatibility JavaVersion.VERSION_17
echo     }
echo.
echo     kotlinOptions {
echo         jvmTarget = '17'
echo     }
echo.
echo     buildFeatures {
echo         viewBinding true
echo     }
echo.
echo     packaging {
echo         resources {
echo             excludes += '/META-INF/{AL2.0,LGPL2.1}'
echo         }
echo     }
echo }
echo.
echo dependencies {
echo     implementation 'androidx.core:core-ktx:1.12.0'
echo     implementation 'androidx.appcompat:appcompat:1.6.1'
echo     implementation 'com.google.android.material:material:1.11.0'
echo     implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
echo     implementation 'androidx.lifecycle:lifecycle-livedata-ktx:2.6.2'
echo     implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2'
echo     implementation 'androidx.navigation:navigation-fragment-ktx:2.7.5'
echo     implementation 'androidx.navigation:navigation-ui-ktx:2.7.5'
echo.
echo     ^<! KAPT EN LUGAR DE KSP !^>
echo     kapt 'com.google.dagger:hilt-compiler:2.48.1'
echo     implementation 'com.google.dagger:hilt-android:2.48.1'
echo.
echo     testImplementation 'junit:junit:4.13.2'
echo     androidTestImplementation 'androidx.test.ext:junit:1.1.5'
echo     androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
echo }
) > "app\build.gradle.new"

move /y "app\build.gradle.new" "app\build.gradle"

echo ✅ app/build.gradle modificado para usar KAPT
echo.

:: Solución 3: Intentar Gradle Sync con KAPT
echo 🔄 Solución 3: Intentando Gradle Sync con KAPT...
echo.

cd ..

:: Intentar gradle tasks básico
cd android
call gradlew clean --no-daemon
if %errorlevel% neq 0 (
    echo ⚠️  Clean falló, continuando...
)

call gradlew tasks --no-daemon
if %errorlevel% neq 0 (
    echo ⚠️  Tasks falló, intentando offline...
    call gradlew tasks --offline --no-daemon
    if %errorlevel% neq 0 (
        echo ❌ Gradle tasks falló en ambos modos
        echo.
        echo 🚨 Posibles causas:
        echo 1. Java JDK no instalado o incorrecto
        echo 2. Problema de red
        echo 3. Gradle daemons corruptos
        echo.
        echo 💡 Intenta estas soluciones manuales:
        echo - Cierra Android Studio completamente
        echo - Abre Task Manager y mata procesos java
        echo - Reinstala Java JDK 17
        echo.
        pause
        exit /b 1
    )
)

echo ✅ Gradle tasks ejecutados con éxito
echo.

:: Solución 4: Crear APK básico
echo 📦 Solución 4: Intentando crear APK básico...
call gradlew assembleDebug --no-daemon
if %errorlevel% neq 0 (
    echo ⚠️  APK falló, pero el sync funcionó
    echo.
    echo 📱 Próximos pasos:
    echo 1. Abre Android Studio
    echo 2. File > Open > Selecciona la carpeta 'android'
    echo 3. Espera a que termine el Gradle Sync
    echo 4. Run > Run 'app'
    echo.
) else (
    echo ✅ APK creado exitosamente
    echo.
    echo APK location: android\app\build\outputs\apk\debug\app-debug.apk
    echo.
    echo 📱 Puedes instalar el APK con:
    echo adb install android\app\build\outputs\apk\debug\app-debug.apk
)

echo.
echo 🎉 ¡SOLUCIÓN KSP COMPLETADA!
echo.
echo 📱 Próximos pasos:
echo 1. Abre Android Studio
echo 2. File > Open > Selecciona la carpeta 'android'
echo 3. Espera a que termine el Gradle Sync
echo 4. Run > Run 'app'
echo.
echo 💡 Si aún falla, considera usar la web app:
echo quick_install.bat
echo.
pause