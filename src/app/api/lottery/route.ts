import { NextRequest, NextResponse } from 'next/server';
import { LOTTERY_GAMES, getLotteryResults, getNextDraw, generateLotteryNumbers, getAvailableGames } from '@/lib/nexa-core/lottery';

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

    try {
        switch (action) {
            case 'results': {
                if (!game) {
                    return NextResponse.json({ error: 'Missing game parameter. Use ?game=us_powerball' }, { status: 400, headers: corsHeaders });
                }
                const results = await getLotteryResults(game);
                return NextResponse.json({ result: results, game }, { headers: corsHeaders });
            }

            case 'next_draw': {
                if (!game) {
                    return NextResponse.json({ error: 'Missing game parameter' }, { status: 400, headers: corsHeaders });
                }
                const nextDraw = await getNextDraw(game);
                return NextResponse.json({ result: nextDraw, game }, { headers: corsHeaders });
            }

            case 'numbers': {
                if (!game) {
                    return NextResponse.json({ error: 'Missing game parameter' }, { status: 400, headers: corsHeaders });
                }
                const numbers = await generateLotteryNumbers(game);
                return NextResponse.json({ result: numbers, game }, { headers: corsHeaders });
            }

            case 'games': {
                const gamesList = getAvailableGames();
                const gamesData = Object.entries(LOTTERY_GAMES).map(([key, g]) => ({
                    key,
                    id: g.id,
                    name: g.name,
                    country: g.country,
                    numbers: g.numbers,
                    maxNumber: g.maxNumber,
                    bonusNumbers: g.bonusNumbers,
                }));
                return NextResponse.json({ result: gamesList, games: gamesData }, { headers: corsHeaders });
            }

            default:
                return NextResponse.json({
                    error: 'Invalid action',
                    available_actions: ['results', 'next_draw', 'numbers', 'games'],
                    example: '/api/lottery?action=results&game=us_powerball',
                    games: Object.fromEntries(Object.entries(LOTTERY_GAMES).map(([k, v]) => [k, v.id])),
                }, { status: 400, headers: corsHeaders });
        }
    } catch (error: any) {
        return NextResponse.json({ error: error.message }, { status: 500, headers: corsHeaders });
    }
}
