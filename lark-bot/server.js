/**
 * ============================================================
 *  NEXA PRO AI Assistant v3.0 — Lark Suite Bot
 *  Multi-AI, Multi-Service, Full-Featured Integration Server
 * ============================================================
 */

require('dotenv').config();
const lark = require('@larksuiteoapi/node-sdk');
const axios = require('axios');
const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const compression = require('compression');
const rateLimit = require('express-rate-limit');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const { URL } = require('url');

// ============ CONFIGURATION ============
const CONFIG = {
  // Lark
  LARK_APP_ID: process.env.LARK_APP_ID || '',
  LARK_APP_SECRET: process.env.LARK_APP_SECRET || '',
  LARK_VERIFICATION_TOKEN: process.env.LARK_VERIFICATION_TOKEN || '',
  LARK_ENCRYPT_KEY: process.env.LARK_ENCRYPT_KEY || '',

  // Dify AI
  DIFY_API_KEY: process.env.DIFY_API_KEY || '',
  DIFY_BASE_URL: process.env.DIFY_BASE_URL || 'https://api.dify.ai/v1',

  // OpenAI Compatible
  OPENAI_API_KEY: process.env.OPENAI_API_KEY || '',
  OPENAI_BASE_URL: process.env.OPENAI_BASE_URL || 'https://api.openai.com/v1',
  OPENAI_MODEL: process.env.OPENAI_MODEL || 'gpt-4o-mini',

  // Google Gemini
  GEMINI_API_KEY: process.env.GEMINI_API_KEY || '',

  // Weather
  WEATHER_API_KEY: process.env.WEATHER_API_KEY || '',
  WEATHER_BASE_URL: 'https://api.openweathermap.org/data/2.5',

  // Finance
  ALPHA_VANTAGE_KEY: process.env.ALPHA_VANTAGE_KEY || '',
  EXCHANGE_RATE_KEY: process.env.EXCHANGE_RATE_KEY || '',

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

  // Maps & Location
  GOOGLE_MAPS_KEY: process.env.GOOGLE_MAPS_KEY || '',

  // NASA
  NASA_API_KEY: process.env.NASA_API_KEY || '',

  // TMDB (Movies)
  TMDB_API_KEY: process.env.TMDB_API_KEY || '',

  // Spotify
  SPOTIFY_CLIENT_ID: process.env.SPOTIFY_CLIENT_ID || '',
  SPOTIFY_CLIENT_SECRET: process.env.SPOTIFY_CLIENT_SECRET || '',

  // Wolfram Alpha
  WOLFRAM_APP_ID: process.env.WOLFRAM_APP_ID || '',

  // Aviation / Flights
  AVIATIONSTACK_KEY: process.env.AVIATIONSTACK_KEY || '',

  // Server
  PORT: process.env.PORT || 3000,
  NODE_ENV: process.env.NODE_ENV || 'development',
  ADMIN_SECRET: process.env.ADMIN_SECRET || 'nexa-admin-2024',

  // Rate Limiting
  RATE_LIMIT_WINDOW: 60000,
  RATE_LIMIT_MAX: 30,
  MAX_CONVERSATION_AGE: 24 * 60 * 60 * 1000,
  MAX_MESSAGE_LENGTH: 4000,

  // Feature Flags
  ENABLE_WEATHER: true,
  ENABLE_FINANCE: true,
  ENABLE_NEWS: true,
  ENABLE_TRANSLATE: true,
  ENABLE_SEARCH: true,
  ENABLE_CODE: true,
  ENABLE_CALC: true,
  ENABLE_NASA: true,
  ENABLE_MOVIES: true,
  ENABLE_FLIGHTS: true,
  ENABLE_IMAGE_GEN: true,
};

// ============ LARK CLIENT ============
const client = new lark.Client({
  appId: CONFIG.LARK_APP_ID,
  appSecret: CONFIG.LARK_APP_SECRET,
  domain: lark.Domain.Lark,
});

// ============ IN-MEMORY STORAGE ============
const conversationStore = new Map();
const rateLimitStore = new Map();
const userPreferences = new Map();
const adminLogs = [];
const serviceStats = {
  totalMessages: 0,
  commandUsage: {},
  apiCalls: { dify: 0, openai: 0, gemini: 0, weather: 0, finance: 0, news: 0, translate: 0, search: 0, code: 0 },
  startTime: Date.now(),
  errors: 0,
};

// ============ UTILITY FUNCTIONS ============
function log(level, category, message, data = null) {
  const timestamp = new Date().toISOString();
  const logEntry = { timestamp, level, category, message, data };
  console.log(`[${timestamp}] [${level}] [${category}] ${message}`);
  if (data) console.log(JSON.stringify(data, null, 2));
  if (level === 'error') {
    serviceStats.errors++;
    adminLogs.push(logEntry);
    if (adminLogs.length > 500) adminLogs.shift();
  }
}

function truncate(text, maxLen = CONFIG.MAX_MESSAGE_LENGTH) {
  if (!text || text.length <= maxLen) return text;
  return text.substring(0, maxLen) + '\n\n... *(respuesta truncada)*';
}

function formatNumber(num) {
  if (num >= 1e9) return (num / 1e9).toFixed(2) + 'B';
  if (num >= 1e6) return (num / 1e6).toFixed(2) + 'M';
  if (num >= 1e3) return (num / 1e3).toFixed(2) + 'K';
  return num.toString();
}

