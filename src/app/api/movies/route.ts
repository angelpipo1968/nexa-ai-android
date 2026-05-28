import { NextRequest, NextResponse } from 'next/server';
import { searchMovies, getTrendingMovies } from '@/lib/nexa-core/tmdb';

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
    const query = searchParams.get('q');
    const trending = searchParams.get('trending');

    try {
        if (trending === 'true') {
            const result = await getTrendingMovies();
            return NextResponse.json({ report: result }, { headers: corsHeaders });
        }

        if (!query) {
            return NextResponse.json({ error: 'Missing q parameter' }, { status: 400, headers: corsHeaders });
        }

        const report = await searchMovies(query);
        return NextResponse.json({ report }, { headers: corsHeaders });
    } catch (error: any) {
        return NextResponse.json({ error: error.message }, { status: 500, headers: corsHeaders });
    }
}
