// Vercel Serverless Function: /api/facts
// Free random facts using uselessfacts API (no API key needed)

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
    const category = url.searchParams.get('category') || 'random';

    let fact;

    if (category === 'math') {
      const res = await fetch('http://numbersapi.com/random/math?json');
      const data = await res.json();
      fact = { text: data.text, number: data.number, type: 'math' };
    } else if (category === 'trivia') {
      const res = await fetch('http://numbersapi.com/random/trivia?json');
      const data = await res.json();
      fact = { text: data.text, number: data.number, type: 'trivia' };
    } else if (category === 'date') {
      const res = await fetch('http://numbersapi.com/random/date?json');
      const data = await res.json();
      fact = { text: data.text, year: data.year, type: 'date' };
    } else {
      // Random useless fact
      const res = await fetch('https://uselessfacts.jsph.pl/api/v2/facts/random?language=en');
      const data = await res.json();
      fact = { text: data.text, source: data.source, type: 'random' };
    }

    return new Response(JSON.stringify(fact), { headers });
  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), { status: 500, headers });
  }
}
