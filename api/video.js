// Vercel Serverless Function: /api/video
// Multi-provider video generation
// Supports: Luma AI (free trial), Replicate, Minimax

export const config = {
  runtime: 'edge',
};

const PROVIDERS = {
  luma: {
    name: 'Luma AI',
    baseUrl: 'https://api.lumalabs.ai',
    envKey: 'LUMA_API_KEY',
    generate: async (apiKey, prompt, options = {}) => {
      const resp = await fetch('https://api.lumalabs.ai/video/v1/generations', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${apiKey}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          prompt,
          ...options.aspect_ratio && { aspect_ratio: options.aspect_ratio },
          ...options.duration && { duration: options.duration },
          ...options.model && { model: options.model },
          ...options.imageUrl && { keyframes: { frame0: { type: 'image', url: options.imageUrl } } },
        }),
      });
      return resp.json();
    },
    poll: async (apiKey, generationId) => {
      const resp = await fetch(`https://api.lumalabs.ai/video/v1/generations/${generationId}`, {
        headers: { 'Authorization': `Bearer ${apiKey}` },
      });
      return resp.json();
    },
  },

  replicate: {
    name: 'Replicate',
    baseUrl: 'https://api.replicate.com',
    envKey: 'REPLICATE_API_TOKEN',
    generate: async (apiKey, prompt, options = {}) => {
      const model = options.model || 'minimax/video-01';
      const resp = await fetch(`https://api.replicate.com/v1/predictions`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${apiKey}`,
          'Content-Type': 'application/json',
          'Prefer': 'respond-async',
        },
        body: JSON.stringify({
          version: options.version || undefined,
          input: {
            prompt,
            ...options.duration && { duration: options.duration },
            ...options.aspect_ratio && { aspect_ratio: options.aspect_ratio },
          },
        }),
      });
      return resp.json();
    },
    poll: async (apiKey, predictionId) => {
      const resp = await fetch(`https://api.replicate.com/v1/predictions/${predictionId}`, {
        headers: { 'Authorization': `Bearer ${apiKey}` },
      });
      return resp.json();
    },
  },

  minimax: {
    name: 'Minimax (Hailuo)',
    baseUrl: 'https://api.minimaxi.chat',
    envKey: 'MINIMAX_API_KEY',
    generate: async (apiKey, prompt, options = {}) => {
      const resp = await fetch('https://api.minimaxi.chat/v1/video_generation', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${apiKey}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          prompt,
          model: options.model || 'MiniMax-Hailuo-01',
          ...options.duration && { duration: options.duration },
        }),
      });
      return resp.json();
    },
    poll: async (apiKey, taskId) => {
      const resp = await fetch(`https://api.minimaxi.chat/v1/query/video_generation?task_id=${taskId}`, {
        headers: { 'Authorization': `Bearer ${apiKey}` },
      });
      return resp.json();
    },
  },
};

function findAvailableProvider() {
  if (process.env.LUMA_API_KEY) return 'luma';
  if (process.env.REPLICATE_API_TOKEN) return 'replicate';
  if (process.env.MINIMAX_API_KEY) return 'minimax';
  return null;
}

export default async function handler(req) {
  const corsHeaders = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  };

  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: corsHeaders });
  }

  if (req.method === 'GET') {
    // Check status of a generation
    const url = new URL(req.url);
    const provider = url.searchParams.get('provider');
    const id = url.searchParams.get('id');

    if (!provider || !id) {
      return new Response(JSON.stringify({
        error: 'provider and id required',
        usage: 'GET /api/video?provider=luma&id=xxx',
      }), {
        status: 400,
        headers: { 'Content-Type': 'application/json', ...corsHeaders },
      });
    }

    const providerConfig = PROVIDERS[provider];
    if (!providerConfig) {
      return new Response(JSON.stringify({ error: 'Unknown provider' }), {
        status: 400,
        headers: { 'Content-Type': 'application/json', ...corsHeaders },
      });
    }

    const apiKey = process.env[providerConfig.envKey];
    if (!apiKey) {
      return new Response(JSON.stringify({ error: `${providerConfig.name} API key not configured` }), {
        status: 500,
        headers: { 'Content-Type': 'application/json', ...corsHeaders },
      });
    }

    try {
      const result = await providerConfig.poll(apiKey, id);
      return new Response(JSON.stringify(result), {
        headers: { 'Content-Type': 'application/json', ...corsHeaders },
      });
    } catch (err) {
      return new Response(JSON.stringify({ error: err.message }), {
        status: 500,
        headers: { 'Content-Type': 'application/json', ...corsHeaders },
      });
    }
  }

  if (req.method !== 'POST') {
    return new Response(JSON.stringify({ error: 'POST required' }), {
      status: 405,
      headers: { 'Content-Type': 'application/json', ...corsHeaders },
    });
  }

  try {
    const body = await req.json();
    const { prompt, provider: requestedProvider, options = {} } = body;

    if (!prompt) {
      return new Response(JSON.stringify({ error: 'prompt required' }), {
        status: 400,
        headers: { 'Content-Type': 'application/json', ...corsHeaders },
      });
    }

    const providerName = requestedProvider || findAvailableProvider();
    if (!providerName) {
      return new Response(JSON.stringify({
        error: 'No video provider configured',
        help: 'Set one of these environment variables in Vercel:\n- LUMA_API_KEY (free trial at lumalabs.ai)\n- REPLICATE_API_TOKEN (replicate.com)\n- MINIMAX_API_KEY (minimax.chat)',
        providers: Object.entries(PROVIDERS).map(([k, v]) => ({
          id: k,
          name: v.name,
          envKey: v.envKey,
          configured: !!process.env[v.envKey],
        })),
      }), {
        status: 400,
        headers: { 'Content-Type': 'application/json', ...corsHeaders },
      });
    }

    const providerConfig = PROVIDERS[providerName];
    const apiKey = process.env[providerConfig.envKey];

    if (!apiKey) {
      return new Response(JSON.stringify({
        error: `${providerConfig.name} API key not configured`,
        envKey: providerConfig.envKey,
      }), {
        status: 500,
        headers: { 'Content-Type': 'application/json', ...corsHeaders },
      });
    }

    const result = await providerConfig.generate(apiKey, prompt, options);

    return new Response(JSON.stringify({
      provider: providerName,
      ...result,
    }), {
      headers: { 'Content-Type': 'application/json', ...corsHeaders },
    });

  } catch (err) {
    return new Response(JSON.stringify({ error: err.message }), {
      status: 500,
      headers: { 'Content-Type': 'application/json', ...corsHeaders },
    });
  }
}
