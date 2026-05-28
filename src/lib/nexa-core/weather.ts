/**
 * NEXA CORE — Servicio de Clima (wttr.in)
 * Obtiene el clima actual de forma rápida y sin necesidad de keys adicionales (usando wttr.in).
 */

export async function getWeather(city: string): Promise<string> {
    try {
        // Usamos la versión v2 de wttr.in para obtener datos limpios
        const url = `https://wttr.in/${encodeURIComponent(city)}?format=%l:+%C+%t+(Humedad:+%h,+Viento:+%w)`;
        const response = await fetch(url);
        
        if (!response.ok) return `No pude obtener el clima para ${city} en este momento.`;
        
        const data = await response.text();
        return `CLIMA ACTUAL EN ${city.toUpperCase()}:\n${data}`;
    } catch (error: any) {
        return `Error al consultar el clima: ${error.message}`;
    }
}
