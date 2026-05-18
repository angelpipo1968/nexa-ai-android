/**
 * NEXA CORE — Servicio de Google Flights (via SerpAPI)
 * Busca vuelos reales con precios, aerolíneas y links de reserva.
 */

export async function searchGoogleFlights(
    origin: string,
    destination: string,
    date: string,
    returnDate?: string
): Promise<string> {
    const apiKey = process.env.SERPAPI_KEY || process.env.GOOGLE_FLIGHTS_API_KEY;
    if (!apiKey) return "No hay API key configurada para Google Flights.";

    try {
        // SerpAPI Google Flights endpoint
        const params = new URLSearchParams({
            engine: 'google_flights',
            departure_id: origin.toUpperCase(),
            arrival_id: destination.toUpperCase(),
            outbound_date: date,
            currency: 'USD',
            hl: 'es',
            api_key: apiKey
        });

        if (returnDate) {
            params.set('return_date', returnDate);
        }

        const res = await fetch(`https://serpapi.com/search.json?${params.toString()}`);
        const data = await res.json();

        if (data.error) {
            return `Error Google Flights: ${data.error}`;
        }

        const bestFlights = data.best_flights || [];
        const otherFlights = data.other_flights || [];
        const allFlights = [...bestFlights, ...otherFlights].slice(0, 6);

        if (allFlights.length === 0) {
            return `No encontré vuelos en Google Flights de ${origin} a ${destination} para el ${date}.`;
        }

        let report = `✈️ GOOGLE FLIGHTS (${origin} → ${destination}) — ${date}\n\n`;

        allFlights.forEach((flight: any, i: number) => {
            const price = flight.price ? `$${flight.price}` : 'Precio no disponible';
            const totalDuration = flight.total_duration
                ? `${Math.floor(flight.total_duration / 60)}h ${flight.total_duration % 60}m`
                : '';

            report += `${i + 1}. `;

            // Mostrar cada tramo del vuelo
            const legs = flight.flights || [];
            legs.forEach((leg: any, j: number) => {
                const airline = leg.airline || 'Aerolínea';
                const flightNum = leg.flight_number || '';
                const depTime = leg.departure_airport?.time || '';
                const arrTime = leg.arrival_airport?.time || '';
                const depId = leg.departure_airport?.id || origin;
                const arrId = leg.arrival_airport?.id || destination;

                if (j > 0) report += `   → `;
                report += `${airline} ${flightNum} (${depId}→${arrId})\n`;
                if (j === 0) {
                    report += `   💰 ${price} | 🕐 ${totalDuration}\n`;
                    if (depTime) report += `   🛫 ${depTime}`;
                    if (arrTime) report += ` → 🛬 ${arrTime}`;
                    report += `\n`;
                }
            });

            // Escalas
            const stops = legs.length - 1;
            report += `   📍 ${stops === 0 ? 'Directo' : `${stops} escala(s)`}\n`;

            // Link de booking
            if (flight.booking_token) {
                report += `   🔗 https://www.google.com/travel/flights?q=${encodeURIComponent(flight.booking_token)}\n`;
            }
            report += `\n`;
        });

        // Link general
        const googleDate = date;
        report += `🔎 Ver más en Google Flights: https://www.google.com/travel/flights?q=Flights%20to%20${destination}%20from%20${origin}%20on%20${googleDate}`;

        return report;
    } catch (error: any) {
        return `Error al consultar Google Flights: ${error.message}`;
    }
}
