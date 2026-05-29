import { NextRequest, NextResponse } from 'next/server';

export const runtime = 'nodejs';

// Lark and Feishu open API domains
const LARK_DOMAIN = 'https://open.larksuite.com';
const FEISHU_DOMAIN = 'https://open.feishu.cn';

// Helper to acquire tenant_access_token from Lark/Feishu
async function getTenantAccessToken(appId: string, appSecret: string): Promise<string | null> {
    const body = { app_id: appId, app_secret: appSecret };
    const headers = { 'Content-Type': 'application/json' };

    // Try Lark domain first
    try {
        const res = await fetch(`${LARK_DOMAIN}/open-apis/auth/v3/tenant_access_token/internal`, {
            method: 'POST',
            headers,
            body: JSON.stringify(body),
        });
        if (res.ok) {
            const data = await res.json();
            if (data.tenant_access_token) return data.tenant_access_token;
        }
    } catch (err) {
        console.warn('[LARK] Failed to fetch token from Lark suite domain, falling back to Feishu...', err);
    }

    // Try Feishu domain as fallback
    try {
        const res = await fetch(`${FEISHU_DOMAIN}/open-apis/auth/v3/tenant_access_token/internal`, {
            method: 'POST',
            headers,
            body: JSON.stringify(body),
        });
        if (res.ok) {
            const data = await res.json();
            if (data.tenant_access_token) return data.tenant_access_token;
        }
    } catch (err) {
        console.error('[LARK] Failed to fetch token from both Lark and Feishu domains:', err);
    }

    return null;
}

// Helper to reply to a user message in Lark/Feishu
async function replyToLarkMessage(token: string, messageId: string, text: string): Promise<boolean> {
    const body = {
        content: JSON.stringify({ text }),
        msg_type: 'text',
    };
    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
    };

    // Try Lark domain
    try {
        const res = await fetch(`${LARK_DOMAIN}/open-apis/im/v1/messages/${messageId}/reply`, {
            method: 'POST',
            headers,
            body: JSON.stringify(body),
        });
        if (res.ok) {
            const data = await res.json();
            if (data.code === 0) return true;
            console.warn('[LARK] Lark reply error response:', data);
        }
    } catch (err) {
        console.warn('[LARK] Lark reply failed, trying Feishu fallback...', err);
    }

    // Try Feishu domain
    try {
        const res = await fetch(`${FEISHU_DOMAIN}/open-apis/im/v1/messages/${messageId}/reply`, {
            method: 'POST',
            headers,
            body: JSON.stringify(body),
        });
        if (res.ok) {
            const data = await res.json();
            if (data.code === 0) return true;
            console.error('[LARK] Feishu reply error response:', data);
        }
    } catch (err) {
        console.error('[LARK] Reply failed on both domains:', err);
    }

    return false;
}

