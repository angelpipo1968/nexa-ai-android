/**
 * NEXA CORE — Enciclopedia Universal
 * Conecta con Wikipedia, Diccionarios y Base de datos de Países.
 */

// 1. WIKIPEDIA
export async function searchWikipedia(query: string): Promise<string> {
    try {
        const url = `https://es.wikipedia.org/api/rest_v1/page/summary/${encodeURIComponent(query.replace(/ /g, '_'))}`;
        const res = await fetch(url);
        const data = await res.json();
        
        if (data.type === 'disambiguation') return `Wikipedia encontró varios resultados para "${query}". Por favor, sé más específico.`;
        if (!data.extract) return `No encontré información sobre "${query}" en Wikipedia.`;

        return `WIKIPEDIA: ${data.extract}\nFuente: ${data.content_urls.desktop.page}`;
    } catch {
        return "Error al consultar Wikipedia.";
    }
}

// 2. DICCIONARIO
// @internal — not imported externally
export async function getDictionaryDefinition(word: string): Promise<string> {
    try {
        const url = `https://api.dictionaryapi.dev/api/v2/entries/en/${word}`; // Nota: Esta API es mayormente en inglés
        const res = await fetch(url);
        const data = await res.json();
        
        if (!data[0]) return `No encontré la definición de "${word}".`;
        
        const def = data[0].meanings[0].definitions[0].definition;
        return `DICCIONARIO (${word}): ${def}`;
    } catch {
        return "Error al consultar el diccionario.";
    }
}

// 3. DATOS DE PAÍSES
export async function getCountryData(country: string): Promise<string> {
    try {
        const url = `https://restcountries.com/v3.1/name/${country}?fullText=true`;
        const res = await fetch(url);
        const data = await res.json();
        
        if (!data[0]) return `No encontré datos sobre el país "${country}".`;
        
        const c = data[0];
        return `DATOS DE PAÍS (${c.name.common}):
Capital: ${c.capital?.[0]}
Población: ${c.population.toLocaleString()}
Continente: ${c.continents?.[0]}
Moneda: ${(Object.values(c.currencies || {})[0] as any)?.name || 'N/A'}`;
    } catch {
        return "Error al consultar datos del país.";
    }
}

// 4. VOCABULARIO MODERNO (Urban Dictionary)
// @internal — not imported externally
export async function searchSlang(word: string): Promise<string> {
    try {
        const url = `https://api.urbandictionary.com/v0/define?term=${encodeURIComponent(word)}`;
        const res = await fetch(url);
        const data = await res.json();
        
        if (!data.list || data.list.length === 0) return `No encontré jerga o vocabulario informal para "${word}".`;
        
        const entry = data.list[0];
        return `VOCABULARIO MODERNO (${word}):
Definición: ${entry.definition.replace(/[\[\]]/g, '')}
Ejemplo: ${entry.example.replace(/[\[\]]/g, '')}`;
    } catch {
        return "Error al consultar el diccionario de jerga.";
    }
}
