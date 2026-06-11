/**
 * NEXA PRO AI v4.0 — Standalone Server (Render / Local)
 */
require('dotenv').config();
const express = require('express');
const cors = require('cors');
const lark = require('@larksuiteoapi/node-sdk');
const bot = require('./lib/bot');

const app = express();
app.use(cors());
app.use(express.json({ limit: '10mb' }));

// Conversation cleanup
setInterval(function() {
  var now = Date.now();
  bot.CONFIG.MAX_CONVERSATION_AGE = 24 * 60 * 60 * 1000;
}, 60 * 60 * 1000);

// ---- HTTP ENDPOINTS ----

app.get('/', function(req, res) {
  res.json({
    status: 'online',
    service: 'NEXA PRO AI Assistant',
    version: '4.0.0',
    mode: bot.CONFIG.LARK_APP_ID ? 'websocket+http' : 'http',
    providers: {
      dify: !!bot.CONFIG.DIFY_API_KEY,
      openai: !!bot.CONFIG.OPENAI_API_KEY,
      gemini: !!bot.CONFIG.GEMINI_API_KEY,
      claude: !!bot.CONFIG.CLAUDE_API_KEY,
    },
    services: {
      weather: !!bot.CONFIG.WEATHER_API_KEY,
      finance: !!bot.CONFIG.ALPHA_VANTAGE_KEY,
      news: !!bot.CONFIG.NEWS_API_KEY,
      translate: !!bot.CONFIG.DEEPL_API_KEY,
      search: !!(bot.CONFIG.GOOGLE_SEARCH_KEY || bot.CONFIG.BRAVE_SEARCH_KEY),
      movies: !!bot.CONFIG.TMDB_API_KEY,
      nasa: !!bot.CONFIG.NASA_API_KEY,
      wolfram: !!bot.CONFIG.WOLFRAM_APP_ID,
      flights: !!bot.CONFIG.AVIATIONSTACK_KEY,
      spotify: !!bot.CONFIG.SPOTIFY_CLIENT_ID,
      dictionary: true,
      wikipedia: true,
      books: true,
    },
    uptime: Math.floor(process.uptime()),
  });
});

app.get('/health', function(req, res) {
  var mem = process.memoryUsage();
  res.json({
    status: 'healthy',
    uptime: process.uptime(),
    memory: { heapUsed: (mem.heapUsed / 1024 / 1024).toFixed(1) + ' MB' },
    totalMessages: bot.serviceStats.totalMessages,
    errors: bot.serviceStats.errors,
  });
});

// Lark webhook
app.post('/webhook/lark', function(req, res) {
  var body = req.body;
  if (body.type === 'url_verification') {
    return res.json({ challenge: body.challenge });
  }
  if (bot.CONFIG.LARK_VERIFICATION_TOKEN && body.token !== bot.CONFIG.LARK_VERIFICATION_TOKEN) {
    return res.status(403).json({ error: 'Invalid token' });
  }
  var event = body.event;
  if (event && body.header && body.header.event_type === 'im.message.receive_v1') {
    setImmediate(function() {
      bot.handleImMessageReceive(event).catch(function(e) {
        console.error('[WEBHOOK]', e.message);
      });
    });
  }
  res.json({ code: 0 });
});

// REST API endpoints
app.post('/api/chat', async function(req, res) {
  try {
    var q = req.body.query;
    if (!q) return res.status(400).json({ error: 'Query required' });
    var r = await bot.callAI(q, req.body.userId || 'api_user');
    res.json({ response: r });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

app.get('/api/weather/:city', async function(req, res) {
  try { res.json({ weather: await bot.getWeather(req.params.city) }); } catch (e) { res.status(500).json({ error: e.message }); }
});

app.get('/api/stock/:symbol', async function(req, res) {
  try { res.json({ quote: await bot.getStockQuote(req.params.symbol) }); } catch (e) { res.status(500).json({ error: e.message }); }
});

app.get('/api/crypto/:symbol', async function(req, res) {
  try { res.json({ price: await bot.getCryptoPrice(req.params.symbol) }); } catch (e) { res.status(500).json({ error: e.message }); }
});

app.get('/api/search/:query', async function(req, res) {
  try { res.json({ results: await bot.webSearch(req.params.query) }); } catch (e) { res.status(500).json({ error: e.message }); }
});

app.get('/api/nasa', async function(req, res) {
  try { res.json({ apod: await bot.getNasaApod() }); } catch (e) { res.status(500).json({ error: e.message }); }
});

app.get('/api/movie/:query', async function(req, res) {
  try { res.json({ results: await bot.searchMovie(req.params.query) }); } catch (e) { res.status(500).json({ error: e.message }); }
});

app.get('/api/wiki/:query', async function(req, res) {
  try { res.json({ summary: await bot.getWikiSummary(req.params.query) }); } catch (e) { res.status(500).json({ error: e.message }); }
});

app.get('/api/book/:query', async function(req, res) {
  try { res.json({ results: await bot.getBookInfo(req.params.query) }); } catch (e) { res.status(500).json({ error: e.message }); }
});

app.get('/api/joke', async function(req, res) {
  try { res.json({ joke: await bot.getJoke() }); } catch (e) { res.status(500).json({ error: e.message }); }
});

app.get('/api/fact', async function(req, res) {
  try { res.json({ fact: await bot.getRandomFact() }); } catch (e) { res.status(500).json({ error: e.message }); }
});

app.get('/api/quote', async function(req, res) {
  try { res.json({ quote: await bot.getQuote() }); } catch (e) { res.status(500).json({ error: e.message }); }
});

app.get('/api/dict/:word', async function(req, res) {
  try { res.json({ definition: await bot.getDictionary(req.params.word) }); } catch (e) { res.status(500).json({ error: e.message }); }
});

// Start HTTP server
var PORT = bot.CONFIG.PORT || 3000;
app.listen(PORT, '0.0.0.0', function() {
  console.log('[HTTP] Server on port ' + PORT);
});

// Start WebSocket if credentials are set
if (bot.CONFIG.LARK_APP_ID && bot.CONFIG.LARK_APP_SECRET) {
  var eventDispatcher = new lark.EventDispatcher({}).register({
    'im.message.receive_v1': bot.handleImMessageReceive,
  });

  var wsClient = new lark.WSClient({
    appId: bot.CONFIG.LARK_APP_ID,
    appSecret: bot.CONFIG.LARK_APP_SECRET,
    domain: lark.Domain.Lark,
  });

  wsClient.start({ eventDispatcher: eventDispatcher }).then(function() {
    console.log('');
    console.log('==================================================');
    console.log('  NEXA PRO AI Assistant v4.0 - ONLINE');
    console.log('  Mode: WebSocket + HTTP');
    console.log('  AI: Dify | OpenAI | Gemini | Claude');
    console.log('  28+ Commands | 20+ APIs | Full Wisdom');
    console.log('==================================================');
    console.log('');
  }).catch(function(err) {
    console.error('[WS] Failed: ' + err.message);
    console.log('Running in HTTP-only mode.');
  });
} else {
  console.log('');
  console.log('==================================================');
  console.log('  NEXA PRO AI Assistant v4.0 - HTTP Mode');
  console.log('  Set LARK_APP_ID/SECRET for WebSocket');
  console.log('==================================================');
  console.log('');
}