// Helper to fetch response from Dify Chat API
async function getDifyResponse(apiKey: string, query: string, userId: string): Promise<string> {
    try {
        const res = await fetch('https://api.dify.ai/v1/chat-messages', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${apiKey}`,
            },
            body: JSON.stringify({
                inputs: {},
                query: query,
                response_mode: 'blocking',
                user: userId,
            }),
        });

        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.message || `HTTP ${res.status}`);
        }

        const data = await res.json();
        return data.answer || 'No he podido obtener una respuesta de Dify.';
    } catch (e: any) {
        console.error('[DIFY] Error calling Dify API:', e);
        return `Error al conectar con Dify: ${e.message}`;
    }
}

// Helper to fetch response from Nexa AI chat (streaming internally)
async function getNexaResponse(origin: string, query: string): Promise<string> {
    try {
        const res = await fetch(`${origin}/api/chat`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                messages: [{ role: 'user', content: query }],
                stream: true,
            }),
        });

        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.error || `HTTP ${res.status}`);
        }

        const reader = res.body?.getReader();
        const decoder = new TextDecoder();
        let fullResponse = '';
        let buffer = '';

        if (reader) {
            while (true) {
                const { done, value } = await reader.read();
                if (done) break;
                buffer += decoder.decode(value, { stream: true });
                const parts = buffer.split('\n\n');
                buffer = parts.pop() ?? '';
                for (const part of parts) {
                    const line = part.trim();
                    if (line.startsWith('data: ')) {
                        try {
                            const parsed = JSON.parse(line.slice(6));
                            if (parsed.text) {
                                fullResponse += parsed.text;
                            }
                            if (parsed.done && parsed.fullResponse) {
                                fullResponse = parsed.fullResponse;
                            }
                        } catch {}
                    }
                }
            }
        }

        return fullResponse || 'No he podido generar una respuesta.';
    } catch (e: any) {
        console.error('[NEXA] Error calling Nexa AI API:', e);
        return `Error al conectar con el motor de Nexa AI: ${e.message}`;
    }
}

export async function POST(req: NextRequest) {
    try {
        const body = await req.json().catch(() => null);
        if (!body) {
            return NextResponse.json({ error: 'Body vacío' }, { status: 400 });
        }

        // 1. URL Verification Challenge
        if (body.type === 'url_verification') {
            console.log('[LARK] Event URL verification challenge received.');
            return NextResponse.json({ challenge: body.challenge });
        }

        // 2. Event subscription processing
        const eventType = body.header?.event_type;
        if (eventType === 'im.message.receive_v1') {
            const message = body.event?.message;
            const sender = body.event?.sender;
            
            if (!message || !sender) {
                return NextResponse.json({ error: 'Formato de evento inválido' }, { status: 400 });
            }

            const messageId = message.message_id;
            const openId = sender.sender_id?.open_id || 'unknown';
            const msgType = message.msg_type;

            // Only support text messages for now
            if (msgType !== 'text') {
                console.log(`[LARK] Ignored message type: ${msgType}`);
                return NextResponse.json({ status: 'ignored', reason: 'unsupported_message_type' });
            }

            // Extract prompt
            let userQuery = '';
            try {
                const parsedContent = JSON.parse(message.content);
                userQuery = parsedContent.text || '';
            } catch (err) {
                console.error('[LARK] Failed to parse message content:', err);
                return NextResponse.json({ error: 'Mensaje inválido' }, { status: 400 });
            }

            if (!userQuery.trim()) {
                return NextResponse.json({ status: 'ignored', reason: 'empty_query' });
            }

            console.log(`[LARK] Received message from user ${openId}: "${userQuery}"`);

            // Fetch tenant access token from environment variables
            const appId = process.env.LARK_APP_ID;
            const appSecret = process.env.LARK_APP_SECRET;

            if (!appId || !appSecret) {
                console.error('[LARK] LARK_APP_ID or LARK_APP_SECRET is not configured in .env.local');
                return NextResponse.json({ error: 'Lark integration not configured' }, { status: 500 });
            }

            // Get Lark token
            const token = await getTenantAccessToken(appId, appSecret);
            if (!token) {
                console.error('[LARK] Failed to retrieve tenant_access_token');
                return NextResponse.json({ error: 'Lark token failure' }, { status: 500 });
            }

            // Process query using selected provider
            const provider = process.env.LARK_BOT_PROVIDER || 'nexa';
            let aiResponse = '';

            if (provider === 'dify') {
                const difyKey = process.env.DIFY_API_KEY;
                if (!difyKey) {
                    aiResponse = 'Error: DIFY_API_KEY no está configurada.';
                } else {
                    console.log('[LARK] Routing message to Dify API...');
                    aiResponse = await getDifyResponse(difyKey, userQuery, openId);
                }
            } else {
                console.log('[LARK] Routing message to local Nexa AI API...');
                // Use request origin to call Nexa API locally
                const origin = req.nextUrl.origin;
                aiResponse = await getNexaResponse(origin, userQuery);
            }

            // Clean reply for voice or plain format if needed, but since it's text chat, direct markdown is fine.
            // Send reply back to Lark
            console.log(`[LARK] Sending reply to message ${messageId}...`);
            const sent = await replyToLarkMessage(token, messageId, aiResponse);
            
            if (sent) {
                console.log('[LARK] Message replied successfully.');
                return NextResponse.json({ status: 'success' });
            } else {
                console.error('[LARK] Failed to send reply to Lark.');
                return NextResponse.json({ error: 'Failed to reply to Lark' }, { status: 500 });
            }
        }

        // Return 200 for other event types to acknowledge receipt
        return NextResponse.json({ status: 'received', event: eventType });
    } catch (error: any) {
        console.error('[LARK] Error processing webhook:', error);
        return NextResponse.json({ error: error.message }, { status: 500 });
    }
}
