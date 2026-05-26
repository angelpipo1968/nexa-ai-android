import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
    appId: 'com.nexa.ai',
    appName: 'NEXA AI',
    webDir: 'out',
    server: {
        // In production Android builds, API routes don't exist in the static export.
        // Set NEXA_BACKEND_URL env var to point to your deployed Next.js backend
        // (e.g., https://www.nexa-ai.dev). For local dev, leave empty to use relative URLs.
        url: process.env.NEXA_BACKEND_URL || undefined,
        androidScheme: 'https',
    },
    android: {
        buildOptions: {
            keystorePath: undefined,
            keystoreAlias: undefined,
        },
    },
    plugins: {
        CapacitorHttp: {
            enabled: true,
        },
        SplashScreen: {
            launchAutoHide: true,
            launchShowDuration: 3000,
            backgroundColor: '#0a0a0a',
            showSpinner: false,
            androidSplashResourceName: 'splash',
            androidScaleType: 'CENTER_CROP',
            splashFullScreen: true,
            splashImmersive: true,
        },
        StatusBar: {
            style: 'DARK' as any,
            backgroundColor: '#0a0a0a',
        },
        Keyboard: {
            resize: 'body' as any,
            style: 'DARK' as any,
        },
    },
};

export default config;
