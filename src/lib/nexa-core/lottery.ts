/**
 * NEXA CORE — Servicio de Loteria
 * Consulta resultados de sorteos mundiales usando Magayo.
 * v2: Soporte para resultados, proximo sorteo, juegos disponibles, generador de numeros.
 */

// Juegos de loteria soportados por Magayo
export const LOTTERY_GAMES: Record<string, { id: string; name: string; country: string; numbers: number; maxNumber: number; bonusNumbers: number; maxBonus: number }> = {
    powerball: { id: 'us_powerball', name: 'Powerball', country: 'EE.UU.', numbers: 5, maxNumber: 69, bonusNumbers: 1, maxBonus: 26 },
    megamillions: { id: 'us_megamillions', name: 'Mega Millions', country: 'EE.UU.', numbers: 5, maxNumber: 70, bonusNumbers: 1, maxBonus: 25 },
    melate: { id: 'mx_melate', name: 'Melate', country: 'Mexico', numbers: 6, maxNumber: 56, bonusNumbers: 1, maxBonus: 56 },
    chispazo: { id: 'mx_chispazo', name: 'Chispazo', country: 'Mexico', numbers: 5, maxNumber: 28, bonusNumbers: 0, maxBonus: 0 },
    melate_retro: { id: 'mx_melate_retro', name: 'Melate Retro', country: 'Mexico', numbers: 5, maxNumber: 39, bonusNumbers: 1, maxBonus: 39 },
    super_baloto: { id: 'co_baloto', name: 'Super Baloto', country: 'Colombia', numbers: 5, maxNumber: 43, bonusNumbers: 1, maxBonus: 16 },
    loteria_nacional: { id: 'es_loteria_nacional', name: 'Loteria Nacional', country: 'Espana', numbers: 5, maxNumber: 54, bonusNumbers: 1, maxBonus: 9 },
    euromillions: { id: 'eu_euromillions', name: 'EuroMillions', country: 'Europa', numbers: 5, maxNumber: 50, bonusNumbers: 2, maxBonus: 12 },
    lotto: { id: 'uk_lotto', name: 'Lotto', country: 'UK', numbers: 6, maxNumber: 59, bonusNumbers: 1, maxBonus: 59 },
    el_gordo: { id: 'es_el_gordo', name: 'El Gordo', country: 'Espana', numbers: 5, maxNumber: 54, bonusNumbers: 1, maxBonus: 9 },
};

/**
 * Obtiene resultados de un sorteo de loteria usando Magayo API
 */
export async function getLotteryResults(game: string): Promise<string> {
    const apiKey = process.env.MAGAYO_API_KEY;
    if (!apiKey) return "Falta MAGAYO_API_KEY. Configurala en .env.local";

    try {
        const url = `https://www.magayo.com/api/results.php?api_key=${apiKey}&game=${game}`;
        const res = await fetch(url, { signal: AbortSignal.timeout(10000) });
        const data = await res.json();

        if (data.error) return `Error de Magayo: ${data.error}`;

        // Buscar nombre del juego
        const gameInfo = Object.values(LOTTERY_GAMES).find(g => g.id === game);
        const gameName = gameInfo?.name || game.toUpperCase();

        return `RESULTADOS DE LOTERIA (${gameName}):
Fecha del sorteo: ${data.draw_date || 'N/A'}
Numero de sorteo: ${data.draw_number || 'N/A'}
Numeros ganadores: ${data.results || 'N/A'}
Bonus: ${data.bonus || 'N/A'}
Jackpot: ${data.jackpot || 'No disponible'}`;
    } catch (error: any) {
        return `Error al consultar loteria: ${error.message}`;
    }
}

/**
 * Obtiene informacion del proximo sorteo usando Magayo API
 */