function escapeMarkdown(text) {
  return text.replace(/[\\`*_{}[\]()#+\-.!|]/g, (c) => '\\' + c);
}

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
  // Cleanup rate limit store
  for (const [userId, data] of rateLimitStore.entries()) {
    if (now > data.resetTime) {
      rateLimitStore.delete(userId);
    }
  }
}, 60 * 60 * 1000);

// ============ AI PROVIDERS ============

// --- Dify AI ---
async function callDifyAI(query, userId, conversationId = null) {
  if (!CONFIG.DIFY_API_KEY) {
    return null; // Fall through to next provider
  }
  serviceStats.apiCalls.dify++;
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
        provider: 'dify',
        lastActivity: Date.now(),
      });
    }

    return response.data.answer || null;
  } catch (error) {
    log('error', 'DIFY', 'API call failed', { status: error.response?.status, data: error.response?.data });
    return null;
  }
}

// --- OpenAI Compatible ---
async function callOpenAI(query, userId, systemPrompt = null) {
  if (!CONFIG.OPENAI_API_KEY) return null;
  serviceStats.apiCalls.openai++;

  try {
    const convData = conversationStore.get(userId);
    const messages = [];

    if (systemPrompt) {
      messages.push({ role: 'system', content: systemPrompt });
    } else {
      messages.push({
        role: 'system',
        content: 'Eres NEXA PRO AI, un asistente inteligente avanzado integrado en Lark Suite. Respondes en el idioma del usuario. Eres helpful, preciso y detallado.'
      });
    }

    messages.push({ role: 'user', content: query });

    const response = await axios.post(
      `${CONFIG.OPENAI_BASE_URL}/chat/completions`,
      {
        model: CONFIG.OPENAI_MODEL,
        messages,
        max_tokens: 2000,
        temperature: 0.7,
      },
      {
        headers: {
          'Authorization': `Bearer ${CONFIG.OPENAI_API_KEY}`,
          'Content-Type': 'application/json',
        },
        timeout: 60000,
      }
    );

    conversationStore.set(userId, { provider: 'openai', lastActivity: Date.now() });
    return response.data.choices?.[0]?.message?.content || null;
  } catch (error) {
    log('error', 'OPENAI', 'API call failed', { status: error.response?.status, data: error.response?.data });
    return null;
  }
}

// --- Google Gemini ---
async function callGemini(query, userId) {
  if (!CONFIG.GEMINI_API_KEY) return null;
  serviceStats.apiCalls.gemini++;

  try {
    const response = await axios.post(
      `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${CONFIG.GEMINI_API_KEY}`,
      {
        contents: [{ parts: [{ text: query }] }],
        generationConfig: { maxOutputTokens: 2000, temperature: 0.7 },
      },
      { timeout: 60000 }
    );

    conversationStore.set(userId, { provider: 'gemini', lastActivity: Date.now() });
    const text = response.data.candidates?.[0]?.content?.parts?.[0]?.text;
    return text || null;
  } catch (error) {
    log('error', 'GEMINI', 'API call failed', { status: error.response?.status, data: error.response?.data });
    return null;
  }
}

// --- Multi-provider AI with fallback ---
async function callAI(query, userId, systemPrompt = null) {
  // Try Dify first (has conversation memory)
  const convData = conversationStore.get(userId);
  if (convData?.provider === 'dify' || CONFIG.DIFY_API_KEY) {
    const result = await callDifyAI(query, userId, convData?.conversationId);
    if (result) return result;
  }

  // Fallback to OpenAI
  const openaiResult = await callOpenAI(query, userId, systemPrompt);
  if (openaiResult) return openaiResult;

  // Fallback to Gemini
  const geminiResult = await callGemini(query, userId);
  if (geminiResult) return geminiResult;

  return '❌ Todos los proveedores de IA no están disponibles. Verifica las claves API en las variables de entorno.';
}

// ============ SERVICE MODULES ============

// --- Weather Service ---
async function getWeather(location) {
  serviceStats.apiCalls.weather++;
  try {
    // Try OpenWeatherMap
    if (CONFIG.WEATHER_API_KEY) {
      const response = await axios.get(`${CONFIG.WEATHER_BASE_URL}/weather`, {
        params: { q: location, appid: CONFIG.WEATHER_API_KEY, units: 'metric', lang: 'es' },
        timeout: 10000,
      });
      const d = response.data;
      return `**${d.name}, ${d.sys.country}**\n\n` +
        `- **Temperatura:** ${d.main.temp}°C (sensación: ${d.main.feels_like}°C)\n` +
        `- **Clima:** ${d.weather[0].description}\n` +
        `- **Humedad:** ${d.main.humidity}%\n` +
        `- **Viento:** ${d.wind.speed} m/s\n` +
        `- **Presión:** ${d.main.pressure} hPa\n` +
        `- **Visibilidad:** ${d.visibility / 1000} km`;
    }

    // Fallback to wttr.in (no API key needed)
    const response = await axios.get(`https://wttr.in/${encodeURIComponent(location)}?format=j1`, { timeout: 10000 });
    const d = response.data;
    const current = d.current_condition[0];
    return `**${location}**\n\n` +
      `- **Temperatura:** ${current.temp_C}°C (sensación: ${current.FeelsLikeC}°C)\n` +
      `- **Clima:** ${current.weatherDesc[0].value}\n` +
      `- **Humedad:** ${current.humidity}%\n` +
      `- **Viento:** ${current.windspeedKmph} km/h\n` +
      `- **Visibilidad:** ${current.visibility} km`;
  } catch (error) {
    log('error', 'WEATHER', 'Failed', { location, error: error.message });
    return '❌ No se pudo obtener el clima. Verifica el nombre de la ciudad.';
  }
}

// --- Weather Forecast (5-day) ---
async function getWeatherForecast(location) {
  serviceStats.apiCalls.weather++;
  try {
    if (!CONFIG.WEATHER_API_KEY) {
      return '⚠️ Se requiere WEATHER_API_KEY para pronósticos. Usa /weather para datos actuales (sin API key).';
    }
    const response = await axios.get(`${CONFIG.WEATHER_BASE_URL}/forecast`, {
      params: { q: location, appid: CONFIG.WEATHER_API_KEY, units: 'metric', lang: 'es' },
      timeout: 10000,
    });
    const forecasts = response.data.list.filter((_, i) => i % 8 === 0); // Every 24h
    let result = `**Pronóstico para ${response.data.city.name}**\n\n`;
    for (const f of forecasts) {
      const date = new Date(f.dt * 1000).toLocaleDateString('es', { weekday: 'short', day: 'numeric' });
      result += `- **${date}:** ${f.main.temp}°C, ${f.weather[0].description}\n`;
    }
    return result;
  } catch (error) {
    return '❌ No se pudo obtener el pronóstico.';
  }
}

// --- Finance Service ---
async function getStockQuote(symbol) {
  serviceStats.apiCalls.finance++;
  try {
    if (CONFIG.ALPHA_VANTAGE_KEY) {
      const response = await axios.get('https://www.alphavantage.co/query', {
        params: { function: 'GLOBAL_QUOTE', symbol, apikey: CONFIG.ALPHA_VANTAGE_KEY },
        timeout: 10000,
      });
      const q = response.data['Global Quote'];
      if (!q || !q['01. symbol']) return '❌ Símbolo no encontrado. Ejemplo: AAPL, GOOGL, TSLA';
      const change = parseFloat(q['10. change percent']);
      const emoji = change >= 0 ? '📈' : '📉';
      return `**${q['01. symbol']}** ${emoji}\n\n` +
        `- **Precio:** $${parseFloat(q['05. price']).toFixed(2)}\n` +
        `- **Apertura:** $${parseFloat(q['02. open']).toFixed(2)}\n` +
        `- **Máximo:** $${parseFloat(q['03. high']).toFixed(2)}\n` +
        `- **Mínimo:** $${parseFloat(q['04. low']).toFixed(2)}\n` +
        `- **Volumen:** ${formatNumber(parseInt(q['06. volume']))}\n` +
        `- **Cambio:** ${q['10. change percent']}`;
    }

    // Fallback: use AI
    return await callAI(`Dame la cotización actual de la acción ${symbol} y su rendimiento reciente. Si no tienes datos en tiempo real, proporciona la información más reciente que tengas.`, 'finance-service');
  } catch (error) {
    return '❌ Error al obtener datos financieros.';
  }
}

// --- Exchange Rate Service ---
async function getExchangeRate(from, to, amount = 1) {
  serviceStats.apiCalls.finance++;
  try {
    const response = await axios.get(`https://api.exchangerate-api.com/v4/latest/${from.toUpperCase()}`, { timeout: 10000 });
    const rate = response.data.rates[to.toUpperCase()];
    if (!rate) return '❌ Moneda no encontrada. Ejemplo: USD, EUR, MXN, COP';
    const converted = (amount * rate).toFixed(4);
    return `**Conversión de Moneda**\n\n` +
      `- **${amount} ${from.toUpperCase()}** = **${converted} ${to.toUpperCase()}**\n` +
      `- **Tasa:** 1 ${from.toUpperCase()} = ${rate.toFixed(6)} ${to.toUpperCase()}\n` +
      `- **Fuente:** ExchangeRate-API\n` +
      `- **Actualizado:** ${response.data.date}`;
  } catch (error) {
    return '❌ Error al obtener tasas de cambio.';
  }
}

// --- Crypto Price ---
async function getCryptoPrice(symbol) {
  serviceStats.apiCalls.finance++;
  try {
    const response = await axios.get(`https://api.coingecko.com/api/v3/simple/price`, {
      params: { ids: symbol.toLowerCase(), vs_currencies: 'usd,eur,mxn', include_24hr_change: 'true' },
      timeout: 10000,
    });
    const data = response.data[symbol.toLowerCase()];
    if (!data) return '❌ Criptomoneda no encontrada. Ejemplo: bitcoin, ethereum, solana';
    return `**${symbol.toUpperCase()}**\n\n` +
      `- **USD:** $${data.usd?.toLocaleString() || 'N/A'}\n` +
      `- **EUR:** €${data.eur?.toLocaleString() || 'N/A'}\n` +
      `- **MXN:** $${data.mxn?.toLocaleString() || 'N/A'}\n` +
      `- **Cambio 24h:** ${data.usd_24h_change ? data.usd_24h_change.toFixed(2) + '%' : 'N/A'}`;
  } catch (error) {
    return '❌ Error al obtener precio de criptomoneda.';
  }
}

// --- News Service ---
async function getNews(query = 'technology', country = 'us') {
  serviceStats.apiCalls.news++;
  try {
    if (CONFIG.NEWS_API_KEY) {
      const response = await axios.get('https://newsapi.org/v2/top-headlines', {
        params: { q: query, country, apiKey: CONFIG.NEWS_API_KEY, pageSize: 5 },
        timeout: 10000,
      });
      const articles = response.data.articles || [];
      if (articles.length === 0) return '❌ No se encontraron noticias.';
      let result = `**Noticias sobre "${query}"**\n\n';
      for (let i = 0; i < articles.length; i++) {
        const a = articles[i];
        result += `**${i + 1}. ${a.title}**\n${a.description || ''}\n*Fuente: ${a.source?.name || 'N/A'}*\n\n`;
      }
      return result;
    }

    // Fallback: AI summary
    return await callAI(`Dame un resumen de las 5 noticias más importantes sobre "${query}" del día de hoy.`, 'news-service');
  } catch (error) {
    return '❌ Error al obtener noticias.';
  }
}

// --- Translation Service ---
async function translateText(text, targetLang = 'en') {
  serviceStats.apiCalls.translate++;
  try {
    if (CONFIG.DEEPL_API_KEY) {
      const response = await axios.post('https://api-free.deepl.com/v2/translate', null, {
        params: { text, target_lang: targetLang.toUpperCase(), auth_key: CONFIG.DEEPL_API_KEY },
        timeout: 10000,
      });
      return `**Traducción (${targetLang.toUpperCase()}):**\n\n${response.data.translations[0].text}`;
    }

    // Fallback: use AI for translation
    return await callAI(`Traduce el siguiente texto al ${targetLang}. Solo muestra la traducción, sin explicaciones:\n\n"${text}"`, 'translate-service');
  } catch (error) {
    return '❌ Error al traducir. Verifica el código de idioma (en, es, fr, de, pt, ja, ko, zh).';
  }
}

// --- Web Search Service ---
async function webSearch(query) {
  serviceStats.apiCalls.search++;
  try {
    // Try Google Custom Search
    if (CONFIG.GOOGLE_SEARCH_KEY && CONFIG.GOOGLE_SEARCH_CX) {
      const response = await axios.get('https://www.googleapis.com/customsearch/v1', {
        params: { key: CONFIG.GOOGLE_SEARCH_KEY, cx: CONFIG.GOOGLE_SEARCH_CX, q: query, num: 5 },
        timeout: 10000,
      });
      const items = response.data.items || [];
      if (items.length === 0) return '❌ No se encontraron resultados.';
      let result = `**Resultados de búsqueda: "${query}"**\n\n';
      for (const item of items) {
        result += `- **[${item.title}](${item.link})**\n  ${item.snippet}\n\n`;
      }
      return result;
    }

    // Try Brave Search
    if (CONFIG.BRAVE_SEARCH_KEY) {
      const response = await axios.get('https://api.search.brave.com/res/v1/web/search', {
        params: { q: query, count: 5 },
        headers: { 'X-Subscription-Token': CONFIG.BRAVE_SEARCH_KEY },
        timeout: 10000,
      });
      const results = response.data.web?.results || [];
      if (results.length === 0) return '❌ No se encontraron resultados.';
      let result = `**Resultados de búsqueda: "${query}"**\n\n';
      for (const r of results) {
        result += `- **[${r.title}](${r.url})**\n  ${r.description}\n\n`;
      }
      return result;
    }

    // Fallback: AI-powered search simulation
    return await callAI(`Busca información actualizada sobre: ${query}\n\nProporciona un resumen completo con las fuentes más relevantes y datos actualizados.`, 'search-service');
  } catch (error) {
    return '❌ Error en la búsqueda web.';
  }
}

// --- Code Execution / Analysis Service ---
async function analyzeCode(code, language = 'auto') {
  serviceStats.apiCalls.code++;
  return await callAI(
    `Analiza el siguiente código${language !== 'auto' ? ` (${language})` : ''}. Proporciona:\n` +
    `1. Explicación de qué hace\n2. Posibles bugs o mejoras\n3. Mejores prácticas\n4. Versión optimizada si aplica\n\n` +
    `\`\`\`${language !== 'auto' ? language : ''}\n${code}\n\`\`\``,
    'code-service'
  );
}

// --- Calculator / Math Service ---
async function calculate(expression) {
  try {
    // Safe math evaluation
    const sanitized = expression.replace(/[^0-9+\-*/().^%sqrt sin cos tan log pi e ]/gi, '');
    if (!sanitized) return '❌ Expresión no válida.';

    // Try Wolfram Alpha
    if (CONFIG.WOLFRAM_APP_ID) {
      const response = await axios.get('https://api.wolframalpha.com/v2/query', {
        params: { input: expression, appid: CONFIG.WOLFRAM_APP_ID, output: 'JSON', format: 'plaintext' },
        timeout: 10000,
      });
      const pods = response.data.queryresult?.pods || [];
      const resultPod = pods.find(p => p.primary) || pods[1];
      if (resultPod?.subpods?.[0]?.plaintext) {
        return `**Cálculo:** ${expression}\n\n**Resultado:** ${resultPod.subpods[0].plaintext}`;
      }
    }

    // Fallback: use AI
    return await callAI(`Resuelve paso a paso: ${expression}. Muestra el proceso y el resultado final.`, 'calc-service');
  } catch (error) {
    return await callAI(`Resuelve paso a paso: ${expression}. Muestra el proceso y el resultado final.`, 'calc-service');
  }
}

// --- NASA Service ---
async function getNasaApod() {
  serviceStats.apiCalls.weather++; // Reusing counter
  try {
    const response = await axios.get('https://api.nasa.gov/planetary/apod', {
      params: { api_key: CONFIG.NASA_API_KEY || 'DEMO_KEY' },
      timeout: 10000,
    });
    const d = response.data;
    return `**${d.title}** (${d.date})\n\n${d.explanation}\n\n![NASA APOD](${d.url})`;
  } catch (error) {
    return '❌ Error al obtener imagen de NASA.';
  }
}

// --- Movies (TMDB) ---
async function searchMovie(query) {
  try {
    if (!CONFIG.TMDB_API_KEY) {
      return await callAI(`Dame información sobre la película o serie: "${query}". Incluye sinopsis, año, calificación y datos interesantes.`, 'movies-service');
    }
    const response = await axios.get('https://api.themoviedb.org/3/search/multi', {
      params: { api_key: CONFIG.TMDB_API_KEY, query, language: 'es-ES' },
      timeout: 10000,
    });
    const results = response.data.results?.slice(0, 3) || [];
    if (results.length === 0) return '❌ No se encontraron resultados.';
    let result = `**Resultados para "${query}"**\n\n';
    for (const r of results) {
      const title = r.title || r.name || 'N/A';
      const year = (r.release_date || r.first_air_date || 'N/A').substring(0, 4);
      const rating = r.vote_average ? `${r.vote_average.toFixed(1)}/10` : 'N/A';
      result += `**${title}** (${year}) - ${r.media_type === 'tv' ? 'Serie' : 'Película'}\n` +
        `Calificación: ${rating}\n${r.overview ? r.overview.substring(0, 200) + '...' : ''}\n\n`;
    }
    return result;
  } catch (error) {
    return '❌ Error al buscar películas.';
  }
}

// --- Flights Service ---
async function searchFlights(origin, destination, date) {
  try {
    if (CONFIG.AVIATIONSTACK_KEY) {
      const response = await axios.get('http://api.aviationstack.com/v1/flights', {
        params: { access_key: CONFIG.AVIATIONSTACK_KEY, dep_iata: origin, arr_iata: destination, flight_date: date },
        timeout: 10000,
      });
      const flights = response.data.data?.slice(0, 5) || [];
      if (flights.length === 0) return '❌ No se encontraron vuelos.';
      let result = `**Vuelos ${origin} → ${destination}** (${date})\n\n`;
      for (const f of flights) {
        result += `- **${f.airline.name} ${f.flight.iata}**\n  Salida: ${f.departure.terminal || ''} ${f.departure.scheduled || 'N/A'}\n  Llegada: ${f.arrival.scheduled || 'N/A'}\n  Estado: ${f.flight_status}\n\n`;
      }
      return result;
    }
    return await callAI(`Busca información sobre vuelos de ${origin} a ${destination} para el ${date}. Proporciona aerolíneas típicas y precios estimados.`, 'flights-service');
  } catch (error) {
    return '❌ Error al buscar vuelos.';
  }
}

// --- Image Generation Service ---
async function generateImage(prompt) {
  try {
    if (CONFIG.STABILITY_API_KEY) {
      const response = await axios.post(
        'https://api.stability.ai/v1/generation/stable-diffusion-xl-1024-v1-0/text-to-image',
        {
          text_prompts: [{ text: prompt, weight: 1 }],
          cfg_scale: 7,
          height: 1024,
          width: 1024,
          steps: 30,
          samples: 1,
        },
        {
          headers: {
            'Authorization': `Bearer ${CONFIG.STABILITY_API_KEY}`,
            'Content-Type': 'application/json',
          },
          timeout: 60000,
        }
      );
      if (response.data.artifacts?.[0]?.base64) {
        // We have the image data, but can't easily send it via Lark card
        // Save and upload would be needed; for now, describe it
        return '✅ Imagen generada con éxito. (Se requiere implementar subida de imagen a Lark para mostrarla).';
      }
    }
    // Fallback: AI description
    return await callAI(`Describe en detalle cómo sería una imagen de: ${prompt}. Sé muy visual y descriptivo.`, 'image-service');
  } catch (error) {
    return await callAI(`Describe en detalle cómo sería una imagen de: ${prompt}. Sé muy visual y descriptivo.`, 'image-service');
  }
}

// --- Joke Service ---
async function getJoke() {
  try {
    const response = await axios.get('https://v2.jokeapi.dev/joke/Any?lang=es&type=single', { timeout: 5000 });
    return `😂 **Chiste:**\n\n${response.data.joke}`;
  } catch (error) {
    return '😂 ¿Por qué los programadores prefieren el modo oscuro? Porque la luz atrae bugs.';
  }
}

// --- QR Code Service ---
async function generateQR(text) {
  try {
    return `**Código QR generado para:** ${text}\n\n![QR](https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=${encodeURIComponent(text)})`;
  } catch (error) {
    return '❌ Error al generar código QR.';
  }
}

// --- Random Facts Service ---
async function getRandomFact() {
  try {
    const response = await axios.get('https://uselessfacts.jsph.pl/api/v2/facts/random?language=es', { timeout: 5000 });
    return `🧠 **Dato curioso:** ${response.data.text}`;
  } catch (error) {
    return '🧠 Los pulpos tienen tres corazones y sangre azul.';
  }
}

// --- Time Zone Service ---
function getTimeInCity(city) {
  const cityTimezones = {
    'mexico': 'America/Mexico_City', 'cdmx': 'America/Mexico_City', 'bogota': 'America/Bogota',
    'lima': 'America/Lima', 'buenos aires': 'America/Argentina/Buenos_Aires', 'santiago': 'America/Santiago',
    'madrid': 'Europe/Madrid', 'barcelona': 'Europe/Madrid', 'london': 'Europe/London',
    'paris': 'Europe/Paris', 'berlin': 'Europe/Berlin', 'rome': 'Europe/Rome',
    'tokyo': 'Asia/Tokyo', 'beijing': 'Asia/Shanghai', 'shanghai': 'Asia/Shanghai',
    'seoul': 'Asia/Seoul', 'sydney': 'Australia/Sydney', 'dubai': 'Asia/Dubai',
    'new york': 'America/New_York', 'los angeles': 'America/Los_Angeles', 'chicago': 'America/Chicago',
    'miami': 'America/New_York', 'san francisco': 'America/Los_Angeles',
    'mumbai': 'Asia/Kolkata', 'delhi': 'Asia/Kolkata', 'singapore': 'Asia/Singapore',
    'moscow': 'Europe/Moscow', 'istanbul': 'Europe/Istanbul', 'cairo': 'Africa/Cairo',
  };
  const tz = cityTimezones[city.toLowerCase()] || null;
  if (!tz) {
    return `❌ Ciudad no reconocida. Ciudades disponibles: ${Object.keys(cityTimezones).join(', ')}`;
  }
  const now = new Date().toLocaleString('es-ES', { timeZone: tz, weekday: 'long', year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit' });
  return `🕐 **Hora en ${city}:**\n\n${now}`;
}

// ============ LARK MESSAGE SENDING ============
async function sendCardMessage(chatId, title, content, template = 'blue') {
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
            template: template,
          },
          elements: [{ tag: 'markdown', content }],
        }),
      },
    });
  } catch (error) {
    log('error', 'LARK', 'Card send failed', { chatId, error: error.message });
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
    log('error', 'LARK', 'Text send failed', { chatId, error: error.message });
  }
}

async function sendMultiCardMessage(chatId, cards) {
  for (const card of cards) {
    await sendCardMessage(chatId, card.title, card.content, card.template || 'blue');
  }
}

// ============ COMMAND ROUTER ============
const COMMANDS = {
  '/help': { desc: 'Muestra la ayuda', alias: ['/ayuda'] },
  '/chat': { desc: 'Chatea con la IA', alias: [] },
  '/search': { desc: 'Busca en la web', alias: ['/buscar'] },
  '/weather': { desc: 'Clima actual', alias: ['/clima'] },
  '/forecast': { desc: 'Pronóstico 5 días', alias: ['/pronostico'] },
  '/stock': { desc: 'Cotización de acciones', alias: ['/accion'] },
  '/crypto': { desc: 'Precio criptomonedas', alias: ['/cripto'] },
  '/exchange': { desc: 'Conversión de monedas', alias: ['/moneda'] },
  '/news': { desc: 'Noticias', alias: ['/noticias'] },
  '/translate': { desc: 'Traductor', alias: ['/traducir'] },
  '/code': { desc: 'Analiza código', alias: ['/codigo'] },
  '/calc': { desc: 'Calculadora', alias: ['/calcular'] },
  '/image': { desc: 'Genera imágenes', alias: ['/imagen'] },
  '/movie': { desc: 'Info de películas', alias: ['/pelicula'] },
  '/flight': { desc: 'Busca vuelos', alias: ['/vuelo'] },
  '/nasa': { desc: 'Imagen del día NASA', alias: [] },
  '/joke': { desc: 'Chiste aleatorio', alias: ['/chiste'] },
  '/qr': { desc: 'Genera código QR', alias: [] },
  '/fact': { desc: 'Dato curioso', alias: ['/dato'] },
  '/time': { desc: 'Hora en ciudades', alias: ['/hora'] },
  '/document': { desc: 'Crea documentos', alias: ['/documento'] },
  '/new': { desc: 'Nueva conversación', alias: ['/reset', '/nuevo'] },
  '/status': { desc: 'Estado del sistema', alias: ['/estado'] },
  '/admin': { desc: 'Panel admin', alias: [] },
};

async function routeCommand(text, chatId, userId, msgId) {
  const parts = text.split(/\s+/);
  const cmd = parts[0].toLowerCase();
  const args = text.substring(parts[0].length).trim();

  // Track command usage
  serviceStats.commandUsage[cmd] = (serviceStats.commandUsage[cmd] || 0) + 1;
  serviceStats.totalMessages++;

  // Send typing reaction
  try {
    await client.im.message.patch({
      path: { message_id: msgId },
      data: { reactions: [{ reaction_type: 'ThumbUp' }] },
    });
  } catch (e) { /* optional */ }

  // ---- HELP ----
  if (cmd === '/help' || cmd === '/ayuda') {
    await sendCardMessage(chatId, 'NEXA PRO AI - Ayuda',
      '**🤖 NEXA PRO AI Assistant v3.0**\n\n' +
      '**Comandos de IA:**\n' +
      '💬 `/chat [pregunta]` — Chatea con la IA (Dify/OpenAI/Gemini)\n' +
      '🔍 `/search [tema]` — Búsqueda web\n' +
      '🖼️ `/image [desc]` — Genera imágenes\n' +
      '📄 `/document [tema]` — Crea documentos\n\n' +
      '**Clima y Viajes:**\n' +
      '🌤️ `/weather [ciudad]` — Clima actual\n' +
      '📅 `/forecast [ciudad]` — Pronóstico 5 días\n' +
      '✈️ `/flight [origen] [destino] [fecha]` — Busca vuelos\n' +
      '🕐 `/time [ciudad]` — Hora mundial\n\n' +
      '**Finanzas:**\n' +
      '📈 `/stock [simbolo]` — Cotizaciones (AAPL, TSLA)\n' +
      '🪙 `/crypto [moneda]` — Criptomonedas (bitcoin, ethereum)\n' +
      '💱 `/exchange [cantidad] [de] [a]` — Conversión de monedas\n\n' +
      '**Información:**\n' +
      '📰 `/news [tema]` — Noticias\n' +
      '🎬 `/movie [nombre]` — Info de películas/series\n' +
      '🚀 `/nasa` — Imagen astronómica del día\n\n' +
      '**Herramientas:**\n' +
      '🌍 `/translate [texto] [idioma]` — Traductor\n' +
      '💻 `/code [código]` — Análisis de código\n' +
      '🧮 `/calc [expresión]` — Calculadora\n' +
      '📱 `/qr [texto]` — Código QR\n\n' +
      '**Entretenimiento:**\n' +
      '😂 `/joke` — Chiste aleatorio\n' +
      '🧠 `/fact` — Dato curioso\n\n' +
      '**Sistema:**\n' +
      '🔄 `/new` — Nueva conversación\n' +
      '📊 `/status` — Estado del sistema\n' +
      '❓ `/help` — Esta ayuda\n\n' +
      '💡 **Tip:** Escribe directamente sin comando y te responderé con IA.\n' +
      '⚡ **Límites:** 30 mensajes/minuto por usuario.\n' +
      '🔄 **Memoria:** 24 horas por conversación.'
    );
    return;
  }

  // ---- CHAT (default AI) ----
  if (cmd === '/chat' || (!cmd.startsWith('/') && text.length > 0)) {
    const query = cmd === '/chat' ? args : text;
    if (!query) {
      await sendCardMessage(chatId, 'Chat con IA', 'Envía un mensaje después del comando.\n**Ejemplo:** `/chat ¿Cuál es la capital de Francia?`');
      return;
    }
    const response = await callAI(query, userId);
    await sendCardMessage(chatId, '🤖 NEXA PRO AI', truncate(response));
    return;
  }

  // ---- SEARCH ----
  if (cmd === '/search' || cmd === '/buscar') {
    if (!args) { await sendCardMessage(chatId, 'Búsqueda', 'Especifica qué buscar. **Ejemplo:** `/search inteligencia artificial`'); return; }
    const results = await webSearch(args);
    await sendCardMessage(chatId, '🔍 Resultados de Búsqueda', truncate(results), 'green');
    return;
  }

  // ---- WEATHER ----
  if (cmd === '/weather' || cmd === '/clima') {
    if (!args) { await sendCardMessage(chatId, 'Clima', 'Especifica una ciudad. **Ejemplo:** `/weather Mexico City`'); return; }
    const weather = await getWeather(args);
    await sendCardMessage(chatId, '🌤️ Clima Actual', weather, 'turquoise');
    return;
  }

  // ---- FORECAST ----
  if (cmd === '/forecast' || cmd === '/pronostico') {
    if (!args) { await sendCardMessage(chatId, 'Pronóstico', 'Especifica una ciudad. **Ejemplo:** `/forecast Bogota`'); return; }
    const forecast = await getWeatherForecast(args);
    await sendCardMessage(chatId, '📅 Pronóstico 5 Días', forecast, 'turquoise');
    return;
  }

  // ---- STOCK ----
  if (cmd === '/stock' || cmd === '/accion') {
    if (!args) { await sendCardMessage(chatId, 'Acciones', 'Especifica un símbolo. **Ejemplo:** `/stock AAPL`'); return; }
    const quote = await getStockQuote(args.toUpperCase());
    await sendCardMessage(chatId, '📈 Cotización', quote, 'orange');
    return;
  }

  // ---- CRYPTO ----
  if (cmd === '/crypto' || cmd === '/cripto') {
    if (!args) { await sendCardMessage(chatId, 'Cripto', 'Especifica una moneda. **Ejemplo:** `/crypto bitcoin`'); return; }
    const price = await getCryptoPrice(args);
    await sendCardMessage(chatId, '🪙 Criptomoneda', price, 'orange');
    return;
  }

  // ---- EXCHANGE ----
  if (cmd === '/exchange' || cmd === '/moneda') {
    const parts2 = args.split(/\s+/);
    if (parts2.length < 2) {
      await sendCardMessage(chatId, 'Moneda', 'Especifica las monedas. **Ejemplo:** `/exchange 100 USD MXN` o `/exchange USD EUR`');
      return;
    }
    let amount = 1, from, to;
    if (parts2.length === 3 && !isNaN(parts2[0])) {
      amount = parseFloat(parts2[0]); from = parts2[1]; to = parts2[2];
    } else {
      from = parts2[0]; to = parts2[1];
    }
    const rate = await getExchangeRate(from, to, amount);
    await sendCardMessage(chatId, '💱 Conversión de Moneda', rate, 'orange');
    return;
  }

  // ---- NEWS ----
  if (cmd === '/news' || cmd === '/noticias') {
    const news = await getNews(args || 'technology');
    await sendCardMessage(chatId, '📰 Noticias', truncate(news), 'violet');
    return;
  }

  // ---- TRANSLATE ----
  if (cmd === '/translate' || cmd === '/traducir') {
    const parts2 = args.split(/\s+/);
    if (parts2.length < 2) {
      await sendCardMessage(chatId, 'Traductor', 'Especifica texto e idioma. **Ejemplo:** `/translate Hello world es` (es=en español, en=inglés, fr=francés)');
      return;
    }
    const targetLang = parts2[parts2.length - 1];
    const textToTranslate = parts2.slice(0, -1).join(' ');
    const translation = await translateText(textToTranslate, targetLang);
    await sendCardMessage(chatId, '🌍 Traducción', translation, 'indigo');
    return;
  }

  // ---- CODE ----
  if (cmd === '/code' || cmd === '/codigo') {
    if (!args) { await sendCardMessage(chatId, 'Código', 'Envía código para analizar. **Ejemplo:** `/code function hello() { return "world"; }`'); return; }
    const analysis = await analyzeCode(args);
    await sendCardMessage(chatId, '💻 Análisis de Código', truncate(analysis), 'purple');
    return;
  }

  // ---- CALC ----
  if (cmd === '/calc' || cmd === '/calcular') {
    if (!args) { await sendCardMessage(chatId, 'Calculadora', 'Especifica una expresión. **Ejemplo:** `/calc 2^10 + sqrt(144)`'); return; }
    const result = await calculate(args);
    await sendCardMessage(chatId, '🧮 Cálculo', result, 'wathet');
    return;
  }

  // ---- IMAGE ----
  if (cmd === '/image' || cmd === '/imagen') {
    if (!args) { await sendCardMessage(chatId, 'Imagen', 'Describe la imagen. **Ejemplo:** `/image un atardecer en la playa`'); return; }
    const imageResult = await generateImage(args);
    await sendCardMessage(chatId, '🖼️ Imagen', truncate(imageResult), 'green');
    return;
  }

  // ---- MOVIE ----
  if (cmd === '/movie' || cmd === '/pelicula') {
    if (!args) { await sendCardMessage(chatId, 'Película', 'Especifica un nombre. **Ejemplo:** `/movie Inception`'); return; }
    const movieInfo = await searchMovie(args);
    await sendCardMessage(chatId, '🎬 Película/Serie', truncate(movieInfo), 'red');
    return;
  }

  // ---- FLIGHT ----
  if (cmd === '/flight' || cmd === '/vuelo') {
    const parts2 = args.split(/\s+/);
    if (parts2.length < 3) {
      await sendCardMessage(chatId, 'Vuelos', 'Especifica origen, destino y fecha. **Ejemplo:** `/flight BOG MEX 2025-01-15`');
      return;
    }
    const flights = await searchFlights(parts2[0], parts2[1], parts2[2]);
    await sendCardMessage(chatId, '✈️ Vuelos', truncate(flights), 'blue');
    return;
  }

  // ---- NASA ----
  if (cmd === '/nasa') {
    const apod = await getNasaApod();
    await sendCardMessage(chatId, '🚀 NASA - Imagen del Día', truncate(apod), 'indigo');
    return;
  }

  // ---- JOKE ----
  if (cmd === '/joke' || cmd === '/chiste') {
    const joke = await getJoke();
    await sendCardMessage(chatId, '😂 Chiste', joke, 'yellow');
    return;
  }

  // ---- QR ----
  if (cmd === '/qr') {
    if (!args) { await sendCardMessage(chatId, 'QR', 'Especifica el texto/URL. **Ejemplo:** `/qr https://example.com`'); return; }
    const qr = await generateQR(args);
    await sendCardMessage(chatId, '📱 Código QR', qr, 'green');
    return;
  }

  // ---- FACT ----
  if (cmd === '/fact' || cmd === '/dato') {
    const fact = await getRandomFact();
    await sendCardMessage(chatId, '🧠 Dato Curioso', fact, 'violet');
    return;
  }

  // ---- TIME ----
  if (cmd === '/time' || cmd === '/hora') {
    if (!args) { await sendCardMessage(chatId, 'Hora', 'Especifica una ciudad. **Ejemplo:** `/time Tokyo`'); return; }
    const time = getTimeInCity(args);
    await sendCardMessage(chatId, '🕐 Hora Mundial', time, 'wathet');
    return;
  }

  // ---- DOCUMENT ----
  if (cmd === '/document' || cmd === '/documento') {
    if (!args) { await sendCardMessage(chatId, 'Documento', 'Especifica el tema. **Ejemplo:** `/document Plan de Marketing Digital`'); return; }
    const doc = await callAI(
      `Crea un documento profesional y bien estructurado sobre: ${args}\n\nIncluye: título, tabla de contenidos, introducción, secciones principales con subsecciones, conclusiones y recomendaciones. Usa formato Markdown.`,
      userId
    );
    await sendCardMessage(chatId, '📄 Documento', truncate(doc), 'blue');
    return;
  }

  // ---- NEW CONVERSATION ----
  if (cmd === '/new' || cmd === '/reset' || cmd === '/nuevo') {
    conversationStore.delete(userId);
    await sendCardMessage(chatId, '🔄 Nueva Conversación', 'Se ha iniciado una nueva conversación. La memoria anterior ha sido borrada.');
    return;
  }

  // ---- STATUS ----
  if (cmd === '/status' || cmd === '/estado') {
    const uptime = Math.floor(process.uptime());
    const hours = Math.floor(uptime / 3600);
    const mins = Math.floor((uptime % 3600) / 60);
    const memUsage = process.memoryUsage();
    const status = `**🤖 NEXA PRO AI v3.0**\n\n` +
      `- **Uptime:** ${hours}h ${mins}m\n` +
      `- **Mensajes totales:** ${serviceStats.totalMessages}\n` +
      `- **Errores:** ${serviceStats.errors}\n` +
      `- **Conversaciones activas:** ${conversationStore.size}\n` +
      `- **Memoria:** ${(memUsage.heapUsed / 1024 / 1024).toFixed(1)} MB / ${(memUsage.heapTotal / 1024 / 1024).toFixed(1)} MB\n\n` +
      `**APIs Configuradas:**\n` +
      `- Dify: ${CONFIG.DIFY_API_KEY ? '✅' : '❌'} (${serviceStats.apiCalls.dify} calls)\n` +
      `- OpenAI: ${CONFIG.OPENAI_API_KEY ? '✅' : '❌'} (${serviceStats.apiCalls.openai} calls)\n` +
      `- Gemini: ${CONFIG.GEMINI_API_KEY ? '✅' : '❌'} (${serviceStats.apiCalls.gemini} calls)\n` +
      `- Weather: ${CONFIG.WEATHER_API_KEY ? '✅' : '⚠️ (fallback)' }\n` +
      `- Finance: ${CONFIG.ALPHA_VANTAGE_KEY ? '✅' : '❌'}\n` +
      `- News: ${CONFIG.NEWS_API_KEY ? '✅' : '❌'}\n` +
      `- Translate: ${CONFIG.DEEPL_API_KEY ? '✅' : '❌'}\n` +
      `- Search: ${CONFIG.GOOGLE_SEARCH_KEY || CONFIG.BRAVE_SEARCH_KEY ? '✅' : '❌'}\n` +
      `- Movies: ${CONFIG.TMDB_API_KEY ? '✅' : '❌'}\n` +
      `- NASA: ${CONFIG.NASA_API_KEY ? '✅' : '⚠️ (demo)'}\n` +
      `- Wolfram: ${CONFIG.WOLFRAM_APP_ID ? '✅' : '❌'}\n` +
      `- Flights: ${CONFIG.AVIATIONSTACK_KEY ? '✅' : '❌'}`;
    await sendCardMessage(chatId, '📊 Estado del Sistema', status, 'blue');
    return;
  }

  // ---- ADMIN ----
  if (cmd === '/admin') {
    if (args !== CONFIG.ADMIN_SECRET) {
      await sendTextMessage(chatId, '❌ Acceso denegado. Uso: /admin [secreto]');
      return;
    }
    const topCommands = Object.entries(serviceStats.commandUsage)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 10)
      .map(([cmd, count]) => `- ${cmd}: ${count}`)
      .join('\n');
    const recentErrors = adminLogs.slice(-5).map(l => `[${l.timestamp}] ${l.category}: ${l.message}`).join('\n');
    await sendCardMessage(chatId, '🔧 Admin Panel',
      `**Panel de Administración**\n\n` +
      `**Top Comandos:**\n${topCommands || 'Sin datos'}\n\n` +
      `**API Calls:**\n` +
      `- Dify: ${serviceStats.apiCalls.dify}\n` +
      `- OpenAI: ${serviceStats.apiCalls.openai}\n` +
      `- Gemini: ${serviceStats.apiCalls.gemini}\n` +
      `- Weather: ${serviceStats.apiCalls.weather}\n` +
      `- Finance: ${serviceStats.apiCalls.finance}\n` +
      `- News: ${serviceStats.apiCalls.news}\n` +
      `- Translate: ${serviceStats.apiCalls.translate}\n` +
      `- Search: ${serviceStats.apiCalls.search}\n\n` +
      `**Errores Recientes:**\n${recentErrors || 'Sin errores'}`,
      'red'
    );
    return;
  }

  // ---- UNKNOWN COMMAND - Try AI ----
  const response = await callAI(text, userId);
  await sendCardMessage(chatId, '🤖 NEXA PRO AI', truncate(response));
}

// ============ EVENT HANDLER ============
async function handleImMessageReceive(data) {
  const msgId = data.message.message_id;
  const chatId = data.message.chat_id;
  const msgType = data.message.message_type;
  const senderType = data.sender.sender_type;

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

  // Remove @bot mention
  text = text.replace(/@_user_\d+\s*/g, '').trim();

  // Rate limit check
  if (!checkRateLimit(userId)) {
    await sendTextMessage(chatId, '⏳ Has enviado muchos mensajes. Espera un momento antes de enviar otro.');
    return;
  }

  log('info', 'MSG', `User ${userId}: ${text.substring(0, 80)}`);

  try {
    await routeCommand(text, chatId, userId, msgId);
  } catch (error) {
    log('error', 'CMD', 'Command execution failed', { text: text.substring(0, 100), error: error.message });
    await sendTextMessage(chatId, '❌ Ocurrió un error al procesar tu mensaje. Intenta de nuevo.');
  }
}

// ============ WEBSOCKET EVENT REGISTRATION ============
const eventDispatcher = new lark.EventDispatcher({}).register({
  'im.message.receive_v1': handleImMessageReceive,
});

// ============ EXPRESS HTTP SERVER ============
const app = express();

// Middleware
app.use(cors());
app.use(helmet({ contentSecurityPolicy: false }));
app.use(compression());
app.use(express.json({ limit: '10mb' }));

// Global rate limiter for HTTP
const httpLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 100,
  message: { error: 'Too many requests' },
});
app.use(httpLimiter);

