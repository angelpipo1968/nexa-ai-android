// Vercel Serverless Function: /api/preview
// Renders HTML pages and serves them as live previews
// Also handles image proxy for Pollinations.ai

export const config = {
  runtime: 'edge',
};

export default async function handler(req) {
  const url = new URL(req.url);
  const path = url.pathname;

  // CORS headers
  const corsHeaders = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  };

  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: corsHeaders });
  }

  // ── Image proxy: /api/preview/image?prompt=... ──
  if (path.endsWith('/image') || url.searchParams.has('prompt')) {
    const prompt = url.searchParams.get('prompt');
    if (!prompt) {
      return new Response(JSON.stringify({ error: 'prompt required' }), {
        status: 400,
        headers: { 'Content-Type': 'application/json', ...corsHeaders },
      });
    }

    const width = url.searchParams.get('w') || '1024';
    const height = url.searchParams.get('h') || '1024';
    const seed = url.searchParams.get('seed') || Math.floor(Math.random() * 999999).toString();

    const imageUrl = `https://image.pollinations.ai/prompt/${encodeURIComponent(prompt)}?width=${width}&height=${height}&seed=${seed}&nologo=true`;

    // Return the URL as JSON so the AI can embed it
    return new Response(JSON.stringify({
      url: imageUrl,
      prompt,
      width: parseInt(width),
      height: parseInt(height),
    }), {
      headers: { 'Content-Type': 'application/json', ...corsHeaders },
    });
  }

  // ── HTML preview: /api/preview/html ──
  if (path.endsWith('/html')) {
    if (req.method === 'POST') {
      try {
        const body = await req.json();
        const html = body.html;
        const title = body.title || 'NEXA Preview';

        if (!html) {
          return new Response(JSON.stringify({ error: 'html required' }), {
            status: 400,
            headers: { 'Content-Type': 'application/json', ...corsHeaders },
          });
        }

        // Wrap in a full HTML page with proper meta tags
        const fullHtml = `<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${escapeHtml(title)}</title>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
  </style>
</head>
<body>
${html}
</body>
</html>`;

        // Return as HTML response (can be opened in browser)
        return new Response(fullHtml, {
          headers: {
            'Content-Type': 'text/html; charset=utf-8',
            ...corsHeaders,
          },
        });
      } catch (err) {
        return new Response(JSON.stringify({ error: err.message }), {
          status: 500,
          headers: { 'Content-Type': 'application/json', ...corsHeaders },
        });
      }
    }

    // GET: return info
    return new Response(JSON.stringify({
      usage: 'POST { html: "...", title: "..." }',
      description: 'Renders HTML as a live preview page',
    }), {
      headers: { 'Content-Type': 'application/json', ...corsHeaders },
    });
  }

  // ── Default: info page ──
  return new Response(JSON.stringify({
    service: 'NEXA PRO Preview Engine',
    endpoints: {
      '/api/preview/image?prompt=...': 'Generate image URL via Pollinations.ai',
      '/api/preview/html': 'POST { html, title } to render live preview',
    },
  }), {
    headers: { 'Content-Type': 'application/json', ...corsHeaders },
  });
}

function escapeHtml(str) {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
