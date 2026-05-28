import { NextResponse } from 'next/server';

export const runtime = 'edge';

export async function GET() {
    return NextResponse.json({
        status: 'ok',
        service: 'nexa-local-backend',
        version: '4.0.0',
        timestamp: new Date().toISOString(),
    });
}
