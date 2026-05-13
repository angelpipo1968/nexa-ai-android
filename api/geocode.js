// Vercel Serverless Function: /api/geocode
// Free geocoding using Nominatim/OpenStreetMap (no API key needed)

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
    const query = url.searchParams.get('q');
    const lat = url.searchParams.get('lat');
    const lon = url.searchParams.get('lon');
    const limit = url.searchParams.get('limit') || '5';

    if (!query && (!lat || !lon)) {
      return new Response(JSON.stringify({ error: 'Provide ?q=address or ?lat=X&lon=Y' }), { status: 400, headers });
    }

    let nominatimUrl;
    if (query) {
      // Forward geocoding: address → coordinates
      nominatimUrl = `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(query)}&format=jsonv2&limit=${limit}&addressdetails=1&accept-language=es`;
    } else {
      // Reverse geocoding: coordinates → address
      nominatimUrl = `https://nominatim.openstreetmap.org/reverse?lat=${lat}&lon=${lon}&format=jsonv2&addressdetails=1&accept-language=es`;
    }

    const res = await fetch(nominatimUrl, {
      headers: { 'User-Agent': 'NexaAI/1.0 (contact@nexa-ai.dev)' },
    });
    const data = await res.json();

    return new Response(JSON.stringify(data), { headers });
  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), { status: 500, headers });
  }
}
