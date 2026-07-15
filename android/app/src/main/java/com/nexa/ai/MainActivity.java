package com.nexa.ai;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.util.Log;
import android.view.View;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;
import java.util.Locale;
import android.content.Intent;
import android.provider.MediaStore;
import android.graphics.Bitmap;
import android.util.Base64;
import java.io.ByteArrayOutputStream;

public class MainActivity extends BridgeActivity {
    private static final int REQUEST_CODE_PERMISSIONS = 101;
    private static final int REQUEST_IMAGE_CAPTURE = 102;
    private WebView webView;
    private TextToSpeech tts;
    private boolean ttsReady = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Solicitar permisos de audio y cámara preventivamente
        String[] permissions = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        };
        
        boolean hasPermissions = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                hasPermissions = false;
                break;
            }
        }
        
        if (!hasPermissions) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_PERMISSIONS);
        }

        // --- Inicializar motor nativo de TextToSpeech ---
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                try {
                    int result = tts.setLanguage(new Locale("es", "ES"));
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts.setLanguage(Locale.US);
                    }
                    ttsReady = true;
                    tts.setSpeechRate(1.0f);
                    tts.setPitch(1.0f);
                    Log.d("NexusTTS", "TTS nativo inicializado correctamente");
                } catch (Exception e) {
                    Log.e("NexusTTS", "Error configurando idioma TTS: " + e.getMessage());
                    ttsReady = false;
                }
            } else {
                Log.e("NexusTTS", "Fallo al inicializar TTS");
            }
        });
        
        // Configurar WebView para manos libres
        webView = getBridge().getWebView();
        setupWebViewForHandsFree();
        
        // Cargar la web app optimizada para móviles
        webView.loadUrl("file:///android_asset/web/index.html");
    }
    
    private void setupWebViewForHandsFree() {
        // Capacitor's default WebChromeClient handles permissions and file uploads automatically.
        // We do not override it to avoid breaking <input type="file">.
        
        // Exponer el puente nativo AndroidTTS a JavaScript
        webView.addJavascriptInterface(new NativeTTSBridge(), "AndroidTTS");

        // Configurar WebViewClient para errores e inyección del shim de audio
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d("NexusWebView", "Página cargada, inyectando shim de TTS nativo");
                injectTTSShim(view);
            }

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
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
    }

    /**
     * Inyecta una función puente que intercepta speechSynthesis.speak en la web
     * y la envía a través de nuestro puente nativo AndroidTTS.
     */
    private void injectTTSShim(WebView view) {
        String js =
            "(function(){" +
            "  if (window._nexaTTSInstalled) return;" +
            "  window._nexaTTSInstalled = true;" +
            "  function NexaUtterance(text) {" +
            "    this.text = text || '';" +
            "    this.lang = 'es-ES';" +
            "    this.rate = 1.0;" +
            "    this.pitch = 1.0;" +
            "    this.volume = 1.0;" +
            "    this.voice = null;" +
            "    this.onstart = null;" +
            "    this.onend = null;" +
            "    this.onerror = null;" +
            "    this.onpause = null;" +
            "    this.onresume = null;" +
            "    this.onmark = null;" +
            "    this.onboundary = null;" +
            "  }" +
            "  try { window.SpeechSynthesisUtterance = NexaUtterance; } catch(e) {}" +
            "  if (!window.speechSynthesis) window.speechSynthesis = {};" +
            "  var ss = window.speechSynthesis;" +
            "  ss.speaking = false;" +
            "  ss.pending = false;" +
            "  ss.paused = false;" +
            "  ss.speak = function(u) {" +
            "    try {" +
            "      if (u && u.text) {" +
            "        ss.speaking = true;" +
            "        if (window.AndroidTTS && window.AndroidTTS.speak) {" +
            "          window.AndroidTTS.speak(String(u.text), String(u.lang || 'es-ES'));" +
            "        }" +
            "        if (u.onstart) setTimeout(function(){ try{u.onstart();}catch(e){} }, 20);" +
            "        var dur = Math.max(800, Math.round(String(u.text).length * 70));" +
            "        setTimeout(function(){" +
            "          ss.speaking = false;" +
            "          if (u.onend) { try{u.onend();}catch(e){} }" +
            "        }, dur);" +
            "      }" +
            "    } catch(e) { if (u && u.onerror) { try{u.onerror(e);}catch(_){} } }" +
            "  };" +
            "  ss.cancel = function() {" +
            "    try { if (window.AndroidTTS && window.AndroidTTS.stop) window.AndroidTTS.stop(); } catch(e){}" +
            "    ss.speaking = false; ss.pending = false;" +
            "  };" +
            "  ss.pause = function() {};" +
            "  ss.resume = function() {};" +
            "  ss.getVoices = function() {" +
            "    return [{name:'Nexa Voz', lang:'es-ES', default:true, localService:true, voiceURI:'nexa'}];" +
            "  };" +
            "  ss.addEventListener = function() {};" +
            "  ss.removeEventListener = function() {};" +
            "  if (typeof window.speechSynthesisEvent === 'undefined') { window.speechSynthesisEvent = function(){}; }" +
            "})();";
        view.evaluateJavascript(js, null);
    }

    /**
     * Puente nativo expuesto a JavaScript como window.AndroidTTS
     */
    private class NativeTTSBridge {
        @JavascriptInterface
        public void speak(final String text, final String lang) {
            if (tts == null || text == null || text.isEmpty()) return;
            try {
                if (lang != null && !lang.isEmpty()) {
                    Locale loc;
                    if (lang.contains("-")) {
                        String[] parts = lang.split("-");
                        loc = parts.length > 1 ? new Locale(parts[0], parts[1]) : new Locale(parts[0]);
                    } else {
                        loc = new Locale(lang);
                    }
                    tts.setLanguage(loc);
                }
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nexa_" + System.currentTimeMillis());
            } catch (Exception e) {
                Log.e("NexusTTS", "Error en speak nativo: " + e.getMessage());
            }
        }

        @JavascriptInterface
        public void stop() {
            if (tts != null) {
                try { tts.stop(); } catch (Exception e) {}
            }
        }

        @JavascriptInterface
        public boolean isReady() {
            return ttsReady;
        }

        @JavascriptInterface
        public void openNativeCamera() {
            runOnUiThread(() -> {
                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                }
            });
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            if (extras != null && extras.get("data") != null) {
                Bitmap imageBitmap = (Bitmap) extras.get("data");
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                imageBitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
                byte[] byteArray = byteArrayOutputStream.toByteArray();
                String encoded = Base64.encodeToString(byteArray, Base64.NO_WRAP);
                String js = "javascript:if(window.handleNativeImage){window.handleNativeImage('data:image/jpeg;base64," + encoded + "')}";
                webView.post(() -> webView.evaluateJavascript(js, null));
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
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

    @Override
    public void onDestroy() {
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Exception e) {}
            tts = null;
        }
        super.onDestroy();
    }
}
