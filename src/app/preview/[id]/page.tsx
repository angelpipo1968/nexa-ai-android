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

export default async function PreviewPage({ params }: { params: Promise<{ id: string }> }) {
    const redis = getRedis();
    if (!redis) return notFound();

    const { id } = await params;
    const data: any = await redis.get(`preview:${id}`);
    
    if (!data) return notFound();

    const { code, title } = typeof data === 'string' ? JSON.parse(data) : data;

    return (
        <div style={{ margin: 0, padding: 0, height: '100vh', overflow: 'hidden', background: '#000' }}>
            <title>{title || 'Nexa Preview'}</title>
            <iframe 
                srcDoc={code} 
                sandbox="allow-scripts allow-forms allow-popups allow-modals"
                title="Nexa Live Preview"
                style={{ border: 'none', width: '100%', height: '100%', background: 'white' }}
            />
        </div>
    );
}
