/**
 * NEXA CORE — Servicio de Lotería
 * Consulta resultados de sorteos mundiales usando Magayo.
 */

export async function getLotteryResults(game: string): Promise<string> {
    const apiKey = process.env.MAGAYO_API_KEY;
    if (!apiKey) return "Falta MAGAYO_API_KEY.";

    try {
        const url = `https://www.magayo.com/api/results.php?api_key=${apiKey}&game=${game}`;
        const res = await fetch(url);
        const data = await res.json();
        
        if (data.error) return `Error de Magayo: ${data.error}`;

        return `RESULTADOS LOTERÍA (${game.toUpperCase()}):
Fecha: ${data.draw_date}
Números: ${data.results}
Bonus: ${data.bonus || 'N/A'}`;
    } catch (error: any) {
        return `Error al consultar lotería: ${error.message}`;
    }
}
