import express from 'express';
import cors from 'cors';
import * as cheerio from 'cheerio';
import { exec } from 'child_process';
import util from 'util';
import crypto from 'crypto';
import fs from 'fs';
import path from 'path';

const execPromise = util.promisify(exec);
const app = express();

app.use(cors());
app.use(express.json());

// --- AUTOMATIC ENV LOADER (.env.local) ---
function loadEnv() {
    try {
        const envPath = path.resolve(process.cwd(), '.env.local');
        if (fs.existsSync(envPath)) {
            const content = fs.readFileSync(envPath, 'utf8');
            content.split(/\r?\n/).forEach(line => {
                const trimmed = line.trim();
                if (!trimmed || trimmed.startsWith('#')) return;
                const match = trimmed.match(/^\s*([\w.-]+)\s*=\s*(.*)?\s*$/);
                if (match) {
                    const key = match[1];
                    let value = match[2] || '';
                    if (value.startsWith('"') && value.endsWith('"')) value = value.slice(1, -1);
                    if (value.startsWith("'") && value.endsWith("'")) value = value.slice(1, -1);
                    process.env[key] = value.trim();
                }
            });
            console.log('[Hermes Config] .env.local cargado con éxito.');
        } else {
            console.warn('[Hermes Config] Archivo .env.local no encontrado, usando variables del sistema.');
        }
    } catch (e) {
        console.warn('[Hermes Config] No se pudo cargar .env.local:', e.message);
    }
}
loadEnv();

// --- LOCAL SKILLS ENGINE (Auto-Aprendizaje Autónomo) ---
const SKILLS_FILE = path.resolve(process.cwd(), 'hermes-skills.json');

function loadLocalSkills() {
    try {
        if (fs.existsSync(SKILLS_FILE)) {
            return JSON.parse(fs.readFileSync(SKILLS_FILE, 'utf8'));
        }
    } catch (e) {
        console.warn('[Hermes Skills] Error leyendo hermes-skills.json:', e.message);
    }
    return [];
}

function saveLocalSkill(skill) {
    try {
        const skills = loadLocalSkills();
        const filtered = skills.filter(s => s.name !== skill.name);
        filtered.push(skill);
        fs.writeFileSync(SKILLS_FILE, JSON.stringify(filtered, null, 2), 'utf8');
        console.log(`[Hermes Skills] ¡Nueva habilidad aprendida y guardada localmente!: ${skill.name}`);
    } catch (e) {
        console.error('[Hermes Skills] Error guardando skill local:', e.message);
    }
}

