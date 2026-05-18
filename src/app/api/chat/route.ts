import { NextRequest, NextResponse } from 'next/server';
import { getIdentifier } from '@/lib/nexa-core/rate-limiter';
import { logger, generateRequestId } from '@/lib/nexa-core/logger';
import { chatSchema } from '@/lib/validation';
import { getSystemPrompt } from '@/lib/nexa-core/prompts';
import { detectIntent, executeIntent } from '@/lib/nexa-core/tools';
import { searchFlights } from '@/lib/nexa-core/aviation';
import { getWeather } from '@/lib/nexa-core/weather';
import { generateImage, searchPhotos } from '@/lib/nexa-core/images';
import { getWolframAnswer } from '@/lib/nexa-core/wolfram';
import { searchMovies } from '@/lib/nexa-core/tmdb';
import { getNASAAPOD, searchMarsPhotos } from '@/lib/nexa-core/nasa';
import { getStockPrice, getCryptoPrice } from '@/lib/nexa-core/finance';
import { getLotteryResults } from '@/lib/nexa-core/lottery';
import { searchSkyscannerFlights } from '@/lib/nexa-core/skyscanner';
import { searchWikipedia, getCountryData } from '@/lib/nexa-core/knowledge';
import { getMemories, extractAndSaveFacts, logActivity } from '@/lib/nexa-core/memory';
import { auditCode } from '@/lib/nexa-core/repairer';
import { searchVideos, searchLibraries } from '@/lib/nexa-core/multimedia';
import { searchReddit, searchYouTube } from '@/lib/nexa-core/social';
import { searchSpotify } from '@/lib/nexa-core/spotify';
import { getUserLocation, getLocalTime } from '@/lib/nexa-core/location';
import { searchPlace } from '@/lib/nexa-core/maps';
import { searchArXiv, searchBooks } from '@/lib/nexa-core/academic';
import { searchSpecies } from '@/lib/nexa-core/nature';
import { searchGlobalFacts } from '@/lib/nexa-core/world-knowledge';
import { searchNews, getTopHeadlines } from '@/lib/nexa-core/news';
import { translateText } from '@/lib/nexa-core/translator';

export const maxDuration = 60;
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
    }
};

const FALLBACK_ORDER = ['openrouter', 'groq', 'zai', 'anthropic', 'gemini', 'deepseek', 'openai'];

// ─── Key Rotation: Soporte para múltiples keys separadas por coma ───
function getKeyList(envValue: string | undefined, envKey?: string): string[] {
    // Collect keys from: KEY, KEY_2, KEY_3 (separate env vars in Vercel)
    const keys: string[] = [];
    
    // Also check _2 and _3 suffixed versions
    if (envKey) {
        for (let i = 1; i <= 5; i++) {
            const suffix = i === 1 ? '' : `_${i}`;
            const val = process.env[`${envKey}${suffix}`];
            if (val) {
                // Support comma-separated within each var too
                val.split(',').map(k => k.trim()).filter(k => k.length > 0).forEach(k => keys.push(k));
            }
        }
        return keys;
    }
    
    if (!envValue) return [];
    return envValue.split(',').map(k => k.trim()).filter(k => k.length > 0);
}

function getRandomKey(envValue: string | undefined, envKey?: string): string | undefined {
    const keys = getKeyList(envValue, envKey);
    if (keys.length === 0) return undefined;
    return keys[Math.floor(Math.random() * keys.length)];
}

// ─── Tool Integration: Detect and execute tools before AI responds ───
async function processTools(userMessage: string): Promise<string | null> {
    const intent = detectIntent(userMessage);
    
    // Only process tool-related intents (not chat/code/web/design/analysis/vision)
    const toolTypes = ['weather', 'search', 'geolocation', 'geocode', 'exchange', 'translate', 'news', 'jokes', 'facts', 'time', 'qrcode', 'countries'];
    if (!toolTypes.includes(intent.type)) return null;
    
    try {
        const result = await executeIntent(intent);
        if (result.success && result.output) {
            return result.output;
        }
    } catch (e: any) {
        logger.warn(`Tool execution failed: ${e.message}`, 'tools');
    }
    return null;
}

