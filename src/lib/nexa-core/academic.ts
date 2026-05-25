/**
 * NEXA CORE — Academic Hub
 * Conecta con ArXiv (Ciencia) y Gutendex (Libros Clásicos).
 */

// 1. ARXIV (Ciencia y Artículos)
export async function searchArXiv(query: string): Promise<string> {
    try {
        // ArXiv devuelve XML, pero podemos usar un buscador que devuelva datos limpios o parsear
        // Por simplicidad en este módulo, usamos el buscador de exportación de ArXiv
        const url = `https://export.arxiv.org/api/query?search_query=all:${encodeURIComponent(query)}&start=0&max_results=3`;
        const res = await fetch(url);
        const xml = await res.text();

        // Extracción básica de títulos y links del XML (Regex simple para evitar dependencias pesadas)
        const titles = xml.match(/<title>([\s\S]*?)<\/title>/g)?.slice(1, 4) || [];
        const summaries = xml.match(/<summary>([\s\S]*?)<\/summary>/g)?.slice(0, 3) || [];

        if (titles.length === 0) return `No encontré artículos científicos para "${query}".`;

        const results = titles.map((t, i) => {
            const cleanTitle = t.replace(/<\/?title>/g, '').trim();
            const cleanSummary = summaries[i]?.replace(/<\/?summary>/g, '').trim().substring(0, 200) + '...';
            return `- ${cleanTitle}\n  Resumen: ${cleanSummary}`;
        }).join('\n\n');

        return `ARTÍCULOS CIENTÍFICOS (ArXiv):\n${results}`;
    } catch {
        return "Error al conectar con ArXiv.";
    }
}

// 2. GUTENDEX (Libros Clásicos - Project Gutenberg)
export async function searchBooks(query: string): Promise<string> {
    try {
        const url = `https://gutendex.com/books/?search=${encodeURIComponent(query)}`;
        const res = await fetch(url);
        const data = await res.json();

        if (!data.results || data.results.length === 0) return `No encontré libros para "${query}" en la biblioteca Gutenberg.`;

        const books = data.results.slice(0, 3).map((b: any) => 
            `- ${b.title} (${b.authors[0]?.name || 'Autor desconocido'})\n  Descargar/Leer: https://www.gutenberg.org/ebooks/${b.id}`
        ).join('\n');

        return `LIBROS CLÁSICOS ENCONTRADOS (Project Gutenberg):\n${books}`;
    } catch {
        return "Error al conectar con la biblioteca Gutenberg.";
    }
}
