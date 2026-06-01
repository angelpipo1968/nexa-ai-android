// ═══════════════════════════════════════════════════════════════
//  NEXA CORE — Unified Tools System
//  Single source of truth for intent detection, tool execution,
//  and tool registry. Direct module imports — no HTTP round-trips.
// ═══════════════════════════════════════════════════════════════

import type { ToolResult } from '@/lib/shared-types';
export type { ToolResult };

// ── Direct module imports ───────────────────────────────────────
import { getWeather } from './weather';
import { searchWikipedia, getCountryData } from './knowledge';
import { getWolframAnswer } from './wolfram';
import { searchMovies } from './tmdb';
import { getNASAAPOD, searchMarsPhotos } from './nasa';
import { getStockPrice, getCryptoPrice } from './finance';
import { searchFlights } from './aviation';
import { searchSkyscannerFlights } from './skyscanner';
import { searchGoogleFlights, searchPriceCalendar } from './google-flights';
import { getLotteryResults, getNextDraw, generateLotteryNumbers, getAvailableGames } from './lottery';
import { searchStackOverflow } from './stackoverflow';
import { searchSpotify } from './spotify';
import { searchPlace } from './maps';
import { searchArXiv, searchBooks } from './academic';
import { searchSpecies } from './nature';
import { searchGlobalFacts } from './world-knowledge';
import { searchNews } from './news';
import { translateText } from './translator';
import { generateImage, searchPhotos } from './images';
import { searchReddit, searchYouTube } from './social';
import { searchVideos, searchLibraries } from './multimedia';
import { auditCode } from './repairer';

// ── Intent Types ────────────────────────────────────────────────

export type UserIntent =
    | { type: 'weather'; city?: string }
    | { type: 'flights'; origin?: string; destination?: string; date?: string; returnDate?: string }
    | { type: 'wolfram'; query: string }
    | { type: 'movies'; query: string }
    | { type: 'nasa'; mars: boolean }
    | { type: 'finance_stock'; symbol: string }
    | { type: 'finance_crypto'; coin: string }
    | { type: 'lottery'; game: string; action: 'results' | 'next_draw' | 'generate' | 'available' }
    | { type: 'knowledge'; query: string }
    | { type: 'country'; name: string }
    | { type: 'translate'; text: string; targetLang: string }
    | { type: 'news'; query: string }
    | { type: 'music'; query: string }
    | { type: 'maps'; query: string }
    | { type: 'science'; query: string }
    | { type: 'books'; query: string }
    | { type: 'nature'; query: string }
    | { type: 'encyclopedia'; query: string }
    | { type: 'code'; language?: string; description: string }
    | { type: 'web'; description: string }
    | { type: 'design'; description: string }
    | { type: 'analysis'; subject: string }
    | { type: 'vision'; hasImage: boolean }
    | { type: 'search'; query: string }
    | { type: 'image_generation'; prompt: string }
    | { type: 'video_generation'; prompt: string }
    | { type: 'stackoverflow'; query: string }
    | { type: 'social_reddit'; query: string }
    | { type: 'social_youtube'; query: string }
    | { type: 'geolocation'; ip?: string }
    | { type: 'geocode'; query?: string; lat?: string; lon?: string }
    | { type: 'exchange'; from?: string; to?: string; amount?: number }
    | { type: 'jokes'; category?: string }
    | { type: 'facts'; category?: string }
    | { type: 'time'; timezone?: string }
    | { type: 'qrcode'; text?: string }
    | { type: 'chat'; message: string };

// ── TOOL REGISTRY ───────────────────────────────────────────────
// Used by chat route, orchestrator, and the intent detector prompt.

export interface ToolDefinition {
    name: string;
    keywords: string[];
    description: string;
}

