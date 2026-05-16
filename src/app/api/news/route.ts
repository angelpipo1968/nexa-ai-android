import { NextRequest, NextResponse } from 'next/server';

export const dynamic = 'force-dynamic';

const corsHeaders = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
};

export async function OPTIONS() {
    return new NextResponse(null, { headers: corsHeaders });
}

export async function GET(req: NextRequest) {
    const { searchParams } = new URL(req.url);
    const topic = searchParams.get('topic') || 'general';
    const lang = searchParams.get('lang') || 'es';
    const apiKey = process.env.NEWS_API_KEY;

    try {
        if (apiKey) {
            // Implementation for NewsAPI.org
            const res = await fetch(`https://newsapi.org/v2/top-headlines?category=${topic}&language=${lang}&apiKey=${apiKey}`);
            const data = await res.json();
            if (res.ok) return NextResponse.json(data, { headers: corsHeaders });
        }

        // Fallback to RSS (Simplified logic from tools route)
        const feeds: Record<string, Record<string, string>> = {
            es: {
                general: 'https://feeds.elpais.com/mrss-s/pages/pe/site/elpais.com/portada',
                technology: 'https://feeds.elpais.com/mrss-s/pages/pe/site/elpais.com/section/tecnologia/portada',
            },
            en: {
                general: 'https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml',
            },
        };

        const feedUrl = feeds[lang]?.[topic] || feeds[lang]?.general || feeds.en.general;
        const res = await fetch(feedUrl);
        const text = await res.text();
        const articles = [...text.matchAll(/<item>[\s\S]*?<\/item>/g)].slice(0, 10).map(m => {
            const item = m[0];
            return {
                title: item.match(/<title><!\[CDATA\[(.*?)\]\]><\/title>/)?.[1] || item.match(/<title>(.*?)<\/title>/)?.[1] || '',
                link: item.match(/<link>(.*?)<\/link>/)?.[1] || '',
            };
        });

        return NextResponse.json({ articles }, { headers: corsHeaders });
    } catch (error: any) {
        return NextResponse.json({ error: error.message }, { status: 500, headers: corsHeaders });
    }
}
