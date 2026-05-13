// Vercel Serverless Function: /api/qrcode
// Free QR code generator using qrserver.com (no API key needed)

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
    const text = url.searchParams.get('text');
    const size = url.searchParams.get('size') || '300';
    const format = url.searchParams.get('format') || 'png';

    if (!text) {
      return new Response(JSON.stringify({ error: 'Missing ?text= parameter' }), { status: 400, headers });
    }

    // Generate QR code URL using free API
    const qrUrl = `https://api.qrserver.com/v1/create-qr-code/?size=${size}x${size}&data=${encodeURIComponent(text)}&format=${format}`;

    return new Response(JSON.stringify({
      text,
      qrUrl,
      size: `${size}x${size}`,
      format,
    }), { headers });
  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), { status: 500, headers });
  }
}
