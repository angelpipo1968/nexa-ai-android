'use client';
import React, { useState, useEffect, useRef, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { getSupabase } from '@/lib/supabase';
import {
    FileText, Image as ImageIcon, Film, Music, Camera,
    Plus, Mic, Menu, X,
    Volume2, VolumeX,
    Zap, Loader2, ArrowUp,
    MoreVertical, Moon, Sun,
    Copy, ThumbsUp, ThumbsDown, RotateCcw, Edit,
    FolderInput, File,
    Sparkles, Pin, ChevronDown, Palette, Search, Code2
} from 'lucide-react';
import { SettingsPanel } from './SettingsPanel';

// ═══════════════════════════════════════════
//  THEME PRESETS — Multiple color schemes
// ═══════════════════════════════════════════

const THEME_PRESETS = {
    emerald: { accent: '#00e5a0', glow: 'rgba(0,229,160,0.3)', name: 'Esmeralda', emoji: '💚' },
    ocean: { accent: '#00b4ff', glow: 'rgba(0,180,255,0.3)', name: 'Océano', emoji: '💙' },
    violet: { accent: '#a855f7', glow: 'rgba(168,85,247,0.3)', name: 'Violeta', emoji: '💜' },
    rose: { accent: '#ec4899', glow: 'rgba(236,72,153,0.3)', name: 'Rosa', emoji: '💗' },
    amber: { accent: '#f59e0b', glow: 'rgba(245,158,11,0.3)', name: 'Ámbar', emoji: '🧡' },
    ruby: { accent: '#ef4444', glow: 'rgba(239,68,68,0.3)', name: 'Rubí', emoji: '❤️' },
    ice: { accent: '#22d3ee', glow: 'rgba(34,211,238,0.3)', name: 'Hielo', emoji: '🩵' },
    lime: { accent: '#84cc16', glow: 'rgba(132,204,22,0.3)', name: 'Lima', emoji: '💚' },
} as const;

type ThemePreset = keyof typeof THEME_PRESETS;

const THEMES = {
    light: { bg: '#ffffff', surf: '#f0f2f5', border: '#d1d5db', text: '#0f172a', sec: '#475569', muted: '#64748b', inputBg: '#e8eaed' },
    dark: { bg: '#0a0a0a', surf: '#141414', border: '#1f1f1f', text: '#f0f0f0', sec: '#888', muted: '#555', inputBg: '#111' },
    ultra: { bg: '#000000', surf: '#080808', border: '#141414', text: '#e0e0e0', sec: '#666', muted: '#333', inputBg: '#050505' },
};

const FILE_TYPES = {
    document: { icon: FileText, label: 'Subir documento', color: '#3b82f6', accept: '.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.rtf,.csv,.js,.ts,.py,.html,.css,.json,.xml,.md', maxSize: 20 * 1024 * 1024 },
    image: { icon: ImageIcon, label: 'Subir imagen', color: '#a855f7', accept: 'image/*,.heic', maxSize: 10 * 1024 * 1024 },
    video: { icon: Film, label: 'Subir video', color: '#ec4899', accept: 'video/mp4,video/webm,video/quicktime', maxSize: 100 * 1024 * 1024 },
    audio: { icon: Music, label: 'Subir audio', color: '#f97316', accept: 'audio/mpeg,audio/wav,audio/ogg', maxSize: 50 * 1024 * 1024 },
    camera: { icon: Camera, label: 'Activar Cámara', color: '#3b82f6', accept: 'image/*,video/*', maxSize: 50 * 1024 * 1024 },
} as const;

type FileType = keyof typeof FILE_TYPES;
interface UploadedFile { id: string; type: FileType; name: string; size: number; preview?: string; data: string; mimeType: string; }
interface Msg { id: string; role: 'user' | 'assistant'; content: string; ts: number; streaming?: boolean; }
interface Conv { id: string; title: string; pinned?: boolean; archived?: boolean; }

// ═══════════════════════════════════════════
//  PARTICLES — Floating background effect
// ═══════════════════════════════════════════

function Particles({ color, count = 20 }: { color: string; count?: number }) {
    const [particles, setParticles] = useState<{ id: number; x: number; y: number; size: number; duration: number; delay: number; opacity: number; }[]>([]);
    const [mounted, setMounted] = useState(false);

    useEffect(() => {
        setParticles(Array.from({ length: count }, (_, i) => ({
            id: i,
            x: Math.random() * 100,
            y: Math.random() * 100,
            size: Math.random() * 3 + 1,
            duration: Math.random() * 20 + 15,
            delay: Math.random() * 10,
            opacity: Math.random() * 0.15 + 0.03,
        })));
        setMounted(true);
    }, [count]);

    if (!mounted) return null;

    return (
        <div style={{ position: 'absolute', inset: 0, overflow: 'hidden', pointerEvents: 'none', zIndex: 0 }}>
            {particles.map(p => (
                <motion.div
                    key={p.id}
                    animate={{
                        y: [0, -30, 0, 20, 0],
                        x: [0, 15, -10, 5, 0],
                        opacity: [p.opacity, p.opacity * 2, p.opacity, p.opacity * 1.5, p.opacity],
                    }}
                    transition={{
                        duration: p.duration,
                        delay: p.delay,
                        repeat: Infinity,
                        ease: 'easeInOut',
                    }}
                    style={{
                        position: 'absolute',
                        left: `${p.x}%`,
                        top: `${p.y}%`,
                        width: p.size,
                        height: p.size,
                        borderRadius: '50%',
                        background: color,
                        boxShadow: `0 0 ${p.size * 3}px ${color}`,
                    }}
                />
            ))}
        </div>
    );
}

// ═══════════════════════════════════════════
//  TYPING INDICATOR
// ═══════════════════════════════════════════

function TypingIndicator({ accent }: { accent: string }) {
    return (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '14px 18px' }}>
            <div style={{ display: 'flex', gap: 5, alignItems: 'center' }}>
                <div className="typing-dot" style={{ background: accent }} />
                <div className="typing-dot" style={{ background: accent }} />
                <div className="typing-dot" style={{ background: accent }} />
            </div>
            <span style={{ fontSize: 12, color: '#666', fontStyle: 'italic' }}>pensando...</span>
        </div>
    );
}

// ═══════════════════════════════════════════
//  MAIN COMPONENT
// ═══════════════════════════════════════════