// ---- HTTP ENDPOINTS ----

// Root
app.get('/', (req, res) => {
  res.json({
    status: 'online',
    service: 'NEXA PRO AI Assistant',
    version: '3.0.0',
    mode: CONFIG.LARK_APP_ID ? 'websocket+http' : 'http',
    providers: {
      dify: !!CONFIG.DIFY_API_KEY,
      openai: !!CONFIG.OPENAI_API_KEY,
      gemini: !!CONFIG.GEMINI_API_KEY,
    },
    services: {
      weather: !!CONFIG.WEATHER_API_KEY,
      finance: !!CONFIG.ALPHA_VANTAGE_KEY,
      news: !!CONFIG.NEWS_API_KEY,
      translate: !!CONFIG.DEEPL_API_KEY,
      search: !!(CONFIG.GOOGLE_SEARCH_KEY || CONFIG.BRAVE_SEARCH_KEY),
      movies: !!CONFIG.TMDB_API_KEY,
      nasa: !!CONFIG.NASA_API_KEY,
      wolfram: !!CONFIG.WOLFRAM_APP_ID,
      flights: !!CONFIG.AVIATIONSTACK_KEY,
    },
    uptime: Math.floor(process.uptime()),
  });
});

// Health check
app.get('/health', (req, res) => {
  const memUsage = process.memoryUsage();
  res.json({
    status: 'healthy',
    uptime: process.uptime(),
    memory: {
      heapUsed: `${(memUsage.heapUsed / 1024 / 1024).toFixed(1)} MB`,
      heapTotal: `${(memUsage.heapTotal / 1024 / 1024).toFixed(1)} MB`,
    },
    conversations: conversationStore.size,
    totalMessages: serviceStats.totalMessages,
    errors: serviceStats.errors,
  });
});

