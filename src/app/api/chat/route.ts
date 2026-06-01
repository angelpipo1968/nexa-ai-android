import { NextRequest, NextResponse } from 'next/server';
import { getIdentifier, checkRateLimit, RATE_LIMITS } from '@/lib/nexa-core/rate-limiter';
import { logger, generateRequestId } from '@/lib/nexa-core/logger';
import { chatSchema } from '@/lib/validation';
import { getSystemPrompt } from '@/lib/nexa-core/prompts';
import { searchFlights } from '@/lib/nexa-core/aviation';
import { getWeather } from '@/lib/nexa-core/weather';
import { generateImage, searchPhotos } from '@/lib/nexa-core/images';
import { getWolframAnswer } from '@/lib/nexa-core/wolfram';
import { searchMovies } from '@/lib/nexa-core/tmdb';
import { getNASAAPOD, searchMarsPhotos } from '@/lib/nexa-core/nasa';
import { getStockPrice, getCryptoPrice } from '@/lib/nexa-core/finance';
import { getLotteryResults, getNextDraw, generateLotteryNumbers, getAvailableGames } from '@/lib/nexa-core/lottery';
import { searchStackOverflow, searchByTags } from '@/lib/nexa-core/stackoverflow';
import { searchSkyscannerFlights } from '@/lib/nexa-core/skyscanner';
import { searchGoogleFlights, searchPriceCalendar } from '@/lib/nexa-core/google-flights';
import { searchWikipedia, getCountryData } from '@/lib/nexa-core/knowledge';
import { getMemories, extractAndSaveFacts, logActivity } from '@/lib/nexa-core/memory';
import { getSkills, extractAndSaveSkills } from '@/lib/nexa-core/skills';
import { auditCode } from '@/lib/nexa-core/repairer';
import { searchVideos, searchLibraries } from '@/lib/nexa-core/multimedia';
import { generateVideo } from '@/lib/nexa-core/video-generation';
import { searchReddit, searchYouTube } from '@/lib/nexa-core/social';
import { searchSpotify } from '@/lib/nexa-core/spotify';
import { getUserLocation, getLocalTime } from '@/lib/nexa-core/location';
import { searchPlace } from '@/lib/nexa-core/maps';
import { searchArXiv, searchBooks } from '@/lib/nexa-core/academic';
import { searchSpecies } from '@/lib/nexa-core/nature';
import { searchGlobalFacts } from '@/lib/nexa-core/world-knowledge';
import { searchNews } from '@/lib/nexa-core/news';
import { translateText } from '@/lib/nexa-core/translator';
import {
    analyzeEmotion, detectImplicitSignals, recordLearningSignal,
    getLearningInsights, getUserProfile, updateUserProfile, generatePersonalizationContext,
    extractKnowledge, getRelatedKnowledge, analyzeMessageAdvanced
} from '@/lib/nexa-core/machine-learning';
import { detectVisionCategory, getCategoryInstructions } from '@/lib/nexa-core/vision-plus';

export const maxDuration = 120;
export const runtime = 'nodejs';

const corsHeaders = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
};

const PROVIDERS = {
    groq: {
        url: 'https://api.groq.com/openai/v1/chat/completions',
        model: 'llama-3.3-70b-versatile',
        keyEnv: 'GROQ_API_KEY'
    },
    anthropic: {
        url: 'https://api.anthropic.com/v1/messages',
        model: 'claude-3-5-sonnet-20241022',
        keyEnv: 'ANTHROPIC_API_KEY'
    },
    gemini: {
        url: (model: string, key: string) => `https://generativelanguage.googleapis.com/v1beta/models/${model}:streamGenerateContent?alt=sse&key=${key}`,
        model: 'gemini-1.5-flash',
        keyEnv: 'GOOGLE_AI_API_KEY'
    },
    deepseek: {
        url: 'https://api.deepseek.com/chat/completions',
        model: 'deepseek-chat',
        keyEnv: 'DEEPSEEK_API_KEY'
    },
    openai: {
        url: 'https://api.openai.com/v1/chat/completions',
        model: 'gpt-4o-mini',
        keyEnv: 'OPENAI_API_KEY'
    },
    zai: {
        url: 'https://api.z.ai/api/v4/chat/completions',
        model: 'glm-4.7',
        keyEnv: 'ZAI_API_KEY'
    },
    openrouter: {
        url: 'https://openrouter.ai/api/v1/chat/completions',
        model: 'anthropic/claude-3.5-sonnet',
        keyEnv: 'OPENROUTER_API_KEY'
    },
    ollama: {
        url: process.env.OLLAMA_URL || 'http://localhost:11434/v1/chat/completions',
        model: process.env.OLLAMA_MODEL || 'llama3.3',
        keyEnv: 'OLLAMA_API_KEY' // Ollama usually doesn't need a key, but keeping the structure
    },
    dify: {
        url: 'https://api.dify.ai/v1/chat-messages',
        model: 'dify-chatflow',
        keyEnv: 'DIFY_API_KEY'
    }
};

const FALLBACK_ORDER = ['openrouter', 'groq', 'zai', 'anthropic', 'gemini', 'deepseek', 'openai', 'ollama', 'dify'];

// ─── Key Rotation: Soporte para múltiples keys separadas por coma ───
function getKeyList(envValue: string | undefined, envKey?: string): string[] {
    // Collect keys from: KEY, KEY_2, KEY_3 (separate env vars in Vercel)
    const keys: string[] = [];
    
    // Also check _2 and _3 suffixed versions
    if (envKey) {
        const envKeysToCheck = [envKey];
        if (envKey === 'GOOGLE_AI_API_KEY') {
            envKeysToCheck.push('GEMINI_API_KEY');
        }
        
        for (const currentKey of envKeysToCheck) {
            for (let i = 1; i <= 5; i++) {
                const suffix = i === 1 ? '' : `_${i}`;
                const val = process.env[`${currentKey}${suffix}`];
                if (val) {
                    // Support comma-separated within each var too
                    val.split(',').map(k => k.trim()).filter(k => k.length > 0).forEach(k => keys.push(k));
                }
            }
        }
        if (keys.length > 0) return keys;
    }
    
    if (!envValue) return [];
    return envValue.split(',').map(k => k.trim()).filter(k => k.length > 0);
}

