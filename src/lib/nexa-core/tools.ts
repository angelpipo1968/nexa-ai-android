// ═══════════════════════════════════════════
//  NEXA CORE — Sistema de Herramientas
// ═══════════════════════════════════════════

export interface ToolResult {
    success: boolean;
    output: string;
    error?: string;
}

// ─── NEXA Tools API ───
const TOOLS_BASE = '/api/tools';

export async function callTool(tool: string, params: Record<string, string> = {}): Promise<any> {
    const searchParams = new URLSearchParams({ tool, ...params });
    const res = await fetch(`${TOOLS_BASE}?${searchParams}`);
    return res.json();
}

// ─── Detección de Intención (expandida) ───
export type UserIntent = 
    | { type: 'code'; language?: string; description: string }
    | { type: 'web'; description: string }
    | { type: 'design'; description: string }
    | { type: 'analysis'; subject: string }
    | { type: 'vision'; hasImage: boolean }
    | { type: 'weather'; city?: string }
    | { type: 'search'; query: string }
    | { type: 'geolocation'; ip?: string }
    | { type: 'geocode'; query?: string; lat?: string; lon?: string }
    | { type: 'exchange'; from?: string; to?: string; amount?: number }
    | { type: 'translate'; text?: string; from?: string; to?: string }
    | { type: 'news'; topic?: string; lang?: string }
    | { type: 'jokes'; category?: string }
    | { type: 'facts'; category?: string }
    | { type: 'time'; timezone?: string }
    | { type: 'qrcode'; text?: string }
    | { type: 'countries'; query?: string }
    | { type: 'chat'; message: string };

export function detectIntent(message: string): UserIntent {
    const lower = message.toLowerCase();
    
    // ─── Code ───
    if (lower.includes('código') || lower.includes('codigo') || lower.includes('code') || 
        lower.includes('función') || lower.includes('script') || lower.includes('programa') ||
        lower.includes('api') || lower.includes('endpoint')) {
        const langMatch = message.match(/(?:python|javascript|typescript|react|html|css|sql|go|rust|java|c\+\+)/i);
        return { type: 'code', language: langMatch?.[0]?.toLowerCase(), description: message };
    }
    
    // ─── Web Design ───
    if (lower.includes('página web') || lower.includes('pagina web') || lower.includes('website') || 
        lower.includes('landing') || lower.includes('portfolio') || lower.includes('sitio web')) {
        return { type: 'web', description: message };
    }
    
    // ─── Design ───
    if (lower.includes('diseño') || lower.includes('logo') || lower.includes('ui') || 
        lower.includes('ux') || lower.includes('interfaz') || lower.includes('mockup')) {
        return { type: 'design', description: message };
    }
    
    // ─── Analysis ───
    if (lower.includes('analiza') || lower.includes('analice') || lower.includes('explica') || 
        lower.includes('por qué') || lower.includes('por que') || lower.includes('cómo funciona')) {
        return { type: 'analysis', subject: message };
    }
    
    // ─── Weather ───
    if (lower.includes('clima') || lower.includes('weather') || lower.includes('temperatura') ||
        lower.includes('lluvia') || lower.includes('pronóstico') || lower.includes('pronostico') ||
        lower.includes('tiempo hace') || lower.includes('va a llover')) {
        const cityMatch = message.match(/(?:en|in|de|del)\s+([A-Za-zÀ-ÿ\s]+?)(?:\?|$|\.)/i);
        return { type: 'weather', city: cityMatch?.[1]?.trim() };
    }
    
    // ─── Search ───
    if (lower.includes('busca') || lower.includes('search') || lower.includes('qué es') || 
        lower.includes('que es') || lower.includes('quién es') || lower.includes('quien es') ||
        lower.includes('define') || lower.includes('significa') || lower.includes('wikipedia')) {
        const query = message.replace(/^(?:busca|search|qué es|que es|quién es|quien es|define|significa|wikipedia)\s*/i, '').trim();
        return { type: 'search', query };
    }
    
    // ─── Geolocation ───
    if (lower.includes('ubicación') || lower.includes('ubicacion') || lower.includes('location') ||
        lower.includes('donde estoy') || lower.includes('dónde estoy') || lower.includes('mi ip') ||
        lower.includes('geolocalización') || lower.includes('geolocalizacion')) {
        return { type: 'geolocation' };
    }
    
    // ─── Geocode ───
    if (lower.includes('coordenadas') || lower.includes('dirección') || lower.includes('direccion') ||
        lower.includes('latitud') || lower.includes('longitud') || lower.includes('geocod')) {
        const query = message.replace(/(?:coordenadas|dirección|direccion|latitud|longitud|geocod)\s*(?:de|of|para|for)?\s*/i, '').trim();
        return { type: 'geocode', query };
    }
    
    // ─── Exchange Rate ───
    if (lower.includes('dólar') || lower.includes('dolar') || lower.includes('euro') || 
        lower.includes('peso') || lower.includes('moneda') || lower.includes('cambio') ||
        lower.includes('exchange') || lower.includes('currency') || lower.includes('convertir')) {
        const fromMatch = message.match(/(\d+)\s*(?:dólares?|dolares?|usd|€|euros?|pesos?|mxn)/i);
        const currencyMatch = message.match(/(?:a|to|en|in)\s*(dólares?|dolares?|usd|€|euros?|pesos?|mxn)/i);
        return { type: 'exchange', amount: fromMatch ? parseFloat(fromMatch[1]) : undefined };
    }
    
    // ─── Translation ───
    if (lower.includes('traduce') || lower.includes('translate') || lower.includes('traducir') ||
        lower.includes('en inglés') || lower.includes('en español') || lower.includes('in english') ||
        lower.includes('in spanish')) {
        const text = message.replace(/(?:traduce|translate|traducir|en inglés|en español|in english|in spanish)\s*/i, '').trim();
        const to = lower.includes('inglés') || lower.includes('english') ? 'en' : 'es';
        return { type: 'translate', text, from: to === 'en' ? 'es' : 'en', to };
    }
    
    // ─── News ───
    if (lower.includes('noticias') || lower.includes('news') || lower.includes('actualidad') ||
        lower.includes('últimas') || lower.includes('ultimas')) {
        const topicMatch = message.match(/(?:noticias|news)\s*(?:de|del|about)?\s*(tecnología|technology|deportes|sports|ciencia|science|general)?/i);
        return { type: 'news', topic: topicMatch?.[1]?.toLowerCase() || 'general' };
    }
    
    // ─── Jokes ───
    if (lower.includes('chiste') || lower.includes('joke') || lower.includes('gracioso') ||
        lower.includes('divertido') || lower.includes('ríe') || lower.includes('rie')) {
        return { type: 'jokes' };
    }
    
    // ─── Facts ───
    if (lower.includes('dato') || lower.includes('fact') || lower.includes('curiosidad') ||
        lower.includes('sabías') || lower.includes('sabias') || lower.includes('trivia')) {
        return { type: 'facts' };
    }
    
    // ─── Time ───
    if (lower.includes('hora') || lower.includes('time') || lower.includes('reloj') ||
        lower.includes('timezone') || lower.includes('zona horaria')) {
        const tzMatch = message.match(/(?:en|in|de)\s+([A-Za-zÀ-ÿ_/]+(?:\/[A-Za-zÀ-ÿ_]+)?)/i);
        return { type: 'time', timezone: tzMatch?.[1] };
    }
    
    // ─── QR Code ───
    if (lower.includes('qr') || lower.includes('código qr') || lower.includes('codigo qr')) {
        const text = message.replace(/(?:qr|código qr|codigo qr)\s*(?:de|de|for|para)?\s*/i, '').trim();
        return { type: 'qrcode', text };
    }
    
    // ─── Countries ───
    if (lower.includes('país') || lower.includes('pais') || lower.includes('country') ||
        lower.includes('población') || lower.includes('poblacion') || lower.includes('capital')) {
        const query = message.replace(/(?:país|pais|country|población|poblacion|capital)\s*(?:de|of|del)?\s*/i, '').trim();
        return { type: 'countries', query };
    }
    
    return { type: 'chat', message };
}

