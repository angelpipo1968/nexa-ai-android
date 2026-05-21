/**
 * NEXA CORE — Servicio de Vuelos (Skyscanner via RapidAPI)
 * Permite buscar precios reales y disponibilidad de vuelos.
 * v2: Deep links clicables, formato mejorado, links de reserva directos.
 */

export async function searchSkyscannerFlights(origin: string, destination: string, date: string): Promise<string> {
    const apiKey = process.env.RAPIDAPI_KEY;
    if (!apiKey) return "Falta RAPIDAPI_KEY.";

    try {
        // First, get the Sky IDs for origin and destination
        const originSkyId = await getSkyId(origin, apiKey);
        const destSkyId = await getSkyId(destination, apiKey);
        
        if (!originSkyId || !destSkyId) {
            // Fallback: try direct search with IATA codes
            return await searchFlightsDirect(origin, destination, date, apiKey);
        }

        const url = `https://sky-scrapper.p.rapidapi.com/api/v2/flights/searchFlights?originSkyId=${originSkyId.skyId}&destinationSkyId=${destSkyId.skyId}&originEntityId=${originSkyId.entityId}&destinationEntityId=${destSkyId.entityId}&date=${date}&currency=USD&market=en-US&locale=en-US&adults=1&cabinClass=economy`;
        
        const res = await fetch(url, {
            method: 'GET',
            headers: {
                'X-RapidAPI-Key': apiKey,
                'X-RapidAPI-Host': 'sky-scrapper.p.rapidapi.com'
            }
        });

        const data = await res.json();
        
        if (!res.ok || !data.data || !data.data.itineraries || data.data.itineraries.length === 0) {
            // Fallback to v1 API or direct search
            return await searchFlightsDirect(origin, destination, date, apiKey);
        }

        // Show up to 6 options sorted by price
        const itineraries = data.data.itineraries
            .sort((a: any, b: any) => {
                const priceA = parseFloat(a.price?.raw?.toString() || '999999');
                const priceB = parseFloat(b.price?.raw?.toString() || '999999');
                return priceA - priceB;
            })
            .slice(0, 6);

        let report = `✈️ **SKYSCANNER** (${origin} → ${destination}) — ${date}\n\n`;

        itineraries.forEach((flight: any, i: number) => {
            const price = flight.price?.formatted || 'Precio no disponible';
            const leg = flight.legs?.[0];
            const carriers = leg?.carriers?.marketing || [];
            const airlineNames = carriers.map((c: any) => c.name).join(' + ') || 'Aerolínea desconocida';
            const depTime = leg?.departure ? new Date(leg.departure).toLocaleTimeString('es', { hour: '2-digit', minute: '2-digit' }) : '?';
            const arrTime = leg?.arrival ? new Date(leg.arrival).toLocaleTimeString('es', { hour: '2-digit', minute: '2-digit' }) : '?';
            const duration = leg?.durationInMinutes ? `${Math.floor(leg.durationInMinutes / 60)}h ${leg.durationInMinutes % 60}m` : '';
            const stops = leg?.stopCount === 0 ? '✅ Directo' : `${leg?.stopCount} escala(s)`;

            report += `**${i + 1}. ${airlineNames}**\n`;
            report += `   💰 **${price}**\n`;
            report += `   🕐 ${depTime} → ${arrTime} (${duration})\n`;
            report += `   📍 ${stops}\n`;

            // Deep link - use the Skyscanner booking URL if available
            const deepLink = flight.bookingUrl || flight.deepLink;
            if (deepLink) {
                report += `   🔗 [**Reservar en Skyscanner →**](${deepLink})\n`;
            } else {
                // Fallback: build Skyscanner URL
                const skyDate = date.replace(/-/g, '').slice(2);
                const skyUrl = `https://www.skyscanner.com/transport/flights/${origin}/${destination}/${skyDate}/?adultsv2=1&cabinclass=economy&childrenv2=&ref=home&rtn=0&preferdirects=false&outboundaltsen498=false&inboundaltsenabled=false`;
                report += `   🔗 [**Ver en Skyscanner →**](${skyUrl})\n`;
            }
            report += `\n`;
        });

        // General Skyscanner link
        const skyDate = date.replace(/-/g, '').slice(2);
        const generalUrl = `https://www.skyscanner.com/transport/flights/${origin}/${destination}/${skyDate}/?adultsv2=1&cabinclass=economy&childrenv2=&ref=home&rtn=0&preferdirects=false&outboundaltsen498=false&inboundaltsenabled=false`;
        report += `---\n`;
        report += `🔎 [**Comparar todos los precios en Skyscanner →**](${generalUrl})\n`;

        return report;
    } catch (error: any) {
        return `Error al consultar Skyscanner: ${error.message}`;
    }
}

