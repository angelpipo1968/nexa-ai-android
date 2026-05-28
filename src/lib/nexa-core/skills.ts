/**
 * NEXA CORE — Sistema de Auto-Aprendizaje de Skills (Habilidades)
 * Permite a NEXA extraer procedimientos, reglas y correcciones del usuario en caliente
 * y guardarlas como "Skills" en Redis para usarlas en futuras sesiones.
 */

import { Redis } from '@upstash/redis';

// Lazy-initialize Redis para evitar caídas si no hay URL configurada
let _redis: Redis | null = null;

function getRedis(): Redis | null {
    if (_redis) return _redis;
    if (!process.env.REDIS_URL) return null;
    try {
        _redis = new Redis({
            url: process.env.REDIS_URL,
            token: process.env.REDIS_TOKEN || '',
        });
    } catch (e) {
        console.error('[NEXA Skills] Failed to initialize Redis:', e);
        return null;
    }
    return _redis;
}

export interface NexaSkill {
    name: string;
    description: string;
    instructions: string;
    createdAt: string;
}

/**
 * Guarda una nueva habilidad autogenerada para el usuario
 */
export async function saveSkill(userId: string, skill: NexaSkill): Promise<void> {
    const redis = getRedis();
    if (!redis) return;
    try {
        const key = `skills:${userId}`;
        // Guardamos el objeto serializado en un hash de Redis
        await redis.hset(key, { [skill.name]: JSON.stringify(skill) });
    } catch (e) {
        console.error("Error guardando skill en Redis:", e);
    }
}

/**
 * Obtiene todas las habilidades guardadas de un usuario
 */
export async function getSkills(userId: string): Promise<NexaSkill[]> {
    const redis = getRedis();
    if (!redis) return [];
    try {
        const key = `skills:${userId}`;
        const rawSkills = await redis.hgetall(key);
        if (!rawSkills) return [];

        const skills: NexaSkill[] = [];
        for (const value of Object.values(rawSkills)) {
            try {
                skills.push(JSON.parse(value as string));
            } catch {}
        }
        // Ordenamos las skills por fecha de creación (más recientes primero)
        return skills.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
    } catch {
        return [];
    }
}

/**
 * Elimina una skill específica del usuario
 */
export async function deleteSkill(userId: string, skillName: string): Promise<void> {
    const redis = getRedis();
    if (!redis) return;
    try {
        const key = `skills:${userId}`;
        await redis.hdel(key, skillName);
    } catch (e) {
        console.error("Error eliminando skill:", e);
    }
}

/**
 * Analiza el flujo de conversación en segundo plano para ver si el usuario
 * ha corregido al asistente o le ha enseñado una regla o procedimiento reutilizable.
 * Si es así, extrae y guarda una nueva skill.
 */
export async function extractAndSaveSkills(
    userId: string, 
    userMessage: string, 
    assistantMessage: string
): Promise<void> {
    const groqKey = process.env.GROQ_API_KEY;
    if (!groqKey) return;

    // Triggers comunes que sugieren enseñanza o corrección de reglas
    const lowerUser = userMessage.toLowerCase();
    const teachingIndicators = [
        'debes hacer', 'siempre que te pida', 'nunca hagas', 'cuando te diga',
        'regla:', 'instrucción:', 'la forma correcta', 'así es como se', 'para hacer esto',
        'no es así', 'te equivocaste', 'corrige'
    ];

    const seemsLikeTeaching = teachingIndicators.some(indicator => lowerUser.includes(indicator));
    if (!seemsLikeTeaching) return;

    try {
        // Usamos Groq (Llama-3.3-70b) para estructurar la nueva habilidad
        const prompt = `Analiza la conversación y determina si el usuario está enseñando una nueva regla, instrucción, flujo de trabajo, o corrigiendo al asistente sobre cómo realizar una tarea específica.
Si el usuario está enseñando algo reutilizable, extrae y estructura una nueva "Skill" en formato JSON.
Si no hay ningún procedimiento o regla reutilizable en el mensaje del usuario, responde únicamente con la palabra "NONE".

Formato JSON de salida (solo responde con el JSON puro, sin markdown ni explicaciones adicionales):
{
  "name": "Nombre descriptivo en minúsculas y guiones (ej: compile-zig-windows)",
  "description": "Trigger o contexto de cuándo aplicar esta habilidad (ej: Use when the user asks to compile Zig engine on Windows)",
  "instructions": "Instrucciones paso a paso, comandos exactos o reglas de comportamiento que el asistente debe seguir."
}`;

        const res = await fetch('https://api.groq.com/openai/v1/chat/completions', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${groqKey}` },
            body: JSON.stringify({
                model: 'llama-3.3-70b-versatile',
                messages: [
                    { role: 'system', content: prompt },
                    { role: 'user', content: `[MENSAJE DEL USUARIO]: ${userMessage}\n\n[RESPUESTA DEL ASISTENTE]: ${assistantMessage}` }
                ],
                temperature: 0.1
            }),
        });

        const data = await res.json();
        const output = data.choices[0].message.content.trim();

        if (output && output !== "NONE" && !output.includes("NONE")) {
            // Limpiamos posible markdown de Groq
            const cleanJson = output.replace(/```json|```/g, '').trim();
            const parsedSkill = JSON.parse(cleanJson);
            
            if (parsedSkill.name && parsedSkill.description && parsedSkill.instructions) {
                const newSkill: NexaSkill = {
                    name: parsedSkill.name.toLowerCase().replace(/[^a-z0-9-]/g, '-'),
                    description: parsedSkill.description,
                    instructions: parsedSkill.instructions,
                    createdAt: new Date().toISOString()
                };
                
                await saveSkill(userId, newSkill);
                console.log(`[NEXA Skills] ¡Nueva habilidad aprendida y guardada!: ${newSkill.name}`);
            }
        }
    } catch (e) {
        console.error("[NEXA Skills] Error extrayendo habilidades:", e);
    }
}
