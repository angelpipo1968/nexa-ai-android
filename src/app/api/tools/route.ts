// Vercel Serverless Function: /api/tools
// NEXA AI Tools Router — connects to free external APIs

import { NextRequest, NextResponse } from 'next/server';

export const maxDuration = 30;
export const runtime = 'nodejs';

const corsHeaders = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
};

// ─── Weather (Open-Meteo, free, no key) ───
async function getWeather(city?: string, lat?: string, lon?: string) {
    let latitude = lat || '19.4326';
    let longitude = lon || '-99.1332';
    let cityName = city || 'Unknown';

    if (city) {
        const geoRes = await fetch(
            `https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(city)}&count=1&language=es`
        );
        const geoData = await geoRes.json();
        if (geoData.results?.length > 0) {
            latitude = geoData.results[0].latitude;
            longitude = geoData.results[0].longitude;
            cityName = geoData.results[0].name;
        }
    }

    const weatherRes = await fetch(
        `https://api.open-meteo.com/v1/forecast?latitude=${latitude}&longitude=${longitude}&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,uv_index&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=7`
    );
    const data = await weatherRes.json();

    const codes: Record<number, string> = {
        0: '☀️ Despejado', 1: '🌤️ Mayormente despejado', 2: '⛅ Parcialmente nublado', 3: '☁️ Nublado',
        45: '🌫️ Niebla', 51: '🌦️ Llovizna ligera', 61: '🌧️ Lluvia ligera', 63: '🌧️ Lluvia moderada',
        80: '🌦️ Chubascos', 95: '⛈️ Tormenta',
    };

    return {
        location: { city: cityName, lat: latitude, lon: longitude },
        current: {
            temperature: data.current.temperature_2m,
            feelsLike: data.current.apparent_temperature,
            humidity: data.current.relative_humidity_2m,
            windSpeed: data.current.wind_speed_10m,
            uvIndex: data.current.uv_index,
            weather: codes[data.current.weather_code] || '🌤️ Desconocido',
        },
        forecast: data.daily.time.map((date: string, i: number) => ({
            date,
            weather: codes[data.daily.weather_code[i]] || '🌤️',
            maxTemp: data.daily.temperature_2m_max[i],
            minTemp: data.daily.temperature_2m_min[i],
            precipitation: data.daily.precipitation_probability_max[i],
        })),
    };
}

// ─── Search (DuckDuckGo + Wikipedia, free, no key) ───
async function searchWeb(query: string) {
    const [ddgRes, wikiEsRes, wikiEnRes] = await Promise.all([
        fetch(`https://api.duckduckgo.com/?q=${encodeURIComponent(query)}&format=json&no_html=1&skip_disambig=1`),
        fetch(`https://es.wikipedia.org/api/rest_v1/page/summary/${encodeURIComponent(query)}`).catch(() => null),
        fetch(`https://en.wikipedia.org/api/rest_v1/page/summary/${encodeURIComponent(query)}`).catch(() => null),
    ]);

    const ddg = await ddgRes.json();
    let wiki = null;
    if (wikiEsRes?.ok) {
        const d = await wikiEsRes.json();
        if (d.type !== 'disambiguation') wiki = d;
    }
    if (!wiki && wikiEnRes?.ok) {
        const d = await wikiEnRes.json();
        if (d.type !== 'disambiguation') wiki = d;
    }

    return {
        query,
        duckduckgo: {
            abstract: ddg.AbstractText || null,
            source: ddg.AbstractSource || null,
            url: ddg.AbstractURL || null,
            answer: ddg.Answer || null,
            relatedTopics: (ddg.RelatedTopics || []).slice(0, 5).map((t: any) => ({ text: t.Text, url: t.FirstURL })),
        },
        wikipedia: wiki ? {
            title: wiki.title,
            extract: wiki.extract,
            image: wiki.thumbnail?.source || null,
            url: wiki.content_urls?.desktop?.page || null,
        } : null,
    };
}

// ─── Geolocation (ip-api.com, free, no key) ───
async function getGeolocation(ip?: string) {
    const res = await fetch(`http://ip-api.com/json/${ip || ''}?fields=status,country,countryCode,region,regionName,city,zip,lat,lon,timezone,isp,org,mobile,proxy,hosting`);
    return res.json();
}

