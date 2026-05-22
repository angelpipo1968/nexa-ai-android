/**
 * NEXA CORE — Memoria de Largo Plazo (Inspirado en Mem0)
 * Utiliza Redis para guardar hechos y preferencias del usuario de forma persistente.
 */

import { Redis } from '@upstash/redis';

const redis = new Redis({
    url: process.env.REDIS_URL || '',
    token: process.env.REDIS_TOKEN || '',
});

export async function saveFact(userId: string, fact: string): Promise<void> {
    if (!process.env.REDIS_URL) return;
    try {
        // Guardamos el hecho en una lista para ese usuario
        await redis.lpush(`memory:${userId}`, fact);
        // Mantenemos solo los últimos 50 recuerdos para no saturar
        await redis.ltrim(`memory:${userId}`, 0, 49);
    } catch (e) {
        console.error("Error guardando memoria:", e);
    }
}

export async function getMemories(userId: string): Promise<string[]> {
    if (!process.env.REDIS_URL) return [];
    try {
        return await redis.lrange(`memory:${userId}`, 0, -1);
    } catch {
        return [];
    }
}

export async function extractAndSaveFacts(userId: string, userMessage: string): Promise<void> {
    const groqKey = process.env.GROQ_API_KEY;
    if (!groqKey) return;

    try {
        // Usamos una IA rápida (Groq) para extraer hechos
        const res = await fetch('https://api.groq.com/openai/v1/chat/completions', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${groqKey}` },
            body: JSON.stringify({
                model: 'llama-3.3-70b-versatile',
                messages: [
                    { 
                        role: 'system', 
                        content: 'Extrae hechos clave sobre el usuario, sus preferencias o su entorno en oraciones cortas. Si no hay nada relevante, responde "NONE". Ejemplo: "Al usuario le gusta el cine", "El usuario tiene una API de NASA".' 
                    },
                    { role: 'user', content: userMessage }
                ],
            }),
        });

        const data = await res.json();
        const fact = data.choices[0].message.content;

        if (fact && fact !== "NONE" && !fact.includes("NONE")) {
            await saveFact(userId, fact);
        }
    } catch (e) {
        console.error("Error extrayendo hechos:", e);
    }
}

export async function logActivity(userId: string, city: string, country: string, topic: string): Promise<void> {
    if (!process.env.REDIS_URL) return;
    try {
        const timestamp = new Date().toISOString();
        const activity = { timestamp, city, country, topic };
        
        // Guardamos un historial de actividad
        await redis.lpush(`activity:${userId}`, JSON.stringify(activity));
        await redis.ltrim(`activity:${userId}`, 0, 99); // Guardamos los últimos 100 eventos
        
        // Actualizamos el "Último Visto"
        await redis.set(`last_seen:${userId}`, timestamp);
    } catch (e) {
        console.error("Error logueando actividad:", e);
    }
}
