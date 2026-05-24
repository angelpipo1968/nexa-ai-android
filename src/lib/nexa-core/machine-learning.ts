/**
 * NEXA CORE — Motor de Aprendizaje Automático (Machine Learning)
 * 
 * Sistema de aprendizaje que permite a NEXA:
 * - Aprender de feedback del usuario (refuerzo)
 * - Detectar emociones y adaptar respuestas
 * - Construir perfil de personalización
 * - Mejorar con el tiempo basándose en interacciones
 * 
 * No requiere entrenar modelos desde cero — usa LLMs como "cerebro"
 * y Redis como "memoria a largo plazo" para almacenar patrones aprendidos.
 */

import { Redis } from '@upstash/redis';

// Lazy-initialize Redis to prevent crashes when REDIS_URL is not set.
// All functions that use redis call getRedis() and gracefully degrade if null.
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
        console.error('[NEXA ML] Failed to initialize Redis:', e);
        return null;
    }
    return _redis;
}

// ═══════════════════════════════════════
//  TYPES
// ═══════════════════════════════════════

export interface UserEmotion {
    primary: 'joy' | 'sadness' | 'anger' | 'fear' | 'surprise' | 'disgust' | 'love' | 'neutral';
    intensity: number; // 0.0 - 1.0
    secondary?: string;
    confidence: number; // 0.0 - 1.0
}

export interface LearningSignal {
    type: 'positive' | 'negative' | 'neutral';
    category: 'response_quality' | 'tool_accuracy' | 'conversation_style' | 'topic_interest' | 'emotion_match';
    value: number; // -1.0 to 1.0
    context: string;
    timestamp: string;
}

export interface UserProfile {
    communication_style: 'formal' | 'casual' | 'technical' | 'simple';
    preferred_language: string;
    interests: string[];
    expertise_level: 'beginner' | 'intermediate' | 'advanced' | 'expert';
    emotional_patterns: Record<string, number>;
    topic_preferences: Record<string, number>;
    response_preferences: {
        length: 'brief' | 'moderate' | 'detailed';
        format: 'text' | 'markdown' | 'code' | 'visual';
        tone: 'friendly' | 'professional' | 'humorous' | 'empathetic';
    };
    learning_velocity: number; // How fast user adapts
    interaction_count: number;
    last_updated: string;
}

export interface KnowledgeNode {
    entity: string;
    type: 'person' | 'place' | 'concept' | 'event' | 'preference' | 'fact';
    relations: { target: string; relation: string; strength: number }[];
    confidence: number;
    source: string;
    timestamp: string;
}

// ═══════════════════════════════════════
//  1. EMOTION ANALYSIS ENGINE
// ═══════════════════════════════════════

const EMOTION_KEYWORDS: Record<string, string[]> = {
    joy: ['feliz', 'contento', 'genial', 'increíble', 'excelente', 'maravilloso', 'wow', 'increible', 'bien', 'bueno', 'perfecto', 'gracias', 'love', 'encanta', 'me gusta', 'divertido', 'alegr', 'jaja', '😂', '🥳', '🎉', '😊', '❤️', '💪', 'great', 'awesome', 'amazing'],
    sadness: ['triste', 'deprimido', 'solo', 'solo', 'mal', 'horrible', 'terrible', 'extraño', 'extrañ', 'llorar', 'dolor', 'pena', 'nostálgic', '😢', '😭', '💔', 'lost', 'miss'],
    anger: ['enojado', 'molesto', 'furioso', 'cabrón', 'pésimo', 'basura', 'maldito', 'odio', 'frustrad', 'irritad', 'harto', '😡', '🤬', '😤', 'hate', 'terrible'],
    fear: ['miedo', 'asustado', 'preocupado', 'nervioso', 'ansiedad', 'pánico', 'terror', 'angustia', 'inquiet', '😰', '😱', '😨', 'afraid'],
    surprise: ['sorprendido', 'no sabía', 'increíble', 'no lo puedo creer', 'vaya', 'wow', 'no esperaba', '😮', '🤯', '😲'],
    love: ['te quiero', 'te amo', 'amor', 'cariño', 'hermoso', 'bello', 'adoro', 'especial', '🥰', '💕', '💗', '😘'],
    disgust: ['asqueroso', 'feo', 'horripilante', 'nauseabundo', 'repugnante', 'desagradable', '🤢', '🤮', 'ugh']
};