export async function getNextDraw(game: string): Promise<string> {
    const apiKey = process.env.MAGAYO_API_KEY;
    if (!apiKey) return "Falta MAGAYO_API_KEY.";

    try {
        const url = `https://www.magayo.com/api/next_draw.php?api_key=${apiKey}&game=${game}`;
        const res = await fetch(url, { signal: AbortSignal.timeout(10000) });
        const data = await res.json();

        if (data.error) return `Error de Magayo: ${data.error}`;

        const gameInfo = Object.values(LOTTERY_GAMES).find(g => g.id === game);
        const gameName = gameInfo?.name || game.toUpperCase();

        return `PROXIMO SORTEO (${gameName}):
Fecha: ${data.next_draw || 'N/A'}
Jackpot estimado: ${data.jackpot || 'No disponible'}`;
    } catch (error: any) {
        return `Error al consultar proximo sorteo: ${error.message}`;
    }
}

/**
 * Genera numeros aleatorios para un juego de loteria usando Magayo API
 * Si la API no esta disponible, genera localmente basandose en las reglas del juego
 */
export async function generateLotteryNumbers(game: string): Promise<string> {
    const apiKey = process.env.MAGAYO_API_KEY;
    const gameInfo = Object.values(LOTTERY_GAMES).find(g => g.id === game);
    const gameName = gameInfo?.name || game.toUpperCase();

    // Intentar primero con la API de Magayo
    if (apiKey) {
        try {
            const url = `https://www.magayo.com/api/numbers.php?api_key=${apiKey}&game=${game}`;
            const res = await fetch(url, { signal: AbortSignal.timeout(10000) });
            const data = await res.json();

            if (!data.error && data.numbers) {
                let result = `NUMEROS RECOMENDADOS (${gameName}):\n`;
                result += `Numeros: ${data.numbers}\n`;
                if (data.bonus) result += `Bonus: ${data.bonus}\n`;
                return result;
            }
        } catch {}
    }

    // Fallback: generar numeros localmente basandose en las reglas del juego
    if (gameInfo) {
        const mainNumbers = generateRandomNumbers(gameInfo.numbers, 1, gameInfo.maxNumber);
        const bonusNumbers = gameInfo.bonusNumbers > 0
            ? generateRandomNumbers(gameInfo.bonusNumbers, 1, gameInfo.maxBonus)
            : [];

        let result = `NUMEROS GENERADOS (${gameName}):\n`;
        result += `Numeros: ${mainNumbers.join(', ')}\n`;
        if (bonusNumbers.length > 0) result += `Bonus: ${bonusNumbers.join(', ')}\n`;
        result += `\nNota: Estos numeros son generados aleatoriamente. Juega responsablemente.`;
        return result;
    }

    // Generacion generica si no se conoce el juego
    const genericNumbers = generateRandomNumbers(6, 1, 49);
    return `NUMEROS GENERADOS (Generico):\nNumeros: ${genericNumbers.join(', ')}\n\nNota: Juego no reconocido. Numeros generados aleatoriamente.`;
}

/**
 * Obtiene la lista de juegos de loteria disponibles
 */
export function getAvailableGames(): string {
    let result = 'JUEGOS DE LOTERIA DISPONIBLES:\n\n';

    const grouped = new Map<string, typeof LOTTERY_GAMES[string][]>();
    for (const game of Object.values(LOTTERY_GAMES)) {
        if (!grouped.has(game.country)) grouped.set(game.country, []);
        grouped.get(game.country)!.push(game);
    }

    for (const [country, games] of grouped) {
        result += `${country}:\n`;
        for (const game of games) {
            result += `  - ${game.name} (di "${game.name.toLowerCase()}")\n`;
        }
        result += '\n';
    }

    result += 'Puedes preguntar por resultados, proximo sorteo o generar numeros para cualquiera de estos juegos.';
    return result;
}

/**
 * Utilidad: generar numeros aleatorios sin repeticion
 */
function generateRandomNumbers(count: number, min: number, max: number): number[] {
    const numbers: number[] = [];
    while (numbers.length < count) {
        const n = Math.floor(Math.random() * (max - min + 1)) + min;
        if (!numbers.includes(n)) numbers.push(n);
    }
    return numbers.sort((a, b) => a - b);
}
