import { NextRequest, NextResponse } from 'next/server';
import { getSystemPrompt } from '@/lib/nexa-core/prompts';
import { checkRateLimit, getIdentifier, RATE_LIMITS } from '@/lib/nexa-core/rate-limiter';
import { logger, generateRequestId } from '@/lib/nexa-core/logger';
import { visionSchema } from '@/lib/validation';
import { processAdvancedVision, imageUrlToBase64, detectVisionCategory, isValidImageBase64 } from '@/lib/nexa-core/vision-plus';

// ═══════════════════════════════════════════════════════════════
//  NEXA AI — Vision API Route v3
//  Provider chain: GLM-4.6V (HF) → Gemini → OpenAI GPT-4o
//  Features: URL support, category detection, advanced prompting
// ═══════════════════════════════════════════════════════════════

const DEFAULT_QUESTION = 'Analiza esta imagen en detalle. Describe TODO lo que ves, identifica texto, objetos, patrones. Si es código, explica qué hace. Si es UI, sugiere mejoras. Da recomendaciones específicas y accionables.';

/**
 * POST /api/vision
 *
 * Body (JSON):
 *   - image: string (base64 o URL de imagen)
 *   - mimeType?: string (image/jpeg, image/png, etc.)
 *   - question?: string (pregunta específica, max 5000 chars)
 *   - model?: string ('glm-4.6v' | 'gemini' | 'gpt-4o')
 *
 * Response:
 *   - response: string (análisis de la imagen)
 *   - provider: string (proveedor usado)
 *   - model: string (modelo usado)
 *   - category: string (categoría detectada)
 *   - usage?: object (tokens usados)
 */
