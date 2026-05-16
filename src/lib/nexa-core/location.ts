/**
 * NEXA CORE — Location & Time Service
 * Detecta la ubicación y hora local del usuario mediante IP.
 */

export interface LocationData {
    city: string;
    country: string;
    timezone: string;
    lat: number;
    lon: number;
    query: string; // IP
}

export async function getUserLocation(): Promise<LocationData | null> {
    try {
        // Usamos ip-api.com (Gratis para uso no comercial)
        const res = await fetch('http://ip-api.com/json/');
        const data = await res.json();
        
        if (data.status === 'fail') return null;

        return {
            city: data.city,
            country: data.country,
            timezone: data.timezone,
            lat: data.lat,
            lon: data.lon,
            query: data.query
        };
    } catch {
        return null;
    }
}

export async function getLocalTime(timezone: string = 'UTC'): Promise<string> {
    try {
        const options: Intl.DateTimeFormatOptions = {
            timeZone: timezone,
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            weekday: 'long',
            year: 'numeric',
            month: 'long',
            day: 'numeric'
        };
        const formatter = new Intl.DateTimeFormat('es-ES', options);
        return formatter.format(new Date());
    } catch {
        return new Date().toLocaleString();
    }
}
