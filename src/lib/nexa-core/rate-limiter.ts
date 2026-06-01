// ═══════════════════════════════════════════
//  NEXA CORE — Unified Rate Limiter
//  Pluggable storage backend (Memory / Redis)
//  Single source of truth for all rate limiting.
// ═══════════════════════════════════════════

import type { RateLimitResult } from '@/lib/shared-types';
export type { RateLimitResult };

// ── Storage Backend Interface ──────────────

export interface RateLimitStore {
    get(key: string): Promise<{ count: number; resetAt: number } | null>;
    set(key: string, value: { count: number; resetAt: number }, ttlMs: number): Promise<void>;
    increment(key: string): Promise<number>;
    cleanup(): Promise<void>;
}

// ── In-Memory Store (dev / single-instance) ─

export class MemoryStore implements RateLimitStore {
    private store = new Map<string, { count: number; resetAt: number }>();
    private lastCleanup = Date.now();

    // Lazy cleanup: runs during get() calls, no setInterval needed (serverless-safe)

    async get(key: string): Promise<{ count: number; resetAt: number } | null> {
        // Lazy cleanup every 5 minutes
        if (Date.now() - this.lastCleanup > 5 * 60 * 1000) {
            this.lastCleanup = Date.now();
            this.cleanupSync();
        }

        const entry = this.store.get(key);
        if (!entry || Date.now() > entry.resetAt) {
            if (entry) this.store.delete(key);
            return null;
        }
        return entry;
    }

    async set(key: string, value: { count: number; resetAt: number }, _ttlMs: number): Promise<void> {
        this.store.set(key, value);
    }

    async increment(key: string): Promise<number> {
        const entry = this.store.get(key);
        if (!entry) return 0;
        entry.count++;
        return entry.count;
    }

    async cleanup(): Promise<void> {
        this.cleanupSync();
    }

    private cleanupSync(): void {
        const now = Date.now();
        for (const [key, entry] of this.store) {
            if (now > entry.resetAt) this.store.delete(key);
        }
    }
}

// ── Redis Store (production / multi-instance) ─

export interface RedisLike {
    get(key: string): Promise<string | null>;
    set(key: string, value: string, mode?: string, ttl?: number): Promise<string | null>;
    incr(key: string): Promise<number>;
    expire(key: string, seconds: number): Promise<number>;
    del(key: string): Promise<number>;
    on(event: string, cb: (...args: unknown[]) => void): void;
}

export class RedisStore implements RateLimitStore {
    private redis: RedisLike;

    constructor(redis: RedisLike) {
        this.redis = redis;
    }

    async get(key: string): Promise<{ count: number; resetAt: number } | null> {
        const data = await this.redis.get(key);
        if (!data) return null;
        try {
            return JSON.parse(data);
        } catch {
            return null;
        }
    }

    async set(key: string, value: { count: number; resetAt: number }, ttlMs: number): Promise<void> {
        const ttlSeconds = Math.ceil(ttlMs / 1000);
        await this.redis.set(key, JSON.stringify(value), 'EX', ttlSeconds);
    }

    async increment(key: string): Promise<number> {
        return await this.redis.incr(key);
    }

    async cleanup(): Promise<void> {
        // Redis handles TTL natively; no manual cleanup needed
    }
}

// ── Rate Limiter ───────────────────────────

export interface RateLimitConfig {
    /** Maximum requests per window */
    maxRequests: number;
    /** Window duration in milliseconds */
    windowMs: number;
}

export interface RateLimiterOptions {
    store?: RateLimitStore;
    /** Predefined limit configs by name */
    presets?: Record<string, RateLimitConfig>;
}

const DEFAULT_PRESETS: Record<string, RateLimitConfig> = {
    chat: { maxRequests: 30, windowMs: 60_000 },
    vision: { maxRequests: 10, windowMs: 60_000 },
    generate: { maxRequests: 5, windowMs: 60_000 },
    general: { maxRequests: 60, windowMs: 60_000 },
};

export class RateLimiter {
    private store: RateLimitStore;
    private presets: Record<string, RateLimitConfig>;

    constructor(options: RateLimiterOptions = {}) {
        this.store = options.store ?? new MemoryStore();
        this.presets = { ...DEFAULT_PRESETS, ...options.presets };
    }

    /**
     * Check rate limit for an identifier (IP, user ID, etc.)
     */
    async check(
        identifier: string,
        config: RateLimitConfig = this.presets.general!
    ): Promise<RateLimitResult> {
        const now = Date.now();
        const key = `ratelimit:${identifier}`;
        const entry = await this.store.get(key);

        if (!entry || now > entry.resetAt) {
            // New window
            await this.store.set(key, { count: 1, resetAt: now + config.windowMs }, config.windowMs);
            return {
                allowed: true,
                remaining: config.maxRequests - 1,
                resetAt: now + config.windowMs,
            };
        }

        if (entry.count >= config.maxRequests) {
            // Limit exceeded
            return {
                allowed: false,
                remaining: 0,
                resetAt: entry.resetAt,
                retryAfterMs: entry.resetAt - now,
            };
        }

        // Increment counter
        await this.store.increment(key);
        return {
            allowed: true,
            remaining: config.maxRequests - entry.count - 1,
            resetAt: entry.resetAt,
        };
    }

    /**
     * Check using a named preset
     */
    async checkPreset(identifier: string, presetName: string): Promise<RateLimitResult> {
        const config = this.presets[presetName];
        if (!config) {
            throw new Error(`Unknown rate limit preset: ${presetName}`);
        }
        return this.check(identifier, config);
    }

    /**
     * Get a predefined config by name
     */
    getPreset(name: string): RateLimitConfig | undefined {
        return this.presets[name];
    }
}

// ── Factory Function ───────────────────────

/**
 * Create a rate limiter with the given configuration.
 * Defaults to MemoryStore if no store is provided.
 */
export function createRateLimiter(options: RateLimiterOptions = {}): RateLimiter {
    return new RateLimiter(options);
}

// ── Convenience async checkRateLimit() ─────
// Singleton limiter used by checkRateLimit() for quick one-liner usage in API routes.

const defaultLimiter = new RateLimiter();

/**
 * Async rate limit check using the default in-memory limiter.
 * Drop-in replacement for the legacy sync version — just add `await`.
 */
export async function checkRateLimit(
    identifier: string,
    config: RateLimitConfig = DEFAULT_PRESETS.general!
): Promise<RateLimitResult> {
    return defaultLimiter.check(identifier, config);
}

// ── Utilities ──────────────────────────────

/**
 * Extract identifier from a Request (IP or user ID)
 */
export function getIdentifier(request: Request): string {
    const forwarded = request.headers.get('x-forwarded-for');
    const ip = forwarded?.split(',')[0]?.trim() ||
               request.headers.get('x-real-ip') ||
               'unknown';
    return ip;
}

/**
 * Preset rate limit configurations
 */
export const RATE_LIMITS = DEFAULT_PRESETS;
