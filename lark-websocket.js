import * as Lark from '@larksuiteoapi/node-sdk';
import path from 'path';
import fs from 'fs';

// --- AUTOMATIC ENV LOADER (.env.local) ---
function loadEnv() {
    try {
        const envPath = path.resolve(process.cwd(), '.env.local');
        if (fs.existsSync(envPath)) {
            const content = fs.readFileSync(envPath, 'utf8');
            content.split(/\r?\n/).forEach(line => {
                const trimmed = line.trim();
                if (!trimmed || trimmed.startsWith('#')) return;
                const match = trimmed.match(/^\s*([\w.-]+)\s*=\s*(.*)?\s*$/);
                if (match) {
                    const key = match[1];
                    let value = match[2] || '';
                    if (value.startsWith('"') && value.endsWith('"')) value = value.slice(1, -1);
                    if (value.startsWith("'") && value.endsWith("'")) value = value.slice(1, -1);
                    process.env[key] = value.trim();
                }
            });
            console.log('[Lark-WS] .env.local cargado con éxito.');
        }
    } catch (e) {
        console.warn('[Lark-WS] No se pudo cargar .env.local:', e.message);
    }
}
loadEnv();

const LARK_DOMAIN = 'https://open.larksuite.com';
const FEISHU_DOMAIN = 'https://open.feishu.cn';

// Helper to acquire tenant_access_token
async function getTenantAccessToken(appId, appSecret) {
    const body = { app_id: appId, app_secret: appSecret };
    const headers = { 'Content-Type': 'application/json' };
    try {
        const res = await fetch(`${LARK_DOMAIN}/open-apis/auth/v3/tenant_access_token/internal`, {
            method: 'POST',
            headers,
            body: JSON.stringify(body)
        });
        if (res.ok) {
            const data = await res.json();
            if (data.tenant_access_token) return data.tenant_access_token;
        }
    } catch {}
    try {
        const res = await fetch(`${FEISHU_DOMAIN}/open-apis/auth/v3/tenant_access_token/internal`, {
            method: 'POST',
            headers,
            body: JSON.stringify(body)
        });
        if (res.ok) {
            const data = await res.json();
            if (data.tenant_access_token) return data.tenant_access_token;
        }
    } catch {}
    return null;
}

// Helper to reply to a message
async function replyToLarkMessage(token, messageId, text) {
    const body = {
        content: JSON.stringify({ text }),
        msg_type: 'text'
    };
    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
    };
    try {
        const res = await fetch(`${LARK_DOMAIN}/open-apis/im/v1/messages/${messageId}/reply`, {
            method: 'POST',
            headers,
            body: JSON.stringify(body)
        });
        if (res.ok) {
            const data = await res.json();
            if (data.code === 0) return true;
        }
    } catch {}
    try {
        const res = await fetch(`${FEISHU_DOMAIN}/open-apis/im/v1/messages/${messageId}/reply`, {
            method: 'POST',
            headers,
            body: JSON.stringify(body)
        });
        if (res.ok) {
            const data = await res.json();
            if (data.code === 0) return true;
        }
    } catch {}
    return false;
}

// Helper to send a new message
async function sendLarkMessage(token, receiveId, text, receiveIdType = 'open_id') {
    const body = {
        receive_id: receiveId,
        content: JSON.stringify({ text }),
        msg_type: 'text'
    };
    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
    };
    try {
        const res = await fetch(`${LARK_DOMAIN}/open-apis/im/v1/messages?receive_id_type=${receiveIdType}`, {
            method: 'POST',
            headers,
            body: JSON.stringify(body)
        });
        if (res.ok) {
            const data = await res.json();
            if (data.code === 0) return true;
        }
    } catch {}
    try {
        const res = await fetch(`${FEISHU_DOMAIN}/open-apis/im/v1/messages?receive_id_type=${receiveIdType}`, {
            method: 'POST',
            headers,
            body: JSON.stringify(body)
        });
        if (res.ok) {
            const data = await res.json();
            if (data.code === 0) return true;
        }
    } catch {}
    return false;
}

