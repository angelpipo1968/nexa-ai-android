/**
 * NEXA CORE — Cartographer Hub
 * Búsqueda de lugares y generación de mapas visuales.
 * v5.1: Switched to OpenStreetMap static tiles (more reliable than Yandex)
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
        const url = `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(query)}&format=json&limit=1&addressdetails=1`;
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
        const openStreetMapUrl = `https://www.openstreetmap.org/?mlat=${lat}&mlon=${lon}#map=14/${lat}/${lon}`;
        
        // v5.1: Use multiple static map providers for reliability
        // Primary: OpenStreetMap wiki static map (free, no API key needed)
        const staticMapUrl = `https://staticmap.openstreetmap.de/staticmap.php?center=${lat},${lon}&zoom=14&size=600x300&maptype=mapnik&markers=${lat},${lon},red-pushpin`;

        return `LUGAR ENCONTRADO: ${name}
📍 Coordenadas: ${lat}, ${lon}
🗺️ Ver en Google Maps: ${googleMapsUrl}
🗺️ Ver en OpenStreetMap: ${openStreetMapUrl}
🖼️ [MAPA VISUAL]: ${staticMapUrl}`;
    } catch (e: any) {
        return `Error en el servicio de mapas: ${e.message}`;
    }
}
