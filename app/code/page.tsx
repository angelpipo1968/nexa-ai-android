'use client';
import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
    Code2, Play, Copy, Check, Loader2, ChevronDown,
    Zap, ArrowLeft, Sparkles, Terminal, FileCode2
} from 'lucide-react';

const LANGUAGES = [
    { id: 'python', label: 'Python', emoji: '🐍', color: '#3776ab' },
    { id: 'javascript', label: 'JavaScript', emoji: '⚡', color: '#f7df1e' },
    { id: 'typescript', label: 'TypeScript', emoji: '🔷', color: '#3178c6' },
    { id: 'html', label: 'HTML/CSS', emoji: '🌐', color: '#e34f26' },
    { id: 'react', label: 'React', emoji: '⚛️', color: '#61dafb' },
    { id: 'sql', label: 'SQL', emoji: '🗄️', color: '#4479a1' },
    { id: 'go', label: 'Go', emoji: '🔵', color: '#00add8' },
    { id: 'rust', label: 'Rust', emoji: '🦀', color: '#dea584' },
    { id: 'java', label: 'Java', emoji: '☕', color: '#ed8b00' },
    { id: 'cpp', label: 'C++', emoji: '⚙️', color: '#00599c' },
    { id: 'bash', label: 'Bash', emoji: '🖥️', color: '#4eaa25' },
    { id: 'php', label: 'PHP', emoji: '🐘', color: '#777bb4' },
];

const TEMPLATES = [
    { lang: 'python', prompt: 'Script que lee un CSV, filtra datos y exporta un reporte', icon: '📊' },
    { lang: 'python', prompt: 'API REST con Flask que maneja CRUD de usuarios', icon: '🔌' },
    { lang: 'javascript', prompt: 'Función que hace fetch a una API y pagina los resultados', icon: '📡' },
    { lang: 'typescript', prompt: 'Clase genérica de caché con TTL y LRU eviction', icon: '💾' },
    { lang: 'react', prompt: 'Componente de tabla con sorting, filtro y paginación', icon: '📋' },
    { lang: 'html', prompt: 'Landing page responsive con animaciones CSS', icon: '🎨' },
    { lang: 'sql', prompt: 'Query optimizado con joins, CTEs y window functions', icon: '🔍' },
    { lang: 'bash', prompt: 'Script de backup automático con rotación y compresión', icon: '📦' },
];

