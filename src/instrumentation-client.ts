import * as Sentry from '@sentry/nextjs';

Sentry.init({
    dsn: 'https://a2e614d86ac3626b2304673161519668@o4510709669101568.ingest.us.sentry.io/4511359774031872',
    tracesSampleRate: process.env.NODE_ENV === 'production' ? 0.1 : 1.0,
    debug: true,
    environment: process.env.NODE_ENV || 'development',
});
