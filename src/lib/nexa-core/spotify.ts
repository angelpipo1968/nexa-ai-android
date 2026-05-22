/**
 * NEXA CORE — Spotify Music Hub
 * Busca canciones, álbumes y playlists en Spotify.
 */

import qs from 'qs';

async function getSpotifyToken(): Promise<string | null> {
    const clientId = process.env.SPOTIFY_CLIENT_ID;
    const clientSecret = process.env.SPOTIFY_CLIENT_SECRET;
    if (!clientId || !clientSecret) return null;

    try {
        const auth = Buffer.from(`${clientId}:${clientSecret}`).toString('base64');
        const res = await fetch('https://accounts.spotify.com/api/token', {
            method: 'POST',
            headers: {
                'Authorization': `Basic ${auth}`,
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: qs.stringify({ grant_type: 'client_credentials' })
        });
        const data = await res.json();
        return data.access_token;
    } catch {
        return null;
    }
}

export async function searchSpotify(query: string, type: 'track' | 'playlist' | 'album' = 'track'): Promise<string> {
    const token = await getSpotifyToken();
    if (!token) return "Falta configuración de Spotify (Client ID/Secret).";

    try {
        const url = `https://api.spotify.com/v1/search?q=${encodeURIComponent(query)}&type=${type}&limit=3`;
        const res = await fetch(url, {
            headers: { 'Authorization': `Bearer ${token}` }
        });
        const data = await res.json();

        // En 2026, Spotify puede devolver los resultados en el campo 'items' o dentro del tipo
        const items = data[`${type}s`]?.items || [];

        if (items.length === 0) return `No encontré ${type}s para "${query}" en Spotify.`;

        const results = items.map((item: any) => {
            const name = item.name;
            const artist = item.artists ? item.artists[0].name : '';
            const url = item.external_urls.spotify;
            const id = item.id;
            const embedUrl = `https://open.spotify.com/embed/${type}/${id}?utm_source=generator&theme=0`;
            
            return `- ${name} ${artist ? `por ${artist}` : ''}\n  Link: ${url}\n  [REPRODUCTOR]: ${embedUrl}`;
        }).join('\n');

        return `RESULTADOS DE SPOTIFY (${type.toUpperCase()}):\n${results}`;
    } catch (e: any) {
        return `Error en Spotify: ${e.message}`;
    }
}
