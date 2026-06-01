/**
 * NEXA CORE — Code Repairer & Auditor
 * Analiza, repara y optimiza código fuente.
 */

// @internal — not imported externally
export interface AuditResult {
    analysis: string;
    fixes: string;
    securityLevel: 'Safe' | 'Warning' | 'Critical';
    optimizedCode: string;
}

export async function auditCode(code: string, language: string = 'auto'): Promise<string> {
    const groqKey = process.env.GROQ_API_KEY;
    if (!groqKey) return "Falta GROQ_API_KEY.";

    try {
        const res = await fetch('https://api.groq.com/openai/v1/chat/completions', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${groqKey}` },
            body: JSON.stringify({
                model: 'llama-3.3-70b-versatile',
                messages: [
                    { 
                        role: 'system', 
                        content: `Eres el Auditor de Código de NEXA. Tu misión es encontrar errores, vulnerabilidades y puntos de optimización.
                        
                        Proporciona un reporte estructurado:
                        1. ANÁLISIS: Qué está mal o qué se puede mejorar.
                        2. SEGURIDAD: Nivel de riesgo (Bajo, Medio, Crítico).
                        3. CÓDIGO REPARADO: El código corregido y optimizado.
                        
                        Sé extremadamente técnico y preciso.` 
                    },
                    { role: 'user', content: `Lenguaje: ${language}\n\nCódigo:\n${code}` }
                ],
            }),
        });

        const data = await res.json();
        return data.choices[0].message.content;
    } catch (error: any) {
        return `Error en la auditoría de código: ${error.message}`;
    }
}

// @internal — not imported externally
export async function translateCode(code: string, from: string, to: string): Promise<string> {
    const groqKey = process.env.GROQ_API_KEY;
    try {
        const res = await fetch('https://api.groq.com/openai/v1/chat/completions', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${groqKey}` },
            body: JSON.stringify({
                model: 'llama-3.3-70b-versatile',
                messages: [
                    { role: 'system', content: `Traduce el siguiente código de ${from} a ${to}. Mantén la lógica intacta pero usa las mejores prácticas del lenguaje destino.` },
                    { role: 'user', content: code }
                ],
            }),
        });
        const data = await res.json();
        return data.choices[0].message.content;
    } catch (e: any) {
        return `Error traduciendo código: ${e.message}`;
    }
}
