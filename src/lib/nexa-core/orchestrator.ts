/**
 * NEXA AUTONOMOUS ORCHESTRATOR (V5 — Full Brain)
 *
 * El cerebro central que permite a NEXA razonar y encadenar herramientas.
 * Conectado al sistema completo: Memoria, Skills, ML, Knowledge, Personality, Logger.
 *
 * Flujo:
 *  1. PRE-PLAN: Carga contexto del usuario (memoria, skills, perfil, emoción)
 *  2. PLAN:    LLM genera plan con herramientas expandidas (18+)
 *  3. EXECUTE: Ejecuta cada paso del plan con la herramienta correspondiente
 *  4. SYNTHESIZE: Genera respuesta final con personalización ML + NEXA_SYSTEM_PROMPT
 *  5. POST:    Extrae hechos, skills, actualiza perfil, graba learning signals, loguea actividad
 */

// ═══════════════════════════════════════════
//  BRAIN IMPORTS — Sistemas conectados
// ═══════════════════════════════════════════

// Memory
import { getMemories, extractAndSaveFacts, logActivity } from './memory';

// Skills
import { getSkills, extractAndSaveSkills } from './skills';

// Machine Learning
import {
    analyzeEmotion,
    getUserProfile,
    generatePersonalizationContext,
    recordLearningSignal,
    detectImplicitSignals,
    updateUserProfile,
    type UserEmotion,
} from './machine-learning';

// Knowledge
import { searchWikipedia, getCountryData } from './knowledge';

// Personality
import { NEXA_SYSTEM_PROMPT } from './prompts';

// Logger
import { logger } from './logger';

// Rate Limiter
import { checkRateLimit } from './rate-limiter';

// ═══════════════════════════════════════════
//  TOOL IMPORTS — Herramientas disponibles
// ═══════════════════════════════════════════

import { getWolframAnswer } from './wolfram';
import { searchMovies } from './tmdb';
import { getNASAAPOD, searchMarsPhotos } from './nasa';
import { getStockPrice, getCryptoPrice } from './finance';
import { searchFlights } from './aviation';
import { getLotteryResults } from './lottery';
import { getWeather } from './weather';
import { translateText } from './translator';
import { searchNews } from './news';
import { searchPlace } from './maps';
import { searchSpotify } from './spotify';
import { searchStackOverflow } from './stackoverflow';
import { searchArXiv, searchBooks } from './academic';
import { searchGlobalFacts } from './world-knowledge';
import { searchSpecies } from './nature';
import { searchReddit, searchYouTube } from './social';
import { generateImage, searchPhotos } from './images';

// ═══════════════════════════════════════════
//  DEEP SEARCH (Búsqueda Web Profunda)
// ═══════════════════════════════════════════

async function deepSearch(query: string): Promise<string> {
    const tavilyKey = process.env.TAVILY_API_KEY;
    if (!tavilyKey) return "Error: Falta TAVILY_API_KEY.";
    try {
        const res = await fetch('https://api.tavily.com/search', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ api_key: tavilyKey, query, search_depth: "advanced" }),
        });
        const data = await res.json();
        if (!data.results || !Array.isArray(data.results)) return "No se obtuvieron resultados de la búsqueda web.";
        return data.results.map((r: any) => `- ${r.title}: ${r.content}`).join('\n');
    } catch (e) {
        logger.error('deepSearch failed', 'Orchestrator', e);
        return "Error en búsqueda web profunda.";
    }
}

// ═══════════════════════════════════════════
//  TYPES
// ═══════════════════════════════════════════

export interface AgentTask {
    id: string;
    step: string;
    tool: string;
    params: Record<string, any>;
    status: 'pending' | 'completed' | 'failed';
    result?: string;
}

// ═══════════════════════════════════════════
//  TOOL REGISTRY — Catálogo completo (18+)
// ═══════════════════════════════════════════

