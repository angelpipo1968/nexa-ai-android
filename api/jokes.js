// Vercel Serverless Function: /api/jokes
// Free jokes using JokeAPI (no API key needed)

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
    const category = url.searchParams.get('category') || 'Any';
    const lang = url.searchParams.get('lang') || 'es';
    const search = url.searchParams.get('search');

    let apiUrl = `https://v2.jokeapi.dev/joke/${category}?lang=${lang}&safe-mode`;
    if (search) {
      apiUrl += `&contains=${encodeURIComponent(search)}`;
    }

    const res = await fetch(apiUrl);
    const data = await res.json();

    if (data.error) {
      return new Response(JSON.stringify({ error: data.message }), { status: 400, headers });
    }

    let joke;
    if (data.type === 'single') {
      joke = data.joke;
    } else {
      joke = `${data.setup}\n${data.delivery}`;
    }

    return new Response(JSON.stringify({
      joke,
      category: data.category,
      type: data.type,
      lang: data.lang,
      safe: data.safe,
    }), { headers });
  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), { status: 500, headers });
  }
}
