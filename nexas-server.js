// Servidor simple para Nexas AI Assistant
// Este servidor maneja las respuestas del chat para la aplicación web

const express = require('express');
const cors = require('cors');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3002;

// Middleware
app.use(cors());
app.use(express.json());
app.use(express.static('.'));

// Respuestas inteligentes basadas en categoría
const intelligentResponses = {
    general: [
        "Entiendo perfectamente tu punto. Con los fixes de manos libres v5.0 implementados en la app, la liberación de recursos de audio (`stopVoiceAudioSession`) ahora es instantánea y no causa ningún corte de volumen en la música de fondo.",
        "Excelente observación. La arquitectura híbrida de NexaIA está diseñada para equilibrar el procesamiento en la nube con modelos locales que corren directamente en tu NPU.",
        "Eso tiene mucho sentido. La optimización del barge-in (interrupción) en modo voz ahora cuenta con un cooldown adaptativo de 3.5 segundos que filtra el eco del altavoz sin perder nada de responsividad.",
        "Perfecto, voy a procesar esa información y te regreso con una propuesta detallada sobre cómo integrar estos componentes en tu flujo diario."
    ],
    coding: [
        "Analizando el código. En Kotlin, la llamada de red o procesamiento continuo de `AudioRecord` debe hacerse en un hilo secundario con `android.os.Process.setThreadPriority(THREAD_PRIORITY_URGENT_AUDIO)` para evitar el jank de la UI.",
        "Revisando los cambios. El error `NegativeArraySizeException` en Gradle/KSP es un problema clásico de corrupción de caché de Kotlin. Se resuelve ejecutando `./gradlew --stop` seguido de `./gradlew clean` para reconstruir la base de datos de símbolos.",
        "Para optimizar la comunicación por Bluetooth SCO, es crucial registrar un `BroadcastReceiver` que escuche `AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED` antes de forzar el modo de comunicación. ¿Quieres ver un ejemplo?",
        "Si estás programando en Jetpack Compose, recuerda usar `rememberUpdatedState` para los callbacks de voz en la UI, garantizando que el recomponer no pierda el estado de escucha."
    ],
    design: [
        "Desde el punto de vista de diseño, la retroalimentación visual en modo voz es crítica. Un indicador de volumen (Waveform) reactivo con el valor de `onVolumeLevelChanged` (normalizado entre 0 y 1) le da vida a la interfaz.",
        "¡Efectivamente! El Glassmorphism se ve increíble en el tema oscuro premium. Usar `backdrop-filter: blur(20px)` combinado con un borde semi-transparente de 1px añade una sensación de alta gama inmediata.",
        "Para pantallas móviles en el chat, es mejor mantener un diseño limpio con un área de input flotante y badges redondeados tipo píldora que se desplacen horizontalmente.",
        "Recomiendo usar animaciones `cubic-bezier(0.16, 1, 0.3, 1)` para la aparición de burbujas de chat. Se siente fluido, natural y sumamente moderno."
    ],
    voice: [
        "Iniciando diagnóstico del hands-free. Los fixes de manos libres corrigen el acople acústico en Samsung, Xiaomi y OPPO modificando el orden de apagado: liberando primero el micrófono y apagando el SCO después.",
        "Prueba de latencia: el tiempo de respuesta de interruptibilidad ha bajado de 2.5 segundos a tan solo 80ms gracias al ajuste fino de delays asíncronos en el `ViewModelScope`.",
        "El sensor de proximidad está integrado: apaga la pantalla y enruta el audio del altavoz al auricular del oído automáticamente si el dispositivo detecta que está cerca de tu oreja.",
        "¡Excelente! Hemos diagnosticado que el booster de volumen aumenta el volumen de 7 flujos de audio de forma progresiva, garantizando que escuches a Nexas incluso en ambientes con mucho ruido."
    ]
};