export const TOOL_REGISTRY: ToolDefinition[] = [
    { name: 'weather',         keywords: ['clima', 'tiempo', 'weather', 'temperatura', 'lluvia', 'pronóstico', 'pronostico'], description: 'Clima y pronóstico 7 días (Open-Meteo)' },
    { name: 'flights',         keywords: ['vuelo', 'viaje', 'pasaje', 'avión', 'boleto', 'aerolinea', 'aerolínea'], description: 'Vuelos, precios y estado (Google Flights + Skyscanner + AviationStack)' },
    { name: 'wolfram',         keywords: ['cuanto es', 'cuánto es', 'distancia', 'masa', 'población', 'capital de'], description: 'Datos científicos y matemáticos exactos (WolframAlpha)' },
    { name: 'movies',          keywords: ['película', 'serie', 'actor', 'director', 'estreno', 'reparto'], description: 'Cine, series y TV (TMDB)' },
    { name: 'nasa',            keywords: ['nasa', 'espacio', 'marte', 'universo', 'estrella', 'galaxia', 'planeta'], description: 'Fotos astronómicas y datos de Marte (NASA)' },
    { name: 'finance_stock',   keywords: ['bolsa', 'acción', 'accion', 'precio de'], description: 'Precios de acciones en bolsa' },
    { name: 'finance_crypto',  keywords: ['bitcoin', 'ethereum', 'btc', 'eth', 'cripto', 'crypto'], description: 'Precios de criptomonedas' },
    { name: 'lottery',         keywords: ['lotería', 'loteria', 'sorteo', 'powerball', 'megamillions', 'melate', 'chispazo', 'baloto', 'euromillones', 'jackpot'], description: 'Resultados y números de lotería' },
    { name: 'knowledge',       keywords: ['quien es', 'quién es', 'qué es', 'que es', 'significa', 'biografía', 'historia de'], description: 'Búsqueda en Wikipedia' },
    { name: 'country',         keywords: ['población de', 'capital de', 'moneda de', 'continente de'], description: 'Datos de países (REST Countries)' },
    { name: 'translate',       keywords: ['traduce', 'translate', 'traducir', 'en inglés', 'en español'], description: 'Traducción de texto' },
    { name: 'news',            keywords: ['noticias', 'news', 'actualidad', 'últimas', 'ultimas'], description: 'Noticias y actualidad' },
    { name: 'music',           keywords: ['música', 'canción', 'playlist', 'spotify', 'album'], description: 'Búsqueda de música (Spotify)' },
    { name: 'maps',            keywords: ['mapa', 'dirección', 'lugar', 'ubicación de', 'coordenadas'], description: 'Búsqueda de lugares y mapas (OpenStreetMap)' },
    { name: 'science',         keywords: ['arxiv', 'paper', 'investigación', 'científico', 'ciencia'], description: 'Artículos científicos (arXiv)' },
    { name: 'books',           keywords: ['libro', 'books', 'autor de', 'lectura'], description: 'Búsqueda de libros (Open Library)' },
    { name: 'nature',          keywords: ['especie', 'animal', 'planta', 'flora', 'fauna'], description: 'Búsqueda de especies (iNaturalist)' },
    { name: 'encyclopedia',    keywords: ['dato', 'hecho', 'fact', 'curiosidad', 'enciclopedia'], description: 'Datos y hechos globales' },
    { name: 'code',            keywords: ['código', 'codigo', 'code', 'función', 'script', 'programa', 'api', 'endpoint'], description: 'Generación y reparación de código' },
    { name: 'image_generation',keywords: ['dibuja', 'genera', 'diseña', 'crea', 'imagina', 'muestra', 'foto', 'imagen'], description: 'Generación de imágenes (DALL-E)' },
    { name: 'stackoverflow',   keywords: ['bug', 'error', 'exception', 'debug', 'stackoverflow', 'stack overflow'], description: 'Búsqueda en Stack Overflow' },
    { name: 'social_reddit',   keywords: ['reddit', 'hilo', 'foro'], description: 'Búsqueda en Reddit' },
    { name: 'social_youtube',  keywords: ['youtube', 'video de', 'canal de'], description: 'Búsqueda en YouTube' },
    { name: 'exchange',        keywords: ['dólar', 'dolar', 'euro', 'peso', 'moneda', 'cambio', 'exchange', 'currency'], description: 'Conversión de monedas' },
    { name: 'geolocation',     keywords: ['ubicación', 'ubicacion', 'location', 'donde estoy', 'dónde estoy', 'mi ip'], description: 'Ubicación por IP' },
    { name: 'jokes',           keywords: ['chiste', 'joke', 'gracioso', 'divertido'], description: 'Chistes y humor' },
    { name: 'facts',           keywords: ['dato curioso', 'fact', 'sabías', 'trivia'], description: 'Datos curiosos' },
    { name: 'time',            keywords: ['hora', 'time', 'reloj', 'timezone', 'zona horaria'], description: 'Hora mundial' },
    { name: 'qrcode',          keywords: ['qr', 'código qr', 'codigo qr'], description: 'Generador de QR' },
    { name: 'vision',          keywords: ['imagen', 'ver', 'mira', 'foto de'], description: 'Análisis de imágenes' },
];

