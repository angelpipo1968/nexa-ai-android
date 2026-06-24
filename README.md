# Nexa AI — APK Releases

APKs precompiladas para Android. Cada push a `capacitor-wrapper-v2` genera una nueva versión via GitHub Actions.

## Última versión

- **Archivo:** `nexa-ai-debug.apk`
- **Tamaño:** 5.3 MB
- **Compilada:** 2026-06-24
- **Versión del fix:** TTS nativo + dominio `https://www.nexa-ai.dev` primario + fallback LAN + botón Reintentar

## Cómo instalar

1. Descargar `nexa-ai-debug.apk` desde este repo
2. En Android: tocar el archivo descargado
3. Permitir "instalar apps de origen desconocido" si lo pide
4. Si Google Play Protect bloquea: "Instalar de todos modos"
5. Abrir el icono **Nexa AI** del launcher (NO abrir desde navegador)

## Cambios incluidos

- Server primario: `https://www.nexa-ai.dev` (funciona desde cualquier red)
- Fallback automático: `http://192.168.50.158:3000` si el dominio falla
- TTS nativo Android (fix para "manos libres no habla")
- Pantalla offline con botón "Reintentar"
- Permisos runtime: RECORD_AUDIO + POST_NOTIFICATIONS (Android 13+)