// Analizar la conversación en segundo plano para aprender reglas o correcciones
async function extractAndLearnSkills(userMessage, assistantMessage) {
    const lowerUser = userMessage.toLowerCase();
    const teachingIndicators = [
        'debes hacer', 'siempre que te pida', 'nunca hagas', 'cuando te diga',
        'regla:', 'instrucción:', 'la forma correcta', 'así es como se', 'para hacer esto',
        'no es así', 'te equivocaste', 'corrige'
    ];

    const seemsLikeTeaching = teachingIndicators.some(indicator => lowerUser.includes(indicator));
    if (!seemsLikeTeaching) return;

    const difyKey = process.env.DIFY_API_KEY;
    if (!difyKey) return;

    try {
        console.log('[Hermes Skills] Detectado posible intento de enseñanza, extrayendo regla...');
        const prompt = `Analiza esta conversación y extrae la regla de comportamiento, flujo o instrucción reutilizable que el usuario está enseñando o corrigiendo al asistente.
Si encuentras una regla reutilizable, devuélvela estrictamente en este formato JSON (sin markdown ni texto adicional, solo el JSON puro):
{
  "name": "nombre-descriptivo-en-minúsculas-y-guiones",
  "description": "cuándo aplicar esta habilidad",
  "instructions": "instrucciones exactas y claras que el asistente debe seguir"
}
Si no se está enseñando ninguna regla o procedimiento útil para el futuro, responde únicamente: "NONE".

Conversación:
Usuario: ${userMessage}
Asistente: ${assistantMessage}`;

        const res = await fetch('https://api.dify.ai/v1/chat-messages', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${difyKey}`
            },
            body: JSON.stringify({
                inputs: {},
                query: prompt,
                response_mode: 'blocking',
                user: 'hermes-skills-extractor'
            })
        });

        if (res.ok) {
            const data = await res.json();
            const answer = data.answer?.trim() || '';
            if (answer && !answer.includes('NONE')) {
                const match = answer.match(/\{[\s\S]*?\}/);
                if (match) {
                    const parsed = JSON.parse(match[0]);
                    if (parsed.name && parsed.instructions) {
                        saveLocalSkill({
                            name: parsed.name.toLowerCase().replace(/[^a-z0-9-]/g, '-'),
                            description: parsed.description || '',
                            instructions: parsed.instructions,
                            createdAt: new Date().toISOString()
                        });
                    }
                }
            }
        }
    } catch (e) {
        console.warn('[Hermes Skills] Falló la extracción en segundo plano:', e.message);
    }
}

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
        dify_active: !!process.env.DIFY_API_KEY,
        skills_learned: loadLocalSkills().length,
        timestamp: new Date().toISOString()
    });
});

const OLLAMA_URL = process.env.OLLAMA_URL || 'http://127.0.0.1:11434/api/generate';
const MODEL = process.env.OLLAMA_MODEL || 'llama3.2:3b';

async function fetchUrlContent(url) {
    try {
        const response = await fetch(url);
        const html = await response.text();
        const $ = cheerio.load(html);
        $('script, style, nav, footer, header').remove();
        const text = $('body').text().replace(/\s+/g, ' ').trim();
        return text.substring(0, 5000); // Límite de 5000 caracteres
    } catch (e) {
        return `Error al leer la URL: ${e.message}`;
    }
}

app.post('/api/chat', async (req, res) => {
    const reqId = crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(36).substring(7);
    console.log(`[REQ-${reqId}] Nueva petición recibida a las ${new Date().toLocaleTimeString()}`);
    
    try {
        const userMessage = req.body.message || '';
        let enhancedPrompt = userMessage;
        let systemContext = "Eres Hermes, un asistente de IA local para Nexa OS. Eres experto en programación y muy servicial.";

        // Inyección de Skills (Aprendizaje Autónomo Local)
        const localSkills = loadLocalSkills();
        if (localSkills.length > 0) {
            let skillsContext = "\n\n[HABILIDADES Y REGLAS LOCALES APRENDIDAS - Síguelas estrictamente]:\n";
            localSkills.forEach(skill => {
                skillsContext += `- [${skill.name}]: ${skill.instructions}\n`;
            });
            systemContext += skillsContext;
        }

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
                let commitMsg = "Automated commit via Hermes";
                await execPromise('git add .');
                await execPromise(`git commit -m "${commitMsg}"`);
                await execPromise('git push');
                enhancedPrompt = `El usuario pidió hacer un commit y push. ACABAS DE EJECUTAR CON ÉXITO: git add ., git commit -m "${commitMsg}", y git push. Informale al usuario que los cambios ya están subidos a GitHub.`;
            } catch (err) {
                console.error("Git error:", err);
                enhancedPrompt = `El usuario pidió hacer un commit y push, pero ocurrió un error al ejecutarlo: ${err.message}. Informale amablemente del error.`;
            }
        }

        // --- SISTEMA DE DOBLE PROVEEDOR RESILIENTE (Ollama con Fallback transparente a Dify) ---
        let responseSent = false;
        let finalResponseText = '';

        // 1. Intentar Ollama primero si está activo
        try {
            console.log(`[REQ-${reqId}] Enviando a Ollama (Modelo: ${MODEL})...`);
            const ollamaRes = await fetch(OLLAMA_URL, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    model: MODEL,
                    prompt: enhancedPrompt,
                    system: systemContext,
                    stream: false
                }),
                signal: AbortSignal.timeout(15000) // Timeout de 15 segundos para evitar bloqueos
            });

            if (!ollamaRes.ok) {
                throw new Error(`Ollama HTTP ${ollamaRes.status}`);
            }

            const data = await ollamaRes.json();
            finalResponseText = data.response;
            console.log(`[REQ-${reqId}] Respuesta procesada con éxito desde Ollama.`);
            res.json({ response: finalResponseText });
            responseSent = true;
        } catch (ollamaErr) {
            console.warn(`[REQ-${reqId}] Ollama falló o está inactivo: ${ollamaErr.message}`);
            
            // 2. Fallback transparente a Dify si Ollama falla y la API Key existe
            const difyKey = process.env.DIFY_API_KEY;
            if (difyKey) {
                console.log(`[REQ-${reqId}] Activando Fallback Transparente a Dify...`);
                try {
                    const difyRes = await fetch('https://api.dify.ai/v1/chat-messages', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                            'Authorization': `Bearer ${difyKey}`
                        },
                        body: JSON.stringify({
                            inputs: {},
                            query: enhancedPrompt,
                            response_mode: 'blocking',
                            user: 'hermes-local-user'
                        })
                    });

                    if (!difyRes.ok) {
                        const errData = await difyRes.json().catch(() => ({}));
                        throw new Error(errData.message || `Dify HTTP ${difyRes.status}`);
                    }

                    const difyData = await difyRes.json();
                    finalResponseText = difyData.answer;
                    console.log(`[REQ-${reqId}] Respuesta procesada con éxito desde Dify (Fallback).`);
                    res.json({ response: finalResponseText });
                    responseSent = true;
                } catch (difyErr) {
                    console.error(`[REQ-${reqId}] Error en Dify (Fallback):`, difyErr.message);
                    res.status(500).json({ error: `Ambos proveedores fallaron. Ollama: ${ollamaErr.message}. Dify: ${difyErr.message}` });
                    responseSent = true;
                }
            } else {
                // Si no hay Dify configurado, devolver el error original de conexión de Ollama
                if (ollamaErr.cause && ollamaErr.cause.code === 'ECONNREFUSED') {
                    res.status(503).json({ error: "No se pudo conectar a Ollama. Asegúrate de que Ollama esté en ejecución (http://127.0.0.1:11434) o configura DIFY_API_KEY en .env.local." });
                } else {
                    res.status(500).json({ error: ollamaErr.message });
                }
                responseSent = true;
            }
        }

        // 3. Extracción de habilidades de auto-aprendizaje en segundo plano (no bloquea al usuario)
        if (responseSent && finalResponseText) {
            extractAndLearnSkills(userMessage, finalResponseText).catch(console.error);
        }

    } catch (error) {
        console.error(`[REQ-${reqId}] ERROR CRÍTICO:`, error.message);
        if (!res.headersSent) {
            res.status(500).json({ error: error.message });
        }
    }
});

const PORT = 3001;
app.listen(PORT, () => {
    console.log(`Hermes Backend corriendo en http://localhost:${PORT}`);
    console.log(`Asegúrate de ejecutar esto desde la raíz del proyecto para que los comandos git funcionen.`);
});
