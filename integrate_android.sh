#!/bin/bash

# Script de Integración Nexas AI con Android Studio
# Automatiza la integración de la web app en el proyecto Android

echo "🚀 Iniciando integración de Nexas AI con Android Studio..."

# Directorios
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
ANDROID_DIR="$PROJECT_DIR/android"
WEB_DIR="$ANDROID_DIR/app/src/main/assets/web"

# Crear directorio web si no existe
mkdir -p "$WEB_DIR"

echo "📁 Copiando archivos web al proyecto Android..."
cp "$PROJECT_DIR/nexas-mobile.html" "$WEB_DIR/index.html"
cp "$PROJECT_DIR/nexas-mobile-server.js" "$WEB_DIR/server.js"
cp "$PROJECT_DIR/package.json" "$WEB_DIR/package.json"

echo "🔧 Modificando MainActivity.java para manos libres..."
# Crear backup del archivo original
cp "$ANDROID_DIR/app/src/main/java/com/nexa/ai/MainActivity.java" "$ANDROID_DIR/app/src/main/java/com/nexa/ai/MainActivity.java.backup"

# Sobreescribir MainActivity.java con versión mejorada
cat > "$ANDROID_DIR/app/src/main/java/com/nexa/ai/MainActivity.java" << 'EOF'
package com.nexa.ai;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private static final int REQUEST_CODE_PERMISSIONS = 101;
    private WebView webView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Solicitar permisos de audio preventivamente
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_CODE_PERMISSIONS);
        }
        
        // Configurar WebView para manos libres
        webView = getBridge().getWebView();
        setupWebViewForHandsFree();
        
        // Cargar la web app optimizada para móviles
        webView.loadUrl("file:///android_asset/web/index.html");
    }
    
    private void setupWebViewForHandsFree() {
        // Configurar ChromeClient para permisos
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    request.grant(request.getResources());
                });
            }
        });
        
        // Configurar WebViewClient para errores
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                Log.e("NexusWebView", "Error: " + error.getDescription());
            }
        });
        
        // Optimizar para móviles y manos libres
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        
        // Habilitar hardware acceleration
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            settings.setLayerType(WebSettings.LAYER_TYPE_HARDWARE, null);
        }
    }
    
    @Override
    public void onStart() {
        super.onStart();
        Log.d("NexusApp", "Modo manos libres activado");
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("NexusApp", "Permisos de audio concedidos");
            } else {
                Log.w("NexusApp", "Permisos de audio denegados");
            }
        }
    }
}
EOF

echo "📋 Actualizando AndroidManifest.xml para manos libres..."
# Crear backup
cp "$ANDROID_DIR/app/src/main/AndroidManifest.xml" "$ANDROID_DIR/app/src/main/AndroidManifest.xml.backup"

# Modificar AndroidManifest.xml
cat > "$ANDROID_DIR/app/src/main/AndroidManifest.xml" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.nexa.ai">

    <!-- Permisos para manos libres y audio -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
    
    <!-- Características para manos libres -->
    <uses-feature android:name="android.hardware.microphone" android:required="true" />
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />
    
    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.NexaAI"
        android:usesCleartextTraffic="true">
        
        <activity
            android:name=".MainActivity"
            android:configChanges="orientation|screenSize|keyboardHidden"
            android:exported="true"
            android:screenOrientation="portrait"
            android:theme="@style/Theme.NexaAI.NoActionBar"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        
    </application>
</manifest>
EOF

echo "📦 Instalando dependencias para el servidor web..."
cd "$WEB_DIR"
npm install

echo "🎉 ¡Integración completada!"
echo ""
echo "📱 Próximos pasos:"
echo "1. Abre Android Studio"
echo "2. Selecciona el directorio: $ANDROID_DIR"
echo "3. Click en 'Build' > 'Make Project'"
echo "4. Click en 'Run' > 'Run 'app''"
echo "5. Selecciona un dispositivo o emulador"
echo ""
echo "🔗 La app estará disponible como 'Nexas AI' en tu dispositivo"
echo "🎤 Modo manos libre ya está integrado y optimizado!"
echo ""
echo "📝 Nota: Si tienes problemas, revisa el archivo:"
echo "   $ANDROID_DIR/app/src/main/java/com/nexa/ai/MainActivity.java.backup"
echo "   para los archivos originales."