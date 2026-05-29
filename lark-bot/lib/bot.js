/**
 * ============================================================
 *  NEXA PRO AI Assistant v4.0 — ULTIMATE EDITION
 *  Multi-AI · Multi-Service · Full Wisdom · Lark Suite Bot
 *  Compatible: Vercel Serverless + Render + Standalone
 * ============================================================
 */

const lark = require('@larksuiteoapi/node-sdk');
const axios = require('axios');
const crypto = require('crypto');

// ============ CONFIGURATION ============
function getConfig() {
  // Support both process.env and Vercel env
  return {
    // Lark
    LARK_APP_ID: process.env.LARK_APP_ID || '',
    LARK_APP_SECRET: process.env.LARK_APP_SECRET || '',
    LARK_VERIFICATION_TOKEN: process.env.LARK_VERIFICATION_TOKEN || '',
    LARK_ENCRYPT_KEY: process.env.LARK_ENCRYPT_KEY || '',

    // Dify AI
    DIFY_API_KEY: process.env.DIFY_API_KEY || '',
    DIFY_BASE_URL: process.env.DIFY_BASE_URL || 'https://api.dify.ai/v1',

    // OpenAI
    OPENAI_API_KEY: process.env.OPENAI_API_KEY || '',
    OPENAI_BASE_URL: process.env.OPENAI_BASE_URL || 'https://api.openai.com/v1',
    OPENAI_MODEL: process.env.OPENAI_MODEL || 'gpt-4o-mini',

    // Google Gemini
    GEMINI_API_KEY: process.env.GEMINI_API_KEY || '',

    // Anthropic Claude
    CLAUDE_API_KEY: process.env.CLAUDE_API_KEY || '',

    // Weather
    WEATHER_API_KEY: process.env.WEATHER_API_KEY || '',

    // Finance
    ALPHA_VANTAGE_KEY: process.env.ALPHA_VANTAGE_KEY || '',

    // News
    NEWS_API_KEY: process.env.NEWS_API_KEY || '',

    // Translation
    DEEPL_API_KEY: process.env.DEEPL_API_KEY || '',

    // Search
    GOOGLE_SEARCH_KEY: process.env.GOOGLE_SEARCH_KEY || '',
    GOOGLE_SEARCH_CX: process.env.GOOGLE_SEARCH_CX || '',
    BRAVE_SEARCH_KEY: process.env.BRAVE_SEARCH_KEY || '',

    // Image Generation
    STABILITY_API_KEY: process.env.STABILITY_API_KEY || '',

    // Maps
    GOOGLE_MAPS_KEY: process.env.GOOGLE_MAPS_KEY || '',

    // NASA
    NASA_API_KEY: process.env.NASA_API_KEY || '',

    // Movies
    TMDB_API_KEY: process.env.TMDB_API_KEY || '',

    // Spotify
    SPOTIFY_CLIENT_ID: process.env.SPOTIFY_CLIENT_ID || '',
    SPOTIFY_CLIENT_SECRET: process.env.SPOTIFY_CLIENT_SECRET || '',

    // Wolfram Alpha
    WOLFRAM_APP_ID: process.env.WOLFRAM_APP_ID || '',

    // Aviation
    AVIATIONSTACK_KEY: process.env.AVIATIONSTACK_KEY || '',

    // Dictionary / Thesaurus
    DICTIONARY_API_KEY: process.env.DICTIONARY_API_KEY || '',

    // Server
    PORT: process.env.PORT || 3000,
    NODE_ENV: process.env.NODE_ENV || 'development',
    ADMIN_SECRET: process.env.ADMIN_SECRET || 'nexa-admin-2024',

    // Limits
    RATE_LIMIT_WINDOW: 60000,
    RATE_LIMIT_MAX: 30,
    MAX_CONVERSATION_AGE: 24 * 60 * 60 * 1000,
    MAX_MESSAGE_LENGTH: 4000,
  };
}

const CONFIG = getConfig();

// ============ LARK CLIENT (lazy init) ============
let _larkClient = null;
function getLarkClient() {
  if (!_larkClient && CONFIG.LARK_APP_ID) {
    _larkClient = new lark.Client({
      appId: CONFIG.LARK_APP_ID,
      appSecret: CONFIG.LARK_APP_SECRET,
      domain: lark.Domain.Lark,
    });
  }
  return _larkClient;
}

// ============ IN-MEMORY STORAGE ============
const conversationStore = new Map();
const rateLimitStore = new Map();
const adminLogs = [];
const serviceStats = {
  totalMessages: 0,
  commandUsage: {},
  apiCalls: { dify: 0, openai: 0, gemini: 0, claude: 0, weather: 0, finance: 0, news: 0, translate: 0, search: 0, code: 0, calc: 0, nasa: 0, movies: 0, flights: 0, spotify: 0, dictionary: 0 },
  startTime: Date.now(),
  errors: 0,
};

// ============ UTILITIES ============
function log(level, category, message, data) {
  const ts = new Date().toISOString();
  console.log('[' + ts + '] [' + level + '] [' + category + '] ' + message);
  if (data) console.log(JSON.stringify(data));
  if (level === 'error') {
    serviceStats.errors++;
    adminLogs.push({ ts, level, category, message });
    if (adminLogs.length > 500) adminLogs.shift();
  }
}

function truncate(text, maxLen) {
  maxLen = maxLen || CONFIG.MAX_MESSAGE_LENGTH;
  if (!text || text.length <= maxLen) return text;
  return text.substring(0, maxLen) + '\n\n... *(respuesta truncada)*';
}

function formatNum(n) {
  if (n >= 1e9) return (n / 1e9).toFixed(2) + 'B';
  if (n >= 1e6) return (n / 1e6).toFixed(2) + 'M';
  if (n >= 1e3) return (n / 1e3).toFixed(2) + 'K';
  return String(n);
}

function checkRateLimit(userId) {
  const now = Date.now();
  const u = rateLimitStore.get(userId);
  if (!u || now > u.resetTime) {
    rateLimitStore.set(userId, { count: 1, resetTime: now + CONFIG.RATE_LIMIT_WINDOW });
    return true;
  }
  if (u.count >= CONFIG.RATE_LIMIT_MAX) return false;
  u.count++;
  return true;
}

// ============ AI PROVIDERS ============

async function callDifyAI(query, userId, conversationId) {
  if (!CONFIG.DIFY_API_KEY) return null;
  serviceStats.apiCalls.dify++;
  try {
    const payload = { inputs: {}, query: query, user: 'lark_' + userId, response_mode: 'blocking' };
    if (conversationId) payload.conversation_id = conversationId;
    const res = await axios.post(CONFIG.DIFY_BASE_URL + '/chat-messages', payload, {
      headers: { 'Authorization': 'Bearer ' + CONFIG.DIFY_API_KEY, 'Content-Type': 'application/json' },
      timeout: 120000,
    });
    if (res.data.conversation_id) {
      conversationStore.set(userId, { conversationId: res.data.conversation_id, provider: 'dify', lastActivity: Date.now() });
    }
    return res.data.answer || null;
  } catch (e) {
    log('error', 'DIFY', 'Failed: ' + (e.response ? e.response.status : e.message));
    return null;
  }
}

