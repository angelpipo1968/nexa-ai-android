/**
 * NEXA CORE — Nature Hub (Encyclopedia of Life)
 * Información detallada sobre especies, plantas y animales.
 */

export async function searchSpecies(query: string): Promise<string> {
    try {
        // 1. Buscamos el ID de la especie en EOL
        const searchUrl = `https://eol.org/api/search/1.0.json?q=${encodeURIComponent(query)}&page=1`;
        const searchRes = await fetch(searchUrl);
        const searchData = await searchRes.json();

        if (!searchData.results || searchData.results.length === 0) {
            return `No encontré información específica sobre "${query}" en la base de datos biológica.`;
        }

        const speciesId = searchData.results[0].id;
        const title = searchData.results[0].title;

        // 2. Obtenemos los detalles de la especie
        const detailUrl = `https://eol.org/api/pages/1.0/${speciesId}.json?taxonomy=true&images_per_page=1&language=es`;
        const detailRes = await fetch(detailUrl);
        const detailData = await detailRes.json();

        const commonName = detailData.scientificName;
        
        return `ESTUDIO BIOLÓGICO: ${title}
🔬 Nombre Científico: ${commonName}
🌿 Fuente: Encyclopedia of Life (EOL)
📖 Más detalles: https://eol.org/pages/${speciesId}`;
    } catch {
        return "Error al conectar con la enciclopedia de la naturaleza.";
    }
}
