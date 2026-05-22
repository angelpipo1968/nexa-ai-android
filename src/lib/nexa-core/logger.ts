// ═══════════════════════════════════════════
//  NEXA CORE — Logger & Error Monitor
//  Logging estructurado para producción
// ═══════════════════════════════════════════

type LogLevel = 'debug' | 'info' | 'warn' | 'error';

interface LogEntry {
    level: LogLevel;
    message: string;
    context?: string;
    data?: unknown;
    timestamp: string;
    requestId?: string;
}

const LOG_COLORS: Record<LogLevel, string> = {
    debug: '\x1b[36m',  // cyan
    info: '\x1b[32m',   // green
    warn: '\x1b[33m',   // yellow
    error: '\x1b[31m',  // red
};

const RESET = '\x1b[0m';

function formatLog(entry: LogEntry): string {
    const color = LOG_COLORS[entry.level];
    const prefix = entry.context ? `[${entry.context}]` : '[NEXA]';
    const reqId = entry.requestId ? ` (${entry.requestId})` : '';
    return `${color}${entry.timestamp} ${entry.level.toUpperCase()}${RESET} ${prefix}${reqId} ${entry.message}`;
}

function log(level: LogLevel, message: string, context?: string, data?: unknown): void {
    const entry: LogEntry = {
        level,
        message,
        context,
        data,
        timestamp: new Date().toISOString(),
    };

    // En producción, solo log info+
    if (process.env.NODE_ENV === 'production' && level === 'debug') return;

    const formatted = formatLog(entry);

    switch (level) {
        case 'error':
            console.error(formatted, data ? '\n' + JSON.stringify(data, null, 2) : '');
            break;
        case 'warn':
            console.warn(formatted, data ? '\n' + JSON.stringify(data, null, 2) : '');
            break;
        default:
            console.log(formatted, data ? '\n' + JSON.stringify(data, null, 2) : '');
    }
}

export const logger = {
    debug: (msg: string, ctx?: string, data?: unknown) => log('debug', msg, ctx, data),
    info: (msg: string, ctx?: string, data?: unknown) => log('info', msg, ctx, data),
    warn: (msg: string, ctx?: string, data?: unknown) => log('warn', msg, ctx, data),
    error: (msg: string, ctx?: string, data?: unknown) => log('error', msg, ctx, data),
};

/**
 * Genera un ID único para request tracking
 */
export function generateRequestId(): string {
    return `req_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
}
