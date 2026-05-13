// Vercel Serverless Function: /api/lottery
// Proxy for magayo Lottery API
// Keeps API key secure on server side

export const config = {
  runtime: 'edge',
};

const MAGAYO_BASE = 'https://www.magayo.com/api';

// Supported lottery games
const GAMES = {
  // México
  'melate': { name: 'Melate', game: 'mx_melate', country: '🇲🇽 México' },
  'melate_retro': { name: 'Melate Retro', game: 'mx_melate_retro', country: '🇲🇽 México' },
  'chispazo': { name: 'Chispazo', game: 'mx_chispazo', country: '🇲🇽 México' },
  // España
  'euromillions': { name: 'EuroMillones', game: 'es_euromillions', country: '🇪🇸 España' },
  'primitiva': { name: 'La Primitiva', game: 'es_primitiva', country: '🇪🇸 España' },
  'el_gordo': { name: 'El Gordo', game: 'es_el_gordo', country: '🇪🇸 España' },
  'lototurf': { name: 'Lototurf', game: 'es_lototurf', country: '🇪🇸 España' },
  // USA
  'powerball': { name: 'Powerball', game: 'us_powerball', country: '🇺🇸 USA' },
  'megamillions': { name: 'Mega Millions', game: 'us_megamillions', country: '🇺🇸 USA' },
  // Colombia
  'baloto': { name: 'Baloto', game: 'co_baloto', country: '🇨🇴 Colombia' },
  // Argentina
  'loteria_nacional': { name: 'Lotería Nacional', game: 'ar_quini6', country: '🇦🇷 Argentina' },
};

export default async function handler(req) {
  // CORS
  const origin = req.headers.get('origin') || '';
  const allowedOrigins = ['https://www.nexa-ai.dev', 'https://nexa-ai.dev', 'http://localhost:3000'];
  const corsOrigin = allowedOrigins.includes(origin) ? origin : allowedOrigins[0];

  if (req.method === 'OPTIONS') {
    return new Response(null, {
      headers: {
        'Access-Control-Allow-Origin': corsOrigin,
        'Access-Control-Allow-Methods': 'GET, OPTIONS',
        'Access-Control-Allow-Headers': 'Content-Type',
      },
    });
  }

  const apiKey = process.env.MAGAYO_API_KEY;
  if (!apiKey) {
    return Response.json({ error: 'Lottery API not configured' }, { status: 500 });
  }

  try {
    const url = new URL(req.url);
    const action = url.searchParams.get('action') || 'results';
    const gameKey = url.searchParams.get('game') || 'melate';
    const tickets = url.searchParams.get('tickets') || '5';

    const gameInfo = GAMES[gameKey];
    if (!gameInfo) {
      return Response.json({ error: `Unknown game: ${gameKey}`, available: Object.keys(GAMES) }, { status: 400 });
    }

    const game = gameInfo.game;
    let endpoint;

    switch (action) {
      case 'results':
        endpoint = `${MAGAYO_BASE}/results.php?api_key=${apiKey}&game=${game}`;
        break;
      case 'info':
        endpoint = `${MAGAYO_BASE}/info.php?api_key=${apiKey}&game=${game}`;
        break;
      case 'next_draw':
        endpoint = `${MAGAYO_BASE}/next_draw.php?api_key=${apiKey}&game=${game}`;
        break;
      case 'jackpot':
        endpoint = `${MAGAYO_BASE}/jackpot.php?api_key=${apiKey}&game=${game}`;
        break;
      case 'numbers':
        endpoint = `${MAGAYO_BASE}/numbers.php?api_key=${apiKey}&game=${game}`;
        break;
      case 'tickets':
        endpoint = `${MAGAYO_BASE}/tickets.php?api_key=${apiKey}&game=${game}&tickets=${tickets}`;
        break;
      case 'games':
        // Return available games list (no API call needed)
        return Response.json({
          games: Object.entries(GAMES).map(([key, info]) => ({
            id: key,
            name: info.name,
            country: info.country,
          }))
        }, { headers: { 'Access-Control-Allow-Origin': corsOrigin } });
      default:
        return Response.json({ error: `Unknown action: ${action}` }, { status: 400 });
    }

    const upstream = await fetch(endpoint);
    const data = await upstream.json();

    return Response.json(data, {
      headers: { 'Access-Control-Allow-Origin': corsOrigin },
    });

  } catch (err) {
    return Response.json({ error: 'Internal error: ' + err.message }, { status: 500 });
  }
}