// Lark Webhook (HTTP fallback)
app.post('/webhook/lark', async (req, res) => {
  try {
    const body = req.body;

    // URL verification challenge
    if (body.type === 'url_verification') {
      log('info', 'WEBHOOK', 'URL verification challenge received');
      return res.json({ challenge: body.challenge });
    }

    // Token verification
    if (CONFIG.LARK_VERIFICATION_TOKEN && body.token !== CONFIG.LARK_VERIFICATION_TOKEN) {
      log('warn', 'WEBHOOK', 'Invalid verification token');
      return res.status(403).json({ error: 'Invalid token' });
    }

    // Decrypt event if encryption is configured
    if (CONFIG.LARK_ENCRYPT_KEY && body.encrypt) {
      try {
        const key = Buffer.from(CONFIG.LARK_ENCRYPT_KEY, 'utf8');
        const iv = key.subarray(0, 16);
        const decipher = crypto.createDecipheriv('aes-256-cbc', key, iv);
        let decrypted = decipher.update(body.encrypt, 'base64', 'utf8');
        decrypted += decipher.final('utf8');
        const decryptedBody = JSON.parse(decrypted);
        body.event = decryptedBody.event;
        body.header = decryptedBody.header;
      } catch (decryptError) {
        log('error', 'WEBHOOK', 'Decryption failed', { error: decryptError.message });
      }
    }

    const event = body.event;
    if (event && body.header?.event_type === 'im.message.receive_v1') {
      setImmediate(async () => {
        try {
          await handleImMessageReceive(event);
        } catch (e) {
          log('error', 'WEBHOOK', 'Process error', { error: e.message });
        }
      });
    }

    res.json({ code: 0 });
  } catch (e) {
    log('error', 'WEBHOOK', 'Request error', { error: e.message });
    res.status(500).json({ error: 'Internal error' });
  }
});