// ── Intent Detection ────────────────────────────────────────────

/**
 * Detect user intent from a message string.
 * Covers ALL tools in TOOL_REGISTRY plus orchestrator tools.
 */
export function detectIntent(message: string): UserIntent {
    const lower = message.toLowerCase();

    // ── Finance: Crypto (check BEFORE general finance keywords) ──
    if (/\b(bitcoin|btc)\b/.test(lower)) return { type: 'finance_crypto', coin: 'bitcoin' };
    if (/\b(ethereum|eth)\b/.test(lower)) return { type: 'finance_crypto', coin: 'ethereum' };

    // ── Finance: Stock ──
    const triggerFinanceStock = ['bolsa', 'acción', 'accion'];
    if (triggerFinanceStock.some(kw => lower.includes(kw))) {
        const symbolMatch = message.match(/\b[A-Z]{3,5}\b/);
        if (symbolMatch) return { type: 'finance_stock', symbol: symbolMatch[0] };
    }
    if (/precio de\b.*\b[a-z]/i.test(lower)) {
        const symbolMatch = message.match(/\b[A-Z]{3,5}\b/);
        if (symbolMatch) return { type: 'finance_stock', symbol: symbolMatch[0] };
    }

    // ── Weather ──
    if (/\b(clima|weather|temperatura|lluvia|pronóstico|pronostico)\b/.test(lower) || /tiempo\s+(hace|va|va\s+a)\b/.test(lower)) {
        const cityMatch = message.match(/(?:en|in|de|del)\s+([A-Za-zÀ-ÿ\s]+?)(?:\?|$|\.)/i);
        return { type: 'weather', city: cityMatch?.[1]?.trim() };
    }

    // ── Flights ──
    if (/\b(vuelo|viaje|pasaje|avión|avion|boleto|aerolinea|aerolínea)\b/.test(lower)) {
        // Lightweight extraction — actual params filled by LLM in route
        return { type: 'flights' };
    }

    // ── Wolfram ──
    const triggerWolfram = ['cuanto es', 'cuánto es', 'qué es', 'que es', 'quién es', 'quien es', 'distancia', 'masa', 'población', 'capital de'];
    if (triggerWolfram.some(kw => lower.includes(kw))) {
        return { type: 'wolfram', query: message };
    }

    // ── Movies ──
    const triggerMovies = ['película', 'serie', 'actor', 'director', 'estreno', 'reparto'];
    if (triggerMovies.some(kw => lower.includes(kw))) {
        return { type: 'movies', query: message };
    }

    // ── NASA ──
    if (/\b(nasa|espacio|marte|universo|estrella|galaxia|planeta)\b/.test(lower)) {
        return { type: 'nasa', mars: lower.includes('marte') };
    }

    // ── Lottery ──
    const triggerLottery = ['lotería', 'loteria', 'sorteo', 'powerball', 'megamillions', 'melate', 'chispazo', 'baloto', 'euromillones', 'jackpot'];
    if (triggerLottery.some(kw => lower.includes(kw))) {
        let game = 'us_powerball';
        if (lower.includes('mega')) game = 'us_megamillions';
        if (lower.includes('melate') && !lower.includes('retro')) game = 'mx_melate';
        if (lower.includes('chispazo')) game = 'mx_chispazo';
        if (lower.includes('retro')) game = 'mx_melate_retro';
        if (lower.includes('baloto')) game = 'co_baloto';
        if (lower.includes('euromill')) game = 'eu_euromillions';
        if (lower.includes('el gordo')) game = 'es_el_gordo';

        let action: 'results' | 'next_draw' | 'generate' | 'available' = 'results';
        if (lower.includes('qué juegos') || lower.includes('que juegos') || lower.includes('disponibles') || lower.includes('cuáles hay')) action = 'available';
        else if (lower.includes('genera') || lower.includes('números') || lower.includes('numeros') || lower.includes('recomienda')) action = 'generate';
        else if (lower.includes('próximo') || lower.includes('proximo') || lower.includes('cuándo') || lower.includes('cuando') || lower.includes('siguiente sorteo')) action = 'next_draw';

        return { type: 'lottery', game, action };
    }

    // ── Knowledge (Wikipedia) ──
    const triggerWiki = ['quien es', 'quién es', 'significa', 'biografía', 'historia de'];
    if (triggerWiki.some(kw => lower.includes(kw))) {
        const topic = message.replace(/quien es|quién es|qué es|que es|dime sobre|háblame de/gi, '').trim();
        return { type: 'knowledge', query: topic };
    }

    // ── Countries ──
    const triggerCountry = ['población de', 'capital de', 'moneda de', 'continente de'];
    if (triggerCountry.some(kw => lower.includes(kw))) {
        const country = message.match(/de\s+([A-Za-zÀ-ÿ\s]+?)(?:\?|$|\.)/i)?.[1]?.trim() || message.split(' ').pop() || '';
        return { type: 'country', name: country };
    }

    // ── Translation ──
    if (/\b(traduce|translate|traducir)\b/.test(lower) || /\b(en inglés|en español|in english|in spanish)\b/.test(lower)) {
        const text = message.replace(/(?:traduce|translate|traducir|en inglés|en español|in english|in spanish)\s*/i, '').trim();
        const targetLang = lower.includes('inglés') || lower.includes('english') ? 'inglés' : 'español';
        return { type: 'translate', text, targetLang };
    }

    // ── News ──
    if (/\b(noticias|news|actualidad|últimas|ultimas)\b/.test(lower)) {
        return { type: 'news', query: message };
    }

    // ── Music ──
    if (/\b(música|canción|playlist|spotify|album)\b/.test(lower)) {
        return { type: 'music', query: message };
    }

    // ── Maps ──
    if (/\b(mapa|dirección|lugar|ubicación de|coordenadas)\b/.test(lower)) {
        return { type: 'maps', query: message };
    }

    // ── Science (arXiv) ──
    if (/\b(arxiv|paper|investigación|científico)\b/.test(lower)) {
        return { type: 'science', query: message };
    }

    // ── Books ──
    if (/\b(libro|books|autor de|lectura)\b/.test(lower)) {
        return { type: 'books', query: message };
    }

    // ── Nature ──
    if (/\b(especie|animal|planta|flora|fauna)\b/.test(lower)) {
        return { type: 'nature', query: message };
    }

    // ── Encyclopedia (global facts) ──
    if (/\b(dato|hecho|fact|curiosidad|enciclopedia)\b/.test(lower)) {
        return { type: 'encyclopedia', query: message };
    }

    // ── Image Generation ──
    const triggerImages = ['dibuja', 'genera', 'diseña', 'crea', 'imagina', 'muestra', 'muéstrame', 'foto', 'imagen'];
    if (triggerImages.some(kw => new RegExp(`\\b${kw}\\b`, 'i').test(lower))) {
        return { type: 'image_generation', prompt: message };
    }

    // ── Code ──
    if (/\b(código|codigo|code|función|script|programa|api|endpoint)\b/.test(lower)) {
        const langMatch = message.match(/(?:python|javascript|typescript|react|html|css|sql|go|rust|java|c\+\+)/i);
        return { type: 'code', language: langMatch?.[0]?.toLowerCase(), description: message };
    }

    // ── StackOverflow ──
    if (/\b(bug|error|exception|debug|stackoverflow|stack overflow)\b/.test(lower)) {
        return { type: 'stackoverflow', query: message };
    }

    // ── Social: Reddit ──
    if (/\b(reddit|hilo|foro)\b/.test(lower)) {
        return { type: 'social_reddit', query: message };
    }

    // ── Social: YouTube ──
    if (/\b(youtube|video de|canal de)\b/.test(lower)) {
        return { type: 'social_youtube', query: message };
    }

    // ── Exchange ──
    if (/\b(dólar|dolar|euro|peso|moneda|cambio|exchange|currency|convertir)\b/.test(lower)) {
        const fromMatch = message.match(/(\d+)\s*(?:dólares?|dolares?|usd|€|euros?|pesos?|mxn)/i);
        return { type: 'exchange', amount: fromMatch ? parseFloat(fromMatch[1]) : undefined };
    }

    // ── Geolocation ──
    if (/\b(ubicación|ubicacion|location|donde estoy|dónde estoy|mi ip|geolocalización)\b/.test(lower)) {
        return { type: 'geolocation' };
    }

    // ── Jokes ──
    if (/\b(chiste|joke|gracioso|divertido|ríe|rie)\b/.test(lower)) {
        return { type: 'jokes' };
    }

    // ── Facts ──
    if (/\b(sabías|sabias|trivia)\b/.test(lower)) {
        return { type: 'facts' };
    }

    // ── Time ──
    if (/\b(hora|time|reloj|timezone|zona horaria)\b/.test(lower)) {
        const tzMatch = message.match(/(?:en|in|de)\s+([A-Za-zÀ-ÿ_/]+(?:\/[A-Za-zÀ-ÿ_]+)?)/i);
        return { type: 'time', timezone: tzMatch?.[1] };
    }

    // ── QR Code ──
    if (/\bqr\b|código qr|codigo qr/.test(lower)) {
        return { type: 'qrcode', text: message.replace(/(?:qr|código qr|codigo qr)\s*(?:de|for|para)?\s*/i, '').trim() };
    }

    // ── Vision ──
    if (/\b(imagen|ver|mira|foto de)\b/.test(lower)) {
        return { type: 'vision', hasImage: true };
    }

    // ── Design ──
    if (/\b(diseño|logo|ui|ux|interfaz|mockup)\b/.test(lower)) {
        return { type: 'design', description: message };
    }

    // ── Analysis ──
    if (/\b(analiza|analice|explica|por qué|por que|cómo funciona)\b/.test(lower)) {
        return { type: 'analysis', subject: message };
    }

    // ── Web Design ──
    if (/\b(página web|pagina web|website|landing|portfolio|sitio web)\b/.test(lower)) {
        return { type: 'web', description: message };
    }

    // ── Fallback: general chat ──
    return { type: 'chat', message };
}

