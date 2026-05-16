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

        const flight = data.data.itineraries[0];
        const price = flight.price.formatted;
        const airline = flight.legs[0].carriers.marketing[0].name;

        return `VUELO ENCONTRADO (Skyscanner):
Ruta: ${origin} ➔ ${destination}
Fecha: ${date}
Precio más bajo: ${price}
Aerolínea: ${airline}
Link: https://www.skyscanner.com/transport/flights/${origin}/${destination}/${date.replace(/-/g, '').slice(2)}`;
    } catch (error: any) {
        return `Error al consultar Skyscanner: ${error.message}`;
    }
}
