import express from 'express';
import cors from 'cors';
import * as cheerio from 'cheerio';
import { exec } from 'child_process';
import util from 'util';
import crypto from 'crypto';

const execPromise = util.promisify(exec);
const app = express();

app.use(cors());
app.use(express.json());

// --- RATE LIMITING MIDDLEWARE ---
const rateLimitMap = new Map();
const RATE_LIMIT_WINDOW_MS = 60000; // 1 minuto
const MAX_REQUESTS_PER_WINDOW = 30;

app.use((req, res, next) => {
    if (!req.path.startsWith('/api/chat')) return next();
    const ip = req.ip || req.connection?.remoteAddress || 'unknown';
    const now = Date.now();
    
    if (!rateLimitMap.has(ip)) {
        rateLimitMap.set(ip, { count: 1, resetTime: now + RATE_LIMIT_WINDOW_MS });
        return next();
    }
    
    const limitData = rateLimitMap.get(ip);
    if (now > limitData.resetTime) {
        limitData.count = 1;
        limitData.resetTime = now + RATE_LIMIT_WINDOW_MS;
        return next();
    }
    
    limitData.count++;
    if (limitData.count > MAX_REQUESTS_PER_WINDOW) {
        console.warn(`[RATE LIMIT] IP ${ip} superó el límite de peticiones.`);
        return res.status(429).json({ error: "Demasiadas peticiones. Por favor, espera un minuto." });
    }
    next();
});

// --- HEALTH CHECK ENDPOINT ---
app.get('/api/health', (req, res) => {
    res.json({
        status: 'ok',
        uptime: process.uptime(),
        model_configured: MODEL,
        timestamp: new Date().toISOString()
    });
});

const OLLAMA_URL = 'http://127.0.0.1:11434/api/generate';
const MODEL = 'llama3.2:3b'; // Cambiado temporalmente porque nexa-os:latest no existe en tu sistema

async function fetchUrlContent(url) {
    try {
        const response = await fetch(url);
        const html = await response.text();
        const $ = cheerio.load(html);
        // Remove scripts, styles, etc.
        $('script, style, nav, footer, header').remove();
        const text = $('body').text().replace(/\s+/g, ' ').trim();
        return text.substring(0, 5000); // Limit to 5000 chars to fit in context window
    } catch (e) {
        return `Error al leer la URL: ${e.message}`;
    }
}

app.post('/api/chat', async (req, res) => {
    const reqId = crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(36).substring(7);
    console.log(`[REQ-${reqId}] Nueva petición recibida a las ${new Date().toLocaleTimeString()}`);
    
    try {
        const userMessage = req.body.message;
        let enhancedPrompt = userMessage;
        let systemContext = "Eres Hermes, un asistente de IA local para Nexa OS. Eres experto en programación y muy servicial.";

        // Detectar si hay una URL
        const urlMatch = userMessage.match(/https?:\/\/[^\s]+/);
        if (urlMatch) {
            const url = urlMatch[0];
            console.log(`[REQ-${reqId}] Buscando URL: ${url}`);
            const content = await fetchUrlContent(url);
            enhancedPrompt = `El usuario mencionó esta URL: ${url}\n\nContenido extraído de la página:\n"""\n${content}\n"""\n\nMensaje original del usuario: ${userMessage.replace(url, '')}`;
        }

        // Detectar si pide hacer commit / push
        const msgLower = userMessage.toLowerCase();
        if (msgLower.includes('commit') || msgLower.includes('push') || msgLower.includes('guarda los cambios') || msgLower.includes('sube los cambios')) {
            console.log(`[REQ-${reqId}] Ejecutando Git Commit y Push...`);
            try {
                // Determine a commit message
                let commitMsg = "Automated commit via Hermes";
                // run git commands
                await execPromise('git add .');
                await execPromise(`git commit -m "${commitMsg}"`);
                await execPromise('git push');
                enhancedPrompt = `El usuario pidió hacer un commit y push. ACABAS DE EJECUTAR CON ÉXITO: git add ., git commit -m "${commitMsg}", y git push. Informale al usuario que los cambios ya están subidos a GitHub.`;
            } catch (err) {
                console.error("Git error:", err);
                enhancedPrompt = `El usuario pidió hacer un commit y push, pero ocurrió un error al ejecutarlo: ${err.message}. Informale amablemente del error.`;
            }
        }

        // Llamar a Ollama
        console.log(`[REQ-${reqId}] Enviando a Ollama (Modelo: ${MODEL})...`);
        const ollamaRes = await fetch(OLLAMA_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                model: MODEL,
                prompt: enhancedPrompt,
                system: systemContext,
                stream: false
            })
        });

        if (!ollamaRes.ok) {
            if (ollamaRes.status === 404) {
                throw new Error(`El modelo '${MODEL}' no se encontró en Ollama. Asegúrate de haberlo descargado con 'ollama run ${MODEL}'.`);
            }
            throw new Error(`Ollama devolvió estado HTTP ${ollamaRes.status}: ${ollamaRes.statusText}`);
        }

        const data = await ollamaRes.json();
        
        console.log(`[REQ-${reqId}] Respuesta procesada con éxito y enviada.`);
        res.json({ response: data.response });

    } catch (error) {
        console.error(`[REQ-${reqId}] ERROR:`, error.message);
        
        // Manejo específico para errores de conexión
        if (error.cause && error.cause.code === 'ECONNREFUSED') {
            return res.status(503).json({ error: "No se pudo conectar a Ollama. Asegúrate de que Ollama esté en ejecución (http://127.0.0.1:11434)." });
        }
        
        res.status(500).json({ error: error.message });
    }
});

const PORT = 3001;
app.listen(PORT, () => {
    console.log(`Hermes Backend corriendo en http://localhost:${PORT}`);
    console.log(`Asegúrate de ejecutar esto desde la raíz del proyecto para que los comandos git funcionen.`);
});
