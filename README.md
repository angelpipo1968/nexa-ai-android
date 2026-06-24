# Nexa AI — APK Releases

APKs precompiladas para Android. Cada push a `capacitor-wrapper-v2` genera una nueva versión via GitHub Actions.

## Última versión (v2 - 2026-06-24)

- **Archivo:** `nexa-ai-debug.apk`
- **Tamaño:** 5.3 MB
- **Compilada:** 2026-06-24 (Run #17)
- **Commit:** `8a0b634` - "fix(android): remove LAN fallback to avoid blank screen on non-home networks"

## Cambios incluidos

- ✅ **Server único:** `https://www.nexa-ai.dev` (funciona desde cualquier red: WiFi, 4G, 5G)
- ✅ **Sin fallback a LAN** (elimina pantalla blanca cuando no estás en tu WiFi de casa)
- ✅ **TTS nativo Android** (fix para "manos libres no habla")
- ✅ **Pantalla offline** con botón "Reintentar"
- ✅ **Permisos runtime:** RECORD_AUDIO + POST_NOTIFICATIONS (Android 13+)

## Cómo instalar

1. Descargar `nexa-ai-debug.apk` desde este repo
2. En Android: tocar el archivo descargado
3. Permitir "instalar apps de origen desconocido" si lo pide
4. Si Google Play Protect bloquea: "Instalar de todos modos"
5. Abrir el icono **Nexa AI** del launcher (NO abrir desde navegador)

## Cómo probar el fix de voz

1. Una vez abierta la app → carga `https://www.nexa-ai.dev` automáticamente
2. Tocá **"Manos Libres"** abajo → hablás
3. Debería responder **con voz** (fix TTS nativo)
