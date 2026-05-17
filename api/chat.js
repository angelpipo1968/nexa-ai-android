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
      max_tokens: 4096,
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
        max_tokens: 4096,
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
      max_tokens: 4096,
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
//  SYSTEM PROMPT — MASTER (language-aware)
// ═══════════════════════════════════════

function getSystemPrompt(language) {
  const lang = (language || 'es').toLowerCase();

  if (lang === 'en') {
    return `You are NEXA PRO, an advanced AI assistant with extraordinary capabilities.

CORE IDENTITY:
- You are NEXA PRO — intelligent, creative, resourceful, and always helpful
- ALWAYS respond in English
- Be direct, natural, and conversational — no robotic responses
- Have personality: slightly witty, confident, and genuinely useful
- Max 800 tokens per response (expand for creative content when needed)

═══ IMAGE GENERATION ═══
You CAN generate images! When the user asks for any image, illustration, photo, drawing, logo, avatar, wallpaper, or visual content:
- Generate a descriptive English prompt (be detailed: style, colors, mood, composition)
- Use this EXACT format to show the image:
  ![description](https://image.pollinations.ai/prompt/YOUR_ENGLISH_PROMPT_HERE?width=1024&height=1024&nologo=true)
- Replace spaces with %20 in the URL, or use encodeURIComponent
- Examples:
  - "Make me a photo of Miami beach" → ![Miami Beach](https://image.pollinations.ai/prompt/Beautiful%20photorealistic%20sunset%20photo%20of%20Miami%20South%20Beach%20with%20art%20deco%20buildings%20palm%20trees%20and%20golden%20sand%20warm%20light%204k?width=1024&height=1024&nologo=true)
  - "Create a futuristic robot logo" → ![Robot Logo](https://image.pollinations.ai/prompt/Minimalist%20futuristic%20robot%20logo%20design%20clean%20lines%20neon%20green%20on%20dark%20background%20vector%20style?width=512&height=512&nologo=true)
- For wallpapers use width=1920&height=1080
- For avatars/icons use width=512&height=512
- Always make the prompt detailed and descriptive for better results

═══ VIDEO GENERATION ═══
You CAN generate videos! When the user asks for any video, animation, or motion content:
- Generate a detailed, cinematic prompt in English
- The system supports: Luma AI, Replicate, Minimax (Hailuo)
- Structure your prompt with: subject, action, camera movement, lighting, mood, style
- VIDEO PROMPT FORMULA: [Subject] + [Action/Movement] + [Camera] + [Lighting] + [Style/Mood]
- Examples:
  - "Hazme un video de un atardecer en la playa" → Cinematic slow-motion video of golden sunset over tropical ocean waves, camera slowly panning right, warm orange and pink sky reflections on wet sand, palm trees silhouetted, photorealistic 4K
  - "Video de un robot caminando" → Futuristic humanoid robot walking through neon-lit cyberpunk city street, rain reflections on pavement, low angle tracking shot, blue and purple lighting, cinematic sci-fi
  - "Un video de un gato jugando" → Adorable fluffy kitten playing with yarn ball in cozy sunlit living room, close-up shot with shallow depth of field, warm golden hour lighting, heartwarming mood
- If no video API is configured, provide the detailed prompt and explain which platforms to use it on:
  * Luma AI (lumalabs.ai) — free trial, no credit card
  * Kling AI (klingai.com) — free daily credits
  * Hailuo AI (hailuoai.video) — free tier
  * Pika (pika.art) — free credits
  * Runway (runwayml.com) — free trial

═══ VIDEO PROMPT ENGINEERING ═══
When the user wants prompts for a SPECIFIC platform, optimize accordingly:

LUMA AI prompts: Be cinematic, describe camera movements (pan, tilt, dolly, tracking), lighting (golden hour, volumetric), and mood. Luma excels at photorealistic and artistic styles.

KLING AI prompts: Be descriptive about motion and physics. Kling is great at realistic human movement, dance, and action sequences. Include camera angles.

HAILUO/MINIMAX prompts: Focus on the main subject and action. Hailuo excels at short, punchy clips with clear subjects. Keep prompts concise but vivid.

PIKA prompts: Creative and artistic. Pika does well with stylized, animated, and abstract content. Include art style references.

RUNWAY prompts: Cinematic language. Use film terminology: "dolly shot", "rack focus", "establishing shot", "B-roll". Runway excels at professional-looking footage.

═══ WEB PAGE CREATION ═══
You CAN create real web pages! When the user asks for a website, landing page, portfolio, or any web content:
- Generate COMPLETE, beautiful HTML with inline CSS
- Make it visually stunning: gradients, animations, modern typography, responsive design
- Use Google Fonts via CDN for premium typography
- Include smooth CSS animations and transitions
- After the code, provide a link using this format:
  [🔗 View Live Preview](/api/preview/html) — then explain they can paste the HTML
- OR create the HTML inline and tell the user to open it
- Make pages that look professional and original — not generic templates
- Include: hero sections, cards, animations, gradients, glassmorphism, modern UI patterns

═══ CREATIVE WRITING ═══
You are an exceptional creative writer. When asked for:
- POEMS: Write with rhythm, emotion, and imagery. Match the style requested (haiku, sonnet, free verse, etc.)
- SONGS: Include verse, chorus, bridge structure. Write lyrics that flow and have feeling
- STORIES: Vivid characters, compelling plot, engaging dialogue
- BOOKS: Structure with chapters, develop themes, create immersive narratives
- SCRIPTS: Proper format with dialogue and stage directions

═══ CODE & DEVELOPMENT ═══
- Write clean, production-ready code in any language
- Explain code clearly with comments
- Debug issues proactively
- Suggest best practices and optimizations
- Create complete, runnable examples

═══ ANALYSIS & PROBLEM SOLVING ═══
- Analyze URLs, code, data, documents
- Detect bugs, security issues, performance problems
- Provide actionable solutions, not just descriptions
- Think critically and give honest assessments

═══ GENERAL KNOWLEDGE ═══
- Answer any question with accuracy and depth
- Explain complex topics simply
- Provide multiple perspectives when relevant
- Cite reasoning for factual claims

RESPONSE STYLE:
- Be concise for simple questions
- Be detailed and thorough for complex tasks
- Use formatting (lists, bold, code blocks) when it helps readability
- For images: always show the rendered image, not just a URL
- For web pages: generate complete, beautiful HTML
- For creative content: pour effort into quality — make it memorable`;
  }

  // Default: Spanish
  return `Eres NEXA PRO, un asistente de IA avanzado con capacidades extraordinarias.

IDENTIDAD CENTRAL:
- Eres NEXA PRO — inteligente, creativo, ingenioso y siempre servicial
- SIEMPRE responde en español
- Sé directo, natural y conversacional — nada de respuestas robóticas
- Tienes personalidad: ligeramente ingenioso, seguro de ti mismo y genuinamente útil
- Máximo 800 tokens por respuesta (expande para contenido creativo cuando sea necesario)

═══ GENERACIÓN DE IMÁGENES ═══
¡PUEDES generar imágenes! Cuando el usuario pida cualquier imagen, ilustración, foto, dibujo, logo, avatar, fondo de pantalla o contenido visual:
- Genera un prompt descriptivo en inglés (detallado: estilo, colores, ambiente, composición)
- Usa este formato EXACTO para mostrar la imagen:
  ![descripción](https://image.pollinations.ai/prompt/TU_PROMPT_EN_INGLES_AQUI?width=1024&height=1024&nologo=true)
- Reemplaza espacios con %20 en la URL
- Ejemplos:
  - "Hazme una foto de la playa de Miami" → ![Playa de Miami](https://image.pollinations.ai/prompt/Beautiful%20photorealistic%20sunset%20photo%20of%20Miami%20South%20Beach%20with%20art%20deco%20buildings%20palm%20trees%20and%20golden%20sand%20warm%20light%204k?width=1024&height=1024&nologo=true)
  - "Crea un logo de robot futurista" → ![Logo Robot](https://image.pollinations.ai/prompt/Minimalist%20futuristic%20robot%20logo%20design%20clean%20lines%20neon%20green%20on%20dark%20background%20vector%20style?width=512&height=512&nologo=true)
- Para fondos de pantalla usa width=1920&height=1080
- Para avatares/iconos usa width=512&height=512
- Siempre haz el prompt detallado y descriptivo para mejores resultados

═══ GENERACIÓN DE VIDEO ═══
¡PUEDES generar videos! Cuando el usuario pida cualquier video, animación o contenido en movimiento:
- Genera un prompt detallado y cinematográfico en inglés
- El sistema soporta: Luma AI, Replicate, Minimax (Hailuo)
- Estructura tu prompt con: sujeto, acción, movimiento de cámara, iluminación, ambiente, estilo
- FÓRMULA DE VIDEO PROMPT: [Sujeto] + [Acción/Movimiento] + [Cámara] + [Iluminación] + [Estilo/Ambiente]
- Ejemplos:
  - "Hazme un video de un atardecer en la playa" → Cinematic slow-motion video of golden sunset over tropical ocean waves, camera slowly panning right, warm orange and pink sky reflections on wet sand, palm trees silhouetted, photorealistic 4K
  - "Video de un robot caminando" → Futuristic humanoid robot walking through neon-lit cyberpunk city street, rain reflections on pavement, low angle tracking shot, blue and purple lighting, cinematic sci-fi
  - "Un video de un gato jugando" → Adorable fluffy kitten playing with yarn ball in cozy sunlit living room, close-up shot with shallow depth of field, warm golden hour lighting, heartwarming mood
- Si no hay API de video configurada, proporciona el prompt detallado y explica en qué plataformas usarlo:
  * Luma AI (lumalabs.ai) — prueba gratis, sin tarjeta de crédito
  * Kling AI (klingai.com) — créditos gratis diarios
  * Hailuo AI (hailuoai.video) — tier gratuito
  * Pika (pika.art) — créditos gratis
  * Runway (runwayml.com) — prueba gratis

═══ INGENIERÍA DE PROMPTS DE VIDEO ═══
Cuando el usuario quiera prompts para una plataforma ESPECÍFICA, optimiza así:

LUMA AI: Sé cinematográfico, describe movimientos de cámara (paneo, tilt, dolly, tracking), iluminación (hora dorada, volumétrica) y ambiente. Luma destaca en estilos fotorrealistas y artísticos.

KLING AI: Sé descriptivo sobre movimiento y física. Kling es genial con movimiento humano realista, secuencias de baile y acción. Incluye ángulos de cámara.

HAILUO/MINIMAX: Enfócate en el sujeto principal y la acción. Hailuo destaca en clips cortos y directos con sujetos claros. Mantén los prompts concisos pero vívidos.

PIKA: Creativos y artísticos. Pika funciona bien con contenido estilizado, animado y abstracto. Incluye referencias de estilo artístico.

RUNWAY: Lenguaje cinematográfico. Usa terminología de cine: "dolly shot", "rack focus", "establishing shot", "B-roll". Runway destaca en footage profesional.

═══ CREACIÓN DE PÁGINAS WEB ═══
¡PUEDES crear páginas web reales! Cuando el usuario pida un sitio web, landing page, portfolio o cualquier contenido web:
- Genera HTML COMPLETO y hermoso con CSS inline
- Hazlo visualmente impresionante: gradientes, animaciones, tipografía moderna, diseño responsivo
- Usa Google Fonts vía CDN para tipografía premium
- Incluye animaciones y transiciones CSS suaves
- Haz páginas que se vean profesionales y originales — no plantillas genéricas
- Incluye: secciones hero, tarjetas, animaciones, gradientes, glassmorphism, patrones de UI modernos

═══ ESCRITURA CREATIVA ═══
Eres un escritor excepcional. Cuando te pidan:
- POEMAS: Escribe con ritmo, emoción e imágenes. Adapta el estilo solicitado (haiku, soneto, verso libre, etc.)
- CANCIONES: Incluye estructura de verso, coro, puente. Escribe letras que fluyan y tengan sentimiento
- HISTORIAS: Personajes vívidos, trama convincente, diálogo atractivo
- LIBROS: Estructura con capítulos, desarrolla temas, crea narrativas inmersivas
- GUIONES: Formato apropiado con diálogos y direcciones de escenario

═══ CÓDIGO Y DESARROLLO ═══
- Escribe código limpio y listo para producción en cualquier lenguaje
- Explica el código claramente con comentarios
- Depura problemas de forma proactiva
- Sugiere mejores prácticas y optimizaciones
- Crea ejemplos completos y ejecutables

═══ ANÁLISIS Y RESOLUCIÓN DE PROBLEMAS ═══
- Analiza URLs, código, datos, documentos
- Detecta bugs, problemas de seguridad, rendimiento
- Proporciona soluciones accionables, no solo descripciones
- Piensa críticamente y da evaluaciones honestas

═══ CONOCIMIENTO GENERAL ═══
- Responde cualquier pregunta con precisión y profundidad
- Explica temas complejos de forma simple
- Proporciona múltiples perspectivas cuando sea relevante
- Cita el razonamiento para afirmaciones factuales

ESTILO DE RESPUESTA:
- Sé conciso para preguntas simples
- Sé detallado y exhaustivo para tareas complejas
- Usa formato (listas, negritas, bloques de código) cuando ayude a la legibilidad
- Para imágenes: siempre muestra la imagen renderizada, no solo una URL
- Para páginas web: genera HTML completo y hermoso
- Para contenido creativo: esfuérzate en la calidad — hazlo memorable`;
}

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
//  URL DETECTION & CONTENT FETCHING
// ═══════════════════════════════════════

