/**
 * NEXA CORE — World Knowledge Hub (Wikidata & DBpedia)
 * El cerebro enciclopédico para datos estructurados y universales.
 */

export async function searchGlobalFacts(query: string): Promise<string> {
    try {
        // Usamos el API de búsqueda de entidades de Wikidata (muy potente para datos exactos)
        const url = `https://www.wikidata.org/w/api.php?action=wbsearchentities&search=${encodeURIComponent(query)}&language=es&format=json&origin=*`;
        const res = await fetch(url);
        const data = await res.json();

        if (!data.search || data.search.length === 0) {
            return `No encontré datos enciclopédicos específicos para "${query}".`;
        }

        const entity = data.search[0];
        const description = entity.description || "Sin descripción disponible.";
        const label = entity.label;
        const id = entity.id;

        return `ENCICLOPEDIA GLOBAL (Wikidata):
📌 Concepto: ${label}
📝 Descripción: ${description}
🔗 Identificador Universal: ${id}
📚 Ficha completa: https://www.wikidata.org/wiki/${id}`;
    } catch {
        return "Error al conectar con la enciclopedia global.";
    }
}
