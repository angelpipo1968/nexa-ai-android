/**
 * NEXA CORE — Preview Tool
 * Permite a NEXA publicar código y generar enlaces de previsualización.
 */

export async function generateLivePreview(code: string, title: string): Promise<string> {
    try {
        // En un entorno real, el backend llamaría a su propia API de guardado
        // Para simplificar la respuesta a la IA, simulamos la llamada al servidor
        const baseUrl = process.env.NEXT_PUBLIC_BASE_URL || 'https://www.nexa-ai.dev';
        
        // El servidor real usará Redis en la ruta /api/preview/save
        // Aquí solo definimos la instrucción para que el LLM sepa que puede usarlo
        return `[SISTEMA DE PREVIEW]: Activado. 
El código ha sido procesado. Para ver el resultado, el usuario debe abrir el enlace generado por el endpoint /api/preview/save. 
(Simulación de URL: ${baseUrl}/preview/demo-${Math.random().toString(36).substring(7)})`;
    } catch {
        return "Error al conectar con el sistema de despliegue de previews.";
    }
}