// Helper to query Dify Chat
async function getDifyResponse(apiKey, query, userId) {
    try {
        const res = await fetch('https://api.dify.ai/v1/chat-messages', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${apiKey}`
            },
            body: JSON.stringify({
                inputs: {},
                query: query,
                response_mode: 'blocking',
                user: userId
            })
        });
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.message || `HTTP ${res.status}`);
        }
        const data = await res.json();
        return data.answer || 'No he podido obtener una respuesta de Dify.';
    } catch (e) {
        console.error('[Dify] Error:', e.message);
        return `Error al conectar con Dify: ${e.message}`;
    }
}

// Helper to query Next.js chat
async function getNextChatResponse(query) {
    try {
        const res = await fetch('http://localhost:3000/api/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                messages: [{ role: 'user', content: query }]
            })
        });

        if (!res.ok) throw new Error(`HTTP ${res.status}`);

        const text = await res.text();
        // Parse SSE stream to rebuild full answer
        let fullResponse = '';
        const lines = text.split('\n');
        for (const line of lines) {
            const trimmed = line.trim();
            if (trimmed.startsWith('data: ')) {
                try {
                    const parsed = JSON.parse(trimmed.slice(6));
                    if (parsed.text) fullResponse += parsed.text;
                    if (parsed.done && parsed.fullResponse) fullResponse = parsed.fullResponse;
                } catch {}
            }
        }
        return fullResponse || 'No he podido generar una respuesta.';
    } catch (e) {
        console.error('[Next.js Chat] Error:', e.message);
        return `Error al conectar con Next.js Chat: ${e.message}`;
    }
}

// Init values
const appId = process.env.LARK_APP_ID;
const appSecret = process.env.LARK_APP_SECRET;

if (!appId || !appSecret) {
    console.error('❌ Error: LARK_APP_ID o LARK_APP_SECRET no están definidos en .env.local');
    process.exit(1);
}

// Define the dispatcher for events
const eventDispatcher = new Lark.EventDispatcher({});

// 1. Message received handler
eventDispatcher.register({
    type: 'im.message.receive_v1',
    callback: async (data) => {
        const message = data.message;
        const sender = data.sender;
        if (!message || !sender) return;

        const messageId = message.message_id;
        const openId = sender.sender_id?.open_id || 'unknown';
        const msgType = message.msg_type;

        if (msgType !== 'text') {
            console.log(`[Lark-WS] Mensaje ignorado de tipo: ${msgType}`);
            return;
        }

        let userQuery = '';
        try {
            const parsedContent = JSON.parse(message.content);
            userQuery = parsedContent.text || '';
        } catch (err) {
            console.error('[Lark-WS] Error decodificando contenido:', err.message);
            return;
        }

        if (!userQuery.trim()) return;
        console.log(`[Lark-WS] 💬 Mensaje recibido de ${openId}: "${userQuery}"`);

        // Fetch Lark token
        const token = await getTenantAccessToken(appId, appSecret);
        if (!token) return;

        // Choose provider
        const provider = process.env.LARK_BOT_PROVIDER || 'dify';
        let aiResponse = '';

        if (provider === 'dify') {
            const difyKey = process.env.DIFY_API_KEY;
            if (!difyKey) {
                aiResponse = 'Error: DIFY_API_KEY no está configurada.';
            } else {
                aiResponse = await getDifyResponse(difyKey, userQuery, openId);
            }
        } else {
            aiResponse = await getNextChatResponse(userQuery);
        }

        console.log(`[Lark-WS] 📤 Enviando respuesta a mensaje ${messageId}...`);
        await replyToLarkMessage(token, messageId, aiResponse);
    }
});

// 2. Custom menu click handler
eventDispatcher.register({
    type: 'application.bot.menu_v6',
    callback: async (data) => {
        const eventKey = data.event_key;
        const openId = data.operator?.operator_id?.open_id;
        if (!eventKey || !openId) return;

        console.log(`[Lark-WS] 📌 Click en menú: "${eventKey}" por usuario ${openId}`);

        const token = await getTenantAccessToken(appId, appSecret);
        if (!token) return;

        let responseMessage = '';
        const normalizedKey = eventKey.trim();
        switch (normalizedKey) {
            case 'search_web':
            case 'buscar_web':
            case 'Buscar en Web':
                responseMessage = '🌐 **Búsqueda Web Activa**\n\nPor favor, escribe lo que deseas buscar precedido por la palabra **/buscar** (por ejemplo:\n`/buscar clima en Madrid` o `/buscar últimas noticias sobre IA`).';
                break;
            case 'generate_image':
            case 'generar_imagen':
            case 'Generar Imagen':
                responseMessage = '🎨 **Diseño y Generación de Imágenes**\n\nPor favor, describe la imagen que deseas crear precedido por la palabra **/imagen** (por ejemplo:\n`/imagen un gato astronauta en estilo cyberpunk`).';
                break;
            case 'create_doc':
            case 'crear_documento':
            case 'Crear Documento':
                responseMessage = '📝 **Creación de Documentos Inteligentes**\n\nPor favor, escribe lo que necesitas redactar precedido por la palabra **/documento** (por ejemplo:\n`/documento contrato de confidencialidad estándar`).';
                break;
            case 'help':
            case 'ayuda':
            case 'Ayuda':
            default:
                responseMessage = '🤖 **NEXA PRO AI Assistant - Guía de Ayuda**\n\n¡Hola! Soy tu asistente inteligente integrado con el motor Nexa AI y Dify. Puedes interactuar conmigo de las siguientes maneras:\n\n💬 **Chat Libre**: Escríbeme cualquier pregunta directamente en este chat.\n\n🌐 **Búsqueda en Web**: Escribe `/buscar <tu consulta>` para realizar búsquedas en tiempo real.\n\n🎨 **Generador de Imágenes**: Escribe `/imagen <descripción>` para diseñar imágenes personalizadas.\n\n📝 **Creador de Documentos**: Escribe `/documento <detalles>` para redactar textos y documentos profesionales.\n\n📌 **Acceso Rápido**: Utiliza el menú personalizado ubicado abajo a la izquierda para ver estas opciones en cualquier momento.';
                break;
        }

        console.log(`[Lark-WS] 📤 Enviando respuesta de menú a ${openId}...`);
        await sendLarkMessage(token, openId, responseMessage);
    }
});

// Setup and start WebSocket Persistent Client
console.log('[Lark-WS] Iniciando cliente WebSocket de Lark (Conexión Persistente)...');
const wsClient = new Lark.WSClient({
    appId,
    appSecret,
    loggerLevel: Lark.LoggerLevel.info
});

wsClient.start({ eventDispatcher });
console.log('[Lark-WS] ¡Cliente WebSocket de Lark conectado de forma persistente y escuchando eventos!');
