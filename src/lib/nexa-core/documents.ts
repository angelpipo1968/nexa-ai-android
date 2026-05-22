// @ts-ignore
import pdf from 'pdf-parse/lib/pdf-parse';

/**
 * NEXA CORE — Document Engine
 * Procesa y extrae información de archivos PDF y texto.
 */

export async function parsePDF(buffer: Buffer): Promise<string> {
    try {
        const data = await pdf(buffer);
        return `CONTENIDO DEL PDF:\n${data.text.substring(0, 5000)}`; // Devolvemos los primeros 5000 caracteres
    } catch (e) {
        console.error("Error al parsear PDF:", e);
        return "Error al leer el archivo PDF.";
    }
}

export async function formatText(text: string, format: 'json' | 'markdown' | 'summary'): Promise<string> {
    // Esta función ayuda a NEXA a convertir texto plano a formatos útiles
    if (format === 'json') return JSON.stringify({ content: text }, null, 2);
    if (format === 'markdown') return `### Texto Formateado\n\n${text}`;
    return text.substring(0, 200) + "...";
}