export function analyzeEmotion(text: string): UserEmotion {
    const lower = text.toLowerCase();
    const scores: Record<string, number> = {};
    
    for (const [emotion, keywords] of Object.entries(EMOTION_KEYWORDS)) {
        let score = 0;
        for (const keyword of keywords) {
            if (lower.includes(keyword)) {
                score += keyword.length > 3 ? 2 : 1; // Longer matches = stronger signal
            }
        }
        scores[emotion] = score;
    }
    
    const maxEmotion = Object.entries(scores).reduce((a, b) => a[1] > b[1] ? a : b, ['neutral', 0]);
    
    if (maxEmotion[1] === 0) {
        return { primary: 'neutral', intensity: 0.2, confidence: 0.8 };
    }
    
    const totalScore = Object.values(scores).reduce((a, b) => a + b, 0);
    const intensity = Math.min(maxEmotion[1] / 5, 1.0);
    const confidence = maxEmotion[1] / (totalScore || 1);
    
    // Find secondary emotion
    const sorted = Object.entries(scores)
        .filter(([e]) => e !== maxEmotion[0])
        .sort((a, b) => b[1] - a[1]);
    const secondary = sorted[0]?.[1] > 0 ? sorted[0][0] : undefined;
    
    return {
        primary: maxEmotion[0] as UserEmotion['primary'],
        intensity,
        secondary,
        confidence
    };
}

/**
 * Advanced emotion analysis using LLM for complex cases
 */