export function NexaApp() {
    const [mounted, setMounted] = useState(false);
    useEffect(() => { setMounted(true); }, []);
    // ─── States ───
    const [msgs, setMsgs] = useState<Msg[]>([]);
    const [convs, setConvs] = useState<Conv[]>([]);
    const [convId, setConvId] = useState<string | null>(null);
    const [input, setInput] = useState('');
    const [suggestion, setSuggestion] = useState('');
    const [thinking, setThinking] = useState(false);
    const [streaming, setStreaming] = useState(false);
    const [drawer, setDrawer] = useState(false);
    const [showUpload, setShowUpload] = useState(false);
    const [attachedFiles, setAttachedFiles] = useState<UploadedFile[]>([]);
    const [activeConvMenu, setActiveConvMenu] = useState<string | null>(null);
    const [activeMsgMenu, setActiveMsgMenu] = useState<string | null>(null);
    const [showSettings, setShowSettings] = useState(false);
    const [analyzingImage, setAnalyzingImage] = useState(false);
    const [activeProvider, setActiveProvider] = useState<string>('groq');

    // Settings (Persisted)
    const [accent, setAccent] = useState(THEME_PRESETS.emerald.accent);
    const [themePreset, setThemePreset] = useState<ThemePreset>('emerald');
    const [themeName, setThemeName] = useState<'system' | 'light' | 'dark'>('dark');
    const [resolvedTheme, setResolvedTheme] = useState<keyof typeof THEMES>('dark');
    const [autoSpeak, setAutoSpeak] = useState(false);
    const [autoSend, setAutoSend] = useState(true);
    const [voiceGender, setVoiceGender] = useState<'male' | 'female'>('female');
    const [voiceIndex, setVoiceIndex] = useState(0);
    const [lang, setLang] = useState('es');
    const [showThemePicker, setShowThemePicker] = useState(false);

    // System
    const [conn, setConn] = useState<'ok' | 'err' | 'check'>('check');
    const [search, setSearch] = useState('');
    const [recording, setRecording] = useState(false);
    const [speaking, setSpeaking] = useState(false);
    const [speakingMsgId, setSpeakingMsgId] = useState<string | null>(null);
    const [availableVoices, setAvailableVoices] = useState<SpeechSynthesisVoice[]>([]);
    const [convLoading, setConvLoading] = useState(false);
    const [msgRatings, setMsgRatings] = useState<Record<string, 'up' | 'down'>>({});

    // Scroll
    const endRef = useRef<HTMLDivElement>(null);
    const chatContainerRef = useRef<HTMLDivElement>(null);
    const userScrolledRef = useRef(false);
    const [showScrollBtn, setShowScrollBtn] = useState(false);
    const [unreadCount, setUnreadCount] = useState(0);

    const inputRef = useRef<HTMLTextAreaElement>(null);
    const recRef = useRef<any>(null);
    const sb = getSupabase();

    const T = THEMES[resolvedTheme] || THEMES.dark;
    const preset = THEME_PRESETS[themePreset];

    // ═══════════════════════════════════════════
    //  SCROLL SYSTEM — Smooth + keyboard + auto
    // ═══════════════════════════════════════════

    const scrollToBottom = useCallback((smooth = true) => {
        const container = chatContainerRef.current;
        if (!container) return;
        if (smooth) {
            container.scrollTo({ top: container.scrollHeight, behavior: 'smooth' });
        } else {
            container.scrollTop = container.scrollHeight;
        }
    }, []);

    const isNearBottom = useCallback(() => {
        const container = chatContainerRef.current;
        if (!container) return true;
        return container.scrollHeight - container.scrollTop - container.clientHeight < 120;
    }, []);

    // Scroll detection
    useEffect(() => {
        const container = chatContainerRef.current;
        if (!container) return;
        const handleScroll = () => {
            const nearBottom = isNearBottom();
            userScrolledRef.current = !nearBottom;
            setShowScrollBtn(!nearBottom && msgs.length > 0);
            if (nearBottom) setUnreadCount(0);
        };
        container.addEventListener('scroll', handleScroll, { passive: true });
        return () => container.removeEventListener('scroll', handleScroll);
    }, [msgs.length, isNearBottom]);

    // Keyboard shortcuts for scroll
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            const container = chatContainerRef.current;
            if (!container || showSettings || drawer) return;
            const target = e.target as HTMLElement;
            if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA') return;

            if (e.key === 'PageDown') {
                e.preventDefault();
                container.scrollBy({ top: container.clientHeight * 0.8, behavior: 'smooth' });
            } else if (e.key === 'PageUp') {
                e.preventDefault();
                container.scrollBy({ top: -container.clientHeight * 0.8, behavior: 'smooth' });
            } else if (e.key === 'Home' && e.ctrlKey) {
                e.preventDefault();
                container.scrollTo({ top: 0, behavior: 'smooth' });
            } else if (e.key === 'End' && e.ctrlKey) {
                e.preventDefault();
                scrollToBottom();
            }
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [showSettings, drawer, scrollToBottom]);

    // Auto-scroll on new messages
    useEffect(() => {
        if (!userScrolledRef.current) {
            scrollToBottom();
        } else if (streaming) {
            setUnreadCount(p => p + 1);
        }
    }, [msgs, streaming, scrollToBottom]);

    // Auto-scroll during streaming — faster interval for smoother experience
    useEffect(() => {
        if (streaming) {
            const interval = setInterval(() => {
                if (!userScrolledRef.current) scrollToBottom(false);
            }, 50);
            return () => clearInterval(interval);
        }
    }, [streaming, scrollToBottom]);

    // Scroll to bottom when conversation changes
    useEffect(() => {
        if (convId) {
            userScrolledRef.current = false;
            setTimeout(() => scrollToBottom(false), 150);
        }
    }, [convId, scrollToBottom]);

    // ═══════════════════════════════════════════
    //  THEME SYSTEM
    // ═══════════════════════════════════════════

    useEffect(() => {
        if (themeName === 'system') {
            const mql = window.matchMedia('(prefers-color-scheme: dark)');
            const update = () => setResolvedTheme(mql.matches ? 'dark' : 'light');
            update();
            mql.addEventListener('change', update);
            return () => mql.removeEventListener('change', update);
        }
        setResolvedTheme(themeName as any);
    }, [themeName]);

    const cycleTheme = () => {
        const next = resolvedTheme === 'dark' ? 'light' : resolvedTheme === 'light' ? 'ultra' : 'dark';
        setThemeName(next as any);
        localStorage.setItem('nexa_theme', next);
    };

    const cycleAccent = () => {
        const keys = Object.keys(THEME_PRESETS) as ThemePreset[];
        const idx = keys.indexOf(themePreset);
        const next = keys[(idx + 1) % keys.length];
        setThemePreset(next);
        setAccent(THEME_PRESETS[next].accent as any);
        localStorage.setItem('nexa_preset', next);
        localStorage.setItem('nexa_accent', THEME_PRESETS[next].accent);
    };

    // ═══════════════════════════════════════════
    //  PERSISTENCE
    // ═══════════════════════════════════════════

    useEffect(() => {
        const savedPreset = localStorage.getItem('nexa_preset') as ThemePreset | null;
        const savedAccent = localStorage.getItem('nexa_accent');
        const savedTheme = localStorage.getItem('nexa_theme');
        const savedAuto = localStorage.getItem('nexa_autospeak');
        const savedAutoSend = localStorage.getItem('nexa_autosend');
        const savedGender = localStorage.getItem('nexa_gender');
        const savedVoiceIdx = localStorage.getItem('nexa_voice_idx');
        const savedLang = localStorage.getItem('nexa_lang');

        if (savedPreset && THEME_PRESETS[savedPreset]) {
            setThemePreset(savedPreset);
            setAccent(THEME_PRESETS[savedPreset].accent as any);
        } else if (savedAccent) {
            setAccent(savedAccent as any);
        }
        if (savedTheme) setThemeName(savedTheme as any);
        if (savedAuto) setAutoSpeak(savedAuto === 'true');
        if (savedAutoSend) setAutoSend(savedAutoSend === 'true');
        if (savedGender) setVoiceGender(savedGender as any);
        if (savedVoiceIdx) setVoiceIndex(parseInt(savedVoiceIdx));
        if (savedLang) setLang(savedLang);

        const loadVoices = () => {
            if (typeof window !== 'undefined' && window.speechSynthesis) {
                setAvailableVoices(window.speechSynthesis.getVoices());
            }
        };
        loadVoices();
        if (typeof window !== 'undefined' && window.speechSynthesis) {
            window.speechSynthesis.onvoiceschanged = loadVoices;
        }
        setMounted(true);
    }, []);

    useEffect(() => {
        localStorage.setItem('nexa_accent', accent);
        localStorage.setItem('nexa_preset', themePreset);
        localStorage.setItem('nexa_theme', themeName);
        localStorage.setItem('nexa_autospeak', autoSpeak.toString());
        localStorage.setItem('nexa_autosend', autoSend.toString());
        localStorage.setItem('nexa_gender', voiceGender);
        localStorage.setItem('nexa_voice_idx', voiceIndex.toString());
        localStorage.setItem('nexa_lang', lang);
        document.documentElement.style.setProperty('--nexa-accent', accent);
    }, [accent, themePreset, themeName, autoSpeak, autoSend, voiceGender, voiceIndex, lang]);

    // ═══════════════════════════════════════════
    //  SUPABASE
    // ═══════════════════════════════════════════

    useEffect(() => { checkConn(); const i = setInterval(checkConn, 30000); return () => clearInterval(i); }, []);
    useEffect(() => { loadConvs(); }, []);

    // Cleanup on unmount — prevent memory leaks
    useEffect(() => {
        return () => {
            try { recRef.current?.stop(); } catch {}
            if (typeof window !== 'undefined' && window.speechSynthesis) {
                window.speechSynthesis.cancel();
            }
        };
    }, []);

    const checkConn = async () => {
        setConn('check');
        try { const r = await fetch('/api/health', { method: 'HEAD', signal: AbortSignal.timeout(8000) }); setConn(r.ok ? 'ok' : 'err'); }
        catch { setConn('err'); }
    };

    const loadConvs = async () => {
        setConvLoading(true);
        try {
            const { data, error } = await sb.from('conversations').select('*').order('updated_at', { ascending: false });
            if (!error && Array.isArray(data)) setConvs(data);
        } catch {} finally { setConvLoading(false); }
    };

    const createConv = async (title = 'Nueva conversación') => {
        const local: Conv = { id: `c-${Date.now()}`, title };
        try {
            const { data, error } = await sb.from('conversations').insert({ title }).select().single();
            if (!error && data) { local.id = data.id; local.title = data.title; }
        } catch {}
        setConvs(p => [local, ...(Array.isArray(p) ? p : [])]);
        setConvId(local.id);
        setMsgs([]);
        return local.id;
    };

    const delConv = async (id: string) => {
        try { await sb.from('messages').delete().eq('conversation_id', id); await sb.from('conversations').delete().eq('id', id); } catch {}
        setConvs(p => Array.isArray(p) ? p.filter(c => c.id !== id) : []);
        if (convId === id) { setConvId(null); setMsgs([]); }
    };

    const selConv = async (id: string) => {
        setConvId(id); setDrawer(false);
        try {
            const { data, error } = await sb.from('messages').select('*').eq('conversation_id', id).order('created_at');
            if (!error && Array.isArray(data)) {
                setMsgs(data.map((m: any) => ({ id: m.id, role: m.role, content: m.content || '', ts: +new Date(m.created_at) })));
            }
        } catch {}
        setTimeout(() => scrollToBottom(false), 100);
    };

    const exportConv = (id: string, format: 'json' | 'txt') => {
        const c = (Array.isArray(convs) ? convs : []).find(cv => cv.id === id);
        const content = format === 'json' 
            ? JSON.stringify({ title: c?.title, messages: msgs }, null, 2) 
            : (Array.isArray(msgs) ? msgs : []).map(m => `${m.role.toUpperCase()}: ${m.content}`).join('\n\n');
        const blob = new Blob([content], { type: format === 'json' ? 'application/json' : 'text/plain' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url; a.download = `${c?.title || 'nexa-chat'}.${format}`; a.click();
        URL.revokeObjectURL(url);
    };

    // ═══════════════════════════════════════════
    //  SEND MESSAGE
    // ═══════════════════════════════════════════

    const [sending, setSending] = useState(false);

    const send = async (overrideText?: string) => {
        if (sending) return;
        const textToSend = (overrideText ?? input).trim();
        if (!textToSend && attachedFiles.length === 0) return;
        
        setSending(true);
        setStreaming(false);
        setThinking(false);

        let finalContent = textToSend;
        if (attachedFiles.length > 0) {
            const fileInfo = attachedFiles.map(f => `[📎 ${f.name} (${f.type})]`).join(' ');
            finalContent = textToSend ? `${textToSend}\n\n${fileInfo}` : fileInfo;
        }

        if (!overrideText) setInput('');
        setSuggestion('');
        setAttachedFiles([]);
        userScrolledRef.current = false;

        let currentCid = convId;
        if (!currentCid) {
            currentCid = await createConv(finalContent.slice(0, 50));
            setConvId(currentCid);
        }

        const userMsg: Msg = { id: `u-${Date.now()}`, role: 'user', content: finalContent, ts: Date.now() };
        setMsgs(prev => [...prev, userMsg]);
        setThinking(true);

        const aid = `a-${Date.now()}`;
        const thinkingTimeout = setTimeout(() => {
            setThinking(false);
            setStreaming(true);
            setMsgs(prev => [...prev, { id: aid, role: 'assistant', content: '', ts: Date.now(), streaming: true }]);
        }, 400);

        // FIX: Retry logic with AbortController timeout to prevent connection abort
        const MAX_RETRIES = 2;
        let lastError: any = null;
        
        for (let attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            if (attempt > 0) {
                // Exponential backoff: 2s, 4s
                await new Promise(r => setTimeout(r, 2000 * attempt));
            }
            
            const controller = new AbortController();
            const fetchTimeout = setTimeout(() => controller.abort(), 55000); // < 60s server limit
            
        try {
            await sb.from('messages').insert({ conversation_id: currentCid, role: 'user', content: finalContent });
            
            const res = await fetch('/api/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ 
                    messages: [...msgs, userMsg].map(m => ({ role: m.role, content: m.content })),
                    stream: true 
                }),
                signal: controller.signal,
            });

            if (!res.ok) {
                const e = await res.json().catch(() => ({}));
                throw new Error(e.error || `Error ${res.status}`);
            }

            clearTimeout(thinkingTimeout);
            setThinking(false);
            setStreaming(true);
            
            setMsgs(p => {
                if (p.some(m => m.id === aid)) return p;
                return [...p, { id: aid, role: 'assistant', content: '', ts: Date.now(), streaming: true }];
            });

            const reader = res.body?.getReader();
            const dec = new TextDecoder();
            let full = '';
            let buffer = '';
            let serverError = '';

            if (reader) {
                while (true) {
                    const { done, value } = await reader.read();
                    if (done) break;
                    buffer += dec.decode(value, { stream: true });
                    const parts = buffer.split('\n\n');
                    buffer = parts.pop() ?? '';
                    for (const part of parts) {
                        const line = part.trim();
                        if (!line.startsWith('data: ')) continue;
                        try {
                            const d = JSON.parse(line.slice(6));
                            if (d.provider) setActiveProvider(d.provider);
                            if (d.error) serverError = d.error;
                            if (d.text) {
                                full += d.text;
                                setMsgs(p => p.map(m => m.id === aid ? { ...m, content: full } : m));
                            }
                            if (d.done) {
                                const finalRes = full || d.fullResponse || '';
                                setMsgs(p => p.map(m => m.id === aid ? { ...m, content: finalRes, streaming: false } : m));
                                if (autoSpeak) speak(finalRes);
                            }
                        } catch {}
                    }
                }
            }

            if (!full) {
                const errMsg = serverError ? `❌ ${serverError.split('\n')[0]}` : '❌ No se recibió respuesta del servidor.';
                setMsgs(p => p.map(m => m.id === aid ? { ...m, content: errMsg, streaming: false } : m));
            } else {
                try { await sb.from('messages').insert({ conversation_id: currentCid, role: 'assistant', content: full }); } catch {}
            }

            clearTimeout(fetchTimeout);
            lastError = null;
            break; // Success, exit retry loop
            
        } catch (e: any) {
            clearTimeout(fetchTimeout);
            lastError = e;
            const isAbort = e.name === 'AbortError';
            const isNetworkError = e.message?.includes('connection') || e.message?.includes('network') || e.message?.includes('abort') || e.message?.includes('fetch');
            
            // Only retry on network/connection errors, not on server logic errors
            if (!isNetworkError || attempt >= MAX_RETRIES) {
                break;
            }
            // Continue to next retry attempt
            continue;
        } finally {
            clearTimeout(fetchTimeout);
        }
        } // end retry loop
        
        // Handle error after all retries exhausted
        if (lastError) {
            clearTimeout(thinkingTimeout);
            const isTimeout = lastError.name === 'AbortError';
            const errorMsg = isTimeout
                ? '⚠️ La respuesta tardó demasiado. Intenta de nuevo.'
                : '⚠️ Error de conexión. Verifica tu internet e intenta de nuevo.';
            setMsgs(p => {
                if (p.some(m => m.id === aid)) {
                    return p.map(m => m.id === aid ? { ...m, content: errorMsg, streaming: false } : m);
                }
                return [...p, { id: `e-${Date.now()}`, role: 'assistant', content: errorMsg, ts: Date.now() }];
            });
        }
        
        setSending(false);
        setThinking(false);
        setStreaming(false);
    };

    // ═══════════════════════════════════════════
    //  VOICE
    // ═══════════════════════════════════════════

    const voiceSentRef = useRef(false);

    const toggleRec = async () => {
        if (recording) { try { recRef.current?.stop(); } catch {} setRecording(false); return; }
        if (typeof window === 'undefined') return;
        const SR = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
        if (!SR) { alert('Tu navegador no soporta reconocimiento de voz.'); return; }
        try {
            if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
                await navigator.mediaDevices.getUserMedia({ audio: true });
            }
        } catch { alert('Permiso de micrófono denegado.'); return; }
        try {
            voiceSentRef.current = false;
            const r = new SR(); r.lang = lang === 'es' ? 'es-ES' : 'en-US'; r.continuous = true; r.interimResults = true;
            r.onstart = () => { setRecording(true); setInput(''); };
            r.onresult = (e: any) => {
                let fullTxt = '';
                for (let i = 0; i < e.results.length; i++) {
                    fullTxt += e.results[i][0].transcript;
                }
                setInput(fullTxt);

                const lastResult = e.results[e.results.length - 1];
                if (lastResult.isFinal && autoSend && !voiceSentRef.current) {
                    const finalTxt = fullTxt.trim();
                    if (finalTxt) {
                        voiceSentRef.current = true;
                        send(finalTxt).then(() => {
                            setInput('');
                            try { r.stop(); } catch {}
                            setRecording(false);
                        });
                    }
                }
            };
            r.onerror = (e: any) => {
                console.warn('[NEXA] Voice error:', e.error);
                setRecording(false);
            };
            r.onend = () => setRecording(false);
            r.start(); recRef.current = r;
        } catch { setRecording(false); }
    };

    const cleanForSpeech = (text: string) => text.replace(/#{1,6}\s*/g, '').replace(/\*{1,3}(.+?)\*{1,3}/g, '$1').replace(/_{1,3}(.+?)_{1,3}/g, '$1').replace(/```[\s\S]*?```/g, 'código').replace(/`([^`]+)`/g, '$1').replace(/\n{2,}/g, '. ').replace(/\n/g, '. ').replace(/\s{2,}/g, ' ').trim();

    const stopSpeaking = useCallback(() => {
        if (typeof window === 'undefined' || !window.speechSynthesis) return;
        speechQueueRef.current = [];
        isSpeakingQueueRef.current = false;
        window.speechSynthesis.cancel();
        // cancel() is async and onend may not fire reliably — force-reset state immediately
        setSpeaking(false);
        setSpeakingMsgId(null);
    }, []);

    // Queue system for chunked speech (fixes Android WebView cutoff)
    const speechQueueRef = useRef<SpeechSynthesisUtterance[]>([]);
    const isSpeakingQueueRef = useRef(false);

    const processSpeechQueue = useCallback(() => {
        if (speechQueueRef.current.length === 0) {
            isSpeakingQueueRef.current = false;
            setSpeaking(false);
            setSpeakingMsgId(null);
            return;
        }
        isSpeakingQueueRef.current = true;
        const u = speechQueueRef.current.shift()!;
        u.onend = () => processSpeechQueue();
        u.onerror = () => processSpeechQueue();
        window.speechSynthesis.speak(u);
    }, []);

    const speak = (text: string, msgId?: string) => {
        if (!('speechSynthesis' in window)) return;
        try {
            // If clicking on the same message that's already speaking → stop it
            if (msgId && speakingMsgId === msgId) {
                stopSpeaking();
                return;
            }
            // Stop any current speech before starting new one
            stopSpeaking();
            const cleaned = cleanForSpeech(text);
            if (!cleaned) return;
            setSpeakingMsgId(msgId ?? null);
            setSpeaking(true);

            const langVoices = availableVoices.filter(v => v.lang.includes(lang.split('-')[0]));
            const genderedVoices = langVoices.filter(v => {
                const n = v.name.toLowerCase();
                if (voiceGender === 'male') return n.includes('male') || n.includes('diego') || n.includes('carlos');
                return n.includes('female') || n.includes('katerina') || n.includes('sofia') || n.includes('helena');
            });
            const filtered = genderedVoices.length > 0 ? genderedVoices : langVoices;
            const selectedVoice = filtered[voiceIndex % filtered.length] || filtered[0] || (availableVoices.length > 0 ? availableVoices[0] : null);

            // Split text into chunks of ~200 chars at sentence boundaries
            const maxChunk = 200;
            const chunks: string[] = [];
            let remaining = cleaned;
            while (remaining.length > 0) {
                if (remaining.length <= maxChunk) {
                    chunks.push(remaining);
                    break;
                }
                // Find last sentence boundary within maxChunk
                let splitAt = -1;
                for (const sep of ['. ', '! ', '? ', '.\n', '!\n', '?\n', '\n']) {
                    const idx = remaining.lastIndexOf(sep, maxChunk);
                    if (idx > splitAt) splitAt = idx + sep.length;
                }
                if (splitAt <= 0) splitAt = maxChunk; // No boundary found, hard split
                chunks.push(remaining.slice(0, splitAt));
                remaining = remaining.slice(splitAt).trimStart();
            }

            // Create utterances for each chunk
            speechQueueRef.current = chunks.map(chunk => {
                const u = new SpeechSynthesisUtterance(chunk);
                u.lang = lang === 'es' ? 'es-ES' : 'en-US';
                if (selectedVoice) u.voice = selectedVoice;
                u.rate = 1;
                u.pitch = 1;
                return u;
            });

            processSpeechQueue();
        } catch { setSpeaking(false); setSpeakingMsgId(null); }
    };

    // ═══════════════════════════════════════════
    //  IMAGE ANALYSIS
    // ═══════════════════════════════════════════

    const analyzeImage = async (file: File, question?: string) => {
        setAnalyzingImage(true);
        try {
            const base64 = await new Promise<string>((res, rej) => {
                const reader = new FileReader();
                reader.onload = () => res((reader.result as string).split(',')[1]);
                reader.onerror = rej;
                reader.readAsDataURL(file);
            });
            const qMsg: Msg = { id: `u-${Date.now()}`, role: 'user', content: question || '🖼️ [Imagen enviada para análisis]', ts: Date.now() };
            setMsgs(p => [...p, qMsg]); setThinking(true);
            const res = await fetch('/api/vision', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ image: base64, mimeType: file.type, question }) });
            if (res.ok) {
                const data = await res.json();
                setMsgs(p => [...p, { id: `a-${Date.now()}`, role: 'assistant', content: data.response, ts: Date.now() }]);
                if (autoSpeak) speak(data.response);
            } else {
                const err = await res.json().catch(() => ({}));
                setMsgs(p => [...p, { id: `a-${Date.now()}`, role: 'assistant', content: `❌ Error: ${err.error || 'Desconocido'}`, ts: Date.now() }]);
            }
        } catch (e: any) {
            setMsgs(p => [...p, { id: `a-${Date.now()}`, role: 'assistant', content: `❌ ${e.message}`, ts: Date.now() }]);
        } finally { setAnalyzingImage(false); setThinking(false); }
    };

    // ═══════════════════════════════════════════
    //  RENDER HELPERS
    // ═══════════════════════════════════════════

    const renderMessageContent = (content: string) => {
        if (!content) return <span style={{ whiteSpace: 'pre-wrap', opacity: 0.4 }}>(vacío)</span>;

        // 1. Manejo de Mapas Visuales
        if (content.includes('[MAPA VISUAL]:')) {
            const mapUrl = content.match(/\[MAPA VISUAL\]: (https?:\/\/[^\s\n]+)/)?.[1];
            if (mapUrl) {
                const textBefore = content.split('[MAPA VISUAL]:')[0];
                return (
                    <div>
                        {textBefore && <div style={{ marginBottom: 10 }}>{renderMessageContent(textBefore)}</div>}
                        <div style={{ borderRadius: 16, overflow: 'hidden', border: `1px solid ${T.border}`, margin: '10px 0' }}>
                            <img src={mapUrl} alt="Mapa de ubicación" style={{ width: '100%', height: 'auto', display: 'block' }} />
                        </div>
                    </div>
                );
            }
        }

        // 2. Manejo de Spotify
        if (content.includes('[REPRODUCTOR]:')) {
            const spotifyUrl = content.match(/\[REPRODUCTOR\]: (https?:\/\/[^\s\n]+)/)?.[1];
            if (spotifyUrl) {
                const textBefore = content.split('[REPRODUCTOR]:')[0];
                return (
                    <div>
                        {textBefore && <div style={{ marginBottom: 10 }}>{renderMessageContent(textBefore)}</div>}
                        <div style={{ borderRadius: 16, overflow: 'hidden', margin: '10px 0' }}>
                            <iframe src={spotifyUrl} width="100%" height="152" frameBorder="0" allow="autoplay; clipboard-write; encrypted-media; fullscreen; picture-in-picture" loading="lazy" />
                        </div>
                    </div>
                );
            }
        }

        // 3. Manejo de Imágenes (Markdown)
        if (content.includes('![') && content.includes('](')) {
            const imgMatch = content.match(/!\[.*?\]\((https?:\/\/[^\s\n)]+)\)/);
            if (imgMatch) {
                const imgUrl = imgMatch[1];
                const parts = content.split(imgMatch[0]);
                return (
                    <div>
                        {parts[0] && <span>{parts[0]}</span>}
                        <div style={{ borderRadius: 16, overflow: 'hidden', border: `1px solid ${T.border}`, margin: '12px 0' }}>
                            <img src={imgUrl} alt="Visual" style={{ width: '100%', maxHeight: 400, objectFit: 'cover' }} />
                        </div>
                        {parts[1] && <span>{parts[1]}</span>}
                    </div>
                );
            }
        }

        const parts = content.split(/(```[\s\S]*?```)/g);
        return parts.map((part, i) => {
            if (part.startsWith('```')) {
                const match = part.match(/```(\w+)?\n?([\s\S]*?)```/);
                if (match) {
                    const lang = match[1] || 'code';
                    const code = match[2].trim();
                    return (
                        <div key={i} style={{ margin: '12px 0', borderRadius: 12, overflow: 'hidden', border: `1px solid ${T.border}` }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 14px', background: T.surf, borderBottom: `1px solid ${T.border}` }}>
                                <span style={{ fontSize: 12, color: accent, fontWeight: 600, textTransform: 'uppercase', fontFamily: "'JetBrains Mono', monospace" }}>{lang}</span>
                                <button onClick={() => navigator.clipboard.writeText(code)} style={{ background: 'none', border: 'none', color: T.muted, cursor: 'pointer', fontSize: 12, display: 'flex', alignItems: 'center', gap: 4 }}>📋 Copiar</button>
                            </div>
                            <pre style={{ padding: '14px', background: resolvedTheme === 'light' ? '#f6f8fa' : '#0d1117', overflow: 'auto', maxHeight: 400, margin: 0, WebkitOverflowScrolling: 'touch' as any }}>
                                <code style={{ fontSize: 13, lineHeight: 1.6, fontFamily: "'JetBrains Mono', 'Fira Code', monospace", color: T.text }}>{code}</code>
                            </pre>
                        </div>
                    );
                }
            }
            return <span key={i} style={{ whiteSpace: 'pre-wrap' }}>{part}</span>;
        });
    };

    const copyToClipboard = (text: string) => navigator.clipboard.writeText(text);

    const toggleRating = (msgId: string, rating: 'up' | 'down') => {
        setMsgRatings(prev => {
            const next = { ...prev };
            if (next[msgId] === rating) { delete next[msgId]; } else { next[msgId] = rating; }
            return next;
        });
    };
    const filtered = (Array.isArray(convs) ? convs : []).filter(c => (c.title || '').toLowerCase().includes(search.toLowerCase()));
    const ibtn: React.CSSProperties = { background: 'none', border: 'none', color: T.muted, cursor: 'pointer', padding: 8, borderRadius: 8, display: 'flex', alignItems: 'center', justifyContent: 'center' };
    const menuBtn: React.CSSProperties = { background: 'none', border: 'none', cursor: 'pointer', padding: '10px 14px', borderRadius: 10, display: 'flex', alignItems: 'center', gap: 12, fontSize: 14, fontWeight: 500, width: '100%', transition: 'background 0.15s', fontFamily: 'inherit' };

    if (!mounted) {
        return (
            <div style={{ position: 'fixed', inset: 0, background: '#000', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <motion.div animate={{ rotate: 360 }} transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}
                    style={{ width: 40, height: 40, borderRadius: '50%', border: `2px solid ${accent}`, borderTopColor: 'transparent' }} />
            </div>
        );
    }

    // ═══════════════════════════════════════════
    //  RENDER
    // ═══════════════════════════════════════════

    return (
        <div role="application" aria-label="NEXA AI Chat Interface" suppressHydrationWarning
            className={showThemePicker ? 'theme-transition' : ''}
            style={{ position: 'fixed', inset: 0, background: T.bg, color: T.text, fontFamily: "'Inter',sans-serif", display: 'flex', flexDirection: 'column', overflow: 'hidden', transition: 'background-color 0.4s ease' }}>

            {/* Skip link */}
            <a href="#nexa-chat-input" style={{ position: 'absolute', left: -9999, top: 'auto', width: 1, height: 1, overflow: 'hidden' }}
                onFocus={(e) => { e.currentTarget.style.left = '8px'; e.currentTarget.style.top = '8px'; e.currentTarget.style.width = 'auto'; e.currentTarget.style.height = 'auto'; e.currentTarget.style.zIndex = '9999'; e.currentTarget.style.background = accent; e.currentTarget.style.color = '#000'; e.currentTarget.style.padding = '8px 16px'; e.currentTarget.style.borderRadius = '8px'; }}>
                Saltar al chat
            </a>

            {/* Drawer backdrop */}
            <AnimatePresence>{drawer && <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={() => setDrawer(false)} style={{ position: 'fixed', inset: 0, zIndex: 40, background: 'rgba(0,0,0,0.5)', backdropFilter: 'blur(4px)' }} />}</AnimatePresence>

            {/* Drawer */}
            <AnimatePresence>
                {drawer && (
                    <motion.aside role="navigation" aria-label="Historial de conversaciones" initial={{ x: -290 }} animate={{ x: 0 }} exit={{ x: -290 }} transition={{ type: 'spring', damping: 26, stiffness: 260 }}
                        className="nexa-drawer"
                        style={{ position: 'fixed', top: 0, left: 0, bottom: 0, zIndex: 50, width: 280, background: T.surf, borderRight: `1px solid ${T.border}`, display: 'flex', flexDirection: 'column' }}>
                        <div style={{ padding: '18px 14px 14px', borderBottom: `1px solid ${T.border}` }}>
                            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                                    <div style={{ width: 34, height: 34, borderRadius: 10, background: `${accent}15`, border: `1px solid ${accent}30`, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 16 }}>⚡</div>
                                    <div>
                                        <div style={{ fontSize: 14, fontWeight: 700, letterSpacing: 2 }}>NEXA</div>
                                        <div style={{ fontSize: 8, color: accent, letterSpacing: 2, textTransform: 'uppercase' }}>V3 CLEAN CORE</div>
                                    </div>
                                </div>
                                <button aria-label="Cerrar panel" onClick={() => setDrawer(false)} style={{ ...ibtn, fontSize: 20 }}>✕</button>
                            </div>
                            <button onClick={async () => { await createConv(); setDrawer(false); }} style={{ width: '100%', display: 'flex', alignItems: 'center', gap: 8, padding: '10px 14px', borderRadius: 12, background: `${accent}15`, border: `1px solid ${accent}30`, color: accent, fontSize: 13, fontWeight: 600, cursor: 'pointer' }}>+ NUEVO CHAT</button>
                            <a href="/code" style={{
                                width: '100%', display: 'flex', alignItems: 'center', gap: 8,
                                padding: '10px 14px', borderRadius: 12, background: '#a855f715',
                                border: '1px solid #a855f730', color: '#a855f7', fontSize: 13,
                                fontWeight: 600, cursor: 'pointer', textDecoration: 'none', marginTop: 6,
                                transition: 'all 0.2s'
                            }}>
                                <Code2 size={14} /> GENERAR CÓDIGO
                            </a>
                            <a href="/developer" style={{
                                width: '100%', display: 'flex', alignItems: 'center', gap: 8,
                                padding: '10px 14px', borderRadius: 12, background: '#00e5a015',
                                border: '1px solid #00e5a030', color: '#00e5a0', fontSize: 13,
                                fontWeight: 600, cursor: 'pointer', textDecoration: 'none', marginTop: 6,
                                transition: 'all 0.2s'
                            }}>
                                <Zap size={14} /> MODO DEVELOPER (SOLO)
                            </a>
                        </div>
                        <div style={{ padding: '8px 12px' }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 8, background: T.inputBg, border: `1px solid ${T.border}`, borderRadius: 10, padding: '6px 10px' }}>
                                <Search size={14} color={T.muted} />
                                <input
                                    type="text"
                                    placeholder="Buscar..."
                                    value={search}
                                    onChange={e => setSearch(e.target.value)}
                                    style={{ flex: 1, background: 'none', border: 'none', color: T.text, fontSize: 12, outline: 'none' }}
                                />
                                {search && <button onClick={() => setSearch('')} style={{ background: 'none', border: 'none', color: T.muted, cursor: 'pointer', padding: 2 }}><X size={12} /></button>}
                            </div>
                        </div>
                        <div style={{ flex: 1, overflowY: 'auto', padding: '4px 8px' }}>
                            {convLoading ? (
                                <div style={{ display: 'flex', justifyContent: 'center', padding: 20 }}>
                                    <Loader2 size={20} color={accent} style={{ animation: 'nexa-spin 1s linear infinite' }} />
                                </div>
                            ) : filtered.length === 0 ? (
                                <div style={{ textAlign: 'center', padding: '20px 10px', color: T.muted, fontSize: 12 }}>
                                    {search ? 'Sin resultados' : 'No hay conversaciones'}
                                </div>
                            ) : (
                                filtered.map(c => (
                                    <div key={c.id} style={{ position: 'relative', marginBottom: 2 }}>
                                        <button onClick={() => selConv(c.id)} style={{ width: '100%', display: 'flex', alignItems: 'center', gap: 8, padding: '10px 10px', borderRadius: 10, background: convId === c.id ? `${accent}10` : 'transparent', border: 'none', color: convId === c.id ? accent : T.sec, fontSize: 12, textAlign: 'left', cursor: 'pointer' }}>
                                            {c.pinned && <Pin size={10} style={{ transform: 'rotate(45deg)' }} />}
                                            <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{c.title}</span>
                                        </button>
                                        <button onClick={() => setActiveConvMenu(activeConvMenu === c.id ? null : c.id)} style={{ position: 'absolute', right: 4, top: '50%', transform: 'translateY(-50%)', padding: 6, background: 'none', border: 'none', color: T.muted, cursor: 'pointer' }}><MoreVertical size={14} /></button>
                                    </div>
                                ))
                            )}
                        </div>
                        <div style={{ padding: '10px 12px', borderTop: `1px solid ${T.border}` }}>
                            <button onClick={() => { setShowSettings(true); setDrawer(false); }} style={{ width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: 10, borderRadius: 10, background: T.surf, border: `1px solid ${T.border}`, color: T.sec, fontSize: 12, cursor: 'pointer' }}>
                                <Sparkles size={14} /> Configuración
                            </button>
                        </div>
                    </motion.aside>
                )}
            </AnimatePresence>

            {/* ═══ CHAT VIEW ═══ */}
                <main role="main" style={{ flex: 1, display: 'flex', flexDirection: 'column', position: 'relative', zIndex: 10, minHeight: 0 }}>

                    {/* ─── Header ─── */}
                    <header role="banner" className="nexa-header" style={{
                        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                        padding: '10px 16px', background: T.bg,
                        borderBottom: `1px solid ${T.border}`, flexShrink: 0, zIndex: 30,
                        transition: 'background-color 0.4s ease, border-color 0.4s ease',
                    }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                            <button aria-label="Menú" onClick={() => setDrawer(true)} style={{ ...ibtn, width: 32, height: 32 }}>
                                <Menu size={22} />
                            </button>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer' }} onClick={checkConn}>
                                <div style={{ position: 'relative' }}>
                                    <div style={{ width: 32, height: 32, borderRadius: 8, background: `${accent}12`, border: `1px solid ${accent}25`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                                        <Zap size={15} color={accent} fill={accent} />
                                    </div>
                                    <div style={{ position: 'absolute', bottom: -1, right: -1, width: 8, height: 8, borderRadius: '50%', background: conn === 'ok' ? '#22c55e' : conn === 'err' ? '#ef4444' : '#f59e0b', border: `2px solid ${T.bg}`, boxShadow: conn === 'ok' ? '0 0 6px #22c55e' : 'none' }} />
                                </div>
                                <div>
                                    <span style={{ fontSize: 13, fontWeight: 800, letterSpacing: 1.5, display: 'block', lineHeight: 1.1 }}>NEXA</span>
                                    <span style={{ fontSize: 8, color: accent, letterSpacing: 0.8, fontWeight: 600, textTransform: 'uppercase' }}>{conn === 'ok' ? 'ONLINE' : conn === 'err' ? 'OFFLINE' : 'CHECKING...'}</span>
                                </div>
                            </div>
                        </div>

                        <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                            {/* Global Speaker / Stop button */}
                            <button
                                aria-label={speaking ? "Detener voz" : "Leer última respuesta"}
                                onClick={() => {
                                    if (speaking) {
                                        stopSpeaking();
                                    } else {
                                        const lastAssistant = [...msgs].reverse().find(m => m.role === 'assistant' && m.content && !m.streaming);
                                        if (lastAssistant) speak(lastAssistant.content, lastAssistant.id);
                                    }
                                }}
                                style={{
                                    ...ibtn, width: 32, height: 32,
                                    color: speaking ? '#ef4444' : T.muted,
                                    background: speaking ? '#ef444415' : 'transparent',
                                    transition: 'all 0.2s',
                                    animation: speaking ? 'pulse 1.5s ease-in-out infinite' : 'none',
                                }}>
                                {speaking ? <VolumeX size={18} /> : <Volume2 size={18} />}
                            </button>
                            {/* Theme color picker */}
                            <button aria-label="Cambiar color del tema" onClick={cycleAccent} style={{ ...ibtn, width: 32, height: 32, position: 'relative' }}>
                                <Palette size={18} />
                                <div style={{ position: 'absolute', bottom: 4, right: 4, width: 8, height: 8, borderRadius: '50%', background: accent, border: `2px solid ${T.bg}` }} />
                            </button>
                            {/* New chat */}
                            <button aria-label="Nuevo Chat" onClick={() => createConv()} style={{ ...ibtn, width: 32, height: 32 }}>
                                <Plus size={22} />
                            </button>
                        </div>
                    </header>

                    {/* ─── Theme Picker Dropdown ─── */}
                    <AnimatePresence>
                        {showThemePicker && (
                            <>
                                <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={() => setShowThemePicker(false)} style={{ position: 'fixed', inset: 0, zIndex: 45 }} />
                                <motion.div initial={{ opacity: 0, y: -10, scale: 0.95 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, y: -10, scale: 0.95 }}
                                    style={{ position: 'fixed', top: 56, right: 12, zIndex: 50, background: T.surf, border: `1px solid ${T.border}`, borderRadius: 16, padding: 12, minWidth: 200, boxShadow: `0 16px 48px rgba(0,0,0,0.4)` }}>
                                    <div style={{ fontSize: 10, fontWeight: 700, color: T.muted, textTransform: 'uppercase', letterSpacing: 2, marginBottom: 8, padding: '0 4px' }}>Color de acento</div>
                                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8 }}>
                                        {Object.entries(THEME_PRESETS).map(([key, p]) => (
                                            <button key={key} onClick={() => { setThemePreset(key as ThemePreset); setAccent(p.accent as any); setShowThemePicker(false); }}
                                                style={{ width: 40, height: 40, borderRadius: 10, background: `${p.accent}18`, border: themePreset === key ? `2px solid ${p.accent}` : `1px solid ${T.border}`, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 16, transition: 'all 0.2s' }}
                                                title={p.name}>
                                                {p.emoji}
                                            </button>
                                        ))}
                                    </div>
                                    <div style={{ height: 1, background: T.border, margin: '10px 0' }} />
                                    <div style={{ fontSize: 10, fontWeight: 700, color: T.muted, textTransform: 'uppercase', letterSpacing: 2, marginBottom: 8, padding: '0 4px' }}>Modo</div>
                                    <div style={{ display: 'flex', gap: 4 }}>
                                        {[
                                            { id: 'dark' as const, icon: Moon, label: 'Oscuro' },
                                            { id: 'light' as const, icon: Sun, label: 'Claro' },
                                            { id: 'ultra' as const, icon: Zap, label: 'Ultra' },
                                        ].map(m => (
                                            <button key={m.id} onClick={() => { setThemeName(m.id as any); setShowThemePicker(false); }}
                                                style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4, padding: '8px 4px', borderRadius: 8, background: resolvedTheme === m.id ? `${accent}12` : 'transparent', border: resolvedTheme === m.id ? `1px solid ${accent}25` : '1px solid transparent', color: resolvedTheme === m.id ? accent : T.muted, cursor: 'pointer', fontSize: 9, fontWeight: 600, transition: 'all 0.2s' }}>
                                                <m.icon size={14} />
                                                {m.label}
                                            </button>
                                        ))}
                                    </div>
                                </motion.div>
                            </>
                        )}
                    </AnimatePresence>

                    {/* ─── Messages ─── */}
                    <div ref={chatContainerRef} role="log" aria-label="Mensajes del chat" aria-live="polite"
                        className="chat-scroll"
                        style={{
                            flex: 1, overflowY: 'scroll', padding: '20px 16px',
                            WebkitOverflowScrolling: 'touch' as any,
                            overscrollBehaviorY: 'contain', minHeight: 0, height: 0,
                            touchAction: 'pan-y',
                        }}>
                        {msgs.length === 0 ? (
                            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', gap: 24, textAlign: 'center', padding: 20, position: 'relative' }}>
                                <Particles color={accent} count={15} />
                                <motion.div initial={{ scale: 0.8, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} transition={{ duration: 0.6, ease: [0.16, 1, 0.3, 1] }} style={{ position: 'relative', zIndex: 1 }}>
                                    <motion.div animate={{ y: [0, -8, 0] }} transition={{ duration: 3, repeat: Infinity, ease: 'easeInOut' }}
                                        style={{ fontSize: 56, marginBottom: 16, filter: `drop-shadow(0 0 20px ${preset.glow})` }}>
                                        🧬
                                    </motion.div>
                                    <h1 className="gradient-text nexa-welcome-title" style={{ fontSize: 28, fontWeight: 900, margin: '0 0 8px', letterSpacing: -0.5, lineHeight: 1.2 }}>
                                        NEXA V3
                                    </h1>
                                    <p className="nexa-welcome-subtitle" style={{ fontSize: 14, color: T.muted, margin: 0, maxWidth: 360, lineHeight: 1.6 }}>
                                        Tu asistente inteligente. Pregunta lo que quieras.
                                    </p>
                                    <div style={{ display: 'flex', gap: 8, marginTop: 24, flexWrap: 'wrap', justifyContent: 'center' }}>
                                        {['¿Qué puedes hacer?', 'Escribe un poema', 'Explíca la IA'].map((s, i) => (
                                            <motion.button key={i} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.3 + i * 0.1 }}
                                                onClick={() => { setInput(s); setTimeout(() => send(s), 100); }}
                                                style={{ padding: '8px 16px', borderRadius: 20, background: `${accent}10`, border: `1px solid ${accent}25`, color: accent, fontSize: 13, cursor: 'pointer', fontWeight: 500, transition: 'all 0.2s' }}
                                                onMouseEnter={(e) => { e.currentTarget.style.background = `${accent}20`; e.currentTarget.style.transform = 'translateY(-1px)'; }}
                                                onMouseLeave={(e) => { e.currentTarget.style.background = `${accent}10`; e.currentTarget.style.transform = 'translateY(0)'; }}>
                                                {s}
                                            </motion.button>
                                        ))}
                                    </div>
                                </motion.div>
                            </div>
                        ) : (
                            <div className="nexa-messages-container" style={{ maxWidth: 700, margin: '0 auto', width: '100%', display: 'flex', flexDirection: 'column', gap: 20 }}>
                                {msgs.map((m, idx) => (
                                    <motion.div key={m.id} className="msg-enter"
                                        initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}
                                        transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1], delay: idx === msgs.length - 1 ? 0.05 : 0 }}
                                        style={{ display: 'flex', flexDirection: 'column', alignItems: m.role === 'user' ? 'flex-end' : 'flex-start' }}>
                                        <div className="nexa-msg-bubble" style={{
                                            position: 'relative', maxWidth: '88%',
                                            padding: '14px 18px',
                                            borderRadius: m.role === 'user' ? '20px 20px 4px 20px' : '20px 20px 20px 4px',
                                            background: m.role === 'user'
                                                ? `linear-gradient(135deg, ${accent}15, ${accent}08)`
                                                : T.surf,
                                            border: `1px solid ${m.role === 'user' ? `${accent}20` : T.border}`,
                                            fontSize: 15, lineHeight: 1.65, color: T.text,
                                            whiteSpace: 'pre-wrap', wordBreak: 'break-word',
                                            boxShadow: m.role === 'assistant' ? `0 2px 12px rgba(0,0,0,${resolvedTheme === 'light' ? '0.04' : '0.2'})` : 'none',
                                            transition: 'background 0.3s, border-color 0.3s',
                                        }}>
                                            {m.streaming && !m.content ? <TypingIndicator accent={accent} /> : renderMessageContent(m.content)}
                                        </div>
                                        {m.role === 'assistant' && !m.streaming && m.content && (
                                            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 8, paddingLeft: 4 }}>
                                                <button aria-label="Copiar" style={{ background: 'none', border: 'none', color: T.muted, cursor: 'pointer', padding: 4, borderRadius: 6, transition: 'color 0.2s' }} onMouseEnter={(e) => e.currentTarget.style.color = accent} onMouseLeave={(e) => e.currentTarget.style.color = T.muted} onClick={() => copyToClipboard(m.content)}><Copy size={15} /></button>
                                                <button aria-label="Me gusta" style={{ background: 'none', border: 'none', color: msgRatings[m.id] === 'up' ? '#22c55e' : T.muted, cursor: 'pointer', padding: 4, borderRadius: 6, transition: 'color 0.2s' }} onMouseEnter={(e) => e.currentTarget.style.color = '#22c55e'} onMouseLeave={(e) => e.currentTarget.style.color = msgRatings[m.id] === 'up' ? '#22c55e' : T.muted} onClick={() => toggleRating(m.id, 'up')}><ThumbsUp size={15} /></button>
                                                <button aria-label="No me gusta" style={{ background: 'none', border: 'none', color: msgRatings[m.id] === 'down' ? '#ef4444' : T.muted, cursor: 'pointer', padding: 4, borderRadius: 6, transition: 'color 0.2s' }} onMouseEnter={(e) => e.currentTarget.style.color = '#ef4444'} onMouseLeave={(e) => e.currentTarget.style.color = msgRatings[m.id] === 'down' ? '#ef4444' : T.muted} onClick={() => toggleRating(m.id, 'down')}><ThumbsDown size={15} /></button>
                                                <button aria-label={speakingMsgId === m.id ? "Detener voz" : "Leer en voz alta"} style={{ background: 'none', border: 'none', color: speakingMsgId === m.id ? accent : T.muted, cursor: 'pointer', padding: 4, borderRadius: 6, transition: 'color 0.2s' }} onMouseEnter={(e) => e.currentTarget.style.color = accent} onMouseLeave={(e) => e.currentTarget.style.color = speakingMsgId === m.id ? accent : T.muted} onClick={() => speak(m.content, m.id)}>
                                                    {speakingMsgId === m.id ? <VolumeX size={15} /> : <Volume2 size={15} />}
                                                </button>
                                                <button aria-label="Regenerar" style={{ background: 'none', border: 'none', color: T.muted, cursor: 'pointer', padding: 4, borderRadius: 6, transition: 'color 0.2s' }} onMouseEnter={(e) => e.currentTarget.style.color = accent} onMouseLeave={(e) => e.currentTarget.style.color = T.muted} onClick={() => send(m.content)}><RotateCcw size={15} /></button>
                                                <div style={{ position: 'relative' }}>
                                                    <button aria-label="Más opciones" style={{ background: 'none', border: 'none', color: T.muted, cursor: 'pointer', padding: 4 }} onClick={() => setActiveMsgMenu(activeMsgMenu === m.id ? null : m.id)}><MoreVertical size={15} /></button>
                                                    <AnimatePresence>
                                                        {activeMsgMenu === m.id && (
                                                            <>
                                                                <div style={{ position: 'fixed', inset: 0, zIndex: 100 }} onClick={() => setActiveMsgMenu(null)} />
                                                                <motion.div initial={{ opacity: 0, scale: 0.9, y: 10 }} animate={{ opacity: 1, scale: 1, y: 0 }} exit={{ opacity: 0, scale: 0.9, y: 10 }}
                                                                    style={{ position: 'absolute', bottom: '100%', left: 0, marginBottom: 8, zIndex: 101, width: 200, background: T.surf, backdropFilter: 'blur(10px)', border: `1px solid ${T.border}`, borderRadius: 14, padding: 6, boxShadow: `0 10px 40px rgba(0,0,0,0.3)`, overflow: 'hidden' }}>
                                                                    {[{ icon: Volume2, label: 'Leer en voz alta', fn: () => speak(m.content) },
                                                                      { icon: Edit, label: 'Editar', fn: () => setInput(m.content) },
                                                                      { icon: FolderInput, label: 'Rama en nuevo chat', fn: () => createConv(`Rama: ${m.content.slice(0, 30)}...`) }
                                                                    ].map((item, i) => (
                                                                        <button key={i} onClick={() => { item.fn(); setActiveMsgMenu(null); }}
                                                                            style={{ width: '100%', display: 'flex', alignItems: 'center', gap: 10, padding: '10px 12px', background: 'none', border: 'none', color: T.text, fontSize: 13, cursor: 'pointer', borderRadius: 8, textAlign: 'left', transition: 'background 0.15s' }}
                                                                            onMouseEnter={(e) => e.currentTarget.style.background = `${accent}08`}
                                                                            onMouseLeave={(e) => e.currentTarget.style.background = 'none'}>
                                                                            <item.icon size={15} color={accent} /> {item.label}
                                                                        </button>
                                                                    ))}
                                                                </motion.div>
                                                            </>
                                                        )}
                                                    </AnimatePresence>
                                                </div>
                                            </div>
                                        )}
                                    </motion.div>
                                ))}
                                <div ref={endRef} style={{ height: 16 }} />
                            </div>
                        )}
                    </div>

                    {/* ─── Scroll-to-bottom button ─── */}
                    <AnimatePresence>
                        {showScrollBtn && (
                            <motion.button
                                initial={{ opacity: 0, scale: 0.8, y: 10 }}
                                animate={{ opacity: 1, scale: 1, y: 0 }}
                                exit={{ opacity: 0, scale: 0.8, y: 10 }}
                                transition={{ type: 'spring', stiffness: 300, damping: 25 }}
                                aria-label="Ir al último mensaje"
                                onClick={() => { userScrolledRef.current = false; setShowScrollBtn(false); setUnreadCount(0); scrollToBottom(); }}
                                style={{
                                    position: 'absolute', bottom: 160, left: '50%', transform: 'translateX(-50%)', zIndex: 50,
                                    display: 'flex', alignItems: 'center', gap: 8,
                                    padding: '8px 16px', borderRadius: 20,
                                    background: T.surf, border: `1px solid ${T.border}`,
                                    color: accent, fontSize: 12, fontWeight: 600,
                                    cursor: 'pointer', boxShadow: `0 4px 20px rgba(0,0,0,0.3)`,
                                    backdropFilter: 'blur(10px)',
                                }}>
                                <ChevronDown size={16} />
                                {unreadCount > 0 ? `${unreadCount} nuevo${unreadCount > 1 ? 's' : ''}` : 'Ir abajo'}
                            </motion.button>
                        )}
                    </AnimatePresence>

                    {/* ─── File preview ─── */}
                    {attachedFiles.length > 0 && (
                        <div style={{ display: 'flex', gap: 10, padding: '10px 16px', overflowX: 'auto', background: T.surf, borderTop: `1px solid ${T.border}` }}>
                            {attachedFiles.map(f => (
                                <div key={f.id} style={{ position: 'relative', width: 64, height: 64, borderRadius: 12, overflow: 'hidden', border: `1px solid ${T.border}`, background: T.bg, flexShrink: 0 }}>
                                    {f.preview ? <img src={f.preview} style={{ width: '100%', height: '100%', objectFit: 'cover' }} /> : <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}><File size={20} color={T.muted} /></div>}
                                    <button onClick={() => setAttachedFiles(p => p.filter(x => x.id !== f.id))} style={{ position: 'absolute', top: 2, right: 2, width: 20, height: 20, borderRadius: '50%', background: 'rgba(0,0,0,0.7)', border: 'none', color: '#ef4444', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer' }}><X size={12} /></button>
                                </div>
                            ))}
                        </div>
                    )}

                    {/* ─── Analyzing image indicator ─── */}
                    {analyzingImage && (
                        <div style={{
                            display: 'flex', alignItems: 'center', gap: 10, padding: '10px 16px',
                            background: `${accent}08`, borderTop: `1px solid ${T.border}`
                        }}>
                            <Loader2 size={16} color={accent} style={{ animation: 'nexa-spin 1s linear infinite' }} />
                            <span style={{ fontSize: 13, color: accent, fontWeight: 500 }}>Analizando imagen...</span>
                        </div>
                    )}

                    {/* ─── Input area ─── */}
                    <div role="region" aria-label="Área de entrada" className="nexa-input-area" style={{
                        borderTop: `1px solid ${T.border}`,
                        background: `${T.bg}CC`, backdropFilter: 'blur(20px)',
                        padding: '12px 16px 20px', flexShrink: 0, zIndex: 20,
                        transition: 'background 0.3s, border-color 0.3s',
                    }}>
                        <div style={{ maxWidth: 700, margin: '0 auto' }}>
                            <div style={{
                                display: 'flex', alignItems: 'flex-end', gap: 6,
                                background: T.inputBg, border: `1px solid ${T.border}`,
                                borderRadius: 28, padding: '4px 6px 4px 4px',
                                boxShadow: `0 4px 24px rgba(0,0,0,${resolvedTheme === 'light' ? '0.04' : '0.3'})`,
                                transition: 'border-color 0.3s, box-shadow 0.3s',
                            }}>
                                <FileUpload isOpen={showUpload} onClose={() => setShowUpload(false)} onFilesSelected={(files) => setAttachedFiles(p => [...p, ...files])} onAnalyzeImage={analyzeImage} theme={T} />

                                <button aria-label="Adjuntar" onClick={() => setShowUpload(!showUpload)}
                                    style={{ width: 38, height: 38, borderRadius: '50%', flexShrink: 0, border: 'none', background: `${accent}10`, color: accent, display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', transition: 'all 0.2s' }}>
                                    <Plus size={18} />
                                </button>

                                <div style={{ flex: 1, position: 'relative' }}>
                                    {input && suggestion && (
                                        <div style={{ position: 'absolute', left: 12, top: 10, color: T.muted, opacity: 0.3, pointerEvents: 'none', fontSize: 15, whiteSpace: 'pre-wrap' }}>
                                            <span style={{ opacity: 0 }}>{input}</span>{suggestion}
                                        </div>
                                    )}
                                    <textarea ref={inputRef} id="nexa-chat-input" aria-label="Escribe un mensaje" value={input}
                                        onChange={e => setInput(e.target.value)}
                                        onKeyDown={e => {
                                            if (e.key === 'Tab' && suggestion) { e.preventDefault(); setInput(input + suggestion); setSuggestion(''); }
                                            if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(); }
                                        }}
                                        placeholder={recording ? 'Escuchando...' : 'Escribe un mensaje...'} rows={1}
                                        style={{
                                            width: '100%', resize: 'none', background: 'transparent', border: 'none',
                                            color: T.text, outline: 'none', padding: '10px 12px', fontSize: 15,
                                            maxHeight: 150, lineHeight: 1.5, boxSizing: 'border-box', fontFamily: 'inherit',
                                        }} />
                                </div>

                                <div style={{ display: 'flex', alignItems: 'center', gap: 3, marginBottom: 2 }}>
                                    <button aria-label={recording ? 'Detener' : 'Voz'} onClick={toggleRec}
                                        className="nexa-voice-btn"
                                        style={{
                                            width: 36, height: 36, borderRadius: '50%',
                                            background: recording ? '#ef444420' : 'transparent',
                                            color: recording ? '#ef4444' : T.muted,
                                            border: recording ? '1px solid #ef444440' : 'none',
                                            cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center',
                                            transition: 'all 0.2s',
                                            animation: recording ? 'pulse 1.5s ease-in-out infinite' : 'none',
                                        }}>
                                        <Mic size={17} />
                                    </button>

                                    <button aria-label="Enviar" onClick={() => send()}
                                        disabled={(!input.trim() && attachedFiles.length === 0) || thinking || streaming}
                                        className="glow-btn nexa-send-btn"
                                        style={{
                                            width: 36, height: 36, borderRadius: '50%', flexShrink: 0,
                                            background: (input.trim() || attachedFiles.length > 0) && !thinking && !streaming ? accent : `${T.muted}20`,
                                            color: (input.trim() || attachedFiles.length > 0) && !thinking && !streaming ? '#000' : T.muted,
                                            border: 'none', display: 'flex', alignItems: 'center', justifyContent: 'center',
                                            transition: 'all 0.3s', cursor: 'pointer',
                                            boxShadow: (input.trim() || attachedFiles.length > 0) && !thinking && !streaming ? `0 0 12px ${preset.glow}` : 'none',
                                        }}>
                                        {thinking ? <Loader2 size={16} style={{ animation: 'nexa-spin 1s linear infinite' }} /> : <ArrowUp size={16} />}
                                    </button>
                                </div>
                            </div>

                            {/* Keyboard hints */}
                            <div className="nexa-keyboard-hints" style={{ display: 'flex', justifyContent: 'center', gap: 16, marginTop: 8, opacity: 0.3 }}>
                                <span style={{ fontSize: 10, color: T.muted }}>Enter enviar</span>
                                <span style={{ fontSize: 10, color: T.muted }}>Shift+Enter nueva línea</span>
                                <span style={{ fontSize: 10, color: T.muted }}>Tab autocompletar</span>
                            </div>
                        </div>
                    </div>
                </main>

            <SettingsPanel 
                isOpen={showSettings} 
                onClose={() => setShowSettings(false)} 
                theme={themeName} 
                onThemeChange={(t: any) => setThemeName(t)} 
                locale={lang} 
                onLocaleChange={(l: any) => setLang(l)} 
                activeProvider={activeProvider}
            />

            <style>{`textarea::-webkit-scrollbar { width: 0px; }`}</style>
        </div>
    );
}

