/**
 * NEXA CORE — Servicio de Aviación (AviationStack)
 * Permite buscar vuelos en tiempo real para alimentar al modelo.
 * v2: Formato mejorado, links a aerolíneas y booking.
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

// Map of common airline names to their booking websites
const AIRLINE_BOOKING_URLS: Record<string, string> = {
    'american airlines': 'https://www.aa.com',
    'delta air lines': 'https://www.delta.com',
    'delta': 'https://www.delta.com',
    'united airlines': 'https://www.united.com',
    'southwest airlines': 'https://www.southwest.com',
    'jetblue': 'https://www.jetblue.com',
    'jetblue airways': 'https://www.jetblue.com',
    'spirit airlines': 'https://www.spirit.com',
    'frontier airlines': 'https://www.flyfrontier.com',
    'alaska airlines': 'https://www.alaskaair.com',
    'hawaiian airlines': 'https://www.hawaiianairlines.com',
    'aeromexico': 'https://www.aeromexico.com',
    'aeroméxico': 'https://www.aeromexico.com',
    'interjet': 'https://www.interjet.com',
    'volaris': 'https://www.volaris.com',
    'avianca': 'https://www.avianca.com',
    'copa airlines': 'https://www.copaair.com',
    'latam': 'https://www.latam.com',
    'latam airlines': 'https://www.latam.com',
    'british airways': 'https://www.britishairways.com',
    'lufthansa': 'https://www.lufthansa.com',
    'air france': 'https://www.airfrance.com',
    'klm': 'https://www.klm.com',
    'iberia': 'https://www.iberia.com',
    'emirates': 'https://www.emirates.com',
    'qatar airways': 'https://www.qatarairways.com',
    'turkish airlines': 'https://www.turkishairlines.com',
    'air canada': 'https://www.aircanada.com',
    'westjet': 'https://www.westjet.com',
    'ryanair': 'https://www.ryanair.com',
    'easyjet': 'https://www.easyjet.com',
    'wizz air': 'https://wizzair.com',
    'norwegian': 'https://www.norwegian.com',
    'vueling': 'https://www.vueling.com',
    'china eastern': 'https://www.ceair.com',
    'china southern': 'https://www.csair.com',
    'air china': 'https://www.airchina.com',
    'ana': 'https://www.ana.co.jp',
    'japan airlines': 'https://www.jal.com',
    'korean air': 'https://www.koreanair.com',
    'singapore airlines': 'https://www.singaporeair.com',
    'cathay pacific': 'https://www.cathaypacific.com',
};

function getAirlineUrl(airlineName: string): string | null {
    const lower = airlineName.toLowerCase().trim();
    for (const [key, url] of Object.entries(AIRLINE_BOOKING_URLS)) {
        if (lower.includes(key) || key.includes(lower)) {
            return url;
        }
    }
    return null;
}

function getStatusEmoji(status: string): string {
    switch (status.toLowerCase()) {
        case 'active': case 'en-route': case 'in-flight': return '🟢';
        case 'landed': case 'arrived': return '🟡';
        case 'scheduled': case 'scheduled ': return '🔵';
        case 'cancelled': return '🔴';
        case 'delayed': return '🟠';
        case 'diverted': return '🟣';
        default: return '⚪';
    }
}

export async function searchFlights(originIata: string, destinationIata: string): Promise<string> {
    const apiKey = process.env.AVIATIONSTACK_API_KEY;
    if (!apiKey) return "Error: No hay API Key configurada para vuelos.";

    try {
        const url = `https://api.aviationstack.com/v1/flights?access_key=${apiKey}&dep_iata=${originIata.toUpperCase()}&arr_iata=${destinationIata.toUpperCase()}&limit=10`;
        
        const response = await fetch(url);
        const data = await response.json();

        if (data.error) {
            return `Error de la API de Aviación: ${data.error.message || data.error.code}`;
        }

        if (!data || !data.data || data.data.length === 0) {
            return `No se encontraron vuelos activos directos registrados hoy entre ${originIata.toUpperCase()} y ${destinationIata.toUpperCase()}. (Nota: AviationStack muestra principalmente vuelos comerciales del día actual o programados).`;
        }

        const flights: FlightInfo[] = data.data;
        let report = `📡 **VUELOS EN TIEMPO REAL** (${originIata.toUpperCase()} → ${destinationIata.toUpperCase()})\n`;
        report += `Total encontrados: ${data.pagination?.total || flights.length}\n\n`;

        flights.forEach((f, i) => {
            const statusEmoji = getStatusEmoji(f.flight_status);
            const airlineUrl = getAirlineUrl(f.airline.name);
            
            report += `**${i + 1}. ${f.airline.name} — Vuelo ${f.flight.number}**\n`;
            report += `   ${statusEmoji} Estado: **${f.flight_status.toUpperCase()}**\n`;
            
            const depScheduled = f.departure.scheduled ? new Date(f.departure.scheduled).toLocaleTimeString('es', { hour: '2-digit', minute: '2-digit' }) : '—';
            const arrScheduled = f.arrival.scheduled ? new Date(f.arrival.scheduled).toLocaleTimeString('es', { hour: '2-digit', minute: '2-digit' }) : '—';
            
            report += `   🛫 ${f.departure.iata} (${depScheduled}) → 🛬 ${f.arrival.iata} (${arrScheduled})\n`;
            
            if (airlineUrl) {
                report += `   🔗 [**Web de ${f.airline.name} →**](${airlineUrl})\n`;
            }
            report += `\n`;
        });

        return report;
    } catch (error: any) {
        return `Error técnico al consultar vuelos: ${error.message}`;
    }
}