async function callOpenAI(query, userId, systemPrompt) {
  if (!CONFIG.OPENAI_API_KEY) return null;
  serviceStats.apiCalls.openai++;
  try {
    const messages = [];
    messages.push({ role: 'system', content: systemPrompt || 'Eres NEXA PRO AI, un asistente avanzado con sabiduría profunda. Respondes en el idioma del usuario. Eres preciso, detallado y creativo. Tienes conocimientos enciclopédicos, filosóficos, científicos, técnicos y culturales.' });
    messages.push({ role: 'user', content: query });
    const res = await axios.post(CONFIG.OPENAI_BASE_URL + '/chat/completions', {
      model: CONFIG.OPENAI_MODEL, messages: messages, max_tokens: 2500, temperature: 0.7,
    }, {
      headers: { 'Authorization': 'Bearer ' + CONFIG.OPENAI_API_KEY, 'Content-Type': 'application/json' },
      timeout: 60000,
    });
    conversationStore.set(userId, { provider: 'openai', lastActivity: Date.now() });
    return res.data.choices[0].message.content || null;
  } catch (e) {
    log('error', 'OPENAI', 'Failed: ' + (e.response ? e.response.status : e.message));
    return null;
  }
}

async function callGemini(query, userId) {
  if (!CONFIG.GEMINI_API_KEY) return null;
  serviceStats.apiCalls.gemini++;
  try {
    const res = await axios.post(
      'https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=' + CONFIG.GEMINI_API_KEY,
      { contents: [{ parts: [{ text: query }] }], generationConfig: { maxOutputTokens: 2500, temperature: 0.7 } },
      { timeout: 60000 }
    );
    conversationStore.set(userId, { provider: 'gemini', lastActivity: Date.now() });
    return res.data.candidates[0].content.parts[0].text || null;
  } catch (e) {
    log('error', 'GEMINI', 'Failed: ' + (e.response ? e.response.status : e.message));
    return null;
  }
}

async function callClaude(query, userId, systemPrompt) {
  if (!CONFIG.CLAUDE_API_KEY) return null;
  serviceStats.apiCalls.claude++;
  try {
    const res = await axios.post('https://api.anthropic.com/v1/messages', {
      model: 'claude-sonnet-4-20250514', max_tokens: 2500,
      system: systemPrompt || 'Eres NEXA PRO AI, asistente avanzado con sabiduría profunda.',
      messages: [{ role: 'user', content: query }],
    }, {
      headers: { 'x-api-key': CONFIG.CLAUDE_API_KEY, 'anthropic-version': '2023-06-01', 'Content-Type': 'application/json' },
      timeout: 60000,
    });
    conversationStore.set(userId, { provider: 'claude', lastActivity: Date.now() });
    return res.data.content[0].text || null;
  } catch (e) {
    log('error', 'CLAUDE', 'Failed: ' + (e.response ? e.response.status : e.message));
    return null;
  }
}

async function callAI(query, userId, systemPrompt) {
  const convData = conversationStore.get(userId);

  // Try Dify first (conversation memory)
  if (convData && convData.provider === 'dify' || CONFIG.DIFY_API_KEY) {
    const r = await callDifyAI(query, userId, convData ? convData.conversationId : null);
    if (r) return r;
  }
  // OpenAI
  const r2 = await callOpenAI(query, userId, systemPrompt);
  if (r2) return r2;
  // Gemini
  const r3 = await callGemini(query, userId);
  if (r3) return r3;
  // Claude
  const r4 = await callClaude(query, userId, systemPrompt);
  if (r4) return r4;

  return 'Todos los proveedores de IA no estan disponibles. Configura al menos DIFY_API_KEY u OPENAI_API_KEY.';
}

// ============ SERVICE MODULES ============

async function getWeather(location) {
  serviceStats.apiCalls.weather++;
  try {
    if (CONFIG.WEATHER_API_KEY) {
      const r = await axios.get('https://api.openweathermap.org/data/2.5/weather', {
        params: { q: location, appid: CONFIG.WEATHER_API_KEY, units: 'metric', lang: 'es' }, timeout: 10000,
      });
      const d = r.data;
      const lines = [
        '**' + d.name + ', ' + d.sys.country + '**',
        '',
        '- **Temperatura:** ' + d.main.temp + ' C (sensacion: ' + d.main.feels_like + ' C)',
        '- **Clima:** ' + d.weather[0].description,
        '- **Humedad:** ' + d.main.humidity + '%',
        '- **Viento:** ' + d.wind.speed + ' m/s',
        '- **Presion:** ' + d.main.pressure + ' hPa',
        '- **Visibilidad:** ' + (d.visibility / 1000) + ' km',
      ];
      return lines.join('\n');
    }
    // Fallback: wttr.in (no API key)
    const r = await axios.get('https://wttr.in/' + encodeURIComponent(location) + '?format=j1', { timeout: 10000 });
    const c = r.data.current_condition[0];
    const lines = [
      '**' + location + '**',
      '',
      '- **Temperatura:** ' + c.temp_C + ' C (sensacion: ' + c.FeelsLikeC + ' C)',
      '- **Clima:** ' + c.weatherDesc[0].value,
      '- **Humedad:** ' + c.humidity + '%',
      '- **Viento:** ' + c.windspeedKmph + ' km/h',
    ];
    return lines.join('\n');
  } catch (e) {
    return 'No se pudo obtener el clima. Verifica el nombre de la ciudad.';
  }
}

async function getForecast(location) {
  serviceStats.apiCalls.weather++;
  try {
    if (!CONFIG.WEATHER_API_KEY) return 'Se requiere WEATHER_API_KEY para pronosticos. Usa /weather para datos actuales.';
    const r = await axios.get('https://api.openweathermap.org/data/2.5/forecast', {
      params: { q: location, appid: CONFIG.WEATHER_API_KEY, units: 'metric', lang: 'es' }, timeout: 10000,
    });
    const forecasts = r.data.list.filter(function(_, i) { return i % 8 === 0; });
    const lines = ['**Pronostico para ' + r.data.city.name + '**', ''];
    for (let i = 0; i < forecasts.length; i++) {
      const f = forecasts[i];
      const date = new Date(f.dt * 1000).toLocaleDateString('es', { weekday: 'short', day: 'numeric' });
      lines.push('- **' + date + ':** ' + f.main.temp + ' C, ' + f.weather[0].description);
    }
    return lines.join('\n');
  } catch (e) {
    return 'No se pudo obtener el pronostico.';
  }
}