// ─── Geocoding (Nominatim, free, no key) ───
async function geocode(query?: string, lat?: string, lon?: string) {
    if (query) {
        const res = await fetch(
            `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(query)}&format=jsonv2&limit=5&addressdetails=1&accept-language=es`,
            { headers: { 'User-Agent': 'NexaAI/1.0' } }
        );
        return res.json();
    } else if (lat && lon) {
        const res = await fetch(
            `https://nominatim.openstreetmap.org/reverse?lat=${lat}&lon=${lon}&format=jsonv2&addressdetails=1&accept-language=es`,
            { headers: { 'User-Agent': 'NexaAI/1.0' } }
        );
        return res.json();
    }
    return { error: 'Provide query or lat/lon' };
}

// ─── Currency Exchange (open.er-api.com, free, no key) ───
async function getExchangeRate(from: string, to: string, amount: number) {
    const res = await fetch(`https://open.er-api.com/v6/latest/${from.toUpperCase()}`);
    const data = await res.json();
    if (data.result !== 'success') return { error: 'Failed to fetch rates' };
    const rate = data.rates[to.toUpperCase()];
    if (!rate) return { error: `Currency ${to} not found` };
    return { from: from.toUpperCase(), to: to.toUpperCase(), rate, amount, result: (amount * rate).toFixed(2) };
}

// ─── Translation (MyMemory, free, no key) ───
async function translateText(text: string, from: string, to: string) {
    const res = await fetch(`https://api.mymemory.translated.net/get?q=${encodeURIComponent(text)}&langpair=${from}|${to}`);
    const data = await res.json();
    return { original: text, translated: data.responseData.translatedText, from, to };
}

// ─── News (RSS feeds, free, no key) ───
async function getNews(topic: string, lang: string) {
    const feeds: Record<string, Record<string, string>> = {
        es: {
            general: 'https://feeds.elpais.com/mrss-s/pages/pe/site/elpais.com/portada',
            technology: 'https://feeds.elpais.com/mrss-s/pages/pe/site/elpais.com/section/tecnologia/portada',
        },
        en: {
            general: 'https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml',
            technology: 'https://rss.nytimes.com/services/xml/rss/nyt/Technology.xml',
            science: 'https://rss.nytimes.com/services/xml/rss/nyt/Science.xml',
        },
    };

    const feedUrl = feeds[lang]?.[topic] || feeds[lang]?.general || feeds.en.general;
    const res = await fetch(feedUrl);
    const text = await res.text();
    const items = [...text.matchAll(/<item>[\s\S]*?<\/item>/g)].slice(0, 10).map(m => {
        const item = m[0];
        return {
            title: item.match(/<title><!\[CDATA\[(.*?)\]\]><\/title>/)?.[1] || item.match(/<title>(.*?)<\/title>/)?.[1] || '',
            link: item.match(/<link>(.*?)<\/link>/)?.[1] || '',
            description: item.match(/<description><!\[CDATA\[(.*?)\]\]><\/description>/)?.[1]?.replace(/<[^>]*>/g, '').trim() || '',
        };
    });
    return { topic, lang, source: feedUrl, articles: items };
}

// ─── Jokes (JokeAPI, free, no key) ───
async function getJoke(category: string, lang: string) {
    const res = await fetch(`https://v2.jokeapi.dev/joke/${category}?lang=${lang}&safe-mode`);
    const data = await res.json();
    if (data.error) return { error: data.message };
    return {
        joke: data.type === 'single' ? data.joke : `${data.setup}\n${data.delivery}`,
        category: data.category, type: data.type, lang: data.lang,
    };
}

// ─── Facts (Numbers API + Useless Facts, free, no key) ───
async function getFact(category: string) {
    if (category === 'math' || category === 'trivia' || category === 'date') {
        const res = await fetch(`http://numbersapi.com/random/${category}?json`);
        const data = await res.json();
        return { text: data.text, number: data.number, type: category };
    }
    const res = await fetch('https://uselessfacts.jsph.pl/api/v2/facts/random?language=en');
    const data = await res.json();
    return { text: data.text, source: data.source, type: 'random' };
}

// ─── QR Code (qrserver.com, free, no key) ───
function generateQR(text: string, size: string) {
    return {
        text,
        qrUrl: `https://api.qrserver.com/v1/create-qr-code/?size=${size}x${size}&data=${encodeURIComponent(text)}`,
        size: `${size}x${size}`,
    };
}

// ─── World Time (WorldTimeAPI, free, no key) ───
async function getTime(timezone: string) {
    const res = await fetch(`http://worldtimeapi.org/api/timezone/${encodeURIComponent(timezone)}`);
    const data = await res.json();
    if (data.error) return { error: data.error };
    return {
        timezone: data.timezone, datetime: data.datetime, utcOffset: data.utc_offset,
        dayOfWeek: data.day_of_week, abbreviation: data.abbreviation,
    };
}

