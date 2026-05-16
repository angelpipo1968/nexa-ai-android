import { NextResponse } from 'next/server';

export const runtime = 'edge';

export async function GET() {
    const providers = {
        groq: !!process.env.GROQ_API_KEY,
        gemini: !!(process.env.GOOGLE_AI_API_KEY || process.env.GEMINI_API_KEY),
        anthropic: !!process.env.ANTHROPIC_API_KEY,
        openai: !!process.env.OPENAI_API_KEY,
        supabase: !!(process.env.NEXT_PUBLIC_SUPABASE_URL && process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY),
    };

    const activeProviders = Object.entries(providers).filter(([_, v]) => v).map(([k]) => k);

    const tools = [
        { name: 'weather', desc: 'Clima y pronóstico 7 días (Open-Meteo)', status: 'free' },
        { name: 'search', desc: 'Búsqueda web + Wikipedia (DuckDuckGo)', status: 'free' },
        { name: 'geolocation', desc: 'Ubicación por IP (ip-api.com)', status: 'free' },
        { name: 'geocode', desc: 'Dirección ↔ coordenadas (OpenStreetMap)', status: 'free' },
        { name: 'exchange', desc: 'Conversión de monedas (ExchangeRate)', status: 'free' },
        { name: 'translate', desc: 'Traducción de texto (MyMemory)', status: 'free' },
        { name: 'news', desc: 'Noticias RSS (El País / NYT)', status: 'free' },
        { name: 'jokes', desc: 'Chistes (JokeAPI)', status: 'free' },
        { name: 'facts', desc: 'Datos curiosos (Numbers API)', status: 'free' },
        { name: 'time', desc: 'Hora mundial (WorldTimeAPI)', status: 'free' },
        { name: 'qrcode', desc: 'Generador de QR (QR Server)', status: 'free' },
        { name: 'countries', desc: 'Info de países (REST Countries)', status: 'free' },
    ];

    return NextResponse.json({
        status: 'ok',
        version: '4.0.0',
        timestamp: new Date().toISOString(),
        providers,
        activeProviders,
        tools,
        toolsCount: tools.length,
        environment: process.env.NODE_ENV || 'development',
    });
}
