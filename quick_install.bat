@echo off
echo 🚀 SOLUCIÓN RÁPIDA: Nexas AI sin Android Studio
echo Instalando Nexas AI directamente en tu teléfono...
echo.

:: Verificar Node.js
node --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Node.js no encontrado. Instalando Node.js...
    echo Descarga Node.js desde: https://nodejs.org/
    echo Instala la versión LTS (Long Term Support)
    pause
    exit /b 1
)

echo ✅ Node.js encontrado
echo.

:: Instalar dependencias
echo 📦 Instalando dependencias del servidor...
cd "C:\Users\pipog\Downloads\nexa-ai-android-main\nexa-ai-android-main"

if not exist "node_modules" (
    echo Instalando npm packages...
    npm install
    if %errorlevel% neq 0 (
        echo ❌ Error al instalar dependencias
        pause
        exit /b 1
    )
)

echo ✅ Dependencias instaladas
echo.

:: Obtener IP local
echo 🔍 Buscando IP local...
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /R /C:"IPv4"') do (
    for /f "tokens=1" %%b in ("%%a") do (
        set LOCAL_IP=%%b
        goto :ip_found
    )
)

:ip_found
if not defined LOCAL_IP (
    echo ❌ No se pudo encontrar IP local
    echo Usa manualmente: ipconfig para encontrar tu IP
    pause
    exit /b 1
)

echo ✅ IP local encontrada: %LOCAL_IP%
echo.

:: Iniciar servidor
echo 🚀 Iniciando servidor Nexas AI...
echo Servidor disponible en: http://%LOCAL_IP%:3001
echo.
echo 📱 En tu teléfono, abre Chrome y visita:
echo http://%LOCAL_IP%:3001/nexas-mobile.html
echo.
echo 🎤 Modo manos libre listo para usar!
echo.

:: Preguntar si iniciar servidor
set /p start_server="¿Iniciar servidor ahora? (s/n): "
if /i "%start_server%"=="s" (
    echo.
    echo 🚀 Servidor iniciado. Presiona Ctrl+C para detenerlo.
    echo.
    node nexas-mobile-server.js
) else (
    echo.
    echo 📝 Para iniciar el servidor manualmente:
    echo cd "C:\Users\pipog\Downloads\nexa-ai-android-main\nexa-ai-android-main"
    echo node nexas-mobile-server.js
    echo.
    echo Luego en tu teléfono: http://%LOCAL_IP%:3001/nexas-mobile.html
)

echo.
echo 🎉 ¡NEXAS AI LISTO PARA USAR!
echo.
echo 💡 Tips:
echo - Asegúrate de que tu teléfono y computadora estén en la misma red WiFi
echo - Si no funciona, verifica el firewall de Windows
echo - El modo manos libre está completamente funcional
echo.
pause