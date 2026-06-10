@echo off
echo 🔧 Solucionando problemas de Gradle Sync para Nexas AI...
echo.

:: Verificar directorio del proyecto
if not exist "android\build.gradle" (
    echo ❌ No se encontró el proyecto Android en: android\
    echo Verifica que estás en el directorio correcto
    pause
    exit /b 1
)

echo ✅ Proyecto Android encontrado
echo.

:: Verificar Gradle Wrapper
if not exist "android\gradlew.bat" (
    echo ❌ Gradle Wrapper no encontrado
    echo Descargando Gradle Wrapper...
    cd android
    call gradlew wrapper --gradle-version 8.1
    cd ..
)

echo ✅ Gradle Wrapper encontrado
echo.

:: Limpiar caché de Gradle
echo 🧹 Limpiando caché de Gradle...
cd android
call gradlew clean
if %errorlevel% neq 0 (
    echo ⚠️  Advertencia en limpieza, continuando...
)

:: Forzar descarga de dependencias
echo 📥 Descargando dependencias de Gradle...
call gradlew --refresh-dependencies
if %errorlevel% neq 0 (
    echo ❌ Error al descargar dependencias
    echo Posibles causas:
    echo - Sin internet
    echo - Proxy bloqueando
    echo - Firewall
    pause
    exit /b 1
)

echo ✅ Dependencias descargadas correctamente
echo.

:: Verificar versión de Java
echo ☕ Verificando Java...
java -version
echo.

:: Si hay problemas, intentar con JDK específico
if %errorlevel% neq 0 (
    echo ❌ Java no encontrado. Instalando JDK 17...
    echo Descarga JDK 17 desde: https://adoptium.net/temurin/releases/?version=17
    pause
    exit /b 1
)

echo ✅ Java encontrado
echo.

:: Intentar Gradle Sync manual
echo 🔄 Intentando Gradle Sync manual...
call gradlew build --offline
if %errorlevel% neq 0 (
    echo ❌ Sync falló en modo offline
    echo Intentando en línea...
    call gradlew build
    if %errorlevel% neq 0 (
        echo ❌ Sync falló en línea
        echo Revisa los logs en: android\build\logs
        pause
        exit /b 1
    )
)

echo.
echo 🎉 ¡Gradle Sync completado con éxito!
echo.
echo 📱 Ahora puedes intentar ejecutar la aplicación en Android Studio:
echo 1. Abre Android Studio
echo 2. Selecciona el directorio: android\
echo 3. Espera a que termine el sync
echo 4. Click en Run > Run 'app'
echo.
pause