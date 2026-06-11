@echo off
echo 🔧 SOLUCIÓN: Conflicto KSP + Hilt Plugin
echo Este error ocurre cuando KSP y Hilt están en diferentes scopes
echo.

:: Verificar archivos existentes
if not exist "android\build.gradle" (
    echo ❌ No se encontró build.gradle en android\
    pause
    exit /b 1
)

if not exist "android\app\build.gradle" (
    echo ❌ No se encontró app/build.gradle
    pause
    exit /b 1
)

echo ✅ Archivos de Gradle encontrados
echo.

:: Crear backup de los archivos originales
echo 📁 Creando backups...
copy "android\build.gradle" "android\build.gradle.backup"
copy "android\app\build.gradle" "android\app\build.gradle.backup"

echo ✅ Backups creados
echo.

echo 🛠️ MODIFICANDO ARCHIVOS PARA SOLUCIONAR CONFLICTO...
echo.

:: Modificar build.gradle raíz (android/build.gradle)
echo 🔧 Modificando android/build.gradle...
(
echo.
echo plugins {
echo     id 'com.android.application' version '8.1.4' apply false
echo     id 'org.jetbrains.kotlin.android' version '1.9.10' apply false
echo     id 'com.google.devtools.ksp' version '1.9.10-1.0.13' apply true  ^<! MOVER KSP A RAÍZ !^>
echo     id 'dagger.hilt.android.plugin' version '2.48.1' apply true      ^<! MOVER HILT A RAÍZ !^>
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
) > "android\build.gradle.new"

move /y "android\build.gradle.new" "android\build.gradle"

echo ✅ android/build.gradle modificado
echo.

:: Modificar app/build.gradle (quitar plugins duplicados)
echo 🔧 Modificando android/app/build.gradle...
(
echo plugins {
echo     id 'com.android.application'
echo     id 'org.jetbrains.kotlin.android'
echo     ^<! QUITAR KSP y HILT de aquí !^>
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
echo     ^<! KSP y Hilt en el scope correcto !^>
echo     ksp 'com.google.devtools.ksp:symbol-processing-api:1.9.10-1.0.13'
echo     implementation 'com.google.dagger:hilt-android:2.48.1'
echo     ksp 'com.google.dagger:hilt-compiler:2.48.1'
echo.
echo     testImplementation 'junit:junit:4.13.2'
echo     androidTestImplementation 'androidx.test.ext:junit:1.1.5'
echo     androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
echo }
) > "android\app\build.gradle.new"

move /y "android\app\build.gradle.new" "android\app\build.gradle"

echo ✅ android/app/build.gradle modificado
echo.

echo 🧹 Limpiando caché de Gradle...
cd android
call gradlew clean --no-daemon
cd ..

echo ✅ Caché limpiada
echo.

echo 🔄 Intentando Gradle Sync corregido...
cd android
call gradlew --refresh-dependencies --no-daemon
if %errorlevel% neq 0 (
    echo ⚠️  Sync con --refresh-dependencies falló, intentando normal...
    call gradlew --no-daemon
    if %errorlevel% neq 0 (
        echo ❌ Sync falló. Revisa los logs.
        pause
        exit /b 1
    )
)
cd ..

echo.
echo 🎉 ¡CONFLICTO RESUELTO!
echo.
echo 📱 Ahora puedes intentar ejecutar en Android Studio:
echo 1. Abre Android Studio
echo 2. File > Open > Selecciona la carpeta 'android'
echo 3. Espera a que termine el Gradle Sync
echo 4. Run > Run 'app'
echo.
echo 💡 Si aún falla, intenta:
echo - File > Invalidate Caches / Restart
echo - Build > Clean Project
echo - Build > Rebuild Project
echo.
pause