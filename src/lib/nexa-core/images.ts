/**
 * NEXA CORE — Servicio de Generación de Imágenes (OpenAI DALL-E 3)
 */

export async function generateImage(prompt: string): Promise<string> {
    const apiKey = process.env.OPENAI_API_KEY;
    if (!apiKey) return "Error: No hay API Key de OpenAI configurada.";

    try {
        const response = await fetch('https://api.openai.com/v1/images/generations', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${apiKey}`
            },
            body: JSON.stringify({
                model: "dall-e-2",
                prompt: prompt,
                n: 1,
                size: "1024x1024"
            })
        });

        const data = await response.json();
        if (data.error) return `Error de DALL-E: ${data.error.message}`;

        const imageUrl = data.data[0].url;
        return `IMAGEN GENERADA EXITOSAMENTE:\nAquí tienes el enlace a la imagen que diseñé para ti: ${imageUrl}\n\n(Puedes verla abriendo el enlace o yo puedo describirla por ti).`;
    } catch (error: any) {
        return `Error al generar imagen: ${error.message}`;
    }
}

export async function searchPhotos(query: string): Promise<string> {
    const accessKey = process.env.UNSPLASH_ACCESS_KEY;
    if (!accessKey) return "Error: No hay Access Key de Unsplash configurada.";

    try {
        const response = await fetch(`https://api.unsplash.com/search/photos?query=${encodeURIComponent(query)}&per_page=3&orientation=landscape`, {
            headers: { 'Authorization': `Client-ID ${accessKey}` }
        });

        const data = await response.json();
        if (data.errors) return `Error de Unsplash: ${data.errors[0]}`;

        const photos = data.results;
        if (photos.length === 0) return `No encontré fotos para "${query}" en Unsplash.`;

        const results = photos.map((p: any) => 
            `- ${p.description || p.alt_description || 'Sin título'} (por ${p.user.name})\n  Foto: ${p.urls.regular}`
        ).join('\n');

        return `FOTOS DE ALTA CALIDAD ENCONTRADAS (Unsplash):\n${results}`;
    } catch (error: any) {
        return `Error al buscar fotos: ${error.message}`;
    }
}
