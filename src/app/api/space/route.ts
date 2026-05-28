import { NextRequest, NextResponse } from 'next/server';
import { getNASAAPOD, searchMarsPhotos } from '@/lib/nexa-core/nasa';

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
    const type = searchParams.get('type') || 'apod';

    try {
        let result;
        if (type === 'mars') {
            result = await searchMarsPhotos();
        } else {
            result = await getNASAAPOD();
        }
        return NextResponse.json({ report: result }, { headers: corsHeaders });
    } catch (error: any) {
        return NextResponse.json({ error: error.message }, { status: 500, headers: corsHeaders });
    }
}
