/**
 * NEXA CORE — Servicio de Vuelos (Skyscanner via RapidAPI)
 * Permite buscar precios reales y disponibilidad de vuelos.
 */

export async function searchSkyscannerFlights(origin: string, destination: string, date: string): Promise<string> {
    const apiKey = process.env.RAPIDAPI_KEY;
    if (!apiKey) return "Falta RAPIDAPI_KEY.";

    try {
        const url = `https://sky-scrapper.p.rapidapi.com/api/v1/flights/searchFlights?originSkyId=${origin}&destinationSkyId=${destination}&date=${date}`;
        
        const res = await fetch(url, {
            method: 'GET',
            headers: {
                'X-RapidAPI-Key': apiKey,
                'X-RapidAPI-Host': 'sky-scrapper.p.rapidapi.com'
            }
        });

        const data = await res.json();
        
        if (!res.ok || !data.data || !data.data.itineraries || data.data.itineraries.length === 0) {
            return `No encontré vuelos de ${origin} a ${destination} para el ${date}.`;
        }

        // Mostrar hasta 5 opciones con precio y aerolínea
        const itineraries = data.data.itineraries.slice(0, 5);
        let report = `✈️ VUELOS ENCONTRADOS (${origin} → ${destination}) — ${date}\n\n`;

        itineraries.forEach((flight: any, i: number) => {
            const price = flight.price?.formatted || 'Precio no disponible';
            const leg = flight.legs?.[0];
            const airline = leg?.carriers?.marketing?.[0]?.name || 'Aerolínea desconocida';
            const depTime = leg?.departure ? new Date(leg.departure).toLocaleTimeString('es', { hour: '2-digit', minute: '2-digit' }) : '?';
            const arrTime = leg?.arrival ? new Date(leg.arrival).toLocaleTimeString('es', { hour: '2-digit', minute: '2-digit' }) : '?';
            const duration = leg?.durationInMinutes ? `${Math.floor(leg.durationInMinutes / 60)}h ${leg.durationInMinutes % 60}m` : '';
            const stops = leg?.stopCount === 0 ? 'Directo' : `${leg.stopCount} escala(s)`;

            report += `${i + 1}. ${airline}\n`;
            report += `   💰 ${price}\n`;
            report += `   🕐 ${depTime} → ${arrTime} (${duration})\n`;
            report += `   📍 ${stops}\n`;

            // Link directo a Skyscanner para esta ruta y fecha
            const skyDate = date.replace(/-/g, '').slice(2);
            report += `   🔗 https://www.skyscanner.com/transport/flights/${origin}/${destination}/${skyDate}\n\n`;
        });

        // Link general de Skyscanner
        const skyDate = date.replace(/-/g, '').slice(2);
        report += `🔎 Buscar más opciones: https://www.skyscanner.com/transport/flights/${origin}/${destination}/${skyDate}`;

        return report;
    } catch (error: any) {
        return `Error al consultar Skyscanner: ${error.message}`;
    }
}
