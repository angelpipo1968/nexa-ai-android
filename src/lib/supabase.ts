import { createClient, SupabaseClient } from '@supabase/supabase-js';

let _client: SupabaseClient | null = null;

function isValidHttpUrl(value: string | undefined): value is string {
    if (!value) return false;

    try {
        const parsed = new URL(value);
        return parsed.protocol === 'http:' || parsed.protocol === 'https:';
    } catch {
        return false;
    }
}

export function getSupabase(): SupabaseClient {
    if (_client) return _client;
    
    const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
    const key = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
    
    if (!isValidHttpUrl(url) || !key) {
        console.warn('[NEXA] Supabase credentials not configured. Set NEXT_PUBLIC_SUPABASE_URL and NEXT_PUBLIC_SUPABASE_ANON_KEY in .env');
    }
    
    _client = createClient(
        isValidHttpUrl(url) ? url : 'https://placeholder.supabase.co',
        key || 'placeholder'
    );
    return _client;
}

export const supabase = new Proxy({} as SupabaseClient, {
    get(_, prop) {
        return (getSupabase() as unknown as Record<string, unknown>)[prop as string];
    },
});

export const isSupabaseConfigured = !!(
    isValidHttpUrl(process.env.NEXT_PUBLIC_SUPABASE_URL) && process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY
);
