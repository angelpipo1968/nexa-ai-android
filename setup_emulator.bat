@echo off
echo 📱 Configurando emulador Android para Nexas AI...
echo.

:: Verificar si Android Studio está instalado
if not exist "%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe" (
    echo ❌ Android Studio no encontrado. Por favor instala Android Studio:
    echo https://developer.android.com/studio
    pause
    exit /b 1
)

echo ✅ Android Studio encontrado
echo.

:: Listar AVDs disponibles
echo 📋 Emuladores disponibles:
call "%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe" -list-avds
echo.

:: Si no hay AVDs, crear uno básico
for /f "tokens=*" %%i in ('call "%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe" -list-avds 2^>nul') do (
    echo ✅ Emulador encontrado: %%i
    goto :emulator_found
)

echo.
echo ❌ No se encontraron emuladores. Creando uno básico...
echo.

:: Crear directorio para AVDs
set AVD_DIR=%USERPROFILE%\.android\avd
if not exist "%AVD_DIR%" mkdir "%AVD_DIR%"

:: Crear archivo de configuración básico
echo avd.ini.encoding=UTF-8 > "%AVD_DIR%\nexus_ai.avd\config.ini"
echo hw.cpu.arch=x86 >> "%AVD_DIR%\nexus_ai.avd\config.ini"
echo hw.gpu=yes >> "%AVD_DIR%\nexus_ai.avd\config.ini"
echo hw.ramSize=2048 >> "%AVD_DIR%\nexus_ai.avd\config.ini"
echo skin.name=1080x1920 >> "%AVD_DIR%\nexus_ai.avd\config.ini"
echo skin.path=platforms/android-33/skins/1080x1920 >> "%AVD_DIR%\nexus_ai.avd\config.ini"
echo target=android-33 >> "%AVD_DIR%\nexus_ai.avd\config.ini"

echo 📱 Emulador básico configurado
echo.
echo 🚀 Para iniciar el emulador:
echo "%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe" -avd nexus_ai
echo.
echo 💡 O abre Android Studio y crea un emulador visualmente:
echo 1. Abre Android Studio
echo 2. Click en "Tools" > "AVD Manager"
echo 3. Click en "Create Virtual Device"
echo 4. Selecciona un teléfono (ej: Pixel 6)
echo 5. Descarga una imagen system (si es necesario)
echo 6. Click en "Finish"
echo.
pause
exit /b 1

:emulator_found
echo.
echo 🎉 Emulador listo para usar!
echo.
echo 🚀 Para iniciar el emulador:
echo "%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe" -avd %%i
echo.
pause