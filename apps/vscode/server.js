import http from 'node:http';

const port = Number(process.env.PORT || process.env.NEXA_MICRO_BACKEND_PORT || 3011);
const nextBaseUrl = process.env.NEXA_NEXT_URL || 'http://localhost:3001';

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET,POST,OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type,Authorization',
};

function sendJson(res, statusCode, payload) {
  res.writeHead(statusCode, {
    ...corsHeaders,
    'Content-Type': 'application/json; charset=utf-8',
  });
  res.end(JSON.stringify(payload));
}

async function proxyToNext(req, res, pathname) {
  const chunks = [];

  req.on('data', (chunk) => chunks.push(chunk));
  req.on('end', async () => {
    try {
      const upstream = await fetch(`${nextBaseUrl}${pathname}`, {
        method: req.method,
        headers: {
          'Content-Type': req.headers['content-type'] || 'application/json',
          Authorization: req.headers.authorization || '',
        },
        body: chunks.length ? Buffer.concat(chunks) : undefined,
      });

      res.writeHead(upstream.status, {
        ...corsHeaders,
        'Content-Type': upstream.headers.get('content-type') || 'application/json; charset=utf-8',
      });

      if (upstream.body) {
        const reader = upstream.body.getReader();
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          res.write(Buffer.from(value));
        }
      }
      res.end();
    } catch (error) {
      sendJson(res, 502, {
        status: 'error',
        error: 'No se pudo contactar con el backend Next local.',
        nextBaseUrl,
        detail: error instanceof Error ? error.message : String(error),
      });
    }
  });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url || '/', `http://${req.headers.host || `localhost:${port}`}`);

  if (req.method === 'OPTIONS') {
    res.writeHead(204, corsHeaders);
    res.end();
    return;
  }

  if (req.method === 'GET' && (url.pathname === '/' || url.pathname === '/health' || url.pathname === '/api/health')) {
    sendJson(res, 200, {
      status: 'ok',
      service: 'nexa-vscode-micro-backend',
      port,
      nextBaseUrl,
      timestamp: new Date().toISOString(),
    });
    return;
  }

  if (req.method === 'POST' && (url.pathname === '/chat' || url.pathname === '/api/chat')) {
    await proxyToNext(req, res, '/api/chat');
    return;
  }

  sendJson(res, 404, {
    status: 'error',
    error: 'Ruta no encontrada',
    routes: ['GET /health', 'POST /chat', 'POST /api/chat'],
  });
});

server.listen(port, () => {
  console.log(`[NEXA] Micro-Backend listo en http://localhost:${port}`);
  console.log(`[NEXA] Proxy hacia Next: ${nextBaseUrl}`);
});
