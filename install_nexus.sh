#!/bin/bash

echo "🚀 Iniciando instalación de Nexas AI en tu teléfono..."

# Verificar si ADB está disponible
if ! command -v adb &> /dev/null; then
    echo "❌ ADB no encontrado. Por favor instala Android Platform Tools."
    echo "macOS: brew install android-platform-tools"
    echo "Linux: sudo apt install android-tools-adb"
    exit 1
fi

echo "✅ ADB encontrado"

# Verificar dispositivos conectados
echo "🔍 Buscando dispositivos..."
adb devices

if [ -z "$(adb devices | tail -n +2 | grep -v "device")" ]; then
    echo "📱 Por favor, conecta tu teléfono y acepta el permiso de depuración USB"
    echo "Presiona Enter cuando esté listo..."
    read
fi

# Crear directorio web
mkdir -p "android/app/src/main/assets/web"

# Copiar archivos
echo "📁 Copiando archivos web al proyecto Android..."
cp "nexas-mobile.html" "android/app/src/main/assets/web/index.html"
cp "nexas-mobile-server.js" "android/app/src/main/assets/web/server.js"
cp "package.json" "android/app/src/main/assets/web/package.json"

# Construir aplicación
echo "🔧 Construyendo la aplicación..."
cd android
./gradlew assembleDebug

if [ $? -ne 0 ]; then
    echo "❌ Error en la compilación. Revisa los logs."
    exit 1
fi

# Instalar aplicación
echo "📦 Instalando en tu teléfono..."
adb install app/build/outputs/apk/debug/app-debug.apk

if [ $? -ne 0 ]; then
    echo "❌ Error en la instalación. Revisa los permisos de tu teléfono."
    exit 1
fi

echo ""
echo "🎉 ¡INSTALACIÓN COMPLETA!"
echo ""
echo "📱 La aplicación 'Nexas AI' está instalada en tu teléfono"
echo "🎤 Modo manos libre ya está integrado"
echo "🌐 Abre la app y disfruta de Nexas AI v5"
echo ""
echo "💡 Tips:"
echo " - Conecta audífonos para mejor experiencia de manos libres"
echo " - Activa permisos de micrófono cuando se solicite"
echo " - Usa Chrome para mejor compatibilidad con voz"