// ─── Countries (REST Countries, free, no key) ───
async function getCountries(query?: string, code?: string, region?: string) {
    let url = 'https://restcountries.com/v3.1/';
    if (code) url += `alpha/${code}`;
    else if (query) url += `name/${encodeURIComponent(query)}`;
    else if (region) url += `region/${encodeURIComponent(region)}`;
    else url += 'all?fields=name,capital,population,region,flags,currencies,languages';
    const res = await fetch(url);
    return res.json();
}

// ═══════════════════════════════════════════
//  MAIN HANDLER
// ═══════════════════════════════════════════

export async function OPTIONS() {
    return new NextResponse(null, { headers: corsHeaders });
}

export async function GET(req: NextRequest) {
    try {
        const url = new URL(req.url);
        const tool = url.searchParams.get('tool');
        const params = Object.fromEntries(url.searchParams.entries());

        let result;

        switch (tool) {
            case 'weather':
                result = await getWeather(params.city, params.lat, params.lon);
                break;
            case 'search':
                if (!params.q) return NextResponse.json({ error: 'Missing ?q=' }, { status: 400 });
                result = await searchWeb(params.q);
                break;
            case 'geolocation':
                result = await getGeolocation(params.ip);
                break;
            case 'geocode':
                result = await geocode(params.q, params.lat, params.lon);
                break;
            case 'exchange':
                result = await getExchangeRate(params.from || 'USD', params.to || 'EUR', parseFloat(params.amount || '1'));
                break;
            case 'translate':
                if (!params.text) return NextResponse.json({ error: 'Missing ?text=' }, { status: 400 });
                result = await translateText(params.text, params.from || 'es', params.to || 'en');
                break;
            case 'news':
                result = await getNews(params.topic || 'general', params.lang || 'es');
                break;
            case 'jokes':
                result = await getJoke(params.category || 'Any', params.lang || 'es');
                break;
            case 'facts':
                result = await getFact(params.category || 'random');
                break;
            case 'qrcode':
                if (!params.text) return NextResponse.json({ error: 'Missing ?text=' }, { status: 400 });
                result = generateQR(params.text, params.size || '300');
                break;
            case 'time':
                result = await getTime(params.tz || 'America/Mexico_City');
                break;
            case 'countries':
                result = await getCountries(params.name, params.code, params.region);
                break;
            case 'list':
                result = {
                    tools: [
                        { name: 'weather', desc: 'Clima y pronóstico 7 días', params: 'city | lat,lon' },
                        { name: 'search', desc: 'Búsqueda web + Wikipedia', params: 'q' },
                        { name: 'geolocation', desc: 'Ubicación por IP', params: 'ip (optional)' },
                        { name: 'geocode', desc: 'Dirección ↔ coordenadas', params: 'q | lat,lon' },
                        { name: 'exchange', desc: 'Conversión de monedas', params: 'from, to, amount' },
                        { name: 'translate', desc: 'Traducción de texto', params: 'text, from, to' },
                        { name: 'news', desc: 'Noticias RSS', params: 'topic, lang' },
                        { name: 'jokes', desc: 'Chistes', params: 'category, lang' },
                        { name: 'facts', desc: 'Datos curiosos', params: 'category' },
                        { name: 'qrcode', desc: 'Generador QR', params: 'text, size' },
                        { name: 'time', desc: 'Hora mundial', params: 'tz' },
                        { name: 'countries', desc: 'Info de países', params: 'name | code | region' },
                    ],
                };
                break;
            default:
                result = { error: 'Unknown tool. Use ?tool=list to see available tools.' };
        }

        return NextResponse.json(result, { headers: corsHeaders });
    } catch (error: any) {
        return NextResponse.json({ error: error.message }, { status: 500, headers: corsHeaders });
    }
}

export async function POST(req: NextRequest) {
    try {
        const body = await req.json();
        const { tool, ...params } = body;

        let result;

        switch (tool) {
            case 'weather':
                result = await getWeather(params.city, params.lat, params.lon);
                break;
            case 'search':
                result = await searchWeb(params.query || params.q);
                break;
            case 'translate':
                result = await translateText(params.text, params.from || 'es', params.to || 'en');
                break;
            case 'exchange':
                result = await getExchangeRate(params.from || 'USD', params.to || 'EUR', params.amount || 1);
                break;
            default:
                result = { error: 'POST not supported for this tool. Use GET.' };
        }

        return NextResponse.json(result, { headers: corsHeaders });
    } catch (error: any) {
        return NextResponse.json({ error: error.message }, { status: 500, headers: corsHeaders });
    }
}