// ─── Ejecutar herramienta según intención ───
export async function executeIntent(intent: UserIntent): Promise<ToolResult> {
    try {
        let result: any;

        switch (intent.type) {
            case 'weather':
                result = await callTool('weather', intent.city ? { city: intent.city } : {});
                break;
            case 'search':
                result = await callTool('search', { q: intent.query });
                break;
            case 'geolocation':
                result = await callTool('geolocation');
                break;
            case 'geocode':
                result = await callTool('geocode', intent.query ? { q: intent.query } : {});
                break;
            case 'exchange':
                result = await callTool('exchange', {
                    from: intent.from || 'USD',
                    to: intent.to || 'MXN',
                    amount: String(intent.amount || 1),
                });
                break;
            case 'translate':
                result = await callTool('translate', {
                    text: intent.text || '',
                    from: intent.from || 'es',
                    to: intent.to || 'en',
                });
                break;
            case 'news':
                result = await callTool('news', { topic: intent.topic || 'general', lang: intent.lang || 'es' });
                break;
            case 'jokes':
                result = await callTool('jokes', { category: intent.category || 'Any', lang: 'es' });
                break;
            case 'facts':
                result = await callTool('facts', { category: intent.category || 'random' });
                break;
            case 'time':
                result = await callTool('time', intent.timezone ? { tz: intent.timezone } : {});
                break;
            case 'qrcode':
                result = await callTool('qrcode', { text: intent.text || '' });
                break;
            case 'countries':
                result = await callTool('countries', intent.query ? { name: intent.query } : {});
                break;
            default:
                return { success: false, output: '', error: 'Intent not handled by tools' };
        }

        return { success: true, output: JSON.stringify(result, null, 2) };
    } catch (error: any) {
        return { success: false, output: '', error: error.message };
    }
}
