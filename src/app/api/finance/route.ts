import { NextRequest, NextResponse } from 'next/server';
import { getStockPrice, getCryptoPrice } from '@/lib/nexa-core/finance';

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
    const stock = searchParams.get('stock');
    const crypto = searchParams.get('crypto');

    try {
        if (stock) {
            const report = await getStockPrice(stock);
            return NextResponse.json({ report }, { headers: corsHeaders });
        }
        if (crypto) {
            const report = await getCryptoPrice(crypto);
            return NextResponse.json({ report }, { headers: corsHeaders });
        }
        return NextResponse.json({ error: 'Missing parameters' }, { status: 400, headers: corsHeaders });
    } catch (error: any) {
        return NextResponse.json({ error: error.message }, { status: 500, headers: corsHeaders });
    }
}