const TOOL_DESCRIPTIONS = `
- 'wolfram': Datos científicos, matemáticos, hechos objetivos.
- 'movies': Cine, series, actores, estrenos.
- 'nasa': Espacio, Marte, fotos astronómicas (APOD).
- 'finance': Bolsa (símbolos tipo AAPL) y Criptomonedas.
- 'flights': Estado de vuelos (códigos IATA).
- 'lottery': Resultados de sorteos y loterías.
- 'weather': Clima y pronóstico por ciudad.
- 'web_search': Búsqueda general en internet para cualquier tema.
- 'knowledge': Búsqueda enciclopédica en Wikipedia.
- 'countries': Datos oficiales de países (capital, población, moneda).
- 'translate': Traducción profesional a más de 100 idiomas.
- 'news': Noticias de última hora y titulares.
- 'maps': Búsqueda de lugares, coordenadas y mapas.
- 'spotify': Búsqueda de canciones, álbumes y playlists.
- 'stackoverflow': Búsqueda de respuestas de programación con votos.
- 'academic': Artículos científicos (ArXiv) y libros clásicos.
- 'world_knowledge': Datos enciclopédicos universales (Wikidata).
- 'nature': Información biológica de especies y animales.
- 'social': Tendencias en Reddit y búsqueda de videos en YouTube.
- 'images': Generación de imágenes (DALL-E) y búsqueda de fotos (Unsplash).
`.trim();

const TOOL_LIST = [
    'wolfram', 'movies', 'nasa', 'finance', 'flights', 'lottery', 'weather', 'web_search',
    'knowledge', 'countries', 'translate', 'news', 'maps', 'spotify', 'stackoverflow',
    'academic', 'world_knowledge', 'nature', 'social', 'images',
] as const;

type ToolName = typeof TOOL_LIST[number];

// ═══════════════════════════════════════════
//  TOOL EXECUTOR
// ═══════════════════════════════════════════

async function executeTool(tool: string, params: Record<string, any>, fallbackQuery: string): Promise<string> {
    try {
        switch (tool as ToolName) {
            case 'wolfram':
                return await getWolframAnswer(params.query || fallbackQuery);

            case 'movies':
                return await searchMovies(params.query || fallbackQuery);

            case 'nasa':
                return params.type === 'mars' ? await searchMarsPhotos() : await getNASAAPOD();

            case 'finance':
                return params.stock
                    ? await getStockPrice(params.stock)
                    : await getCryptoPrice(params.crypto || 'bitcoin');

            case 'flights':
                return await searchFlights(params.origin, params.destination);

            case 'lottery':
                return await getLotteryResults(params.game || 'us_powerball');

            case 'weather':
                return await getWeather(params.city);

            case 'web_search':
                return await deepSearch(params.query || fallbackQuery);

            case 'knowledge':
                return await searchWikipedia(params.query || fallbackQuery);

            case 'countries':
                return await getCountryData(params.country || params.query || fallbackQuery);

            case 'translate':
                return await translateText(params.text || fallbackQuery, params.target_lang || 'en');

            case 'news':
                return await searchNews(params.query || params.topic || fallbackQuery);

            case 'maps':
                return await searchPlace(params.query || fallbackQuery);

            case 'spotify':
                return await searchSpotify(params.query || fallbackQuery, params.type || 'track');

            case 'stackoverflow':
                return await searchStackOverflow(params.query || fallbackQuery);

            case 'academic':
                return params.type === 'books'
                    ? await searchBooks(params.query || fallbackQuery)
                    : await searchArXiv(params.query || fallbackQuery);

            case 'world_knowledge':
                return await searchGlobalFacts(params.query || fallbackQuery);

            case 'nature':
                return await searchSpecies(params.query || fallbackQuery);

            case 'social':
                return params.platform === 'youtube'
                    ? await searchYouTube(params.query || fallbackQuery)
                    : await searchReddit(params.query || params.topic || fallbackQuery);

            case 'images':
                return params.action === 'generate'
                    ? await generateImage(params.prompt || params.query || fallbackQuery)
                    : await searchPhotos(params.query || fallbackQuery);

            default:
                // Fallback: intentamos búsqueda web para herramientas desconocidas
                logger.warn(`Unknown tool "${tool}" requested, falling back to web_search`, 'Orchestrator');
                return await deepSearch(params.query || fallbackQuery);
        }
    } catch (e: any) {
        logger.error(`Tool "${tool}" execution failed`, 'Orchestrator', e);
        return `Error ejecutando ${tool}: ${e.message || 'Error desconocido'}`;
    }
}

// ═══════════════════════════════════════════
//  GROQ HELPER
// ═══════════════════════════════════════════

function getGroqKey(): string | null {
    return process.env.GROQ_API_KEY || null;
}

// ═══════════════════════════════════════════
//  MAIN LOOP — Orquestador Autónomo V5
// ═══════════════════════════════════════════

