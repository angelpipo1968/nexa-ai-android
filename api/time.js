// Vercel Serverless Function: /api/time
// Free world time using WorldTimeAPI (no API key needed)

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
    const timezone = url.searchParams.get('tz') || 'America/Mexico_City';
    const list = url.searchParams.get('list');

    if (list === 'timezones') {
      // Return popular timezones
      const res = await fetch('http://worldtimeapi.org/api/timezone');
      const allTz = await res.json();
      const popular = allTz.filter(tz =>
        ['America/', 'Europe/', 'Asia/', 'Australia/', 'Africa/'].some(p => tz.startsWith(p))
      ).slice(0, 50);
      return new Response(JSON.stringify({ timezones: popular }), { headers });
    }

    const res = await fetch(`http://worldtimeapi.org/api/timezone/${encodeURIComponent(timezone)}`);
    const data = await res.json();

    if (data.error) {
      return new Response(JSON.stringify({ error: data.error }), { status: 400, headers });
    }

    return new Response(JSON.stringify({
      timezone: data.timezone,
      datetime: data.datetime,
      utcOffset: data.utc_offset,
      dayOfWeek: data.day_of_week,
      dayOfYear: data.day_of_year,
      weekNumber: data.week_number,
      abbreviation: data.abbreviation,
    }), { headers });
  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), { status: 500, headers });
  }
}
