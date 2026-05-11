# NEXA PRO — Android App

App nativa de Android para hablar con NEXA PRO por voz.

## Características

- 🎙️ Reconocimiento de voz nativo (Samsung S26 Ultra optimizado)
- 🔊 Text-to-Speech (NEXA habla de vuelta)
- 🤖 Streaming de respuestas en tiempo real
- 🌙 Modo oscuro (como la web)
- ⚡ Tema Esmeralda (accent #00E5A0)
- ✍️ Soporte S Pen

## Requisitos

- Android Studio Hedgehog (2023.1.1) o superior
- Android SDK 34
- Kotlin 1.9.22

## Configuración

1. Abre el proyecto en Android Studio
2. Edita `app/build.gradle.kts` → cambia `API_BASE_URL` a tu dominio de Vercel
3. Sync Gradle
4. Run en tu dispositivo/emulador

## API

La app se conecta a `/api/chat` de tu backend Nexa desplegado en Vercel.
El endpoint debe soportar streaming SSE (Server-Sent Events).

## Permisos

- `INTERNET` — para conectar con la API
- `RECORD_AUDIO` — para el micrófono (se pide al primer uso)
