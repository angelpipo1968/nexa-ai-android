/**
 * NEXA AUTONOMOUS ORCHESTRATOR (V4)
 * El cerebro central que permite a NEXA razonar y encadenar herramientas.
 */

import { getWolframAnswer } from './wolfram';
import { searchMovies } from './tmdb';
import { getNASAAPOD, searchMarsPhotos } from './nasa';
import { getStockPrice, getCryptoPrice } from './finance';
import { searchFlights } from './aviation';
import { getLotteryResults } from './lottery';
import { getWeather } from './weather';

// Herramientas de Búsqueda Web Profunda
async function deepSearch(query: string): Promise<string> {
    const tavilyKey = process.env.TAVILY_API_KEY;
    if (!tavilyKey) return "Error: Falta TAVILY_API_KEY.";
    try {
        const res = await fetch('https://api.tavily.com/search', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ api_key: tavilyKey, query, search_depth: "advanced" })
        });
        const data = await res.json();
        return data.results.map((r: any) => `- ${r.title}: ${r.content}`).join('\n');
    } catch { return "Error en búsqueda web profunda."; }
}

export interface AgentTask {
    id: string;
    step: string;
    tool: string;
    params: any;
    status: 'pending' | 'completed' | 'failed';
    result?: string;
}

export async function runAutonomousLoop(userQuery: string): Promise<string> {
    const groqKey = process.env.GROQ_API_KEY;
    if (!groqKey) return "Error: Falta GROQ_API_KEY para el orquestador.";

    try {
        // 1. PLANIFICACIÓN (Thinking)
        // Le pedimos a Groq que cree un plan de ejecución basado en las herramientas disponibles
        const planRes = await fetch('https://api.groq.com/openai/v1/chat/completions', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${groqKey}` },
            body: JSON.stringify({
                model: 'llama-3.3-70b-versatile',
                messages: [
                    { 
                        role: 'system', 
                        content: `Eres el Orquestador de NEXA. Tu trabajo es crear un plan de 1 a 3 pasos para responder al usuario usando estas herramientas:
                        - 'wolfram': Datos científicos, matemáticos, hechos.
                        - 'movies': Cine, series, actores.
                        - 'nasa': Espacio, Marte, fotos astronómicas.
                        - 'finance': Bolsa (símbolos tipo AAPL) y Cripto.
                        - 'flights': Estado de vuelos (IATA).
                        - 'lottery': Resultados de sorteos.
                        - 'weather': Clima por ciudad.
                        - 'web_search': Búsqueda general en internet para cualquier otra cosa.
                        
                        Responde EXCLUSIVAMENTE en formato JSON: {"plan": [{"step": "desc", "tool": "name", "params": {"key": "val"}}]} ` 
                    },
                    { role: 'user', content: userQuery }
                ],
                response_format: { type: "json_object" }
            }),
        });

        const planData = await planRes.json();
        const plan: AgentTask[] = planData.choices[0].message.content ? JSON.parse(planData.choices[0].message.content).plan : [];

        if (!plan || plan.length === 0) return "NEXA no detectó tareas especiales para este pedido.";

        // 2. EJECUCIÓN (Acting)
        let totalContext = "";
        for (const task of plan) {
            let result = "";
            switch (task.tool) {
                case 'wolfram': result = await getWolframAnswer(task.params.query || userQuery); break;
                case 'movies': result = await searchMovies(task.params.query || userQuery); break;
                case 'nasa': result = task.params.type === 'mars' ? await searchMarsPhotos() : await getNASAAPOD(); break;
                case 'finance': result = task.params.stock ? await getStockPrice(task.params.stock) : await getCryptoPrice(task.params.crypto || 'bitcoin'); break;
                case 'flights': result = await searchFlights(task.params.origin, task.params.destination); break;
                case 'lottery': result = await getLotteryResults(task.params.game || 'us_powerball'); break;
                case 'weather': result = await getWeather(task.params.city); break;
                case 'web_search': result = await deepSearch(task.params.query || userQuery); break;
            }
            totalContext += `[RESULTADO ${task.tool.toUpperCase()}]: ${result}\n\n`;
        }

        // 3. SÍNTESIS FINAL
        const finalRes = await fetch('https://api.groq.com/openai/v1/chat/completions', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${groqKey}` },
            body: JSON.stringify({
                model: 'llama-3.3-70b-versatile',
                messages: [
                    { role: 'system', content: 'Eres NEXA, una IA súper inteligente. Responde al usuario de forma épica y profesional usando los datos obtenidos por tus agentes.' },
                    { role: 'user', content: `Usuario: ${userQuery}\n\nContexto obtenido:\n${totalContext}\n\nResponde ahora.` }
                ],
            }),
        });

        const finalData = await finalRes.json();
        return finalData.choices[0].message.content;

    } catch (error: any) {
        return `Error en el orquestador autónomo: ${error.message}`;
    }
}
