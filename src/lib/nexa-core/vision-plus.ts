/**
 * NEXA VISION PLUS v2
 * Procesamiento avanzado pre-LLM para el pipeline de visión.
 *
 * GLM-4.6V capabilities leveraged:
 * - Document understanding (text extraction, tables, charts)
 * - UI/UX analysis (screenshots, wireframes, mockups)
 * - OCR multi-idioma (detecta idioma automáticamente)
 * - QR / Barcode detection
 * - Math equation recognition (LaTeX output)
 * - Diagram/chart analysis (flowcharts, architecture, etc.)
 * - Code from screenshot recognition
 */

export type VisionCategory =
    | 'general'
    | 'document'
    | 'ui_screenshot'
    | 'qr_barcode'
    | 'math'
    | 'chart_diagram'
    | 'code_screenshot'
    | 'photo'
    | 'meme'
    | 'whiteboard';

/**
 * Detecta la categoría probable de la imagen basándose en la pregunta del usuario.
 * Esto NO analiza la imagen (eso lo hace GLM-4.6V), sino que contextualiza la pregunta.
 */
export function detectVisionCategory(question?: string): VisionCategory {
    if (!question) return 'general';

    const q = question.toLowerCase();

    // QR / Barcode
    if (/\b(qr|barcode|código de barras|escanea|scan)\b/.test(q)) return 'qr_barcode';

    // Math
    if (/\b(ecuación|math|calcular|integral|derivada|fórmula|formula|álgebra|algebra|trigonometría|latex|matemátic)\b/.test(q)) return 'math';

    // Code screenshot
    if (/\b(código|code|error|bug|depura|debug|syntax|compila|compiles|función|function|variable|clase|class|import)\b/.test(q)) return 'code_screenshot';

    // UI / Screenshot
    if (/\b(ui|ux|interfaz|diseño|layout|wireframe|mockup|pantalla|screen|app|web|botón|button|menú|menu|navbar|header|footer)\b/.test(q)) return 'ui_screenshot';

    // Chart / Diagram
    if (/\b(gráfica|chart|diagrama|diagram|flowchart|organigrama|arquitectura|topología|tabla|table|data|datos|estadística)\b/.test(q)) return 'chart_diagram';

    // Document
    if (/\b(documento|document|contrato|contrato|factura|recibo|formulario|form|texto|text|letra|leer|extract|ocr|pdf)\b/.test(q)) return 'document';

    // Meme
    if (/\b(meme|chiste|gracioso|funny|humor)\b/.test(q)) return 'meme';

    // Whiteboard / Handwriting
    if (/\b(pizarra|whiteboard|manuscrito|handwriting|nota|note|dibujo|drawing|boceto|sketch)\b/.test(q)) return 'whiteboard';

    return 'general';
}

/**
 * Genera instrucciones adicionales para GLM-4.6V basadas en la categoría detectada.
 * Estas instrucciones se concatenan al system prompt para especializar el análisis.
 */
