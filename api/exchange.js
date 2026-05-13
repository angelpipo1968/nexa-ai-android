// Vercel Serverless Function: /api/exchange
// Free exchange rates using exchangerate-api.com (no API key needed for basic)

export const config = {
  runtime: 'edge',
};

export default async function handler(req) {
  const origin = req.headers.get('origin') || '';
  const allowedOrigins = ['https://www.nexa-ai.dev', 'https://nexa-ai.dev', 'http://localhost:3000'];
  const corsOrigin = allowedOrigins.includes(origin) ? origin : allowedOrigins[0];

  const headers = {
    'Access-Control-Allow-Origin': corsOrigin,
    'Access-Control-Allow-Methods': 'GET, OPTIONS',
    'Content-Type': 'application/json',
  };

  if (req.method === 'OPTIONS') {
    return new Response(null, { headers });
  }

  try {
    const url = new URL(req.url);
    const from = (url.searchParams.get('from') || 'USD').toUpperCase();
    const to = (url.searchParams.get('to') || 'EUR').toUpperCase();
    const amount = parseFloat(url.searchParams.get('amount') || '1');

    // Use free open.er-api.com (no key needed)
    const res = await fetch(`https://open.er-api.com/v6/latest/${from}`);
    const data = await res.json();

    if (data.result !== 'success') {
      return new Response(JSON.stringify({ error: 'Failed to fetch rates' }), { status: 500, headers });
    }

    const rate = data.rates[to];
    if (!rate) {
      return new Response(JSON.stringify({ error: `Currency ${to} not found` }), { status: 400, headers });
    }

    return new Response(JSON.stringify({
      from,
      to,
      rate,
      amount,
      result: (amount * rate).toFixed(2),
      lastUpdated: data.time_last_update_utc,
    }), { headers });
  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), { status: 500, headers });
  }
}