/**
 * Get Sky ID for an airport IATA code (needed for v2 API)
 */
async function getSkyId(iata: string, apiKey: string): Promise<{ skyId: string; entityId: string } | null> {
    try {
        const url = `https://sky-scrapper.p.rapidapi.com/api/v1/flights/searchAirport?query=${iata}&locale=en-US`;
        const res = await fetch(url, {
            method: 'GET',
            headers: {
                'X-RapidAPI-Key': apiKey,
                'X-RapidAPI-Host': 'sky-scrapper.p.rapidapi.com'
            }
        });
        const data = await res.json();
        
        if (data.data && data.data.length > 0) {
            // Find exact IATA match
            const exactMatch = data.data.find((item: any) => item.skyId === iata.toUpperCase());
            const firstResult = exactMatch || data.data[0];
            return {
                skyId: firstResult.skyId,
                entityId: firstResult.entityId
            };
        }
        return null;
    } catch {
        return null;
    }
}

/**
 * Fallback: Search flights using v1 API directly with IATA codes
 */
async function searchFlightsDirect(origin: string, destination: string, date: string, apiKey: string): Promise<string> {
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
            return `No encontré vuelos de ${origin} a ${destination} para el ${date} en Skyscanner.`;
        }

        const itineraries = data.data.itineraries
            .sort((a: any, b: any) => {
                const priceA = parseFloat(a.price?.raw?.toString() || a.price?.formatted?.replace(/[^0-9.]/g, '') || '999999');
                const priceB = parseFloat(b.price?.raw?.toString() || b.price?.formatted?.replace(/[^0-9.]/g, '') || '999999');
                return priceA - priceB;
            })
            .slice(0, 6);

        let report = `✈️ **SKYSCANNER** (${origin} → ${destination}) — ${date}\n\n`;

        itineraries.forEach((flight: any, i: number) => {
            const price = flight.price?.formatted || 'Precio no disponible';
            const leg = flight.legs?.[0];
            const airline = leg?.carriers?.marketing?.[0]?.name || 'Aerolínea desconocida';
            const depTime = leg?.departure ? new Date(leg.departure).toLocaleTimeString('es', { hour: '2-digit', minute: '2-digit' }) : '?';
            const arrTime = leg?.arrival ? new Date(leg.arrival).toLocaleTimeString('es', { hour: '2-digit', minute: '2-digit' }) : '?';
            const duration = leg?.durationInMinutes ? `${Math.floor(leg.durationInMinutes / 60)}h ${leg.durationInMinutes % 60}m` : '';
            const stops = leg?.stopCount === 0 ? '✅ Directo' : `${leg?.stopCount} escala(s)`;

            report += `**${i + 1}. ${airline}**\n`;
            report += `   💰 **${price}**\n`;
            report += `   🕐 ${depTime} → ${arrTime} (${duration})\n`;
            report += `   📍 ${stops}\n`;

            const skyDate = date.replace(/-/g, '').slice(2);
            const skyUrl = `https://www.skyscanner.com/transport/flights/${origin}/${destination}/${skyDate}/?adultsv2=1&cabinclass=economy&childrenv2=&ref=home&rtn=0&preferdirects=false&outboundaltsen498=false&inboundaltsenabled=false`;
            report += `   🔗 [**Ver en Skyscanner →**](${skyUrl})\n\n`;
        });

        const skyDate = date.replace(/-/g, '').slice(2);
        const generalUrl = `https://www.skyscanner.com/transport/flights/${origin}/${destination}/${skyDate}/?adultsv2=1&cabinclass=economy&childrenv2=&ref=home&rtn=0&preferdirects=false&outboundaltsen498=false&inboundaltsenabled=false`;
        report += `---\n`;
        report += `🔎 [**Comparar todos los precios en Skyscanner →**](${generalUrl})\n`;

        return report;
    } catch (error: any) {
        return `No encontré vuelos en Skyscanner: ${error.message}`;
    }
}
