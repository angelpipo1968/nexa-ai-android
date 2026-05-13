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
- Si el usuario te saluda, responde de forma amigable y breve
- Máximo 500 tokens por respuesta para mantener las respuestas concisas

CAPACIDADES ESPECIALES:
- LOTERÍA: Puedes consultar resultados de loterías (Melate, EuroMillones, Powerball, etc.) y generar números recomendados.
- VUELOS: Cuando el usuario pregunte por vuelos, rutas aéreas, o el estado de un vuelo, SIEMPRE incluye la información de vuelos que te proporciono en el contexto. Si no hay datos de vuelos disponibles, sugiérele al usuario que proporcione más detalles (ciudad origen, destino, fecha).

FORMATO DE RESPUESTA PARA VUELOS:
- Muestra aerolínea, número de vuelo, horario de salida y llegada
- Indica el estado del vuelo (a tiempo, retrasado, etc.)
- Si hay retraso, menciona cuántos minutos
- Sé conciso pero informativo`;

// ═══════════════════════════════════════
//  FLIGHT DETECTION & DATA FETCHING
// ═══════════════════════════════════════

// Common airport codes mapping
const AIRPORT_CODES = {
  // México
  'ciudad de mexico': 'MEX', 'cdmx': 'MEX', 'mexico city': 'MEX', 'guadalajara': 'GDL',
  'monterrey': 'MTY', 'cancun': 'CUN', 'tijuana': 'TIJ', 'puebla': 'PBC',
  'queretaro': 'QRO', 'mazatlan': 'MZT', 'puerto vallarta': 'PVR', 'los cabos': 'SJD',
  'merida': 'MID', 'oaxaca': 'OAX', 'acapulco': 'ACA', 'leon': 'BJX',
  // España
  'madrid': 'MAD', 'barcelona': 'BCN', 'malaga': 'AGP', 'sevilla': 'SVQ',
  'valencia': 'VLC', 'palma': 'PMI', 'bilbao': 'BIO', 'ibiza': 'IBZ',
  // USA
  'new york': 'JFK', 'nueva york': 'JFK', 'los angeles': 'LAX', 'miami': 'MIA',
  'chicago': 'ORD', 'houston': 'IAH', 'dallas': 'DFW', 'san francisco': 'SFO',
  'las vegas': 'LAS', 'orlando': 'MCO', 'atlanta': 'ATL', 'denver': 'DEN',
  'washington': 'IAD', 'boston': 'BOS', 'seattle': 'SEA',
  // Colombia
  'bogota': 'BOG', 'medellin': 'MDE', 'cali': 'CLO', 'cartagena': 'CTG',
  // Argentina
  'buenos aires': 'EZE', 'cordoba': 'COR',
  // Otros
  'london': 'LHR', 'londres': 'LHR', 'paris': 'CDG', 'roma': 'FCO',
  'tokyo': 'NRT', 'tokio': 'NRT', 'dubai': 'DXB', 'sao paulo': 'GRU',
  'lima': 'LIM', 'santiago': 'SCL', 'quito': 'UIO',
};

function detectFlightQuery(userMessage) {
  const msg = userMessage.toLowerCase();

  // Flight keywords
  const flightKeywords = [
    'vuelo', 'vuelos', 'volar', 'flight', 'flights', 'fly',
    'aeropuerto', 'airport', 'aerolinea', 'airline',
    'salida', 'llegada', 'departure', 'arrival',
    'boleto', 'boleto de avion', 'ticket', 'boarding',
    'retrasado', 'delayed', 'a tiempo', 'on time',
  ];

  const hasFlightKeyword = flightKeywords.some(kw => msg.includes(kw));

  // Route pattern: "de X a Y" or "X to Y" or "X - Y"
  const routePattern = /(?:de|from)\s+(\w[\w\s]*?)\s+(?:a|to|hasta)\s+(\w[\w\s]*?)(?:\s|$|,|\?|\.)/i;
  const routeMatch = msg.match(routePattern);

  // Flight number pattern: "AA100", "IB3200", "AM123"
  const flightNumPattern = /\b([A-Z]{2}\d{1,4})\b/i;
  const flightNumMatch = msg.match(flightNumPattern);

  // Date pattern
  const datePattern = /(\d{1,2})[\/\-](\d{1,2})(?:[\/\-](\d{2,4}))?/;
  const dateMatch = msg.match(datePattern);

  if (!hasFlightKeyword && !routeMatch && !flightNumMatch) return null;

  const result = { type: null, dep: null, arr: null, date: null, flight: null };

  if (flightNumMatch) {
    result.type = 'track';
    result.flight = flightNumMatch[1].toUpperCase();
    return result;
  }

  if (routeMatch) {
    result.type = 'search';
    const fromCity = routeMatch[1].trim().toLowerCase();
    const toCity = routeMatch[2].trim().toLowerCase();

    // Try to find airport codes
    result.dep = findAirportCode(fromCity);
    result.arr = findAirportCode(toCity);
  } else if (hasFlightKeyword) {
    // Try to extract any city names mentioned
    result.type = 'search';
    for (const [city, code] of Object.entries(AIRPORT_CODES)) {
      if (msg.includes(city)) {
        if (!result.dep) result.dep = code;
        else if (!result.arr) result.arr = code;
      }
    }
  }

  // Extract date
  if (dateMatch) {
    const day = dateMatch[1].padStart(2, '0');
    const month = dateMatch[2].padStart(2, '0');
    const year = dateMatch[3] || new Date().getFullYear();
    result.date = `${year}-${month}-${day}`;
  }

  // Only return if we have at least departure or flight number
  if (result.dep || result.flight) return result;
  return null;
}

function findAirportCode(city) {
  // Exact match first
  if (AIRPORT_CODES[city]) return AIRPORT_CODES[city];
  // Partial match
  for (const [key, code] of Object.entries(AIRPORT_CODES)) {
    if (city.includes(key) || key.includes(city)) return code;
  }
  // Check if it's already an IATA code
  if (/^[A-Z]{3}$/i.test(city)) return city.toUpperCase();
  return null;
}

async function fetchFlightData(flightQuery) {
  const apiKey = process.env.AVIATIONSTACK_KEY;
  if (!apiKey) return null;

  try {
    let url;
    if (flightQuery.type === 'track' && flightQuery.flight) {
      url = `http://api.aviationstack.com/v1/flights?access_key=${apiKey}&flight_iata=${flightQuery.flight}&limit=5`;
    } else if (flightQuery.type === 'search' && flightQuery.dep) {
      url = `http://api.aviationstack.com/v1/flights?access_key=${apiKey}&dep_iata=${flightQuery.dep}&limit=5`;
      if (flightQuery.arr) url += `&arr_iata=${flightQuery.arr}`;
      if (flightQuery.date) url += `&flight_date=${flightQuery.date}`;
    } else {
      return null;
    }

    const resp = await fetch(url);
    const data = await resp.json();

    if (!data.data || data.data.length === 0) return null;

    return data.data.map(f => ({
      airline: f.airline?.name || 'N/A',
      flight: f.flight?.iata || 'N/A',
      from: f.departure?.airport || f.departure?.iata || 'N/A',
      fromCode: f.departure?.iata || 'N/A',
      to: f.arrival?.airport || f.arrival?.iata || 'N/A',
      toCode: f.arrival?.iata || 'N/A',
      scheduledDep: f.departure?.scheduled || null,
      estimatedDep: f.departure?.estimated || null,
      scheduledArr: f.arrival?.scheduled || null,
      estimatedArr: f.arrival?.estimated || null,
      depDelay: f.departure?.delay || null,
      arrDelay: f.arrival?.delay || null,
      status: f.flight_status || 'unknown',
      terminal: f.departure?.terminal || null,
      gate: f.departure?.gate || null,
    }));
  } catch (err) {
    console.error('Flight API error:', err);
    return null;
  }
}

