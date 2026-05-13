// Vercel Serverless Function: /api/news
// Free news using GNews API (no API key for limited use) + RSS fallback

export const config = {
  runtime: 'edge',
};

// Parse RSS feed
async function parseRSS(url) {
  const res = await fetch(url);
  const text = await res.text();
  const items = [];
  const matches = text.matchAll(/<item>[\s\S]*?<\/item>/g);
  for (const match of matches) {
    const item = match[0];
    const title = item.match(/<title><!\[CDATA\[(.*?)\]\]><\/title>/)?.[1] || item.match(/<title>(.*?)<\/title>/)?.[1] || '';
    const link = item.match(/<link>(.*?)<\/link>/)?.[1] || '';
    const desc = item.match(/<description><!\[CDATA\[(.*?)\]\]><\/description>/)?.[1] || item.match(/<description>(.*?)<\/description>/)?.[1] || '';
    const pubDate = item.match(/<pubDate>(.*?)<\/pubDate>/)?.[1] || '';
    items.push({ title: title.trim(), link, description: desc.replace(/<[^>]*>/g, '').trim(), pubDate });
  }
  return items.slice(0, 10);
}

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
    const topic = url.searchParams.get('topic') || 'general';
    const lang = url.searchParams.get('lang') || 'es';

    // Use free RSS feeds from major news sources
    const rssFeeds = {
      es: {
        general: 'https://feeds.elpais.com/mrss-s/pages/pe/site/elpais.com/portada',
        technology: 'https://feeds.elpais.com/mrss-s/pages/pe/site/elpais.com/section/tecnologia/portada',
        sports: 'https://feeds.elpais.com/mrss-s/pages/pe/site/elpais.com/section/deportes/portada',
      },
      en: {
        general: 'https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml',
        technology: 'https://rss.nytimes.com/services/xml/rss/nyt/Technology.xml',
        sports: 'https://rss.nytimes.com/services/xml/rss/nyt/Sports.xml',
        science: 'https://rss.nytimes.com/services/xml/rss/nyt/Science.xml',
        world: 'https://rss.nytimes.com/services/xml/rss/nyt/World.xml',
      },
    };

    const feedUrl = rssFeeds[lang]?.[topic] || rssFeeds[lang]?.general || rssFeeds.en.general;
    const articles = await parseRSS(feedUrl);

    return new Response(JSON.stringify({
      topic,
      lang,
      source: feedUrl,
      articles,
    }), { headers });
  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), { status: 500, headers });
  }
}
