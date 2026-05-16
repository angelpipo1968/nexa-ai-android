/**
 * NEXA CORE — Servicio de Aviación (AviationStack)
 * Permite buscar vuelos en tiempo real para alimentar al modelo.
 */

export interface FlightInfo {
    flight_date: string;
    flight_status: string;
    departure: {
        airport: string;
        timezone: string;
        iata: string;
        scheduled: string;
    };
    arrival: {
        airport: string;
        timezone: string;
        iata: string;
        scheduled: string;
    };
    airline: {
        name: string;
    };
    flight: {
        number: string;
    };
}

export async function searchFlights(originIata: string, destinationIata: string): Promise<string> {
    const apiKey = process.env.AVIATIONSTACK_API_KEY;
    if (!apiKey) return "Error: No hay API Key configurada para vuelos.";

    try {
        const url = `http://api.aviationstack.com/v1/flights?access_key=${apiKey}&dep_iata=${originIata.toUpperCase()}&arr_iata=${destinationIata.toUpperCase()}&limit=10`;
        
        const response = await fetch(url);
        const data = await response.json();

        if (data.error) {
            return `Error de la API de Aviación: ${data.error.message || data.error.code}`;
        }

        if (!data || !data.data || data.data.length === 0) {
            return `No se encontraron vuelos activos directos registrados hoy entre ${originIata.toUpperCase()} y ${destinationIata.toUpperCase()}. (Nota: AviationStack muestra principalmente vuelos comerciales del día actual o programados).`;
        }

        const flights: FlightInfo[] = data.data;
        let report = `REPORTE DE VUELOS REALES (${originIata.toUpperCase()} -> ${destinationIata.toUpperCase()}):\n`;
        report += `Total encontrados: ${data.pagination.total}\n\n`;

        flights.forEach((f, i) => {
            report += `${i + 1}. [${f.airline.name}] Vuelo ${f.flight.number}\n`;
            report += `   - Estado: ${f.flight_status.toUpperCase()}\n`;
            report += `   - Salida: ${f.departure.iata} (${f.departure.airport}) a las ${f.departure.scheduled}\n`;
            report += `   - Llegada: ${f.arrival.iata} (${f.arrival.airport}) a las ${f.arrival.scheduled}\n\n`;
        });

        return report;
    } catch (error: any) {
        return `Error técnico al consultar vuelos: ${error.message}`;
    }
}