export async function analyzeEmotionAdvanced(text: string): Promise<UserEmotion> {
    const quickResult = analyzeEmotion(text);
    
    // If quick analysis is confident enough, use it
    if (quickResult.confidence > 0.6 || text.length < 20) {
        return quickResult;
    }
    
    // For ambiguous cases, use LLM for deeper analysis
    const groqKey = process.env.GROQ_API_KEY;
    if (!groqKey) return quickResult;
    
    try {
        const res = await fetch('https://api.groq.com/openai/v1/chat/completions', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${groqKey}` },
            body: JSON.stringify({
                model: 'llama-3.1-8b-instant',
                messages: [{
                    role: 'system',
                    content: 'Analyze the emotional tone of this message. Respond ONLY with JSON: {"primary":"joy|sadness|anger|fear|surprise|disgust|love|neutral","intensity":0.0-1.0,"secondary":"emotion or null","confidence":0.0-1.0}'
                }, { role: 'user', content: text }],
                response_format: { type: 'json_object' },
                max_tokens: 80
            }),
        });
        const data = await res.json();
        return JSON.parse(data.choices[0].message.content);
    } catch {
        return quickResult;
    }
}

// ═══════════════════════════════════════
//  2. REINFORCEMENT LEARNING ENGINE
// ═══════════════════════════════════════

/**
 * Record a learning signal (positive/negative feedback)
 */
export async function recordLearningSignal(
    userId: string, 
    signal: LearningSignal
): Promise<void> {
    const redis = getRedis();
    if (!redis) return;

    try {
        // Store the signal
        await redis.lpush(`learning:${userId}`, JSON.stringify(signal));
        await redis.ltrim(`learning:${userId}`, 0, 499); // Keep last 500 signals
        
        // Update aggregate scores per category
        const key = `learning_score:${userId}:${signal.category}`;
        const current = await redis.get<number>(key) || 0;
        // Exponential moving average (more recent signals matter more)
        const alpha = 0.3;
        const newScore = current * (1 - alpha) + signal.value * alpha;
        await redis.set(key, newScore);
        
        // Update topic preferences if relevant
        if (signal.category === 'topic_interest' && signal.context) {
            const topicKey = `topic_pref:${userId}:${signal.context.toLowerCase()}`;
            const topicScore = await redis.get<number>(topicKey) || 0;
            await redis.set(topicKey, topicScore * (1 - alpha) + signal.value * alpha);
        }
    } catch (e) {
        console.error('Error recording learning signal:', e);
    }
}

/**
 * Detect implicit learning signals from user behavior
 */
export function detectImplicitSignals(
    userMessage: string, 
    previousResponse: string,
    emotion: UserEmotion
): LearningSignal[] {
    const signals: LearningSignal[] = [];
    const lower = userMessage.toLowerCase();
    
    // Positive signals
    if (lower.includes('gracias') || lower.includes('perfecto') || lower.includes('excelente') || lower.includes('genial')) {
        signals.push({
            type: 'positive', category: 'response_quality', value: 0.8,
            context: 'user_expressed_gratitude', timestamp: new Date().toISOString()
        });
    }
    
    // Negative signals
    if (lower.includes('no es eso') || lower.includes('mal') || lower.includes('incorrecto') || lower.includes('otra vez') || lower.includes('inténtalo de nuevo')) {
        signals.push({
            type: 'negative', category: 'response_quality', value: -0.6,
            context: 'user_rejected_response', timestamp: new Date().toISOString()
        });
    }
    
    // Emotion-based signals
    if (emotion.primary === 'joy' || emotion.primary === 'love') {
        signals.push({
            type: 'positive', category: 'emotion_match', value: emotion.intensity * 0.5,
            context: `user_emotion_${emotion.primary}`, timestamp: new Date().toISOString()
        });
    } else if (emotion.primary === 'anger' || emotion.primary === 'sadness') {
        signals.push({
            type: 'negative', category: 'emotion_match', value: -emotion.intensity * 0.3,
            context: `user_emotion_${emotion.primary}`, timestamp: new Date().toISOString()
        });
    }
    
    // Topic interest signal (user asking follow-up = interested)
    if (lower.includes('más sobre') || lower.includes('cuéntame más') || lower.includes('dime más') || lower.includes('explícame')) {
        signals.push({
            type: 'positive', category: 'topic_interest', value: 0.7,
            context: 'follow_up_question', timestamp: new Date().toISOString()
        });
    }
    
    // Conversation style preference
    if (lower.includes('más corto') || lower.includes('resumen') || lower.includes('breve')) {
        signals.push({
            type: 'negative', category: 'conversation_style', value: -0.4,
            context: 'prefers_shorter_responses', timestamp: new Date().toISOString()
        });
    }
    if (lower.includes('más detalle') || lower.includes('explica mejor') || lower.includes('más información')) {
        signals.push({
            type: 'positive', category: 'conversation_style', value: 0.4,
            context: 'prefers_detailed_responses', timestamp: new Date().toISOString()
        });
    }
    
    return signals;
}

/**
 * Get learning insights for a user
 */
export async function getLearningInsights(userId: string): Promise<{
    scores: Record<string, number>;
    topTopics: { topic: string; score: number }[];
    totalSignals: number;
    recommendation: string;
}> {
    const redis = getRedis();
    if (!redis) {
        return { scores: {}, topTopics: [], totalSignals: 0, recommendation: '' };
    }
    
    try {
        const categories = ['response_quality', 'tool_accuracy', 'conversation_style', 'topic_interest', 'emotion_match'];
        const scores: Record<string, number> = {};
        
        for (const cat of categories) {
            scores[cat] = await redis.get<number>(`learning_score:${userId}:${cat}`) || 0;
        }
        
        // Get topic preferences
        const topicKeys = await redis.keys(`topic_pref:${userId}:*`);
        const topTopics: { topic: string; score: number }[] = [];
        
        for (const key of topicKeys.slice(0, 20)) {
            const topic = key.replace(`topic_pref:${userId}:`, '');
            const score = await redis.get<number>(key) || 0;
            topTopics.push({ topic, score });
        }
        topTopics.sort((a, b) => b.score - a.score);
        
        // Count total signals
        const totalSignals = await redis.llen(`learning:${userId}`) || 0;
        
        // Generate recommendation based on scores
        let recommendation = '';
        if (scores.conversation_style < -0.2) {
            recommendation = 'El usuario prefiere respuestas más breves. Adapta la longitud.';
        } else if (scores.conversation_style > 0.2) {
            recommendation = 'El usuario prefiere respuestas detalladas. Expande las explicaciones.';
        }
        if (scores.emotion_match < -0.1) {
            recommendation += ' Mejora la detección emocional y responde con más empatía.';
        }
        
        return { scores, topTopics, totalSignals: Number(totalSignals), recommendation };
    } catch {
        return { scores: {}, topTopics: [], totalSignals: 0, recommendation: '' };
    }
}

// ═══════════════════════════════════════
//  3. USER PROFILE & PERSONALIZATION
// ═══════════════════════════════════════

const DEFAULT_PROFILE: UserProfile = {
    communication_style: 'casual',
    preferred_language: 'es',
    interests: [],
    expertise_level: 'intermediate',
    emotional_patterns: {},
    topic_preferences: {},
    response_preferences: {
        length: 'moderate',
        format: 'markdown',
        tone: 'friendly'
    },
    learning_velocity: 0.5,
    interaction_count: 0,
    last_updated: new Date().toISOString()
};

/**
 * Get or create user profile
 */
export async function getUserProfile(userId: string): Promise<UserProfile> {
    const redis = getRedis();
    if (!redis) return DEFAULT_PROFILE;
    
    try {
        const profile = await redis.get<UserProfile>(`profile:${userId}`);
        return profile || DEFAULT_PROFILE;
    } catch {
        return DEFAULT_PROFILE;
    }
}

/**
 * Update user profile based on interaction
 */
export async function updateUserProfile(
    userId: string, 
    userMessage: string, 
    emotion: UserEmotion,
    detectedTopics: string[]
): Promise<UserProfile> {
    const profile = await getUserProfile(userId);
    
    // Increment interaction count
    profile.interaction_count++;
    profile.last_updated = new Date().toISOString();
    
    // Update emotional patterns
    if (!profile.emotional_patterns[emotion.primary]) {
        profile.emotional_patterns[emotion.primary] = 0;
    }
    profile.emotional_patterns[emotion.primary] = 
        profile.emotional_patterns[emotion.primary] * 0.9 + emotion.intensity * 0.1;
    
    // Update topic preferences
    for (const topic of detectedTopics) {
        const key = topic.toLowerCase();
        if (!profile.topic_preferences[key]) {
            profile.topic_preferences[key] = 0.5;
        }
        profile.topic_preferences[key] = Math.min(profile.topic_preferences[key] + 0.1, 1.0);
    }
    
    // Update interests (keep top 15)
    for (const topic of detectedTopics) {
        if (!profile.interests.includes(topic)) {
            profile.interests.push(topic);
        }
    }
    // Sort by preference score and keep top 15
    profile.interests.sort((a, b) => 
        (profile.topic_preferences[b.toLowerCase()] || 0) - (profile.topic_preferences[a.toLowerCase()] || 0)
    );
    profile.interests = profile.interests.slice(0, 15);
    
    // Detect communication style changes
    const lower = userMessage.toLowerCase();
    if (lower.includes('tú') || lower.includes('qué') || lower.includes('cómo') || lower.includes('informalmente')) {
        profile.communication_style = 'casual';
    } else if (lower.includes('usted') || lower.includes('formalmente') || lower.includes('estimado')) {
        profile.communication_style = 'formal';
    }
    
    // Detect expertise level from vocabulary
    const technicalTerms = ['API', 'SDK', 'algoritmo', 'neural', 'framework', 'deploy', 'optimizar', 'arquitectura', 'backend', 'frontend', 'database', 'regex'];
    const technicalCount = technicalTerms.filter(t => lower.includes(t.toLowerCase())).length;
    if (technicalCount >= 3) {
        profile.expertise_level = 'expert';
    } else if (technicalCount >= 1) {
        profile.expertise_level = 'advanced';
    }
    
    // Save profile
    const redis = getRedis();
    if (redis) {
        try {
            await redis.set(`profile:${userId}`, JSON.stringify(profile));
        } catch {}
    }
    
    return profile;
}

/**
 * Generate personalization context for the LLM prompt
 */
export function generatePersonalizationContext(profile: UserProfile, emotion: UserEmotion): string {
    const parts: string[] = [];
    
    // Communication style
    parts.push(`Estilo de comunicación: ${profile.communication_style}`);
    
    // Expertise level
    parts.push(`Nivel técnico: ${profile.expertise_level}`);
    
    // Current emotional state
    if (emotion.primary !== 'neutral') {
        const emotionMap: Record<string, string> = {
            joy: 'alegría', sadness: 'tristeza', anger: 'enojo', fear: 'miedo',
            surprise: 'sorpresa', love: 'cariño', disgust: 'disgusto'
        };
        parts.push(`Estado emocional actual: ${emotionMap[emotion.primary] || emotion.primary} (intensidad: ${Math.round(emotion.intensity * 100)}%)`);
        
        // Emotion-specific instructions
        if (emotion.primary === 'sadness') {
            parts.push('INSTRUCCIÓN: Responde con empatía y calidez. Sé reconfortante.');
        } else if (emotion.primary === 'anger') {
            parts.push('INSTRUCCIÓN: Mantén la calma y sé comprensivo. No confrontes.');
        } else if (emotion.primary === 'joy') {
            parts.push('INSTRUCCIÓN: Comparte el entusiasmo. Sé energético y positivo.');
        } else if (emotion.primary === 'fear') {
            parts.push('INSTRUCCIÓN: Sé tranquilizador y ofrece seguridad.');
        }
    }
    
    // Top interests
    const topInterests = Object.entries(profile.topic_preferences)
        .sort((a, b) => b[1] - a[1])
        .slice(0, 5)
        .map(([topic]) => topic);
    if (topInterests.length > 0) {
        parts.push(`Temas de interés: ${topInterests.join(', ')}`);
    }
    
    // Response preferences
    if (profile.response_preferences.length === 'brief') {
        parts.push('Prefiere respuestas breves y concisas.');
    } else if (profile.response_preferences.length === 'detailed') {
        parts.push('Prefiere respuestas detalladas con explicaciones completas.');
    }
    
    // Dominant emotional pattern
    const dominantEmotion = Object.entries(profile.emotional_patterns)
        .sort((a, b) => b[1] - a[1])[0];
    if (dominantEmotion && dominantEmotion[0] !== 'neutral') {
        parts.push(`Patrón emocional: tiende a ${dominantEmotion[0]} (${Math.round(dominantEmotion[1] * 100)}%)`);
    }
    
    return parts.join('\n');
}

// ═══════════════════════════════════════
//  4. KNOWLEDGE GRAPH
// ═══════════════════════════════════════

/**
 * Add a knowledge node to the user's knowledge graph
 */
export async function addKnowledgeNode(
    userId: string,
    node: KnowledgeNode
): Promise<void> {
    const redis = getRedis();
    if (!redis) return;
    
    try {
        const key = `kg:${userId}:${node.entity.toLowerCase().replace(/\s+/g, '_')}`;
        const existing = await redis.get<KnowledgeNode>(key);
        
        if (existing) {
            // Merge relations
            for (const newRel of node.relations) {
                const existingRel = existing.relations.find(r => r.target === newRel.target && r.relation === newRel.relation);
                if (existingRel) {
                    existingRel.strength = Math.min(existingRel.strength + 0.2, 1.0);
                } else {
                    existing.relations.push(newRel);
                }
            }
            existing.confidence = Math.min(existing.confidence + 0.1, 1.0);
            existing.timestamp = new Date().toISOString();
            await redis.set(key, JSON.stringify(existing));
        } else {
            await redis.set(key, JSON.stringify(node));
        }
    } catch (e) {
        console.error('Error adding knowledge node:', e);
    }
}

/**
 * Extract knowledge from user message using LLM
 */
export async function extractKnowledge(userId: string, userMessage: string): Promise<void> {
    const groqKey = process.env.GROQ_API_KEY;
    if (!groqKey) return;
    
    try {
        const res = await fetch('https://api.groq.com/openai/v1/chat/completions', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${groqKey}` },
            body: JSON.stringify({
                model: 'llama-3.1-8b-instant',
                messages: [{
                    role: 'system',
                    content: `Extract entities and relationships from this message as JSON array. Each item: {"entity": "name", "type": "person|place|concept|event|preference|fact", "relations": [{"target": "other entity", "relation": "relationship type", "strength": 0.5}]}. If nothing relevant, return [].`
                }, { role: 'user', content: userMessage }],
                response_format: { type: 'json_object' },
                max_tokens: 200
            }),
        });
        const data = await res.json();
        const nodes = JSON.parse(data.choices[0].message.content);
        
        if (Array.isArray(nodes)) {
            for (const node of nodes) {
                if (node.entity) {
                    await addKnowledgeNode(userId, {
                        entity: node.entity,
                        type: node.type || 'concept',
                        relations: node.relations || [],
                        confidence: 0.6,
                        source: 'user_message',
                        timestamp: new Date().toISOString()
                    });
                }
            }
        }
    } catch {
        // Silent fail - knowledge extraction is non-critical
    }
}

