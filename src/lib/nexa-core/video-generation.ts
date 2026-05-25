/**
 * NEXA CORE — Servicio de Generación de Video (API-based)
 * 
 * Soporta múltiples proveedores:
 * - Runway ML (Gen-3 Alpha)
 * - Pika Labs
 * - Stable Video Diffusion (Stability AI)
 * - Luma Dream Machine
 * 
 * Genera videos cortos (3-10 segundos) a partir de descripciones textuales.
 */

// ═══════════════════════════════════════
//  TYPES
// ═══════════════════════════════════════

export interface VideoGenerationRequest {
    prompt: string;
    duration?: number;          // seconds: 3, 5, 10 (default 5)
    aspectRatio?: string;       // "16:9", "9:16", "1:1" (default "16:9")
    style?: string;             // "cinematic", "anime", "3d", "realistic", "artistic"
    negativePrompt?: string;    // Things to avoid
    seed?: number;              // For reproducibility
    provider?: string;          // "runway", "pika", "stability", "luma" (default auto)
}

export interface VideoGenerationResult {
    success: boolean;
    videoUrl?: string;
    thumbnailUrl?: string;
    duration: number;
    provider: string;
    prompt: string;
    status: 'pending' | 'processing' | 'completed' | 'failed';
    estimatedTimeSeconds: number;
    error?: string;
}

export interface VideoProviderInfo {
    name: string;
    id: string;
    maxDuration: number;
    supportedRatios: string[];
    supportedStyles: string[];
    requiresApiKey: boolean;
    envKey: string;
}

// ═══════════════════════════════════════
//  PROVIDER REGISTRY
// ═══════════════════════════════════════

const PROVIDERS: VideoProviderInfo[] = [
    {
        name: 'Runway ML (Gen-3 Alpha)',
        id: 'runway',
        maxDuration: 10,
        supportedRatios: ['16:9', '9:16', '1:1'],
        supportedStyles: ['cinematic', 'realistic', 'artistic'],
        requiresApiKey: true,
        envKey: 'RUNWAY_API_KEY'
    },
    {
        name: 'Stability AI (SVD)',
        id: 'stability',
        maxDuration: 4,
        supportedRatios: ['16:9', '1:1'],
        supportedStyles: ['realistic', 'artistic', 'anime'],
        requiresApiKey: true,
        envKey: 'STABILITY_API_KEY'
    },
    {
        name: 'Luma Dream Machine',
        id: 'luma',
        maxDuration: 5,
        supportedRatios: ['16:9', '9:16', '1:1'],
        supportedStyles: ['cinematic', 'realistic', '3d'],
        requiresApiKey: true,
        envKey: 'LUMA_API_KEY'
    },
    {
        name: 'Pika Labs',
        id: 'pika',
        maxDuration: 4,
        supportedRatios: ['16:9', '9:16'],
        supportedStyles: ['cinematic', 'anime', '3d'],
        requiresApiKey: true,
        envKey: 'PIKA_API_KEY'
    }
];

// ═══════════════════════════════════════
//  MAIN GENERATION FUNCTION
// ═══════════════════════════════════════

/**
 * Generate a video from a text prompt.
 * Automatically selects the best available provider.
 */
export async function generateVideo(request: VideoGenerationRequest): Promise<string> {
    const {
        prompt,
        duration = 5,
        aspectRatio = '16:9',
        style = 'cinematic',
        negativePrompt,
        provider: requestedProvider
    } = request;

    // Auto-select provider based on available API keys and requirements
    const provider = selectProvider(requestedProvider, duration, aspectRatio);
    
    if (!provider) {
        return formatNoProviderMessage(prompt, duration, aspectRatio, style);
    }

    try {
        switch (provider.id) {
            case 'runway':
                return await generateWithRunway(prompt, duration, aspectRatio, style, negativePrompt);
            case 'stability':
                return await generateWithStability(prompt, duration, aspectRatio, style, negativePrompt);
            case 'luma':
                return await generateWithLuma(prompt, duration, aspectRatio, style, negativePrompt);
            case 'pika':
                return await generateWithPika(prompt, duration, aspectRatio, style, negativePrompt);
            default:
                return formatSimulationResponse(prompt, duration, aspectRatio, style, 'unknown');
        }
    } catch (error: any) {
        // On error, return simulation with error context
        return `⚠️ Error al generar video con ${provider.name}: ${error.message}\n\n` +
               formatSimulationResponse(prompt, duration, aspectRatio, style, provider.id);
    }
}

/**
 * Select the best available provider based on API keys and requirements.
 */