export async function POST(req: NextRequest) {
    const requestId = generateRequestId();

    try {
        // Rate limiting
        const identifier = getIdentifier(req);
        const rateLimit = await checkRateLimit(identifier, RATE_LIMITS.vision);

        if (!rateLimit.allowed) {
            logger.warn(`Vision rate limit exceeded for ${identifier}`, 'vision', { requestId });
            return NextResponse.json(
                { error: 'Demasiadas solicitudes de análisis de imagen. Espera un momento.', code: 'RATE_LIMITED' },
                { status: 429, headers: { 'Retry-After': String(Math.ceil((rateLimit.retryAfterMs || 60000) / 1000)) } }
            );
        }

        const body = await req.json();
        const parsed = visionSchema.safeParse(body);
        if (!parsed.success) {
            return NextResponse.json(
                { error: 'Input inválido', code: 'VALIDATION_ERROR', details: parsed.error.issues },
                { status: 400 }
            );
        }
        let { image, mimeType, question } = parsed.data;

        logger.info('Vision analysis request', 'vision', { requestId, mimeType, requestedModel: body.model });

        // 🌐 --- URL SUPPORT: Convert image URL to base64 ---
        if (image.startsWith('http://') || image.startsWith('https://')) {
            try {
                logger.info('Converting image URL to base64', 'vision', { requestId, url: image.slice(0, 100) });
                const result = await imageUrlToBase64(image);
                image = result.base64;
                mimeType = result.mimeType;
            } catch (urlError) {
                const msg = urlError instanceof Error ? urlError.message : String(urlError);
                logger.warn(`Failed to fetch image URL: ${msg}`, 'vision', { requestId });
                // Continue with the URL — GLM-4.6V can handle image URLs directly
            }
        }

        // 🔍 --- CATEGORY DETECTION & ADVANCED PROMPTING ---
        const category = detectVisionCategory(question);
        const advancedData = await processAdvancedVision(image, question);

        logger.info(`Vision category detected: ${category}`, 'vision', { requestId });

        const hfKey = process.env.HUGGINGFACE_API_KEY;
        const googleKey = process.env.GOOGLE_API_KEY || process.env.GEMINI_API_KEY || process.env.GOOGLE_AI_API_KEY;
        const openaiKey = process.env.OPENAI_API_KEY;
        const openrouterKey = process.env.OPENROUTER_API_KEY;
        const userQuestion = question || DEFAULT_QUESTION;

        // ═══════════════════════════════════════════════
        //  Provider 1: GLM-4.6V via HuggingFace (FREE, MIT)
        //  128K context, multimodal tool calling, MoE
        // ═══════════════════════════════════════════════
        if (hfKey && (!body.model || body.model === 'glm-4.6v')) {
            try {
                // GLM-4.6V can handle both base64 data URIs and HTTPS URLs
                const imageUrl = image.startsWith('http')
                    ? image
                    : `data:${mimeType || 'image/jpeg'};base64,${image}`;

                // Retry up to 3 times if HuggingFace returns 503 (model loading)
                let lastError: string | null = null;

                for (let attempt = 0; attempt < 3; attempt++) {
                    const hfRes = await fetch(
                        'https://api-inference.huggingface.co/models/zai-org/GLM-4.6V/v1/chat/completions',
                        {
                            method: 'POST',
                            headers: {
                                'Content-Type': 'application/json',
                                'Authorization': `Bearer ${hfKey}`,
                            },
                            body: JSON.stringify({
                                model: 'zai-org/GLM-4.6V',
                                messages: [
                                    {
                                        role: 'system',
                                        content: getSystemPrompt('vision') + '\n\n' + advancedData,
                                    },
                                    {
                                        role: 'user',
                                        content: [
                                            { type: 'text', text: userQuestion },
                                            {
                                                type: 'image_url',
                                                image_url: { url: imageUrl },
                                            },
                                        ],
                                    },
                                ],
                                max_tokens: 4096,
                                temperature: 0.7,
                            }),
                        }
                    );

                    if (hfRes.ok) {
                        const data = await hfRes.json();
                        const text = data.choices?.[0]?.message?.content || '';
                        if (text) {
                            logger.info('Vision analysis completed via GLM-4.6V', 'vision', { requestId, category, attempt });
                            return NextResponse.json({
                                response: text,
                                provider: 'glm-4.6v',
                                model: 'zai-org/GLM-4.6V',
                                category,
                                usage: data.usage,
                            });
                        }
                    } else {
                        const errData = await hfRes.json().catch(() => ({}));
                        lastError = `GLM-4.6V (${hfRes.status}): ${JSON.stringify(errData?.error || 'Unknown')}`;
                        logger.warn(
                            `GLM-4.6V attempt ${attempt + 1} failed: ${lastError}`,
                            'vision',
                            { requestId }
                        );

                        if (hfRes.status === 503) {
                            // Model is loading — wait and retry
                            const waitTime = Math.min((errData?.estimated_time || 20) * 1000, 30000);
                            logger.info(`Model loading, waiting ${waitTime / 1000}s before retry...`, 'vision', { requestId, attempt });
                            await new Promise(resolve => setTimeout(resolve, waitTime));
                            continue; // retry
                        }
                        if (hfRes.status === 429) {
                            logger.warn('HuggingFace rate limit hit, falling back to next provider', 'vision', { requestId });
                            break; // fall through to next provider
                        }
                        if (hfRes.status === 401) {
                            logger.error('HuggingFace API key invalid, falling back', 'vision', { requestId });
                            break; // fall through to next provider
                        }
                        // Other errors — fall through to next provider
                        break;
                    }
                }
                // If we exhausted retries, fall through to next provider
                if (lastError) {
                    logger.warn(`GLM-4.6V all attempts failed, falling back. Last: ${lastError}`, 'vision', { requestId });
                }
            } catch (e: unknown) {
                const msg = e instanceof Error ? e.message : String(e);
                logger.error(`GLM-4.6V vision error: ${msg}`, 'vision', { requestId });
            }
        }

        // ═══════════════════════════════════════════════
        //  Provider 2: Google Gemini Vision
        // ═══════════════════════════════════════════════
        if (googleKey && (!body.model || body.model === 'gemini')) {
            try {
                const model = body.model?.startsWith('gemini') ? body.model : 'gemini-2.0-flash';
                const res = await fetch(
                    `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${googleKey}`,
                    {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            system_instruction: { parts: [{ text: getSystemPrompt('vision') + '\n\n' + advancedData }] },
                            contents: [{
                                parts: [
                                    { text: userQuestion },
                                    {
                                        inline_data: {
                                            mime_type: mimeType || 'image/jpeg',
                                            data: image.startsWith('data:') ? image.split(',')[1] : image,
                                        },
                                    },
                                ],
                            }],
                            generationConfig: { temperature: 0.7, maxOutputTokens: 4096 },
                        }),
                    }
                );

                if (res.ok) {
                    const data = await res.json();
                    const text = data.candidates?.[0]?.content?.parts?.[0]?.text || '';
                    if (text) {
                        logger.info('Vision analysis completed via Gemini', 'vision', { requestId, category });
                        return NextResponse.json({ response: text, provider: 'gemini', model, category });
                    }
                } else {
                    logger.warn(`Gemini vision failed (${res.status})`, 'vision', { requestId });
                }
            } catch (e: unknown) {
                const msg = e instanceof Error ? e.message : String(e);
                logger.error(`Gemini vision error: ${msg}`, 'vision', { requestId });
            }
        }

        // ═══════════════════════════════════════════════
        //  Provider 3: OpenRouter Vision (GPT-4o / Claude Vision)
        //  Supports multiple vision models via OpenRouter API
        // ═══════════════════════════════════════════════
        if (openrouterKey && (!body.model || body.model === 'gpt-4o' || body.model === 'openrouter')) {
            try {
                const visionModel = body.model === 'openrouter' ? 'openai/gpt-4o' : 'openai/gpt-4o';
                const res = await fetch('https://openrouter.ai/api/v1/chat/completions', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': `Bearer ${openrouterKey}`,
                        'HTTP-Referer': process.env.NEXT_PUBLIC_BACKEND_URL || 'https://www.nexa-ai.dev',
                    },
                    body: JSON.stringify({
                        model: visionModel,
                        messages: [
                            { role: 'system', content: getSystemPrompt('vision') + '\n\n' + advancedData },
                            {
                                role: 'user',
                                content: [
                                    { type: 'text', text: userQuestion },
                                    { type: 'image_url', image_url: { url: `data:${mimeType || 'image/jpeg'};base64,${image}` } },
                                ],
                            },
                        ],
                        max_tokens: 4096,
                    }),
                });

                if (res.ok) {
                    const data = await res.json();
                    logger.info('Vision analysis completed via OpenRouter', 'vision', { requestId, category });
                    return NextResponse.json({
                        response: data.choices[0]?.message?.content || '',
                        provider: 'openrouter',
                        model: visionModel,
                        category,
                    });
                } else {
                    const errData = await res.json().catch(() => ({}));
                    logger.warn(`OpenRouter vision failed (${res.status}): ${JSON.stringify(errData?.error || 'Unknown')}`, 'vision', { requestId });
                }
            } catch (e: unknown) {
                const msg = e instanceof Error ? e.message : String(e);
                logger.error(`OpenRouter vision error: ${msg}`, 'vision', { requestId });
            }
        }

        // ═══════════════════════════════════════════════
        //  Provider 4: OpenAI GPT-4o Vision (direct, fallback)
        //  Only used if OPENAI_API_KEY is a real OpenAI key
        // ═══════════════════════════════════════════════
        if (openaiKey && !openaiKey.startsWith('sk-or-v1-') && (!body.model || body.model === 'gpt-4o')) {
            try {
                const res = await fetch('https://api.openai.com/v1/chat/completions', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${openaiKey}` },
                    body: JSON.stringify({
                        model: 'gpt-4o',
                        messages: [
                            { role: 'system', content: getSystemPrompt('vision') + '\n\n' + advancedData },
                            {
                                role: 'user',
                                content: [
                                    { type: 'text', text: userQuestion },
                                    { type: 'image_url', image_url: { url: `data:${mimeType || 'image/jpeg'};base64,${image}` } },
                                ],
                            },
                        ],
                        max_tokens: 4096,
                    }),
                });

                if (res.ok) {
                    const data = await res.json();
                    logger.info('Vision analysis completed via OpenAI', 'vision', { requestId, category });
                    return NextResponse.json({
                        response: data.choices[0]?.message?.content || '',
                        provider: 'openai',
                        model: 'gpt-4o',
                        category,
                    });
                }
            } catch (e: unknown) {
                const msg = e instanceof Error ? e.message : String(e);
                logger.error(`OpenAI vision error: ${msg}`, 'vision', { requestId });
            }
        }

        return NextResponse.json({
            error: 'No hay proveedor de visión disponible. Configura: HUGGINGFACE_API_KEY (GLM-4.6V, gratis), GOOGLE_API_KEY (Gemini), o OPENAI_API_KEY (GPT-4o).',
            code: 'NO_VISION_PROVIDER',
        }, { status: 503 });

    } catch (e: unknown) {
        const msg = e instanceof Error ? e.message : String(e);
        logger.error(`Vision error: ${msg}`, 'vision', { requestId });
        return NextResponse.json({ error: 'Error interno del servidor' }, { status: 500 });
    }
}