async function getStockQuote(symbol) {
  serviceStats.apiCalls.finance++;
  try {
    if (CONFIG.ALPHA_VANTAGE_KEY) {
      const r = await axios.get('https://www.alphavantage.co/query', {
        params: { function: 'GLOBAL_QUOTE', symbol: symbol, apikey: CONFIG.ALPHA_VANTAGE_KEY }, timeout: 10000,
      });
      const q = r.data['Global Quote'];
      if (!q || !q['01. symbol']) return 'Simbolo no encontrado. Ejemplo: AAPL, GOOGL, TSLA';
      const change = parseFloat(q['10. change percent']);
      const emoji = change >= 0 ? '📈' : '📉';
      const lines = [
        '**' + q['01. symbol'] + '** ' + emoji, '',
        '- **Precio:** $' + parseFloat(q['05. price']).toFixed(2),
        '- **Apertura:** $' + parseFloat(q['02. open']).toFixed(2),
        '- **Maximo:** $' + parseFloat(q['03. high']).toFixed(2),
        '- **Minimo:** $' + parseFloat(q['04. low']).toFixed(2),
        '- **Volumen:** ' + formatNum(parseInt(q['06. volume'])),
        '- **Cambio:** ' + q['10. change percent'],
      ];
      return lines.join('\n');
    }
    return await callAI('Dame la cotizacion actual de la accion ' + symbol + ' y su rendimiento reciente.', 'finance');
  } catch (e) {
    return 'Error al obtener datos financieros.';
  }
}

async function getCryptoPrice(symbol) {
  serviceStats.apiCalls.finance++;
  try {
    const r = await axios.get('https://api.coingecko.com/api/v3/simple/price', {
      params: { ids: symbol.toLowerCase(), vs_currencies: 'usd,eur,mxn,cop', include_24hr_change: 'true' }, timeout: 10000,
    });
    const d = r.data[symbol.toLowerCase()];
    if (!d) return 'Criptomoneda no encontrada. Ejemplo: bitcoin, ethereum, solana';
    const lines = [
      '**' + symbol.toUpperCase() + '**', '',
      '- **USD:** $' + (d.usd ? d.usd.toLocaleString() : 'N/A'),
      '- **EUR:** ' + (d.eur ? d.eur.toLocaleString() + ' EUR' : 'N/A'),
      '- **MXN:** $' + (d.mxn ? d.mxn.toLocaleString() + ' MXN' : 'N/A'),
      '- **COP:** $' + (d.cop ? d.cop.toLocaleString() + ' COP' : 'N/A'),
      '- **Cambio 24h:** ' + (d.usd_24h_change ? d.usd_24h_change.toFixed(2) + '%' : 'N/A'),
    ];
    return lines.join('\n');
  } catch (e) {
    return 'Error al obtener precio de criptomoneda.';
  }
}

async function getExchangeRate(from, to, amount) {
  serviceStats.apiCalls.finance++;
  amount = amount || 1;
  try {
    const r = await axios.get('https://api.exchangerate-api.com/v4/latest/' + from.toUpperCase(), { timeout: 10000 });
    const rate = r.data.rates[to.toUpperCase()];
    if (!rate) return 'Moneda no encontrada. Ejemplo: USD, EUR, MXN, COP';
    const converted = (amount * rate).toFixed(4);
    const lines = [
      '**Conversion de Moneda**', '',
      '- **' + amount + ' ' + from.toUpperCase() + '** = **' + converted + ' ' + to.toUpperCase() + '**',
      '- **Tasa:** 1 ' + from.toUpperCase() + ' = ' + rate.toFixed(6) + ' ' + to.toUpperCase(),
      '- **Fuente:** ExchangeRate-API',
      '- **Actualizado:** ' + r.data.date,
    ];
    return lines.join('\n');
  } catch (e) {
    return 'Error al obtener tasas de cambio.';
  }
}

async function getNews(query) {
  serviceStats.apiCalls.news++;
  query = query || 'technology';
  try {
    if (CONFIG.NEWS_API_KEY) {
      const r = await axios.get('https://newsapi.org/v2/top-headlines', {
        params: { q: query, country: 'us', apiKey: CONFIG.NEWS_API_KEY, pageSize: 5 }, timeout: 10000,
      });
      const articles = r.data.articles || [];
      if (articles.length === 0) return 'No se encontraron noticias.';
      const lines = ['**Noticias sobre "' + query + '"**', ''];
      for (let i = 0; i < articles.length; i++) {
        const a = articles[i];
        const num = i + 1;
        const source = a.source ? (a.source.name || 'N/A') : 'N/A';
        lines.push('**' + num + '. ' + (a.title || 'Sin titulo') + '**');
        lines.push((a.description || ''));
        lines.push('*Fuente: ' + source + '*');
        lines.push('');
      }
      return lines.join('\n');
    }
    return await callAI('Dame un resumen de las 5 noticias mas importantes sobre "' + query + '" del dia de hoy.', 'news-service');
  } catch (e) {
    return 'Error al obtener noticias.';
  }
}

async function translateText(text, targetLang) {
  serviceStats.apiCalls.translate++;
  targetLang = targetLang || 'en';
  try {
    if (CONFIG.DEEPL_API_KEY) {
      const r = await axios.post('https://api-free.deepl.com/v2/translate', null, {
        params: { text: text, target_lang: targetLang.toUpperCase(), auth_key: CONFIG.DEEPL_API_KEY }, timeout: 10000,
      });
      return '**Traduccion (' + targetLang.toUpperCase() + '):**\n\n' + r.data.translations[0].text;
    }
    return await callAI('Traduce al ' + targetLang + '. Solo muestra la traduccion:\n\n"' + text + '"', 'translate-service');
  } catch (e) {
    return 'Error al traducir. Verifica el codigo de idioma.';
  }
}

async function webSearch(query) {
  serviceStats.apiCalls.search++;
  try {
    if (CONFIG.GOOGLE_SEARCH_KEY && CONFIG.GOOGLE_SEARCH_CX) {
      const r = await axios.get('https://www.googleapis.com/customsearch/v1', {
        params: { key: CONFIG.GOOGLE_SEARCH_KEY, cx: CONFIG.GOOGLE_SEARCH_CX, q: query, num: 5 }, timeout: 10000,
      });
      const items = r.data.items || [];
      if (items.length === 0) return 'No se encontraron resultados.';
      const lines = ['**Resultados: "' + query + '"**', ''];
      for (let i = 0; i < items.length; i++) {
        lines.push('- **[' + items[i].title + '](' + items[i].link + ')**');
        lines.push('  ' + items[i].snippet);
        lines.push('');
      }
      return lines.join('\n');
    }
    if (CONFIG.BRAVE_SEARCH_KEY) {
      const r = await axios.get('https://api.search.brave.com/res/v1/web/search', {
        params: { q: query, count: 5 },
        headers: { 'X-Subscription-Token': CONFIG.BRAVE_SEARCH_KEY }, timeout: 10000,
      });
      const results = r.data.web ? (r.data.web.results || []) : [];
      if (results.length === 0) return 'No se encontraron resultados.';
      const lines = ['**Resultados: "' + query + '"**', ''];
      for (let i = 0; i < results.length; i++) {
        lines.push('- **[' + results[i].title + '](' + results[i].url + ')**');
        lines.push('  ' + (results[i].description || ''));
        lines.push('');
      }
      return lines.join('\n');
    }
    return await callAI('Busca informacion actualizada sobre: ' + query + '\n\nProporciona un resumen con fuentes relevantes.', 'search-service');
  } catch (e) {
    return 'Error en la busqueda web.';
  }
}