function formatFlightContext(flights) {
  if (!flights || flights.length === 0) return '';

  let ctx = '\n\n[DATOS DE VUELOS EN TIEMPO REAL]\n';
  flights.forEach((f, i) => {
    ctx += `\nVuelo ${i + 1}: ${f.airline} ${f.flight}\n`;
    ctx += `  Ruta: ${f.from} (${f.fromCode}) → ${f.to} (${f.toCode})\n`;
    if (f.scheduledDep) {
      const depTime = new Date(f.scheduledDep).toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' });
      ctx += `  Salida: ${depTime}`;
      if (f.depDelay) ctx += ` (retraso: ${f.depDelay} min)`;
      ctx += '\n';
    }
    if (f.scheduledArr) {
      const arrTime = new Date(f.scheduledArr).toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' });
      ctx += `  Llegada: ${arrTime}`;
      if (f.arrDelay) ctx += ` (retraso: ${f.arrDelay} min)`;
      ctx += '\n';
    }
    const statusMap = {
      'scheduled': 'Programado',
      'active': 'En vuelo',
      'landed': 'Aterrizado',
      'cancelled': 'Cancelado',
      'delayed': 'Retrasado',
      'diverted': 'Desviado',
      'incident': 'Incidente',
      'unknown': 'Desconocido',
    };
    ctx += `  Estado: ${statusMap[f.status] || f.status}\n`;
    if (f.terminal) ctx += `  Terminal: ${f.terminal}\n`;
    if (f.gate) ctx += `  Puerta: ${f.gate}\n`;
  });
  ctx += '[FIN DATOS DE VUELOS]\n';
  return ctx;
}

