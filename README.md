# NEXA PRO - Android App

App nativa de Android para hablar con NEXA PRO por voz.

## ✨ Características

- 🎙️ Reconocimiento de voz nativo
- 🔊 Text-to-Speech con múltiples voces (6 opciones: 3 masculinas, 3 femeninas)
- 🤖 Streaming de respuestas en tiempo real (SSE)
- 🌙 Modo oscuro / claro
- ⚡ Tema Esmeralda (accent #00E5A0)
- 📝 Soporte S Pen
- 📄 Exportar mensajes a PDF
- 🔐 Sesiones múltiples con persistencia local
- 🌍 Soporte español / inglés
- 🔄 Actualización in-app desde GitHub Releases

## 🛠️ Stack

- **Kotlin** 2.0 + **Jetpack Compose** (Material3)
- **Android SDK 35** (minSdk 26)
- **OkHttp** + SSE para streaming
- **DataStore** para persistencia
- **Vercel Edge Functions** para el backend

## 📋 Requisitos

- Android Studio Hedgehog (2023.1.1) o superior
- Android SDK 35
- JDK 17

## 🚀 Instalación

1. Clona el proyecto:
   ```bash
   git clone https://github.com/angelpipo1968/nexa-ai-android.git
   ```

2. Abre el proyecto en Android Studio

3. Edita `app/build.gradle.kts` → cambia `API_BASE_URL` a tu dominio de Vercel:
   ```kotlin
   buildConfigField("String", "API_BASE_URL", "\"https://tu-dominio.vercel.app\"")
   ```

4. Sync Gradle

5. Run en tu dispositivo/emulador

## 🔧 Backend (Vercel)

La app se conecta a `/api/chat` de tu backend desplegado en Vercel.

### Variables de entorno necesarias en Vercel:

| Variable | Descripción |
|----------|-------------|
| `OPENAI_API_KEY` | API key de OpenAI |
| `ANTHROPIC_API_KEY` | API key de Anthropic |
| `GEMINI_API_KEY` | API key de Google Gemini |
| `GROQ_API_KEY` | API key de Groq |
| `DEFAULT_PROVIDER` | Proveedor por defecto (opcional) |

El endpoint soporta streaming SSE (Server-Sent Events) con 4 proveedores:
- **OpenAI** (gpt-4o-mini)
- **Anthropic** (claude-sonnet-4-20250514)
- **Gemini** (gemini-2.0-flash)
- **Groq** (llama-3.3-70b-versatile)

## 🔐 Permisos

| Permiso | Uso |
|---------|-----|
| `INTERNET` | Conexión con la API |
| `RECORD_AUDIO` | Micrófono para reconocimiento de voz |
| `ACCESS_NETWORK_STATE` | Verificar estado de conexión |

## 🏗️ Arquitectura

```
app/src/main/java/com/nexa/ai/
├── MainActivity.kt          # Entry point
├── data/
│   ├── NexaRepository.kt    # API client (SSE streaming)
│   ├── SessionStore.kt      # Persistencia de sesiones
│   ├── UserStore.kt         # Auth local con salt
│   └── UpdateChecker.kt     # Verificación de actualizaciones
├── ui/
│   ├── NexaChatScreen.kt    # Composables de UI
│   └── theme/Theme.kt       # Material3 theme
└── viewmodel/
    └── NexaViewModel.kt     # ViewModel principal
```

## 📦 CI/CD

GitHub Actions builda automáticamente:
- **Debug APK** en cada push a `main`
- **Release APK** (firmado) cuando los secrets están configurados
- **GitHub Release** al crear tags `v*`

### Secrets necesarios para firma:

| Secret | Descripción |
|--------|-------------|
| `KEYSTORE_BASE64` | Keystore en base64 |
| `KEYSTORE_PASSWORD` | Password del keystore |
| `KEY_ALIAS` | Alias de la key |
| `KEY_PASSWORD` | Password de la key |

## 📄 Licencia

Proyecto privado.