// ── Intent Execution ────────────────────────────────────────────
// Calls nexa-core modules directly — NO HTTP round-trips.

export async function executeIntent(intent: UserIntent): Promise<ToolResult> {
    try {
        let output: string | undefined;

        switch (intent.type) {
            case 'weather':
                output = await getWeather(intent.city || '');
                break;

            case 'flights':
                if (intent.origin && intent.destination) {
                    const date = intent.date || new Date().toISOString().split('T')[0];
                    // Run all three sources in parallel for speed
                    const [google, skyscanner, aviation] = await Promise.allSettled([
                        searchGoogleFlights(intent.origin, intent.destination, date, intent.returnDate),
                        searchSkyscannerFlights(intent.origin, intent.destination, date),
                        searchFlights(intent.origin, intent.destination),
                    ]);
                    const parts = [google, skyscanner, aviation]
                        .filter((r): r is PromiseFulfilledResult<string> => r.status === 'fulfilled' && !!r.value)
                        .map(r => r.value);
                    output = parts.join('\n');
                }
                break;

            case 'wolfram':
                output = await getWolframAnswer(intent.query);
                break;

            case 'movies':
                output = await searchMovies(intent.query);
                break;

            case 'nasa':
                output = intent.mars ? await searchMarsPhotos() : await getNASAAPOD();
                break;

            case 'finance_stock':
                output = await getStockPrice(intent.symbol);
                break;

            case 'finance_crypto':
                output = await getCryptoPrice(intent.coin);
                break;

            case 'lottery':
                switch (intent.action) {
                    case 'available': output = getAvailableGames(); break;
                    case 'generate': output = await generateLotteryNumbers(intent.game); break;
                    case 'next_draw': output = (await getNextDraw(intent.game)) + '\n' + (await getLotteryResults(intent.game)); break;
                    default: output = await getLotteryResults(intent.game); break;
                }
                break;

            case 'knowledge':
                output = await searchWikipedia(intent.query);
                break;

            case 'country':
                output = await getCountryData(intent.name);
                break;

            case 'translate':
                output = await translateText(intent.text, intent.targetLang);
                break;

            case 'news':
                output = await searchNews(intent.query);
                break;

            case 'music':
                output = await searchSpotify(intent.query);
                break;

            case 'maps':
                output = await searchPlace(intent.query);
                break;

            case 'science':
                output = await searchArXiv(intent.query);
                break;

            case 'books':
                output = await searchBooks(intent.query);
                break;

            case 'nature':
                output = await searchSpecies(intent.query);
                break;

            case 'encyclopedia':
                output = await searchGlobalFacts(intent.query);
                break;

            case 'code':
                output = await auditCode(intent.description, intent.language || 'auto');
                break;

            case 'image_generation':
                output = await generateImage(intent.prompt);
                break;

            case 'stackoverflow':
                output = await searchStackOverflow(intent.query);
                break;

            case 'social_reddit':
                output = await searchReddit(intent.query);
                break;

            case 'social_youtube':
                output = await searchYouTube(intent.query);
                break;

            case 'exchange':
                output = await getStockPrice('USD'); // Fallback — no direct exchange module
                break;

            case 'jokes':
                output = undefined; // No jokes module in nexa-core
                break;

            case 'facts':
                output = await searchGlobalFacts('random');
                break;

            case 'time':
                output = undefined; // Use getLocalTime from location module directly
                break;

            case 'qrcode':
                output = undefined; // No QR module in nexa-core
                break;

            case 'geolocation':
                output = undefined; // Use getUserLocation from location module directly
                break;

            case 'vision':
            case 'web':
            case 'design':
            case 'analysis':
            case 'search':
            case 'geocode':
            case 'video_generation':
            case 'chat':
                return { success: false, error: `Intent type '${intent.type}' is not handled by executeIntent — use the chat/vision route instead` };

            default:
                return { success: false, error: `Unknown intent type` };
        }

        if (output) {
            return { success: true, output, data: undefined, metadata: { type: intent.type } };
        }
        return { success: false, error: `No output for intent '${intent.type}'` };
    } catch (error: unknown) {
        const message = error instanceof Error ? error.message : String(error);
        return { success: false, error: message };
    }
}

// ── Helper: get tool names for the LLM prompt ──────────────────

/**
 * Returns a comma-separated list of tool names for the intent detection prompt.
 */
export function getToolNamesForPrompt(): string {
    return TOOL_REGISTRY.map(t => t.name).join(', ');
}
