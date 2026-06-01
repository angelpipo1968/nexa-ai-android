'use client';

import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { getSupabase } from '@/lib/supabase';
import {
    X, ChevronRight, ChevronLeft, User, LogOut, LogIn,
    Eye, EyeOff, Loader2, Moon, Sun, Monitor, Globe,
    Volume2, Palette, Bell, Lock, Mail, Check,
    Zap, Brain, Sparkles, Heart, Download, Database,
    Fingerprint, AlertCircle, CheckCircle2,
    Cpu, Activity, Waves, Radio, Settings2,
    Type, HelpCircle, Send, Clock, Info,
    Github,
} from 'lucide-react';

// ═══════════════════════════════════════════
//  COLORES
// ═══════════════════════════════════════════

const C = {
    bg: '#02020a',
    surface: '#08081a',
    surfaceH: '#0c0c24',
    card: '#0a0a1e',
    border: '#12123a',
    accent: '#00e5a0',
    accentD: 'rgba(0,229,160,0.08)',
    accentG: 'rgba(0,229,160,0.12)',
    blue: '#00b4ff',
    blueD: 'rgba(0,180,255,0.06)',
    purple: '#a855f7',
    purpleD: 'rgba(168,85,247,0.08)',
    pink: '#ec4899',
    orange: '#f97316',
    yellow: '#fbbf24',
    text: '#e8e8f0',
    sec: '#7878a0',
    muted: '#3a3a60',
    red: '#ff4466',
    redD: 'rgba(255,68,102,0.08)',
    cyan: '#22d3ee',
};

// ═══════════════════════════════════════════
//  TIPOS
// ═══════════════════════════════════════════

type Page = 'main' | 'language' | 'voice' | 'auth';

interface SettingsProps {
    isOpen: boolean;
    onClose: () => void;
    theme: 'system' | 'light' | 'dark';
    onThemeChange: (t: 'system' | 'light' | 'dark') => void;
    locale: string;
    onLocaleChange: (l: string) => void;
    activeProvider?: string;
    // Setting values (controlled from parent)
    stream?: boolean;
    onStreamChange?: (v: boolean) => void;
    sounds?: boolean;
    onSoundsChange?: (v: boolean) => void;
    notif?: boolean;
    onNotifChange?: (v: boolean) => void;
    glowEffect?: boolean;
    onGlowEffectChange?: (v: boolean) => void;
    particleEffect?: boolean;
    onParticleEffectChange?: (v: boolean) => void;
    holoEffect?: boolean;
    onHoloEffectChange?: (v: boolean) => void;
    fontSize?: 'sm' | 'md' | 'lg';
    onFontSizeChange?: (v: 'sm' | 'md' | 'lg') => void;
    animSpeed?: 'slow' | 'normal' | 'fast';
    onAnimSpeedChange?: (v: 'slow' | 'normal' | 'fast') => void;
    autoSend?: boolean;
    onAutoSendChange?: (v: boolean) => void;
    voice?: string;
    onVoiceChange?: (v: string) => void;
}

const PROVIDER_NAMES: Record<string, string> = {
    groq: 'Nexa Core (Llama 3.3 70B)',
    groq_fast: 'Nexa Fast (Llama 3.1 8B)',
    gemini: 'Nexa Vision (Gemini 2.0 Flash)',
    deepseek: 'Nexa Deep (DeepSeek Chat)',
    openai: 'Nexa GPT (GPT-4o Mini)',
};

// ═══════════════════════════════════════════
//  COMPONENTE PRINCIPAL
// ═══════════════════════════════════════════

