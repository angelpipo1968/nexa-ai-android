import { Redis } from '@upstash/redis';
import { notFound } from 'next/navigation';

// Lazy-initialize Redis to prevent crashes when REDIS_URL is not set.
let _redis: Redis | null = null;

function getRedis(): Redis | null {
    if (_redis) return _redis;
    try {
        _redis = Redis.fromEnv();
    } catch {
        return null;
    }
    return _redis;
}

export default async function PreviewPage({ params }: { params: { id: string } }) {
    const redis = getRedis();
    if (!redis) return notFound();

    const data: any = await redis.get(`preview:${params.id}`);
    
    if (!data) return notFound();

    const { code, title } = typeof data === 'string' ? JSON.parse(data) : data;

    return (
        <html>
            <head>
                <title>{title || 'Nexa Preview'}</title>
                <meta name="viewport" content="width=device-width, initial-scale=1" />
                <style>{`
                    body, html { margin: 0; padding: 0; height: 100%; overflow: hidden; background: #000; }
                    iframe { border: none; width: 100%; height: 100%; background: white; }
                `}</style>
            </head>
            <body>
                <iframe 
                    srcDoc={code} 
                    sandbox="allow-scripts allow-forms allow-popups allow-modals"
                    title="Nexa Live Preview"
                />
            </body>
        </html>
    );
}
