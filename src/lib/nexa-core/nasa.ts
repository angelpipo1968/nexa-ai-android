/**
 * NEXA CORE — Servicio Espacial (NASA)
 * Proporciona imágenes astronómicas y datos del universo.
 */

export async function getNASAAPOD(): Promise<string> {
    const apiKey = process.env.NASA_API_KEY || 'DEMO_KEY';
    
    try {
        const url = `https://api.nasa.gov/planetary/apod?api_key=${apiKey}`;
        const res = await fetch(url);
        const data = await res.json();

        if (!res.ok) return "No pude conectar con la base de datos de la NASA.";

        const report = `FOTO ASTRONÓMICA DEL DÍA:
Título: ${data.title}
Fecha: ${data.date}
Explicación: ${data.explanation}
Imagen: ${data.url}`;

        return report;
    } catch (error: any) {
        return `Error al consultar la NASA: ${error.message}`;
    }
}

export async function searchMarsPhotos(): Promise<string> {
    const apiKey = process.env.NASA_API_KEY || 'DEMO_KEY';
    try {
        const url = `https://api.nasa.gov/mars-photos/api/v1/rovers/curiosity/latest_photos?api_key=${apiKey}`;
        const res = await fetch(url);
        const data = await res.json();
        
        if (!res.ok || !data.latest_photos) return "No hay fotos recientes de Marte disponibles.";
        
        const photo = data.latest_photos[0];
        return `FOTO RECIENTE DE MARTE:
Rover: ${photo.rover.name}
Fecha: ${photo.earth_date}
Cámara: ${photo.camera.full_name}
Imagen: ${photo.img_src}`;
    } catch (error: any) {
        return `Error al buscar fotos de Marte: ${error.message}`;
    }
}
