import { NextRequest, NextResponse } from 'next/server';
import { runAutonomousLoop } from '@/lib/nexa-core/orchestrator';

export const maxDuration = 60;
export const runtime = 'nodejs';

const corsHeaders = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
};

export async function OPTIONS() {
    return new NextResponse(null, { headers: corsHeaders });
}

export async function POST(req: NextRequest) {
    try {
        const { message } = await req.json();

        if (!message) {
            return NextResponse.json({ error: 'Missing message' }, { status: 400, headers: corsHeaders });
        }

        // Ejecutamos el bucle autónomo
        const result = await runAutonomousLoop(message);

        return NextResponse.json({ 
            response: result,
            autonomous: true,
            version: 'V4-Autonomous'
        }, { headers: corsHeaders });

    } catch (error: any) {
        return NextResponse.json({ error: error.message }, { status: 500, headers: corsHeaders });
    }
}