export default function CodePage() {
    const [prompt, setPrompt] = useState('');
    const [language, setLanguage] = useState('python');
    const [showLangPicker, setShowLangPicker] = useState(false);
    const [generating, setGenerating] = useState(false);
    const [result, setResult] = useState('');
    const [provider, setProvider] = useState('');
    const [copied, setCopied] = useState(false);
    const [error, setError] = useState('');
    const [history, setHistory] = useState<{ prompt: string; lang: string; code: string }[]>([]);

    const selectedLang = LANGUAGES.find(l => l.id === language) || LANGUAGES[0];

    const generate = async () => {
        if (!prompt.trim() || generating) return;
        setGenerating(true);
        setError('');
        setResult('');
        setProvider('');

        try {
            const res = await fetch('/api/code', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ prompt: prompt.trim(), language }),
            });

            if (!res.ok) {
                const data = await res.json().catch(() => ({}));
                throw new Error(data.error || `Error ${res.status}`);
            }

            const data = await res.json();
            setResult(data.code || '');
            setProvider(data.provider || '');
            setHistory(prev => [{ prompt: prompt.trim(), lang: language, code: data.code || '' }, ...prev].slice(0, 10));
        } catch (e: any) {
            setError(e.message || 'Error generando código');
        } finally {
            setGenerating(false);
        }
    };

    const copyCode = () => {
        navigator.clipboard.writeText(result);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
    };

    const detectLanguage = (code: string): string => {
        if (code.includes('def ') || (code.includes('import ') && code.includes('print('))) return 'python';
        if (code.includes('const ') || code.includes('=>') || code.includes('console.log')) return 'javascript';
        if (code.includes('interface ') || code.includes(': string') || code.includes(': number')) return 'typescript';
        if (code.includes('<div') || code.includes('<html') || code.includes('className')) return 'html';
        if (code.includes('SELECT ') || code.includes('FROM ') || code.includes('WHERE ')) return 'sql';
        if (code.includes('func ') || code.includes('package ')) return 'go';
        if (code.includes('fn ') || code.includes('let mut ')) return 'rust';
        return 'plaintext';
    };

    return (
        <div style={{
            position: 'fixed', inset: 0, background: '#0a0a0a', color: '#f0f0f0',
            fontFamily: "'Inter', sans-serif", display: 'flex', flexDirection: 'column', overflow: 'hidden'
        }}>
            {/* Header */}
            <header style={{
                display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                padding: '12px 20px', borderBottom: '1px solid #1f1f1f', flexShrink: 0, zIndex: 30
            }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                    <a href="/" style={{
                        width: 36, height: 36, borderRadius: 10, background: '#ffffff08',
                        border: '1px solid #ffffff15', display: 'flex', alignItems: 'center',
                        justifyContent: 'center', color: '#888', textDecoration: 'none',
                        transition: 'all 0.2s'
                    }}>
                        <ArrowLeft size={18} />
                    </a>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <div style={{
                            width: 32, height: 32, borderRadius: 8, background: '#a855f715',
                            border: '1px solid #a855f730', display: 'flex', alignItems: 'center', justifyContent: 'center'
                        }}>
                            <Code2 size={16} color="#a855f7" />
                        </div>
                        <div>
                            <span style={{ fontSize: 14, fontWeight: 800, letterSpacing: 1.5, display: 'block', lineHeight: 1.1 }}>NEXA</span>
                            <span style={{ fontSize: 8, color: '#a855f7', letterSpacing: 0.8, fontWeight: 600, textTransform: 'uppercase' }}>CODE GEN</span>
                        </div>
                    </div>
                </div>

                {provider && (
                    <div style={{
                        fontSize: 10, color: '#666', background: '#ffffff08',
                        padding: '4px 10px', borderRadius: 8, border: '1px solid #ffffff10'
                    }}>
                        Powered by {provider}
                    </div>
                )}
            </header>

            {/* Main content */}
            <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
                {/* Left panel — Input */}
                <div style={{
                    width: '40%', minWidth: 340, display: 'flex', flexDirection: 'column',
                    borderRight: '1px solid #1f1f1f', overflow: 'hidden'
                }}>
                    <div style={{ flex: 1, overflowY: 'auto', padding: 24 }}>
                        {/* Language selector */}
                        <label style={{ fontSize: 12, fontWeight: 600, color: '#888', textTransform: 'uppercase', letterSpacing: 1.5, marginBottom: 8, display: 'block' }}>
                            Lenguaje
                        </label>
                        <div style={{ position: 'relative', marginBottom: 24 }}>
                            <button
                                onClick={() => setShowLangPicker(!showLangPicker)}
                                style={{
                                    width: '100%', display: 'flex', alignItems: 'center', gap: 10,
                                    padding: '12px 16px', borderRadius: 12, background: '#141414',
                                    border: '1px solid #1f1f1f', color: '#f0f0f0', fontSize: 14,
                                    cursor: 'pointer', transition: 'all 0.2s'
                                }}>
                                <span style={{ fontSize: 18 }}>{selectedLang.emoji}</span>
                                <span style={{ flex: 1, textAlign: 'left' }}>{selectedLang.label}</span>
                                <ChevronDown size={16} color="#666" style={{
                                    transform: showLangPicker ? 'rotate(180deg)' : 'none',
                                    transition: 'transform 0.2s'
                                }} />
                            </button>
                            <AnimatePresence>
                                {showLangPicker && (
                                    <motion.div
                                        initial={{ opacity: 0, y: -8, scale: 0.97 }}
                                        animate={{ opacity: 1, y: 0, scale: 1 }}
                                        exit={{ opacity: 0, y: -8, scale: 0.97 }}
                                        style={{
                                            position: 'absolute', top: '100%', left: 0, right: 0, marginTop: 4,
                                            zIndex: 50, background: '#141414', border: '1px solid #1f1f1f',
                                            borderRadius: 14, padding: 6, boxShadow: '0 16px 48px rgba(0,0,0,0.5)',
                                            display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 4
                                        }}>
                                        {LANGUAGES.map(lang => (
                                            <button key={lang.id} onClick={() => { setLanguage(lang.id); setShowLangPicker(false); }}
                                                style={{
                                                    display: 'flex', alignItems: 'center', gap: 6,
                                                    padding: '8px 10px', borderRadius: 8, background: language === lang.id ? `${lang.color}15` : 'transparent',
                                                    border: language === lang.id ? `1px solid ${lang.color}40` : '1px solid transparent',
                                                    color: language === lang.id ? lang.color : '#888', fontSize: 12, cursor: 'pointer',
                                                    transition: 'all 0.15s', fontWeight: language === lang.id ? 600 : 400
                                                }}>
                                                <span>{lang.emoji}</span> {lang.label}
                                            </button>
                                        ))}
                                    </motion.div>
                                )}
                            </AnimatePresence>
                        </div>

                        {/* Prompt input */}
                        <label style={{ fontSize: 12, fontWeight: 600, color: '#888', textTransform: 'uppercase', letterSpacing: 1.5, marginBottom: 8, display: 'block' }}>
                            ¿Qué querés crear?
                        </label>
                        <textarea
                            value={prompt}
                            onChange={e => setPrompt(e.target.value)}
                            onKeyDown={e => { if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) { e.preventDefault(); generate(); } }}
                            placeholder={`Ej: "Función en ${selectedLang.label} que..."`}
                            rows={6}
                            style={{
                                width: '100%', resize: 'vertical', background: '#141414', border: '1px solid #1f1f1f',
                                borderRadius: 14, padding: 14, color: '#f0f0f0', fontSize: 14, lineHeight: 1.6,
                                outline: 'none', fontFamily: 'inherit', minHeight: 120,
                                transition: 'border-color 0.2s',
                                boxSizing: 'border-box'
                            }}
                            onFocus={e => e.currentTarget.style.borderColor = '#a855f740'}
                            onBlur={e => e.currentTarget.style.borderColor = '#1f1f1f'}
                        />
                        <div style={{ fontSize: 11, color: '#555', marginTop: 6 }}>
                            Ctrl+Enter para generar
                        </div>

                        {/* Generate button */}
                        <motion.button
                            whileHover={{ scale: 1.01 }}
                            whileTap={{ scale: 0.98 }}
                            onClick={generate}
                            disabled={!prompt.trim() || generating}
                            style={{
                                width: '100%', marginTop: 16, padding: '14px 20px', borderRadius: 14,
                                background: prompt.trim() && !generating
                                    ? 'linear-gradient(135deg, #a855f7, #6366f1)'
                                    : '#1f1f1f',
                                color: prompt.trim() && !generating ? '#fff' : '#555',
                                border: 'none', fontSize: 15, fontWeight: 700, cursor: 'pointer',
                                display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
                                transition: 'all 0.3s', fontFamily: 'inherit',
                                boxShadow: prompt.trim() && !generating ? '0 4px 20px rgba(168,85,247,0.3)' : 'none'
                            }}>
                            {generating ? (
                                <><Loader2 size={18} style={{ animation: 'nexa-spin 1s linear infinite' }} /> Generando...</>
                            ) : (
                                <><Sparkles size={18} /> Generar código</>
                            )}
                        </motion.button>

                        {/* Templates */}
                        <div style={{ marginTop: 28 }}>
                            <div style={{ fontSize: 12, fontWeight: 600, color: '#888', textTransform: 'uppercase', letterSpacing: 1.5, marginBottom: 12 }}>
                                Plantillas rápidas
                            </div>
                            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                                {TEMPLATES.filter(t => t.lang === language).map((t, i) => (
                                    <button key={i} onClick={() => setPrompt(t.prompt)}
                                        style={{
                                            display: 'flex', alignItems: 'center', gap: 10,
                                            padding: '10px 14px', borderRadius: 10, background: '#ffffff04',
                                            border: '1px solid #ffffff08', color: '#aaa', fontSize: 12,
                                            cursor: 'pointer', textAlign: 'left', transition: 'all 0.15s'
                                        }}
                                        onMouseEnter={e => { e.currentTarget.style.background = '#ffffff08'; e.currentTarget.style.borderColor = '#a855f720'; }}
                                        onMouseLeave={e => { e.currentTarget.style.background = '#ffffff04'; e.currentTarget.style.borderColor = '#ffffff08'; }}>
                                        <span>{t.icon}</span> {t.prompt}
                                    </button>
                                ))}
                                {TEMPLATES.filter(t => t.lang === language).length === 0 && (
                                    <div style={{ fontSize: 12, color: '#555', padding: '8px 0' }}>
                                        Escribí tu prompt para {selectedLang.label}
                                    </div>
                                )}
                            </div>
                        </div>

                        {/* History */}
                        {history.length > 0 && (
                            <div style={{ marginTop: 28 }}>
                                <div style={{ fontSize: 12, fontWeight: 600, color: '#888', textTransform: 'uppercase', letterSpacing: 1.5, marginBottom: 12 }}>
                                    Historial
                                </div>
                                <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                                    {history.map((h, i) => (
                                        <button key={i} onClick={() => { setPrompt(h.prompt); setLanguage(h.lang); setResult(h.code); }}
                                            style={{
                                                display: 'flex', alignItems: 'center', gap: 8,
                                                padding: '8px 12px', borderRadius: 8, background: 'transparent',
                                                border: 'none', color: '#666', fontSize: 11, cursor: 'pointer',
                                                textAlign: 'left', transition: 'color 0.15s',
                                                overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap'
                                            }}
                                            onMouseEnter={e => e.currentTarget.style.color = '#aaa'}
                                            onMouseLeave={e => e.currentTarget.style.color = '#666'}>
                                            <span>{LANGUAGES.find(l => l.id === h.lang)?.emoji || '📄'}</span>
                                            <span style={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>{h.prompt}</span>
                                        </button>
                                    ))}
                                </div>
                            </div>
                        )}
                    </div>
                </div>

                {/* Right panel — Output */}
                <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
                    {/* Output toolbar */}
                    <div style={{
                        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                        padding: '10px 20px', borderBottom: '1px solid #1f1f1f', flexShrink: 0
                    }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                            <Terminal size={14} color="#666" />
                            <span style={{ fontSize: 12, color: '#666', fontWeight: 600 }}>Resultado</span>
                            {result && (
                                <span style={{
                                    fontSize: 10, color: '#a855f7', background: '#a855f710',
                                    padding: '2px 8px', borderRadius: 6, border: '1px solid #a855f720',
                                    fontWeight: 600
                                }}>
                                    {selectedLang.label}
                                </span>
                            )}
                        </div>
                        {result && (
                            <button onClick={copyCode} style={{
                                display: 'flex', alignItems: 'center', gap: 6, padding: '6px 12px',
                                borderRadius: 8, background: copied ? '#22c55415' : '#ffffff08',
                                border: `1px solid ${copied ? '#22c55430' : '#ffffff10'}`,
                                color: copied ? '#22c55e' : '#888', fontSize: 12, cursor: 'pointer',
                                transition: 'all 0.2s', fontWeight: 500
                            }}>
                                {copied ? <Check size={14} /> : <Copy size={14} />}
                                {copied ? 'Copiado' : 'Copiar'}
                            </button>
                        )}
                    </div>

                    {/* Code output */}
                    <div style={{ flex: 1, overflow: 'auto', padding: 0 }}>
                        {error ? (
                            <div style={{
                                margin: 24, padding: 20, borderRadius: 14,
                                background: '#ef444410', border: '1px solid #ef444420',
                                color: '#ef4444', fontSize: 13
                            }}>
                                <div style={{ fontWeight: 600, marginBottom: 6 }}>❌ Error</div>
                                <div style={{ color: '#ef4444cc' }}>{error}</div>
                            </div>
                        ) : result ? (
                            <pre style={{
                                margin: 0, padding: 24, background: '#0d1117',
                                minHeight: '100%', boxSizing: 'border-box',
                                overflow: 'auto', fontSize: 13, lineHeight: 1.7,
                                fontFamily: "'JetBrains Mono', 'Fira Code', monospace",
                                color: '#e6edf3', whiteSpace: 'pre-wrap', wordBreak: 'break-word'
                            }}>
                                <code>{result}</code>
                            </pre>
                        ) : generating ? (
                            <div style={{
                                display: 'flex', flexDirection: 'column', alignItems: 'center',
                                justifyContent: 'center', height: '100%', gap: 16
                            }}>
                                <motion.div
                                    animate={{ rotate: 360 }}
                                    transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}
                                    style={{
                                        width: 48, height: 48, borderRadius: '50%',
                                        border: '2px solid #a855f7', borderTopColor: 'transparent'
                                    }}
                                />
                                <span style={{ color: '#888', fontSize: 14 }}>Generando código...</span>
                                <span style={{ color: '#555', fontSize: 12 }}>Esto puede tomar unos segundos</span>
                            </div>
                        ) : (
                            <div style={{
                                display: 'flex', flexDirection: 'column', alignItems: 'center',
                                justifyContent: 'center', height: '100%', gap: 16, padding: 40
                            }}>
                                <div style={{
                                    width: 64, height: 64, borderRadius: 16, background: '#a855f708',
                                    border: '1px solid #a855f715', display: 'flex', alignItems: 'center', justifyContent: 'center'
                                }}>
                                    <FileCode2 size={28} color="#a855f7" />
                                </div>
                                <div style={{ textAlign: 'center' }}>
                                    <div style={{ fontSize: 16, fontWeight: 600, color: '#aaa', marginBottom: 6 }}>
                                        Tu código aparecerá aquí
                                    </div>
                                    <div style={{ fontSize: 13, color: '#555', maxWidth: 300, lineHeight: 1.5 }}>
                                        Elegí un lenguaje, escribí qué necesitás y hacé click en Generar
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            </div>

            <style>{`
                @keyframes nexa-spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
                textarea::-webkit-scrollbar { width: 0px; }
                pre::-webkit-scrollbar { width: 6px; }
                pre::-webkit-scrollbar-track { background: transparent; }
                pre::-webkit-scrollbar-thumb { background: #333; border-radius: 3px; }
                pre::-webkit-scrollbar-thumb:hover { background: #444; }
            `}</style>
        </div>
    );
}
