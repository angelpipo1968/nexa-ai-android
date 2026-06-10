# 📱 Manual de Instalación Nexas AI con ADB

## 🚀 PASOS MANUALES

### 1. Preparar el entorno
```cmd
# Verificar ADB
adb version

# Listar dispositivos
adb devices
```

### 2. Copiar archivos al proyecto Android
```cmd
# Crear directorio web
mkdir "android\app\src\main\assets\web"

# Copiar archivos
copy "nexas-mobile.html" "android\app\src\main\assets\web\index.html"
copy "nexas-mobile-server.js" "android\app\src\main\assets\web\server.js"
copy "package.json" "android\app\src\main\assets\web\package.json"
```

### 3. Construir la aplicación
```cmd
cd android
gradlew assembleDebug
```

### 4. Instalar en el teléfono
```cmd
adb install app\build\outputs\apk\debug\app-debug.apk
```

### 5. Verificar instalación
```cmd
adb shell pm list packages | findstr nexa
```

## 🔧 TROUBLESHOOTING

### Problemas comunes:

#### **ADB no encontrado:**
1. Descarga Android Platform Tools: https://developer.android.com/studio/releases/platform-tools
2. Extrae en `C:\adb`
3. Añade al PATH de Windows

#### **Dispositivo no detectado:**
1. Activa "Depuración USB" en Opciones para desarrolladores
2. Conecta el teléfono con USB
3. Acepta el permiso en el teléfono

#### **Error de compilación:**
1. Verifica que tienes Java JDK instalado
2. Revisa los permisos del proyecto
3. Intenta: `gradlew clean build`

#### **Error de instalación:**
1. Desinstala la versión anterior: `adb uninstall com.nexa.ai`
2. Verifica espacio en el teléfono
3. Revisa que la APK se generó correctamente

## 🎉 ¡LISTO!**

Una vez instalada, abre la app "Nexus AI" en tu teléfono y disfruta del modo manos libre con:
- 🎤 Latencia optimizada (80ms)
- 🔊 Booster de volumen (+7 flujos)
- 👂 Sensor de proximidad
- 🎯 Touch targets grandes

## 💡 Tips para uso:
1. Usa audífonos para mejor experiencia
2. Activa permisos de micrófono
3. Conecta a WiFi estable
4. Cierra apps en segundo plano