/**
 * Get related knowledge for context
 */
export async function getRelatedKnowledge(userId: string, topic: string): Promise<string> {
    const redis = getRedis();
    if (!redis) return '';
    
    try {
        const key = `kg:${userId}:${topic.toLowerCase().replace(/\s+/g, '_')}`;
        const node = await redis.get<KnowledgeNode>(key);
        
        if (!node) return '';
        
        let context = `Conocimiento sobre ${node.entity}: `;
        for (const rel of node.relations.slice(0, 5)) {
            context += `${rel.relation} ${rel.target} (confianza: ${Math.round(rel.strength * 100)}%), `;
        }
        return context.replace(/,\s*$/, '');
    } catch {
        return '';
    }
}

// ═══════════════════════════════════════
//  5. ADVANCED NLP PIPELINE
// ═══════════════════════════════════════

export interface NLPResult {
    intent: string;
    entities: { name: string; type: string; value: string }[];
    topics: string[];
    sentiment: number; // -1.0 to 1.0
    urgency: 'low' | 'medium' | 'high';
    complexity: 'simple' | 'moderate' | 'complex';
    language: string;
    isQuestion: boolean;
    isCommand: boolean;
    needsTool: boolean;
    suggestedTools: string[];
}

/**
 * Advanced NLP analysis of user message
 */
