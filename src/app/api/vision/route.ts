import { NextRequest, NextResponse } from 'next/server';
import { getSystemPrompt } from '@/lib/nexa-core/prompts';
import { checkRateLimit, getIdentifier, RATE_LIMITS } from '@/lib/nexa-core/rate-limiter';
import { logger, generateRequestId } from '@/lib/nexa-core/logger';
import { visionSchema } from '@/lib/validation';
import { processAdvancedVision } from '@/lib/nexa-core/vision-plus';

export async function POST(req: NextRequest) {
    const requestId = generateRequestId();

    try {
        // Rate limiting (más restrictivo para visión)
        const identifier = getIdentifier(req);
        const rateLimit = checkRateLimit(identifier, RATE_LIMITS.vision);
        
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
                { error: 'Invalid input', code: 'VALIDATION_ERROR', details: parsed.error.issues },
                { status: 400 }
            );
        }
        const { image, mimeType, question } = parsed.data;

        logger.info('Vision analysis request', 'vision', { requestId, mimeType });

        // 🧠 --- PROCESAMIENTO AVANZADO (QR / OCR PROACTIVO) ---
        const advancedData = await processAdvancedVision(image);
        const isQR = advancedData.includes('[QR DETECTADO]');

        const googleKey = process.env.GOOGLE_API_KEY || process.env.GEMINI_API_KEY;
        const openaiKey = process.env.OPENAI_API_KEY;

        // Try Gemini Vision (best quality)
        if (googleKey) {
            try {
                const model = body.model || 'gemini-1.5-flash';
                const res = await fetch(
                    `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${googleKey}`,
                    {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            system_instruction: { parts: [{ text: getSystemPrompt('vision') + "\n\n" + advancedData }] },
                            contents: [{
                                parts: [
                                    { text: question || 'Analiza esta imagen en detalle. Describe TODO lo que ves, identifica texto, objetos, patrones. Si es código, explica qué hace. Si es UI, sugiere mejoras. Da recomendaciones específicas y accionables.' },
                                    { inline_data: { mime_type: mimeType || 'image/jpeg', data: image } }
                                ]
                            }],
                            generationConfig: { temperature: 0.7, maxOutputTokens: 4096 }
                        }),
                    }
                );

                if (res.ok) {
                    const data = await res.json();
                    let text = data.candidates?.[0]?.content?.parts?.[0]?.text || '';
                    
                    logger.info('Vision analysis completed via Gemini', 'vision', { requestId });
                    return NextResponse.json({ response: text, provider: 'gemini', model });
                } else {
                    logger.warn(`Gemini vision failed (${res.status})`, 'vision', { requestId });
                }
            } catch (e: unknown) {
                const msg = e instanceof Error ? e.message : String(e);
                logger.error(`Gemini vision error: ${msg}`, 'vision', { requestId });
            }
        }

        // Try OpenAI GPT-4 Vision
        if (openaiKey) {
            try {
                const res = await fetch('https://api.openai.com/v1/chat/completions', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${openaiKey}` },
                    body: JSON.stringify({
                        model: 'gpt-4o',
                        messages: [
                            { role: 'system', content: getSystemPrompt('vision') },
                            {
                                role: 'user',
                                content: [
                                    { type: 'text', text: question || 'Analiza esta imagen en detalle.' },
                                    { type: 'image_url', image_url: { url: `data:${mimeType || 'image/jpeg'};base64,${image}` } }
                                ]
                            }
                        ],
                        max_tokens: 4096,
                    }),
                });

                if (res.ok) {
                    const data = await res.json();
                    logger.info('Vision analysis completed via OpenAI', 'vision', { requestId });
                    return NextResponse.json({ response: data.choices[0]?.message?.content || '', provider: 'openai', model: 'gpt-4o' });
                }
            } catch (e: unknown) {
                const msg = e instanceof Error ? e.message : String(e);
                logger.error(`OpenAI vision error: ${msg}`, 'vision', { requestId });
            }
        }

        return NextResponse.json({ 
            error: 'No hay proveedor de visión configurado. Configura GOOGLE_API_KEY.',
            code: 'NO_VISION_PROVIDER'
        }, { status: 503 });

    } catch (e: unknown) {
        const msg = e instanceof Error ? e.message : String(e);
        logger.error(`Vision error: ${msg}`, 'vision', { requestId });
        return NextResponse.json({ error: 'Error interno del servidor' }, { status: 500 });
    }
}
