/**
 * NEXA CORE — Servicio de Google Flights (via SerpAPI)
 * Busca vuelos reales con precios, aerolíneas y links de reserva.
 * v2: Links clicables, booking URLs correctas, soporte multi-día.
 */

export interface FlightResult {
    airline: string;
    flightNumber: string;
    price: number | null;
    currency: string;
    departureTime: string;
    arrivalTime: string;
    departureAirport: string;
    arrivalAirport: string;
    duration: string;
    stops: number;
    bookingUrl: string;
    date: string;
}

export interface PriceCalendarDay {
    date: string;
    price: number | null;
    cheapest: boolean;
}

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
        const allFlights = [...bestFlights, ...otherFlights].slice(0, 8);

        if (allFlights.length === 0) {
            return `No encontré vuelos en Google Flights de ${origin} a ${destination} para el ${date}.`;
        }

        // Build the Google Flights search URL for direct booking
        const gfSearchUrl = buildGoogleFlightsUrl(origin, destination, date, returnDate);

        let report = `✈️ **GOOGLE FLIGHTS** (${origin} → ${destination}) — ${date}\n\n`;

        allFlights.forEach((flight: any, i: number) => {
            const price = flight.price ? `$${flight.price} USD` : 'Precio no disponible';
            const totalDuration = flight.total_duration
                ? `${Math.floor(flight.total_duration / 60)}h ${flight.total_duration % 60}m`
                : '';

            report += `**${i + 1}.** `;

            // Mostrar cada tramo del vuelo
            const legs = flight.flights || [];
            const airlines = legs.map((leg: any) => leg.airline || '').filter(Boolean);
            const uniqueAirlines = [...new Set(airlines)];
            
            legs.forEach((leg: any, j: number) => {
                const airline = leg.airline || 'Aerolínea';
                const flightNum = leg.flight_number || '';
                const depTime = leg.departure_airport?.time || '';
                const arrTime = leg.arrival_airport?.time || '';
                const depId = leg.departure_airport?.id || origin;
                const arrId = leg.arrival_airport?.id || destination;

                if (j > 0) report += `   → `;
                report += `**${airline} ${flightNum}** (${depId}→${arrId})\n`;
                if (j === 0) {
                    report += `   💰 **${price}** | 🕐 ${totalDuration}\n`;
                    if (depTime) report += `   🛫 ${depTime}`;
                    if (arrTime) report += ` → 🛬 ${arrTime}`;
                    report += `\n`;
                }
            });

            // Escalas
            const stops = legs.length - 1;
            report += `   📍 ${stops === 0 ? '✅ Directo' : `${stops} escala(s)`}\n`;

            // Link de booking - URL directa a Google Flights para esta ruta
            const bookingUrl = buildGoogleFlightsUrl(origin, destination, date, returnDate, flight);
            report += `   🔗 [**Reservar este vuelo →**](${bookingUrl})\n`;
            report += `\n`;
        });

        // Link general de Google Flights
        report += `---\n`;
        report += `🔎 [**Ver todos los vuelos en Google Flights →**](${gfSearchUrl})\n`;

        // Price calendar info if available in SerpAPI response
        if (data.price_insights) {
            report += `\n💡 **Consejo de precios:** ${data.price_insights}\n`;
        }

        return report;
    } catch (error: any) {
        return `Error al consultar Google Flights: ${error.message}`;
    }
}

/**
 * Busca precios para múltiples días (calendario de precios como Google Flights)
 * Busca ±3 días de la fecha dada para encontrar el día más barato
 */
