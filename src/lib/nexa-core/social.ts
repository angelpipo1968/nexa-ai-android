/**
 * NEXA CORE — Social Hub
 * Conecta con Reddit y YouTube para obtener tendencias y contenido.
 */

// 1. REDDIT (Tendencias y Búsqueda)
export async function searchReddit(topic: string): Promise<string> {
    try {
        // Usamos el endpoint público de Reddit (.json) que es gratis y rápido
        const url = `https://www.reddit.com/r/${topic}/hot.json?limit=3`;
        const res = await fetch(url, {
            headers: { 'User-Agent': 'NexaBot/1.0' }
        });
        const data = await res.json();

        if (!data.data || !data.data.children) return `No encontré hilos recientes en r/${topic}.`;

        const posts = data.data.children.map((p: any) => 
            `- [${p.data.title}](https://reddit.com${p.data.permalink}) (👍${p.data.ups})`
        ).join('\n');

        return `TENDENCIAS EN REDDIT (r/${topic}):\n${posts}`;
    } catch {
        return "Error al conectar con Reddit.";
    }
}

// 2. YOUTUBE (Búsqueda de videos)
export async function searchYouTube(query: string): Promise<string> {
    const apiKey = process.env.YOUTUBE_API_KEY;
    if (!apiKey) return "Falta YOUTUBE_API_KEY.";

    try {
        const url = `https://www.googleapis.com/youtube/v3/search?part=snippet&q=${encodeURIComponent(query)}&maxResults=3&type=video&key=${apiKey}`;
        const res = await fetch(url);
        const data = await res.json();

        if (!data.items || data.items.length === 0) return `No encontré videos para "${query}".`;

        const videos = data.items.map((v: any) => 
            `- ${v.snippet.title}\n  https://www.youtube.com/watch?v=${v.id.videoId}`
        ).join('\n');

        return `VIDEOS DE YOUTUBE ENCONTRADOS:\n${videos}`;
    } catch (e: any) {
        return `Error en YouTube: ${e.message}`;
    }
}
