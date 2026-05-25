import { NextRequest, NextResponse } from 'next/server';
import { generateVideo, checkVideoStatus, getVideoProviders } from '@/lib/nexa-core/video-generation';

export async function POST(req: NextRequest) {
    try {
        const body = await req.json();
        const { action } = body;

        switch (action) {
            case 'generate': {
                const { prompt, duration, aspectRatio, style, negativePrompt, provider } = body;
                
                if (!prompt || typeof prompt !== 'string' || prompt.trim().length < 3) {
                    return NextResponse.json(
                        { error: 'Se requiere un prompt de al menos 3 caracteres.' },
                        { status: 400 }
                    );
                }

                const result = await generateVideo({
                    prompt: prompt.trim(),
                    duration: duration || 5,
                    aspectRatio: aspectRatio || '16:9',
                    style: style || 'cinematic',
                    negativePrompt,
                    provider
                });

                return NextResponse.json({
                    success: true,
                    result,
                    prompt: prompt.trim()
                });
            }

            case 'status': {
                const { taskId, provider } = body;
                
                if (!taskId || !provider) {
                    return NextResponse.json(
                        { error: 'Se requiere taskId y provider.' },
                        { status: 400 }
                    );
                }

                const status = await checkVideoStatus(taskId, provider);
                return NextResponse.json(status);
            }

            case 'providers': {
                const providers = getVideoProviders();
                return NextResponse.json({ providers });
            }

            default:
                return NextResponse.json(
                    { error: 'Acción no válida. Usa: generate, status, o providers.' },
                    { status: 400 }
                );
        }
    } catch (error: any) {
        console.error('Video API error:', error);
        return NextResponse.json(
            { error: `Error interno: ${error.message}` },
            { status: 500 }
        );
    }
}

export async function GET() {
    const providers = getVideoProviders();
    return NextResponse.json({
        service: 'NEXA Video Generation API',
        version: '1.0.0',
        providers,
        endpoints: {
            generate: 'POST { action: "generate", prompt, duration?, aspectRatio?, style?, provider? }',
            status: 'POST { action: "status", taskId, provider }',
            providers: 'POST { action: "providers" }'
        }
    });
}
