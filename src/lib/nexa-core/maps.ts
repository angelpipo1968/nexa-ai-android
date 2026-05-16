/**
 * NEXA CORE — Cartographer Hub
 * Búsqueda de lugares y generación de mapas visuales.
 */

export interface PlaceResult {
    name: string;
    address: string;
    lat: string;
    lon: string;
    mapUrl: string;
    staticImageUrl: string;
}

export async function searchPlace(query: string): Promise<string> {
    try {
        // 1. Buscamos las coordenadas en OpenStreetMap (Nominatim)
        const url = `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(query)}&format=json&limit=1`;
        const res = await fetch(url, {
            headers: { 'User-Agent': 'NexaAssistant/1.0' }
        });
        const data = await res.json();

        if (!data[0]) return `No pude encontrar el lugar "${query}" en el mapa global.`;

        const place = data[0];
        const lat = place.lat;
        const lon = place.lon;
        const name = place.display_name;

        // 2. Generamos links útiles
        const googleMapsUrl = `https://www.google.com/maps/search/?api=1&query=${lat},${lon}`;
        
        // Usamos un servicio de mapas estáticos (Stadia Maps o similar)
        // Por ahora generamos el link para que el frontend lo renderice
        const staticMapUrl = `https://static-maps.yandex.ru/1.x/?lang=es_ES&ll=${lon},${lat}&z=14&l=map&size=600,300&pt=${lon},${lat},pm2rdm`;

        return `LUGAR ENCONTRADO: ${name}
📍 Coordenadas: ${lat}, ${lon}
🗺️ Ver en Google Maps: ${googleMapsUrl}
🖼️ [MAPA VISUAL]: ${staticMapUrl}`;
    } catch (e: any) {
        return `Error en el servicio de mapas: ${e.message}`;
    }
}