export async function runAutonomousLoop(
    userQuery: string,
    userId?: string,
    userLocation?: { city?: string; country?: string },
): Promise<string> {
    const groqKey = getGroqKey();
    if (!groqKey) return "Error: Falta GROQ_API_KEY para el orquestador.";

    // ── Rate Limit ──────────────────────────
    const rlIdentifier = userId || 'anonymous';
    const rl = await checkRateLimit(`orchestrator:${rlIdentifier}`);
    if (!rl.allowed) {
        logger.warn(`Rate limited: ${rlIdentifier}`, 'Orchestrator');
        return `Estoy procesando muchas peticiones. Por favor espera ${Math.ceil((rl.retryAfterMs || 60000) / 1000)} segundos e intenta de nuevo.`;
    }

    const startTime = Date.now();
    const effectiveUserId = userId || 'guest';

    try {
        // ══════════════════════════════════════
        //  PHASE 0: PRE-PLAN — Load brain context
        // ══════════════════════════════════════
        logger.info(`Starting autonomous loop for: "${userQuery.substring(0, 80)}"`, 'Orchestrator');

        // Emotion analysis (synchronous, fast)
        const emotion: UserEmotion = analyzeEmotion(userQuery);

        // Parallel: load memories, skills, profile
        const [memories, skills, profile] = await Promise.all([
            getMemories(effectiveUserId),
            getSkills(effectiveUserId),
            getUserProfile(effectiveUserId),
        ]);

        // Personalization context from ML
        const personalizationCtx = generatePersonalizationContext(profile, emotion);

        // Build user context string for the planner
        const userContextParts: string[] = [];

        if (memories.length > 0) {
            userContextParts.push(`RECUERDOS DEL USUARIO:\n${memories.slice(-10).map(m => `- ${m}`).join('\n')}`);
        }

        if (skills.length > 0) {
            userContextParts.push(`HABILIDADES APRENDIDAS DEL USUARIO:\n${skills.slice(0, 5).map(s => `- ${s.name}: ${s.description}`).join('\n')}`);
        }

        if (personalizationCtx) {
            userContextParts.push(`CONTEXTO DE PERSONALIZACIÓN:\n${personalizationCtx}`);
        }

        if (userLocation?.city || userLocation?.country) {
            userContextParts.push(`UBICACIÓN DEL USUARIO: ${userLocation.city || ''}, ${userLocation.country || ''}`);
        }

        const userContextBlock = userContextParts.length > 0
            ? `\n\n--- CONTEXTO DEL USUARIO ---\n${userContextParts.join('\n\n')}\n--- FIN CONTEXTO ---\n`
            : '';

        // ══════════════════════════════════════
        //  PHASE 1: PLAN — LLM generates plan
        // ══════════════════════════════════════
        const planRes = await fetch('https://api.groq.com/openai/v1/chat/completions', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${groqKey}` },
            body: JSON.stringify({
                model: 'llama-3.3-70b-versatile',
                messages: [
                    {
                        role: 'system',
                        content: `Eres el Orquestador de NEXA. Tu trabajo es crear un plan de 1 a 3 pasos para responder al usuario usando las herramientas disponibles.

HERRAMIENTAS DISPONIBLES:
${TOOL_DESCRIPTIONS}

REGLAS:
- Elige SOLO herramientas de la lista anterior.
- Para consultas de conocimiento general, prefiere 'knowledge' (Wikipedia) o 'world_knowledge' (Wikidata).
- Para preguntas científicas/matemáticas, usa 'wolfram'.
- Para temas de actualidad, usa 'news'.
- Para programación, usa 'stackoverflow'.
- Para cualquier otra cosa sin herramienta específica, usa 'web_search'.
- Si el usuario pregunta sobre un país, usa 'countries'.
- Si necesita traducción, usa 'translate'.
- Respeta el contexto del usuario (ubicación, preferencias, emociones) para elegir herramientas relevantes.

Responde EXCLUSIVAMENTE en formato JSON:
{"plan": [{"step": "descripción del paso", "tool": "nombre_herramienta", "params": {"key": "val"}}]}

Si la consulta no requiere ninguna herramienta, responde:
{"plan": []}`,
                    },
                    {
                        role: 'user',
                        content: `${userContextBlock}\nConsulta del usuario: ${userQuery}`,
                    },
                ],
                response_format: { type: "json_object" },
            }),
        });

        if (!planRes.ok) {
            logger.error(`Plan API returned ${planRes.status}`, 'Orchestrator');
            return "No pude generar un plan en este momento. Intenta de nuevo.";
        }

        const planData = await planRes.json();
        let plan: AgentTask[] = [];

        try {
            const rawContent = planData?.choices?.[0]?.message?.content;
            if (rawContent) {
                const parsed = JSON.parse(rawContent);
                plan = Array.isArray(parsed.plan) ? parsed.plan : [];
            }
        } catch (parseErr) {
            logger.error('Failed to parse plan JSON', 'Orchestrator', parseErr);
            plan = [];
        }

        // Validate each task in the plan
        for (const task of plan) {
            task.status = 'pending';
            task.id = task.id || `task_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`;
            // Sanitize: ensure tool is in the allowed list
            if (!(TOOL_LIST as readonly string[]).includes(task.tool)) {
                logger.warn(`Planner returned unknown tool "${task.tool}", replacing with web_search`, 'Orchestrator');
                task.tool = 'web_search';
            }
        }

        if (!plan || plan.length === 0) {
            logger.info('No tool tasks detected, falling back to direct LLM response', 'Orchestrator');
            // No tools needed — give a direct response using NEXA personality
            const directRes = await fetch('https://api.groq.com/openai/v1/chat/completions', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${groqKey}` },
                body: JSON.stringify({
                    model: 'llama-3.3-70b-versatile',
                    messages: [
                        { role: 'system', content: NEXA_SYSTEM_PROMPT },
                        { role: 'user', content: userQuery },
                    ],
                }),
            });

            const directData = await directRes.json();
            const directResponse = directData?.choices?.[0]?.message?.content || "No pude procesar tu consulta.";

            // Post-processing (non-blocking)
            postProcess(effectiveUserId, userQuery, directResponse, emotion, userLocation).catch(() => {});

            return directResponse;
        }

        logger.info(`Plan generated: ${plan.map(t => t.tool).join(' → ')}`, 'Orchestrator');

        // ══════════════════════════════════════
        //  PHASE 2: EXECUTE — Run each task
        // ══════════════════════════════════════
        let totalContext = "";
        for (const task of plan) {
            const result = await executeTool(task.tool, task.params || {}, userQuery);
            task.status = result.startsWith('Error') ? 'failed' : 'completed';
            task.result = result;
            totalContext += `[RESULTADO ${task.tool.toUpperCase()}]: ${result}\n\n`;
        }

        // ══════════════════════════════════════
        //  PHASE 3: SYNTHESIZE — Final response
        // ══════════════════════════════════════

        // Build emotion-aware instruction
        let emotionInstruction = '';
        if (emotion.primary === 'sadness') {
            emotionInstruction = '\n\nNOTA: El usuario parece estar triste. Responde con empatía y calidez.';
        } else if (emotion.primary === 'anger') {
            emotionInstruction = '\n\nNOTA: El usuario parece frustrado. Sé comprensivo y calmado.';
        } else if (emotion.primary === 'joy') {
            emotionInstruction = '\n\nNOTA: El usuario está contento. Comparte su entusiasmo.';
        } else if (emotion.primary === 'fear') {
            emotionInstruction = '\n\nNOTA: El usuario parece preocupado. Sé tranquilizador y ofrece seguridad.';
        }

        const synthMessages: { role: string; content: string }[] = [];

        // System: Use NEXA's canonical personality prompt
        synthMessages.push({
            role: 'system',
            content: NEXA_SYSTEM_PROMPT + emotionInstruction,
        });

        // If we have personalization context, add it
        if (personalizationCtx) {
            synthMessages.push({
                role: 'system',
                content: `Contexto de personalización para este usuario:\n${personalizationCtx}`,
            });
        }

        // If we have memories, include relevant ones
        if (memories.length > 0) {
            synthMessages.push({
                role: 'system',
                content: `Recuerdos sobre este usuario (úsalos para personalizar):\n${memories.slice(-5).join('\n')}`,
            });
        }

        // If we have skills, mention them
        if (skills.length > 0) {
            synthMessages.push({
                role: 'system',
                content: `Reglas/habilidades que has aprendido de este usuario:\n${skills.map(s => `- ${s.name}: ${s.instructions}`).join('\n')}`,
            });
        }

        // User query + tool results
        synthMessages.push({
            role: 'user',
            content: `Consulta: ${userQuery}\n\nContexto obtenido por tus agentes:\n${totalContext}\n\nResponde ahora de forma concisa y útil.`,
        });

        const finalRes = await fetch('https://api.groq.com/openai/v1/chat/completions', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${groqKey}` },
            body: JSON.stringify({
                model: 'llama-3.3-70b-versatile',
                messages: synthMessages,
            }),
        });

        const finalData = await finalRes.json();
        const finalResponse = finalData?.choices?.[0]?.message?.content;

        if (!finalResponse) {
            logger.error('Synthesis API returned no content', 'Orchestrator', finalData);
            return "No pude generar una respuesta coherente con los datos obtenidos. Intenta reformular tu pregunta.";
        }

        // ══════════════════════════════════════
        //  PHASE 4: POST-PROCESS — Learn & log
        // ══════════════════════════════════════
        postProcess(effectiveUserId, userQuery, finalResponse, emotion, userLocation).catch(() => {});

        const elapsed = Date.now() - startTime;
        logger.info(`Autonomous loop completed in ${elapsed}ms (${plan.length} tools)`, 'Orchestrator');

        return finalResponse;

    } catch (error: any) {
        logger.error('Orchestrator loop failed', 'Orchestrator', error);
        return `Error en el orquestador autónomo: ${error.message || 'Error desconocido'}`;
    }
}

// ═══════════════════════════════════════════
//  POST-PROCESSING — Learning & Memory (fire-and-forget)
// ═══════════════════════════════════════════

async function postProcess(
    userId: string,
    userQuery: string,
    assistantResponse: string,
    emotion: UserEmotion,
    location?: { city?: string; country?: string },
): Promise<void> {
    // Run all post-processing tasks in parallel — errors are caught individually
    await Promise.allSettled([
        // Extract and save facts about the user
        extractAndSaveFacts(userId, userQuery),

        // Extract and save skills (if the user is teaching/correcting)
        extractAndSaveSkills(userId, userQuery, assistantResponse),

        // Update user profile based on this interaction
        updateUserProfile(userId, userQuery, emotion, extractTopics(userQuery)),

        // Record implicit learning signals
        ...detectImplicitSignals(userQuery, assistantResponse, emotion).map(signal =>
            recordLearningSignal(userId, signal)
        ),

        // Log activity
        logActivity(
            userId,
            location?.city || 'unknown',
            location?.country || 'unknown',
            extractTopics(userQuery)[0] || 'general',
        ),
    ]);

    logger.debug('Post-processing completed', 'Orchestrator');
}

// ═══════════════════════════════════════════
//  HELPERS
// ═══════════════════════════════════════════

/**
 * Simple topic extraction from user query for profile/learning purposes.
 * Does not call LLM — uses keyword matching for speed.
 */
function extractTopics(text: string): string[] {
    const lower = text.toLowerCase();
    const topicMap: Record<string, string[]> = {
        'technology': ['código', 'programa', 'app', 'software', 'api', 'bug', 'desarrollo', 'computadora', 'código'],
        'travel': ['vuelo', 'viaje', 'avión', 'hotel', 'pasaje', 'aerolínea'],
        'finance': ['precio', 'dinero', 'dólar', 'bitcoin', 'bolsa', 'cripto', 'inversión', 'acción'],
        'health': ['salud', 'ejercicio', 'dieta', 'médico', 'enfermedad', 'bienestar'],
        'entertainment': ['película', 'música', 'serie', 'juego', 'canción', 'libro', 'spotify'],
        'science': ['ciencia', 'investigación', 'espacio', 'nasa', 'física', 'química', 'wolfram'],
        'food': ['receta', 'cocina', 'comida', 'restaurante', 'ingredientes'],
        'sports': ['fútbol', 'deporte', 'equipo', 'partido', 'liga'],
        'weather': ['clima', 'tiempo', 'lluvia', 'temperatura', 'pronóstico'],
        'news': ['noticias', 'actualidad', 'evento', 'sucedido'],
        'education': ['estudiar', 'aprender', 'curso', 'universidad', 'escuela'],
        'maps': ['mapa', 'ubicación', 'dirección', 'lugar', 'coordenadas'],
        'nature': ['animal', 'planta', 'especie', 'biología', 'ecología'],
        'social': ['reddit', 'youtube', 'red social', 'tendencia', 'viral'],
        'translation': ['traducir', 'traducción', 'inglés', 'español', 'idioma'],
    };

    const topics: string[] = [];
    for (const [topic, keywords] of Object.entries(topicMap)) {
        if (keywords.some(kw => lower.includes(kw))) {
            topics.push(topic);
        }
    }
    return topics.length > 0 ? topics : ['general'];
}
