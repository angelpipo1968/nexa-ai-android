package com.nexaai.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.speech.tts.Voice;
import java.util.Set;

import java.util.Locale;

public class MainActivity extends Activity {
    // URL local para cargar los archivos compilados en la APK
    private static final String SERVER_URL = "file:///android_asset/public/index.html";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private WebView webView;
    private TextToSpeech tts;
    private boolean ttsReady = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // --- Initialize native TTS engine (Android TextToSpeech) ---
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                try {
                    int result = tts.setLanguage(new Locale("es", "ES"));
                    // Fall back to US English if Spanish TTS data isn't installed
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts.setLanguage(Locale.US);
                    }
                    ttsReady = true;
                    tts.setSpeechRate(1.0f);
                    tts.setPitch(1.0f);
                } catch (Exception e) {
                    ttsReady = false;
                }
            }
        });

        // --- Request runtime permissions (Android 13+ needs POST_NOTIFICATIONS, mic always needs RECORD_AUDIO) ---
        requestRuntimePermissions();

        try {
            webView = new WebView(this);
            setContentView(webView);

            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setMediaPlaybackRequiresUserGesture(false);
            settings.setAllowFileAccess(true);
            settings.setAllowContentAccess(true);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            settings.setDatabaseEnabled(true);
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);

            // Expose native TTS bridge to JavaScript as window.AndroidTTS
            webView.addJavascriptInterface(new NativeTTSBridge(), "AndroidTTS");

            // Grant mic/camera permissions automatically when web app requests them
            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onPermissionRequest(final PermissionRequest request) {
                    runOnUiThread(() -> request.grant(request.getResources()));
                }
            });

            // Stay in app + inject TTS shim after each page load
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    injectTTSShim(view);
                }

                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    if (request != null && request.isForMainFrame()) {
                        showOfflineScreen();
                    }
                }
            });

            // Show version banner overlay (WebView keeps loading underneath)
            showVersionBanner();

            // Load the server URL
            webView.loadUrl(SERVER_URL);

        } catch (Exception e) {
            TextView errorView = new TextView(this);
            errorView.setText("Error al iniciar: " + e.getMessage());
            errorView.setTextColor(Color.RED);
            setContentView(errorView);
        }
    }

    /**
     * Inject a JavaScript shim that overrides window.speechSynthesis
     * to use our native AndroidTTS bridge (WebView's built-in speechSynthesis
     * is unreliable / silent on most Android versions).
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
     * Native bridge exposed to JavaScript as window.AndroidTTS
     */
    private class NativeTTSBridge {
        @JavascriptInterface
        public void speak(final String text, final String lang) {
            if (tts == null || text == null || text.isEmpty()) return;
            try {
                // Seleccionar voz basada en el género (es-ES-male o es-ES-female)
                if (lang != null && lang.startsWith("es-ES")) {
                    Set<Voice> voices = tts.getVoices();
                    if (voices != null) {
                        for (Voice v : voices) {
                            if (v.getLocale().getLanguage().equals("es")) {
                                if (lang.contains("female") && v.getName().toLowerCase().contains("female")) {
                                    tts.setVoice(v);
                                    break;
                                } else if (lang.contains("male") && v.getName().toLowerCase().contains("male")) {
                                    tts.setVoice(v);
                                    break;
                                }
                            }
                        }
                    }
                    tts.setLanguage(new Locale("es", "ES"));
                }

                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nexa_" + System.currentTimeMillis());
            } catch (Exception e) {
                // swallow
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
    }

    private void showOfflineScreen() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(android.view.Gravity.CENTER);
        layout.setBackgroundColor(Color.parseColor("#0f0a1f"));

        TextView title = new TextView(this);
        title.setText("✦ Nexa AI");
        title.setTextColor(Color.parseColor("#a78bfa"));
        title.setTextSize(32);
        title.setPadding(0, 0, 0, 32);
        layout.addView(title);

        TextView msg = new TextView(this);
        msg.setText("No se pudo conectar con https://nexa-ai.dev\n\nVerificá tu conexión a Internet (WiFi o datos móviles) e intentá de nuevo.");
        msg.setTextColor(Color.WHITE);
        msg.setTextSize(16);
        msg.setGravity(android.view.Gravity.CENTER);
        layout.addView(msg);

        // Retry button
        android.widget.Button retry = new android.widget.Button(this);
        retry.setText("Reintentar");
        retry.setTextColor(Color.WHITE);
        retry.setBackgroundColor(Color.parseColor("#7c3aed"));
        retry.setPadding(48, 24, 48, 24);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.topMargin = 48;
        retry.setLayoutParams(lp);
        retry.setOnClickListener(v -> {
            try {
                setContentView(webView);
                webView.loadUrl(SERVER_URL);
            } catch (Exception e) {
                // ignore
            }
        });
        layout.addView(retry);

        setContentView(layout);
    }

    private void showVersionBanner() {
        try {
            final android.widget.TextView banner = new android.widget.TextView(this);
            String now = new java.text.SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(new java.util.Date());
            banner.setText("✦ NEXA AI v5.0 — VERSIÓN COMPILADA LOCAL\nCargando: " + SERVER_URL + "\nCompilado: " + now + "\n\nAPK ACTUALIZADA ✅");
            banner.setTextColor(Color.WHITE);
            banner.setTextSize(15);
            banner.setBackgroundColor(Color.parseColor("#CC000000"));
            banner.setPadding(40, 40, 40, 40);
            banner.setGravity(android.view.Gravity.CENTER);

            // Wrap the WebView in a FrameLayout and add the banner on top,
            // so the WebView stays attached to the window and keeps loading.
            android.widget.FrameLayout root = new android.widget.FrameLayout(this);
            root.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            ));
            root.addView(webView,
                new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                )
            );
            android.widget.FrameLayout.LayoutParams bannerParams = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            );
            bannerParams.gravity = android.view.Gravity.TOP;
            banner.setLayoutParams(bannerParams);
            root.addView(banner);

            setContentView(root);

            // After 4 seconds, remove just the banner (keep the WebView)
            new android.os.Handler().postDelayed(() -> {
                try {
                    root.removeView(banner);
                } catch (Exception e) {
                    // ignore
                }
            }, 4000);
        } catch (Exception e) {
            // ignore
        }
    }

    private void requestRuntimePermissions() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                String[] perms = new String[] {
                    Manifest.permission.RECORD_AUDIO
                };
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    perms = new String[] {
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.POST_NOTIFICATIONS
                    };
                }
                java.util.List<String> toRequest = new java.util.ArrayList<>();
                for (String p : perms) {
                    if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                        toRequest.add(p);
                    }
                }
                if (!toRequest.isEmpty()) {
                    requestPermissions(toRequest.toArray(new String[0]), PERMISSION_REQUEST_CODE);
                }
            }
        } catch (Exception e) {
            // ignore - permissions are nice-to-have
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Exception e) {}
            tts = null;
        }
        super.onDestroy();
    }
}
