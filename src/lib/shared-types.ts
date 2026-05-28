// ═══════════════════════════════════════════
//  Shared Types — Unified across the monorepo
//  Consolidates duplicate definitions from
//  tools, models, SDK, search-service, core
// ═══════════════════════════════════════════

export interface SearchResult {
    title: string;
    url: string;
    snippet: string;
    content?: string;
    score?: number;
    source?: string;
    publishedDate?: string;
}

export interface ChatMessage {
    id?: string;
    role: 'user' | 'assistant' | 'system' | 'model';
    content?: string;
    parts?: string | { text: string }[];
    timestamp?: Date;
    model?: string;
    tokens?: number;
    toolCalls?: ToolCall[];
    metadata?: Record<string, unknown>;
}

export interface ToolCall {
    id: string;
    name: string;
    arguments: Record<string, unknown>;
    result?: unknown;
}

export interface ToolResult {
    success: boolean;
    output?: string;
    error?: string;
    data?: unknown;
    metadata?: Record<string, unknown>;
}

export interface Model {
    id: string;
    name: string;
    provider: string;
    capabilities: {
        contextLength: number;
        streaming: boolean;
        functionCalling: boolean;
        vision?: boolean;
    };
    recommendationReason?: string;
}

export interface ModelRequest {
    userId: string;
    message: string;
    context?: ChatMessage[];
    images?: string[];
    requirements?: Record<string, unknown>;
    budget?: number;
    priority?: 'balanced' | 'speed' | 'quality' | 'cost';
}

export interface ModelResponse {
    text: string;
    modelId?: string;
    latency: number;
    usage?: {
        promptTokens: number;
        completionTokens: number;
        totalTokens: number;
    };
    cost: number;
}

export interface StreamChunk {
    text: string;
    done: boolean;
    usage?: {
        promptTokens: number;
        completionTokens: number;
        totalTokens: number;
    };
}

export interface ModelProvider {
    id: string;
    name?: string;
    baseUrl?: string;
    apiKey?: string;
    getModels(): Model[];
    execute(request: ModelRequest): Promise<ModelResponse>;
    streamExecute(request: ModelRequest): AsyncIterable<StreamChunk>;
    isAvailable?(): Promise<boolean>;
}

export interface NexaConfig {
    apiUrl?: string;
    apiKey?: string;
    baseURL?: string;
    model?: string;
    temperature?: number;
    maxTokens?: number;
    stream?: boolean;
    timeout?: number;
    maxRetries?: number;
    security?: Record<string, unknown>;
    tools?: Record<string, unknown>;
    models?: Record<string, unknown>;
    memory?: Record<string, unknown>;
}

export interface RateLimitResult {
    allowed: boolean;
    remaining: number;
    resetAt: number;
    reset?: number;
    limit?: number;
    retryAfter?: number;
    retryAfterMs?: number;
    algorithm?: string;
}
