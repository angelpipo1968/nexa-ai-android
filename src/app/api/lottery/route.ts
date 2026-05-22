import { NextRequest, NextResponse } from 'next/server';

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
    const action = searchParams.get('action');
    const game = searchParams.get('game');
    const apiKey = process.env.MAGAYO_API_KEY;

    if (!game) {
        return NextResponse.json({ error: 'Missing game parameter' }, { status: 400, headers: corsHeaders });
    }

    try {
        const isDemoMode = !apiKey;
        
        switch (action) {
            case 'results':
                let data;
                if (isDemoMode) {
                    data = {
                        results: Array.from({ length: 6 }, () => Math.floor(Math.random() * 49 + 1)).join(','),
                        bonus: Math.floor(Math.random() * 10 + 1).toString(),
                        draw_date: new Date().toLocaleDateString('es-MX'),
                        draw_number: "DEMO-001"
                    };
                } else {
                    const res = await fetch(`https://www.magayo.com/api/results.php?api_key=${apiKey}&game=${game}`);
                    data = await res.json();
                }
                
                // Map Magayo response to Android app format
                // Magayo returns: { "game": "...", "draw_date": "...", "draw_number": "...", "results": "1,2,3,4,5", "bonus": "6" }
                const numbers = data.results ? data.results.split(',') : [];
                const bonus = data.bonus ? [data.bonus] : [];

                return NextResponse.json({
                    numbers,
                    bonus,
                    draw_date: data.draw_date,
                    draw_number: data.draw_number
                }, { headers: corsHeaders });

            case 'tickets':
                // Generate random tickets (simulated)
                const count = parseInt(searchParams.get('tickets') || '5');
                const tickets = [];
                for (let i = 0; i < count; i++) {
                    const nums = Array.from({ length: 6 }, () => Math.floor(Math.random() * 49 + 1).toString().padStart(2, '0'));
                    tickets.push({ numbers: nums, bonus: [Math.floor(Math.random() * 10 + 1).toString()] });
                }
                return NextResponse.json({ tickets }, { headers: corsHeaders });

            case 'numbers':
                // Recommended numbers (simulated)
                const recNums = Array.from({ length: 6 }, () => Math.floor(Math.random() * 49 + 1).toString().padStart(2, '0'));
                return NextResponse.json({ numbers: recNums }, { headers: corsHeaders });

            case 'next_draw':
                // Mock next draw info
                return NextResponse.json({
                    next_draw_date: "Próximo Sorteo",
                    jackpot: "Acumulado Estimado"
                }, { headers: corsHeaders });

            default:
                return NextResponse.json({ error: 'Invalid action' }, { status: 400, headers: corsHeaders });
        }
    } catch (error: any) {
        return NextResponse.json({ error: error.message }, { status: 500, headers: corsHeaders });
    }
}
