// Vercel Serverless Function: /api/chat
// Multi-provider AI chat with SSE streaming
// Supports: OpenAI, Anthropic, Gemini, Groq

export const config = {
  runtime: 'edge',
};

// ═══════════════════════════════════════
//  PROVIDERS
// ═══════════════════════════════════════

const PROVIDERS = {
  openai: {
    name: 'OpenAI',
    url: 'https://api.openai.com/v1/chat/completions',
    envKey: 'OPENAI_API_KEY',
    defaultModel: 'gpt-4o-mini',
    buildBody: (messages, model) => ({
      model: model || 'gpt-4o-mini',
      messages,
      stream: true,
      max_tokens: 2048,
      temperature: 0.7,
    }),
    headers: (apiKey) => ({
      'Authorization': `Bearer ${apiKey}`,
      'Content-Type': 'application/json',
    }),
    parseChunk: (line) => {
      if (!line.startsWith('data: ')) return null;
      const data = line.slice(6);
      if (data === '[DONE]') return { done: true };
      try {
        const json = JSON.parse(data);
        const delta = json.choices?.[0]?.delta;
        if (delta?.content) return { text: delta.content };
        return null;
      } catch { return null; }
    },
  },

  anthropic: {
    name: 'Claude',
    url: 'https://api.anthropic.com/v1/messages',
    envKey: 'ANTHROPIC_API_KEY',
    defaultModel: 'claude-sonnet-4-20250514',
    buildBody: (messages, model) => {
      // Extract system message
      const system = messages.find(m => m.role === 'system')?.content || SYSTEM_PROMPT;
      const chatMessages = messages.filter(m => m.role !== 'system');
      return {
        model: model || 'claude-sonnet-4-20250514',
        max_tokens: 2048,
        system,
        messages: chatMessages,
        stream: true,
      };
    },
    headers: (apiKey) => ({
      'x-api-key': apiKey,
      'anthropic-version': '2023-06-01',
      'Content-Type': 'application/json',
    }),
    parseChunk: (line) => {
      if (!line.startsWith('data: ')) return null;
      try {
        const json = JSON.parse(line.slice(6));
        if (json.type === 'content_block_delta' && json.delta?.text) {
          return { text: json.delta.text };
        }
        if (json.type === 'message_stop') return { done: true };
        return null;
      } catch { return null; }
    },
  },

  gemini: {
    name: 'Gemini',
    url: 'https://generativelanguage.googleapis.com/v1beta/models',
    envKey: 'GEMINI_API_KEY',
    defaultModel: 'gemini-2.0-flash',
    buildBody: (messages, model) => {
      const contents = messages
        .filter(m => m.role !== 'system')
        .map(m => ({
          role: m.role === 'assistant' ? 'model' : 'user',
          parts: [{ text: m.content }],
        }));
      const system = messages.find(m => m.role === 'system')?.content || SYSTEM_PROMPT;
      return {
        contents,
        systemInstruction: { parts: [{ text: system }] },
        generationConfig: {
          maxOutputTokens: 2048,
          temperature: 0.7,
        },
      };
    },
    getUrl: (model, apiKey) => {
      const m = model || 'gemini-2.0-flash';
      return `https://generativelanguage.googleapis.com/v1beta/models/${m}:streamGenerateContent?alt=sse&key=${apiKey}`;
    },
    headers: () => ({
      'Content-Type': 'application/json',
    }),
    parseChunk: (line) => {
      if (!line.startsWith('data: ')) return null;
      try {
        const json = JSON.parse(line.slice(6));
        const text = json.candidates?.[0]?.content?.parts?.[0]?.text;
        if (text) return { text };
        return null;
      } catch { return null; }
    },
  },

  groq: {
    name: 'Groq',
    url: 'https://api.groq.com/openai/v1/chat/completions',
    envKey: 'GROQ_API_KEY',
    defaultModel: 'llama-3.3-70b-versatile',
    buildBody: (messages, model) => ({
      model: model || 'llama-3.3-70b-versatile',
      messages,
      stream: true,
      max_tokens: 2048,
      temperature: 0.7,
    }),
    headers: (apiKey) => ({
      'Authorization': `Bearer ${apiKey}`,
      'Content-Type': 'application/json',
    }),
    parseChunk: (line) => {
      if (!line.startsWith('data: ')) return null;
      const data = line.slice(6);
      if (data === '[DONE]') return { done: true };
      try {
        const json = JSON.parse(data);
        const delta = json.choices?.[0]?.delta;
        if (delta?.content) return { text: delta.content };
        return null;
      } catch { return null; }
    },
  },
};

// ═══════════════════════════════════════
//  SYSTEM PROMPT
// ═══════════════════════════════════════