export async function analyzeMessage(userMessage: string): Promise<NLPResult> {
    const lower = userMessage.toLowerCase();
    
    // Quick local analysis first (no LLM needed)
    const isQuestion = lower.includes('?') || lower.startsWith('qué') || lower.startsWith('quién') || 
                       lower.startsWith('cómo') || lower.startsWith('cuándo') || lower.startsWith('dónde') ||
                       lower.startsWith('por qué') || lower.startsWith('cuánto');
    
    const isCommand = lower.startsWith('crea') || lower.startsWith('genera') || lower.startsWith('haz') ||
                      lower.startsWith('busca') || lower.startsWith('dibuja') || lower.startsWith('escribe') ||
                      lower.startsWith('traduce') || lower.startsWith('repara');
    
    // Topic detection
    const topicMap: Record<string, string[]> = {
        'technology': ['código', 'programa', 'app', 'software', 'api', 'bug', 'desarrollo', 'computadora'],
        'travel': ['vuelo', 'viaje', 'avión', 'hotel', 'pasaje', 'aerolínea'],
        'finance': ['precio', 'dinero', 'dólar', 'bitcoin', 'bolsa', 'cripto', 'inversión'],
        'health': ['salud', 'ejercicio', 'dieta', 'médico', 'enfermedad', 'bienestar'],
        'entertainment': ['película', 'música', 'serie', 'juego', 'canción', 'libro'],
        'science': ['ciencia', 'investigación', 'espacio', 'nasa', 'física', 'química'],
        'food': ['receta', 'cocina', 'comida', 'restaurante', 'ingredientes'],
        'sports': ['fútbol', 'deporte', 'equipo', 'partido', 'liga'],
        'weather': ['clima', 'tiempo', 'lluvia', 'temperatura', 'pronóstico'],
        'news': ['noticias', 'actualidad', 'evento', 'sucedido']
    };
    
    const detectedTopics: string[] = [];
    for (const [topic, keywords] of Object.entries(topicMap)) {
        if (keywords.some(kw => lower.includes(kw))) {
            detectedTopics.push(topic);
        }
    }
    
    // Tool detection
    const toolMap: Record<string, string[]> = {
        'flights': ['vuelo', 'avión', 'pasaje', 'aerolínea', 'boleto'],
        'weather': ['clima', 'tiempo', 'temperatura', 'lluvia'],
        'search': ['busca', 'encuentra', 'qué es', 'quién es'],
        'translate': ['traduce', 'traducción', 'en inglés', 'en español'],
        'exchange': ['dólar', 'euro', 'moneda', 'cambio', 'cuánto vale'],
        'news': ['noticias', 'actualidad', 'últimas'],
        'image': ['dibuja', 'genera imagen', 'crea imagen', 'foto'],
        'code': ['código', 'programa', 'script', 'función'],
        'maps': ['mapa', 'ubicación', 'dónde queda', 'dirección']
    };
    
    const suggestedTools: string[] = [];
    let needsTool = false;
    for (const [tool, keywords] of Object.entries(toolMap)) {
        if (keywords.some(kw => lower.includes(kw))) {
            suggestedTools.push(tool);
            needsTool = true;
        }
    }
    
    // Urgency detection
    let urgency: 'low' | 'medium' | 'high' = 'low';
    if (lower.includes('urgente') || lower.includes('ya') || lower.includes('rápido') || lower.includes('ayuda') || lower.includes('emergencia')) {
        urgency = 'high';
    } else if (lower.includes('cuando puedas') || lower.includes('necesito') || lower.includes('puedes')) {
        urgency = 'medium';
    }
    
    // Complexity detection
    const wordCount = userMessage.split(/\s+/).length;
    const complexity: 'simple' | 'moderate' | 'complex' = 
        wordCount < 10 ? 'simple' : wordCount < 30 ? 'moderate' : 'complex';
    
    // Sentiment from emotion keywords
    const positiveWords = ['bien', 'bueno', 'genial', 'feliz', 'gracias', 'excelente', 'perfecto', 'love'];
    const negativeWords = ['mal', 'malo', 'horrible', 'odio', 'error', 'problema', 'no funciona', 'fracaso'];
    const posCount = positiveWords.filter(w => lower.includes(w)).length;
    const negCount = negativeWords.filter(w => lower.includes(w)).length;
    const sentiment = Math.max(-1, Math.min(1, (posCount - negCount) / 3));
    
    // Language detection (simple)
    const language = lower.match(/[áéíóúñü]/) ? 'es' : 'en';
    
    // Intent classification (simple rule-based, enhanced by Groq later)
    let intent = 'general_conversation';
    if (isQuestion) intent = 'question';
    if (isCommand) intent = 'command';
    if (needsTool) intent = 'tool_request';
    if (lower.includes('hola') || lower.includes('buenos días') || lower.includes('buenas')) intent = 'greeting';
    if (lower.includes('chiste') || lower.includes('broma') || lower.includes('divertido')) intent = 'entertainment';
    
    return {
        intent,
        entities: [], // Will be filled by LLM if needed
        topics: detectedTopics,
        sentiment,
        urgency,
        complexity,
        language,
        isQuestion,
        isCommand,
        needsTool,
        suggestedTools
    };
}

