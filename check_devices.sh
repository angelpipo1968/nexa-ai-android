#!/bin/bash

echo "🔍 Verificando dispositivos Android conectados..."

# Verificar si ADB está disponible
if ! command -v adb &> /dev/null; then
    echo "❌ ADB no encontrado. Instalando Android Platform Tools..."
    echo "macOS: brew install android-platform-tools"
    echo "Linux: sudo apt install android-tools-adb"
    exit 1
fi

echo "✅ ADB encontrado"

# Listar dispositivos
echo "📱 Dispositivos conectados:"
adb devices

# Verificar permisos de depuración USB
echo ""
echo "🔐 Verificando permisos de depuración USB:"
adb shell settings get global adb_allowed

# Contar dispositivos
DEVICE_COUNT=$(adb devices | tail -n +2 | grep -v "device$" | wc -l)
if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo ""
    echo "❌ No se encontraron dispositivos Android"
    echo ""
    echo "📱 SOLUCIONES:"
    echo ""
    echo "1. Conecta tu teléfono con USB"
    echo "   - Activa 'Depuración USB' en: Configuración > Opciones para desarrolladores"
    echo "   - Acepta el permiso en el teléfono"
    echo ""
    echo "2. O usa un emulador Android"
    echo "   - Abre Android Studio"
    echo "   - Click en 'Tools' > 'AVD Manager'"
    echo "   - Crea un nuevo emulador o usa uno existente"
    echo ""
    echo "3. Verifica cable USB"
    echo "   - Prueba otro cable USB"
    echo "   - Prueba otro puerto USB"
    echo "   - Activa 'Transferir archivos' en el teléfono"
    echo ""
    echo "4. Reinicia servicios ADB"
    echo "   adb kill-server"
    echo "   adb start-server"
    echo ""
    exit 1
else
    echo ""
    echo "🎉 Dispositivo listo para instalación!"
fi