export function SettingsPanel({ 
    isOpen, onClose, theme, onThemeChange, locale, onLocaleChange, activeProvider = 'groq',
    stream: streamProp, onStreamChange,
    sounds: soundsProp, onSoundsChange,
    notif: notifProp, onNotifChange,
    glowEffect: glowEffectProp, onGlowEffectChange,
    particleEffect: particleEffectProp, onParticleEffectChange,
    holoEffect: holoEffectProp, onHoloEffectChange,
    fontSize: fontSizeProp, onFontSizeChange,
    animSpeed: animSpeedProp, onAnimSpeedChange,
    autoSend: autoSendProp, onAutoSendChange,
    voice: voiceProp, onVoiceChange,
}: SettingsProps) {
    const [page, setPage] = useState<Page>('main');
    const [user, setUser] = useState<any>(null);
    const [aMode, setAMode] = useState<'login' | 'signup'>('login');
    const [aEmail, setAEmail] = useState('');
    const [aPass, setAPass] = useState('');
    const [aShow, setAShow] = useState(false);
    const [aLoad, setALoad] = useState(false);
    const [aErr, setAErr] = useState('');
    const [aOk, setAOk] = useState('');

    // Local fallbacks for uncontrolled usage
    const [localVoice, setLocalVoice] = useState('Katerina');
    const [localNotif, setLocalNotif] = useState(true);
    const [localAutoSend, setLocalAutoSend] = useState(true);
    const [localStream, setLocalStream] = useState(true);
    const [localSounds, setLocalSounds] = useState(true);
    const [localFontSize, setLocalFontSize] = useState<'sm' | 'md' | 'lg'>('md');
    const [localAnimSpeed, setLocalAnimSpeed] = useState<'slow' | 'normal' | 'fast'>('normal');
    const [localGlowEffect, setLocalGlowEffect] = useState(true);
    const [localParticleEffect, setLocalParticleEffect] = useState(true);
    const [localHoloEffect, setLocalHoloEffect] = useState(true);

    // Resolved values: use prop if provided, else local state
    const voice = voiceProp ?? localVoice;
    const notif = notifProp ?? localNotif;
    const autoSend = autoSendProp ?? localAutoSend;
    const stream = streamProp ?? localStream;
    const sounds = soundsProp ?? localSounds;
    const fontSize = fontSizeProp ?? localFontSize;
    const animSpeed = animSpeedProp ?? localAnimSpeed;
    const glowEffect = glowEffectProp ?? localGlowEffect;
    const particleEffect = particleEffectProp ?? localParticleEffect;
    const holoEffect = holoEffectProp ?? localHoloEffect;

    // Handlers: call parent callback + persist to localStorage
    const handleStreamChange = (v: boolean) => { setLocalStream(v); onStreamChange?.(v); localStorage.setItem('nexa_stream', String(v)); };
    const handleSoundsChange = (v: boolean) => { setLocalSounds(v); onSoundsChange?.(v); localStorage.setItem('nexa_sounds', String(v)); };
    const handleNotifChange = (v: boolean) => { setLocalNotif(v); onNotifChange?.(v); localStorage.setItem('nexa_notif', String(v)); };
    const handleGlowEffectChange = (v: boolean) => { setLocalGlowEffect(v); onGlowEffectChange?.(v); localStorage.setItem('nexa_glowEffect', String(v)); };
    const handleParticleEffectChange = (v: boolean) => { setLocalParticleEffect(v); onParticleEffectChange?.(v); localStorage.setItem('nexa_particleEffect', String(v)); };
    const handleHoloEffectChange = (v: boolean) => { setLocalHoloEffect(v); onHoloEffectChange?.(v); localStorage.setItem('nexa_holoEffect', String(v)); };
    const handleFontSizeChange = (v: 'sm' | 'md' | 'lg') => { setLocalFontSize(v); onFontSizeChange?.(v); localStorage.setItem('nexa_fontSize', v); };
    const handleAnimSpeedChange = (v: 'slow' | 'normal' | 'fast') => { setLocalAnimSpeed(v); onAnimSpeedChange?.(v); localStorage.setItem('nexa_animSpeed', v); };
    const handleAutoSendChange = (v: boolean) => { setLocalAutoSend(v); onAutoSendChange?.(v); localStorage.setItem('nexa_autosend', String(v)); };
    const handleVoiceChange = (v: string) => { setLocalVoice(v); onVoiceChange?.(v); localStorage.setItem('nexa_voice', v); };

    // Read persisted settings from localStorage on mount
    useEffect(() => {
        try {
            const ls = localStorage;
            if (ls.getItem('nexa_stream') !== null) setLocalStream(ls.getItem('nexa_stream') === 'true');
            if (ls.getItem('nexa_sounds') !== null) setLocalSounds(ls.getItem('nexa_sounds') === 'true');
            if (ls.getItem('nexa_notif') !== null) setLocalNotif(ls.getItem('nexa_notif') === 'true');
            if (ls.getItem('nexa_glowEffect') !== null) setLocalGlowEffect(ls.getItem('nexa_glowEffect') === 'true');
            if (ls.getItem('nexa_particleEffect') !== null) setLocalParticleEffect(ls.getItem('nexa_particleEffect') === 'true');
            if (ls.getItem('nexa_holoEffect') !== null) setLocalHoloEffect(ls.getItem('nexa_holoEffect') === 'true');
            const fs = ls.getItem('nexa_fontSize');
            if (fs === 'sm' || fs === 'md' || fs === 'lg') setLocalFontSize(fs);
            const as = ls.getItem('nexa_animSpeed');
            if (as === 'slow' || as === 'normal' || as === 'fast') setLocalAnimSpeed(as);
            if (ls.getItem('nexa_autosend') !== null) setLocalAutoSend(ls.getItem('nexa_autosend') === 'true');
            const v = ls.getItem('nexa_voice');
            if (v) setLocalVoice(v);
        } catch {}
    }, []);

    const sb = getSupabase();

    useEffect(() => {
        if (isOpen) {
            sb.auth.getUser().then(({ data }: any) => setUser(data.user));
            setPage('main');
        }
    }, [isOpen]);

    const handleAuth = async (e: React.FormEvent) => {
        e.preventDefault();
        setALoad(true);
        setAErr('');
        setAOk('');
        try {
            const fn = aMode === 'login' ? sb.auth.signInWithPassword : sb.auth.signUp;
            const { error } = await fn({ email: aEmail, password: aPass });
            if (error) throw error;
            if (aMode === 'signup') {
                setAOk('Revisa tu email para confirmar');
            } else {
                const { data } = await sb.auth.getUser();
                setUser(data.user);
                setPage('main');
                setAEmail('');
                setAPass('');
            }
        } catch (err: any) {
            setAErr(err.message);
        } finally {
            setALoad(false);
        }
    };

    const logout = async () => {
        await sb.auth.signOut();
        setUser(null);
        setPage('main');
    };

    // ═══════════════════════════════════════════
    //  COMPONENTES REUTILIZABLES
    // ═══════════════════════════════════════════

    function Toggle({ value, onChange }: { value: boolean; onChange: (v: boolean) => void }) {
        return (
            <button onClick={() => onChange(!value)} style={{
                width: 44, height: 24, borderRadius: 12,
                background: value ? `linear-gradient(135deg, ${C.accent}, ${C.blue})` : C.border,
                border: 'none', cursor: 'pointer', position: 'relative',
                transition: 'all 0.3s', flexShrink: 0,
                boxShadow: value ? `0 0 12px rgba(0,229,160,0.3)` : 'none',
            }}>
                <motion.div
                    animate={{ x: value ? 20 : 2 }}
                    transition={{ type: 'spring', stiffness: 500, damping: 30 }}
                    style={{
                        width: 20, height: 20, borderRadius: '50%',
                        background: '#fff', position: 'absolute', top: 2,
                        boxShadow: '0 1px 4px rgba(0,0,0,0.3)',
                    }}
                />
            </button>
        );
    }

    function SettingRow({
        icon: Icon, iconBg, iconColor, label, desc, value, onClick, right, danger, badge,
    }: {
        icon: any; iconBg?: string; iconColor?: string; label: string; desc?: string;
        value?: string; onClick?: () => void; right?: React.ReactNode; danger?: boolean; badge?: string;
    }) {
        const [hovered, setHovered] = useState(false);
        return (
            <div
                onClick={onClick}
                onMouseEnter={() => setHovered(true)}
                onMouseLeave={() => setHovered(false)}
                onKeyDown={(e) => {
                    if (onClick && (e.key === 'Enter' || e.key === ' ')) {
                        e.preventDefault();
                        onClick();
                    }
                }}
                role={onClick ? "button" : undefined}
                tabIndex={onClick ? 0 : undefined}
                style={{
                    width: '100%', display: 'flex', alignItems: 'center', gap: 12,
                    padding: '12px 14px', borderRadius: 12,
                    background: hovered ? C.surfaceH : 'transparent',
                    border: 'none', cursor: onClick || right ? 'pointer' : 'default',
                    textAlign: 'left', transition: 'all 0.15s',
                    userSelect: 'none',
                    outline: 'none',
                }}
            >
                <div style={{
                    width: 34, height: 34, borderRadius: 9,
                    background: danger ? C.redD : iconBg || `${iconColor || C.accent}12`,
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    flexShrink: 0,
                }}>
                    <Icon size={15} color={danger ? C.red : iconColor || C.accent} />
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <span style={{ fontSize: 13, fontWeight: 500, color: danger ? C.red : C.text }}>
                            {label}
                        </span>
                        {badge && (
                            <span style={{
                                padding: '1px 6px', borderRadius: 4,
                                background: C.accentD, color: C.accent,
                                fontSize: 8, fontWeight: 700, letterSpacing: 0.5,
                            }}>{badge}</span>
                        )}
                    </div>
                    {desc && <div style={{ fontSize: 10, color: C.muted, marginTop: 2 }}>{desc}</div>}
                </div>
                {right || (value && <span style={{ fontSize: 12, color: C.sec }}>{value}</span>)}
                {onClick && !right && <ChevronRight size={14} color={C.muted} />}
            </div>
        );
    }

    function SectionLabel({ children }: { children: React.ReactNode }) {
        return (
            <div style={{
                fontSize: 9, fontWeight: 700, color: C.muted,
                textTransform: 'uppercase', letterSpacing: 3,
                padding: '14px 14px 6px',
            }}>{children}</div>
        );
    }

    function Card({ children }: { children: React.ReactNode }) {
        return (
            <div style={{
                background: C.surface, borderRadius: 14,
                border: `1px solid ${C.border}`, overflow: 'hidden',
                marginBottom: 8,
            }}>{children}</div>
        );
    }

    function Divider() {
        return <div style={{ height: 1, background: C.border, margin: '0 14px' }} />;
    }

    function PageHeader({ title, icon: Icon }: { title: string; icon?: any }) {
        return (
            <div style={{
                display: 'flex', alignItems: 'center', gap: 10,
                padding: '16px 14px 12px',
                borderBottom: `1px solid ${C.border}`,
            }}>
                <button onClick={() => setPage('main')} style={{
                    background: 'none', border: 'none', color: C.muted,
                    cursor: 'pointer', padding: 4,
                }}>
                    <ChevronLeft size={18} />
                </button>
                {Icon && <Icon size={16} color={C.accent} />}
                <span style={{ fontSize: 14, fontWeight: 700, letterSpacing: 1 }}>{title}</span>
            </div>
        );
    }

    // ═══════════════════════════════════════════
    //  MAIN PAGE
    // ═══════════════════════════════════════════

    function MainPage() {
        return (
            <>
                {/* User card */}
                <div style={{ padding: '14px 14px 6px' }}>
                    {user ? (
                        <div style={{
                            position: 'relative', overflow: 'hidden',
                            borderRadius: 16, padding: '18px',
                            background: `linear-gradient(135deg, ${C.accentD}, ${C.blueD}, ${C.purpleD})`,
                            border: `1px solid rgba(0,229,160,0.12)`,
                        }}>
                            <div style={{
                                position: 'absolute', top: -50, right: -50,
                                width: 150, height: 150, borderRadius: '50%',
                                background: `radial-gradient(circle, rgba(0,229,160,0.06), transparent 70%)`,
                                pointerEvents: 'none',
                            }} />
                            <div style={{ display: 'flex', alignItems: 'center', gap: 14, position: 'relative' }}>
                                <div style={{
                                    width: 50, height: 50, borderRadius: 14,
                                    background: `linear-gradient(135deg, ${C.accentG}, ${C.blueD})`,
                                    border: `1px solid rgba(0,229,160,0.2)`,
                                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                                }}>
                                    <User size={22} color={C.accent} />
                                </div>
                                <div style={{ flex: 1, minWidth: 0 }}>
                                    <div style={{
                                        fontSize: 15, fontWeight: 700, color: C.text,
                                        overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                                    }}>
                                        {user.email?.split('@')[0] || 'Usuario'}
                                    </div>
                                    <div style={{
                                        fontSize: 11, color: C.sec, marginTop: 2,
                                        overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                                    }}>
                                        {user.email}
                                    </div>
                                </div>
                                <div style={{
                                    padding: '4px 10px', borderRadius: 8,
                                    background: `linear-gradient(135deg, ${C.accent}20, ${C.blue}15)`,
                                    border: `1px solid rgba(0,229,160,0.15)`,
                                    fontSize: 9, fontWeight: 700, color: C.accent, letterSpacing: 1.5,
                                }}>
                                    PRO
                                </div>
                            </div>
                        </div>
                    ) : (
                        <button onClick={() => setPage('auth')} style={{
                            width: '100%', position: 'relative', overflow: 'hidden',
                            borderRadius: 16, padding: '20px',
                            background: `linear-gradient(135deg, ${C.accentD}, ${C.blueD})`,
                            border: `1px dashed rgba(0,229,160,0.2)`,
                            cursor: 'pointer', textAlign: 'left',
                        }}>
                            <div style={{
                                position: 'absolute', top: -30, right: -30,
                                width: 120, height: 120, borderRadius: '50%',
                                background: `radial-gradient(circle, rgba(0,229,160,0.04), transparent 70%)`,
                            }} />
                            <div style={{ display: 'flex', alignItems: 'center', gap: 14, position: 'relative' }}>
                                <div style={{
                                    width: 50, height: 50, borderRadius: 14,
                                    background: C.accentG,
                                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                                    animation: 'nexa-glow 3s ease-in-out infinite',
                                }}>
                                    <Fingerprint size={24} color={C.accent} />
                                </div>
                                <div>
                                    <div style={{ fontSize: 15, fontWeight: 700, color: C.accent }}>
                                        Iniciar sesión
                                    </div>
                                    <div style={{ fontSize: 11, color: C.sec, marginTop: 3 }}>
                                        Sincroniza tus datos entre dispositivos
                                    </div>
                                </div>
                                <ChevronRight size={16} color={C.muted} style={{ marginLeft: 'auto' }} />
                            </div>
                        </button>
                    )}
                </div>

                <div style={{ flex: 1, overflowY: 'auto', paddingBottom: 20 }}>
                    {/* GENERAL */}
                    <SectionLabel>General</SectionLabel>
                    <div style={{ padding: '0 8px' }}>
                        <Card>
                            <SettingRow
                                icon={Monitor} iconColor={C.accent}
                                label="Tema"
                                desc={theme === 'system' ? 'Se adapta a tu sistema' : theme === 'light' ? 'Modo claro' : 'Modo oscuro'}
                                onClick={() => onThemeChange(theme === 'dark' ? 'light' : theme === 'light' ? 'system' : 'dark')}
                                right={
                                    <div style={{ display: 'flex', gap: 3, background: C.bg, borderRadius: 8, padding: 2 }}>
                                        {[
                                            { id: 'system' as const, icon: Monitor },
                                            { id: 'light' as const, icon: Sun },
                                            { id: 'dark' as const, icon: Moon },
                                        ].map(m => (
                                            <button key={m.id} onClick={e => { e.stopPropagation(); onThemeChange(m.id); }} style={{
                                                width: 28, height: 24, borderRadius: 6,
                                                background: theme === m.id ? C.accentD : 'transparent',
                                                border: `1px solid ${theme === m.id ? 'rgba(0,229,160,0.2)' : 'transparent'}`,
                                                color: theme === m.id ? C.accent : C.muted,
                                                display: 'flex', alignItems: 'center', justifyContent: 'center',
                                                cursor: 'pointer', transition: 'all 0.2s',
                                            }}>
                                                <m.icon size={11} />
                                            </button>
                                        ))}
                                    </div>
                                }
                            />
                            <Divider />
                            <SettingRow
                                icon={Globe} iconColor={C.blue}
                                label="Lenguaje"
                                value={locale === 'es' ? '🇪🇸 Español' : locale === 'en' ? '🇺🇸 English' : locale === 'pt' ? '🇧🇷 Português' : '🌐 ' + locale}
                                onClick={() => setPage('language')}
                            />
                            <Divider />
                            <SettingRow
                                icon={Volume2} iconColor={C.purple}
                                label="Voz"
                                desc="Voz de NEXA para respuestas habladas"
                                value={voice}
                                onClick={() => setPage('voice')}
                            />
                        </Card>
                    </div>

                    {/* INTERFAZ */}
                    <SectionLabel>Interfaz</SectionLabel>
                    <div style={{ padding: '0 8px' }}>
                        <Card>
                            <SettingRow
                                icon={Zap} iconColor={C.accent}
                                label="Streaming en vivo"
                                desc="Respuestas en tiempo real"
                                right={<Toggle value={stream} onChange={handleStreamChange} />}
                            />
                            <Divider />
                            <SettingRow
                                icon={Radio} iconColor={C.yellow}
                                label="Efectos de sonido"
                                desc="Sonidos al enviar y recibir"
                                right={<Toggle value={sounds} onChange={handleSoundsChange} />}
                            />
                            <Divider />
                            <SettingRow
                                icon={Bell} iconColor={C.orange}
                                label="Notificaciones"
                                desc={notif ? 'Activadas' : 'Desactivadas'}
                                right={<Toggle value={notif} onChange={handleNotifChange} />}
                            />
                        </Card>
                    </div>

                    {/* PERSONALIZACIÓN */}
                    <SectionLabel>Personalización</SectionLabel>
                    <div style={{ padding: '0 8px' }}>
                        <Card>
                            <SettingRow
                                icon={Sparkles} iconColor={C.accent}
                                label="Efecto holográfico"
                                desc="Brillo y reflejos en la interfaz"
                                right={<Toggle value={holoEffect} onChange={handleHoloEffectChange} />}
                            />
                            <Divider />
                            <SettingRow
                                icon={Waves} iconColor={C.purple}
                                label="Efecto de partículas"
                                desc="Partículas animadas de fondo"
                                right={<Toggle value={particleEffect} onChange={handleParticleEffectChange} />}
                            />
                            <Divider />
                            <SettingRow
                                icon={Zap} iconColor={C.blue}
                                label="Efecto de brillo"
                                desc="Glow neón en elementos activos"
                                right={<Toggle value={glowEffect} onChange={handleGlowEffectChange} />}
                            />
                            <Divider />
                            <SettingRow
                                icon={Type} iconColor={C.pink}
                                label="Tamaño de texto"
                                value={fontSize === 'sm' ? 'Pequeño' : fontSize === 'md' ? 'Mediano' : 'Grande'}
                                onClick={() => handleFontSizeChange(fontSize === 'sm' ? 'md' : fontSize === 'md' ? 'lg' : 'sm')}
                            />
                            <Divider />
                            <SettingRow
                                icon={Activity} iconColor={C.cyan}
                                label="Velocidad de animación"
                                value={animSpeed === 'slow' ? 'Lenta' : animSpeed === 'normal' ? 'Normal' : 'Rápida'}
                                onClick={() => handleAnimSpeedChange(animSpeed === 'slow' ? 'normal' : animSpeed === 'normal' ? 'fast' : 'slow')}
                            />
                        </Card>
                    </div>

                    {/* CHATS */}
                    <SectionLabel>Chats</SectionLabel>
                    <div style={{ padding: '0 8px' }}>
                        <Card>
                            <SettingRow
                                icon={Send} iconColor={C.accent}
                                label="Auto-enviar con voz"
                                desc="Enviar al dejar de hablar"
                                right={<Toggle value={autoSend} onChange={handleAutoSendChange} />}
                            />
                        </Card>
                    </div>

                    {/* CUENTA */}
                    {user && (
                        <>
                            <SectionLabel>Cuenta</SectionLabel>
                            <div style={{ padding: '0 8px' }}>
                                <Card>
                                    <SettingRow
                                        icon={Database} iconColor={C.blue}
                                        label="Datos en la nube"
                                        desc="Supabase conectado"
                                        badge="SYNC"
                                    />
                                    <Divider />
                                    <SettingRow
                                        icon={Download} iconColor={C.accent}
                                        label="Exportar datos"
                                        desc="Descargar conversaciones"
                                        onClick={() => {}}
                                    />
                                    <Divider />
                                    <SettingRow
                                        icon={LogOut} iconColor={C.red}
                                        label="Cerrar sesión"
                                        onClick={logout}
                                        danger
                                    />
                                </Card>
                            </div>
                        </>
                    )}

                    {/* SOBRE NOSOTROS */}
                    <SectionLabel>Sobre nosotros</SectionLabel>
                    <div style={{ padding: '0 8px' }}>
                        <Card>
                            <SettingRow
                                icon={Info} iconColor={C.blue}
                                label="Versión"
                                value="NEXA v4.0"
                                badge="ULTRA"
                            />
                            <Divider />
                            <SettingRow
                                icon={Brain} iconColor={C.accent}
                                label="Modelo de IA"
                                value={PROVIDER_NAMES[activeProvider] || activeProvider}
                            />
                            <Divider />
                            <SettingRow
                                icon={Cpu} iconColor={C.purple}
                                label="Motor"
                                value="Next.js 15 + Turbopack"
                            />
                            <Divider />
                            <SettingRow
                                icon={Heart} iconColor={C.pink}
                                label="Creado por"
                                value="Angel Pipó"
                            />
                        </Card>
                    </div>

                    {/* Links */}
                    <div style={{
                        display: 'flex', justifyContent: 'center', gap: 20,
                        padding: '20px 0 40px',
                    }}>
                        {[
                            { icon: Github, label: 'GitHub', color: C.text },
                            { icon: Globe, label: 'Web', color: C.accent },
                            { icon: HelpCircle, label: 'Ayuda', color: C.blue },
                        ].map(link => (
                            <button key={link.label} style={{
                                display: 'flex', flexDirection: 'column',
                                alignItems: 'center', gap: 6, background: 'none',
                                border: 'none', color: link.color, cursor: 'pointer',
                                opacity: 0.5, padding: 8,
                            }}>
                                <link.icon size={16} />
                                <span style={{ fontSize: 9, letterSpacing: 1 }}>{link.label}</span>
                            </button>
                        ))}
                    </div>
                </div>
            </>
        );
    }

    // ═══════════════════════════════════════════
    //  LANGUAGE PAGE
    // ═══════════════════════════════════════════

    function LanguagePage() {
        const langs = [
            { code: 'es', name: 'Español', native: 'Spanish', flag: '🇪🇸', region: 'Latinoamérica / España' },
            { code: 'en', name: 'English', native: 'English', flag: '🇺🇸', region: 'United States' },
            { code: 'pt', name: 'Português', native: 'Portuguese', flag: '🇧🇷', region: 'Brasil' },
            { code: 'fr', name: 'Français', native: 'French', flag: '🇫🇷', region: 'France' },
            { code: 'de', name: 'Deutsch', native: 'German', flag: '🇩🇪', region: 'Deutschland' },
            { code: 'ja', name: '日本語', native: 'Japanese', flag: '🇯🇵', region: 'Japan' },
            { code: 'zh', name: '中文', native: 'Chinese', flag: '🇨🇳', region: 'China' },
            { code: 'ko', name: '한국어', native: 'Korean', flag: '🇰🇷', region: 'Korea' },
            { code: 'ar', name: 'العربية', native: 'Arabic', flag: '🇸🇦', region: 'السعودية' },
            { code: 'hi', name: 'हिन्दी', native: 'Hindi', flag: '🇮🇳', region: 'India' },
        ];

        return (
            <>
                <PageHeader title="Lenguaje" icon={Globe} />
                <div style={{ flex: 1, overflowY: 'auto', padding: '8px' }}>
                    <div style={{ padding: '0 6px' }}>
                        <Card>
                            {langs.map((lang, i) => (
                                <React.Fragment key={lang.code}>
                                    <button onClick={() => onLocaleChange(lang.code)} style={{
                                        width: '100%', display: 'flex', alignItems: 'center',
                                        gap: 12, padding: '13px 14px',
                                        background: locale === lang.code ? C.accentD : 'transparent',
                                        border: 'none', cursor: 'pointer', textAlign: 'left',
                                        transition: 'background 0.15s',
                                    }}>
                                        <span style={{ fontSize: 22 }}>{lang.flag}</span>
                                        <div style={{ flex: 1 }}>
                                            <div style={{
                                                fontSize: 13, fontWeight: 600,
                                                color: locale === lang.code ? C.accent : C.text,
                                            }}>{lang.native}</div>
                                            <div style={{ fontSize: 10, color: C.muted, marginTop: 1 }}>
                                                {lang.region}
                                            </div>
                                        </div>
                                        {locale === lang.code && (
                                            <motion.div initial={{ scale: 0 }} animate={{ scale: 1 }}>
                                                <Check size={16} color={C.accent} />
                                            </motion.div>
                                        )}
                                    </button>
                                    {i < langs.length - 1 && <Divider />}
                                </React.Fragment>
                            ))}
                        </Card>
                    </div>
                </div>
            </>
        );
    }

    // ═══════════════════════════════════════════
    //  VOICE PAGE
    // ═══════════════════════════════════════════

    function VoicePage() {
        const maleVoices = [
            { id: 'Diego', desc: 'Masculina, profunda, profesional', icon: '🎤', color: C.blue },
            { id: 'Carlos', desc: 'Masculina, clara, neutra', icon: '📢', color: C.purple },
            { id: 'Paulo', desc: 'Masculina, brasileña, amigable', icon: '🇧🇷', color: C.yellow },
        ];
        const femaleVoices = [
            { id: 'Katerina', desc: 'Femenina, natural, cálida', icon: '🎙️', color: C.accent },
            { id: 'Sofia', desc: 'Femenina, joven, energética', icon: '🎵', color: C.pink },
            { id: 'Helena', desc: 'Femenina, elegante, profesional', icon: '🎶', color: C.cyan },
        ];
        const allVoices = [...maleVoices, ...femaleVoices];

        return (
            <>
                <PageHeader title="Voz" icon={Volume2} />
                <div style={{ flex: 1, overflowY: 'auto', padding: '8px' }}>
                    <div style={{ padding: '0 6px' }}>
                        {/* Preview */}
                        <div style={{
                            padding: '20px', borderRadius: 14, marginBottom: 12,
                            background: `linear-gradient(135deg, ${C.accentD}, ${C.blueD})`,
                            border: `1px solid rgba(0,229,160,0.1)`,
                            textAlign: 'center',
                        }}>
                            <div style={{
                                width: 60, height: 60, borderRadius: '50%',
                                background: C.accentG,
                                display: 'flex', alignItems: 'center', justifyContent: 'center',
                                margin: '0 auto 12px',
                                animation: 'nexa-glow 2s ease-in-out infinite',
                            }}>
                                <Waves size={24} color={C.accent} />
                            </div>
                            <p style={{ fontSize: 13, color: C.text, fontWeight: 500 }}>{voice}</p>
                            <p style={{ fontSize: 10, color: C.muted, marginTop: 4 }}>Voz seleccionada</p>
                        </div>

                        <Card>
                            {/* Hombres */}
                            <div style={{ padding: '8px 14px 4px', fontSize: 10, fontWeight: 700, color: C.muted, textTransform: 'uppercase', letterSpacing: 1.5 }}>
                                🧔 Hombres
                            </div>
                            {maleVoices.map((v, i) => (
                                <React.Fragment key={v.id}>
                                    <button onClick={() => handleVoiceChange(v.id)} style={{
                                        width: '100%', display: 'flex', alignItems: 'center',
                                        gap: 12, padding: '13px 14px',
                                        background: voice === v.id ? `${v.color}10` : 'transparent',
                                        border: 'none', cursor: 'pointer', textAlign: 'left',
                                        transition: 'background 0.15s',
                                    }}>
                                        <div style={{
                                            width: 38, height: 38, borderRadius: 10,
                                            background: voice === v.id ? `${v.color}18` : C.bg,
                                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                                            fontSize: 18,
                                        }}>
                                            {v.icon}
                                        </div>
                                        <div style={{ flex: 1 }}>
                                            <div style={{
                                                fontSize: 13, fontWeight: 600,
                                                color: voice === v.id ? v.color : C.text,
                                            }}>{v.id}</div>
                                            <div style={{ fontSize: 10, color: C.muted, marginTop: 1 }}>{v.desc}</div>
                                        </div>
                                        {voice === v.id && (
                                            <motion.div initial={{ scale: 0 }} animate={{ scale: 1 }}>
                                                <Check size={16} color={v.color} />
                                            </motion.div>
                                        )}
                                    </button>
                                    {i < maleVoices.length - 1 && <Divider />}
                                </React.Fragment>
                            ))}
                            <Divider />
                            {/* Mujeres */}
                            <div style={{ padding: '8px 14px 4px', fontSize: 10, fontWeight: 700, color: C.muted, textTransform: 'uppercase', letterSpacing: 1.5 }}>
                                👩 Mujeres
                            </div>
                            {femaleVoices.map((v, i) => (
                                <React.Fragment key={v.id}>
                                    <button onClick={() => handleVoiceChange(v.id)} style={{
                                        width: '100%', display: 'flex', alignItems: 'center',
                                        gap: 12, padding: '13px 14px',
                                        background: voice === v.id ? `${v.color}10` : 'transparent',
                                        border: 'none', cursor: 'pointer', textAlign: 'left',
                                        transition: 'background 0.15s',
                                    }}>
                                        <div style={{
                                            width: 38, height: 38, borderRadius: 10,
                                            background: voice === v.id ? `${v.color}18` : C.bg,
                                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                                            fontSize: 18,
                                        }}>
                                            {v.icon}
                                        </div>
                                        <div style={{ flex: 1 }}>
                                            <div style={{
                                                fontSize: 13, fontWeight: 600,
                                                color: voice === v.id ? v.color : C.text,
                                            }}>{v.id}</div>
                                            <div style={{ fontSize: 10, color: C.muted, marginTop: 1 }}>{v.desc}</div>
                                        </div>
                                        {voice === v.id && (
                                            <motion.div initial={{ scale: 0 }} animate={{ scale: 1 }}>
                                                <Check size={16} color={v.color} />
                                            </motion.div>
                                        )}
                                    </button>
                                    {i < femaleVoices.length - 1 && <Divider />}
                                </React.Fragment>
                            ))}
                        </Card>
                    </div>
                </div>
            </>
        );
    }

    // ═══════════════════════════════════════════
    //  AUTH PAGE
    // ═══════════════════════════════════════════

    function AuthPage() {
        return (
            <>
                <PageHeader title={aMode === 'login' ? 'Iniciar sesión' : 'Crear cuenta'} icon={Fingerprint} />
                <div style={{
                    flex: 1, display: 'flex', flexDirection: 'column',
                    alignItems: 'center', justifyContent: 'center',
                    padding: '24px 24px 60px', overflowY: 'auto',
                }}>
                    {/* Logo holográfico */}
                    <motion.div initial={{ scale: 0.8, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} style={{ marginBottom: 28 }}>
                        <div style={{
                            width: 84, height: 84, borderRadius: 26,
                            background: `linear-gradient(135deg, ${C.accentD}, ${C.blueD}, ${C.purpleD})`,
                            border: `1px solid rgba(0,229,160,0.15)`,
                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                            position: 'relative', overflow: 'hidden',
                        }}>
                            <div style={{
                                position: 'absolute', inset: 0,
                                background: `conic-gradient(from 0deg, transparent, ${C.accent}08, transparent, ${C.blue}08, transparent)`,
                                animation: 'nexa-spin 8s linear infinite',
                            }} />
                            <Brain size={36} color={C.accent} style={{ position: 'relative', zIndex: 1 }} />
                        </div>
                    </motion.div>

                    <form onSubmit={handleAuth} style={{ width: '100%', maxWidth: 280 }}>
                        <label style={{ display: 'block', fontSize: 9, color: C.muted, textTransform: 'uppercase', letterSpacing: 2, marginBottom: 6 }}>Email</label>
                        <div style={{ position: 'relative', marginBottom: 14 }}>
                            <Mail size={14} style={{ position: 'absolute', left: 14, top: '50%', transform: 'translateY(-50%)', color: C.muted }} />
                            <input type="email" value={aEmail} onChange={e => setAEmail(e.target.value)} placeholder="tu@email.com" required
                                style={{ width: '100%', padding: '13px 14px 13px 38px', borderRadius: 12, fontSize: 13, background: C.surface, border: `1px solid ${C.border}`, color: C.text, outline: 'none', boxSizing: 'border-box' }} />
                        </div>

                        <label style={{ display: 'block', fontSize: 9, color: C.muted, textTransform: 'uppercase', letterSpacing: 2, marginBottom: 6 }}>Contraseña</label>
                        <div style={{ position: 'relative', marginBottom: 14 }}>
                            <Lock size={14} style={{ position: 'absolute', left: 14, top: '50%', transform: 'translateY(-50%)', color: C.muted }} />
                            <input type={aShow ? 'text' : 'password'} value={aPass} onChange={e => setAPass(e.target.value)} placeholder="••••••••" required minLength={6}
                                style={{ width: '100%', padding: '13px 38px 13px 38px', borderRadius: 12, fontSize: 13, background: C.surface, border: `1px solid ${C.border}`, color: C.text, outline: 'none', boxSizing: 'border-box' }} />
                            <button type="button" onClick={() => setAShow(!aShow)} style={{ position: 'absolute', right: 12, top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', color: C.muted, cursor: 'pointer' }}>
                                {aShow ? <EyeOff size={14} /> : <Eye size={14} />}
                            </button>
                        </div>

                        {aErr && (
                            <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '10px 12px', borderRadius: 10, marginBottom: 14, background: C.redD, border: `1px solid rgba(255,68,102,0.15)` }}>
                                <AlertCircle size={13} color={C.red} />
                                <span style={{ fontSize: 11, color: C.red }}>{aErr}</span>
                            </div>
                        )}

                        {aOk && (
                            <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '10px 12px', borderRadius: 10, marginBottom: 14, background: C.accentD, border: `1px solid rgba(0,229,160,0.15)` }}>
                                <CheckCircle2 size={13} color={C.accent} />
                                <span style={{ fontSize: 11, color: C.accent }}>{aOk}</span>
                            </div>
                        )}

                        <button type="submit" disabled={aLoad} style={{
                            width: '100%', padding: 13, borderRadius: 12,
                            background: `linear-gradient(135deg, ${C.accent}, ${C.blue})`,
                            color: C.bg, fontSize: 13, fontWeight: 700,
                            border: 'none', cursor: 'pointer', marginBottom: 14, letterSpacing: 1,
                            opacity: aLoad ? 0.6 : 1,
                            display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
                            boxShadow: `0 4px 20px rgba(0,229,160,0.2)`,
                        }}>
                            {aLoad ? (
                                <Loader2 size={15} style={{ animation: 'nexa-spin 1s linear infinite' }} />
                            ) : (
                                <><Fingerprint size={15} />{aMode === 'login' ? 'ENTRAR' : 'REGISTRARME'}</>
                            )}
                        </button>

                        <p style={{ fontSize: 11, color: C.muted, textAlign: 'center', marginBottom: 20 }}>
                            {aMode === 'login' ? '¿No tienes cuenta?' : '¿Ya tienes cuenta?'}{' '}
                            <button type="button" onClick={() => { setAMode(aMode === 'login' ? 'signup' : 'login'); setAErr(''); setAOk(''); }}
                                style={{ background: 'none', border: 'none', color: C.accent, cursor: 'pointer', fontWeight: 600 }}>
                                {aMode === 'login' ? 'Regístrate' : 'Inicia sesión'}
                            </button>
                        </p>

                        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
                            <div style={{ flex: 1, height: 1, background: C.border }} />
                            <span style={{ fontSize: 9, color: C.muted, letterSpacing: 1 }}>O</span>
                            <div style={{ flex: 1, height: 1, background: C.border }} />
                        </div>

                        <button type="button" style={{
                            width: '100%', padding: '12px', borderRadius: 12,
                            background: C.surface, border: `1px solid ${C.border}`,
                            color: C.text, fontSize: 12, cursor: 'pointer',
                            display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10,
                        }}>
                            <svg width="15" height="15" viewBox="0 0 24 24">
                                <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 01-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z"/>
                                <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
                                <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
                                <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
                            </svg>
                            Continuar con Google
                        </button>
                    </form>
                </div>
            </>
        );
    }

    // ═══════════════════════════════════════════
    //  RENDER PRINCIPAL
    // ═══════════════════════════════════════════

    return (
        <AnimatePresence>
            {isOpen && (
                <>
                    <motion.div
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0 }}
                        onClick={onClose}
                        style={{
                            position: 'fixed', inset: 0, zIndex: 60,
                            background: 'rgba(0,0,0,0.75)',
                            backdropFilter: 'blur(12px)',
                        }}
                    />

                    <motion.div
                        initial={{ x: -340, opacity: 0 }}
                        animate={{ x: 0, opacity: 1 }}
                        exit={{ x: -340, opacity: 0 }}
                        transition={{ type: 'spring', damping: 26, stiffness: 260 }}
                        style={{
                            position: 'fixed', top: 0, left: 0, bottom: 0,
                            zIndex: 70, width: 340,
                            background: `linear-gradient(180deg, ${C.surface} 0%, ${C.bg} 50%, ${C.surface} 100%)`,
                            borderRight: `1px solid ${C.border}`,
                            display: 'flex', flexDirection: 'column',
                            backdropFilter: 'blur(40px)',
                            overflow: 'hidden',
                        }}
                    >
                        {/* Ambient glow */}
                        <div style={{
                            position: 'absolute', top: -100, left: '50%', transform: 'translateX(-50%)',
                            width: 400, height: 200, borderRadius: '50%',
                            background: `radial-gradient(ellipse, rgba(0,229,160,0.03), transparent 70%)`,
                            pointerEvents: 'none',
                        }} />

                        {/* Header */}
                        {page === 'main' && (
                            <div style={{
                                display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                                padding: '18px 14px 12px',
                                borderBottom: `1px solid ${C.border}`,
                            }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                                    <div style={{
                                        width: 30, height: 30, borderRadius: 8,
                                        background: `linear-gradient(135deg, ${C.accentD}, ${C.blueD})`,
                                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                                    }}>
                                        <Settings2 size={14} color={C.accent} />
                                    </div>
                                    <div>
                                        <span style={{ fontSize: 14, fontWeight: 700, letterSpacing: 2, display: 'block' }}>
                                            CONFIGURACIÓN
                                        </span>
                                        <span style={{
                                            fontSize: 8, color: C.accent, fontWeight: 600,
                                            letterSpacing: 2, opacity: 0.7,
                                        }}>NEXA ULTRA</span>
                                    </div>
                                </div>
                                <button onClick={onClose} style={{
                                    background: 'none', border: 'none',
                                    color: C.muted, cursor: 'pointer', padding: 6,
                                }}>
                                    <X size={16} />
                                </button>
                            </div>
                        )}

                        {/* Content */}
                        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
                            {page === 'main' && <MainPage />}
                            {page === 'language' && <LanguagePage />}
                            {page === 'voice' && <VoicePage />}
                            {page === 'auth' && <AuthPage />}
                        </div>
                    </motion.div>
                </>
            )}
        </AnimatePresence>
    );
}
