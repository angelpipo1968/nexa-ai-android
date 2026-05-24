/**
 * NEXA AI — Admin API Keys Route
 * 
 * GET  — Read video API key statuses (masked) and AI model key statuses
 * POST — Save a video API key to runtime env (persisted via .env.local note)
 * 
 * Security: This route is meant to be called from the admin panel only.
 * In production, keys should be set in .env.local or Vercel env vars.
 */

import { NextRequest, NextResponse } from 'next/server';

const ADMIN_SECRET = process.env.ADMIN_SECRET || '';
if (!ADMIN_SECRET) {
    console.warn('[NEXA ADMIN] ADMIN_SECRET environment variable is not set. Admin panel login will be disabled.');
}

// ─── Video API Key env names ───
const VIDEO_KEYS = [
  { id: 'runway',        envKey: 'RUNWAY_API_KEY',     name: 'Runway ML',           color: '#6366f1' },
  { id: 'stability',     envKey: 'STABILITY_API_KEY',   name: 'Stability AI',         color: '#a855f7' },
  { id: 'kling_access',  envKey: 'KLING_ACCESS_KEY',   name: 'Kling AI (Access Key)',   color: '#f97316' },
  { id: 'kling_secret',  envKey: 'KLING_SECRET_KEY',   name: 'Kling AI (Secret Key)',   color: '#ea580c' },
  { id: 'luma',          envKey: 'LUMA_API_KEY',      name: 'Luma Dream Machine',  color: '#3b82f6' },
  { id: 'pika',          envKey: 'PIKA_API_KEY',      name: 'Pika Labs',           color: '#ec4899' },
] as const;

// ─── AI Model Key env names ───
const AI_KEYS = [
  { id: 'groq',      envKey: 'GROQ_API_KEY',        name: 'Groq (LLM)',          color: '#f55036' },
  { id: 'gemini',    envKey: 'GOOGLE_AI_API_KEY',   name: 'Gemini (Google)',     color: '#4285f4' },
  { id: 'openai',    envKey: 'OPENAI_API_KEY',      name: 'OpenAI',              color: '#10a37f' },
  { id: 'deepseek',  envKey: 'DEEPSEEK_API_KEY',    name: 'DeepSeek',            color: '#00b4d8' },
  { id: 'anthropic', envKey: 'ANTHROPIC_API_KEY',   name: 'Anthropic',           color: '#d97706' },
] as const;

// ─── Vision Model Keys ───
const VISION_KEYS = [
  { id: 'hf_glm46v', envKey: 'HUGGINGFACE_API_KEY', name: 'GLM-4.6V (HuggingFace)', color: '#22c55e' },
] as const;

// In-memory store for keys saved during this session (they don't persist across restarts)
const runtimeKeys: Record<string, string> = {};

/** Mask a key for display: sk-abc...xyz → sk-a•••z */
function maskKey(key: string): string {
  if (key.length <= 8) return '••••••••';
  return key.slice(0, 4) + '••••' + key.slice(-4);
}

/** CORS headers */
const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, PUT, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization',
};

export async function OPTIONS() {
  return new Response(null, { headers: corsHeaders });
}

// ═══════════════════════════════════════
//  GET — Read key statuses
// ═══════════════════════════════════════

export async function GET(req: NextRequest) {
  // Verify admin secret from query param or header
  const authHeader = req.headers.get('x-admin-secret');
  const authQuery = new URL(req.url).searchParams.get('secret');
  const secret = authHeader || authQuery;

  if (!ADMIN_SECRET || secret !== ADMIN_SECRET) {
    return NextResponse.json(
      { error: 'No autorizado' },
      { status: 401, headers: corsHeaders }
    );
  }

  const videoStatus = VIDEO_KEYS.map(k => {
    const value = runtimeKeys[k.envKey] || process.env[k.envKey] || '';
    return {
      id: k.id,
      name: k.name,
      envKey: k.envKey,
      color: k.color,
      configured: value.length > 0,
      maskedValue: value.length > 0 ? maskKey(value) : '',
      source: runtimeKeys[k.envKey] ? 'runtime' : (process.env[k.envKey] ? 'env' : 'none'),
    };
  });

  const aiStatus = AI_KEYS.map(k => {
    const value = runtimeKeys[k.envKey] || process.env[k.envKey] || '';
    return {
      id: k.id,
      name: k.name,
      envKey: k.envKey,
      color: k.color,
      configured: value.length > 0,
      maskedValue: value.length > 0 ? maskKey(value) : '',
      source: runtimeKeys[k.envKey] ? 'runtime' : (process.env[k.envKey] ? 'env' : 'none'),
    };
  });

  const visionStatus = VISION_KEYS.map(k => {
    const value = runtimeKeys[k.envKey] || process.env[k.envKey] || '';
    return {
      id: k.id,
      name: k.name,
      envKey: k.envKey,
      color: k.color,
      configured: value.length > 0,
      maskedValue: value.length > 0 ? maskKey(value) : '',
      source: runtimeKeys[k.envKey] ? 'runtime' : (process.env[k.envKey] ? 'env' : 'none'),
    };
  });

  const configuredVideoCount = videoStatus.filter(k => k.configured).length;
  const configuredAiCount = aiStatus.filter(k => k.configured).length;
  const configuredVisionCount = visionStatus.filter(k => k.configured).length;

  return NextResponse.json({
    video: videoStatus,
    ai: aiStatus,
    vision: visionStatus,
    summary: {
      videoConfigured: configuredVideoCount,
      videoTotal: VIDEO_KEYS.length,
      aiConfigured: configuredAiCount,
      aiTotal: AI_KEYS.length,
      visionConfigured: configuredVisionCount,
      visionTotal: VISION_KEYS.length,
    },
  }, { headers: corsHeaders });
}

