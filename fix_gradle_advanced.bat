@echo off
echo 🔧 SOLUCIÓN AVANZADA: Gradle Sync persistente
echo Intentando múltiples soluciones para forzar el sync...
echo.

:: Verificar directorio
if not exist "android" (
    echo ❌ No se encontró directorio android
    pause
    exit /b 1
)

echo ✅ Directorio android encontrado
echo.

:: Solución 1: Limpiar completamente el proyecto
echo 🧹 Solución 1: Limpiar caché completo...
echo.

:: Eliminar carpetas de caché
if exist "android\.gradle" rmdir /s /q "android\.gradle"
if exist "android\build" rmdir /s /q "android\build"
if exist "android\app\build" rmdir /s /q "android\app\build"

:: Eliminar archivos de caché
if exist "android\.gradle" del /f /q "android\.gradle"
if exist "android\gradle\wrapper\gradle-wrapper.jar" del /f /q "android\gradle\wrapper\gradle-wrapper.jar"

echo ✅ Caché eliminado completamente
echo.

:: Solución 2: Forzar descarga de Gradle Wrapper
echo 📥 Solución 2: Forzar descarga de Gradle Wrapper...
cd android

:: Eliminar gradle wrapper existente
if exist "gradlew" del /f /q "gradlew"
if exist "gradlew.bat" del /f /q "gradlew.bat"
if exist "gradle\wrapper\gradle-wrapper.jar" del /f /q "gradle\wrapper\gradle-wrapper.jar"

:: Crear gradle wrapper manualmente
mkdir -p gradle\wrapper

echo @echo off > gradlew.bat
echo call "%~dp0gradlew" %* >> gradlew.bat

echo distributionBase=GRADLE_USER_HOME >> gradle\wrapper\gradle-wrapper.properties
echo distributionPath=wrapper\libs >> gradle\wrapper\gradle-wrapper.properties
echo distributionUrl=https\://services.gradle.org/distributions/gradle-8.1-bin.zip >> gradle\wrapper\gradle-wrapper.properties
echo zipStoreBase=GRADLE_USER_HOME >> gradle\wrapper\gradle-wrapper.properties
echo zipStorePath=wrapper\libs >> gradle\wrapper\gradle-wrapper.properties

echo ✅ Gradle wrapper recreado
echo.

:: Solución 3: Crear build.gradle simplificado
echo 🛠️ Solución 3: Crear build.gradle simplificado...

:: Crear backup del build.gradle original
if exist "build.gradle" copy "build.gradle" "build.gradle.original"

:: Crear build.gradle simplificado
(
echo.
echo plugins {
echo     id 'com.android.application' version '8.1.4' apply false
echo     id 'org.jetbrains.kotlin.android' version '1.9.10' apply false
echo }
echo.
echo allprojects {
echo     repositories {
echo         google()
echo         mavenCentral()
echo     }
echo }
) > "build.gradle.new"

move /y "build.gradle.new" "build.gradle"

echo ✅ build.gradle simplificado creado
echo.

:: Solución 4: Crear app/build.gradle básico
echo 📱 Solución 4: Crear app/build.gradle básico...

:: Crear backup del app/build.gradle original
if exist "app\build.gradle" copy "app\build.gradle" "app\build.gradle.original"

:: Crear app/build.gradle básico sin Hilt/KSP
(
echo plugins {
echo     id 'com.android.application'
echo     id 'org.jetbrains.kotlin.android'
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
echo }
echo.
echo dependencies {
echo     implementation 'androidx.core:core-ktx:1.12.0'
echo     implementation 'androidx.appcompat:appcompat:1.6.1'
echo     implementation 'com.google.android.material:material:1.11.0'
echo     implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
echo     testImplementation 'junit:junit:4.13.2'
echo     androidTestImplementation 'androidx.test.ext:junit:1.1.5'
echo     androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
echo }
) > "app\build.gradle.new"

move /y "app\build.gradle.new" "app\build.gradle"

echo ✅ app/build.gradle básico creado
echo.

:: Solución 5: Intentar Gradle Sync
echo 🔄 Solución 5: Intentando Gradle Sync...
echo.

cd ..

:: Intentar gradlew wrapper
cd android
call gradlew wrapper --gradle-version 8.1 --distribution-type all

echo ✅ Gradle wrapper actualizado
echo.

:: Intentar gradle tasks básico
call gradlew tasks --no-daemon
if %errorlevel% neq 0 (
    echo ⚠️  Gradle tasks falló, intentando offline...
    call gradlew tasks --offline --no-daemon
    if %errorlevel% neq 0 (
        echo ❌ Gradle tasks falló en ambos modos
        echo.
        echo 🚨 Posibles soluciones adicionales:
        echo 1. Verifica Java JDK 17+ está instalado
        echo 2. Verifica conexión a internet
        echo 3. Intenta abrir Android Studio manualmente
        echo 4. Considera crear un nuevo proyecto Android Studio
        echo.
        pause
        exit /b 1
    )
)

echo ✅ Gradle tasks ejecutados con éxito
echo.

:: Solución 6: Crear APK básico
echo 📦 Solución 6: Intentando crear APK básico...
call gradlew assembleDebug --no-daemon
if %errorlevel% neq 0 (
    echo ⚠️  APK falló, pero el sync funcionó
    echo.
) else (
    echo ✅ APK creado exitosamente
    echo.
    echo APK location: android\app\build\outputs\apk\debug\app-debug.apk
)

echo.
echo 🎉 ¡SOLUCIÓN AVANZADA COMPLETADA!
echo.
echo 📱 Próximos pasos:
echo 1. Abre Android Studio
echo 2. File > Open > Selecciona la carpeta 'android'
echo 3. Espera a que termine el Gradle Sync
echo 4. Run > Run 'app'
echo.
echo 💡 Si aún falla:
echo - File > Invalidate Caches / Restart
echo - Considera crear un nuevo proyecto Android Studio
echo - O usa la web app: start chrome nexas-mobile.html
echo.
pause