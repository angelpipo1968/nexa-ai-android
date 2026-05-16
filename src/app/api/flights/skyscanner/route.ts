import { NextRequest, NextResponse } from 'next/server';
import { searchSkyscannerFlights } from '@/lib/nexa-core/skyscanner';

export const dynamic = 'force-dynamic';

const corsHeaders = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
};

export async function OPTIONS() {
    return new NextResponse(null, { headers: corsHeaders });
}

export async function GET(req: NextRequest) {
    const { searchParams } = new URL(req.url);
    const origin = searchParams.get('origin');
    const destination = searchParams.get('destination');
    const date = searchParams.get('date') || new Date().toISOString().split('T')[0];

    if (!origin || !destination) {
        return NextResponse.json({ error: 'Missing parameters' }, { status: 400, headers: corsHeaders });
    }

    try {
        const report = await searchSkyscannerFlights(origin, destination, date);
        return NextResponse.json({ report }, { headers: corsHeaders });
    } catch (error: any) {
        return NextResponse.json({ error: error.message }, { status: 500, headers: corsHeaders });
    }
}
