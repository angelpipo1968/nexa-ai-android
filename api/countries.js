// Vercel Serverless Function: /api/countries
// Free country info using REST Countries API (no API key needed)

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
    const name = url.searchParams.get('name');
    const code = url.searchParams.get('code');
    const region = url.searchParams.get('region');
    const all = url.searchParams.get('all');

    let apiUrl = 'https://restcountries.com/v3.1/';
    if (all) {
      apiUrl += 'all?fields=name,capital,population,region,subregion,flags,currencies,languages,area';
    } else if (code) {
      apiUrl += `alpha/${code}`;
    } else if (name) {
      apiUrl += `name/${encodeURIComponent(name)}`;
    } else if (region) {
      apiUrl += `region/${encodeURIComponent(region)}`;
    } else {
      return new Response(JSON.stringify({ error: 'Provide ?name=, ?code=, ?region=, or ?all=1' }), { status: 400, headers });
    }

    const res = await fetch(apiUrl);
    const data = await res.json();

    return new Response(JSON.stringify(data), { headers });
  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), { status: 500, headers });
  }
}
