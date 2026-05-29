require('dotenv').config();
const lark = require('@larksuiteoapi/node-sdk');
const axios = require('axios');

// ============ CONFIGURATION ============
const CONFIG = {
  LARK_APP_ID: process.env.LARK_APP_ID || '',
  LARK_APP_SECRET: process.env.LARK_APP_SECRET || '',
  LARK_VERIFICATION_TOKEN: process.env.LARK_VERIFICATION_TOKEN || '',
  LARK_ENCRYPT_KEY: process.env.LARK_ENCRYPT_KEY || '',
  DIFY_API_KEY: process.env.DIFY_API_KEY || '',
  DIFY_BASE_URL: process.env.DIFY_BASE_URL || 'https://api.dify.ai/v1',
  PORT: process.env.PORT || 3000,
  RATE_LIMIT_WINDOW: 60000,
  RATE_LIMIT_MAX: 30,
  MAX_CONVERSATION_AGE: 24 * 60 * 60 * 1000,
};

// ============ LARK CLIENT (WebSocket Mode) ============
const client = new lark.Client({
  appId: CONFIG.LARK_APP_ID,
  appSecret: CONFIG.LARK_APP_SECRET,
  domain: lark.Domain.Lark,
});

// ============ IN-MEMORY STORAGE ============
const conversationStore = new Map();
const rateLimitStore = new Map();

// ============ RATE LIMITING ============
function checkRateLimit(userId) {
  const now = Date.now();
  const userLimit = rateLimitStore.get(userId);
  if (!userLimit || now > userLimit.resetTime) {
    rateLimitStore.set(userId, { count: 1, resetTime: now + CONFIG.RATE_LIMIT_WINDOW });
    return true;
  }
  if (userLimit.count >= CONFIG.RATE_LIMIT_MAX) return false;
  userLimit.count++;
  return true;
}

// ============ CONVERSATION CLEANUP ============
setInterval(() => {
  const now = Date.now();
  for (const [userId, data] of conversationStore.entries()) {
    if (now - data.lastActivity > CONFIG.MAX_CONVERSATION_AGE) {
      conversationStore.delete(userId);
    }
  }
}, 60 * 60 * 1000);

// ============ DIFY AI INTEGRATION ============
async function callDifyAI(query, userId, conversationId = null) {
  if (!CONFIG.DIFY_API_KEY) {
    return '⚠️ Dify API Key no configurada. Por favor configura DIFY_API_KEY en las variables de entorno del servidor.';
  }

  try {
    const payload = {
      inputs: {},
      query: query,
      user: `lark_${userId}`,
      response_mode: 'blocking',
    };

    if (conversationId) {
      payload.conversation_id = conversationId;
    }

    const response = await axios.post(
      `${CONFIG.DIFY_BASE_URL}/chat-messages`,
      payload,
      {
        headers: {
          'Authorization': `Bearer ${CONFIG.DIFY_API_KEY}`,
          'Content-Type': 'application/json',
        },
        timeout: 120000,
      }
    );

    if (response.data.conversation_id) {
      conversationStore.set(userId, {
        conversationId: response.data.conversation_id,
        lastActivity: Date.now(),
      });
    }

    return response.data.answer || 'No se pudo obtener respuesta de la IA.';
  } catch (error) {
    console.error('[DIFY] Error:', error.response?.data || error.message);
    if (error.response?.status === 401) return '❌ Error de autenticación con Dify. Verifica la API Key.';
    if (error.response?.status === 429) return '⏳ Demasiadas solicitudes. Espera un momento.';
    if (error.code === 'ECONNABORTED') return '⏱️ La IA tardó demasiado. Intenta con una pregunta más corta.';
    return '❌ Error al comunicarse con la IA. Intenta de nuevo en un momento.';
  }
}