// ═══════════════════════════════════════
//  POST — Save a video API key
// ═══════════════════════════════════════

export async function POST(req: NextRequest) {
  const authHeader = req.headers.get('x-admin-secret');
  if (!ADMIN_SECRET || authHeader !== ADMIN_SECRET) {
    return NextResponse.json(
      { error: 'No autorizado' },
      { status: 401, headers: corsHeaders }
    );
  }

  try {
    const body = await req.json();
    const { keyId, keyValue } = body;

    if (!keyId || typeof keyId !== 'string') {
      return NextResponse.json(
        { error: 'Se requiere keyId (string)' },
        { status: 400, headers: corsHeaders }
      );
    }

    if (!keyValue || typeof keyValue !== 'string' || keyValue.trim().length < 8) {
      return NextResponse.json(
        { error: 'Se requiere keyValue con al menos 8 caracteres' },
        { status: 400, headers: corsHeaders }
      );
    }

    // Validate keyId is one of the known keys (video, vision, or AI)
    const knownKey = VIDEO_KEYS.find(k => k.id === keyId)
      || VISION_KEYS.find(k => k.id === keyId)
      || AI_KEYS.find(k => k.id === keyId);
    if (!knownKey) {
      const allKeys = [...VIDEO_KEYS, ...VISION_KEYS, ...AI_KEYS];
      return NextResponse.json(
        { error: `keyId desconocido: ${keyId}. Keys válidas: ${allKeys.map(k => k.id).join(', ')}` },
        { status: 400, headers: corsHeaders }
      );
    }

    // Store in runtime memory
    runtimeKeys[knownKey.envKey] = keyValue.trim();

    // Also set process.env so it takes effect immediately for API calls
    process.env[knownKey.envKey] = keyValue.trim();

    return NextResponse.json({
      success: true,
      message: `Clave API para ${knownKey.name} guardada correctamente (runtime)`,
      keyId,
      envKey: knownKey.envKey,
      maskedValue: maskKey(keyValue.trim()),
      note: '⚠️ Esta clave se almacenó en memoria del servidor. Para persistencia, añádela a .env.local o a las variables de entorno de Vercel.',
    }, { headers: corsHeaders });

  } catch (error: any) {
    return NextResponse.json(
      { error: `Error del servidor: ${error.message}` },
      { status: 500, headers: corsHeaders }
    );
  }
}

// ═══════════════════════════════════════
//  PUT — Verify admin password
// ═══════════════════════════════════════

export async function PUT(req: NextRequest) {
  try {
    const body = await req.json();
    const { password } = body;

    if (!password || typeof password !== 'string') {
      return NextResponse.json(
        { error: 'Se requiere password' },
        { status: 400, headers: corsHeaders }
      );
    }

    // Use environment variable (required — no hardcoded fallback for security)
    const adminSecret = ADMIN_SECRET;

    if (password === adminSecret) {
      return NextResponse.json({ success: true }, { headers: corsHeaders });
    } else {
      return NextResponse.json(
        { error: 'Contraseña incorrecta' },
        { status: 401, headers: corsHeaders }
      );
    }
  } catch (error: any) {
    return NextResponse.json(
      { error: `Error del servidor: ${error.message}` },
      { status: 500, headers: corsHeaders }
    );
  }
}