function getRandomKey(envValue: string | undefined, envKey?: string): string | undefined {
    const keys = getKeyList(envValue, envKey);
    if (keys.length === 0) return undefined;
    return keys[Math.floor(Math.random() * keys.length)];
}


function createStream(requestId: string, messages: any[], keys: Record<string, string | undefined>, userId: string, userQuery: string, toolContext?: string) {
    const encoder = new TextEncoder();
    return new ReadableStream({
        async start(controller) {
            let fullResponse = '';
            
            // Inject tool context into the conversation if available
            if (toolContext) {
                const toolMessage = {
                    role: 'system',
                    content: `[DATOS EN TIEMPO REAL - Usa estos datos para responder al usuario]\n\n${toolContext}\n\nResponde al usuario usando estos datos. Sé natural y conversacional. REGLA DE ORO PARA LA VOZ: NUNCA uses símbolos Markdown (*, #, -, \`, /, \). Tu respuesta será leída por un sintetizador de voz. NO hagas listas con viñetas, redacta todo en prosa fluida y continua. Escribe los números y precios de forma natural (ej. '400 dólares').`
                };
                // Insert tool message before the last user message
                const lastUserIdx = messages.map(m => m.role).lastIndexOf('user');
                if (lastUserIdx >= 0) {
                    messages.splice(lastUserIdx, 0, toolMessage);
                } else {
                    messages.push(toolMessage);
                }
            }
            
            for (const providerKey of FALLBACK_ORDER) {
                const config = (PROVIDERS as any)[providerKey];
                const keyList = getKeyList(keys[config.keyEnv], config.keyEnv);
                if (keyList.length === 0) continue;
                
                // Intentar cada key del proveedor antes de pasar al siguiente
                for (let keyIdx = 0; keyIdx < keyList.length; keyIdx++) {
                const key = keyList[keyIdx];
                try {
                    logger.info(`Attempting chat with ${providerKey}`, 'chat', { requestId });
                    
        // FIX: Timeout helper to prevent slow providers from blocking all fallbacks
        const fetchWithTimeout = (url: string, options: any, timeoutMs = 20000): Promise<Response> => {
            const controller = new AbortController();
            const timeout = setTimeout(() => controller.abort(), timeoutMs);
            return Promise.race([
                fetch(url, { ...options, signal: controller.signal }),
                new Promise<Response>((_, reject) => setTimeout(() => {
                    clearTimeout(timeout);
                    reject(new Error(`Timeout: ${timeoutMs}ms`));
                }, timeoutMs))
            ]).finally(() => clearTimeout(timeout));
        };

                    // OpenAI Compatible (Groq, DeepSeek, OpenAI, Z.ai, OpenRouter, Ollama)
                    if (['groq', 'deepseek', 'openai', 'zai', 'openrouter', 'ollama'].includes(providerKey)) {
                        const res = await fetchWithTimeout(config.url, {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${key}` },
                            body: JSON.stringify({ model: config.model, messages, stream: true, temperature: 0.7, max_tokens: 4096 }),
                        });
                        if (res.ok && res.body) {
                            const reader = res.body.getReader();
                            while (true) {
                                const { done, value } = await reader.read();
                                if (done) break;
                                const chunk = new TextDecoder().decode(value);
                                for (const line of chunk.split('\n')) {
                                    if (line.startsWith('data: ') && !line.includes('[DONE]')) {
                                        try {
                                            const content = JSON.parse(line.slice(6)).choices?.[0]?.delta?.content || '';
                                            if (content) {
                                                fullResponse += content;
                                                controller.enqueue(encoder.encode(`data: ${JSON.stringify({ text: content, provider: providerKey })}\n\n`));
                                            }
                                        } catch {}
                                    }
                                }
                            }
                            controller.enqueue(encoder.encode(`data: ${JSON.stringify({ done: true, fullResponse, provider: providerKey })}\n\n`));
                            extractAndSaveSkills(userId, userQuery, fullResponse).catch(console.error);
                            controller.close();
                            return;
                        }
                    } 
                    // Anthropic Logic
                    else if (providerKey === 'anthropic') {
                        const systemMessage = messages.find(m => m.role === 'system')?.content;
                        const userMessages = messages.filter(m => m.role !== 'system');
                        const res = await fetchWithTimeout(config.url, {
                            method: 'POST',
                            headers: {
                                'Content-Type': 'application/json',
                                'x-api-key': key,
                                'anthropic-version': '2023-06-01'
                            },
                            body: JSON.stringify({ 
                                model: config.model, 
                                system: systemMessage,
                                messages: userMessages, 
                                stream: true, 
                                max_tokens: 4096 
                            }),
                        });
                        if (res.ok && res.body) {
                            const reader = res.body.getReader();
                            while (true) {
                                const { done, value } = await reader.read();
                                if (done) break;
                                const chunk = new TextDecoder().decode(value);
                                for (const line of chunk.split('\n')) {
                                    if (line.startsWith('data: ')) {
                                        try {
                                            const data = JSON.parse(line.slice(6));
                                            const content = data.delta?.text || '';
                                            if (content) {
                                                fullResponse += content;
                                                controller.enqueue(encoder.encode(`data: ${JSON.stringify({ text: content, provider: 'anthropic' })}\n\n`));
                                            }
                                        } catch {}
                                    }
                                }
                            }
                            controller.enqueue(encoder.encode(`data: ${JSON.stringify({ done: true, fullResponse, provider: 'anthropic' })}\n\n`));
                            extractAndSaveSkills(userId, userQuery, fullResponse).catch(console.error);
                            controller.close();
                            return;
                        }
                    }
                    // Gemini Multimodal Logic
                    else if (providerKey === 'gemini') {
                        const contents = messages.filter(m => m.role !== 'system').map(m => {
                            const parts = [];
                            // Si el contenido tiene una imagen (asumimos formato [IMAGE:base64])
                            const imageMatch = m.content.match(/\[IMAGE:(.*?)\]/);
                            if (imageMatch) {
                                const base64 = imageMatch[1];
                                const textQuery = m.content.replace(/\[IMAGE:.*?\]/, '').trim();
                                
                                const category = detectVisionCategory(textQuery);
                                const visionInstructions = getCategoryInstructions(category);
                                
                                parts.push({ inlineData: { mimeType: "image/jpeg", data: base64 } });
                                parts.push({ text: (textQuery || "Describe esta imagen. Si hay texto, transcríbelo.") + `\n\n[SISTEMA DE VISIÓN]: ${visionInstructions}` });
                            } else {
                                parts.push({ text: m.content });
                            }
                            return { role: m.role === 'assistant' ? 'model' : 'user', parts };
                        });

                        const res = await fetchWithTimeout(config.url(config.model, key), {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ contents }),
                        });
                        if (res.ok && res.body) {
                            const reader = res.body.getReader();
                            // ... resto del stream
                            while (true) {
                                const { done, value } = await reader.read();
                                if (done) break;
                                const chunk = new TextDecoder().decode(value);
                                for (const line of chunk.split('\n')) {
                                    if (line.startsWith('data: ')) {
                                        try {
                                            const content = JSON.parse(line.slice(6)).candidates?.[0]?.content?.parts?.[0]?.text || '';
                                            if (content) {
                                                fullResponse += content;
                                                controller.enqueue(encoder.encode(`data: ${JSON.stringify({ text: content, provider: 'gemini' })}\n\n`));
                                            }
                                        } catch {}
                                    }
                                }
                            }
                            controller.enqueue(encoder.encode(`data: ${JSON.stringify({ done: true, fullResponse, provider: 'gemini' })}\n\n`));
                            extractAndSaveSkills(userId, userQuery, fullResponse).catch(console.error);
                            controller.close();
                            return;
                        }
                    }
                    // Dify Streaming Logic
                    else if (providerKey === 'dify') {
                        logger.info(`[CHAT-API] Routing fallback stream to Dify API...`, 'chat', { requestId });
                        const res = await fetchWithTimeout(config.url, {
                            method: 'POST',
                            headers: {
                                'Content-Type': 'application/json',
                                'Authorization': `Bearer ${key}`
                            },
                            body: JSON.stringify({
                                inputs: {},
                                query: userQuery,
                                response_mode: 'streaming',
                                user: userId
                            }),
                        });
                        
                        if (res.ok && res.body) {
                            const reader = res.body.getReader();
                            let buffer = '';
                            while (true) {
                                const { done, value } = await reader.read();
                                if (done) break;
                                buffer += new TextDecoder().decode(value, { stream: true });
                                const parts = buffer.split('\n\n');
                                buffer = parts.pop() ?? '';
                                for (const part of parts) {
                                    const line = part.trim();
                                    if (line.startsWith('data: ')) {
                                        try {
                                            const data = JSON.parse(line.slice(6));
                                            if (data.event === 'message' && data.answer) {
                                                const content = data.answer;
                                                fullResponse += content;
                                                controller.enqueue(encoder.encode(`data: ${JSON.stringify({ text: content, provider: 'dify' })}\n\n`));
                                            }
                                        } catch {}
                                    }
                                }
                            }
                            controller.enqueue(encoder.encode(`data: ${JSON.stringify({ done: true, fullResponse, provider: 'dify' })}\n\n`));
                            extractAndSaveSkills(userId, userQuery, fullResponse).catch(console.error);
                            controller.close();
                            return;
                        }
                    }
                } catch (e: any) {
                    logger.warn(`Provider ${providerKey} key[${keyIdx}] failed: ${e.message}`, 'chat', { requestId });
                    continue; // Try next key for same provider
                }
                } // end key rotation loop
            }
            controller.enqueue(encoder.encode(`data: ${JSON.stringify({ error: 'Todos los proveedores fallaron.' })}\n\n`));
            controller.close();
        }
    });
}

export async function OPTIONS() { return new Response(null, { headers: corsHeaders }); }

export async function POST(req: NextRequest) {
    const requestId = generateRequestId();

    // --- Rate Limiting ---
    const identifier = getIdentifier(req);
    const rateLimitResult = await checkRateLimit(identifier, RATE_LIMITS.chat);
    if (!rateLimitResult.allowed) {
        return NextResponse.json(
            { error: 'Demasiadas solicitudes. Intentá de nuevo en unos segundos.', retryAfterMs: rateLimitResult.retryAfterMs },
            { status: 429, headers: { ...corsHeaders, 'Retry-After': String(Math.ceil((rateLimitResult.retryAfterMs || 60000) / 1000)) } }
        );
    }

    try {
        const body = await req.json().catch(() => null);
        if (!body) return NextResponse.json({ error: 'Body vacío' }, { status: 400, headers: corsHeaders });
        const parsed = chatSchema.safeParse(body);
        if (!parsed.success) return NextResponse.json({ error: 'Formato inválido' }, { status: 400, headers: corsHeaders });
        let { messages, mode = 'default', latitude, longitude, city, country } = parsed.data;
        
        // --- NEXA INTELLIGENCE HUB (V3 - HIGH PRIORITY) ---
        const userQuery = messages[messages.length - 1].content;
        const lowerQuery = userQuery.toLowerCase();
        let toolContext = "";

        // 0. CONTEXTO DE UBICACIÓN Y TIEMPO (Auto-Inyectado)
        // v5.1: Prioritize client-side GPS location over IP-based location.
        // The Android app sends real GPS coordinates; ip-api only gives
        // the Vercel CDN server location, which is useless for the user.
        let userCity = city || '';
        let userCountry = country || '';
        let userLat = latitude;
        let userLon = longitude;
        
        // Get client IP for geolocation and user identification
        const clientIp = req.headers.get('x-forwarded-for')?.split(',')[0]?.trim()
            || req.headers.get('x-real-ip')
            || undefined;
        
        // If client didn't send GPS data, fall back to IP geolocation
        if (!userLat || !userLon) {
            const ipLocation = await getUserLocation(clientIp);
            if (ipLocation) {
                if (!userCity) userCity = ipLocation.city;
                if (!userCountry) userCountry = ipLocation.country;
                if (!userLat) userLat = ipLocation.lat;
                if (!userLon) userLon = ipLocation.lon;
            }
        }
        
        // Get timezone from coordinates or default to UTC
        let timeStr = '';
        try {
            // Try to determine timezone from coordinates
            const tzResponse = await fetch(
                `https://nominatim.openstreetmap.org/reverse?lat=${userLat}&lon=${userLon}&format=json&zoom=5`,
                { headers: { 'User-Agent': 'NexaAssistant/1.0' } }
            ).catch(() => null);
            const tzData = tzResponse ? await tzResponse.json().catch(() => null) : null;
            timeStr = await getLocalTime(tzData?.timezone || undefined);
        } catch {
            timeStr = await getLocalTime();
        }
        
        toolContext += `[CONTEXTO ACTUAL DEL USUARIO]:
Ubicación: ${userCity || 'Desconocida'}, ${userCountry || 'Desconocida'}${userLat && userLon ? ` (${userLat}, ${userLon})` : ''}
Hora Local: ${timeStr}
--------------------------------------------------\n\n`;

        // --- NEXA ML ENGINE V1 (Machine Learning) ---
        // Derive userId from client IP or custom header (no auth system yet).
        const userId = identifier;
        
        // 1. Emotion Analysis
        const emotion = analyzeEmotion(userQuery);
        
        // 2. Advanced NLP Analysis
        const nlpResult = await analyzeMessageAdvanced(userQuery);
        
        // 3. Get User Profile (learned preferences)
        const userProfile = await getUserProfile(userId);
        
        // 4. Update profile with current interaction
        await updateUserProfile(userId, userQuery, emotion, nlpResult.topics);
        
        // 5. Detect implicit learning signals
        const previousAssistantMsg = messages.filter(m => m.role === 'assistant').slice(-1)[0]?.content || '';
        const implicitSignals = detectImplicitSignals(userQuery, previousAssistantMsg, emotion);
        for (const signal of implicitSignals) {
            await recordLearningSignal(userId, signal);
        }
        
        // 6. Get learning insights
        const learningInsights = await getLearningInsights(userId);
        
        // 7. Get related knowledge from knowledge graph
        let knowledgeContext = '';
        if (nlpResult.topics.length > 0) {
            for (const topic of nlpResult.topics.slice(0, 3)) {
                const knowledge = await getRelatedKnowledge(userId, topic);
                if (knowledge) knowledgeContext += knowledge + '\n';
            }
        }
        
        // 8. Extract knowledge in background (non-blocking)
        extractKnowledge(userId, userQuery).catch(() => {});
        
        // 9. Generate personalization context
        const personalizationContext = generatePersonalizationContext(userProfile, emotion);
        
        // Add ML context to toolContext
        toolContext += `[INTELIGENCIA DE NEXA - ML ENGINE]:
Emoción detectada: ${emotion.primary} (${Math.round(emotion.intensity * 100)}%)${emotion.secondary ? ` + ${emotion.secondary}` : ''}
Intención: ${nlpResult.intent}
Temas: ${nlpResult.topics.join(', ') || 'general'}
Urgencia: ${nlpResult.urgency}
Complejidad: ${nlpResult.complexity}
${personalizationContext ? `Personalización:\n${personalizationContext}` : ''}
${learningInsights.recommendation ? `Aprendizaje: ${learningInsights.recommendation}` : ''}
${knowledgeContext ? `Conocimiento previo:\n${knowledgeContext}` : ''}
Interacciones totales: ${userProfile.interaction_count}
--------------------------------------------------\n\n`;

        // --- DETECTOR DE INTENCIONES AVANZADO (NEXA BRAIN V4) ---
        const groqKey = getRandomKey(process.env.GROQ_API_KEY, 'GROQ_API_KEY');
        let selectedTools: string[] = [];
        if (groqKey) {
            try {
                const intentRes = await fetch('https://api.groq.com/openai/v1/chat/completions', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${groqKey}` },
                    body: JSON.stringify({
                        model: 'llama-3.1-8b-instant', // Usamos el modelo más rápido de Groq
                        messages: [{ 
                            role: 'system', 
                            content: 'Analiza la pregunta e identifica herramientas necesarias: [movies, nasa, science, books, finance, flights, lottery, weather, knowledge, social, music, maps, nature, encyclopedia, news, preview, translate, stackoverflow, programming]. Responde solo con una lista separada por comas o "none".'
                        }, { role: 'user', content: userQuery }],
                        max_tokens: 20
                    }),
                });
                const intentData = await intentRes.json();
                const intentText = intentData.choices[0].message.content.toLowerCase();
                selectedTools = intentText.split(',').map((t: string) => t.trim());
            } catch {}
        }

        // Ejecución proactiva basada en intención
        if (selectedTools.includes('music')) toolContext += await searchSpotify(userQuery) + "\n";
        if (selectedTools.includes('science')) toolContext += await searchArXiv(userQuery) + "\n";
        if (selectedTools.includes('books')) toolContext += await searchBooks(userQuery) + "\n";
        if (selectedTools.includes('maps')) toolContext += await searchPlace(userQuery) + "\n";
        if (selectedTools.includes('nature')) toolContext += await searchSpecies(userQuery) + "\n";
        if (selectedTools.includes('encyclopedia')) toolContext += await searchGlobalFacts(userQuery) + "\n";
        if (selectedTools.includes('news')) toolContext += await searchNews(userQuery) + "\n";
        if (selectedTools.includes('preview')) toolContext += "\n[SISTEMA DE PREVIEW]: Puedes generar previsualizaciones HTML/CSS/JS. Pide al usuario que abra el link generado.\n";
        if (selectedTools.includes('translate')) {
            const targetLang = userQuery.match(/a (la|el| )?([a-zA-Z]+)$/i)?.[2] || 'inglés';
            toolContext += `\n[SISTEMA DE TRADUCCIÓN]: Traduciendo a ${targetLang}. Resultado: ${await translateText(userQuery, targetLang)}\n`;
        }

        // 1. CLIMA
        if (lowerQuery.includes('clima') || lowerQuery.includes('tiempo') || lowerQuery.includes('weather') || lowerQuery.includes('temperatura')) {
            try {
                const extractionRes = await fetch('https://api.groq.com/openai/v1/chat/completions', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${getRandomKey(process.env.GROQ_API_KEY, 'GROQ_API_KEY')}` },
                    body: JSON.stringify({
                        model: 'llama-3.3-70b-versatile',
                        messages: [{ role: 'system', content: 'Extract city in JSON: {"city": "Name"}. Only JSON.' }, { role: 'user', content: userQuery }],
                        response_format: { type: "json_object" }
                    }),
                });
                const info = JSON.parse((await extractionRes.json()).choices[0].message.content);
                if (info.city) toolContext += await getWeather(info.city) + "\n";
            } catch {}
        }

        // 2. IMÁGENES (Detección Ultra-Sensible)
        const triggerImages = ['dibuja', 'genera', 'diseña', 'crea', 'imagina', 'muestra', 'muéstrame', 'foto', 'imagen', 'ver', 'mira', 'playa', 'sol'];
        if (triggerImages.some(kw => new RegExp(`\\b${kw}\\b`, 'i').test(lowerQuery))) {
            try {
                const promptRes = await fetch('https://api.groq.com/openai/v1/chat/completions', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${getRandomKey(process.env.GROQ_API_KEY, 'GROQ_API_KEY')}` },
                    body: JSON.stringify({
                        model: 'llama-3.3-70b-versatile',
                        messages: [{ role: 'system', content: 'Crea un prompt descriptivo en inglés para DALL-E basado en el pedido del usuario. Solo el prompt.' }, { role: 'user', content: userQuery }],
                    }),
                });
                const cleanPrompt = promptRes.ok ? (await promptRes.json()).choices[0].message.content : userQuery;
                const imageResult = await generateImage(cleanPrompt);
                toolContext += `RESULTADO GENERACIÓN IMAGEN: ${imageResult}\n`;
            } catch {}
        }

        // 3. VUELOS (Estado, Precios y Calendario)
        if (lowerQuery.includes('vuelo') || lowerQuery.includes('viaje') || lowerQuery.includes('pasaje') || lowerQuery.includes('avión') || lowerQuery.includes('boleto') || lowerQuery.includes('aerolinea') || lowerQuery.includes('aerolínea') || lowerQuery.includes('avion')) {
            try {
                const extractionRes = await fetch('https://api.groq.com/openai/v1/chat/completions', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${getRandomKey(process.env.GROQ_API_KEY, 'GROQ_API_KEY')}` },
                    body: JSON.stringify({
                        model: 'llama-3.3-70b-versatile',
                        messages: [{ role: 'system', content: 'Extract IATA origin/dest, date and optional return date (YYYY-MM-DD): {"origin": "IATA", "destination": "IATA", "date": "YYYY-MM-DD", "returnDate": "YYYY-MM-DD or null"}. If origin not specified use LAS.' }, { role: 'user', content: userQuery }],
                        response_format: { type: "json_object" }
                    }),
                });
                const info = JSON.parse((await extractionRes.json()).choices[0].message.content);
                
                if (info.destination) {
                    const flightDate = info.date || new Date().toISOString().split('T')[0];
                    
                    // 1. Google Flights (precios + links de reserva)
                    try {
                        toolContext += await searchGoogleFlights(info.origin || 'LAS', info.destination, flightDate, info.returnDate) + "\n";
                    } catch {}
                    
                    // 2. Skyscanner (más opciones + deep links)
                    try {
                        toolContext += await searchSkyscannerFlights(info.origin || 'LAS', info.destination, flightDate) + "\n";
                    } catch {}
                    
                    // 3. Estado de vuelos en tiempo real
                    try {
                        toolContext += await searchFlights(info.origin || 'LAS', info.destination) + "\n";
                    } catch {}
                    
                    // 4. Calendario de precios (±3 días como Google Flights)
                    // Only fetch if SERPAPI_KEY is available and query seems like a price comparison
                    const wantsCalendar = lowerQuery.includes('precio') || lowerQuery.includes('barato') || lowerQuery.includes('más barato') || lowerQuery.includes('mas barato') || lowerQuery.includes('calendario') || lowerQuery.includes('comparar') || lowerQuery.includes('días') || lowerQuery.includes('dias') || lowerQuery.includes('mejor fecha') || lowerQuery.includes('cuando') || lowerQuery.includes('cuándo');
                    
                    if (wantsCalendar && (process.env.SERPAPI_KEY || process.env.GOOGLE_FLIGHTS_API_KEY)) {
                        try {
                            const { report: calendarReport } = await searchPriceCalendar(
                                info.origin || 'LAS', 
                                info.destination, 
                                flightDate, 
                                info.returnDate
                            );
                            toolContext += calendarReport + "\n";
                        } catch {}
                    }
                }
            } catch {}
        }

        // 4. WOLFRAM ALPHA (Ciencia, Datos, Matemáticas)
        const triggerWolfram = ['cuanto es', 'cuánto es', 'qué es', 'que es', 'quién es', 'quien es', 'distancia', 'masa', 'población', 'capital de'];
        if (triggerWolfram.some(kw => lowerQuery.includes(kw))) {
            try {
                const answer = await getWolframAnswer(userQuery);
                if (answer && !answer.includes('Error')) {
                    toolContext += `DATOS EXACTOS (WolframAlpha): ${answer}\n`;
                }
            } catch {}
        }

        // 5. PELÍCULAS Y SERIES (TMDB)
        const triggerMovies = ['película', 'serie', 'actor', 'director', 'estreno', 'reparto', 'quien sale en'];
        if (triggerMovies.some(kw => lowerQuery.includes(kw))) {
            try {
                const report = await searchMovies(userQuery);
                if (report && !report.includes('Error')) {
                    toolContext += `${report}\n`;
                }
            } catch {}
        }

        // 6. ESPACIO Y NASA
        const triggerSpace = ['nasa', 'espacio', 'marte', 'universo', 'estrella', 'galaxia', 'planeta'];
        if (triggerSpace.some(kw => lowerQuery.includes(kw))) {
            try {
                if (lowerQuery.includes('marte')) {
                    toolContext += await searchMarsPhotos() + "\n";
                } else {
                    toolContext += await getNASAAPOD() + "\n";
                }
            } catch {}
        }

        // 7. FINANZAS (Cripto y Bolsa)
        const triggerFinance = ['precio de', 'cotización', 'cuanto vale', 'cuánto vale', 'bitcoin', 'ethereum', 'btc', 'eth', 'bolsa', 'acción', 'accion'];
        if (triggerFinance.some(kw => lowerQuery.includes(kw))) {
            try {
                if (lowerQuery.includes('bitcoin') || lowerQuery.includes('btc')) {
                    toolContext += await getCryptoPrice('bitcoin') + "\n";
                } else if (lowerQuery.includes('ethereum') || lowerQuery.includes('eth')) {
                    toolContext += await getCryptoPrice('ethereum') + "\n";
                } else {
                    // Intenta extraer símbolo de bolsa (ej: AAPL, TSLA)
                    const symbolMatch = userQuery.match(/\b[A-Z]{3,5}\b/);
                    if (symbolMatch) {
                        toolContext += await getStockPrice(symbolMatch[0]) + "\n";
                    }
                }
            } catch {}
        }

        // 8. LOTERIA (Magayo API — resultados, proximo sorteo, generador)
        const triggerLottery = ['lotería', 'loteria', 'sorteo', 'powerball', 'megamillions', 'melate', 'chispazo', 'baloto', 'euromillones', 'el gordo', 'jackpot'];
        if (triggerLottery.some(kw => lowerQuery.includes(kw))) {
            try {
                // Mapeo de juegos comunes
                let game = 'us_powerball';
                if (lowerQuery.includes('mega')) game = 'us_megamillions';
                if (lowerQuery.includes('melate') && !lowerQuery.includes('retro')) game = 'mx_melate';
                if (lowerQuery.includes('chispazo')) game = 'mx_chispazo';
                if (lowerQuery.includes('retro')) game = 'mx_melate_retro';
                if (lowerQuery.includes('baloto') || lowerQuery.includes('super baloto')) game = 'co_baloto';
                if (lowerQuery.includes('euromill') || lowerQuery.includes('euro mill')) game = 'eu_euromillions';
                if (lowerQuery.includes('el gordo')) game = 'es_el_gordo';
                if (lowerQuery.includes('nacional') && lowerQuery.includes('españ')) game = 'es_loteria_nacional';
                if (lowerQuery.includes('uk lotto') || lowerQuery.includes('británica')) game = 'uk_lotto';

                // Si pregunta por juegos disponibles
                if (lowerQuery.includes('qué juegos') || lowerQuery.includes('que juegos') || lowerQuery.includes('disponibles') || lowerQuery.includes('cuáles hay')) {
                    toolContext += getAvailableGames() + "\n";
                }
                // Si pide generar numeros
                else if (lowerQuery.includes('genera') || lowerQuery.includes('números') || lowerQuery.includes('numeros') || lowerQuery.includes('recomienda') || lowerQuery.includes('sugerencia')) {
                    toolContext += await generateLotteryNumbers(game) + "\n";
                }
                // Si pregunta por proximo sorteo
                else if (lowerQuery.includes('próximo') || lowerQuery.includes('proximo') || lowerQuery.includes('cuándo') || lowerQuery.includes('cuando') || lowerQuery.includes('siguiente sorteo')) {
                    toolContext += await getNextDraw(game) + "\n";
                    toolContext += await getLotteryResults(game) + "\n";
                }
                // Default: mostrar resultados
                else {
                    toolContext += await getLotteryResults(game) + "\n";
                }
            } catch {}
        }

        // 9. ENCICLOPEDIA (Wikipedia y Países)
        const triggerWiki = ['quien es', 'quién es', 'qué es', 'que es', 'significa', 'biografía', 'historia de'];
        if (triggerWiki.some(kw => lowerQuery.includes(kw))) {
            try {
                const topic = userQuery.replace(/quien es|quién es|qué es|que es|dime sobre|háblame de/gi, "").trim();
                toolContext += await searchWikipedia(topic) + "\n";
            } catch {}
        }

        const triggerCountry = ['población de', 'capital de', 'moneda de', 'continente de', 'país', 'pais'];
        if (triggerCountry.some(kw => lowerQuery.includes(kw))) {
            try {
                const country = userQuery.match(/de\s+([A-Z][a-z]+)/)?.[1] || userQuery.split(' ').pop();
                if (country) toolContext += await getCountryData(country) + "\n";
            } catch {}
        }

        // 10. REPARADOR DE CÓDIGO (Auditoría y Arreglo)
        const triggerRepair = ['repara', 'arregla', 'audita', 'optimiza', 'qué está mal', 'que esta mal', 'check code'];
        if (triggerRepair.some(kw => lowerQuery.includes(kw)) || userQuery.includes('```')) {
            try {
                // Si el mensaje contiene un bloque de código, lo extraemos, si no usamos todo el mensaje
                const codeBlock = userQuery.match(/```[\s\S]*?```/)?.[0] || userQuery;
                toolContext += await auditCode(codeBlock) + "\n";
            } catch {}
        }

        // 10b. STACKOVERFLOW (Programming Q&A)
        const triggerStackOverflow = ['stackoverflow', 'error en', 'bug en', 'no funciona', 'how to', 'cómo hacer', 'como hacer', 'problema con', 'issue con', 'debug', 'fix error', 'código error', 'codigo error', 'excepción', 'exception', 'TypeError', 'ReferenceError', 'SyntaxError', 'ImportError', 'ModuleNotFoundError'];
        if (triggerStackOverflow.some(kw => lowerQuery.includes(kw)) || selectedTools.includes('stackoverflow') || selectedTools.includes('programming')) {
            try {
                // Extract programming language tags from query
                const langTags: string[] = [];
                const langMap: Record<string, string> = {
                    'javascript': 'javascript', 'js': 'javascript', 'typescript': 'typescript', 'ts': 'typescript',
                    'python': 'python', 'py': 'python', 'java': 'java', 'kotlin': 'kotlin', 'swift': 'swift',
                    'rust': 'rust', 'go': 'go', 'golang': 'go', 'c++': 'c++', 'c#': 'c#', 'ruby': 'ruby',
                    'php': 'php', 'react': 'react', 'vue': 'vue.js', 'angular': 'angular', 'node': 'node.js',
                    'nextjs': 'next.js', 'next.js': 'next.js', 'android': 'android', 'flutter': 'flutter',
                    'css': 'css', 'html': 'html', 'sql': 'sql', 'docker': 'docker', 'git': 'git',
                };
                for (const [keyword, tag] of Object.entries(langMap)) {
                    if (lowerQuery.includes(keyword)) langTags.push(tag);
                }
                // Search with tags if found, otherwise plain text
                if (langTags.length > 0) {
                    toolContext += await searchByTags(langTags.slice(0, 3), userQuery) + "\n";
                } else {
                    toolContext += await searchStackOverflow(userQuery) + "\n";
                }
            } catch {}
        }

        // 11. VIDEO GENERATION (AI-powered video creation)
        const triggerVideoGen = ['genera video', 'generar video', 'crea video', 'crear video', 'haz un video', 'generate video', 'create video', 'make a video', 'animación de', 'animacion de'];
        if (triggerVideoGen.some(kw => lowerQuery.includes(kw))) {
            try {
                const videoPrompt = userQuery.replace(/genera video|generar video|crea video|crear video|haz un video|generate video|create video|make a video|animación de|animacion de/gi, "").trim();
                const style = lowerQuery.includes('anime') ? 'anime' :
                              lowerQuery.includes('3d') ? '3d' :
                              lowerQuery.includes('realista') || lowerQuery.includes('realistic') ? 'realistic' :
                              lowerQuery.includes('artístico') || lowerQuery.includes('artistic') ? 'artistic' : 'cinematic';
                const duration = lowerQuery.includes('10 segundo') || lowerQuery.includes('10 second') ? 10 :
                                 lowerQuery.includes('3 segundo') || lowerQuery.includes('3 second') ? 3 : 5;
                const aspectRatio = lowerQuery.includes('vertical') || lowerQuery.includes('9:16') ? '9:16' :
                                    lowerQuery.includes('cuadrado') || lowerQuery.includes('1:1') ? '1:1' : '16:9';
                toolContext += await generateVideo({ prompt: videoPrompt, duration, aspectRatio, style }) + "\n";
            } catch {}
        }

        // 11b. VIDEO SEARCH (searching for existing videos)
        const triggerVideoSearch = ['video de', 'clip de', 'metraje de', 'vídeo de', 'busca video', 'search video'];
        if (triggerVideoSearch.some(kw => lowerQuery.includes(kw))) {
            try {
                const topic = userQuery.replace(/video de|clip de|metraje de|vídeo de|busca video|search video/gi, "").trim();
                toolContext += await searchVideos(topic) + "\n";
            } catch {}
        }

        const triggerLib = ['librería', 'libreria', 'repo de', 'código de', 'github de', 'biblioteca de'];
        if (triggerLib.some(kw => lowerQuery.includes(kw))) {
            try {
                const query = userQuery.replace(/librería|libreria|repo de|biblioteca de/gi, "").trim();
                toolContext += await searchLibraries(query) + "\n";
            } catch {}
        }

        // 12. REDES SOCIALES (Reddit y YouTube)
        const triggerReddit = ['reddit', 'foro de', 'hilos de', 'que dicen en'];
        if (triggerReddit.some(kw => lowerQuery.includes(kw))) {
            try {
                // Intenta extraer el nombre del subreddit (ej: technology, gaming)
                const subMatch = userQuery.match(/r\/(\w+)/i) || userQuery.match(/(?:en|de)\s+(\w+)/i);
                const sub = subMatch ? subMatch[1] : 'technology';
                toolContext += await searchReddit(sub) + "\n";
            } catch {}
        }

        const triggerYT = ['youtube', 'video tutorial', 'música', 'musica', 'ver video de'];
        if (triggerYT.some(kw => lowerQuery.includes(kw))) {
            try {
                const query = userQuery.replace(/youtube|ver video de|busca en youtube/gi, "").trim();
                toolContext += await searchYouTube(query) + "\n";
            } catch {}
        }

        const triggerSpotify = ['spotify', 'canción de', 'cancion de', 'álbum de', 'album de', 'playlist de', 'escuchar a'];
        if (triggerSpotify.some(kw => lowerQuery.includes(kw))) {
            try {
                let type: 'track' | 'playlist' | 'album' = 'track';
                if (lowerQuery.includes('playlist')) type = 'playlist';
                if (lowerQuery.includes('album') || lowerQuery.includes('álbum')) type = 'album';
                
                const query = userQuery.replace(/spotify|canción de|cancion de|álbum de|album de|playlist de|escuchar a/gi, "").trim();
                toolContext += await searchSpotify(query, type) + "\n";
            } catch {}
        }

        // 13. FOTOS DE ALTA CALIDAD (Unsplash)
        const triggerPhotos = ['foto de', 'imagen de', 'paisaje de', 'fotografía de', 'fotografia de', 'unsplash'];
        if (triggerPhotos.some(kw => lowerQuery.includes(kw)) && !lowerQuery.includes('crea') && !lowerQuery.includes('genera')) {
            try {
                const query = userQuery.replace(/foto de|imagen de|paisaje de|fotografía de|fotografia de|unsplash/gi, "").trim();
                toolContext += await searchPhotos(query) + "\n";
            } catch {}
        }

        // 14. MAPAS Y LUGARES (Cartógrafo)
        const triggerMaps = ['mapa de', 'dónde queda', 'donde queda', 'ubicación de', 'ubicacion de', 'dirección de', 'direccion de', 'lugar'];
        if (triggerMaps.some(kw => lowerQuery.includes(kw))) {
            try {
                const query = userQuery.replace(/mapa de|dónde queda|donde queda|ubicación de|ubicacion de|dirección de|direccion de/gi, "").trim();
                toolContext += await searchPlace(query) + "\n";
            } catch {}
        }

        // 15. ACADÉMICO Y LIBROS (ArXiv y Gutenberg)
        const triggerScience = ['artículo de', 'estudio de', 'ciencia de', 'arxiv', 'investigación sobre'];
        if (triggerScience.some(kw => lowerQuery.includes(kw))) {
            try {
                const query = userQuery.replace(/artículo de|estudio de|ciencia de|arxiv|investigación sobre/gi, "").trim();
                toolContext += await searchArXiv(query) + "\n";
            } catch {}
        }

        const triggerBooks = ['libro de', 'novela de', 'literatura de', 'gutenberg', 'leer a'];
        if (triggerBooks.some(kw => lowerQuery.includes(kw))) {
            try {
                const query = userQuery.replace(/libro de|novela de|literatura de|gutenberg|leer a/gi, "").trim();
                toolContext += await searchBooks(query) + "\n";
            } catch {}
        }

        // 10. MEMORIA DE LARGO PLAZO (Recuperación)
        const memories = await getMemories(userId);
        if (memories.length > 0) {
            toolContext += `[MEMORIA DE SESIONES PASADAS - Lo que recuerdas de este usuario]:\n- ${memories.join('\n- ')}\n\n`;
        }

        // 10b. HABILIDADES APRENDIDAS (Skills de Auto-Aprendizaje)
        const userSkills = await getSkills(userId);
        if (userSkills.length > 0) {
            toolContext += `[HABILIDADES APRENDIDAS - Instrucciones autogeneradas por tu aprendizaje continuo]:\n`;
            for (const skill of userSkills) {
                toolContext += `\n[SKILL APRENDIDA: ${skill.name}]\nTrigger: ${skill.description}\nInstrucciones para ti:\n${skill.instructions}\n`;
            }
            toolContext += `\n--------------------------------------------------\n\n`;
        }


        // EXTRACCIÓN DE NUEVOS RECUERDOS (En segundo plano)
        // No bloqueamos la respuesta, se ejecuta asíncronamente
        extractAndSaveFacts(userId, userQuery).catch(console.error);
        logActivity(userId, userCity || 'Unknown', userCountry || 'Unknown', lowerQuery).catch(console.error);

        // INYECCIÓN DE CONTEXTO FINAL (ADJUNTO AL MENSAJE DEL USUARIO)
        if (toolContext) {
            const lastIndex = messages.length - 1;
            // Check if this is a flight-related context
            const isFlightContext = toolContext.includes('GOOGLE FLIGHTS') || toolContext.includes('SKYSCANNER') || toolContext.includes('VUELOS EN TIEMPO REAL') || toolContext.includes('CALENDARIO DE PRECIOS');
            
            let contextInstruction = `[SISTEMA - INFORMACIÓN REAL OBTENIDA]:\n${toolContext}\n\nINSTRUCCIÓN: Usa los datos de arriba para responder. SI HAY UNA IMAGEN, DEBES MOSTRARLA USANDO Markdown: ![Imagen](URL). NO DIGAS QUE NO PUEDES.`;
            
            if (isFlightContext) {
                contextInstruction += `\n\n⚠️ FORMATO DE VUELOS OBLIGATORIO:\n- Cada vuelo DEBE tener un link clicable en formato Markdown: [**Reservar →**](URL)\n- NUNCA muestres URLs como texto plano\n- LOS LINKS SON LO MÁS IMPORTANTE - sin link el usuario no puede comprar\n- Muestra precios en negrita: **$XXX USD**\n- Ordena por precio (más barato primero)\n- Si hay calendario de precios, muéstralo como tabla\n- Al final muestra resumen: 🏆 Mejor precio con link directo`;
            }
            
            messages[lastIndex].content += `\n\n${contextInstruction}`;
        }

        if (!messages.find((m: any) => m.role === 'system')) messages.unshift({ role: 'system', content: getSystemPrompt(mode as any) });
        
        // Keys con soporte de rotación (KEY, KEY_2, KEY_3 en Vercel)
        const keys = { 
            GROQ_API_KEY: process.env.GROQ_API_KEY, 
            GOOGLE_AI_API_KEY: process.env.GOOGLE_AI_API_KEY || process.env.GEMINI_API_KEY, 
            ANTHROPIC_API_KEY: process.env.ANTHROPIC_API_KEY,
            DEEPSEEK_API_KEY: process.env.DEEPSEEK_API_KEY,
            OPENAI_API_KEY: process.env.OPENAI_API_KEY,
            ZAI_API_KEY: process.env.ZAI_API_KEY
        };
        // Log key availability for debugging
        for (const [k, v] of Object.entries(keys)) {
            const count = getKeyList(v, k).length;
            if (count > 0) logger.info(`${k}: ${count} key(s) available`, 'keys');
        }
        const stream = createStream(requestId, messages, keys, userId, userQuery);
        return new Response(stream, { headers: { ...corsHeaders, 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', 'Connection': 'keep-alive' } });
    } catch (e: any) {
        logger.error(`Chat crash: ${e.message}`, 'chat', { requestId });
        return NextResponse.json({ error: 'Error interno' }, { status: 500, headers: corsHeaders });
    }
}