async function analyzeCode(code) {
  serviceStats.apiCalls.code++;
  return await callAI(
    'Analiza el siguiente codigo. Proporciona:\n1. Explicacion de que hace\n2. Posibles bugs o mejoras\n3. Mejores practicas\n4. Version optimizada si aplica\n\n```\n' + code + '\n```',
    'code-service'
  );
}

async function calculate(expression) {
  serviceStats.apiCalls.calc++;
  try {
    if (CONFIG.WOLFRAM_APP_ID) {
      const r = await axios.get('https://api.wolframalpha.com/v2/query', {
        params: { input: expression, appid: CONFIG.WOLFRAM_APP_ID, output: 'JSON', format: 'plaintext' }, timeout: 10000,
      });
      const pods = r.data.queryresult ? (r.data.queryresult.pods || []) : [];
      const resultPod = pods.find(function(p) { return p.primary; }) || pods[1];
      if (resultPod && resultPod.subpods && resultPod.subpods[0] && resultPod.subpods[0].plaintext) {
        return '**Calculo:** ' + expression + '\n\n**Resultado:** ' + resultPod.subpods[0].plaintext;
      }
    }
    return await callAI('Resuelve paso a paso: ' + expression + '. Muestra el proceso y el resultado final.', 'calc-service');
  } catch (e) {
    return await callAI('Resuelve paso a paso: ' + expression + '. Muestra el proceso y el resultado final.', 'calc-service');
  }
}

async function getNasaApod() {
  serviceStats.apiCalls.nasa++;
  try {
    const r = await axios.get('https://api.nasa.gov/planetary/apod', {
      params: { api_key: CONFIG.NASA_API_KEY || 'DEMO_KEY' }, timeout: 10000,
    });
    const d = r.data;
    return '**' + d.title + '** (' + d.date + ')\n\n' + d.explanation;
  } catch (e) {
    return 'Error al obtener imagen de NASA.';
  }
}

async function searchMovie(query) {
  serviceStats.apiCalls.movies++;
  try {
    if (CONFIG.TMDB_API_KEY) {
      const r = await axios.get('https://api.themoviedb.org/3/search/multi', {
        params: { api_key: CONFIG.TMDB_API_KEY, query: query, language: 'es-ES' }, timeout: 10000,
      });
      const results = r.data.results ? r.data.results.slice(0, 3) : [];
      if (results.length === 0) return 'No se encontraron resultados.';
      const lines = ['**Resultados para "' + query + '"**', ''];
      for (let i = 0; i < results.length; i++) {
        const m = results[i];
        const title = m.title || m.name || 'N/A';
        const year = (m.release_date || m.first_air_date || 'N/A').substring(0, 4);
        const rating = m.vote_average ? (m.vote_average.toFixed(1) + '/10') : 'N/A';
        const type = m.media_type === 'tv' ? 'Serie' : 'Pelicula';
        lines.push('**' + title + '** (' + year + ') - ' + type);
        lines.push('Calificacion: ' + rating);
        if (m.overview) lines.push(m.overview.substring(0, 200) + '...');
        lines.push('');
      }
      return lines.join('\n');
    }
    return await callAI('Dame informacion sobre la pelicula o serie: "' + query + '". Incluye sinopsis, ano, calificacion y datos interesantes.', 'movies-service');
  } catch (e) {
    return 'Error al buscar peliculas.';
  }
}

async function searchFlights(origin, destination, date) {
  serviceStats.apiCalls.flights++;
  try {
    if (CONFIG.AVIATIONSTACK_KEY) {
      const r = await axios.get('http://api.aviationstack.com/v1/flights', {
        params: { access_key: CONFIG.AVIATIONSTACK_KEY, dep_iata: origin, arr_iata: destination, flight_date: date }, timeout: 10000,
      });
      const flights = r.data.data ? r.data.data.slice(0, 5) : [];
      if (flights.length === 0) return 'No se encontraron vuelos.';
      const lines = ['**Vuelos ' + origin + ' -> ' + destination + '** (' + date + ')', ''];
      for (let i = 0; i < flights.length; i++) {
        const f = flights[i];
        lines.push('- **' + f.airline.name + ' ' + f.flight.iata + '**');
        lines.push('  Salida: ' + (f.departure.scheduled || 'N/A') + '  Llegada: ' + (f.arrival.scheduled || 'N/A'));
        lines.push('  Estado: ' + f.flight_status);
        lines.push('');
      }
      return lines.join('\n');
    }
    return await callAI('Busca informacion sobre vuelos de ' + origin + ' a ' + destination + ' para el ' + date + '.', 'flights-service');
  } catch (e) {
    return 'Error al buscar vuelos.';
  }
}

async function getJoke() {
  try {
    const r = await axios.get('https://v2.jokeapi.dev/joke/Any?lang=es&type=single', { timeout: 5000 });
    return '**Chiste:**\n\n' + r.data.joke;
  } catch (e) {
    return 'Por que los programadores prefieren el modo oscuro? Porque la luz atrae bugs.';
  }
}

async function getRandomFact() {
  try {
    const r = await axios.get('https://uselessfacts.jsph.pl/api/v2/facts/random?language=es', { timeout: 5000 });
    return '**Dato curioso:** ' + r.data.text;
  } catch (e) {
    return 'Los pulpos tienen tres corazones y sangre azul.';
  }
}

async function getQuote() {
  try {
    const r = await axios.get('https://api.quotable.io/random', { timeout: 5000 });
    return '**"' + r.data.content + '"**\n\n-- ' + r.data.author;
  } catch (e) {
    return '**"La unica forma de hacer un gran trabajo es amar lo que haces."**\n\n-- Steve Jobs';
  }
}

async function getDictionary(word) {
  serviceStats.apiCalls.dictionary++;
  try {
    const r = await axios.get('https://api.dictionaryapi.dev/api/v2/entries/en/' + encodeURIComponent(word), { timeout: 10000 });
    const d = r.data[0];
    if (!d) return 'Palabra no encontrada.';
    const lines = ['**' + d.word + '**', ''];
    if (d.phonetic) lines.push('Pronunciacion: ' + d.phonetic);
    if (d.meanings) {
      for (let i = 0; i < Math.min(d.meanings.length, 3); i++) {
        const m = d.meanings[i];
        lines.push('\n**' + m.partOfSpeech + ':**');
        if (m.definitions) {
          for (let j = 0; j < Math.min(m.definitions.length, 2); j++) {
            lines.push('  ' + (j + 1) + '. ' + m.definitions[j].definition);
          }
        }
      }
    }
    return lines.join('\n');
  } catch (e) {
    return await callAI('Define la palabra "' + word + '" con significado, etimologia y ejemplos de uso.', 'dict-service');
  }
}