function createStream(requestId: string, messages: any[], keys: Record<string, string | undefined>, toolContext?: string) {
    const encoder = new TextEncoder();
    return new ReadableStream({
        async start(controller) {
            let fullResponse = '';
            
            // Inject tool context into the conversation if available
            if (toolContext) {
                const toolMessage = {
                    role: 'system',
                    content: `[DATOS EN TIEMPO REAL - Usa estos datos para responder al usuario]\n\n${toolContext}\n\nResponde al usuario usando estos datos. Sé natural y conversacional.`
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
                    
                    // OpenAI Compatible (Groq, DeepSeek, OpenAI, Z.ai, OpenRouter)
                    if (['groq', 'deepseek', 'openai', 'zai', 'openrouter'].includes(providerKey)) {
                        const res = await fetch(config.url, {
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
                            controller.close();
                            return;
                        }
                    } 
                    // Anthropic Logic
                    else if (providerKey === 'anthropic') {
                        const systemMessage = messages.find(m => m.role === 'system')?.content;
                        const userMessages = messages.filter(m => m.role !== 'system');
                        const res = await fetch(config.url, {
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
                                parts.push({ inlineData: { mimeType: "image/jpeg", data: base64 } });
                                parts.push({ text: m.content.replace(/\[IMAGE:.*?\]/, '').trim() || "Describe esta imagen." });
                            } else {
                                parts.push({ text: m.content });
                            }
                            return { role: m.role === 'assistant' ? 'model' : 'user', parts };
                        });

                        const res = await fetch(config.url(config.model, key), {
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
    try {
        const body = await req.json().catch(() => null);
        if (!body) return NextResponse.json({ error: 'Body vacío' }, { status: 400, headers: corsHeaders });
        const parsed = chatSchema.safeParse(body);
        if (!parsed.success) return NextResponse.json({ error: 'Formato inválido' }, { status: 400, headers: corsHeaders });
        let { messages, mode = 'default' } = parsed.data;
        
        // --- NEXA INTELLIGENCE HUB (V3 - HIGH PRIORITY) ---
        const userQuery = messages[messages.length - 1].content;
        const lowerQuery = userQuery.toLowerCase();
        let toolContext = "";

        // 0. CONTEXTO DE UBICACIÓN Y TIEMPO (Auto-Inyectado)
        // Extraer IP real del cliente (no la del servidor Vercel)
        const clientIp = req.headers.get('x-forwarded-for')?.split(',')[0]?.trim()
            || req.headers.get('x-real-ip')
            || undefined;
        const location = await getUserLocation(clientIp);
        const timeStr = await getLocalTime(location?.timezone);
        toolContext += `[CONTEXTO ACTUAL DEL USUARIO]:
Ubicación: ${location?.city || 'Desconocida'}, ${location?.country || 'Desconocida'}
Hora Local: ${timeStr}
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
                            content: 'Analiza la pregunta e identifica herramientas necesarias: [movies, nasa, science, books, finance, flights, lottery, weather, knowledge, social, music, maps, nature, encyclopedia, news, preview, translate]. Responde solo con una lista separada por comas o "none".' 
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
        if (triggerImages.some(kw => lowerQuery.includes(kw))) {
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

        // 3. VUELOS (Estado y Precios)
        if (lowerQuery.includes('vuelo') || lowerQuery.includes('viaje') || lowerQuery.includes('avión') || lowerQuery.includes('avión')) {
            try {
                const extractionRes = await fetch('https://api.groq.com/openai/v1/chat/completions', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${getRandomKey(process.env.GROQ_API_KEY, 'GROQ_API_KEY')}` },
                    body: JSON.stringify({
                        model: 'llama-3.3-70b-versatile',
                        messages: [{ role: 'system', content: 'Extract IATA origin/dest and date (YYYY-MM-DD): {"origin": "IATA", "destination": "IATA", "date": "YYYY-MM-DD"}.' }, { role: 'user', content: userQuery }],
                        response_format: { type: "json_object" }
                    }),
                });
                const info = JSON.parse((await extractionRes.json()).choices[0].message.content);
                
                if (info.destination) {
                    if (lowerQuery.includes('precio') || lowerQuery.includes('barato') || lowerQuery.includes('cuanto cuesta')) {
                        toolContext += await searchSkyscannerFlights(info.origin || 'MEX', info.destination, info.date || new Date().toISOString().split('T')[0]) + "\n";
                    } else {
                        toolContext += await searchFlights(info.origin || 'LAS', info.destination) + "\n";
                    }
                }
            } catch {}
        }

        // 4. WOLFRAM ALPHA (Ciencia, Datos, Matemáticas)
        const triggerWolfram = ['cuanto es', 'cuánto es', 'qué es', 'que es', 'quién es', 'quien es', 'distancia', 'masa', 'población', 'capital de'];
        if (triggerWolfram.some(kw => lowerQuery.includes(kw)) && !toolContext) {
            try {
                const answer = await getWolframAnswer(userQuery);
                if (answer && !answer.includes('Error')) {
                    toolContext += `DATOS EXACTOS (WolframAlpha): ${answer}\n`;
                }
            } catch {}
        }

        // 5. PELÍCULAS Y SERIES (TMDB)
        const triggerMovies = ['película', 'serie', 'actor', 'director', 'estreno', 'reparto', 'quien sale en'];
        if (triggerMovies.some(kw => lowerQuery.includes(kw)) && !toolContext) {
            try {
                const report = await searchMovies(userQuery);
                if (report && !report.includes('Error')) {
                    toolContext += `${report}\n`;
                }
            } catch {}
        }

        // 6. ESPACIO Y NASA
        const triggerSpace = ['nasa', 'espacio', 'marte', 'universo', 'estrella', 'galaxia', 'planeta'];
        if (triggerSpace.some(kw => lowerQuery.includes(kw)) && !toolContext) {
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
        if (triggerFinance.some(kw => lowerQuery.includes(kw)) && !toolContext) {
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

        // 8. LOTERÍA
        const triggerLottery = ['lotería', 'loteria', 'sorteo', 'powerball', 'megamillions', 'melate', 'chispazo'];
        if (triggerLottery.some(kw => lowerQuery.includes(kw)) && !toolContext) {
            try {
                // Mapeo básico de juegos comunes
                let game = 'us_powerball';
                if (lowerQuery.includes('mega')) game = 'us_megamillions';
                if (lowerQuery.includes('melate')) game = 'mx_melate';
                
                toolContext += await getLotteryResults(game) + "\n";
            } catch {}
        }

        // 9. ENCICLOPEDIA (Wikipedia y Países)
        const triggerWiki = ['quien es', 'quién es', 'qué es', 'que es', 'significa', 'biografía', 'historia de'];
        if (triggerWiki.some(kw => lowerQuery.includes(kw)) && !toolContext) {
            try {
                const topic = userQuery.replace(/quien es|quién es|qué es|que es|dime sobre|háblame de/gi, "").trim();
                toolContext += await searchWikipedia(topic) + "\n";
            } catch {}
        }

        const triggerCountry = ['población de', 'capital de', 'moneda de', 'continente de', 'país', 'pais'];
        if (triggerCountry.some(kw => lowerQuery.includes(kw)) && !toolContext) {
            try {
                const country = userQuery.match(/de\s+([A-Z][a-z]+)/)?.[1] || userQuery.split(' ').pop();
                if (country) toolContext += await getCountryData(country) + "\n";
            } catch {}
        }

        // 10. REPARADOR DE CÓDIGO (Auditoría y Arreglo)
        const triggerRepair = ['repara', 'arregla', 'audita', 'optimiza', 'qué está mal', 'que esta mal', 'check code'];
        if ((triggerRepair.some(kw => lowerQuery.includes(kw)) || userQuery.includes('```')) && !toolContext) {
            try {
                // Si el mensaje contiene un bloque de código, lo extraemos, si no usamos todo el mensaje
                const codeBlock = userQuery.match(/```[\s\S]*?```/)?.[0] || userQuery;
                toolContext += await auditCode(codeBlock) + "\n";
            } catch {}
        }

        // 11. MULTIMEDIA Y LIBRERÍAS
        const triggerVideo = ['video de', 'clip de', 'metraje de', 'vídeo de'];
        if (triggerVideo.some(kw => lowerQuery.includes(kw)) && !toolContext) {
            try {
                const topic = userQuery.replace(/video de|clip de|vídeo de/gi, "").trim();
                toolContext += await searchVideos(topic) + "\n";
            } catch {}
        }

        const triggerLib = ['librería', 'libreria', 'repo de', 'código de', 'github de', 'biblioteca de'];
        if (triggerLib.some(kw => lowerQuery.includes(kw)) && !toolContext) {
            try {
                const query = userQuery.replace(/librería|libreria|repo de|biblioteca de/gi, "").trim();
                toolContext += await searchLibraries(query) + "\n";
            } catch {}
        }

        // 12. REDES SOCIALES (Reddit y YouTube)
        const triggerReddit = ['reddit', 'foro de', 'hilos de', 'que dicen en'];
        if (triggerReddit.some(kw => lowerQuery.includes(kw)) && !toolContext) {
            try {
                // Intenta extraer el nombre del subreddit (ej: technology, gaming)
                const subMatch = userQuery.match(/r\/(\w+)/i) || userQuery.match(/(?:en|de)\s+(\w+)/i);
                const sub = subMatch ? subMatch[1] : 'technology';
                toolContext += await searchReddit(sub) + "\n";
            } catch {}
        }

        const triggerYT = ['youtube', 'video tutorial', 'música', 'musica', 'ver video de'];
        if (triggerYT.some(kw => lowerQuery.includes(kw)) && !toolContext) {
            try {
                const query = userQuery.replace(/youtube|ver video de|busca en youtube/gi, "").trim();
                toolContext += await searchYouTube(query) + "\n";
            } catch {}
        }

        const triggerSpotify = ['spotify', 'canción de', 'cancion de', 'álbum de', 'album de', 'playlist de', 'escuchar a'];
        if (triggerSpotify.some(kw => lowerQuery.includes(kw)) && !toolContext) {
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
        if (triggerPhotos.some(kw => lowerQuery.includes(kw)) && !toolContext && !lowerQuery.includes('crea') && !lowerQuery.includes('genera')) {
            try {
                const query = userQuery.replace(/foto de|imagen de|paisaje de|fotografía de|fotografia de|unsplash/gi, "").trim();
                toolContext += await searchPhotos(query) + "\n";
            } catch {}
        }

        // 14. MAPAS Y LUGARES (Cartógrafo)
        const triggerMaps = ['mapa de', 'dónde queda', 'donde queda', 'ubicación de', 'ubicacion de', 'dirección de', 'direccion de', 'lugar'];
        if (triggerMaps.some(kw => lowerQuery.includes(kw)) && !toolContext) {
            try {
                const query = userQuery.replace(/mapa de|dónde queda|donde queda|ubicación de|ubicacion de|dirección de|direccion de/gi, "").trim();
                toolContext += await searchPlace(query) + "\n";
            } catch {}
        }

        // 15. ACADÉMICO Y LIBROS (ArXiv y Gutenberg)
        const triggerScience = ['artículo de', 'estudio de', 'ciencia de', 'arxiv', 'investigación sobre'];
        if (triggerScience.some(kw => lowerQuery.includes(kw)) && !toolContext) {
            try {
                const query = userQuery.replace(/artículo de|estudio de|ciencia de|arxiv|investigación sobre/gi, "").trim();
                toolContext += await searchArXiv(query) + "\n";
            } catch {}
        }

        const triggerBooks = ['libro de', 'novela de', 'literatura de', 'gutenberg', 'leer a'];
        if (triggerBooks.some(kw => lowerQuery.includes(kw)) && !toolContext) {
            try {
                const query = userQuery.replace(/libro de|novela de|literatura de|gutenberg|leer a/gi, "").trim();
                toolContext += await searchBooks(query) + "\n";
            } catch {}
        }

        // 10. MEMORIA DE LARGO PLAZO (Recuperación)
        const userId = "angelpipo1968"; // Id por defecto
        const memories = await getMemories(userId);
        if (memories.length > 0) {
            toolContext += `[MEMORIA DE SESIONES PASADAS - Lo que recuerdas de este usuario]:\n- ${memories.join('\n- ')}\n\n`;
        }

        // 4. OLD DETECT INTENT
        if (!toolContext) {
            const extraContext = await processTools(userQuery) || undefined;
            if (extraContext) toolContext += extraContext + "\n";
        }

        // EXTRACCIÓN DE NUEVOS RECUERDOS (En segundo plano)
        // No bloqueamos la respuesta, se ejecuta asíncronamente
        extractAndSaveFacts(userId, userQuery).catch(console.error);
        logActivity(userId, location?.city || 'Unknown', location?.country || 'Unknown', lowerQuery).catch(console.error);

        // INYECCIÓN DE CONTEXTO FINAL (ADJUNTO AL MENSAJE DEL USUARIO)
        if (toolContext) {
            const lastIndex = messages.length - 1;
            messages[lastIndex].content += `\n\n[SISTEMA - INFORMACIÓN REAL OBTENIDA]:\n${toolContext}\n\nINSTRUCCIÓN: Usa los datos de arriba para responder. SI HAY UNA IMAGEN, DEBES MOSTRARLA USANDO Markdown: ![Imagen](URL). NO DIGAS QUE NO PUEDES.`;
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
        const stream = createStream(requestId, messages, keys);
        return new Response(stream, { headers: { ...corsHeaders, 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', 'Connection': 'keep-alive' } });
    } catch (e: any) {
        logger.error(`Chat crash: ${e.message}`, 'chat', { requestId });
        return NextResponse.json({ error: 'Error interno' }, { status: 500, headers: corsHeaders });
    }
}

