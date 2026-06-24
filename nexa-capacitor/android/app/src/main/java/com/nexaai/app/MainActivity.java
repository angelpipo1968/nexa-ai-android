package com.nexaai.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
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

public class MainActivity extends Activity {
    private static final String SERVER_URL = "http://192.168.50.158:3000";
    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

            // Grant mic/camera permissions when web app requests them
            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onPermissionRequest(final PermissionRequest request) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            request.grant(request.getResources());
                        }
                    });
                }
            });

            // Stay in app (don't open external browser)
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    // Show fallback message if server unreachable
                    LinearLayout layout = new LinearLayout(MainActivity.this);
                    layout.setOrientation(LinearLayout.VERTICAL);
                    layout.setGravity(android.view.Gravity.CENTER);
                    layout.setBackgroundColor(Color.parseColor("#0f0a1f"));
                    
                    TextView title = new TextView(MainActivity.this);
                    title.setText("✦ Nexa AI");
                    title.setTextColor(Color.parseColor("#a78bfa"));
                    title.setTextSize(32);
                    title.setPadding(0, 0, 0, 32);
                    layout.addView(title);
                    
                    TextView msg = new TextView(MainActivity.this);
                    msg.setText("No se pudo conectar al servidor.\n\nVerificá que tu celular esté en la misma red WiFi que 192.168.50.158 y que el servidor esté corriendo.");
                    msg.setTextColor(Color.WHITE);
                    msg.setTextSize(16);
                    msg.setGravity(android.view.Gravity.CENTER);
                    layout.addView(msg);
                    
                    setContentView(layout);
                }
            });

            // Load the server URL
            webView.loadUrl(SERVER_URL);
            
        } catch (Exception e) {
            // Last-resort error display
            TextView errorView = new TextView(this);
            errorView.setText("Error al iniciar: " + e.getMessage());
            errorView.setTextColor(Color.RED);
            setContentView(errorView);
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
}