async function getSpotifyTrack(query) {
  serviceStats.apiCalls.spotify++;
  try {
    if (!CONFIG.SPOTIFY_CLIENT_ID || !CONFIG.SPOTIFY_CLIENT_SECRET) {
      return await callAI('Recomienda canciones relacionadas con: "' + query + '". Incluye artista, album y ano.', 'spotify-service');
    }
    // Get Spotify token
    const tokenRes = await axios.post('https://accounts.spotify.com/api/token',
      'grant_type=client_credentials', {
        headers: {
          'Authorization': 'Basic ' + Buffer.from(CONFIG.SPOTIFY_CLIENT_ID + ':' + CONFIG.SPOTIFY_CLIENT_SECRET).toString('base64'),
          'Content-Type': 'application/x-www-form-urlencoded',
        }, timeout: 10000,
      });
    const token = tokenRes.data.access_token;
    const r = await axios.get('https://api.spotify.com/v1/search', {
      params: { q: query, type: 'track', limit: 3 },
      headers: { 'Authorization': 'Bearer ' + token }, timeout: 10000,
    });
    const tracks = r.data.tracks ? r.data.tracks.items : [];
    if (tracks.length === 0) return 'No se encontraron canciones.';
    const lines = ['**Canciones: "' + query + '"**', ''];
    for (let i = 0; i < tracks.length; i++) {
      const t = tracks[i];
      lines.push('**' + t.name + '** - ' + t.artists[0].name);
      lines.push('Album: ' + t.album.name + ' | Duracion: ' + Math.floor(t.duration_ms / 60000) + ':' + String(Math.floor((t.duration_ms % 60000) / 1000)).padStart(2, '0'));
      lines.push('[Escuchar](' + t.external_urls.spotify + ')');
      lines.push('');
    }
    return lines.join('\n');
  } catch (e) {
    return await callAI('Recomienda canciones relacionadas con: "' + query + '". Incluye artista, album y ano.', 'spotify-service');
  }
}

function getTimeInCity(city) {
  const tz = {
    'mexico': 'America/Mexico_City', 'cdmx': 'America/Mexico_City', 'bogota': 'America/Bogota',
    'lima': 'America/Lima', 'buenos aires': 'America/Argentina/Buenos_Aires', 'santiago': 'America/Santiago',
    'madrid': 'Europe/Madrid', 'barcelona': 'Europe/Madrid', 'london': 'Europe/London',
    'paris': 'Europe/Paris', 'berlin': 'Europe/Berlin', 'rome': 'Europe/Rome',
    'tokyo': 'Asia/Tokyo', 'beijing': 'Asia/Shanghai', 'shanghai': 'Asia/Shanghai',
    'seoul': 'Asia/Seoul', 'sydney': 'Australia/Sydney', 'dubai': 'Asia/Dubai',
    'new york': 'America/New_York', 'los angeles': 'America/Los_Angeles',
    'miami': 'America/New_York', 'mumbai': 'Asia/Kolkata', 'singapore': 'Asia/Singapore',
    'moscow': 'Europe/Moscow', 'istanbul': 'Europe/Istanbul', 'cairo': 'Africa/Cairo',
  };
  const timezone = tz[city.toLowerCase()];
  if (!timezone) return 'Ciudad no reconocida. Prueba: ' + Object.keys(tz).slice(0, 10).join(', ') + '...';
  const now = new Date().toLocaleString('es-ES', {
    timeZone: timezone, weekday: 'long', year: 'numeric', month: 'long', day: 'numeric',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  });
  return '**Hora en ' + city + ':**\n\n' + now;
}

async function getWikiSummary(query) {
  try {
    const r = await axios.get('https://es.wikipedia.org/api/rest_v1/page/summary/' + encodeURIComponent(query), { timeout: 10000 });
    const d = r.data;
    const lines = ['**' + d.title + '**', ''];
    if (d.extract) lines.push(d.extract);
    if (d.content_urls && d.content_urls.desktop) lines.push('\n[Leer mas](' + d.content_urls.desktop.page + ')');
    return lines.join('\n');
  } catch (e) {
    return await callAI('Dame un resumen enciclopedico sobre: ' + query, 'wiki-service');
  }
}

async function getBookInfo(query) {
  try {
    const r = await axios.get('https://www.googleapis.com/books/v1/volumes', {
      params: { q: query, maxResults: 3 }, timeout: 10000,
    });
    const items = r.data.items || [];
    if (items.length === 0) return 'No se encontraron libros.';
    const lines = ['**Libros: "' + query + '"**', ''];
    for (let i = 0; i < items.length; i++) {
      const b = items[i].volumeInfo;
      lines.push('**' + b.title + '**');
      if (b.authors) lines.push('Autores: ' + b.authors.join(', '));
      if (b.publishedDate) lines.push('Fecha: ' + b.publishedDate);
      if (b.description) lines.push(b.description.substring(0, 200) + '...');
      lines.push('');
    }
    return lines.join('\n');
  } catch (e) {
    return 'Error al buscar libros.';
  }
}

async function generateImage(prompt) {
  try {
    if (CONFIG.STABILITY_API_KEY) {
      const r = await axios.post(
        'https://api.stability.ai/v1/generation/stable-diffusion-xl-1024-v1-0/text-to-image',
        { text_prompts: [{ text: prompt, weight: 1 }], cfg_scale: 7, height: 1024, width: 1024, steps: 30, samples: 1 },
        { headers: { 'Authorization': 'Bearer ' + CONFIG.STABILITY_API_KEY, 'Content-Type': 'application/json' }, timeout: 60000 }
      );
      if (r.data.artifacts && r.data.artifacts[0]) {
        return 'Imagen generada con exito usando Stability AI.';
      }
    }
    return await callAI('Describe en detalle como seria una imagen de: ' + prompt + '. Se muy visual y descriptivo.', 'image-service');
  } catch (e) {
    return await callAI('Describe en detalle como seria una imagen de: ' + prompt + '. Se muy visual y descriptivo.', 'image-service');
  }
}

// ============ LARK MESSAGE SENDING ============

async function sendCardMessage(chatId, title, content, template) {
  const c = getLarkClient();
  if (!c) { log('error', 'SEND', 'No Lark client'); return; }
  try {
    await c.im.message.create({
      params: { receive_id_type: 'chat_id' },
      data: {
        receive_id: chatId,
        msg_type: 'interactive',
        content: JSON.stringify({
          config: { wide_screen_mode: true },
          header: { title: { tag: 'plain_text', content: title }, template: template || 'blue' },
          elements: [{ tag: 'markdown', content: content }],
        }),
      },
    });
  } catch (e) {
    log('error', 'SEND', 'Card failed: ' + e.message);
  }
}

