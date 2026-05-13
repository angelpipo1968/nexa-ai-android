// Vercel Serverless Function: /api/flights
// Flight search proxy using Aviationstack API
// The chat AI can call this to answer flight questions

export const config = {
  runtime: 'edge',
};

const AVIATIONSTACK_BASE = 'http://api.aviationstack.com/v1';

export default async function handler(req) {
  const origin = req.headers.get('origin') || '';
  const allowedOrigins = ['https://www.nexa-ai.dev', 'https://nexa-ai.dev', 'http://localhost:3000'];
  const corsOrigin = allowedOrigins.includes(origin) ? origin : allowedOrigins[0];

  if (req.method === 'OPTIONS') {
    return new Response(null, {
      headers: {
        'Access-Control-Allow-Origin': corsOrigin,
        'Access-Control-Allow-Methods': 'GET, OPTIONS',
        'Access-Control-Allow-Headers': 'Content-Type',
      },
    });
  }

  const apiKey = process.env.AVIATIONSTACK_KEY;
  if (!apiKey) {
    return Response.json({ error: 'Flight API not configured' }, { status: 500 });
  }

  try {
    const url = new URL(req.url);
    const action = url.searchParams.get('action') || 'search';
    const dep = url.searchParams.get('dep')?.toUpperCase(); // IATA code: MEX, MAD, JFK
    const arr = url.searchParams.get('arr')?.toUpperCase();
    const date = url.searchParams.get('date'); // YYYY-MM-DD
    const flightNum = url.searchParams.get('flight'); // e.g. "AA100"
    const limit = url.searchParams.get('limit') || '10';

    let endpoint;

    switch (action) {
      case 'search':
        // Search flights by route
        if (!dep || !arr) {
          return Response.json({ error: 'Missing dep (departure) or arr (arrival) airport code' }, { status: 400 });
        }
        endpoint = `${AVIATIONSTACK_BASE}/flights?access_key=${apiKey}&dep_iata=${dep}&arr_iata=${arr}&limit=${limit}`;
        if (date) endpoint += `&flight_date=${date}`;
        break;

      case 'track':
        // Track specific flight
        if (!flightNum) {
          return Response.json({ error: 'Missing flight number' }, { status: 400 });
        }
        endpoint = `${AVIATIONSTACK_BASE}/flights?access_key=${apiKey}&flight_iata=${flightNum}&limit=5`;
        break;

      case 'airport':
        // Get airport info
        const airportCode = url.searchParams.get('code');
        if (!airportCode) {
          return Response.json({ error: 'Missing airport code' }, { status: 400 });
        }
        endpoint = `${AVIATIONSTACK_BASE}/airports?access_key=${apiKey}&iata_code=${airportCode}`;
        break;

      case 'airline':
        // Get airline info
        const airlineCode = url.searchParams.get('code');
        if (!airlineCode) {
          return Response.json({ error: 'Missing airline IATA code' }, { status: 400 });
        }
        endpoint = `${AVIATIONSTACK_BASE}/airlines?access_key=${apiKey}&iata_code=${airlineCode}`;
        break;

      default:
        return Response.json({ error: 'Unknown action. Use: search, track, airport, airline' }, { status: 400 });
    }

    const upstream = await fetch(endpoint);
    const data = await upstream.json();

    if (data.error) {
      return Response.json({ error: data.error.message || 'API error' }, { status: 502 });
    }

    // Simplify the response for the AI
    const flights = (data.data || []).map(f => ({
      airline: f.airline?.name || 'Unknown',
      flight: f.flight?.iata || f.flight?.icao || 'N/A',
      departure: {
        airport: f.departure?.airport || 'N/A',
        iata: f.departure?.iata || 'N/A',
        terminal: f.departure?.terminal || null,
        gate: f.departure?.gate || null,
        scheduled: f.departure?.scheduled || null,
        estimated: f.departure?.estimated || null,
        delay: f.departure?.delay || null,
      },
      arrival: {
        airport: f.arrival?.airport || 'N/A',
        iata: f.arrival?.iata || 'N/A',
        terminal: f.arrival?.terminal || null,
        gate: f.arrival?.gate || null,
        scheduled: f.arrival?.scheduled || null,
        estimated: f.arrival?.estimated || null,
        delay: f.arrival?.delay || null,
      },
      status: f.flight_status || 'unknown',
      aircraft: f.aircraft?.registration || null,
    }));

    return Response.json({
      results: flights.length,
      flights,
    }, { headers: { 'Access-Control-Allow-Origin': corsOrigin } });

  } catch (err) {
    return Response.json({ error: 'Internal error: ' + err.message }, { status: 500 });
  }
}