// ---- API ENDPOINTS (for external access) ----

// AI Chat API
app.post('/api/chat', async (req, res) => {
  try {
    const { query, userId = 'api_user', provider } = req.body;
    if (!query) return res.status(400).json({ error: 'Query required' });

    let response;
    if (provider === 'dify') response = await callDifyAI(query, userId);
    else if (provider === 'openai') response = await callOpenAI(query, userId);
    else if (provider === 'gemini') response = await callGemini(query, userId);
    else response = await callAI(query, userId);

    res.json({ response, provider: provider || 'auto' });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// Weather API
app.get('/api/weather/:city', async (req, res) => {
  try {
    const weather = await getWeather(req.params.city);
    res.json({ city: req.params.city, weather });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// Stock API
app.get('/api/stock/:symbol', async (req, res) => {
  try {
    const quote = await getStockQuote(req.params.symbol);
    res.json({ symbol: req.params.symbol, quote });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// Crypto API
app.get('/api/crypto/:symbol', async (req, res) => {
  try {
    const price = await getCryptoPrice(req.params.symbol);
    res.json({ symbol: req.params.symbol, price });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// Exchange API
app.get('/api/exchange/:from/:to/:amount?', async (req, res) => {
  try {
    const rate = await getExchangeRate(req.params.from, req.params.to, parseFloat(req.params.amount) || 1);
    res.json({ from: req.params.from, to: req.params.to, amount: req.params.amount || 1, rate });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// News API
app.get('/api/news/:query?', async (req, res) => {
  try {
    const news = await getNews(req.params.query || 'technology');
    res.json({ query: req.params.query || 'technology', news });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// Translate API
app.post('/api/translate', async (req, res) => {
  try {
    const { text, targetLang = 'en' } = req.body;
    if (!text) return res.status(400).json({ error: 'Text required' });
    const translation = await translateText(text, targetLang);
    res.json({ translation, targetLang });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// Search API
app.get('/api/search/:query', async (req, res) => {
  try {
    const results = await webSearch(req.params.query);
    res.json({ query: req.params.query, results });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// NASA APOD API
app.get('/api/nasa', async (req, res) => {
  try {
    const apod = await getNasaApod();
    res.json({ apod });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// Movies API
app.get('/api/movie/:query', async (req, res) => {
  try {
    const movies = await searchMovie(req.params.query);
    res.json({ query: req.params.query, results: movies });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// Admin Stats API
app.get('/api/admin/stats', (req, res) => {
  const auth = req.headers.authorization;
  if (auth !== `Bearer ${CONFIG.ADMIN_SECRET}`) {
    return res.status(401).json({ error: 'Unauthorized' });
  }
  res.json({
    uptime: process.uptime(),
    memory: process.memoryUsage(),
    stats: serviceStats,
    activeConversations: conversationStore.size,
    recentErrors: adminLogs.slice(-20),
  });
});

// ============ START SERVER ============
app.listen(CONFIG.PORT, '0.0.0.0', () => {
  log('info', 'SERVER', `HTTP server listening on port ${CONFIG.PORT}`);
});

// Start WebSocket connection to Lark (if credentials are configured)
if (CONFIG.LARK_APP_ID && CONFIG.LARK_APP_SECRET) {
  const wsClient = new lark.WSClient({
    appId: CONFIG.LARK_APP_ID,
    appSecret: CONFIG.LARK_APP_SECRET,
    domain: lark.Domain.Lark,
  });

  wsClient.start({ eventDispatcher }).then(() => {
    console.log('');
    console.log('╔══════════════════════════════════════════════════╗');
    console.log('║   🤖 NEXA PRO AI Assistant v3.0                  ║');
    console.log('║   Mode: WebSocket + HTTP                         ║');
    console.log('║   AI: Dify | OpenAI | Gemini                     ║');
    console.log('║   Services: Weather, Finance, News, Translate    ║');
    console.log('║            Search, Code, Calc, Movies, NASA      ║');
    console.log('║            Flights, QR, Jokes, Facts, Time       ║');
    console.log('╚══════════════════════════════════════════════════╝');
    console.log('');
    log('info', 'WS', 'Connected to Lark via WebSocket');
  }).catch((err) => {
    log('error', 'WS', 'WebSocket connection failed', { error: err.message });
    console.log('');
    console.log('⚠️  WebSocket failed. Running in HTTP-only mode.');
    console.log('   Configure LARK_APP_ID and LARK_APP_SECRET to enable WebSocket.');
  });
} else {
  console.log('');
  console.log('╔══════════════════════════════════════════════════╗');
  console.log('║   🤖 NEXA PRO AI Assistant v3.0                  ║');
  console.log('║   Mode: HTTP Only (API Server)                   ║');
  console.log('║   Set LARK_APP_ID/SECRET to enable WebSocket     ║');
  console.log('╚══════════════════════════════════════════════════╝');
  console.log('');
}

module.exports = { client, app };