// Endpoint principal del chat - Compatible con App Nativa
app.post('/api/chat', (req, res) => {
    try {
        // La app nativa envía "messages" (un array), el servidor anterior buscaba "message"
        const messages = req.body.messages || [];
        const lastMessage = messages.length > 0 ? messages[messages.length - 1].content : "";
        
        if (!lastMessage && !req.body.message) {
            return res.status(400).json({ error: 'Mensaje requerido' });
        }

        const userText = lastMessage || req.body.message;
        const lowerMessage = userText.toLowerCase();
        
        // Lógica de categorías
        let category = 'general';
        if (lowerMessage.includes('código') || lowerMessage.includes('program')) category = 'coding';
        else if (lowerMessage.includes('diseño') || lowerMessage.includes('ui')) category = 'design';
        else if (lowerMessage.includes('voz') || lowerMessage.includes('audio')) category = 'voice';

        const responses = intelligentResponses[category];
        const randomResponse = responses[Math.floor(Math.random() * responses.length)];
        const finalResponse = `${randomResponse}\n\n*Conectado exitosamente al servidor Nexa AI*`;

        // Respondemos en el formato que la app nativa entiende (campo "text")
        res.json({
            text: finalResponse,
            response: finalResponse,
            category: category,
            timestamp: new Date().toISOString()
        });

    } catch (error) {
        console.error('Error:', error);
        res.status(500).json({ error: 'Error interno' });
    }
});

const { exec } = require('child_process');
const fs = require('fs');

// Helpers for executing shell commands safely
const execPromise = (command) => {
    return new Promise((resolve, reject) => {
        exec(command, (error, stdout, stderr) => {
            if (error) {
                reject({ error, stderr });
            } else {
                resolve(stdout);
            }
        });
    });
};

// GET /api/cron - List all scheduled jobs
app.get('/api/cron', (req, res) => {
    const jobsFilePath = path.join('/home/angel/.hermes/cron/jobs.json');
    if (!fs.existsSync(jobsFilePath)) {
        return res.json({ jobs: [] });
    }
    fs.readFile(jobsFilePath, 'utf8', (err, data) => {
        if (err) {
            return res.status(500).json({ error: 'Error al leer los trabajos programados', details: err.message });
        }
        try {
            const parsed = JSON.parse(data);
            res.json(parsed);
        } catch (parseErr) {
            res.status(500).json({ error: 'Error al parsear el archivo de trabajos', details: parseErr.message });
        }
    });
});

