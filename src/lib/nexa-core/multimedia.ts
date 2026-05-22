/**
 * NEXA CORE — Multimedia & Library Search
 * Busca videos en Pexels y repositorios en GitHub.
 */

// 1. VIDEOS (Pexels)
export async function searchVideos(query: string): Promise<string> {
    const apiKey = process.env.PEXELS_API_KEY;
    if (!apiKey) return "Falta PEXELS_API_KEY.";

    try {
        const url = `https://api.pexels.com/v1/videos/search?query=${encodeURIComponent(query)}&per_page=1`;
        const res = await fetch(url, {
            headers: { 'Authorization': apiKey }
        });
        const data = await res.json();

        if (!data.videos || data.videos.length === 0) return `No encontré videos para "${query}".`;

        const video = data.videos[0];
        return `VIDEO ENCONTRADO: ${video.url}\nVista previa: ${video.image}\nDuración: ${video.duration}s`;
    } catch (e: any) {
        return `Error en Pexels: ${e.message}`;
    }
}

// 2. LIBRERÍAS DE CÓDIGO (GitHub)
export async function searchLibraries(query: string, language: string = ''): Promise<string> {
    try {
        let url = `https://api.github.com/search/repositories?q=${encodeURIComponent(query)}`;
        if (language) url += `+language:${language}`;
        url += '&sort=stars&order=desc';

        const res = await fetch(url, {
            headers: { 'Accept': 'application/vnd.github.v3+json' }
        });
        const data = await res.json();

        if (!data.items || data.items.length === 0) return `No encontré librerías para "${query}".`;

        const repos = data.items.slice(0, 3).map((r: any) => 
            `- ${r.full_name} (⭐${r.stargazers_count}): ${r.description}\n  Link: ${r.html_url}`
        ).join('\n');

        return `LIBRERÍAS RECOMENDADAS EN GITHUB:\n${repos}`;
    } catch (e: any) {
        return `Error en GitHub: ${e.message}`;
    }
}
