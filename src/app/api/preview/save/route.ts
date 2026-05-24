import { NextRequest, NextResponse } from 'next/server';
import { Redis } from '@upstash/redis';
import { v4 as uuidv4 } from 'uuid';

// Lazy-initialize Redis to prevent crashes when REDIS_URL is not set.
let _redis: Redis | null = null;

function getRedis(): Redis | null {
    if (_redis) return _redis;
    try {
        _redis = Redis.fromEnv();
    } catch {
        return null;
    }
    return _redis;
}

export async function POST(req: NextRequest) {
    const redis = getRedis();
    if (!redis) {
        return NextResponse.json({ success: false, error: 'Servicio de previsualización no disponible (Redis no configurado)' }, { status: 503 });
    }
    try {
        const { code, title } = await req.json();
        const id = uuidv4().substring(0, 8); // ID corto y elegante
        
        // Guardamos el código en Redis por 24 horas (punto de previsualización temporal)
        await redis.set(`preview:${id}`, JSON.stringify({ code, title, createdAt: Date.now() }), { ex: 86400 });

        const url = `${req.nextUrl.origin}/preview/${id}`;
        return NextResponse.json({ success: true, id, url });
    } catch (e) {
        return NextResponse.json({ success: false, error: 'Error al generar la previsualización' }, { status: 500 });
    }
}