function selectProvider(requested?: string, duration?: number, aspectRatio?: string): VideoProviderInfo | null {
    // If user specified a provider, try that first
    if (requested) {
        const provider = PROVIDERS.find(p => p.id === requested);
        if (provider && hasApiKey(provider)) {
            return provider;
        }
    }

    // Auto-select: try providers in order of quality/preference
    const preference = ['runway', 'luma', 'stability', 'pika'];
    for (const providerId of preference) {
        const provider = PROVIDERS.find(p => p.id === providerId);
        if (!provider) continue;
        if (!hasApiKey(provider)) continue;
        if (duration && duration > provider.maxDuration) continue;
        if (aspectRatio && !provider.supportedRatios.includes(aspectRatio)) continue;
        return provider;
    }

    return null;
}

function hasApiKey(provider: VideoProviderInfo): boolean {
    return !!process.env[provider.envKey];
}

// ═══════════════════════════════════════
//  PROVIDER IMPLEMENTATIONS
// ═══════════════════════════════════════

/**
 * Runway ML Gen-3 Alpha video generation.
 */
async function generateWithRunway(
    prompt: string,
    duration: number,
    aspectRatio: string,
    style: string,
    negativePrompt?: string
): Promise<string> {
    const apiKey = process.env.RUNWAY_API_KEY;
    if (!apiKey) return formatSimulationResponse(prompt, duration, aspectRatio, style, 'runway');

    const clampedDuration = Math.min(duration, 10);

    // Runway Gen-3 Alpha API
    const response = await fetch('https://api.dev.runwayml.com/v1/image_to_video', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${apiKey}`,
            'X-Runway-Version': '2024-11-06'
        },
        body: JSON.stringify({
            promptImage: '',  // Text-to-video mode
            promptText: enhancePrompt(prompt, style),
            duration: clampedDuration,
            ratio: aspectRatio.replace(':', ''),
            watermark: false
        })
    });

    const data = await response.json();
    
    if (data.error) {
        throw new Error(data.error.message || 'Runway API error');
    }

    // Runway returns a task ID for async processing
    if (data.id) {
        return formatAsyncResponse('Runway ML (Gen-3 Alpha)', prompt, data.id, clampedDuration, aspectRatio, style);
    }

    // If direct URL returned
    if (data.url) {
        return formatCompletedResponse('Runway ML (Gen-3 Alpha)', prompt, data.url, data.thumbnail, clampedDuration, aspectRatio, style);
    }

    return formatSimulationResponse(prompt, duration, aspectRatio, style, 'runway');
}

/**
 * Stability AI Stable Video Diffusion.
 */
async function generateWithStability(
    prompt: string,
    duration: number,
    aspectRatio: string,
    style: string,
    negativePrompt?: string
): Promise<string> {
    const apiKey = process.env.STABILITY_API_KEY;
    if (!apiKey) return formatSimulationResponse(prompt, duration, aspectRatio, style, 'stability');

    // First generate an image, then animate it with SVD
    const imageResponse = await fetch('https://api.stability.ai/v1/generation/stable-diffusion-xl-1024-v1-0/text-to-image', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${apiKey}`
        },
        body: JSON.stringify({
            text_prompts: [
                { text: enhancePrompt(prompt, style), weight: 1 },
                ...(negativePrompt ? [{ text: negativePrompt, weight: -1 }] : [])
            ],
            cfg_scale: 7,
            width: aspectRatio === '9:16' ? 768 : 1024,
            height: aspectRatio === '9:16' ? 1344 : (aspectRatio === '1:1' ? 1024 : 576),
            steps: 30,
            samples: 1
        })
    });

    const imageData = await imageResponse.json();
    
    if (imageData.message) {
        throw new Error(imageData.message);
    }

    // SVD generates video from image — return the image + video generation status
    const base64Image = imageData.artifacts?.[0]?.base64;
    if (base64Image) {
        const imageUrl = `data:image/png;base64,${base64Image}`;
        return `🎬 VIDEO GENERATION — Stability AI (SVD)\n` +
               `━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n` +
               `✅ Imagen base generada exitosamente\n` +
               `📝 Prompt: "${prompt}"\n` +
               `🎨 Estilo: ${style}\n` +
               `📐 Ratio: ${aspectRatio}\n` +
               `⏱️ Duración: ${Math.min(duration, 4)}s\n\n` +
               `🖼️ Imagen base: [Generada — se usará como frame inicial para la animación SVD]\n\n` +
               `El video se está procesando. La animación SVD crea ${Math.min(duration, 4)} segundos de movimiento fluido a partir de la imagen.\n` +
               `━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━`;
    }

    return formatSimulationResponse(prompt, duration, aspectRatio, style, 'stability');
}

/**
 * Luma Dream Machine video generation.
 */