// ============ MESSAGE SENDING HELPERS ============
async function sendCardMessage(chatId, title, content) {
  try {
    await client.im.message.create({
      params: { receive_id_type: 'chat_id' },
      data: {
        receive_id: chatId,
        msg_type: 'interactive',
        content: JSON.stringify({
          config: { wide_screen_mode: true },
          header: {
            title: { tag: 'plain_text', content: title },
            template: 'blue',
          },
          elements: [{ tag: 'markdown', content }],
        }),
      },
    });
  } catch (error) {
    console.error('[SEND] Card error:', error.message);
  }
}

async function sendTextMessage(chatId, text) {
  try {
    await client.im.message.create({
      params: { receive_id_type: 'chat_id' },
      data: {
        receive_id: chatId,
        msg_type: 'text',
        content: JSON.stringify({ text }),
      },
    });
  } catch (error) {
    console.error('[SEND] Text error:', error.message);
  }
}

// ============ EVENT HANDLER ============
async function handleImMessageReceive(data) {
  const msgId = data.message.message_id;
  const chatId = data.message.chat_id;
  const msgType = data.message.message_type;
  const senderType = data.sender.sender_type;

  // Skip bot's own messages
  if (senderType === 'app') return;

  const userId = data.sender.sender_id.open_id || data.sender.sender_id.user_id || 'unknown';

  // Parse message text
  let text = '';
  if (msgType === 'text') {
    try {
      const content = JSON.parse(data.message.content);
      text = content.text || '';
    } catch (e) {
      text = data.message.content || '';
    }
  } else if (msgType === 'post') {
    try {
      const content = JSON.parse(data.message.content);
      text = content.title || '';
      if (content.content) {
        for (const line of content.content) {
          for (const element of line) {
            if (element.text) text += element.text;
          }
        }
      }
    } catch (e) {
      text = '';
    }
  } else {
    await sendTextMessage(chatId, `👋 Recibí tu mensaje de tipo "${msgType}". Solo puedo procesar texto. Usa /help para ver los comandos.`);
    return;
  }

  text = text.trim();
  if (!text) return;

  // Rate limit check
  if (!checkRateLimit(userId)) {
    await sendTextMessage(chatId, '⏳ Has enviado muchos mensajes. Espera un momento.');
    return;
  }

  console.log(`[MSG] User ${userId}: ${text.substring(0, 100)}`);

  // Route commands
  if (text === '/help' || text === '/ayuda') {
    await sendCardMessage(chatId, '❓ Ayuda - NEXA PRO AI',
      '**🤖 NEXA PRO AI Assistant**\n\n' +
      'Comandos disponibles:\n\n' +
      '💬 **/chat** `[pregunta]` — Chatea con la IA\n' +
      '🔍 **/search** `[tema]` — Busca en la web\n' +
      '🖼️ **/image** `[descripción]` — Genera imágenes\n' +
      '📄 **/document** `[tema]` — Crea documentos\n' +
      '❓ **/help** — Muestra esta ayuda\n\n' +
      '💡 **Tip:** También puedes escribir directamente sin comando y te responderé.\n\n' +
      '🔄 **Memoria:** Recuerdo la conversación por 24 horas.\n' +
      '⚡ **Límites:** 30 mensajes/minuto por usuario.'
    );
    return;
  }

  // Process AI request
  let query = text;
  let title = '🤖 NEXA PRO AI';

  if (text.startsWith('/chat ')) {
    query = text.replace(/^\/chat\s*/i, '').trim();
  } else if (text.startsWith('/search ')) {
    query = `Busca información actualizada sobre: ${text.replace(/^\/search\s*/i, '').trim()}\n\nProporciona un resumen con las fuentes más relevantes.`;
    title = '🔍 Resultados de Búsqueda';
  } else if (text.startsWith('/image ')) {
    query = `Genera una imagen de: ${text.replace(/^\/image\s*/i, '').trim()}. Si no puedes generar imágenes, describe cómo sería en detalle.`;
    title = '🖼️ Imagen Generada';
  } else if (text.startsWith('/document ')) {
    query = `Crea un documento bien estructurado sobre: ${text.replace(/^\/document\s*/i, '').trim()}\n\nIncluye: título, introducción, secciones principales, conclusiones y recomendaciones.`;
    title = '📄 Documento';
  } else if (text.startsWith('/new') || text.startsWith('/reset')) {
    conversationStore.delete(userId);
    await sendCardMessage(chatId, '🔄 Nueva Conversación', 'Se ha iniciado una nueva conversación. La memoria anterior ha sido borrada.');
    return;
  }

  if (!query) {
    await sendCardMessage(chatId, '💬 Chat con IA', 'Envía un mensaje después del comando.\n**Ejemplo:** `/chat ¿Cuál es la capital de Francia?`\nO simplemente escribe tu mensaje directamente.');
    return;
  }

  // Send "typing" reaction
  try {
    await client.im.message.patch({
      path: { message_id: msgId },
      data: { reactions: [{ reaction_type: 'ThumbUp' }] },
    });
  } catch (e) {
    // Reaction is optional
  }

  // Call Dify AI
  const convData = conversationStore.get(userId);
  const response = await callDifyAI(query, userId, convData?.conversationId);

  // Truncate very long responses
  const maxLen = 4000;
  const displayResponse = response.length > maxLen
    ? response.substring(0, maxLen) + '\n\n... *(respuesta truncada)*'
    : response;

  await sendCardMessage(chatId, title, displayResponse);
  console.log(`[RESP] Sent response to ${userId} (${displayResponse.length} chars)`);
}

