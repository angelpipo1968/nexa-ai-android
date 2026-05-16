// Vercel Serverless Function: /api/search
// Free search using DuckDuckGo Instant Answer API (no API key needed)

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

    if (!query) {
      return new Response(JSON.stringify({ error: 'Missing query parameter ?q=' }), { status: 400, headers });
    }

    // DuckDuckGo Instant Answer API
    const ddgRes = await fetch(
      `https://api.duckduckgo.com/?q=${encodeURIComponent(query)}&format=json&no_html=1&skip_disambig=1`
    );
    const ddgData = await ddgRes.json();

    // Wikipedia search as fallback
    const wikiRes = await fetch(
      `https://es.wikipedia.org/api/rest_v1/page/summary/${encodeURIComponent(query)}`
    );
    let wikiData = null;
    if (wikiRes.ok) {
      wikiData = await wikiRes.json();
    }

    // Also try English Wikipedia
    let wikiEnData = null;
    if (!wikiData || wikiData.type === 'disambiguation') {
      const wikiEnRes = await fetch(
        `https://en.wikipedia.org/api/rest_v1/page/summary/${encodeURIComponent(query)}`
      );
      if (wikiEnRes.ok) {
        wikiEnData = await wikiEnRes.json();
      }
    }

    return new Response(JSON.stringify({
      query,
      duckduckgo: {
        abstract: ddgData.AbstractText || null,
        source: ddgData.AbstractSource || null,
        url: ddgData.AbstractURL || null,
        image: ddgData.Image || null,
        answer: ddgData.Answer || null,
        relatedTopics: (ddgData.RelatedTopics || []).slice(0, 5).map(t => ({
          text: t.Text,
          url: t.FirstURL,
        })),
      },
      wikipedia: wikiData ? {
        title: wikiData.title,
        extract: wikiData.extract,
        image: wikiData.thumbnail?.source || null,
        url: wikiData.content_urls?.desktop?.page || null,
      } : (wikiEnData ? {
        title: wikiEnData.title,
        extract: wikiEnData.extract,
        image: wikiEnData.thumbnail?.source || null,
        url: wikiEnData.content_urls?.desktop?.page || null,
      } : null),
    }), { headers });
  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), { status: 500, headers });
  }
}