async function sendTextMessage(chatId, text) {
  const c = getLarkClient();
  if (!c) return;
  try {
    await c.im.message.create({
      params: { receive_id_type: 'chat_id' },
      data: { receive_id: chatId, msg_type: 'text', content: JSON.stringify({ text: text }) },
    });
  } catch (e) {
    log('error', 'SEND', 'Text failed: ' + e.message);
  }
}

// ============ COMMAND ROUTER ============

async function routeCommand(text, chatId, userId, msgId) {
  const parts = text.split(/\s+/);
  const cmd = parts[0].toLowerCase();
  const args = text.substring(parts[0].length).trim();

  serviceStats.commandUsage[cmd] = (serviceStats.commandUsage[cmd] || 0) + 1;
  serviceStats.totalMessages++;

  // Typing reaction
  if (msgId && getLarkClient()) {
    try {
      await getLarkClient().im.message.patch({ path: { message_id: msgId }, data: { reactions: [{ reaction_type: 'ThumbUp' }] } });
    } catch (e) { /* optional */ }
  }

  // ---- HELP ----
  if (cmd === '/help' || cmd === '/ayuda') {
    await sendCardMessage(chatId, 'NEXA PRO AI - Ayuda Completa',
      '**NEXA PRO AI Assistant v4.0**\n\n' +
      '**IA y Chat:**\n' +
      '  /chat [pregunta] — Chatea con IA (Dify/OpenAI/Gemini/Claude)\n' +
      '  /search [tema] — Busqueda web\n' +
      '  /image [desc] — Genera imagenes\n' +
      '  /document [tema] — Crea documentos\n' +
      '  /wiki [tema] — Wikipedia\n' +
      '  /ask [tema] — Sabiduria profunda\n\n' +
      '**Clima y Viajes:**\n' +
      '  /weather [ciudad] — Clima actual\n' +
      '  /forecast [ciudad] — Pronostico 5 dias\n' +
      '  /flight [orig] [dest] [fecha] — Vuelos\n' +
      '  /time [ciudad] — Hora mundial\n\n' +
      '**Finanzas:**\n' +
      '  /stock [simbolo] — Acciones (AAPL, TSLA)\n' +
      '  /crypto [moneda] — Criptomonedas\n' +
      '  /exchange [cant] [de] [a] — Monedas\n\n' +
      '**Informacion:**\n' +
      '  /news [tema] — Noticias\n' +
      '  /movie [nombre] — Peliculas/Series\n' +
      '  /book [titulo] — Buscar libros\n' +
      '  /nasa — Imagen astronomica del dia\n' +
      '  /dict [palabra] — Diccionario Ingles\n\n' +
      '**Herramientas:**\n' +
      '  /translate [texto] [idioma] — Traductor\n' +
      '  /code [codigo] — Analisis de codigo\n' +
      '  /calc [expresion] — Calculadora\n' +
      '  /qr [texto] — Codigo QR\n' +
      '  /spotify [cancion] — Buscar musica\n\n' +
      '**Sabiduria y Entretenimiento:**\n' +
      '  /quote — Cita inspiradora\n' +
      '  /joke — Chiste aleatorio\n' +
      '  /fact — Dato curioso\n' +
      '  /wisdom — Sabiduria del dia\n\n' +
      '**Sistema:**\n' +
      '  /new — Nueva conversacion\n' +
      '  /status — Estado del sistema\n' +
      '  /admin [secreto] — Panel admin\n\n' +
      'Tip: Escribe directamente sin comando y te respondere con IA.\n' +
      'Limites: 30 mensajes/minuto. Memoria: 24 horas.',
      'blue'
    );
    return;
  }

  // ---- CHAT / default ----
  if (cmd === '/chat') {
    if (!args) { await sendCardMessage(chatId, 'Chat con IA', 'Envia un mensaje. **Ejemplo:** /chat Que es la inteligencia artificial?'); return; }
    const r = await callAI(args, userId);
    await sendCardMessage(chatId, 'NEXA PRO AI', truncate(r));
    return;
  }

  // ---- ASK (wisdom mode) ----
  if (cmd === '/ask' || cmd === '/wisdom') {
    const q = args || 'Dame una reflexion filosofica profunda sobre la vida';
    const r = await callAI(
      'Como sabio filosofo y erudito, responde con profundidad, sabiduria y perspectiva multiple: ' + q + '\n\nIncluye perspectivas filosoficas, cientificas, culturales y practicas. Usa citas de pensadores cuando sea relevante.',
      userId,
      'Eres un sabio con conocimiento enciclopedico, filosofico, cientifico, artistico y espiritual. Respondes con profundidad, elegancia y multiples perspectivas. Usas metaforas, analogias y citas de grandes pensadores.'
    );
    await sendCardMessage(chatId, 'Sabiduria NEXA', truncate(r), 'indigo');
    return;
  }

  // ---- SEARCH ----
  if (cmd === '/search' || cmd === '/buscar') {
    if (!args) { await sendCardMessage(chatId, 'Busqueda', 'Especifica que buscar. **Ejemplo:** /search inteligencia artificial'); return; }
    const r = await webSearch(args);
    await sendCardMessage(chatId, 'Resultados de Busqueda', truncate(r), 'green');
    return;
  }

  // ---- WEATHER ----
  if (cmd === '/weather' || cmd === '/clima') {
    if (!args) { await sendCardMessage(chatId, 'Clima', 'Especifica ciudad. **Ejemplo:** /weather Mexico City'); return; }
    const r = await getWeather(args);
    await sendCardMessage(chatId, 'Clima Actual', r, 'turquoise');
    return;
  }

  // ---- FORECAST ----
  if (cmd === '/forecast' || cmd === '/pronostico') {
    if (!args) { await sendCardMessage(chatId, 'Pronostico', 'Especifica ciudad. **Ejemplo:** /forecast Bogota'); return; }
    const r = await getForecast(args);
    await sendCardMessage(chatId, 'Pronostico 5 Dias', r, 'turquoise');
    return;
  }

  // ---- STOCK ----
  if (cmd === '/stock' || cmd === '/accion') {
    if (!args) { await sendCardMessage(chatId, 'Acciones', 'Especifica simbolo. **Ejemplo:** /stock AAPL'); return; }
    const r = await getStockQuote(args.toUpperCase());
    await sendCardMessage(chatId, 'Cotizacion', r, 'orange');
    return;
  }

  // ---- CRYPTO ----
  if (cmd === '/crypto' || cmd === '/cripto') {
    if (!args) { await sendCardMessage(chatId, 'Cripto', 'Especifica moneda. **Ejemplo:** /crypto bitcoin'); return; }
    const r = await getCryptoPrice(args);
    await sendCardMessage(chatId, 'Criptomoneda', r, 'orange');
    return;
  }

  // ---- EXCHANGE ----
  if (cmd === '/exchange' || cmd === '/moneda') {
    const p = args.split(/\s+/);
    if (p.length < 2) { await sendCardMessage(chatId, 'Moneda', '**Ejemplo:** /exchange 100 USD MXN  o  /exchange USD EUR'); return; }
    let amount = 1, from, to;
    if (p.length >= 3 && !isNaN(p[0])) { amount = parseFloat(p[0]); from = p[1]; to = p[2]; }
    else { from = p[0]; to = p[1]; }
    const r = await getExchangeRate(from, to, amount);
    await sendCardMessage(chatId, 'Conversion de Moneda', r, 'orange');
    return;
  }

  // ---- NEWS ----
  if (cmd === '/news' || cmd === '/noticias') {
    const r = await getNews(args);
    await sendCardMessage(chatId, 'Noticias', truncate(r), 'violet');
    return;
  }

  // ---- TRANSLATE ----
  if (cmd === '/translate' || cmd === '/traducir') {
    const p = args.split(/\s+/);
    if (p.length < 2) { await sendCardMessage(chatId, 'Traductor', '**Ejemplo:** /translate Hello world es'); return; }
    const targetLang = p[p.length - 1];
    const textToTranslate = p.slice(0, -1).join(' ');
    const r = await translateText(textToTranslate, targetLang);
    await sendCardMessage(chatId, 'Traduccion', r, 'indigo');
    return;
  }

  // ---- CODE ----
  if (cmd === '/code' || cmd === '/codigo') {
    if (!args) { await sendCardMessage(chatId, 'Codigo', 'Envia codigo. **Ejemplo:** /code function hello() { return "world"; }'); return; }
    const r = await analyzeCode(args);
    await sendCardMessage(chatId, 'Analisis de Codigo', truncate(r), 'purple');
    return;
  }

  // ---- CALC ----
  if (cmd === '/calc' || cmd === '/calcular') {
    if (!args) { await sendCardMessage(chatId, 'Calculadora', '**Ejemplo:** /calc 2^10 + sqrt(144)'); return; }
    const r = await calculate(args);
    await sendCardMessage(chatId, 'Calculo', r, 'wathet');
    return;
  }

  // ---- IMAGE ----
  if (cmd === '/image' || cmd === '/imagen') {
    if (!args) { await sendCardMessage(chatId, 'Imagen', 'Describe la imagen. **Ejemplo:** /image atardecer en la playa'); return; }
    const r = await generateImage(args);
    await sendCardMessage(chatId, 'Imagen', truncate(r), 'green');
    return;
  }

  // ---- MOVIE ----
  if (cmd === '/movie' || cmd === '/pelicula') {
    if (!args) { await sendCardMessage(chatId, 'Pelicula', 'Especifica nombre. **Ejemplo:** /movie Inception'); return; }
    const r = await searchMovie(args);
    await sendCardMessage(chatId, 'Pelicula/Serie', truncate(r), 'red');
    return;
  }

  // ---- FLIGHT ----
  if (cmd === '/flight' || cmd === '/vuelo') {
    const p = args.split(/\s+/);
    if (p.length < 3) { await sendCardMessage(chatId, 'Vuelos', '**Ejemplo:** /flight BOG MEX 2025-01-15'); return; }
    const r = await searchFlights(p[0], p[1], p[2]);
    await sendCardMessage(chatId, 'Vuelos', truncate(r), 'blue');
    return;
  }

  // ---- NASA ----
  if (cmd === '/nasa') {
    const r = await getNasaApod();
    await sendCardMessage(chatId, 'NASA - Imagen del Dia', truncate(r), 'indigo');
    return;
  }

  // ---- JOKE ----
  if (cmd === '/joke' || cmd === '/chiste') {
    const r = await getJoke();
    await sendCardMessage(chatId, 'Chiste', r, 'yellow');
    return;
  }

  // ---- FACT ----
  if (cmd === '/fact' || cmd === '/dato') {
    const r = await getRandomFact();
    await sendCardMessage(chatId, 'Dato Curioso', r, 'violet');
    return;
  }

  // ---- QUOTE ----
  if (cmd === '/quote' || cmd === '/cita') {
    const r = await getQuote();
    await sendCardMessage(chatId, 'Cita Inspiradora', r, 'indigo');
    return;
  }

  // ---- SPOTIFY ----
  if (cmd === '/spotify' || cmd === '/music' || cmd === '/musica') {
    if (!args) { await sendCardMessage(chatId, 'Musica', 'Especifica cancion/artista. **Ejemplo:** /spotify Coldplay'); return; }
    const r = await getSpotifyTrack(args);
    await sendCardMessage(chatId, 'Musica', truncate(r), 'green');
    return;
  }

  // ---- DICTIONARY ----
  if (cmd === '/dict' || cmd === '/diccionario') {
    if (!args) { await sendCardMessage(chatId, 'Diccionario', '**Ejemplo:** /dict serendipity'); return; }
    const r = await getDictionary(args);
    await sendCardMessage(chatId, 'Diccionario', truncate(r), 'wathet');
    return;
  }

  // ---- WIKI ----
  if (cmd === '/wiki' || cmd === '/wikipedia') {
    if (!args) { await sendCardMessage(chatId, 'Wikipedia', '**Ejemplo:** /wiki Inteligencia artificial'); return; }
    const r = await getWikiSummary(args);
    await sendCardMessage(chatId, 'Wikipedia', truncate(r), 'blue');
    return;
  }

  // ---- BOOK ----
  if (cmd === '/book' || cmd === '/libro') {
    if (!args) { await sendCardMessage(chatId, 'Libros', '**Ejemplo:** /book Cien anos de soledad'); return; }
    const r = await getBookInfo(args);
    await sendCardMessage(chatId, 'Libros', truncate(r), 'violet');
    return;
  }

  // ---- QR ----
  if (cmd === '/qr') {
    if (!args) { await sendCardMessage(chatId, 'QR', '**Ejemplo:** /qr https://example.com'); return; }
    const r = '**Codigo QR para:** ' + args + '\n\n![QR](https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=' + encodeURIComponent(args) + ')';
    await sendCardMessage(chatId, 'Codigo QR', r, 'green');
    return;
  }

  // ---- TIME ----
  if (cmd === '/time' || cmd === '/hora') {
    if (!args) { await sendCardMessage(chatId, 'Hora', '**Ejemplo:** /time Tokyo'); return; }
    const r = getTimeInCity(args);
    await sendCardMessage(chatId, 'Hora Mundial', r, 'wathet');
    return;
  }

  // ---- DOCUMENT ----
  if (cmd === '/document' || cmd === '/documento') {
    if (!args) { await sendCardMessage(chatId, 'Documento', '**Ejemplo:** /document Plan de Marketing Digital'); return; }
    const r = await callAI('Crea un documento profesional sobre: ' + args + '\n\nIncluye: titulo, introduccion, secciones con subsecciones, conclusiones y recomendaciones.', userId);
    await sendCardMessage(chatId, 'Documento', truncate(r), 'blue');
    return;
  }

  // ---- NEW CONVERSATION ----
  if (cmd === '/new' || cmd === '/reset' || cmd === '/nuevo') {
    conversationStore.delete(userId);
    await sendCardMessage(chatId, 'Nueva Conversacion', 'Se ha iniciado una nueva conversacion. La memoria anterior ha sido borrada.');
    return;
  }

  // ---- STATUS ----
  if (cmd === '/status' || cmd === '/estado') {
    const uptime = Math.floor(process.uptime());
    const h = Math.floor(uptime / 3600);
    const m = Math.floor((uptime % 3600) / 60);
    const mem = process.memoryUsage();
    const r = '**NEXA PRO AI v4.0**\n\n' +
      '- **Uptime:** ' + h + 'h ' + m + 'm\n' +
      '- **Mensajes:** ' + serviceStats.totalMessages + '\n' +
      '- **Errores:** ' + serviceStats.errors + '\n' +
      '- **Conversaciones:** ' + conversationStore.size + '\n' +
      '- **Memoria:** ' + (mem.heapUsed / 1024 / 1024).toFixed(1) + ' MB\n\n' +
      '**APIs:**\n' +
      '- Dify: ' + (CONFIG.DIFY_API_KEY ? 'OK' : '-') + '  OpenAI: ' + (CONFIG.OPENAI_API_KEY ? 'OK' : '-') + '  Gemini: ' + (CONFIG.GEMINI_API_KEY ? 'OK' : '-') + '  Claude: ' + (CONFIG.CLAUDE_API_KEY ? 'OK' : '-') + '\n' +
      '- Weather: ' + (CONFIG.WEATHER_API_KEY ? 'OK' : 'fallback') + '  Finance: ' + (CONFIG.ALPHA_VANTAGE_KEY ? 'OK' : '-') + '  News: ' + (CONFIG.NEWS_API_KEY ? 'OK' : '-') + '\n' +
      '- Translate: ' + (CONFIG.DEEPL_API_KEY ? 'OK' : '-') + '  Search: ' + (CONFIG.GOOGLE_SEARCH_KEY || CONFIG.BRAVE_SEARCH_KEY ? 'OK' : '-') + '  Movies: ' + (CONFIG.TMDB_API_KEY ? 'OK' : '-') + '\n' +
      '- NASA: ' + (CONFIG.NASA_API_KEY ? 'OK' : 'demo') + '  Wolfram: ' + (CONFIG.WOLFRAM_APP_ID ? 'OK' : '-') + '  Flights: ' + (CONFIG.AVIATIONSTACK_KEY ? 'OK' : '-') + '\n' +
      '- Spotify: ' + (CONFIG.SPOTIFY_CLIENT_ID ? 'OK' : '-') + '  Dictionary: ' + (CONFIG.DICTIONARY_API_KEY ? 'OK' : 'free') + '  Wikipedia: free  Books: free';
    await sendCardMessage(chatId, 'Estado del Sistema', r, 'blue');
    return;
  }

  // ---- ADMIN ----
  if (cmd === '/admin') {
    if (args !== CONFIG.ADMIN_SECRET) { await sendTextMessage(chatId, 'Acceso denegado.'); return; }
    const topCmds = Object.entries(serviceStats.commandUsage).sort(function(a, b) { return b[1] - a[1]; }).slice(0, 10).map(function(c) { return '- ' + c[0] + ': ' + c[1]; }).join('\n');
    const r = '**Admin Panel**\n\n**Top Comandos:**\n' + (topCmds || 'Sin datos') + '\n\n**API Calls:**\n' +
      Object.entries(serviceStats.apiCalls).map(function(e) { return '- ' + e[0] + ': ' + e[1]; }).join('\n') +
      '\n\n**Errores Recientes:**\n' + (adminLogs.slice(-5).map(function(l) { return l.message; }).join('\n') || 'Sin errores');
    await sendCardMessage(chatId, 'Admin Panel', r, 'red');
    return;
  }

  // ---- DEFAULT: plain text -> AI ----
  if (!cmd.startsWith('/')) {
    const r = await callAI(text, userId);
    await sendCardMessage(chatId, 'NEXA PRO AI', truncate(r));
    return;
  }

  // Unknown command -> AI
  const r = await callAI(text, userId);
  await sendCardMessage(chatId, 'NEXA PRO AI', truncate(r));
}

