import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
    appId: 'com.nexa.ai',
    appName: 'NEXA AI',
    webDir: 'out',
    server: {
        url: 'https://api.nexa-ai.dev',
        cleartext: true,
        androidScheme: 'http',
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
