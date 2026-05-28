import { NextRequest, NextResponse } from 'next/server';
import { searchFlights } from '@/lib/nexa-core/aviation';

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

    if (!destination) {
        return NextResponse.json({ error: 'Missing destination parameter' }, { status: 400, headers: corsHeaders });
    }

    try {
        const result = await searchFlights(origin || 'LAS', destination);
        return NextResponse.json({ report: result }, { headers: corsHeaders });
    } catch (error: any) {
        return NextResponse.json({ error: error.message }, { status: 500, headers: corsHeaders });
    }
}