function detectUrls(text) {
  const urlPattern = /https?:\/\/[^\s<>"{}|\\^`\[\]]+/gi;
  const urls = text.match(urlPattern);
  if (!urls) return [];
  // Deduplicate
  return [...new Set(urls)];
}

async function fetchUrlContent(url) {
  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 8000);

    const resp = await fetch(url, {
      signal: controller.signal,
      headers: {
        'User-Agent': 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,text/plain;q=0.8,*/*;q=0.7',
        'Accept-Language': 'es-ES,es;q=0.9,en;q=0.8',
      },
      redirect: 'follow',
    });
    clearTimeout(timeout);

    if (!resp.ok) return null;

    const contentType = resp.headers.get('content-type') || '';
    const rawBody = await resp.text();

    // Extract readable text from HTML
    let content = rawBody;

    if (contentType.includes('text/html') || contentType.includes('application/xhtml')) {
      // Remove scripts, styles, nav, footer, ads
      content = content
        .replace(/<script[\s\S]*?<\/script>/gi, '')
        .replace(/<style[\s\S]*?<\/style>/gi, '')
        .replace(/<nav[\s\S]*?<\/nav>/gi, '')
        .replace(/<footer[\s\S]*?<\/footer>/gi, '')
        .replace(/<header[\s\S]*?<\/header>/gi, '')
        .replace(/<aside[\s\S]*?<\/aside>/gi, '')
        .replace(/<!--[\s\S]*?-->/g, '')
        .replace(/<noscript[\s\S]*?<\/noscript>/gi, '')
        .replace(/<svg[\s\S]*?<\/svg>/gi, '')
        // Extract title
        .replace(/<title[^>]*>([\s\S]*?)<\/title>/gi, (match, title) => {
          return `\n[TÍTULO: ${title.trim()}]\n`;
        })
        // Extract meta description
        .replace(/<meta[^>]*name=["']description["'][^>]*content=["']([\s\S]*?)["'][^>]*>/gi, (match, desc) => {
          return `\n[DESCRIPCIÓN: ${desc.trim()}]\n`;
        })
        // Extract headings
        .replace(/<h[1-3][^>]*>([\s\S]*?)<\/h[1-3]>/gi, (match, text) => {
          return `\n## ${text.replace(/<[^>]+>/g, '').trim()}\n`;
        })
        // Convert paragraphs
        .replace(/<p[^>]*>([\s\S]*?)<\/p>/gi, (match, text) => {
          return `\n${text.replace(/<[^>]+>/g, '').trim()}\n`;
        })
        // Convert links to text
        .replace(/<a[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi, (match, href, text) => {
          return `${text.replace(/<[^>]+>/g, '').trim()} (${href})`;
        })
        // Convert list items
        .replace(/<li[^>]*>([\s\S]*?)<\/li>/gi, (match, text) => {
          return `\n• ${text.replace(/<[^>]+>/g, '').trim()}`;
        })
        // Convert code blocks
        .replace(/<code[^>]*>([\s\S]*?)<\/code>/gi, (match, code) => {
          return `\`${code.trim()}\``;
        })
        .replace(/<pre[^>]*>([\s\S]*?)<\/pre>/gi, (match, code) => {
          return `\n\`\`\`\n${code.replace(/<[^>]+>/g, '').trim()}\n\`\`\`\n`;
        })
        // Remove remaining HTML tags
        .replace(/<[^>]+>/g, ' ')
        // Decode HTML entities
        .replace(/&lt;/g, '<')
        .replace(/&gt;/g, '>')
        .replace(/&amp;/g, '&')
        .replace(/&quot;/g, '"')
        .replace(/&#39;/g, "'")
        .replace(/&nbsp;/g, ' ')
        .replace(/&#\d+;/g, '')
        // Clean up whitespace
        .replace(/\n{3,}/g, '\n\n')
        .replace(/[ \t]{2,}/g, ' ')
        .trim();
    }
    // For non-HTML (plain text, JSON, etc.) just use as-is
    else if (contentType.includes('text/plain') || contentType.includes('application/json')) {
      content = rawBody;
    }
    else {
      return null; // binary content
    }

    // Limit content length
    if (content.length > 6000) {
      content = content.slice(0, 6000) + '\n\n[... contenido truncado]';
    }

    if (content.length < 50) return null; // Too short, probably not useful

    return content;
  } catch (err) {
    console.error('URL fetch error:', url, err.message);
    return null;
  }
}

function formatUrlContext(url, content) {
  if (!content) return '';
  return `\n\n[CONTENIDO DE URL]\nURL: ${url}\n\n${content}\n[FIN CONTENIDO DE URL]\n`;
}

// ═══════════════════════════════════════
//  STACKOVERFLOW DETECTION & FETCHING
// ═══════════════════════════════════════

const PROGRAMMING_KEYWORDS = [
  'javascript', 'python', 'java', 'c++', 'c#', 'ruby', 'php', 'swift', 'kotlin',
  'typescript', 'react', 'angular', 'vue', 'node', 'django', 'flask', 'spring',
  'laravel', 'rails', 'express', 'nextjs', 'nuxt', 'svelte', 'flutter', 'react native',
  'android', 'ios', 'html', 'css', 'sql', 'mongodb', 'postgresql', 'mysql',
  'git', 'docker', 'kubernetes', 'aws', 'azure', 'linux', 'bash', 'regex',
  'api', 'rest', 'graphql', 'json', 'xml', 'http', 'https',
  'error', 'bug', 'exception', 'debug', 'compile', 'runtime',
  'función', 'variable', 'array', 'objeto', 'clase', 'método',
  'loop', 'bucle', 'condicional', 'if', 'else', 'for', 'while',
  'instalar', 'configurar', 'importar', 'exportar', 'deploy',
  'framework', 'librería', 'biblioteca', 'paquete', 'dependencia',
  'como hacer', 'cómo hacer', 'how to', 'how do', 'qué es', 'what is',
  'por qué', 'why does', 'error en', 'error al', 'no funciona',
  'difference between', 'diferencia entre', 'ejemplo de', 'example of',
];

function detectProgrammingQuery(userMessage) {
  const msg = userMessage.toLowerCase();

  // Check if message has programming keywords
  const hasKeyword = PROGRAMMING_KEYWORDS.some(kw => msg.includes(kw));

  // Check for question patterns
  const questionPatterns = [
    /\bhow\s+(to|do|can)\b/i,
    /\bwhat\s+(is|are|does)\b/i,
    /\bwhy\s+(does|is|do)\b/i,
    /\b(cómo|como)\s+(hago|hacer|puedo|funciona)\b/i,
    /\b(qué|que)\s+(es|son|significa)\b/i,
    /\bpor\s+qué\b/i,
    /\berror\b/i,
    /\bno\s+funciona\b/i,
    /\bbug\b/i,
  ];

  const hasQuestion = questionPatterns.some(p => p.test(msg));

  if (!hasKeyword && !hasQuestion) return null;

  // Extract the main search query
  // Remove common filler words and keep the technical terms
  let query = msg
    .replace(/\b(por favor|please|gracias|thanks|hey|hola|hi|hello|oye|dime|dame|explícame|explica|ayuda|help)\b/gi, '')
    .replace(/\b(como|cómo|puedo|hacer|how|to|do|can|is|the|a|an|el|la|los|las|un|una|de|del|en|que|what|why|por|for|with|with|using)\b/gi, ' ')
    .replace(/\s{2,}/g, ' ')
    .trim();

  // If query is too short, use original
  if (query.length < 5) query = msg;

  // Limit query length for API
  query = query.slice(0, 200);

  return { query, originalMessage: msg };
}

async function fetchStackOverflowData(progQuery) {
  try {
    const encodedQuery = encodeURIComponent(progQuery.query);
    const url = `https://api.stackexchange.com/2.3/search/advanced?order=desc&sort=relevance&q=${encodedQuery}&site=stackoverflow&pagesize=3&filter=withbody`;

    const resp = await fetch(url);
    const data = await resp.json();

    if (!data.items || data.items.length === 0) return null;

    return data.items.map(item => ({
      title: item.title,
      score: item.score,
      answers: item.answer_count,
      accepted: item.is_answered,
      url: item.link,
      tags: item.tags?.slice(0, 5) || [],
      // Get the top answer if available
      topAnswer: null,
    }));
  } catch (err) {
    console.error('StackOverflow API error:', err);
    return null;
  }
}

async function fetchTopAnswer(questionId) {
  try {
    const url = `https://api.stackexchange.com/2.3/questions/${questionId}/answers?order=desc&sort=votes&site=stackoverflow&pagesize=1&filter=withbody`;
    const resp = await fetch(url);
    const data = await resp.json();

    if (!data.items || data.items.length === 0) return null;

    const answer = data.items[0];
    // Strip HTML tags for a cleaner text
    const body = answer.body
      .replace(/<code>([\s\S]*?)<\/code>/g, '`$1`')
      .replace(/<pre>([\s\S]*?)<\/pre>/g, '\n```\n$1\n```\n')
      .replace(/<[^>]+>/g, '')
      .replace(/&lt;/g, '<')
      .replace(/&gt;/g, '>')
      .replace(/&amp;/g, '&')
      .replace(/&quot;/g, '"')
      .replace(/&#39;/g, "'")
      .replace(/\n{3,}/g, '\n\n')
      .trim()
      .slice(0, 800); // Limit length

    return {
      score: answer.score,
      accepted: answer.is_accepted,
      body,
    };
  } catch (err) {
    return null;
  }
}

function formatStackOverflowContext(questions) {
  if (!questions || questions.length === 0) return '';

  let ctx = '\n\n[RESULTADOS DE STACKOVERFLOW]\n';
  questions.forEach((q, i) => {
    ctx += `\nPregunta ${i + 1}: ${q.title}\n`;
    ctx += `  Votos: ${q.score} | Respuestas: ${q.answers}`;
    if (q.accepted) ctx += ' | ✅ Tiene respuesta aceptada';
    ctx += '\n';
    if (q.tags.length > 0) ctx += `  Tags: ${q.tags.join(', ')}\n`;
    if (q.topAnswer) {
      ctx += `  Mejor respuesta (${q.topAnswer.score} votos${q.topAnswer.accepted ? ', aceptada' : ''}):\n`;
      ctx += `  ${q.topAnswer.body}\n`;
    }
    ctx += `  Link: ${q.url}\n`;
  });
  ctx += '[FIN STACKOVERFLOW]\n';
  return ctx;
}

// ═══════════════════════════════════════
//  GITHUB API — Search repos & code
// ═══════════════════════════════════════

function detectGitHubQuery(userMessage) {
  const msg = userMessage.toLowerCase();
  const ghKeywords = ['github', 'repositorio', 'repo', 'repository', 'código fuente', 'source code',
    'open source', 'librería para', 'library for', 'paquete npm', 'npm package', 'pip package',
    'framework para', 'framework for', 'alternativa a', 'alternative to'];

  const hasKeyword = ghKeywords.some(kw => msg.includes(kw));
  if (!hasKeyword) return null;

  // Extract search terms
  let query = msg
    .replace(/\b(buscar|search|find|encontrar|dame|muestra|show|find|look|up|for|me|the|a|an|el|la|los|las|un|una|de|del|en|que|github|repositorio|repo|código|source|open|library|librería|paquete|npm|framework|alternativa|alternative)\b/gi, ' ')
    .replace(/\s{2,}/g, ' ')
    .trim()
    .slice(0, 100);

  if (query.length < 3) query = msg.slice(0, 100);
  return query;
}

async function fetchGitHubData(query) {
  try {
    const encodedQuery = encodeURIComponent(query);
    const url = `https://api.github.com/search/repositories?q=${encodedQuery}&sort=stars&order=desc&per_page=3`;

    const resp = await fetch(url, {
      headers: { 'Accept': 'application/vnd.github+json', 'User-Agent': 'NexaProBot' }
    });
    const data = await resp.json();

    if (!data.items || data.items.length === 0) return null;

    return data.items.map(repo => ({
      name: repo.full_name,
      description: repo.description?.slice(0, 200) || 'Sin descripción',
      stars: repo.stargazers_count,
      language: repo.language || 'N/A',
      url: repo.html_url,
      updated: repo.updated_at?.split('T')[0] || 'N/A',
      topics: repo.topics?.slice(0, 5) || [],
    }));
  } catch (err) {
    console.error('GitHub API error:', err);
    return null;
  }
}

function formatGitHubContext(repos) {
  if (!repos || repos.length === 0) return '';
  let ctx = '\n\n[RESULTADOS DE GITHUB]\n';
  repos.forEach((r, i) => {
    ctx += `\n${i + 1}. ${r.name} ⭐ ${r.stars}\n`;
    ctx += `   ${r.description}\n`;
    ctx += `   Lenguaje: ${r.language}`;
    if (r.topics.length > 0) ctx += ` | Tags: ${r.topics.join(', ')}`;
    ctx += `\n   ${r.url}\n`;
  });
  ctx += '[FIN GITHUB]\n';
  return ctx;
}

// ═══════════════════════════════════════
//  MDN WEB DOCS — Search web documentation
// ═══════════════════════════════════════

function detectMDNQuery(userMessage) {
  const msg = userMessage.toLowerCase();
  const mdnKeywords = ['mdn', 'documentación', 'documentation', 'web api', 'dom', 'css property',
    'html tag', 'javascript method', 'fetch api', 'promise', 'async await', 'addEventListener',
    'querySelector', 'flexbox', 'grid', 'position', 'display', 'margin', 'padding',
    'qué es css', 'qué es html', 'qué es javascript', 'qué es dom',
    'propiedad css', 'atributo html', 'método javascript'];

  const hasKeyword = mdnKeywords.some(kw => msg.includes(kw));
  if (!hasKeyword) return null;

  let query = msg
    .replace(/\b(buscar|search|find|encontrar|dame|muestra|show|me|the|a|an|el|la|de|del|en|que|qué|es|documentación|documentation|web|api|propiedad|método|attribute|tag)\b/gi, ' ')
    .replace(/\s{2,}/g, ' ')
    .trim()
    .slice(0, 100);

  if (query.length < 3) return null;
  return query;
}

async function fetchMDNData(query) {
  try {
    const encodedQuery = encodeURIComponent(query);
    const url = `https://developer.mozilla.org/api/v1/search?q=${encodedQuery}&locale=en-US&size=3`;

    const resp = await fetch(url);
    const data = await resp.json();

    if (!data.documents || data.documents.length === 0) return null;

    return data.documents.map(doc => ({
      title: doc.title,
      summary: doc.summary?.slice(0, 250) || 'Sin resumen',
      url: `https://developer.mozilla.org${doc.mdn_url}`,
      popularity: doc.popularity || 0,
    }));
  } catch (err) {
    console.error('MDN API error:', err);
    return null;
  }
}

function formatMDNContext(docs) {
  if (!docs || docs.length === 0) return '';
  let ctx = '\n\n[DOCUMENTACIÓN MDN]\n';
  docs.forEach((d, i) => {
    ctx += `\n${i + 1}. ${d.title}\n`;
    ctx += `   ${d.summary}\n`;
    ctx += `   ${d.url}\n`;
  });
  ctx += '[FIN MDN]\n';
  return ctx;
}

// ═══════════════════════════════════════
//  ROADMAP.SH — Learning roadmaps
// ═══════════════════════════════════════

const ROADMAPS = {
  'frontend': { name: 'Frontend', url: 'https://roadmap.sh/frontend', desc: 'HTML, CSS, JavaScript, React, Vue, Angular' },
  'backend': { name: 'Backend', url: 'https://roadmap.sh/backend', desc: 'Node.js, Python, Java, bases de datos, APIs' },
  'devops': { name: 'DevOps', url: 'https://roadmap.sh/devops', desc: 'Docker, Kubernetes, CI/CD, AWS, Linux' },
  'fullstack': { name: 'Full Stack', url: 'https://roadmap.sh/full-stack', desc: 'Frontend + Backend completo' },
  'android': { name: 'Android', url: 'https://roadmap.sh/android', desc: 'Kotlin, Java, Jetpack Compose' },
  'ios': { name: 'iOS', url: 'https://roadmap.sh/ios', desc: 'Swift, UIKit, SwiftUI' },
  'python': { name: 'Python', url: 'https://roadmap.sh/python', desc: 'Django, Flask, FastAPI, data science' },
  'react': { name: 'React', url: 'https://roadmap.sh/react', desc: 'Hooks, Redux, Next.js, TypeScript' },
  'nodejs': { name: 'Node.js', url: 'https://roadmap.sh/nodejs', desc: 'Express, Nest.js, APIs REST' },
  'typescript': { name: 'TypeScript', url: 'https://roadmap.sh/typescript', desc: 'Tipos, interfaces, generics' },
  'sql': { name: 'SQL', url: 'https://roadmap.sh/sql', desc: 'MySQL, PostgreSQL, queries' },
  'ai': { name: 'AI & Data Science', url: 'https://roadmap.sh/ai-data-scientist', desc: 'ML, Python, TensorFlow' },
  'cybersecurity': { name: 'Cybersecurity', url: 'https://roadmap.sh/cyber-security', desc: 'Redes, ethical hacking' },
  'ux': { name: 'UX Design', url: 'https://roadmap.sh/ux-design', desc: 'Investigación, prototipos, testing' },
  'blockchain': { name: 'Blockchain', url: 'https://roadmap.sh/blockchain', desc: 'Solidity, Web3, smart contracts' },
  'game': { name: 'Game Dev', url: 'https://roadmap.sh/game-developer', desc: 'Unity, Unreal, Godot' },
  'flutter': { name: 'Flutter', url: 'https://roadmap.sh/flutter', desc: 'Dart, widgets, state management' },
  'java': { name: 'Java', url: 'https://roadmap.sh/java', desc: 'Spring Boot, Maven, microservicios' },
  'golang': { name: 'Go', url: 'https://roadmap.sh/golang', desc: 'Goroutines, APIs, microservicios' },
  'rust': { name: 'Rust', url: 'https://roadmap.sh/rust', desc: 'Ownership, sistemas, WebAssembly' },
};

function detectRoadmapQuery(userMessage) {
  const msg = userMessage.toLowerCase();
  const roadmapKeywords = ['roadmap', 'aprender', 'learn', 'estudiar', 'study', 'guía', 'guide',
    'por dónde empezar', 'where to start', 'qué aprender', 'what to learn',
    'ruta de aprendizaje', 'learning path', 'cómo ser', 'how to become',
    'principiante', 'beginner', 'paso a paso', 'step by step'];

  const hasKeyword = roadmapKeywords.some(kw => msg.includes(kw));
  if (!hasKeyword) return null;

  // Find matching roadmap
  for (const [key, roadmap] of Object.entries(ROADMAPS)) {
    if (msg.includes(key) || msg.includes(roadmap.name.toLowerCase())) {
      return { key, ...roadmap };
    }
  }

  // Generic roadmap request
  return { key: 'all', name: 'General', url: 'https://roadmap.sh', desc: 'Roadmaps de todas las tecnologías' };
}

function formatRoadmapContext(roadmap) {
  if (!roadmap) return '';
  let ctx = '\n\n[ROADMAP DE APRENDIZAJE]\n';
  ctx += `\n🗺️ ${roadmap.name}\n`;
  ctx += `   ${roadmap.desc}\n`;
  ctx += `   Ver roadmap completo: ${roadmap.url}\n`;
  ctx += '[FIN ROADMAP]\n';
  return ctx;
}

// ═══════════════════════════════════════
//  DEVDOCS — Documentation lookup
// ═══════════════════════════════════════

const DEVDOCS_DOCS = {
  'javascript': 'javascript', 'js': 'javascript', 'python': 'python~3.12',
  'react': 'react', 'vue': 'vue~3', 'angular': 'angular~17',
  'node': 'node', 'nodejs': 'node', 'express': 'express',
  'typescript': 'typescript', 'ts': 'typescript',
  'html': 'html', 'css': 'css', 'sass': 'sass',
  'git': 'git', 'docker': 'docker~4', 'kubernetes': 'kubernetes',
  'sql': 'postgresql~16', 'postgres': 'postgresql~16', 'mysql': 'mysql~8.0',
  'mongodb': 'mongodb~7', 'redis': 'redis~7.2',
  'php': 'php~8.3', 'laravel': 'laravel~11', 'ruby': 'ruby~3.3',
  'swift': 'swift~5.9', 'kotlin': 'kotlin', 'java': 'openjdk~21',
  'go': 'go', 'golang': 'go', 'rust': 'rust',
  'tailwind': 'tailwindcss', 'bootstrap': 'bootstrap~5.3',
  'nextjs': 'next', 'nuxt': 'nuxt~3', 'svelte': 'svelte~4',
  'graphql': 'graphql', 'rest': 'http',
  'numpy': 'numpy~1.26', 'pandas': 'pandas~2.2',
  'tensorflow': 'tensorflow~2.16', 'pytorch': 'torch~2.2',
};

function detectDevDocsQuery(userMessage) {
  const msg = userMessage.toLowerCase();

  // Check for specific doc requests
  const patterns = [
    /documentación\s+(?:de\s+)?(\w+)/i,
    /docs?\s+(?:for\s+|of\s+|de\s+)?(\w+)/i,
    /(?:cómo|como)\s+usar\s+(\w+)/i,
    /(?:how\s+to\s+use)\s+(\w+)/i,
    /sintaxis\s+(?:de\s+)?(\w+)/i,
    /syntax\s+(?:of\s+|for\s+)?(\w+)/i,
    /referencia\s+(?:de\s+)?(\w+)/i,
    /reference\s+(?:for\s+|of\s+)?(\w+)/i,
  ];

  for (const pattern of patterns) {
    const match = msg.match(pattern);
    if (match) {
      const tech = match[1].toLowerCase();
      if (DEVDOCS_DOCS[tech]) {
        return { tech, docSlug: DEVDOCS_DOCS[tech] };
      }
    }
  }

  return null;
}

async function fetchDevDocsData(docSlug) {
  try {
    const url = `https://devdocs.io/${docSlug}/`;
    // DevDocs doesn't have a search API, but we can provide the link
    return { url, slug: docSlug };
  } catch (err) {
    return null;
  }
}

function formatDevDocsContext(devdocs) {
  if (!devdocs) return '';
  return `\n\n[DOCUMENTACIÓN DEVDOCS]\n📖 Documentación disponible en: ${devdocs.url}\n[FIN DEVDOCS]\n`;
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
    const { messages, provider: requestedProvider, model: requestedModel, language } = body;

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

    // Add system prompt if not present (language-aware)
    if (!sanitizedMessages.some(m => m.role === 'system')) {
      sanitizedMessages.unshift({ role: 'system', content: getSystemPrompt(language) });
    }

    // Detect flight queries and fetch real data
    const lastUserMsg = [...sanitizedMessages].reverse().find(m => m.role === 'user');
    if (lastUserMsg) {
      const flightQuery = detectFlightQuery(lastUserMsg.content);
      if (flightQuery) {
        const flightData = await fetchFlightData(flightQuery);
        if (flightData && flightData.length > 0) {
          const flightContext = formatFlightContext(flightData);
          const sysIdx = sanitizedMessages.findIndex(m => m.role === 'system');
          if (sysIdx >= 0) {
            sanitizedMessages[sysIdx] = {
              ...sanitizedMessages[sysIdx],
              content: sanitizedMessages[sysIdx].content + flightContext,
            };
          }
        }
      }

      // Detect programming queries and fetch StackOverflow data
      const progQuery = detectProgrammingQuery(lastUserMsg.content);
      if (progQuery) {
        const soQuestions = await fetchStackOverflowData(progQuery);
        if (soQuestions && soQuestions.length > 0) {
          // Fetch top answer for the first (most relevant) question
          const firstQ = soQuestions[0];
          if (firstQ.url) {
            // Extract question ID from URL
            const qIdMatch = firstQ.url.match(/\/questions\/(\d+)/);
            if (qIdMatch) {
              const topAnswer = await fetchTopAnswer(qIdMatch[1]);
              if (topAnswer) soQuestions[0].topAnswer = topAnswer;
            }
          }
          const soContext = formatStackOverflowContext(soQuestions);
          const sysIdx = sanitizedMessages.findIndex(m => m.role === 'system');
          if (sysIdx >= 0) {
            sanitizedMessages[sysIdx] = {
              ...sanitizedMessages[sysIdx],
              content: sanitizedMessages[sysIdx].content + soContext,
            };
          }
        }
      }

      // Detect GitHub queries
      const ghQuery = detectGitHubQuery(lastUserMsg.content);
      if (ghQuery) {
        const ghRepos = await fetchGitHubData(ghQuery);
        if (ghRepos && ghRepos.length > 0) {
          const ghContext = formatGitHubContext(ghRepos);
          const sysIdx = sanitizedMessages.findIndex(m => m.role === 'system');
          if (sysIdx >= 0) {
            sanitizedMessages[sysIdx] = {
              ...sanitizedMessages[sysIdx],
              content: sanitizedMessages[sysIdx].content + ghContext,
            };
          }
        }
      }

      // Detect MDN queries
      const mdnQuery = detectMDNQuery(lastUserMsg.content);
      if (mdnQuery) {
        const mdnDocs = await fetchMDNData(mdnQuery);
        if (mdnDocs && mdnDocs.length > 0) {
          const mdnContext = formatMDNContext(mdnDocs);
          const sysIdx = sanitizedMessages.findIndex(m => m.role === 'system');
          if (sysIdx >= 0) {
            sanitizedMessages[sysIdx] = {
              ...sanitizedMessages[sysIdx],
              content: sanitizedMessages[sysIdx].content + mdnContext,
            };
          }
        }
      }

      // Detect roadmap queries
      const roadmapQuery = detectRoadmapQuery(lastUserMsg.content);
      if (roadmapQuery) {
        const roadmapContext = formatRoadmapContext(roadmapQuery);
        const sysIdx = sanitizedMessages.findIndex(m => m.role === 'system');
        if (sysIdx >= 0) {
          sanitizedMessages[sysIdx] = {
            ...sanitizedMessages[sysIdx],
            content: sanitizedMessages[sysIdx].content + roadmapContext,
          };
        }
      }

      // Detect DevDocs queries
      const devdocsQuery = detectDevDocsQuery(lastUserMsg.content);
      if (devdocsQuery) {
        const devdocsData = await fetchDevDocsData(devdocsQuery.docSlug);
        if (devdocsData) {
          const devdocsContext = formatDevDocsContext(devdocsData);
          const sysIdx = sanitizedMessages.findIndex(m => m.role === 'system');
          if (sysIdx >= 0) {
            sanitizedMessages[sysIdx] = {
              ...sanitizedMessages[sysIdx],
              content: sanitizedMessages[sysIdx].content + devdocsContext,
            };
          }
        }
      }

      // Detect URLs in message and fetch their content
      const urls = detectUrls(lastUserMsg.content);
      if (urls.length > 0) {
        // Fetch up to 3 URLs to avoid timeouts
        const urlsToFetch = urls.slice(0, 3);
        const fetchResults = await Promise.allSettled(
          urlsToFetch.map(url => fetchUrlContent(url))
        );

        let urlContext = '';
        fetchResults.forEach((result, i) => {
          if (result.status === 'fulfilled' && result.value) {
            urlContext += formatUrlContext(urlsToFetch[i], result.value);
          }
        });

        if (urlContext) {
          const sysIdx = sanitizedMessages.findIndex(m => m.role === 'system');
          if (sysIdx >= 0) {
            sanitizedMessages[sysIdx] = {
              ...sanitizedMessages[sysIdx],
              content: sanitizedMessages[sysIdx].content + urlContext,
            };
          }
        }
      }
    }

    // Fallback order: anthropic first, then others
    const FALLBACK_ORDER = ['anthropic', 'groq', 'openai', 'gemini', 'openrouter', 'zai', 'deepseek'];
    const providersToTry = requestedProvider
      ? [requestedProvider]
      : (process.env.DEFAULT_PROVIDER ? [process.env.DEFAULT_PROVIDER, ...FALLBACK_ORDER.filter(p => p !== process.env.DEFAULT_PROVIDER)] : FALLBACK_ORDER);

    let provider = null;
    let providerName = null;
    let apiKey = null;
    let upstream = null;

    for (const candidate of providersToTry) {
      const p = PROVIDERS[candidate];
      if (!p) continue;
      const key = process.env[p.envKey];
      if (!key || key.length < 10) continue;

      try {
        console.log(`[CHAT] Trying ${candidate}...`);
        const url = p.getUrl ? p.getUrl(requestedModel, key) : p.url;
        const headers = p.headers(key);
        const bodyData = p.buildBody(sanitizedMessages, requestedModel);

        const res = await fetch(url, { method: 'POST', headers, body: JSON.stringify(bodyData) });
        if (!res.ok) {
          console.error(`[CHAT] ${candidate} failed: ${res.status}`);
          continue;
        }
        provider = p;
        providerName = candidate;
        apiKey = key;
        upstream = res;
        break;
      } catch (err) {
        console.error(`[CHAT] ${candidate} error: ${err.message}`);
        continue;
      }
    }

    if (!upstream) {
      return new Response(JSON.stringify({
        error: 'Todos los proveedores de IA fallaron. Intenta de nuevo.',
      }), {
        status: 503,
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
