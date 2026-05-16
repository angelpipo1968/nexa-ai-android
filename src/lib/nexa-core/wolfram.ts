/**
 * NEXA CORE — Servicio de Wolfram Alpha
 * Proporciona respuestas habladas y datos científicos exactos.
 */

export async function getWolframAnswer(query: string): Promise<string> {
    const appId = process.env.WOLFRAM_APP_ID;
    
    if (!appId) {
        return "Configuración incompleta: Falta WOLFRAM_APP_ID.";
    }

    try {
        // Usamos la Spoken Results API para obtener una respuesta conversacional
        const url = `http://api.wolframalpha.com/v1/spoken?i=${encodeURIComponent(query)}&appid=${appId}&language=es`;
        
        const response = await fetch(url);
        
        if (!response.ok) {
            // Si falla la hablada, intentamos la Short Answers API como respaldo
            const shortUrl = `http://api.wolframalpha.com/v1/result?i=${encodeURIComponent(query)}&appid=${appId}`;
            const shortRes = await fetch(shortUrl);
            if (!shortRes.ok) return "No pude encontrar una respuesta exacta en Wolfram Alpha.";
            return await shortRes.text();
        }
        
        return await response.text();
    } catch (error: any) {
        return `Error al consultar Wolfram Alpha: ${error.message}`;
    }
}
