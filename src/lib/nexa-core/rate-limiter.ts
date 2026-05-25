// ═══════════════════════════════════════════
//  NEXA CORE — Rate Limiter (Legacy)
//  Prefer `@/lib/rate-limiter` for new code.
// ═══════════════════════════════════════════

export type { RateLimitResult } from '../rate-limiter';
export { getIdentifier, RATE_LIMITS } from '../rate-limiter';
export { RateLimiter, createRateLimiter } from '../rate-limiter';
import type { RateLimitResult, RateLimitConfig } from '../rate-limiter';
import { RATE_LIMITS } from '../rate-limiter';

// ── Legacy sync in-memory store ────────────
// Kept for backward compatibility with existing API routes.
// New code should use createRateLimiter() from '@/lib/rate-limiter'.

interface RateLimitEntry {
    count: number;
    resetAt: number;
}

const store = new Map<string, RateLimitEntry>();

// Cleanup is handled during check calls for efficiency in serverless environments

/**
 * Synchronous rate limit check (legacy).
 * @deprecated Use `createRateLimiter().check()` for new code.
 */
export function checkRateLimit(
    identifier: string,
    config: RateLimitConfig = RATE_LIMITS.general
): RateLimitResult {
    const now = Date.now();
    const key = `ratelimit:${identifier}`;
    const entry = store.get(key);

    if (!entry || now > entry.resetAt) {
        store.set(key, { count: 1, resetAt: now + config.windowMs });
        return {
            allowed: true,
            remaining: config.maxRequests - 1,
            resetAt: now + config.windowMs,
        };
    }

    if (entry.count >= config.maxRequests) {
        return {
            allowed: false,
            remaining: 0,
            resetAt: entry.resetAt,
            retryAfterMs: entry.resetAt - now,
        };
    }

    entry.count++;
    return {
        allowed: true,
        remaining: config.maxRequests - entry.count,
        resetAt: entry.resetAt,
    };
}