// ═══════════════════════════════════════
//  HANDLER
// ═══════════════════════════════════════

export default async function handler(req) {
  // CORS
  const allowedOrigins = [
    'https://www.nexa-ai.dev',
    'https://nexa-ai.dev',
    'http://localhost:3000',
  ];
  const origin = req.headers.get('origin') || '';
  const corsOrigin = allowedOrigins.includes(origin) ? origin : allowedOrigins[0];

  if (req.method === 'OPTIONS') {
    return new Response(null, {
      headers: {
        'Access-Control-Allow-Origin': corsOrigin,
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

    // Validate and sanitize messages
    const sanitizedMessages = messages
      .filter(m => m && typeof m === 'object' && typeof m.content === 'string' && m.content.trim())
      .map(m => ({
        role: ['user', 'assistant', 'system'].includes(m.role) ? m.role : 'user',
        content: m.content.trim().slice(0, 10000), // Limit message length
      }))
      .slice(-50); // Keep only last 50 messages for context window

    if (sanitizedMessages.length === 0) {
      return new Response(JSON.stringify({ error: 'No valid messages' }), {
        status: 400,
        headers: { 'Content-Type': 'application/json' },
      });
    }

    // Add system prompt if not present
    if (!sanitizedMessages.some(m => m.role === 'system')) {
      sanitizedMessages.unshift({ role: 'system', content: SYSTEM_PROMPT });
    }

    // Detect flight queries and fetch real data
    const lastUserMsg = [...sanitizedMessages].reverse().find(m => m.role === 'user');
    if (lastUserMsg) {
      const flightQuery = detectFlightQuery(lastUserMsg.content);
      if (flightQuery) {
        const flightData = await fetchFlightData(flightQuery);
        if (flightData && flightData.length > 0) {
          const flightContext = formatFlightContext(flightData);
          // Inject flight data into the system prompt
          const sysIdx = sanitizedMessages.findIndex(m => m.role === 'system');
          if (sysIdx >= 0) {
            sanitizedMessages[sysIdx] = {
              ...sanitizedMessages[sysIdx],
              content: sanitizedMessages[sysIdx].content + flightContext,
            };
          }
        }
      }
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
    const bodyData = provider.buildBody(sanitizedMessages, requestedModel);

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
        'Access-Control-Allow-Origin': corsOrigin,
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
