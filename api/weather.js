// Vercel Serverless Function: /api/weather
// Free weather API using Open-Meteo (no API key needed)

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
    const lat = url.searchParams.get('lat') || '19.4326';
    const lon = url.searchParams.get('lon') || '-99.1332';
    const city = url.searchParams.get('city');

    // If city name provided, geocode it first
    let latitude = lat;
    let longitude = lon;
    let cityName = city || 'Unknown';

    if (city) {
      const geoRes = await fetch(
        `https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(city)}&count=1&language=es`
      );
      const geoData = await geoRes.json();
      if (geoData.results && geoData.results.length > 0) {
        latitude = geoData.results[0].latitude;
        longitude = geoData.results[0].longitude;
        cityName = geoData.results[0].name;
      }
    }

    // Get current weather + 7 day forecast
    const weatherRes = await fetch(
      `https://api.open-meteo.com/v1/forecast?latitude=${latitude}&longitude=${longitude}&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,uv_index&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=7`
    );
    const weatherData = await weatherRes.json();

    // Weather code to description
    const weatherCodes = {
      0: '☀️ Despejado', 1: '🌤️ Mayormente despejado', 2: '⛅ Parcialmente nublado', 3: '☁️ Nublado',
      45: '🌫️ Niebla', 48: '🌫️ Niebla con escarcha', 51: '🌦️ Llovizna ligera', 53: '🌦️ Llovizna moderada',
      55: '🌦️ Llovizna densa', 61: '🌧️ Lluvia ligera', 63: '🌧️ Lluvia moderada', 65: '🌧️ Lluvia fuerte',
      71: '🌨️ Nieve ligera', 73: '🌨️ Nieve moderada', 75: '🌨️ Nieve fuerte', 80: '🌦️ Chubascos ligeros',
      81: '🌧️ Chubascos moderados', 82: '🌧️ Chubascos fuertes', 95: '⛈️ Tormenta', 96: '⛈️ Tormenta con granizo',
      99: '⛈️ Tormenta con granizo fuerte',
    };

    const current = weatherData.current;
    const daily = weatherData.daily;

    const forecast = daily.time.map((date, i) => ({
      date,
      weather: weatherCodes[daily.weather_code[i]] || '🌤️ Desconocido',
      maxTemp: daily.temperature_2m_max[i],
      minTemp: daily.temperature_2m_min[i],
      precipitation: daily.precipitation_probability_max[i],
    }));

    return new Response(JSON.stringify({
      location: { city: cityName, lat: latitude, lon: longitude },
      current: {
        temperature: current.temperature_2m,
        feelsLike: current.apparent_temperature,
        humidity: current.relative_humidity_2m,
        windSpeed: current.wind_speed_10m,
        uvIndex: current.uv_index,
        weather: weatherCodes[current.weather_code] || '🌤️ Desconocido',
      },
      forecast,
    }), { headers });
  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), { status: 500, headers });
  }
}
