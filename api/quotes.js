// Vercel Serverless Function: /api/quotes
// Free inspirational quotes using Quotable API (no API key needed)

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
    const tag = url.searchParams.get('tag');
    const author = url.searchParams.get('author');
    const limit = parseInt(url.searchParams.get('limit') || '1');

    let apiUrl = 'https://api.quotable.io/quotes/random';
    const params = [];
    if (tag) params.push(`tags=${encodeURIComponent(tag)}`);
    if (author) params.push(`author=${encodeURIComponent(author)}`);
    if (limit > 1) params.push(`limit=${limit}`);
    if (params.length > 0) apiUrl += '?' + params.join('&');

    const res = await fetch(apiUrl);
    const data = await res.json();

    // Also get a Spanish quote as backup
    const spanishQuotes = [
      { text: "La vida es lo que pasa mientras estás ocupado haciendo otros planes.", author: "John Lennon" },
      { text: "El único modo de hacer un gran trabajo es amar lo que haces.", author: "Steve Jobs" },
      { text: "En medio de la dificultad reside la oportunidad.", author: "Albert Einstein" },
      { text: "El futuro pertenece a quienes creen en la belleza de sus sueños.", author: "Eleanor Roosevelt" },
      { text: "No es la especie más fuerte la que sobrevive, sino la más adaptable.", author: "Charles Darwin" },
      { text: "La educación es el arma más poderosa que puedes usar para cambiar el mundo.", author: "Nelson Mandela" },
      { text: "Sé tú mismo; todos los demás ya están ocupados.", author: "Oscar Wilde" },
      { text: "La imaginación es más importante que el conocimiento.", author: "Albert Einstein" },
    ];

    const randomSpanish = spanishQuotes[Math.floor(Math.random() * spanishQuotes.length)];

    return new Response(JSON.stringify({
      quotes: Array.isArray(data) ? data : [data],
      spanishFallback: randomSpanish,
    }), { headers });
  } catch (error) {
    // Return Spanish quotes if API fails
    const spanishQuotes = [
      { text: "La vida es lo que pasa mientras estás ocupado haciendo otros planes.", author: "John Lennon" },
      { text: "El único modo de hacer un gran trabajo es amar lo que haces.", author: "Steve Jobs" },
      { text: "En medio de la dificultad reside la oportunidad.", author: "Albert Einstein" },
    ];
    return new Response(JSON.stringify({
      quotes: [spanishQuotes[Math.floor(Math.random() * spanishQuotes.length)]],
      note: "Fallback quotes (API unavailable)",
    }), { headers });
  }
}
