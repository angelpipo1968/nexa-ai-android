# NEXA PRO AI Assistant v3.0

Bot de Lark Suite con integración multi-AI y múltiples servicios.

## Arquitectura

```
Lark Suite ──WebSocket/HTTP──► Express Server ──► AI Providers (Dify/OpenAI/Gemini)
                                      │
                                      ├──► Weather (OpenWeatherMap / wttr.in)
                                      ├──► Finance (Alpha Vantage / CoinGecko / ExchangeRate)
                                      ├──► News (NewsAPI)
                                      ├──► Translation (DeepL)
                                      ├──► Web Search (Google / Brave)
                                      ├──► Code Analysis (AI)
                                      ├──► Calculator (Wolfram Alpha / AI)
                                      ├──► Movies (TMDB)
                                      ├──► NASA APOD
                                      ├──► Flights (AviationStack)
                                      ├──► QR Codes (QR Server API)
                                      └──► Jokes & Facts (JokeAPI / UselessFacts)
```

## Modos de Conexión

- **WebSocket** (recomendado): No necesita URL pública, se conecta automáticamente a Lark
- **HTTP Webhook**: Requiere URL pública, útil para Render/Vercel sin WebSocket

## Despliegue en Render.com

1. Fork/clone este repositorio
2. Conecta a Render.com con el `render.yaml`
3. Configura las variables de entorno necesarias

## Variables de Entorno

### Requeridas (mínimo para funcionar)

| Variable | Descripción |
|----------|-------------|
| `LARK_APP_ID` | App ID de Lark Suite |
| `LARK_APP_SECRET` | App Secret de Lark Suite |
| `DIFY_API_KEY` | API Key de Dify (proveedor principal) |

### Opcionales (activan servicios adicionales)

| Variable | Descripción | Servicio |
|----------|-------------|----------|
| `OPENAI_API_KEY` | API Key de OpenAI | IA fallback |
| `OPENAI_BASE_URL` | URL base OpenAI compatible | IA fallback |
| `OPENAI_MODEL` | Modelo a usar (default: gpt-4o-mini) | IA fallback |
| `GEMINI_API_KEY` | API Key de Google Gemini | IA fallback |
| `WEATHER_API_KEY` | API Key de OpenWeatherMap | Clima |
| `ALPHA_VANTAGE_KEY` | API Key de Alpha Vantage | Finanzas |
| `NEWS_API_KEY` | API Key de NewsAPI | Noticias |
| `DEEPL_API_KEY` | API Key de DeepL | Traducción |
| `GOOGLE_SEARCH_KEY` | API Key de Google Custom Search | Búsqueda web |
| `GOOGLE_SEARCH_CX` | Custom Search Engine ID | Búsqueda web |
| `BRAVE_SEARCH_KEY` | API Key de Brave Search | Búsqueda web |
| `STABILITY_API_KEY` | API Key de Stability AI | Generación de imágenes |
| `NASA_API_KEY` | API Key de NASA | Imagen astronómica |
| `TMDB_API_KEY` | API Key de The Movie DB | Películas/Series |
| `WOLFRAM_APP_ID` | App ID de Wolfram Alpha | Calculadora |
| `AVIATIONSTACK_KEY` | API Key de AviationStack | Vuelos |
| `ADMIN_SECRET` | Secreto para panel admin | Administración |

## Comandos del Bot

### IA y Chat
- `/chat [pregunta]` — Chatea con la IA (Dify → OpenAI → Gemini fallback)
- `/search [tema]` — Búsqueda web con fuentes
- `/image [desc]` — Generación de imágenes
- `/document [tema]` — Crea documentos estructurados

### Clima y Viajes
- `/weather [ciudad]` — Clima actual (funciona sin API key con wttr.in)
- `/forecast [ciudad]` — Pronóstico 5 días
- `/flight [origen] [destino] [fecha]` — Busca vuelos
- `/time [ciudad]` — Hora en ciudades del mundo

