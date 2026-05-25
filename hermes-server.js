import express from 'express';
import cors from 'cors';
import * as cheerio from 'cheerio';
import { exec } from 'child_process';
import util from 'util';

const execPromise = util.promisify(exec);
const app = express();

app.use(cors());
app.use(express.json());

const OLLAMA_URL = 'http://127.0.0.1:11434/api/generate';
const MODEL = 'nexa-os:latest';

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
    try {
        const userMessage = req.body.message;
        let enhancedPrompt = userMessage;
        let systemContext = "Eres Hermes, un asistente de IA local para Nexa OS. Eres experto en programación y muy servicial.";

        // Detectar si hay una URL
        const urlMatch = userMessage.match(/https?:\/\/[^\s]+/);
        if (urlMatch) {
            const url = urlMatch[0];
            console.log(`Buscando URL: ${url}`);
            const content = await fetchUrlContent(url);
            enhancedPrompt = `El usuario mencionó esta URL: ${url}\n\nContenido extraído de la página:\n"""\n${content}\n"""\n\nMensaje original del usuario: ${userMessage.replace(url, '')}`;
        }

        // Detectar si pide hacer commit / push
        const msgLower = userMessage.toLowerCase();
        if (msgLower.includes('commit') || msgLower.includes('push') || msgLower.includes('guarda los cambios') || msgLower.includes('sube los cambios')) {
            console.log('Ejecutando Git Commit y Push...');
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
        console.log("Enviando a Ollama...");
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
            throw new Error(`Ollama falló: ${ollamaRes.statusText}`);
        }

        const data = await ollamaRes.json();
        
        res.json({ response: data.response });

    } catch (error) {
        console.error(error);
        res.status(500).json({ error: error.message });
    }
});

const PORT = 3001;
app.listen(PORT, () => {
    console.log(`Hermes Backend corriendo en http://localhost:${PORT}`);
    console.log(`Asegúrate de ejecutar esto desde la raíz del proyecto para que los comandos git funcionen.`);
});
