/**
 * NEXA CORE — StackOverflow API Integration
 * Busca respuestas de programacion con votos de la comunidad.
 * API gratuita, sin clave requerida (300 peticiones/dia sin key).
 */

interface StackOverflowQuestion {
    question_id: number;
    title: string;
    body?: string;
    score: number;
    answer_count: number;
    is_answered: boolean;
    tags: string[];
    link: string;
    creation_date: number;
    accepted_answer_id?: number;
}

interface StackOverflowAnswer {
    answer_id: number;
    body: string;
    score: number;
    is_accepted: boolean;
    link: string;
}

/**
 * Busca preguntas en StackOverflow por texto
 */
export async function searchStackOverflow(query: string, limit: number = 5): Promise<string> {
    try {
        // Search for questions
        const searchUrl = `https://api.stackexchange.com/2.3/search/advanced?` +
            new URLSearchParams({
                order: 'desc',
                sort: 'relevance',
                q: query,
                site: 'stackoverflow',
                filter: 'withbody',
                pagesize: String(limit),
                answers: '1', // Solo preguntas con respuestas
            }).toString();

        const res = await fetch(searchUrl, {
            headers: { 'Accept-Encoding': 'gzip' },
            signal: AbortSignal.timeout(15000),
        });

        if (!res.ok) {
            return `Error al buscar en StackOverflow: HTTP ${res.status}`;
        }

        const data = await res.json();

        if (!data.items || data.items.length === 0) {
            return `No encontre resultados en StackOverflow para "${query}".`;
        }

        let report = `STACKOVERFLOW — Resultados para "${query}":\n\n`;

        for (let i = 0; i < data.items.length; i++) {
            const q: StackOverflowQuestion = data.items[i];
            report += `${i + 1}. **${q.title}**\n`;
            report += `   Votos: ${q.score} | Respuestas: ${q.answer_count} ${q.is_answered ? '✅ Resuelta' : '⏳ Sin resolver'}\n`;
            report += `   Tags: ${q.tags.join(', ')}\n`;
            report += `   Link: ${q.link}\n`;

            // If there's an accepted answer, fetch it
            if (q.accepted_answer_id && i === 0) {
                const answer = await getAcceptedAnswer(q.accepted_answer_id);
                if (answer) {
                    report += `\n   📌 **Respuesta aceptada** (Votos: ${answer.score}):\n`;
                    // Clean HTML and truncate for readability
                    const cleanAnswer = cleanHtml(answer.body);
                    const truncated = cleanAnswer.length > 800
                        ? cleanAnswer.slice(0, 800) + '...'
                        : cleanAnswer;
                    report += `   ${truncated}\n`;
                }
            }
            report += '\n';
        }

        return report;
    } catch (error: any) {
        return `Error al consultar StackOverflow: ${error.message}`;
    }
}

/**
 * Obtiene la respuesta aceptada de una pregunta
 */
async function getAcceptedAnswer(answerId: number): Promise<StackOverflowAnswer | null> {
    try {
        const url = `https://api.stackexchange.com/2.3/answers/${answerId}?` +
            new URLSearchParams({
                order: 'desc',
                sort: 'activity',
                site: 'stackoverflow',
                filter: 'withbody',
            }).toString();

        const res = await fetch(url, {
            headers: { 'Accept-Encoding': 'gzip' },
            signal: AbortSignal.timeout(10000),
        });

        if (!res.ok) return null;

        const data = await res.json();
        if (data.items && data.items.length > 0) {
            return data.items[0];
        }
        return null;
    } catch {
        return null;
    }
}

/**
 * Busca preguntas por tags especificos (ej: "javascript", "python", "react")
 */
export async function searchByTags(tags: string[], query?: string, limit: number = 5): Promise<string> {
    try {
        const params: Record<string, string> = {
            order: 'desc',
            sort: 'relevance',
            tagged: tags.join(';'),
            site: 'stackoverflow',
            filter: 'withbody',
            pagesize: String(limit),
        };

        if (query) {
            params.q = query;
        }

        const searchUrl = `https://api.stackexchange.com/2.3/search/advanced?` +
            new URLSearchParams(params).toString();

        const res = await fetch(searchUrl, {
            headers: { 'Accept-Encoding': 'gzip' },
            signal: AbortSignal.timeout(15000),
        });

        if (!res.ok) return `Error al buscar en StackOverflow: HTTP ${res.status}`;

        const data = await res.json();

        if (!data.items || data.items.length === 0) {
            return `No encontre resultados en StackOverflow para los tags: ${tags.join(', ')}.`;
        }

        let report = `STACKOVERFLOW — Tags: [${tags.join('] [')}]:\n\n`;

        for (let i = 0; i < data.items.length; i++) {
            const q: StackOverflowQuestion = data.items[i];
            report += `${i + 1}. **${q.title}**\n`;
            report += `   Votos: ${q.score} | Respuestas: ${q.answer_count} ${q.is_answered ? '✅' : '⏳'}\n`;
            report += `   Link: ${q.link}\n\n`;
        }

        return report;
    } catch (error: any) {
        return `Error al consultar StackOverflow: ${error.message}`;
    }
}

/**
 * Limpia HTML basico de las respuestas de StackOverflow
 */
function cleanHtml(html: string): string {
    return html
        // Code blocks
        .replace(/<pre><code[^>]*>([\s\S]*?)<\/code><\/pre>/g, '\n```\n$1\n```\n')
        // Inline code
        .replace(/<code>(.*?)<\/code>/g, '`$1`')
        // Bold
        .replace(/<strong>(.*?)<\/strong>/g, '**$1**')
        .replace(/<b>(.*?)<\/b>/g, '**$1**')
        // Italic
        .replace(/<em>(.*?)<\/em>/g, '*$1*')
        .replace(/<i>(.*?)<\/i>/g, '*$1*')
        // Links
        .replace(/<a[^>]*href="([^"]*)"[^>]*>(.*?)<\/a>/g, '[$2]($1)')
        // Line breaks
        .replace(/<br\s*\/?>/g, '\n')
        .replace(/<\/p>/g, '\n')
        .replace(/<\/li>/g, '\n')
        .replace(/<li>/g, '- ')
        // Remove remaining HTML tags
        .replace(/<[^>]+>/g, '')
        // Decode HTML entities
        .replace(/&lt;/g, '<')
        .replace(/&gt;/g, '>')
        .replace(/&amp;/g, '&')
        .replace(/&quot;/g, '"')
        .replace(/&#39;/g, "'")
        .replace(/&nbsp;/g, ' ')
        // Clean up whitespace
        .replace(/\n{3,}/g, '\n\n')
        .trim();
}
