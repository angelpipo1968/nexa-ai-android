// Vercel Serverless Function: /api/translate
// Free translation using MyMemory API (no API key needed, 5000 chars/day)

export const config = {
  runtime: 'edge',
};

export default async function handler(req) {
  const origin = req.headers.get('origin') || '';
  const allowedOrigins = ['https://www.nexa-ai.dev', 'https://nexa-ai.dev', 'http://localhost:3000'];
  const corsOrigin = allowedOrigins.includes(origin) ? origin : allowedOrigins[0];

  const headers = {
    'Access-Control-Allow-Origin': corsOrigin,
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Content-Type': 'application/json',
  };

  if (req.method === 'OPTIONS') {
    return new Response(null, { headers });
  }

  try {
    let text, from, to;

    if (req.method === 'POST') {
      const body = await req.json();
      text = body.text;
      from = body.from || 'es';
      to = body.to || 'en';
    } else {
      const url = new URL(req.url);
      text = url.searchParams.get('text');
      from = url.searchParams.get('from') || 'es';
      to = url.searchParams.get('to') || 'en';
    }

    if (!text) {
      return new Response(JSON.stringify({ error: 'Missing ?text= parameter' }), { status: 400, headers });
    }

    // MyMemory Translation API (free, no key needed)
    const res = await fetch(
      `https://api.mymemory.translated.net/get?q=${encodeURIComponent(text)}&langpair=${from}|${to}`
    );
    const data = await res.json();

    return new Response(JSON.stringify({
      original: text,
      translated: data.responseData.translatedText,
      from,
      to,
      match: data.responseData.match,
    }), { headers });
  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), { status: 500, headers });
  }
}
