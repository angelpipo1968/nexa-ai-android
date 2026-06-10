@echo off
echo 🔍 Verificando dispositivos Android conectados...
echo.

:: Verificar si ADB está disponible
adb version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ ADB no encontrado. Instalando Android Platform Tools...
    echo Descarga desde: https://developer.android.com/studio/releases/platform-tools
    pause
    exit /b 1
)

echo ✅ ADB encontrado
echo.

:: Listar dispositivos
echo 📱 Dispositivos conectados:
adb devices
echo.

:: Verificar permisos de depuración USB
echo 🔐 Verificando permisos de depuración USB:
adb shell settings get global adb_allowed
echo.

:: Si no hay dispositivos, mostrar opciones
for /f "tokens=2" %%i in ('adb devices ^| findstr /r "device$"') do (
    echo ✅ Dispositivo encontrado: %%i
    goto :device_found
)

echo.
echo ❌ No se encontraron dispositivos Android
echo.
echo 📱 SOLUCIONES:
echo.
echo 1. Conecta tu teléfono con USB
echo    - Activa "Depuración USB" en: Configuración > Opciones para desarrolladores
echo    - Acepta el permiso en el teléfono
echo.
echo 2. O usa un emulador Android
echo    - Abre Android Studio
echo    - Click en "Tools" > "AVD Manager"
echo    - Crea un nuevo emulador o usa uno existente
echo.
echo 3. Verifica cable USB
echo    - Prueba otro cable USB
echo    - Prueba otro puerto USB
echo    - Activa "Transferir archivos" en el teléfono
echo.
echo 4. Reinicia servicios ADB
echo    adb kill-server
echo    adb start-server
echo.
pause
exit /b 1

:device_found
echo.
echo 🎉 Dispositivo listo para instalación!
echo.
pause