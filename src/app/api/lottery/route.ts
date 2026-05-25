import { NextRequest, NextResponse } from 'next/server';
import { LOTTERY_GAMES, resolveGameId, getLotteryResults, getNextDraw, generateLotteryNumbers, getAvailableGames } from '@/lib/nexa-core/lottery';

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
    const rawGame = searchParams.get('game');

    try {
        switch (action) {
            case 'results': {
                if (!rawGame) {
                    return NextResponse.json({ error: 'Missing game parameter. Use ?game=powerball or ?game=us_powerball' }, { status: 400, headers: corsHeaders });
                }
                const game = resolveGameId(rawGame);
                const results = await getLotteryResults(rawGame);
                const gameInfo = LOTTERY_GAMES[rawGame] || Object.values(LOTTERY_GAMES).find(g => g.id === game);
                return NextResponse.json({ result: results, game, name: gameInfo?.name }, { headers: corsHeaders });
            }

            case 'next_draw': {
                if (!rawGame) {
                    return NextResponse.json({ error: 'Missing game parameter' }, { status: 400, headers: corsHeaders });
                }
                const game = resolveGameId(rawGame);
                const nextDraw = await getNextDraw(rawGame);
                const gameInfo = LOTTERY_GAMES[rawGame] || Object.values(LOTTERY_GAMES).find(g => g.id === game);
                // Parse next_draw_date from the text result
                const dateMatch = nextDraw.match(/Fecha:\s*(.+)/);
                const jackpotMatch = nextDraw.match(/Jackpot estimado:\s*(.+)/);
                return NextResponse.json({
                    result: nextDraw,
                    next_draw_date: dateMatch ? dateMatch[1].trim() : null,
                    jackpot: jackpotMatch ? jackpotMatch[1].trim() : null,
                    game,
                    name: gameInfo?.name
                }, { headers: corsHeaders });
            }

            case 'numbers': {
                if (!rawGame) {
                    return NextResponse.json({ error: 'Missing game parameter' }, { status: 400, headers: corsHeaders });
                }
                const game = resolveGameId(rawGame);
                const numbersResult = await generateLotteryNumbers(rawGame);
                const gameInfo = LOTTERY_GAMES[rawGame] || Object.values(LOTTERY_GAMES).find(g => g.id === game);
                // Parse numbers from text result for structured response
                const numsMatch = numbersResult.match(/Numeros?:\s*([\d,\s]+)/);
                const bonusMatch = numbersResult.match(/Bonus:\s*([\d,\s]+)/);
                return NextResponse.json({
                    result: numbersResult,
                    numbers: numsMatch ? numsMatch[1].split(',').map((n: string) => n.trim()) : [],
                    bonus: bonusMatch ? bonusMatch[1].split(',').map((n: string) => n.trim()) : [],
                    game,
                    name: gameInfo?.name
                }, { headers: corsHeaders });
            }

            case 'tickets': {
                // Generate multiple tickets for the Android native app
                if (!rawGame) {
                    return NextResponse.json({ error: 'Missing game parameter' }, { status: 400, headers: corsHeaders });
                }
                const ticketCount = Math.min(parseInt(searchParams.get('tickets') || '5'), 10);
                const game = resolveGameId(rawGame);
                const gameInfo = LOTTERY_GAMES[rawGame] || Object.values(LOTTERY_GAMES).find(g => g.id === game);

                const tickets = [];
                for (let i = 0; i < ticketCount; i++) {
                    const ticketResult = await generateLotteryNumbers(rawGame);
                    const numsMatch = ticketResult.match(/Numeros?:\s*([\d,\s]+)/);
                    const bonusMatch = ticketResult.match(/Bonus:\s*([\d,\s]+)/);
                    tickets.push({
                        numbers: numsMatch ? numsMatch[1].split(',').map((n: string) => n.trim()) : [],
                        bonus: bonusMatch ? bonusMatch[1].split(',').map((n: string) => n.trim()) : [],
                    });
                }

                return NextResponse.json({
                    tickets,
                    game,
                    name: gameInfo?.name
                }, { headers: corsHeaders });
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
                    available_actions: ['results', 'next_draw', 'numbers', 'tickets', 'games'],
                    example: '/api/lottery?action=results&game=powerball',
                    games: Object.fromEntries(Object.entries(LOTTERY_GAMES).map(([k, v]) => [k, v.id])),
                }, { status: 400, headers: corsHeaders });
        }
    } catch (error: any) {
        return NextResponse.json({ error: error.message }, { status: 500, headers: corsHeaders });
    }
}