export async function searchPriceCalendar(
    origin: string,
    destination: string,
    baseDate: string,
    returnDate?: string
): Promise<{ calendar: PriceCalendarDay[]; report: string }> {
    const apiKey = process.env.SERPAPI_KEY || process.env.GOOGLE_FLIGHTS_API_KEY;
    
    if (!apiKey) {
        return { 
            calendar: [], 
            report: "No hay API key configurada para Google Flights." 
        };
    }

    const calendar: PriceCalendarDay[] = [];
    
    // Generate dates: ±3 days from base date
    const baseDateObj = new Date(baseDate);
    const datesToSearch: string[] = [];
    
    for (let offset = -3; offset <= 3; offset++) {
        const d = new Date(baseDateObj);
        d.setDate(d.getDate() + offset);
        const dateStr = d.toISOString().split('T')[0];
        datesToSearch.push(dateStr);
    }

    // Search each date (with rate limiting - max 3 concurrent)
    const results = new Map<string, number | null>();
    const batchSize = 2;
    
    for (let i = 0; i < datesToSearch.length; i += batchSize) {
        const batch = datesToSearch.slice(i, i + batchSize);
        const promises = batch.map(async (date) => {
            try {
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
                    // Adjust return date by same offset
                    const baseReturn = new Date(returnDate);
                    const offset = Math.round((new Date(date).getTime() - baseDateObj.getTime()) / (1000 * 60 * 60 * 24));
                    const adjustedReturn = new Date(baseReturn);
                    adjustedReturn.setDate(adjustedReturn.getDate() + offset);
                    params.set('return_date', adjustedReturn.toISOString().split('T')[0]);
                }

                const res = await fetch(`https://serpapi.com/search.json?${params.toString()}`);
                const data = await res.json();
                
                // Get the lowest price from best_flights
                const allFlights = [...(data.best_flights || []), ...(data.other_flights || [])];
                const prices = allFlights
                    .map((f: any) => f.price)
                    .filter((p: any) => typeof p === 'number');
                
                return { date, price: prices.length > 0 ? Math.min(...prices) : null };
            } catch {
                return { date, price: null };
            }
        });

        const batchResults = await Promise.all(promises);
        batchResults.forEach(r => results.set(r.date, r.price));
        
        // Rate limit: wait 1 second between batches
        if (i + batchSize < datesToSearch.length) {
            await new Promise(resolve => setTimeout(resolve, 1000));
        }
    }

    // Build calendar
    let minPrice = Infinity;
    results.forEach((price) => {
        if (price !== null && price < minPrice) minPrice = price;
    });

    datesToSearch.forEach(date => {
        const price = results.get(date) || null;
        calendar.push({
            date,
            price,
            cheapest: price !== null && price === minPrice
        });
    });

    // Build report
    const dayNames = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];
    const monthNames = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];
    
    let report = `📅 **CALENDARIO DE PRECIOS** (${origin} → ${destination})\n\n`;
    report += `| Fecha | Día | Precio | Mejor |\n`;
    report += `|-------|-----|--------|-------|\n`;

    calendar.forEach(day => {
        const d = new Date(day.date + 'T12:00:00');
        const dayName = dayNames[d.getDay()];
        const monthName = monthNames[d.getMonth()];
        const dateDisplay = `${d.getDate()} ${monthName}`;
        const priceDisplay = day.price !== null ? `$${day.price}` : '—';
        const bestMark = day.cheapest ? '🏆 **MEJOR**' : '';
        
        report += `| ${dateDisplay} | ${dayName} | ${priceDisplay} | ${bestMark} |\n`;
    });

    // Find and highlight cheapest date
    const cheapestDay = calendar.find(d => d.cheapest);
    if (cheapestDay && cheapestDay.price !== null) {
        const d = new Date(cheapestDay.date + 'T12:00:00');
        const monthName = monthNames[d.getMonth()];
        report += `\n🏆 **El día más barato es el ${d.getDate()} de ${monthName} a $${cheapestDay.price} USD**\n`;
        
        const cheapUrl = buildGoogleFlightsUrl(origin, destination, cheapestDay.date, returnDate);
        report += `🔗 [**Ver vuelos del ${d.getDate()} ${monthName} →**](${cheapUrl})\n`;
    }

    return { calendar, report };
}

/**
 * Build a proper Google Flights URL that the user can click to book
 */
function buildGoogleFlightsUrl(
    origin: string, 
    destination: string, 
    date: string, 
    returnDate?: string,
    flight?: any
): string {
    // Google Flights URL format that works directly
    // https://www.google.com/travel/flights?q=Flights%20from%20LAS%20to%20MIA%20on%202025-01-15
    // Or the more precise format:
    // https://www.google.com/travel/flights?curr=USD&tfs=CBwQAhooEgoyMDI1LTAxLTE1agwIAhIIL20vMDFfYnFyDAgCEggvbS8wMWZtNHABSAFwAYIBCwj___________8BmAEC&hl=en
    
    // Simple but effective format that redirects properly
    const searchQuery = returnDate 
        ? `Flights from ${origin} to ${destination} on ${date} returning ${returnDate}`
        : `Flights from ${origin} to ${destination} on ${date}`;
    
    return `https://www.google.com/travel/flights?q=${encodeURIComponent(searchQuery)}&curr=USD&hl=es`;
}
