'use client';
import React, { useState, useRef, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { 
    Terminal, Play, Code2, Globe, Rocket, 
    Database, Cpu, Send, Loader2, ArrowLeft, 
    FileCode2, FolderTree, Sparkles, CheckCircle2, ChevronRight
} from 'lucide-react';

export default function DeveloperMode() {
    const [prompt, setPrompt] = useState('');
    const [messages, setMessages] = useState<{role: string, content: string}[]>([]);
    const [isThinking, setIsThinking] = useState(false);
    const [activeTab, setActiveTab] = useState<'preview' | 'code' | 'terminal'>('terminal');
    const [terminalOutput, setTerminalOutput] = useState<string[]>(['[NEXA OS] Inicializando motor de desarrollo...', '[NEXA OS] Conectado a MCPs (Filesystem, Vercel, Supabase)...', '[NEXA OS] Listo para recibir instrucciones.']);
    
    const endRef = useRef<HTMLDivElement>(null);

    const scrollToBottom = () => {
        endRef.current?.scrollIntoView({ behavior: 'smooth' });
    };

    useEffect(() => {
        scrollToBottom();
    }, [messages, terminalOutput]);

    const handleSend = async () => {
        if (!prompt.trim() || isThinking) return;
        
        const userMsg = { role: 'user', content: prompt };
        setMessages(prev => [...prev, userMsg]);
        setPrompt('');
        setIsThinking(true);
        setActiveTab('terminal');

        // Add real processing log
        setTerminalOutput(prev => [...prev, `> Analizando requerimientos: "${prompt.trim()}"`]);

        try {
            // Call real backend API
            const res = await fetch('/api/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    messages: [...messages, userMsg].map(m => ({ role: m.role, content: m.content })),
                    stream: true,
                }),
            });

            setTerminalOutput(prev => [...prev, `> Conectado al servidor NEXA AI...`]);

            if (!res.ok) {
                const err = await res.json().catch(() => ({}));
                throw new Error(err.error || `Error ${res.status}`);
            }

            const reader = res.body?.getReader();
            const dec = new TextDecoder();
            let full = '';
            let buffer = '';

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
                            if (d.error) {
                                setTerminalOutput(prev => [...prev, `> Error: ${d.error}`]);
                            }
                            if (d.text) {
                                full += d.text;
                            }
                            if (d.provider) {
                                setTerminalOutput(prev => [...prev, `> Proveedor: ${d.provider}`]);
                            }
                        } catch {}
                    }
                }
            }

            if (full) {
                setTerminalOutput(prev => [...prev, `> ✅ Respuesta generada exitosamente.`]);
                setMessages(prev => [...prev, { role: 'assistant', content: full }]);
                setActiveTab('code');
            } else {
                throw new Error('No se recibió respuesta del servidor.');
            }
        } catch (e: any) {
            setTerminalOutput(prev => [...prev, `> ❌ Error: ${e.message}`]);
            setMessages(prev => [...prev, { 
                role: 'assistant', 
                content: `Error: ${e.message}` 
            }]);
        } finally {
            setIsThinking(false);
        }
    };

    return (
        <div style={{
            position: 'fixed', inset: 0, background: '#050505', color: '#f0f0f0',
            fontFamily: "'Inter', sans-serif", display: 'flex', flexDirection: 'column'
        }}>
            {/* Header */}
            <header style={{
                display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                padding: '12px 20px', borderBottom: '1px solid #1a1a1a', background: '#0a0a0a', zIndex: 10
            }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
                    <a href="/" style={{ color: '#888', textDecoration: 'none', display: 'flex', alignItems: 'center', transition: 'color 0.2s' }} onMouseEnter={e => e.currentTarget.style.color = '#fff'} onMouseLeave={e => e.currentTarget.style.color = '#888'}>
                        <ArrowLeft size={20} />
                    </a>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <div style={{ width: 32, height: 32, borderRadius: 8, background: '#00e5a015', border: '1px solid #00e5a030', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                            <Cpu size={18} color="#00e5a0" />
                        </div>
                        <div>
                            <span style={{ fontSize: 14, fontWeight: 800, letterSpacing: 1.5, display: 'block', lineHeight: 1.1 }}>NEXA</span>
                            <span style={{ fontSize: 9, color: '#00e5a0', letterSpacing: 1, fontWeight: 600, textTransform: 'uppercase' }}>Developer SOLO Mode</span>
                        </div>
                    </div>
                </div>
                
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                    <button style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '6px 14px', borderRadius: 8, background: '#111', border: '1px solid #222', color: '#aaa', fontSize: 12, fontWeight: 500, cursor: 'pointer' }}>
                        <Database size={14} /> Supabase
                    </button>
                    <button style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '6px 14px', borderRadius: 8, background: '#fff', color: '#000', fontSize: 12, fontWeight: 600, cursor: 'pointer' }}>
                        <Rocket size={14} /> Deploy a Vercel
                    </button>
                </div>
            </header>

            {/* Layout Principal */}
            <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
                
                {/* Panel Izquierdo - Chat del Agente */}
                <div style={{ width: '35%', minWidth: 350, borderRight: '1px solid #1a1a1a', display: 'flex', flexDirection: 'column', background: '#0a0a0a' }}>
                    <div style={{ padding: '16px 20px', borderBottom: '1px solid #1a1a1a', display: 'flex', alignItems: 'center', gap: 8 }}>
                        <Sparkles size={16} color="#00e5a0" />
                        <span style={{ fontSize: 13, fontWeight: 600, color: '#e0e0e0', textTransform: 'uppercase', letterSpacing: 1 }}>Agente Autónomo</span>
                    </div>
                    
                    <div style={{ flex: 1, overflowY: 'auto', padding: 20, display: 'flex', flexDirection: 'column', gap: 16 }}>
                        {messages.length === 0 ? (
                            <div style={{ textAlign: 'center', color: '#666', marginTop: 40, fontSize: 13 }}>
                                <Cpu size={32} style={{ margin: '0 auto 12px', opacity: 0.5 }} />
                                Soy Nexa Developer.<br />Descríbeme la aplicación que quieres construir y yo me encargaré del código, base de datos y despliegue.
                            </div>
                        ) : (
                            messages.map((m, i) => (
                                <div key={i} style={{ display: 'flex', flexDirection: 'column', alignItems: m.role === 'user' ? 'flex-end' : 'flex-start' }}>
                                    <div style={{ 
                                        maxWidth: '90%', padding: '12px 16px', borderRadius: 12, fontSize: 13, lineHeight: 1.5,
                                        background: m.role === 'user' ? '#111' : '#00e5a008',
                                        border: m.role === 'user' ? '1px solid #222' : '1px solid #00e5a020',
                                        color: m.role === 'user' ? '#fff' : '#e0e0e0',
                                        whiteSpace: 'pre-wrap'
                                    }}>
                                        {m.content}
                                    </div>
                                </div>
                            ))
                        )}
                        {isThinking && (
                            <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: 12, color: '#00e5a0', fontSize: 12 }}>
                                <Loader2 size={14} style={{ animation: 'spin 1s linear infinite' }} />
                                <span>Construyendo aplicación...</span>
                            </div>
                        )}
                        <div ref={endRef} />
                    </div>

                    <div style={{ padding: 20, borderTop: '1px solid #1a1a1a', background: '#0a0a0a' }}>
                        <div style={{ display: 'flex', background: '#111', border: '1px solid #222', borderRadius: 12, padding: '4px' }}>
                            <textarea 
                                value={prompt}
                                onChange={e => setPrompt(e.target.value)}
                                onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend(); } }}
                                placeholder="Construye un clon de Twitter con Next.js y Tailwind..."
                                rows={3}
                                style={{ flex: 1, background: 'transparent', border: 'none', color: '#fff', fontSize: 13, padding: '12px 16px', outline: 'none', resize: 'none' }}
                            />
                            <button 
                                onClick={handleSend}
                                disabled={!prompt.trim() || isThinking}
                                style={{ margin: 4, width: 40, background: prompt.trim() ? '#00e5a0' : '#222', color: prompt.trim() ? '#000' : '#555', border: 'none', borderRadius: 8, display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: prompt.trim() ? 'pointer' : 'default', transition: 'all 0.2s' }}
                            >
                                <Send size={16} />
                            </button>
                        </div>
                    </div>
                </div>

                {/* Panel Derecho - Entorno */}
                <div style={{ flex: 1, display: 'flex', flexDirection: 'column', background: '#000' }}>
                    {/* Tabs */}
                    <div style={{ display: 'flex', borderBottom: '1px solid #1a1a1a', background: '#0a0a0a' }}>
                        <button onClick={() => setActiveTab('terminal')} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '12px 20px', background: activeTab === 'terminal' ? '#111' : 'transparent', border: 'none', borderTop: `2px solid ${activeTab === 'terminal' ? '#00e5a0' : 'transparent'}`, color: activeTab === 'terminal' ? '#fff' : '#666', fontSize: 12, fontWeight: 600, cursor: 'pointer' }}>
                            <Terminal size={14} /> Terminal
                        </button>
                        <button onClick={() => setActiveTab('code')} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '12px 20px', background: activeTab === 'code' ? '#111' : 'transparent', border: 'none', borderTop: `2px solid ${activeTab === 'code' ? '#00e5a0' : 'transparent'}`, color: activeTab === 'code' ? '#fff' : '#666', fontSize: 12, fontWeight: 600, cursor: 'pointer' }}>
                            <Code2 size={14} /> Código Fuente
                        </button>
                        <button onClick={() => setActiveTab('preview')} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '12px 20px', background: activeTab === 'preview' ? '#111' : 'transparent', border: 'none', borderTop: `2px solid ${activeTab === 'preview' ? '#00e5a0' : 'transparent'}`, color: activeTab === 'preview' ? '#fff' : '#666', fontSize: 12, fontWeight: 600, cursor: 'pointer' }}>
                            <Globe size={14} /> Previsualización
                        </button>
                    </div>

                    {/* Contenido del Tab */}
                    <div style={{ flex: 1, position: 'relative', overflow: 'auto' }}>
                        {activeTab === 'terminal' && (
                            <div style={{ padding: 20, fontFamily: "'JetBrains Mono', monospace", fontSize: 12, color: '#00e5a0', lineHeight: 1.6 }}>
                                {terminalOutput.map((log, i) => (
                                    <div key={i} style={{ marginBottom: 4, opacity: log.includes('Error') ? 1 : 0.8, color: log.includes('Error') ? '#ef4444' : log.includes('🚀') ? '#a855f7' : '#00e5a0' }}>
                                        {log}
                                    </div>
                                ))}
                            </div>
                        )}

                        {activeTab === 'code' && (
                            <div style={{ display: 'flex', height: '100%' }}>
                                <div style={{ width: 220, borderRight: '1px solid #1a1a1a', background: '#0a0a0a', padding: '16px 12px' }}>
                                    <div style={{ fontSize: 10, fontWeight: 700, color: '#666', textTransform: 'uppercase', marginBottom: 12, letterSpacing: 1 }}>Archivos Generados</div>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: '#ccc', fontSize: 12, padding: '6px 8px', background: '#111', borderRadius: 6, cursor: 'pointer' }}>
                                        <FileCode2 size={14} color="#3178c6" /> app/page.tsx
                                    </div>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: '#888', fontSize: 12, padding: '6px 8px', cursor: 'pointer' }}>
                                        <FileCode2 size={14} color="#3178c6" /> app/api/route.ts
                                    </div>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: '#888', fontSize: 12, padding: '6px 8px', cursor: 'pointer' }}>
                                        <Database size={14} color="#00e5a0" /> schema.sql
                                    </div>
                                </div>
                                <div style={{ flex: 1, padding: 20, background: '#050505', fontFamily: "'JetBrains Mono', monospace", fontSize: 13, color: '#e0e0e0', whiteSpace: 'pre-wrap', overflow: 'auto' }}>
                                    {messages.length > 0 && !isThinking ? 
                                    messages.filter(m => m.role === 'assistant').slice(-1)[0]?.content || '// Sin respuesta aún'
                                    : '// Esperando instrucciones para generar código...'}
                                </div>
                            </div>
                        )}

                        {activeTab === 'preview' && (
                            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', background: '#fff' }}>
                                {messages.length > 0 && !isThinking ? (
                                    <div style={{ textAlign: 'center', color: '#000', padding: 40, border: '1px solid #ddd', borderRadius: 12, boxShadow: '0 10px 30px rgba(0,0,0,0.1)' }}>
                                        <h1 style={{ fontSize: 24, fontWeight: 'bold', marginBottom: 12 }}>App Autogenerada por Nexa</h1>
                                        <p style={{ color: '#555' }}>Este código fue creado mediante el Modo Developer.</p>
                                        <button style={{ marginTop: 20, padding: '10px 20px', background: '#000', color: '#fff', borderRadius: 6, border: 'none', cursor: 'pointer' }}>Botón de prueba</button>
                                    </div>
                                ) : (
                                    <div style={{ color: '#aaa', fontSize: 14 }}>La previsualización aparecerá aquí cuando termine la generación.</div>
                                )}
                            </div>
                        )}
                    </div>
                </div>
            </div>

            <style>{`
                @keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
                ::-webkit-scrollbar { width: 6px; }
                ::-webkit-scrollbar-track { background: transparent; }
                ::-webkit-scrollbar-thumb { background: #222; border-radius: 3px; }
                ::-webkit-scrollbar-thumb:hover { background: #333; }
            `}</style>
        </div>
    );
}