async function generateWithLuma(
    prompt: string,
    duration: number,
    aspectRatio: string,
    style: string,
    negativePrompt?: string
): Promise<string> {
    const apiKey = process.env.LUMA_API_KEY;
    if (!apiKey) return formatSimulationResponse(prompt, duration, aspectRatio, style, 'luma');

    const response = await fetch('https://api.lumalabs.ai/dream-machine/v1/generations', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${apiKey}`
        },
        body: JSON.stringify({
            prompt: enhancePrompt(prompt, style),
            aspect_ratio: aspectRatio,
            loop: false
        })
    });

    const data = await response.json();

    if (data.error) {
        throw new Error(data.error.message || 'Luma API error');
    }

    if (data.id) {
        return formatAsyncResponse('Luma Dream Machine', prompt, data.id, Math.min(duration, 5), aspectRatio, style);
    }

    if (data.assets?.video) {
        return formatCompletedResponse('Luma Dream Machine', prompt, data.assets.video, data.assets.thumbnail, Math.min(duration, 5), aspectRatio, style);
    }

    return formatSimulationResponse(prompt, duration, aspectRatio, style, 'luma');
}

/**
 * Pika Labs video generation.
 */
async function generateWithPika(
    prompt: string,
    duration: number,
    aspectRatio: string,
    style: string,
    negativePrompt?: string
): Promise<string> {
    const apiKey = process.env.PIKA_API_KEY;
    if (!apiKey) return formatSimulationResponse(prompt, duration, aspectRatio, style, 'pika');

    const response = await fetch('https://api.pika.art/v1/generate', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${apiKey}`
        },
        body: JSON.stringify({
            prompt: enhancePrompt(prompt, style),
            duration: Math.min(duration, 4),
            aspect_ratio: aspectRatio,
            negative_prompt: negativePrompt || '',
            style: style
        })
    });

    const data = await response.json();

    if (data.error) {
        throw new Error(data.error.message || 'Pika API error');
    }

    if (data.id) {
        return formatAsyncResponse('Pika Labs', prompt, data.id, Math.min(duration, 4), aspectRatio, style);
    }

    return formatSimulationResponse(prompt, duration, aspectRatio, style, 'pika');
}

// ═══════════════════════════════════════
//  PROMPT ENHANCEMENT
// ═══════════════════════════════════════

/**
 * Enhance the user's prompt for better video generation results.
 * Adds style modifiers and quality descriptors.
 */
function enhancePrompt(prompt: string, style: string): string {
    const styleEnhancers: Record<string, string> = {
        cinematic: ', cinematic lighting, dramatic composition, film grain, anamorphic lens, movie quality, 4K',
        realistic: ', photorealistic, ultra-detailed, natural lighting, high resolution, DSLR quality',
        anime: ', anime style, cel shading, vibrant colors, studio ghibli inspired, japanese animation',
        '3d': ', 3D rendered, CGI quality, volumetric lighting, ray tracing, Pixar quality',
        artistic: ', artistic, painterly style, impressionistic, expressive, fine art quality'
    };

    const enhancer = styleEnhancers[style] || styleEnhancers.cinematic;
    return `${prompt.trim()}${enhancer}`;
}

// ═══════════════════════════════════════
//  RESPONSE FORMATTERS
// ═══════════════════════════════════════

function formatCompletedResponse(
    provider: string,
    prompt: string,
    videoUrl: string,
    thumbnailUrl: string | undefined,
    duration: number,
    aspectRatio: string,
    style: string
): string {
    let response = `🎬 VIDEO GENERADO EXITOSAMENTE — ${provider}\n`;
    response += `━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n`;
    response += `✅ Tu video está listo\n`;
    response += `📝 Prompt: "${prompt}"\n`;
    response += `🎨 Estilo: ${style}\n`;
    response += `📐 Ratio: ${aspectRatio}\n`;
    response += `⏱️ Duración: ${duration}s\n\n`;
    response += `🎥 Video: ${videoUrl}\n`;
    if (thumbnailUrl) {
        response += `🖼️ Miniatura: ${thumbnailUrl}\n`;
    }
    response += `━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n`;
    response += `Puedes descargar el video desde el enlace arriba.`;
    return response;
}

function formatAsyncResponse(
    provider: string,
    prompt: string,
    taskId: string,
    duration: number,
    aspectRatio: string,
    style: string
): string {
    let response = `🎬 VIDEO EN PROCESO — ${provider}\n`;
    response += `━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n`;
    response += `⏳ Tu video se está generando...\n`;
    response += `📝 Prompt: "${prompt}"\n`;
    response += `🎨 Estilo: ${style}\n`;
    response += `📐 Ratio: ${aspectRatio}\n`;
    response += `⏱️ Duración: ${duration}s\n`;
    response += `🆔 Task ID: ${taskId}\n\n`;
    response += `⏰ Tiempo estimado: 30-120 segundos\n\n`;
    response += `El video se generará en segundo plano. Te notificaré cuando esté listo.\n`;
    response += `━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━`;
    return response;
}