### Finanzas
- `/stock [símbolo]` — Cotizaciones de acciones (AAPL, TSLA, etc.)
- `/crypto [moneda]` — Precios de criptomonedas (bitcoin, ethereum, etc.)
- `/exchange [cantidad] [de] [a]` — Conversión de monedas

### Información
- `/news [tema]` — Noticias actuales
- `/movie [nombre]` — Info de películas y series
- `/nasa` — Imagen astronómica del día (NASA APOD)

### Herramientas
- `/translate [texto] [idioma]` — Traductor (es, en, fr, de, pt, ja, ko, zh)
- `/code [código]` — Análisis y mejora de código
- `/calc [expresión]` — Calculadora avanzada
- `/qr [texto/URL]` — Genera código QR

### Entretenimiento
- `/joke` — Chiste aleatorio
- `/fact` — Dato curioso

### Sistema
- `/new` — Nueva conversación (borra memoria)
- `/status` — Estado del sistema y APIs configuradas
- `/admin [secreto]` — Panel de administración
- `/help` — Muestra la ayuda completa

## Endpoints HTTP

- `GET /` — Estado del servicio
- `GET /health` — Health check detallado
- `POST /webhook/lark` — Webhook de Lark (HTTP fallback)
- `POST /api/chat` — API de chat (JSON: {query, userId, provider})
- `GET /api/weather/:city` — API de clima
- `GET /api/stock/:symbol` — API de acciones
- `GET /api/crypto/:symbol` — API de criptomonedas
- `GET /api/exchange/:from/:to/:amount?` — API de conversión
- `GET /api/news/:query?` — API de noticias
- `POST /api/translate` — API de traducción (JSON: {text, targetLang})
- `GET /api/search/:query` — API de búsqueda
- `GET /api/nasa` — API de NASA APOD
- `GET /api/movie/:query` — API de películas
- `GET /api/admin/stats` — Estadísticas admin (requiere auth)

## Librerías Incluidas

| Librería | Función |
|----------|---------|
| `@larksuiteoapi/node-sdk` | SDK oficial de Lark Suite |
| `express` | Servidor HTTP |
| `axios` | Cliente HTTP para APIs |
| `dotenv` | Variables de entorno |
| `cors` | Cross-Origin Resource Sharing |
| `helmet` | Seguridad HTTP headers |
| `compression` | Compresión gzip |
| `express-rate-limit` | Rate limiting HTTP |
| `morgan` | Logging HTTP |
| `winston` | Logging avanzado |
| `node-cron` | Tareas programadas |
| `crypto-js` | Encriptación |
| `uuid` | IDs únicos |
| `lodash` | Utilidades JavaScript |
| `cheerio` | HTML parsing/scraping |
| `markdown-it` | Renderizado Markdown |
| `sanitize-html` | Sanitización HTML |

## APIs Externas Integradas

| API | Función | Requiere Key |
|-----|---------|--------------|
| Dify AI | Chat con IA (workflow) | ✅ |
| OpenAI | Chat con IA (fallback) | ✅ |
| Google Gemini | Chat con IA (fallback) | ✅ |
| OpenWeatherMap | Clima y pronósticos | ✅ |
| wttr.in | Clima (sin API key) | ❌ |
| Alpha Vantage | Acciones y finanzas | ✅ |
| CoinGecko | Criptomonedas | ❌ |
| ExchangeRate-API | Tasas de cambio | ❌ |
| NewsAPI | Noticias | ✅ |
| DeepL | Traducción | ✅ |
| Google Custom Search | Búsqueda web | ✅ |
| Brave Search | Búsqueda web | ✅ |
| Stability AI | Generación de imágenes | ✅ |
| NASA APOD | Imagen astronómica | ⚠️ (demo) |
| TMDB | Películas y series | ✅ |
| Wolfram Alpha | Cálculos avanzados | ✅ |
| AviationStack | Vuelos | ✅ |
| QR Server API | Códigos QR | ❌ |
| JokeAPI | Chistes | ❌ |
| UselessFacts | Datos curiosos | ❌ |
