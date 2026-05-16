import type { Metadata, Viewport } from 'next';
import './globals.css';
import { SentryInit } from '@/components/sentry-init';

export const metadata: Metadata = {
    title: 'NEXA AI — Intelligence Reborn',
    description: 'Asistente de IA avanzado con chat en tiempo real, voz, y análisis de imágenes.',
    manifest: '/manifest.json',
    metadataBase: new URL('https://nexa-ai.dev'),
    openGraph: {
        title: 'NEXA AI — Intelligence Reborn',
        description: 'Asistente de IA avanzado con chat en tiempo real, voz, y análisis de imágenes.',
        url: 'https://nexa-ai.dev',
        siteName: 'NEXA AI',
        images: [
            {
                url: '/logo-nexa.png',
                width: 1200,
                height: 630,
                alt: 'NEXA AI — Intelligence Reborn',
            },
        ],
        locale: 'es_ES',
        type: 'website',
    },
    twitter: {
        card: 'summary_large_image',
        title: 'NEXA AI — Intelligence Reborn',
        description: 'Asistente de IA avanzado con chat en tiempo real, voz, y análisis de imágenes.',
        images: ['/logo-nexa.png'],
    },
    icons: {
        icon: '/favicon.ico',
        apple: '/apple-touch-icon.png',
    },
};

export const viewport: Viewport = {
    themeColor: '#02020a',
    width: 'device-width',
    initialScale: 1,
    maximumScale: 1,
    userScalable: false,
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
    return (
        <html lang="es" suppressHydrationWarning>
            <head>
                <link rel="icon" href="/favicon.ico" />
            </head>
            <body suppressHydrationWarning>
                {/* <SentryInit /> */}
                {children}
            </body>
        </html>
    );
}