// ============ WS EVENT REGISTRATION ============
const eventDispatcher = new lark.EventDispatcher({}).register({
  'im.message.receive_v1': handleImMessageReceive,
});

// ============ START WITH WEBSOCKET ============
const wsClient = new lark.WSClient({
  appId: CONFIG.LARK_APP_ID,
  appSecret: CONFIG.LARK_APP_SECRET,
  domain: lark.Domain.Lark,
});

// Also start a simple HTTP server for health checks
const express = require('express');
const httpApp = express();
httpApp.use(express.json());

httpApp.get('/', (req, res) => {
  res.json({
    status: 'online',
    service: 'NEXA PRO AI Assistant',
    version: '2.0.0',
    mode: 'websocket',
    difyConfigured: !!CONFIG.DIFY_API_KEY,
  });
});

httpApp.get('/health', (req, res) => {
  res.json({ status: 'healthy', uptime: process.uptime() });
});

// Also support HTTP webhook as fallback
httpApp.post('/webhook/lark', async (req, res) => {
  try {
    const body = req.body;
    if (body.type === 'url_verification') {
      return res.json({ challenge: body.challenge });
    }
    if (CONFIG.LARK_VERIFICATION_TOKEN && body.token !== CONFIG.LARK_VERIFICATION_TOKEN) {
      return res.status(403).json({ error: 'Invalid token' });
    }
    const event = body.event;
    if (event && body.header?.event_type === 'im.message.receive_v1') {
      setImmediate(async () => {
        try { await handleImMessageReceive(event); } catch (e) { console.error('[WEBHOOK] Process error:', e); }
      });
    }
    res.json({ code: 0 });
  } catch (e) {
    res.status(500).json({ error: 'Internal error' });
  }
});

httpApp.listen(CONFIG.PORT, '0.0.0.0', () => {
  console.log(`[HTTP] Health server on port ${CONFIG.PORT}`);
});

// Start WebSocket connection
wsClient.start({ eventDispatcher }).then(() => {
  console.log('[WS] ✅ Connected to Lark via WebSocket!');
  console.log('[WS] Bot is now online and listening for messages.');
  console.log('');
  console.log('╔══════════════════════════════════════════╗');
  console.log('║   🤖 NEXA PRO AI Assistant v2.0          ║');
  console.log('║   Mode: WebSocket (no URL needed!)       ║');
  console.log('║   Dify: ' + (CONFIG.DIFY_API_KEY ? '✅ Configured' : '❌ Not configured') + '                  ║');
  console.log('╚══════════════════════════════════════════╝');
}).catch((err) => {
  console.error('[WS] ❌ Failed to connect:', err.message);
  console.error('[WS] Check your LARK_APP_ID and LARK_APP_SECRET');
});

module.exports = { client, wsClient };