// ============ EVENT HANDLER ============

async function handleImMessageReceive(data) {
  const msgId = data.message.message_id;
  const chatId = data.message.chat_id;
  const msgType = data.message.message_type;
  const senderType = data.sender.sender_type;

  if (senderType === 'app') return;

  const userId = data.sender.sender_id.open_id || data.sender.sender_id.user_id || 'unknown';

  let text = '';
  if (msgType === 'text') {
    try { text = JSON.parse(data.message.content).text || ''; } catch (e) { text = data.message.content || ''; }
  } else if (msgType === 'post') {
    try {
      const content = JSON.parse(data.message.content);
      text = content.title || '';
      if (content.content) { for (var i = 0; i < content.content.length; i++) { for (var j = 0; j < content.content[i].length; j++) { if (content.content[i][j].text) text += content.content[i][j].text; } } }
    } catch (e) { text = ''; }
  } else {
    await sendTextMessage(chatId, 'Recibi tu mensaje tipo "' + msgType + '". Solo proceso texto. Usa /help');
    return;
  }

  text = text.trim().replace(/@_user_\d+\s*/g, '').trim();
  if (!text) return;

  if (!checkRateLimit(userId)) {
    await sendTextMessage(chatId, 'Has enviado muchos mensajes. Espera un momento.');
    return;
  }

  log('info', 'MSG', 'User ' + userId + ': ' + text.substring(0, 80));

  try {
    await routeCommand(text, chatId, userId, msgId);
  } catch (e) {
    log('error', 'CMD', 'Command failed: ' + e.message);
    await sendTextMessage(chatId, 'Error al procesar tu mensaje. Intenta de nuevo.');
  }
}

// ============ EXPORTS ============

module.exports = {
  CONFIG,
  getLarkClient,
  callAI,
  handleImMessageReceive,
  routeCommand,
  sendCardMessage,
  sendTextMessage,
  serviceStats,
  // All service functions
  getWeather, getForecast, getStockQuote, getCryptoPrice, getExchangeRate,
  getNews, translateText, webSearch, analyzeCode, calculate,
  getNasaApod, searchMovie, searchFlights, getJoke, getRandomFact,
  getQuote, getDictionary, getSpotifyTrack, getTimeInCity, getWikiSummary,
  getBookInfo, generateImage,
};