const SYSTEM_PROMPT = `Eres NEXA PRO, un asistente de IA avanzado. Características:
- Respondes en el idioma del usuario (español por defecto)
- Eres directo, útil y conciso
- Puedes ayudar con programación, análisis, creatividad, preguntas generales
- Tienes personalidad: eres inteligente, ligeramente ingenioso, y siempre servicial
- Si te preguntan qué eres, dices que eres NEXA PRO
- No uses markdown excesivo, sé natural en tus respuestas
- Si el usuario te saluda, responde de forma amigable y breve`;

// ═══════════════════════════════════════
//  HANDLER
// ═══════════════════════════════════════

export default async function handler(req) {
  // CORS
  if (req.method === 'OPTIONS') {
    return new Response(null, {
      headers: {
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'POST, OPTIONS',
        'Access-Control-Allow-Headers': 'Content-Type',
      },
    });
  }

  if (req.method !== 'POST') {
    return new Response(JSON.stringify({ error: 'Method not allowed' }), {
      status: 405,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  try {
    const body = await req.json();
    const { messages, provider: requestedProvider, model: requestedModel } = body;

    if (!messages || !Array.isArray(messages) || messages.length === 0) {
      return new Response(JSON.stringify({ error: 'Messages array required' }), {
        status: 400,
        headers: { 'Content-Type': 'application/json' },
      });
    }

    // Add system prompt if not present
    if (!messages.some(m => m.role === 'system')) {
      messages.unshift({ role: 'system', content: SYSTEM_PROMPT });
    }

    // Select provider: requested → env default → first available
    const providerName = requestedProvider || process.env.DEFAULT_PROVIDER || findAvailableProvider();
    const provider = PROVIDERS[providerName];

    if (!provider) {
      return new Response(JSON.stringify({ error: `Unknown provider: ${providerName}` }), {
        status: 400,
        headers: { 'Content-Type': 'application/json' },
      });
    }

    const apiKey = process.env[provider.envKey];
    if (!apiKey) {
      return new Response(JSON.stringify({
        error: `${provider.name} API key not configured. Set ${provider.envKey} in Vercel.`,
        provider: providerName,
      }), {
        status: 500,
        headers: { 'Content-Type': 'application/json' },
      });
    }

    // Build request
    const url = provider.getUrl
      ? provider.getUrl(requestedModel, apiKey)
      : provider.url;
    const headers = provider.headers(apiKey);
    const bodyData = provider.buildBody(messages, requestedModel);

    // Call provider
    const upstream = await fetch(url, {
      method: 'POST',
      headers,
      body: JSON.stringify(bodyData),
    });

    if (!upstream.ok) {
      const errText = await upstream.text();
      console.error(`${provider.name} error:`, upstream.status, errText);
      return new Response(JSON.stringify({
        error: `${provider.name} error: ${upstream.status}`,
        details: errText.slice(0, 200),
      }), {
        status: upstream.status,
        headers: { 'Content-Type': 'application/json' },
      });
    }

    // Stream response
    const encoder = new TextEncoder();
    const decoder = new TextDecoder();

    const stream = new ReadableStream({
      async start(controller) {
        // Send provider info
        controller.enqueue(encoder.encode(`data: ${JSON.stringify({ provider: provider.name })}\n\n`));

        const reader = upstream.body.getReader();
        let buffer = '';

        try {
          while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split('\n');
            buffer = lines.pop() || '';

            for (const line of lines) {
              const trimmed = line.trim();
              if (!trimmed) continue;

              const parsed = provider.parseChunk(trimmed);
              if (parsed) {
                if (parsed.done) {
                  controller.enqueue(encoder.encode(`data: {"done":true}\n\n`));
                  controller.enqueue(encoder.encode(`data: [DONE]\n\n`));
                } else if (parsed.text) {
                  controller.enqueue(encoder.encode(`data: ${JSON.stringify({ text: parsed.text })}\n\n`));
                }
              }
            }
          }
        } catch (err) {
          console.error('Stream error:', err);
          controller.enqueue(encoder.encode(`data: ${JSON.stringify({ error: 'Stream error: ' + err.message })}\n\n`));
        } finally {
          controller.close();
        }
      },
    });

    return new Response(stream, {
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        'Connection': 'keep-alive',
        'Access-Control-Allow-Origin': '*',
      },
    });

  } catch (err) {
    console.error('Handler error:', err);
    return new Response(JSON.stringify({ error: 'Internal server error: ' + err.message }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' },
    });
  }
}

function findAvailableProvider() {
  if (process.env.GROQ_API_KEY) return 'groq';
  if (process.env.OPENAI_API_KEY) return 'openai';
  if (process.env.GEMINI_API_KEY) return 'gemini';
  if (process.env.ANTHROPIC_API_KEY) return 'anthropic';
  return 'openai'; // default fallback
}
