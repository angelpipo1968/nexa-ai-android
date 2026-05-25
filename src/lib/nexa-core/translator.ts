/**
 * NEXA CORE — Universal Translator Hub
 * Traducción profesional en tiempo real para más de 100 idiomas.
 */

export async function translateText(text: string, targetLang: string): Promise<string> {
    try {
        const googleKey = process.env.GOOGLE_API_KEY || process.env.GEMINI_API_KEY;
        if (!googleKey) return "No tengo acceso al motor de traducción (falta GOOGLE_API_KEY).";

        // Usamos Gemini para una traducción contextual superior a la de un diccionario simple
        const url = `https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${googleKey}`;
        
        const prompt = `Actúa como un traductor profesional experto. Traduce el siguiente texto al idioma "${targetLang}". 
Mantén el tono original (formal/informal) y asegúrate de que la traducción sea natural para un hablante nativo. 
Responde ÚNICAMENTE con la traducción, sin explicaciones ni saludos.

Texto a traducir:
"${text}"`;

        const res = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                contents: [{ parts: [{ text: prompt }] }],
                generationConfig: { temperature: 0.1 } // Baja temperatura para máxima precisión
            })
        });

        const data = await res.json();
        return data.candidates?.[0]?.content?.parts?.[0]?.text?.trim() || "No pude completar la traducción.";
    } catch (e) {
        console.error("Error en el traductor:", e);
        return "Error al conectar con el servidor de traducción.";
    }
}

export const LANGUAGE_MAP: Record<string, string> = {
    'inglés': 'en',
    'frances': 'fr',
    'alemán': 'de',
    'italiano': 'it',
    'portugués': 'pt',
    'chino': 'zh',
    'japonés': 'ja',
    'ruso': 'ru',
    'árabe': 'ar',
    'hindi': 'hi'
};
