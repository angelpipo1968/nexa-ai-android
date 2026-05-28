import { NextRequest, NextResponse } from 'next/server';
import { getSystemPrompt } from '@/lib/nexa-core/prompts';
import { createRateLimiter, getIdentifier } from '@/lib/rate-limiter';
import { codeGenSchema } from '@/lib/validation';

const limiter = createRateLimiter();

export async function POST(req: NextRequest) {
    try {
        // Rate limiting
        const identifier = getIdentifier(req);
        const rateLimit = await limiter.checkPreset(identifier, 'generate');
        if (!rateLimit.allowed) {
            return NextResponse.json(
                { error: 'Rate limit exceeded', code: 'RATE_LIMITED' },
                {
                    status: 429,
                    headers: { 'Retry-After': String(Math.ceil((rateLimit.retryAfterMs || 60000) / 1000)) }
                }
            );
        }

        const body = await req.json();
        const parsed = codeGenSchema.safeParse(body);
        if (!parsed.success) {
            return NextResponse.json(
                { error: 'Invalid input', code: 'VALIDATION_ERROR', details: parsed.error.issues },
                { status: 400 }
            );
        }
        const { prompt, language, framework } = parsed.data;

        const systemPrompt = getSystemPrompt('code') + `\n\nGenera código${language ? ` en ${language}` : ''}${framework ? ` usando ${framework}` : ''}. Responde SOLO con el código, sin explicaciones adicionales a menos que se pidan. El código debe ser completo, funcional y listo para usar.`;

        const groqKey = process.env.GROQ_API_KEY;
        const googleKey = process.env.GOOGLE_API_KEY || process.env.GEMINI_API_KEY;
        const anthropicKey = process.env.ANTHROPIC_API_KEY;

        // Try Groq
        if (groqKey) {
            try {
                const res = await fetch('https://api.groq.com/openai/v1/chat/completions', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': `Bearer ${groqKey}`,
                    },
                    body: JSON.stringify({
                        model: 'llama-3.3-70b-versatile',
                        messages: [
                            { role: 'system', content: systemPrompt },
                            { role: 'user', content: prompt }
                        ],
                        temperature: 0.3,
                        max_tokens: 8192,
                    }),
                });

                if (res.ok) {
                    const data = await res.json();
                    return NextResponse.json({
                        code: data.choices[0]?.message?.content || '',
                        provider: 'groq',
                        language: language || 'auto',
                    });
                }
            } catch (e) {}
        }

        // Try Gemini
        if (googleKey) {
            try {
                const res = await fetch(
                    `https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${googleKey}`,
                    {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            system_instruction: { parts: [{ text: systemPrompt }] },
                            contents: [{ parts: [{ text: prompt }] }],
                            generationConfig: { temperature: 0.3, maxOutputTokens: 8192 }
                        }),
                    }
                );

                if (res.ok) {
                    const data = await res.json();
                    return NextResponse.json({
                        code: data.candidates?.[0]?.content?.parts?.[0]?.text || '',
                        provider: 'gemini',
                        language: language || 'auto',
                    });
                }
            } catch (e) {}
        }

        return NextResponse.json({ error: 'No hay proveedor de IA configurado' }, { status: 503 });
    } catch (e: unknown) {
        const message = e instanceof Error ? e.message : String(e);
        return NextResponse.json({ error: message }, { status: 500 });
    }
}