// ═══════════════════════════════════════════
//  FILE UPLOAD COMPONENT
// ═══════════════════════════════════════════

function FileUpload({ isOpen, onClose, onFilesSelected, onAnalyzeImage, theme: T }: { isOpen: boolean; onClose: () => void; onFilesSelected: (files: UploadedFile[]) => void; onAnalyzeImage?: (file: File) => Promise<void>; theme: any }) {
    const fileRef = useRef<HTMLInputElement>(null);
    const camRef = useRef<HTMLInputElement>(null);
    const vidRef = useRef<HTMLInputElement>(null);
    const [activeType, setActiveType] = useState<FileType | null>(null);

    const processFiles = async (files: FileList, type: FileType) => {
        const config = FILE_TYPES[type];
        const processed: UploadedFile[] = [];
        for (const file of Array.from(files)) {
            if (file.size > config.maxSize) continue;
            try {
                const result = await new Promise<{ data: string; preview?: string }>((res, rej) => {
                    const reader = new FileReader();
                    reader.onload = () => {
                        const r = reader.result as string;
                        res(type === 'image' || type === 'camera' ? { data: r.split(',')[1], preview: r } : { data: r.split(',')[1] });
                    };
                    reader.onerror = rej;
                    reader.readAsDataURL(file);
                });
                processed.push({ id: `f-${Date.now()}-${Math.random()}`, type, name: file.name, size: file.size, preview: result.preview, data: result.data, mimeType: file.type || 'application/octet-stream' });
            } catch {}
        }
        if (processed.length > 0) {
            if ((type === 'image' || type === 'camera') && onAnalyzeImage) {
                onClose();
                for (const f of processed) {
                    const byteString = atob(f.data);
                    const ab = new ArrayBuffer(byteString.length);
                    const ia = new Uint8Array(ab);
                    for (let i = 0; i < byteString.length; i++) ia[i] = byteString.charCodeAt(i);
                    const blob = new Blob([ab], { type: f.mimeType });
                    await onAnalyzeImage(new (File as any)([blob], f.name, { type: f.mimeType }));
                }
                return;
            }
            onFilesSelected(processed); onClose();
        }
    };

    return (
        <AnimatePresence>
            {isOpen && (
                <div style={{ position: 'absolute', bottom: 56, left: 0, zIndex: 70, width: 230 }}>
                    <motion.div initial={{ opacity: 0, y: 10, scale: 0.95 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, y: 10, scale: 0.95 }}
                        style={{ position: 'relative', width: '100%', background: T.surf, backdropFilter: 'blur(10px)', border: `1px solid ${T.border}`, borderRadius: 20, padding: 6, boxShadow: `0 20px 50px rgba(0,0,0,0.5)` }}>
                        {(Object.entries(FILE_TYPES) as [FileType, any][]).map(([key, config]) => (
                            <button key={key} onClick={() => { setActiveType(key); if (key === 'camera') camRef.current?.click(); else if (key === 'video') vidRef.current?.click(); else { if (fileRef.current) { fileRef.current.accept = config.accept; fileRef.current.click(); } } }}
                                style={{ width: '100%', display: 'flex', alignItems: 'center', gap: 12, padding: '10px 14px', background: 'none', border: 'none', cursor: 'pointer', borderRadius: 14, transition: '0.2s' }}
                                onMouseEnter={(e) => e.currentTarget.style.background = `${T.text}08`}
                                onMouseLeave={(e) => e.currentTarget.style.background = 'none'}>
                                <div style={{ width: 34, height: 34, borderRadius: '50%', background: T.bg, border: `1px solid ${config.color}40`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                                    {<config.icon size={16} color={config.color} />}
                                </div>
                                <span style={{ fontSize: 13, fontWeight: 500, color: T.text }}>{config.label}</span>
                            </button>
                        ))}
                    </motion.div>
                    <input ref={fileRef} type="file" multiple hidden onChange={(e) => { if (e.target.files && activeType) processFiles(e.target.files, activeType); }} />
                    <input ref={camRef} type="file" accept="image/*" capture="environment" hidden onChange={(e) => { if (e.target.files) processFiles(e.target.files, 'camera'); }} />
                    <input ref={vidRef} type="file" accept="video/*" capture="environment" hidden onChange={(e) => { if (e.target.files) processFiles(e.target.files, 'video'); }} />
                </div>
            )}
        </AnimatePresence>
    );
}
