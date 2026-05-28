export async function register() {
    if (process.env.NEXT_RUNTIME === 'nodejs') {
        // Safe import for Sentry or other telemetry
        try {
            await import('../sentry.server.config');
        } catch (e) {
            console.warn('Instrumentation: Sentry server config not found or failed to load');
        }
    }
}