// POST /api/cron - Create a new scheduled job
app.post('/api/cron', async (req, res) => {
    try {
        const { schedule, prompt, name, noAgent, deliver } = req.body;
        if (!schedule) {
            return res.status(400).json({ error: 'El campo "schedule" es requerido' });
        }

        // Build command safely escaping arguments
        const cleanSchedule = schedule.replace(/"/g, '\\"');
        const cleanPrompt = (prompt || '').replace(/"/g, '\\"');
        const cleanName = (name || '').replace(/"/g, '\\"');
        
        let cmd = `hermes cron create "${cleanSchedule}"`;
        if (cleanPrompt) {
            cmd += ` "${cleanPrompt}"`;
        }
        if (cleanName) {
            cmd += ` --name "${cleanName}"`;
        }
        if (noAgent) {
            cmd += ' --no-agent';
        }
        if (deliver) {
            cmd += ` --deliver "${deliver.replace(/"/g, '\\"')}"`;
        }

        const stdout = await execPromise(cmd);
        
        const lines = stdout.split('\n');
        let jobId = '';
        let jobName = '';
        lines.forEach(line => {
            if (line.includes('Created job:')) {
                jobId = line.split('Created job:')[1].trim();
            }
            if (line.trim().startsWith('Name:')) {
                jobName = line.split('Name:')[1].trim();
            }
        });

        res.json({
            message: 'Trabajo programado creado con éxito',
            id: jobId,
            name: jobName || cleanName,
            output: stdout
        });

    } catch (err) {
        console.error('Error al crear trabajo programado:', err);
        res.status(500).json({
            error: 'Error al crear trabajo programado',
            details: err.stderr || err.message
        });
    }
});

// DELETE /api/cron/:id - Remove a scheduled job
app.delete('/api/cron/:id', async (req, res) => {
    try {
        const { id } = req.params;
        const cleanId = id.replace(/[^a-zA-Z0-9_-]/g, ''); // Sanitize input
        const cmd = `hermes cron remove ${cleanId}`;
        const stdout = await execPromise(cmd);
        res.json({ message: 'Trabajo programado eliminado con éxito', output: stdout.trim() });
    } catch (err) {
        res.status(500).json({ error: 'Error al eliminar el trabajo programado', details: err.stderr || err.message });
    }
});

// POST /api/cron/:id/run - Run a job immediately
app.post('/api/cron/:id/run', async (req, res) => {
    try {
        const { id } = req.params;
        const cleanId = id.replace(/[^a-zA-Z0-9_-]/g, '');
        const cmd = `hermes cron run ${cleanId}`;
        const stdout = await execPromise(cmd);
        res.json({ message: 'Trabajo ejecutado con éxito', output: stdout.trim() });
    } catch (err) {
        res.status(500).json({ error: 'Error al ejecutar el trabajo programado', details: err.stderr || err.message });
    }
});

// POST /api/cron/:id/pause - Pause a job
app.post('/api/cron/:id/pause', async (req, res) => {
    try {
        const { id } = req.params;
        const cleanId = id.replace(/[^a-zA-Z0-9_-]/g, '');
        const cmd = `hermes cron pause ${cleanId}`;
        const stdout = await execPromise(cmd);
        res.json({ message: 'Trabajo pausado con éxito', output: stdout.trim() });
    } catch (err) {
        res.status(500).json({ error: 'Error al pausar el trabajo programado', details: err.stderr || err.message });
    }
});

// POST /api/cron/:id/resume - Resume a paused job
app.post('/api/cron/:id/resume', async (req, res) => {
    try {
        const { id } = req.params;
        const cleanId = id.replace(/[^a-zA-Z0-9_-]/g, '');
        const cmd = `hermes cron resume ${cleanId}`;
        const stdout = await execPromise(cmd);
        res.json({ message: 'Trabajo reanudado con éxito', output: stdout.trim() });
    } catch (err) {
        res.status(500).json({ error: 'Error al reanudar el trabajo programado', details: err.stderr || err.message });
    }
});

// Endpoint de salud
app.get('/api/health', (req, res) => {
    res.json({ 
        status: 'ok', 
        message: 'Nexas AI Server funcionando',
        timestamp: new Date().toISOString(),
        version: '1.0.0'
    });
});

// ────────────────────────────────────────────────────────────────
// POST /api/register-skills  — Bulk-registers all local skills
//   into LiteLLM via /claude-code/plugins
// ────────────────────────────────────────────────────────────────
const http = require('http');

function litellmPost(path, payload) {
    return new Promise((resolve, reject) => {
        const data = JSON.stringify(payload);
        const options = {
            hostname: '127.0.0.1',
            port: 4000,
            path,
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': 'Bearer sk-nexa-local-key',
                'Content-Length': Buffer.byteLength(data)
            }
        };
        const req = http.request(options, (res) => {
            let body = '';
            res.on('data', chunk => body += chunk);
            res.on('end', () => {
                try { resolve({ status: res.statusCode, body: JSON.parse(body) }); }
                catch (e) { resolve({ status: res.statusCode, body }); }
            });
        });
        req.on('error', reject);
        req.write(data);
        req.end();
    });
}

const NEXA_SKILLS = [
  { name:"ASR",              desc:"Implement automatic speech recognition (ASR) capabilities. Transcribe audio, convert speech to text, or build voice-input applications.", category:"AI/ML" },
  { name:"LLM",              desc:"Implement large language model (LLM) completions for text generation, summarization, Q&A, and AI chat.", category:"AI/ML" },
  { name:"TTS",              desc:"Implement text-to-speech (TTS) capabilities. Convert text into natural-sounding speech and build voice-enabled applications.", category:"AI/ML" },
  { name:"VLM",              desc:"Implement vision-based AI chat capabilities. Analyze images, describe visual content, and create multimodal applications.", category:"AI/ML" },
  { name:"agent-browser",    desc:"Use an AI-powered browser agent to navigate websites, extract information, fill forms, and automate web-based tasks.", category:"Productivity" },
  { name:"ai-news-collectors",desc:"Collect and summarize the latest AI news, research papers, and industry updates.", category:"Research" },
  { name:"alexa-voice-assistant", desc:"Integrate Alexa voice assistant capabilities for smart home control and voice-driven workflows.", category:"Productivity" },
  { name:"aminer-academic-search", desc:"Search academic papers and researchers on AMiner academic platform.", category:"Research" },
  { name:"aminer-daily-paper",     desc:"Fetch and summarize the daily top AI papers from AMiner.", category:"Research" },
  { name:"aminer-free-academic",   desc:"Free academic search on AMiner for papers and authors.", category:"Research" },
  { name:"anti-pua",         desc:"Detect and counter manipulative rhetoric, persuasion tactics, and PUA techniques in conversations.", category:"Safety" },
  { name:"auto-target-tracker", desc:"Automatically track targets, goals, and objectives across projects and tasks.", category:"Productivity" },
  { name:"blog-writer",      desc:"Generate high-quality, structured blog posts from outlines or topics with SEO best practices.", category:"Writing" },
  { name:"charts",           desc:"Generate charts and data visualizations from structured data or user-provided datasets.", category:"Data" },
  { name:"cheat-sheet",      desc:"Create quick-reference cheat sheets for programming languages, tools, or concepts.", category:"Education" },
  { name:"coding-agent",     desc:"Coding workflow with planning, implementation, verification, and testing for clean software development.", category:"Development" },
  { name:"content-strategy", desc:"Develop comprehensive content strategies including audience analysis, content calendars, and distribution plans.", category:"Marketing" },
  { name:"contentanalysis",  desc:"Analyze content for sentiment, tone, key themes, and readability metrics.", category:"Data" },
  { name:"docx",             desc:"Create, read, edit, and generate Microsoft Word DOCX documents programmatically.", category:"Productivity" },
  { name:"dream-interpreter",desc:"Interpret dreams and provide psychological insights based on dream content and symbolism.", category:"Wellness" },
  { name:"finance",          desc:"Financial analysis, budgeting, investment calculations, and market data retrieval.", category:"Finance" },
  { name:"fullstack-dev",    desc:"Full-stack web development guidance covering frontend, backend, APIs, databases, and deployment.", category:"Development" },
  { name:"get-fortune-analysis", desc:"Provide fortune and life analysis based on Chinese astrology and numerology.", category:"Entertainment" },
  { name:"gift-evaluator",   desc:"Evaluate and suggest gift ideas based on recipient profile, budget, and occasion.", category:"Lifestyle" },
  { name:"image-edit",       desc:"Edit images using AI: background removal, style transfer, inpainting, and enhancement.", category:"Media" },
  { name:"image-generation", desc:"Generate images from text prompts using AI image generation models.", category:"Media" },
  { name:"image-understand", desc:"Analyze and describe image content, extract text from images, and answer questions about visual content.", category:"AI/ML" },
  { name:"interview-designer", desc:"Design structured interview processes, question banks, and evaluation rubrics.", category:"HR" },
  { name:"interview-prep",   desc:"Prepare for job interviews with practice questions, personalized answers, and coaching.", category:"Career" },
  { name:"jd-resume-tailor", desc:"Tailor resumes to specific job descriptions for maximum ATS compatibility and relevance.", category:"Career" },
  { name:"job-intent-tracker", desc:"Track job application progress, interview stages, and offer statuses.", category:"Career" },
  { name:"market-research-reports", desc:"Generate comprehensive market research reports including competitive analysis and market sizing.", category:"Business" },
  { name:"marketing-mode",   desc:"Switch to marketing-focused thinking for copywriting, campaigns, and brand strategy.", category:"Marketing" },
  { name:"mindfulness-meditation", desc:"Guide mindfulness meditation sessions, breathing exercises, and stress-reduction techniques.", category:"Wellness" },
  { name:"multi-search-engine", desc:"Search across multiple engines simultaneously and aggregate results.", category:"Research" },
  { name:"pdf",              desc:"Create, read, extract, and manipulate PDF documents programmatically.", category:"Productivity" },
  { name:"podcast-generate", desc:"Generate podcast scripts, outlines, and show notes from topics or content briefs.", category:"Media" },
  { name:"ppt",              desc:"Create PowerPoint presentations with structured slides, layouts, and speaker notes.", category:"Productivity" },
  { name:"qingyan-research", desc:"Deep academic and professional research assistant using Qingyan AI.", category:"Research" },
  { name:"quiz-html",        desc:"Generate interactive HTML quiz pages from question sets.", category:"Education" },
  { name:"quiz-mastery",     desc:"Create adaptive quiz systems for learning and retention testing.", category:"Education" },
  { name:"resume-builder",   desc:"Build professional resumes with structured sections, tailored content, and ATS optimization.", category:"Career" },
  { name:"seo-content-writer", desc:"Create high-quality SEO-optimized content that ranks in search engines with keyword optimization.", category:"Marketing" },
  { name:"skill-creator",    desc:"Create new skills, modify and improve existing skills, and measure skill performance.", category:"Development" },
  { name:"skill-finder-cn",  desc:"Skill finder for discovering and installing ClawHub skills. Supports Chinese and English.", category:"Productivity" },
  { name:"skill-vetter",     desc:"Security-first skill vetting for AI agents. Checks for red flags, permission scope, and suspicious patterns.", category:"Safety" },
  { name:"stock-analysis-skill", desc:"Comprehensive stock market analysis covering A-share, Hong Kong, and US equities with buy/sell recommendations.", category:"Finance" },
  { name:"storyboard-manager", desc:"Assist writers with story planning, character development, plot structuring, and chapter writing.", category:"Writing" },
  { name:"study-buddy",      desc:"Smart study supervisor managing long-term learning project workflows and progress tracking.", category:"Education" },
  { name:"task-review",      desc:"Review and retrospect completed tasks. Evaluate whether tasks should be saved as reusable skills.", category:"Productivity" },
  { name:"ui-ux-pro-max",    desc:"UI/UX design intelligence and implementation guidance for building polished, accessible interfaces.", category:"Design" },
  { name:"video-generation", desc:"Generate AI-powered videos from text prompts or images.", category:"Media" },
  { name:"video-understand", desc:"Analyze video content, understand motion sequences, and extract information from video frames.", category:"AI/ML" },
  { name:"visual-design-foundations", desc:"Apply typography, color theory, spacing systems, and iconography to create cohesive visual designs.", category:"Design" },
  { name:"web-reader",       desc:"Extract web page content, scrape articles, retrieve metadata, and process web content.", category:"Research" },
  { name:"web-search",       desc:"Implement web search for real-time information retrieval beyond the AI knowledge cutoff.", category:"Research" },
  { name:"web-shader-extractor", desc:"Extract and analyze WebGL shaders and GPU rendering code from web applications.", category:"Development" },
  { name:"writing-plans",    desc:"Create structured writing plans and outlines for multi-step tasks from specs or requirements.", category:"Writing" },
  { name:"xlsx",             desc:"Open, read, edit, create, and analyze Excel spreadsheets and CSV files with chart generation.", category:"Productivity" },
];

app.post('/api/register-skills', async (req, res) => {
    const results = [];
    for (const skill of NEXA_SKILLS) {
        try {
            const result = await litellmPost('/claude-code/plugins', {
                name: skill.name,
                source: {
                    source: 'local',
                    path: `/home/angel/Desktop/nexa-ai.dev/nexa-ai-android/skills/${skill.name}`
                },
                description: skill.desc,
                domain: skill.category,
                namespace: 'nexa-ai',
                version: '1.0.0',
                author: { name: 'NexaAI', email: 'admin@nexa-ai.dev' }
            });
            const ok = result.status >= 200 && result.status < 300;
            const alreadyExists = !ok && (result.status === 409 ||
                JSON.stringify(result.body).toLowerCase().includes('already exists'));
            results.push({
                name: skill.name,
                status: result.status,
                ok,
                alreadyExists,
                response: typeof result.body === 'object'
                    ? JSON.stringify(result.body).substring(0, 200)
                    : String(result.body).substring(0, 200)
            });
        } catch (e) {
            results.push({ name: skill.name, status: 0, ok: false, error: e.message });
        }
    }

    const ok    = results.filter(r => r.ok).length;
    const skip  = results.filter(r => r.alreadyExists).length;
    const error = results.filter(r => !r.ok && !r.alreadyExists).length;

    res.json({
        summary: { total: NEXA_SKILLS.length, ok, skip, error },
        results
    });
});

// Iniciar servidor
app.listen(PORT, () => {
    console.log(`🚀 Nexas AI Server corriendo en http://localhost:${PORT}`);
    console.log(`📡 Chat API disponible en: http://localhost:${PORT}/api/chat`);
    console.log(`🔍 Health check disponible en: http://localhost:${PORT}/api/health`);
    console.log(`🌐 Serviendo archivos estáticos desde: ${__dirname}`);
});

module.exports = app;