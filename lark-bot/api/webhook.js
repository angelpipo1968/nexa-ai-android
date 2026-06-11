/**
 * NEXA PRO AI v4.0 — Vercel Serverless Function
 * Handles Lark webhook events and API requests
 */
const bot = require('../lib/bot');

module.exports = async function handler(req, res) {
  // CORS
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    return res.status(200).end();
  }

  const { method } = req;
  const path = req.url || '/';

  // ---- Lark Webhook ----
  if (method === 'POST' && (path === '/webhook/lark' || path === '/lark')) {
    const body = req.body;
    // URL verification
    if (body && body.type === 'url_verification') {
      return res.status(200).json({ challenge: body.challenge });
    }
    // Token check
    if (bot.CONFIG.LARK_VERIFICATION_TOKEN && body && body.token !== bot.CONFIG.LARK_VERIFICATION_TOKEN) {
      return res.status(403).json({ error: 'Invalid token' });
    }
    // Process event
    if (body && body.event && body.header && body.header.event_type === 'im.message.receive_v1') {
      // Process asynchronously
      bot.handleImMessageReceive(body.event).catch(function(e) {
        console.error('[WEBHOOK] Error:', e.message);
      });
      return res.status(200).json({ code: 0 });
    }
    return res.status(200).json({ code: 0, message: 'ok' });
  }

  // ---- Health Check ----
  if (method === 'GET' && (path === '/health' || path === '/')) {
    return res.status(200).json({
      status: 'online',
      service: 'NEXA PRO AI Assistant',
      version: '4.0.0',
      mode: 'vercel-serverless',
      providers: {
        dify: !!bot.CONFIG.DIFY_API_KEY,
        openai: !!bot.CONFIG.OPENAI_API_KEY,
        gemini: !!bot.CONFIG.GEMINI_API_KEY,
        claude: !!bot.CONFIG.CLAUDE_API_KEY,
      },
      uptime: Math.floor(process.uptime()),
    });
  }

  // ---- Chat API ----
  if (method === 'POST' && path === '/api/chat') {
    const { query, userId } = req.body || {};
    if (!query) return res.status(400).json({ error: 'Query required' });
    try {
      const response = await bot.callAI(query, userId || 'api_user');
      return res.status(200).json({ response });
    } catch (e) {
      return res.status(500).json({ error: e.message });
    }
  }

  // ---- Weather API ----
  if (method === 'GET' && path.startsWith('/api/weather/')) {
    const city = decodeURIComponent(path.replace('/api/weather/', ''));
    try {
      const weather = await bot.getWeather(city);
      return res.status(200).json({ city, weather });
    } catch (e) {
      return res.status(500).json({ error: e.message });
    }
  }

  // ---- Stock API ----
  if (method === 'GET' && path.startsWith('/api/stock/')) {
    const symbol = path.replace('/api/stock/', '');
    try {
      const quote = await bot.getStockQuote(symbol);
      return res.status(200).json({ symbol, quote });
    } catch (e) {
      return res.status(500).json({ error: e.message });
    }
  }

  // ---- Search API ----
  if (method === 'GET' && path.startsWith('/api/search/')) {
    const query = decodeURIComponent(path.replace('/api/search/', ''));
    try {
      const results = await bot.webSearch(query);
      return res.status(200).json({ query, results });
    } catch (e) {
      return res.status(500).json({ error: e.message });
    }
  }

  // ---- NASA API ----
  if (method === 'GET' && path === '/api/nasa') {
    try {
      const apod = await bot.getNasaApod();
      return res.status(200).json({ apod });
    } catch (e) {
      return res.status(500).json({ error: e.message });
    }
  }

  // ---- Joke API ----
  if (method === 'GET' && path === '/api/joke') {
    try {
      const joke = await bot.getJoke();
      return res.status(200).json({ joke });
    } catch (e) {
      return res.status(500).json({ error: e.message });
    }
  }

  // ---- Quote API ----
  if (method === 'GET' && path === '/api/quote') {
    try {
      const quote = await bot.getQuote();
      return res.status(200).json({ quote });
    } catch (e) {
      return res.status(500).json({ error: e.message });
    }
  }

  // ---- Fact API ----
  if (method === 'GET' && path === '/api/fact') {
    try {
      const fact = await bot.getRandomFact();
      return res.status(200).json({ fact });
    } catch (e) {
      return res.status(500).json({ error: e.message });
    }
  }

  // ---- Wiki API ----
  if (method === 'GET' && path.startsWith('/api/wiki/')) {
    const query = decodeURIComponent(path.replace('/api/wiki/', ''));
    try {
      const summary = await bot.getWikiSummary(query);
      return res.status(200).json({ query, summary });
    } catch (e) {
      return res.status(500).json({ error: e.message });
    }
  }

  // ---- Dictionary API ----
  if (method === 'GET' && path.startsWith('/api/dict/')) {
    const word = path.replace('/api/dict/', '');
    try {
      const definition = await bot.getDictionary(word);
      return res.status(200).json({ word, definition });
    } catch (e) {
      return res.status(500).json({ error: e.message });
    }
  }

  // ---- 404 ----
  return res.status(404).json({ error: 'Not found', available: ['/webhook/lark', '/health', '/api/chat', '/api/weather/:city', '/api/stock/:symbol', '/api/search/:query', '/api/nasa', '/api/joke', '/api/quote', '/api/fact', '/api/wiki/:query', '/api/dict/:word'] });
};