/**
 * Full NLP pipeline with LLM enhancement for complex queries
 */
export async function analyzeMessageAdvanced(userMessage: string): Promise<NLPResult> {
    const quickResult = await analyzeMessage(userMessage);
    
    // For simple cases, quick analysis is enough
    if (quickResult.complexity === 'simple' && !quickResult.needsTool) {
        return quickResult;
    }
    
    // Enhance with LLM for complex queries
    const groqKey = process.env.GROQ_API_KEY;
    if (!groqKey) return quickResult;
    
    try {
        const res = await fetch('https://api.groq.com/openai/v1/chat/completions', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${groqKey}` },
            body: JSON.stringify({
                model: 'llama-3.1-8b-instant',
                messages: [{
                    role: 'system',
                    content: `Analyze this message and return JSON: {"entities":[{"name":"value","type":"person|place|organization|date|product|concept","value":"extracted value"}],"intent":"greeting|question|command|tool_request|emotion_expression|general_conversation|entertainment","topics":["topic1"],"complexity":"simple|moderate|complex"}. Only JSON.`
                }, { role: 'user', content: userMessage }],
                response_format: { type: 'json_object' },
                max_tokens: 200
            }),
        });
        const data = await res.json();
        const enhanced = JSON.parse(data.choices[0].message.content);
        
        return {
            ...quickResult,
            entities: enhanced.entities || quickResult.entities,
            intent: enhanced.intent || quickResult.intent,
            topics: [...new Set([...quickResult.topics, ...(enhanced.topics || [])])],
            complexity: enhanced.complexity || quickResult.complexity
        };
    } catch {
        return quickResult;
    }
}

// ═══════════════════════════════════════
//  6. CONVERSATION INTELLIGENCE
// ═══════════════════════════════════════

export interface ConversationContext {
    turnCount: number;
    topicsDiscussed: string[];
    userEngagement: number; // 0.0 - 1.0
    conversationFlow: 'exploration' | 'deep_dive' | 'task_oriented' | 'casual';
    lastTopics: string[];
    unresolvedQuestions: string[];
    userSatisfaction: number; // -1.0 to 1.0
}

/**
 * Analyze conversation context from message history
 */
export function analyzeConversationContext(messages: { role: string; content: string }[]): ConversationContext {
    const userMessages = messages.filter(m => m.role === 'user');
    const turnCount = userMessages.length;
    
    // Track topics across conversation
    const allTopics = new Set<string>();
    const lastTopics: string[] = [];
    let positiveSignals = 0;
    let negativeSignals = 0;
    
    for (const msg of userMessages.slice(-10)) {
        const lower = msg.content.toLowerCase();
        const quickNlp = analyzeMessageSync(lower);
        quickNlp.topics.forEach(t => {
            allTopics.add(t);
            lastTopics.push(t);
        });
        
        // Count satisfaction signals
        if (lower.includes('gracias') || lower.includes('bien') || lower.includes('genial')) positiveSignals++;
        if (lower.includes('mal') || lower.includes('no es eso') || lower.includes('otra vez')) negativeSignals++;
    }
    
    // Determine conversation flow
    let conversationFlow: ConversationContext['conversationFlow'] = 'casual';
    if (allTopics.size > 3) conversationFlow = 'exploration';
    else if (lastTopics.filter(t => t === lastTopics[0]).length > 2) conversationFlow = 'deep_dive';
    if (userMessages.some(m => m.content.toLowerCase().match(/busca|crea|genera|traduce|haz/))) conversationFlow = 'task_oriented';
    
    // Engagement based on message length and frequency
    const avgLength = userMessages.reduce((sum, m) => sum + m.content.length, 0) / (turnCount || 1);
    const userEngagement = Math.min(avgLength / 100, 1.0);
    
    // Satisfaction
    const userSatisfaction = turnCount > 0 ? (positiveSignals - negativeSignals) / turnCount : 0;
    
    return {
        turnCount,
        topicsDiscussed: Array.from(allTopics),
        userEngagement,
        conversationFlow,
        lastTopics: lastTopics.slice(-5),
        unresolvedQuestions: [],
        userSatisfaction
    };
}

// Synchronous version of analyzeMessage for conversation analysis
function analyzeMessageSync(lower: string): { topics: string[] } {
    const topicMap: Record<string, string[]> = {
        'technology': ['código', 'programa', 'app', 'software', 'api'],
        'travel': ['vuelo', 'viaje', 'avión', 'hotel', 'pasaje'],
        'finance': ['precio', 'dinero', 'dólar', 'bitcoin', 'bolsa'],
        'entertainment': ['película', 'música', 'serie', 'juego'],
        'weather': ['clima', 'tiempo', 'temperatura'],
        'news': ['noticias', 'actualidad'],
    };
    const topics: string[] = [];
    for (const [topic, keywords] of Object.entries(topicMap)) {
        if (keywords.some(kw => lower.includes(kw))) topics.push(topic);
    }
    return { topics };
}
