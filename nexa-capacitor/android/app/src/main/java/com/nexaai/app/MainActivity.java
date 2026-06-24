package com.nexaai.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // No overriding WebChromeClient — Capacitor 6 manages it internally.
        // Microphone/camera permissions are granted by AndroidManifest + runtime request.
    }
}