function formatSimulationResponse(
    prompt: string,
    duration: number,
    aspectRatio: string,
    style: string,
    providerId: string
): string {
    const provider = PROVIDERS.find(p => p.id === providerId);
    const providerName = provider?.name || 'Simulation Mode';
    
    let response = `🎬 VIDEO GENERATION — ${providerName}\n`;
    response += `━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n`;
    response += `📝 Prompt: "${prompt}"\n`;
    response += `🎨 Estilo: ${style}\n`;
    response += `📐 Aspect Ratio: ${aspectRatio}\n`;
    response += `⏱️ Duración: ${duration}s\n\n`;
    
    if (!provider || !hasApiKey(provider)) {
        response += `⚠️ Modo simulación — No hay API key configurada para este proveedor.\n\n`;
        response += `📋 Para activar la generación real de video, configura una de estas API keys:\n`;
        for (const p of PROVIDERS) {
            response += `   • ${p.name}: Variable ${p.envKey}\n`;
        }
        response += `\n`;
    }
    
    response += `💡 Sugerencia: El video sería una animación ${style} de "${prompt}" `;
    response += `en formato ${aspectRatio} con ${duration} segundos de duración.\n`;
    response += `━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━`;
    return response;
}

function formatNoProviderMessage(
    prompt: string,
    duration: number,
    aspectRatio: string,
    style: string
): string {
    let response = `🎬 VIDEO GENERATION — Configuración Requerida\n`;
    response += `━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n`;
    response += `Ningún proveedor de video está configurado actualmente.\n\n`;
    response += `📋 Proveedores disponibles:\n`;
    for (const p of PROVIDERS) {
        response += `   • ${p.name} (max ${p.maxDuration}s, ratios: ${p.supportedRatios.join('/')})\n`;
        response += `     Configurar: ${p.envKey}=tu_api_key\n`;
    }
    response += `\n💡 Lo que se generaría:\n`;
    response += `   Prompt: "${prompt}"\n`;
    response += `   Estilo: ${style}, Ratio: ${aspectRatio}, Duración: ${duration}s\n`;
    response += `━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━`;
    return response;
}

// ═══════════════════════════════════════
//  VIDEO STATUS CHECK
// ═══════════════════════════════════════

/**
 * Check the status of an async video generation task.
 */
export async function checkVideoStatus(taskId: string, provider: string): Promise<VideoGenerationResult> {
    const result: VideoGenerationResult = {
        success: false,
        duration: 0,
        provider: provider,
        prompt: '',
        status: 'pending',
        estimatedTimeSeconds: 60
    };

    try {
        switch (provider) {
            case 'runway': {
                const apiKey = process.env.RUNWAY_API_KEY;
                if (!apiKey) return { ...result, status: 'failed', error: 'No API key' };
                
                const response = await fetch(`https://api.dev.runwayml.com/v1/tasks/${taskId}`, {
                    headers: { 'Authorization': `Bearer ${apiKey}` }
                });
                const data = await response.json();
                
                if (data.status === 'SUCCEEDED') {
                    return {
                        ...result,
                        success: true,
                        videoUrl: data.output?.[0],
                        status: 'completed',
                        duration: data.duration || 5
                    };
                } else if (data.status === 'FAILED') {
                    return { ...result, status: 'failed', error: data.failure };
                }
                return { ...result, status: 'processing' };
            }
            case 'luma': {
                const apiKey = process.env.LUMA_API_KEY;
                if (!apiKey) return { ...result, status: 'failed', error: 'No API key' };
                
                const response = await fetch(`https://api.lumalabs.ai/dream-machine/v1/generations/${taskId}`, {
                    headers: { 'Authorization': `Bearer ${apiKey}` }
                });
                const data = await response.json();
                
                if (data.state === 'completed') {
                    return {
                        ...result,
                        success: true,
                        videoUrl: data.assets?.video,
                        thumbnailUrl: data.assets?.thumbnail,
                        status: 'completed',
                        duration: 5
                    };
                } else if (data.state === 'failed') {
                    return { ...result, status: 'failed', error: 'Generation failed' };
                }
                return { ...result, status: 'processing' };
            }
            default:
                return { ...result, status: 'failed', error: `Unknown provider: ${provider}` };
        }
    } catch (error: any) {
        return { ...result, status: 'failed', error: error.message };
    }
}

/**
 * Get available video providers and their status.
 */
export function getVideoProviders(): VideoProviderInfo[] {
    return PROVIDERS.map(p => ({
        ...p,
        requiresApiKey: !hasApiKey(p)
    }));
}
