@echo off
echo 🚀 Iniciando instalación de Nexas AI en tu teléfono...
echo.

:: Verificar si ADB está disponible
adb version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ ADB no encontrado. Por favor instala Android Platform Tools.
    echo Descarga desde: https://developer.android.com/studio/releases/platform-tools
    pause
    exit /b 1
)

echo ✅ ADB encontrado
echo.

:: Verificar dispositivos conectados
echo 🔍 Buscando dispositivos...
adb devices
echo.

if "%1"=="" (
    echo 📱 Por favor, conecta tu teléfono y acepta el permiso de depuración USB
    echo Luego presiona cualquier tecla para continuar...
    pause >nul
)

echo 📁 Copiando archivos web al proyecto Android...
mkdir "android\app\src\main\assets\web" 2>nul
copy "nexas-mobile.html" "android\app\src\main\assets\web\index.html"
copy "nexas-mobile-server.js" "android\app\src\main\assets\web\server.js"
copy "package.json" "android\app\src\main\assets\web\package.json"

echo 🔧 Construyendo la aplicación...
cd android
call gradlew assembleDebug
if %errorlevel% neq 0 (
    echo ❌ Error en la compilación. Revisa los logs.
    pause
    exit /b 1
)

echo 📦 Instalando en tu teléfono...
adb install app\build\outputs\apk\debug\app-debug.apk
if %errorlevel% neq 0 (
    echo ❌ Error en la instalación. Revisa los permisos de tu teléfono.
    pause
    exit /b 1
)

echo.
echo 🎉 ¡INSTALACIÓN COMPLETA!
echo.
echo 📱 La aplicación "Nexas AI" está instalada en tu teléfono
echo 🎤 Modo manos libre ya está integrado
echo 🌐 Abre la app y disfruta de Nexas AI v5
echo.
echo 💡 Tips:
echo - Conecta audífonos para mejor experiencia de manos libres
echo - Activa permisos de micrófono cuando se solicite
echo - Usa Chrome para mejor compatibilidad con voz
echo.
pause