/**
 * NEXA CORE — News Hub
 * Conecta con la actualidad mundial en tiempo real.
 */

export async function searchNews(query: string): Promise<string> {
    const apiKey = process.env.NEWS_API_KEY;
    if (!apiKey) return "No tengo acceso a las noticias (falta NEWS_API_KEY).";

    try {
        // Buscamos noticias recientes (últimas 24h/semana)
        const url = `https://newsapi.org/v2/everything?q=${encodeURIComponent(query)}&language=es&sortBy=publishedAt&pageSize=3&apiKey=${apiKey}`;
        const res = await fetch(url);
        const data = await res.json();

        if (!data.articles || data.articles.length === 0) {
            return `No encontré noticias recientes sobre "${query}".`;
        }

        const news = data.articles.map((a: any) => 
            `- ${a.title} (${a.source.name})\n  "${a.description?.substring(0, 150)}..." \n  Leer más: ${a.url}`
        ).join('\n\n');

        return `NOTICIAS DE ÚLTIMA HORA:\n${news}`;
    } catch {
        return "Error al conectar con el centro de noticias.";
    }
}

export async function getTopHeadlines(country = 'mx'): Promise<string> {
    const apiKey = process.env.NEWS_API_KEY;
    if (!apiKey) return "No tengo acceso a los titulares (falta NEWS_API_KEY).";

    try {
        const url = `https://newsapi.org/v2/top-headlines?country=${country}&pageSize=3&apiKey=${apiKey}`;
        const res = await fetch(url);
        const data = await res.json();

        if (!data.articles || data.articles.length === 0) return "No hay titulares disponibles en este momento.";

        const headlines = data.articles.map((a: any) => `- ${a.title} [${a.source.name}]`).join('\n');
        return `TITULARES DEL MOMENTO (${country.toUpperCase()}):\n${headlines}`;
    } catch {
        return "Error al recuperar los titulares.";
    }
}
