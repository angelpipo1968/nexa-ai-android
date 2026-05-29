# NEXA PRO AI Assistant

Bot de Lark Suite integrado con Dify AI para asistencia inteligente.

## Despliegue

Este servidor está diseñado para desplegarse en Render.com (gratuito).

### Variables de Entorno Requeridas

| Variable | Descripción |
|----------|-------------|
| `LARK_APP_ID` | App ID de Lark Suite |
| `LARK_APP_SECRET` | App Secret de Lark Suite |
| `LARK_VERIFICATION_TOKEN` | Token de verificación de eventos |
| `LARK_ENCRYPT_KEY` | Clave de cifrado de eventos |
| `DIFY_API_KEY` | API Key de Dify |
| `DIFY_BASE_URL` | URL base de Dify API |

### Endpoints

- `GET /` - Estado del servicio
- `GET /health` - Health check
- `POST /webhook/lark` - Webhook de eventos de Lark

### Comandos del Bot

- `/chat [pregunta]` - Chatea con la IA
- `/search [tema]` - Busca en la web
- `/image [descripción]` - Genera imágenes
- `/document [tema]` - Crea documentos
- `/help` - Muestra ayuda