export function getCategoryInstructions(category: VisionCategory): string {
    const instructions: Record<VisionCategory, string> = {
        general: '',
        document: `
[DOCUMENT MODE]
- Extrae TODO el texto visible con fidelidad (no resumas, transcribe)
- Identifica la estructura: títulos, subtítulos, párrafos, listas, tablas
- Si hay tablas, reprodúcelas en formato Markdown
- Si hay formularios, identifica campos y valores
- Detecta el idioma del documento y responde en el mismo idioma
- Si hay firmas, sellos o marcas de agua, menciónalo
- Indica si el documento parece incompleto o recortado`,

        ui_screenshot: `
[UI/UX ANALYSIS MODE]
- Identifica el tipo: mobile app, web desktop, tablet, smartwatch
- Detecta framework/tecnología si es posible (React, Flutter, SwiftUI, etc.)
- Evalúa: jerarquía visual, espaciado, tipografía, colores, iconos
- Identifica patrones de navegación (tabs, drawer, bottom bar, hamburger)
- Lista todos los elementos interactivos visibles (botones, inputs, toggles, etc.)
- Sugiere mejoras específicas de UX/accessibilidad (WCAG)
- Valora el contraste de colores y legibilidad
- Si hay texto, transcríbelo completamente`,

        qr_barcode: `
[QR/BARCODE MODE]
- Busca códigos QR en toda la imagen (pueden estar en cualquier posición)
- Si detectas un QR, extrae la URL o texto codificado
- Formatea la salida como: [QR DETECTADO] contenido_del_qr
- Busca también códigos de barras (EAN, UPC, Code128, etc.)
- Si hay múltiples QR/barcodes, lista todos`,

        math: `
[MATH MODE]
- Identifica todas las expresiones matemáticas en la imagen
- Transcríbelas en formato LaTeX entre $...$ (inline) o $$...$$ (block)
- Si es una ecuación a resolver, resuélvela paso a paso
- Si hay gráficas, describe la función y sus propiedades
- Identifica símbolos especiales (sumatorias, integrales, matrices, etc.)
- Responde en español explicando cada paso`,

        chart_diagram: `
[CHART/DIAGRAM MODE]
- Identifica el tipo de gráfica/diagrama (barras, líneas, pastel, flujo, etc.)
- Extrae TODOS los datos visibles (números, etiquetas, ejes)
- Reproduce los datos en formato Markdown (tabla) cuando sea posible
- Analiza tendencias, patrones y puntos destacados
- Si es un diagrama de flujo, describe el proceso paso a paso
- Si es arquitectura, describe los componentes y sus relaciones
- Sugiere mejoras en la visualización si aplica`,

        code_screenshot: `
[CODE ANALYSIS MODE]
- Transcribe el código fuente visible con precisión absoluta
- Identifica el lenguaje de programación
- Si hay errores visibles (syntax errors, red squiggly lines), señálalos
- Explica qué hace el código
- Si hay un error/bug, propón la corrección
- Sugiere mejoras (best practices, optimización, legibilidad)
- Mantén la indentación original al transcribir`,

        photo: `
[PHOTO ANALYSIS MODE]
- Describe la escena en detalle (sujeto, entorno, iluminación, composición)
- Identifica objetos, personas, texturas, colores predominantes
- Estima la hora del día, clima, ubicación si es deducible
- Analiza la composición fotográfica (regla de tercios, simetría, etc.)
- Evalúa la calidad técnica (nitidez, exposición, ruido)
- Si hay texto visible, transcríbelo`,

        meme: `
[MEME MODE]
- Identifica la plantilla del meme si es reconocible
- Transcribe el texto exacto del meme
- Explica el contexto/humor del meme
- Responde con humor si aplica, manteniendo la personalidad de NEXA`,

        whiteboard: `
[WHITEBOARD MODE]
- Identifica si es escritura a mano o impresa
- Transcribe todo el contenido legible
- Identifica diagramas, flechas, conexiones entre conceptos
- Organiza la información de forma estructurada
- Si hay ideas sueltas, intenta agruparlas por tema`,
    };

    return instructions[category] || '';
}

/**
 * Procesamiento avanzado de visión: detecta categoría y genera instrucciones
 * especializadas para GLM-4.6V.
 */
export async function processAdvancedVision(
    base64Image: string,
    question?: string
): Promise<string> {
    const category = detectVisionCategory(question);
    const categoryInstructions = getCategoryInstructions(category);

    // Base QR instruction (always present for any image)
    const baseInstruction =
        'Si en la imagen hay un código QR o barcode, extrae la URL o texto y muéstralo con el prefijo [QR DETECTADO].';

    if (category === 'general' && !categoryInstructions) {
        return baseInstruction;
    }

    return `${baseInstruction}\n${categoryInstructions}`.trim();
}

/**
 * Valida si una cadena base64 parece ser una imagen válida.
 */
export function isValidImageBase64(base64: string): boolean {
    if (base64.length < 100) return false;
    // Check for common image data URI prefixes or raw base64 patterns
    const clean = base64.replace(/^data:image\/\w+;base64,/, '');
    // Base64 images should be at least a few hundred chars and valid base64
    return /^[A-Za-z0-9+/=]+$/.test(clean) && clean.length > 100;
}

/**
 * Convierte una URL de imagen a base64 si es necesario.
 * Útil cuando el usuario envía una URL en lugar de un archivo.
 */
export async function imageUrlToBase64(url: string): Promise<{ base64: string; mimeType: string }> {
    try {
        const res = await fetch(url, { signal: AbortSignal.timeout(15000) });
        if (!res.ok) throw new Error(`Fetch failed: ${res.status}`);

        const contentType = res.headers.get('content-type') || 'image/jpeg';
        const buffer = await res.arrayBuffer();
        const base64 = Buffer.from(buffer).toString('base64');

        return { base64, mimeType: contentType };
    } catch (error) {
        throw new Error(`No se pudo descargar la imagen: ${error instanceof Error ? error.message : String(error)}`);
    }
}
