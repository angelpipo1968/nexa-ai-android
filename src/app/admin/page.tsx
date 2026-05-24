'use client';
/**
 * ═══════════════════════════════════════════════════════════════
 *  NEXA AI — Panel de Control (Admin Dashboard)
 *  Standalone page with login gate, video API key management,
 *  AI model status, and system overview.
 *  All text in Spanish. Dark theme + glassmorphism.
 * ═══════════════════════════════════════════════════════════════
 */

import React, { useState, useEffect, useCallback } from 'react';

// ─── Constants ──────────────────────────────────────────────

// Admin password is verified server-side via /api/admin/keys
// This client-side constant is a fallback for demo/offline mode only.
const ADMIN_PASSWORD_FALLBACK = process.env.NEXT_PUBLIC_ADMIN_PASSWORD || '';

const ACCENT = '#00e5a0';
const ACCENT_DIM = 'rgba(0,229,160,0.12)';
const BG_PRIMARY = '#030305';
const BG_CARD = 'rgba(10,10,20,0.75)';
const BG_INPUT = '#0a0a14';
const BORDER = 'rgba(255,255,255,0.06)';
const BORDER_ACCENT = 'rgba(0,229,160,0.2)';
const TEXT_PRIMARY = '#f0f0f0';
const TEXT_SECONDARY = '#888';
const TEXT_MUTED = '#555';

// ─── Types ──────────────────────────────────────────────────

interface KeyStatus {
  id: string;
  name: string;
  envKey: string;
  color: string;
  configured: boolean;
  maskedValue: string;
  source?: string;
}

interface KeysResponse {
  video: KeyStatus[];
  ai: KeyStatus[];
  vision: KeyStatus[];
  summary: {
    videoConfigured: number;
    videoTotal: number;
    aiConfigured: number;
    aiTotal: number;
    visionConfigured: number;
    visionTotal: number;
  };
}

// ─── Login Screen Component ─────────────────────────────────

function LoginScreen({ onLogin }: { onLogin: () => void }) {
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [shake, setShake] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [verifying, setVerifying] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!password.trim()) return;
    setVerifying(true);

    try {
      // Verify password server-side
      const res = await fetch('/api/admin/keys', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ password }),
      });
      if (res.ok) {
        onLogin();
      } else {
        // Fallback to client-side check for offline mode
        if (ADMIN_PASSWORD_FALLBACK && password === ADMIN_PASSWORD_FALLBACK) {
          onLogin();
        } else {
          setError('Contraseña incorrecta');
          setShake(true);
          setTimeout(() => setShake(false), 600);
        }
      }
    } catch {
      // Server unavailable — use fallback if configured
      if (ADMIN_PASSWORD_FALLBACK && password === ADMIN_PASSWORD_FALLBACK) {
        onLogin();
      } else if (!ADMIN_PASSWORD_FALLBACK) {
        setError('Servidor no disponible. Configura NEXT_PUBLIC_ADMIN_PASSWORD.');
      } else {
        setError('Contraseña incorrecta');
        setShake(true);
        setTimeout(() => setShake(false), 600);
      }
    } finally {
      setVerifying(false);
    }
  };

  return (
    <div style={{
      position: 'fixed',
      inset: 0,
      background: BG_PRIMARY,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      fontFamily: "'Inter', sans-serif",
      zIndex: 100,
    }}>
      {/* Background gradient orbs */}
      <div style={{
        position: 'absolute',
        width: 500,
        height: 500,
        borderRadius: '50%',
        background: 'radial-gradient(circle, rgba(0,229,160,0.06) 0%, transparent 70%)',
        top: '20%',
        left: '50%',
        transform: 'translate(-50%, -50%)',
        pointerEvents: 'none',
      }} />
      <div style={{
        position: 'absolute',
        width: 400,
        height: 400,
        borderRadius: '50%',
        background: 'radial-gradient(circle, rgba(0,229,160,0.04) 0%, transparent 70%)',
        bottom: '10%',
        right: '10%',
        pointerEvents: 'none',
      }} />

      <div
        style={{
          width: '100%',
          maxWidth: 420,
          padding: '0 20px',
          animation: shake ? 'shake 0.5s ease' : 'none',
        }}
      >
        {/* Card */}
        <div style={{
          background: BG_CARD,
          backdropFilter: 'blur(20px)',
          WebkitBackdropFilter: 'blur(20px)',
          border: `1px solid ${BORDER}`,
          borderRadius: 24,
          padding: '48px 36px',
          textAlign: 'center',
          position: 'relative',
          overflow: 'hidden',
        }}>
          {/* Top accent line */}
          <div style={{
            position: 'absolute',
            top: 0,
            left: '50%',
            transform: 'translateX(-50%)',
            width: 80,
            height: 3,
            background: ACCENT,
            borderRadius: '0 0 4px 4px',
          }} />

          {/* Logo */}
          <div style={{
            width: 72,
            height: 72,
            borderRadius: 20,
            background: ACCENT_DIM,
            border: `1px solid ${BORDER_ACCENT}`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            margin: '0 auto 24px',
            fontSize: 32,
          }}>
            🔒
          </div>

          {/* Title */}
          <h1 style={{
            fontFamily: "'Orbitron', sans-serif",
            fontSize: 28,
            fontWeight: 800,
            letterSpacing: 3,
            color: TEXT_PRIMARY,
            margin: '0 0 6px',
          }}>
            NEXA AI
          </h1>
          <p style={{
            fontSize: 12,
            color: ACCENT,
            fontWeight: 600,
            letterSpacing: 1.5,
            textTransform: 'uppercase',
            margin: '0 0 4px',
          }}>
            Panel de Control
          </p>
          <p style={{
            fontSize: 11,
            color: TEXT_MUTED,
            letterSpacing: 1,
            textTransform: 'uppercase',
            margin: '0 0 32px',
          }}>
            Acceso Restringido
          </p>

          {/* Form */}
          <form onSubmit={handleSubmit}>
            <div style={{
              position: 'relative',
              marginBottom: 16,
            }}>
              <input
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => { setPassword(e.target.value); setError(''); }}
                placeholder="Introduce la contraseña de acceso..."
                autoFocus
                style={{
                  width: '100%',
                  padding: '14px 48px 14px 16px',
                  background: BG_INPUT,
                  border: `1px solid ${error ? 'rgba(239,68,68,0.4)' : BORDER}`,
                  borderRadius: 14,
                  color: TEXT_PRIMARY,
                  fontSize: 14,
                  fontFamily: "'JetBrains Mono', monospace",
                  outline: 'none',
                  transition: 'border-color 0.2s',
                  boxSizing: 'border-box',
                }}
                onFocus={(e) => {
                  if (!error) e.currentTarget.style.borderColor = BORDER_ACCENT;
                }}
                onBlur={(e) => {
                  if (!error) e.currentTarget.style.borderColor = BORDER;
                }}
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                style={{
                  position: 'absolute',
                  right: 14,
                  top: '50%',
                  transform: 'translateY(-50%)',
                  background: 'none',
                  border: 'none',
                  color: TEXT_MUTED,
                  cursor: 'pointer',
                  fontSize: 16,
                  padding: 4,
                }}
              >
                {showPassword ? '🙈' : '👁️'}
              </button>
            </div>

            {error && (
              <p style={{
                fontSize: 12,
                color: '#ef4444',
                marginBottom: 16,
                fontFamily: "'Inter', sans-serif",
              }}>
                {error}
              </p>
            )}

            <button
              type="submit"
              style={{
                width: '100%',
                padding: '14px',
                background: ACCENT,
                border: 'none',
                borderRadius: 14,
                color: '#000',
                fontSize: 13,
                fontWeight: 700,
                letterSpacing: 1.5,
                textTransform: 'uppercase',
                cursor: 'pointer',
                fontFamily: "'Inter', sans-serif",
                transition: 'all 0.2s',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.transform = 'translateY(-1px)';
                e.currentTarget.style.boxShadow = '0 8px 30px rgba(0,229,160,0.25)';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.transform = 'none';
                e.currentTarget.style.boxShadow = 'none';
              }}
            >
              Acceder al Panel
            </button>
          </form>

          {/* Footer note */}
          <p style={{
            fontSize: 10,
            color: TEXT_MUTED,
            marginTop: 24,
            letterSpacing: 0.5,
          }}>
            NEXA AI v4.0 · Intelligence Reborn
          </p>
        </div>
      </div>

      <style>{`
        @keyframes shake {
          0%, 100% { transform: translateX(0); }
          10%, 50%, 90% { transform: translateX(-6px); }
          30%, 70% { transform: translateX(6px); }
        }
        @keyframes fadeIn {
          from { opacity: 0; transform: translateY(20px); }
          to { opacity: 1; transform: translateY(0); }
        }
      `}</style>
    </div>
  );
}

// ─── Glass Card Component ───────────────────────────────────

function GlassCard({
  title,
  icon,
  children,
  style,
}: {
  title: string;
  icon: string;
  children: React.ReactNode;
  style?: React.CSSProperties;
}) {
  return (
    <div style={{
      background: BG_CARD,
      backdropFilter: 'blur(16px)',
      WebkitBackdropFilter: 'blur(16px)',
      border: `1px solid ${BORDER}`,
      borderRadius: 20,
      overflow: 'hidden',
      ...style,
    }}>
      {/* Section header */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        padding: '20px 24px 0',
      }}>
        <span style={{ fontSize: 20 }}>{icon}</span>
        <h2 style={{
          fontFamily: "'Inter', sans-serif",
          fontSize: 15,
          fontWeight: 700,
          color: TEXT_PRIMARY,
          margin: 0,
          letterSpacing: 0.5,
        }}>
          {title}
        </h2>
      </div>
      {/* Content */}
      <div style={{ padding: '16px 24px 24px' }}>
        {children}
      </div>
    </div>
  );
}

// ─── Status Dot ─────────────────────────────────────────────

function StatusDot({ configured }: { configured: boolean }) {
  return (
    <span style={{
      display: 'inline-block',
      width: 8,
      height: 8,
      borderRadius: '50%',
      background: configured ? '#22c55e' : '#ef4444',
      boxShadow: configured
        ? '0 0 8px rgba(34,197,94,0.5)'
        : '0 0 8px rgba(239,68,68,0.3)',
      flexShrink: 0,
    }} />
  );
}

// ─── Key Input Row (for video API keys) ─────────────────────

function VideoKeyInput({
  keyStatus,
  onSave,
  saving,
}: {
  keyStatus: KeyStatus;
  onSave: (keyId: string, keyValue: string) => void;
  saving: boolean;
}) {
  const [value, setValue] = useState('');
  const [showKey, setShowKey] = useState(false);
  const [localSaving, setLocalSaving] = useState(false);

  const handleSave = async () => {
    if (!value.trim() || localSaving) return;
    setLocalSaving(true);
    await onSave(keyStatus.id, value.trim());
    setValue('');
    setLocalSaving(false);
  };

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 8,
      padding: '14px 16px',
      background: 'rgba(255,255,255,0.02)',
      borderRadius: 14,
      border: `1px solid ${BORDER}`,
      transition: 'border-color 0.2s',
    }}>
      {/* Label row */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span style={{
            display: 'inline-block',
            width: 10,
            height: 10,
            borderRadius: 3,
            background: keyStatus.color,
            flexShrink: 0,
          }} />
          <span style={{
            fontSize: 13,
            fontWeight: 600,
            color: TEXT_PRIMARY,
            fontFamily: "'Inter', sans-serif",
          }}>
            {keyStatus.name}
          </span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <StatusDot configured={keyStatus.configured} />
          <span style={{
            fontSize: 10,
            color: keyStatus.configured ? '#22c55e' : TEXT_MUTED,
            fontWeight: 600,
            fontFamily: "'JetBrains Mono', monospace",
          }}>
            {keyStatus.configured ? keyStatus.maskedValue : 'No configurada'}
          </span>
          {keyStatus.source && keyStatus.configured && (
            <span style={{
              fontSize: 9,
              color: TEXT_MUTED,
              background: 'rgba(255,255,255,0.04)',
              padding: '2px 8px',
              borderRadius: 6,
              fontFamily: "'JetBrains Mono', monospace",
            }}>
              {keyStatus.source || '—'}
            </span>
          )}
        </div>
      </div>

      {/* Input + save row */}
      <div style={{
        display: 'flex',
        gap: 8,
        alignItems: 'center',
      }}>
        <div style={{ flex: 1, position: 'relative' }}>
          <input
            type={showKey ? 'text' : 'password'}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            placeholder={`Enter ${keyStatus.name} API Key...`}
            style={{
              width: '100%',
              padding: '10px 36px 10px 12px',
              background: BG_INPUT,
              border: `1px solid ${BORDER}`,
              borderRadius: 10,
              color: TEXT_PRIMARY,
              fontSize: 12,
              fontFamily: "'JetBrains Mono', monospace",
              outline: 'none',
              transition: 'border-color 0.2s',
              boxSizing: 'border-box',
            }}
            onFocus={(e) => { e.currentTarget.style.borderColor = BORDER_ACCENT; }}
            onBlur={(e) => { e.currentTarget.style.borderColor = BORDER; }}
            onKeyDown={(e) => { if (e.key === 'Enter') handleSave(); }}
          />
          <button
            type="button"
            onClick={() => setShowKey(!showKey)}
            style={{
              position: 'absolute',
              right: 10,
              top: '50%',
              transform: 'translateY(-50%)',
              background: 'none',
              border: 'none',
              color: TEXT_MUTED,
              cursor: 'pointer',
              fontSize: 13,
              padding: 2,
            }}
          >
            {showKey ? '🙈' : '👁️'}
          </button>
        </div>
        <button
          onClick={handleSave}
          disabled={!value.trim() || localSaving || saving}
          style={{
            padding: '10px 18px',
            background: value.trim() && !localSaving && !saving ? ACCENT : '#1a1a2a',
            border: 'none',
            borderRadius: 10,
            color: value.trim() && !localSaving && !saving ? '#000' : TEXT_MUTED,
            fontSize: 11,
            fontWeight: 700,
            cursor: value.trim() && !localSaving && !saving ? 'pointer' : 'default',
            fontFamily: "'Inter', sans-serif",
            letterSpacing: 0.5,
            whiteSpace: 'nowrap',
            transition: 'all 0.2s',
          }}
        >
          {localSaving ? '⏳' : 'Guardar'}
        </button>
      </div>
    </div>
  );
}

// ─── Kling Dual Key Input (Access + Secret in one card) ──

function KlingDualKeyInput({
  accessStatus,
  secretStatus,
  onSave,
  saving,
}: {
  accessStatus: KeyStatus;
  secretStatus: KeyStatus;
  onSave: (keyId: string, keyValue: string) => void;
  saving: boolean;
}) {
  const [accessValue, setAccessValue] = useState('');
  const [secretValue, setSecretValue] = useState('');
  const [showAccess, setShowAccess] = useState(false);
  const [showSecret, setShowSecret] = useState(false);
  const [localSaving, setLocalSaving] = useState('');
  const bothConfigured = accessStatus.configured && secretStatus.configured;

  const handleSave = async (keyId: string, keyValue: string) => {
    if (!keyValue.trim()) return;
    setLocalSaving(keyId);
    await onSave(keyId, keyValue.trim());
    if (keyId === 'kling_access') setAccessValue('');
    else setSecretValue('');
    setLocalSaving('');
  };

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 12,
      padding: '16px',
      background: 'rgba(249,115,22,0.04)',
      borderRadius: 14,
      border: `1px solid ${bothConfigured ? 'rgba(34,197,94,0.2)' : BORDER}`,
    }}>
      {/* Header row */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span style={{
            display: 'inline-block',
            width: 10,
            height: 10,
            borderRadius: 3,
            background: '#f97316',
            flexShrink: 0,
          }} />
          <span style={{
            fontSize: 14,
            fontWeight: 700,
            color: TEXT_PRIMARY,
            fontFamily: "'Inter', sans-serif",
          }}>
            Kling AI
          </span>
          <span style={{
            fontSize: 10,
            color: TEXT_MUTED,
            background: 'rgba(255,255,255,0.04)',
            padding: '2px 8px',
            borderRadius: 6,
            fontFamily: "'JetBrains Mono', monospace",
          }}>
            Requiere 2 claves
          </span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <StatusDot configured={bothConfigured} />
          <span style={{
            fontSize: 10,
            color: bothConfigured ? '#22c55e' : TEXT_MUTED,
            fontWeight: 600,
            fontFamily: "'JetBrains Mono', monospace",
          }}>
            {bothConfigured ? 'Configurada' : 'Incompleta'}
          </span>
        </div>
      </div>

      {/* Access Key */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        <span style={{
          fontSize: 10,
          color: TEXT_SECONDARY,
          fontWeight: 600,
          textTransform: 'uppercase',
          letterSpacing: 0.5,
          paddingLeft: 4,
        }}>
          Access Key
          {accessStatus.configured && (
            <span style={{
              color: '#22c55e',
              marginLeft: 8,
              fontFamily: "'JetBrains Mono', monospace",
            }}>
              {accessStatus.maskedValue}
            </span>
          )}
        </span>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <div style={{ flex: 1, position: 'relative' }}>
            <input
              type={showAccess ? 'text' : 'password'}
              value={accessValue}
              onChange={(e) => setAccessValue(e.target.value)}
              placeholder="Enter Kling Access Key..."
              style={{
                width: '100%',
                padding: '10px 36px 10px 12px',
                background: BG_INPUT,
                border: `1px solid ${BORDER}`,
                borderRadius: 10,
                color: TEXT_PRIMARY,
                fontSize: 12,
                fontFamily: "'JetBrains Mono', monospace",
                outline: 'none',
                transition: 'border-color 0.2s',
                boxSizing: 'border-box',
              }}
              onFocus={(e) => { e.currentTarget.style.borderColor = BORDER_ACCENT; }}
              onBlur={(e) => { e.currentTarget.style.borderColor = BORDER; }}
              onKeyDown={(e) => { if (e.key === 'Enter') handleSave('kling_access', accessValue); }}
            />
            <button
              type="button"
              onClick={() => setShowAccess(!showAccess)}
              style={{
                position: 'absolute',
                right: 10,
                top: '50%',
                transform: 'translateY(-50%)',
                background: 'none',
                border: 'none',
                color: TEXT_MUTED,
                cursor: 'pointer',
                fontSize: 13,
                padding: 2,
              }}
            >
              {showAccess ? '🙈' : '👁️'}
            </button>
          </div>
          <button
            onClick={() => handleSave('kling_access', accessValue)}
            disabled={!accessValue.trim() || localSaving === 'kling_access' || saving}
            style={{
              padding: '10px 18px',
              background: accessValue.trim() && localSaving !== 'kling_access' && !saving ? ACCENT : '#1a1a2a',
              border: 'none',
              borderRadius: 10,
              color: accessValue.trim() && localSaving !== 'kling_access' && !saving ? '#000' : TEXT_MUTED,
              fontSize: 11,
              fontWeight: 700,
              cursor: accessValue.trim() && localSaving !== 'kling_access' && !saving ? 'pointer' : 'default',
              fontFamily: "'Inter', sans-serif",
              letterSpacing: 0.5,
              whiteSpace: 'nowrap',
              transition: 'all 0.2s',
            }}
          >
            {localSaving === 'kling_access' ? '⏳' : 'Guardar'}
          </button>
        </div>
      </div>

      {/* Secret Key */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        <span style={{
          fontSize: 10,
          color: TEXT_SECONDARY,
          fontWeight: 600,
          textTransform: 'uppercase',
          letterSpacing: 0.5,
          paddingLeft: 4,
        }}>
          Secret Key
          {secretStatus.configured && (
            <span style={{
              color: '#22c55e',
              marginLeft: 8,
              fontFamily: "'JetBrains Mono', monospace",
            }}>
              {secretStatus.maskedValue}
            </span>
          )}
        </span>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <div style={{ flex: 1, position: 'relative' }}>
            <input
              type={showSecret ? 'text' : 'password'}
              value={secretValue}
              onChange={(e) => setSecretValue(e.target.value)}
              placeholder="Enter Kling Secret Key..."
              style={{
                width: '100%',
                padding: '10px 36px 10px 12px',
                background: BG_INPUT,
                border: `1px solid ${BORDER}`,
                borderRadius: 10,
                color: TEXT_PRIMARY,
                fontSize: 12,
                fontFamily: "'JetBrains Mono', monospace",
                outline: 'none',
                transition: 'border-color 0.2s',
                boxSizing: 'border-box',
              }}
              onFocus={(e) => { e.currentTarget.style.borderColor = BORDER_ACCENT; }}
              onBlur={(e) => { e.currentTarget.style.borderColor = BORDER; }}
              onKeyDown={(e) => { if (e.key === 'Enter') handleSave('kling_secret', secretValue); }}
            />
            <button
              type="button"
              onClick={() => setShowSecret(!showSecret)}
              style={{
                position: 'absolute',
                right: 10,
                top: '50%',
                transform: 'translateY(-50%)',
                background: 'none',
                border: 'none',
                color: TEXT_MUTED,
                cursor: 'pointer',
                fontSize: 13,
                padding: 2,
              }}
            >
              {showSecret ? '🙈' : '👁️'}
            </button>
          </div>
          <button
            onClick={() => handleSave('kling_secret', secretValue)}
            disabled={!secretValue.trim() || localSaving === 'kling_secret' || saving}
            style={{
              padding: '10px 18px',
              background: secretValue.trim() && localSaving !== 'kling_secret' && !saving ? ACCENT : '#1a1a2a',
              border: 'none',
              borderRadius: 10,
              color: secretValue.trim() && localSaving !== 'kling_secret' && !saving ? '#000' : TEXT_MUTED,
              fontSize: 11,
              fontWeight: 700,
              cursor: secretValue.trim() && localSaving !== 'kling_secret' && !saving ? 'pointer' : 'default',
              fontFamily: "'Inter', sans-serif",
              letterSpacing: 0.5,
              whiteSpace: 'nowrap',
              transition: 'all 0.2s',
            }}
          >
            {localSaving === 'kling_secret' ? '⏳' : 'Guardar'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── AI Key Row (read-only) ─────────────────────────────────

function AiKeyRow({ keyStatus }: { keyStatus: KeyStatus }) {
  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      padding: '12px 16px',
      background: 'rgba(255,255,255,0.02)',
      borderRadius: 12,
      border: `1px solid ${BORDER}`,
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <span style={{
          display: 'inline-block',
          width: 10,
          height: 10,
          borderRadius: 3,
          background: keyStatus.color,
          flexShrink: 0,
        }} />
        <div>
          <span style={{
            fontSize: 13,
            fontWeight: 600,
            color: TEXT_PRIMARY,
            fontFamily: "'Inter', sans-serif",
          }}>
            {keyStatus.name}
          </span>
          <span style={{
            fontSize: 10,
            color: TEXT_MUTED,
            marginLeft: 8,
            fontFamily: "'JetBrains Mono', monospace",
          }}>
            {keyStatus.envKey}
          </span>
        </div>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <StatusDot configured={keyStatus.configured} />
        <span style={{
          fontSize: 11,
          color: keyStatus.configured ? '#22c55e' : '#ef4444',
          fontWeight: 500,
        }}>
          {keyStatus.configured ? '✅ Configurada' : '❌ No configurada'}
        </span>
      </div>
    </div>
  );
}

// ─── Stat Card ──────────────────────────────────────────────

function StatCard({
  icon,
  label,
  value,
  color,
}: {
  icon: string;
  label: string;
  value: string;
  color: string;
}) {
  return (
    <div style={{
      flex: 1,
      minWidth: 150,
      padding: '18px 16px',
      background: 'rgba(255,255,255,0.02)',
      borderRadius: 16,
      border: `1px solid ${BORDER}`,
    }}>
      <div style={{
        fontSize: 22,
        marginBottom: 10,
      }}>
        {icon}
      </div>
      <div style={{
        fontSize: 11,
        color: TEXT_MUTED,
        fontWeight: 600,
        textTransform: 'uppercase',
        letterSpacing: 0.5,
        marginBottom: 6,
        fontFamily: "'Inter', sans-serif",
      }}>
        {label}
      </div>
      <div style={{
        fontSize: 13,
        color,
        fontWeight: 700,
        fontFamily: "'JetBrains Mono', monospace",
      }}>
        {value}
      </div>
    </div>
  );
}

// ─── Local Storage Key Manager (works without API routes!) ──

function maskKey(key: string): string {
  if (key.length <= 8) return '••••••••';
  return key.slice(0, 4) + '••••' + key.slice(-4);
}

const STORAGE_PREFIX = 'nexa_keys_';

function getStoredKeys(): Record<string, string> {
  try {
    const raw = localStorage.getItem(STORAGE_PREFIX);
    return raw ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
}

function setStoredKey(envKey: string, value: string) {
  const keys = getStoredKeys();
  keys[envKey] = value;
  localStorage.setItem(STORAGE_PREFIX, JSON.stringify(keys));
}

function buildKeysData(): KeysResponse {
  const stored = getStoredKeys();

  const videoKeys: KeyStatus[] = [
    { id: 'runway', envKey: 'RUNWAY_API_KEY', name: 'Runway ML', color: '#6366f1',
      configured: !!stored['RUNWAY_API_KEY'], maskedValue: stored['RUNWAY_API_KEY'] ? maskKey(stored['RUNWAY_API_KEY']) : '', source: stored['RUNWAY_API_KEY'] ? 'LOCAL' : undefined },
    { id: 'stability', envKey: 'STABILITY_API_KEY', name: 'Stability AI', color: '#a855f7',
      configured: !!stored['STABILITY_API_KEY'], maskedValue: stored['STABILITY_API_KEY'] ? maskKey(stored['STABILITY_API_KEY']) : '', source: stored['STABILITY_API_KEY'] ? 'LOCAL' : undefined },
    { id: 'kling_access', envKey: 'KLING_ACCESS_KEY', name: 'Kling AI (Access Key)', color: '#f97316',
      configured: !!stored['KLING_ACCESS_KEY'], maskedValue: stored['KLING_ACCESS_KEY'] ? maskKey(stored['KLING_ACCESS_KEY']) : '', source: stored['KLING_ACCESS_KEY'] ? 'LOCAL' : undefined },
    { id: 'kling_secret', envKey: 'KLING_SECRET_KEY', name: 'Kling AI (Secret Key)', color: '#ea580c',
      configured: !!stored['KLING_SECRET_KEY'], maskedValue: stored['KLING_SECRET_KEY'] ? maskKey(stored['KLING_SECRET_KEY']) : '', source: stored['KLING_SECRET_KEY'] ? 'LOCAL' : undefined },
    { id: 'luma', envKey: 'LUMA_API_KEY', name: 'Luma Dream Machine', color: '#3b82f6',
      configured: !!stored['LUMA_API_KEY'], maskedValue: stored['LUMA_API_KEY'] ? maskKey(stored['LUMA_API_KEY']) : '', source: stored['LUMA_API_KEY'] ? 'LOCAL' : undefined },
    { id: 'pika', envKey: 'PIKA_API_KEY', name: 'Pika Labs', color: '#ec4899',
      configured: !!stored['PIKA_API_KEY'], maskedValue: stored['PIKA_API_KEY'] ? maskKey(stored['PIKA_API_KEY']) : '', source: stored['PIKA_API_KEY'] ? 'LOCAL' : undefined },
  ];

  const aiKeys: KeyStatus[] = [
    { id: 'groq', envKey: 'GROQ_API_KEY', name: 'Groq (LLM)', color: '#f55036',
      configured: !!stored['GROQ_API_KEY'], maskedValue: stored['GROQ_API_KEY'] ? maskKey(stored['GROQ_API_KEY']) : '', source: stored['GROQ_API_KEY'] ? 'LOCAL' : undefined },
    { id: 'gemini', envKey: 'GOOGLE_AI_API_KEY', name: 'Gemini (Google)', color: '#4285f4',
      configured: !!stored['GOOGLE_AI_API_KEY'], maskedValue: stored['GOOGLE_AI_API_KEY'] ? maskKey(stored['GOOGLE_AI_API_KEY']) : '', source: stored['GOOGLE_AI_API_KEY'] ? 'LOCAL' : undefined },
    { id: 'openai', envKey: 'OPENAI_API_KEY', name: 'OpenAI', color: '#10a37f',
      configured: !!stored['OPENAI_API_KEY'], maskedValue: stored['OPENAI_API_KEY'] ? maskKey(stored['OPENAI_API_KEY']) : '', source: stored['OPENAI_API_KEY'] ? 'LOCAL' : undefined },
    { id: 'deepseek', envKey: 'DEEPSEEK_API_KEY', name: 'DeepSeek', color: '#00b4d8',
      configured: !!stored['DEEPSEEK_API_KEY'], maskedValue: stored['DEEPSEEK_API_KEY'] ? maskKey(stored['DEEPSEEK_API_KEY']) : '', source: stored['DEEPSEEK_API_KEY'] ? 'LOCAL' : undefined },
    { id: 'anthropic', envKey: 'ANTHROPIC_API_KEY', name: 'Anthropic', color: '#d97706',
      configured: !!stored['ANTHROPIC_API_KEY'], maskedValue: stored['ANTHROPIC_API_KEY'] ? maskKey(stored['ANTHROPIC_API_KEY']) : '', source: stored['ANTHROPIC_API_KEY'] ? 'LOCAL' : undefined },
  ];

  const visionKeys: KeyStatus[] = [
    { id: 'hf_glm46v', envKey: 'HUGGINGFACE_API_KEY', name: 'GLM-4.6V (HuggingFace)', color: '#22c55e',
      configured: !!stored['HUGGINGFACE_API_KEY'], maskedValue: stored['HUGGINGFACE_API_KEY'] ? maskKey(stored['HUGGINGFACE_API_KEY']) : '', source: stored['HUGGINGFACE_API_KEY'] ? 'LOCAL' : undefined },
  ];

  return {
    video: videoKeys,
    ai: aiKeys,
    vision: visionKeys,
    summary: {
      videoConfigured: videoKeys.filter(k => k.configured).length,
      videoTotal: videoKeys.length,
      aiConfigured: aiKeys.filter(k => k.configured).length,
      aiTotal: aiKeys.length,
      visionConfigured: visionKeys.filter(k => k.configured).length,
      visionTotal: visionKeys.length,
    },
  };
}

// ─── Main Admin Dashboard ───────────────────────────────────

function AdminDashboard({ onLogout }: { onLogout: () => void }) {
  const [keysData, setKeysData] = useState<KeysResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);
  const [currentTime, setCurrentTime] = useState('');

  // Load keys from localStorage (no API needed!)
  const loadKeys = useCallback(() => {
    try {
      const data = buildKeysData();
      setKeysData(data);
    } catch (err) {
      console.error('Error loading keys:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // Small delay to let render complete
    const t = setTimeout(loadKeys, 100);
    return () => clearTimeout(t);
  }, [loadKeys]);

  // Clock
  useEffect(() => {
    const update = () => {
      setCurrentTime(new Date().toLocaleString('es-ES', {
        weekday: 'short',
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      }));
    };
    update();
    const timer = setInterval(update, 1000);
    return () => clearInterval(timer);
  }, []);

  // Toast auto-dismiss
  useEffect(() => {
    if (toast) {
      const timer = setTimeout(() => setToast(null), 4000);
      return () => clearTimeout(timer);
    }
  }, [toast]);

  // Save a key to localStorage AND sync to server via /api/admin/keys
  const handleSaveKey = async (keyId: string, keyValue: string) => {
    setSaving(true);
    try {
      // Find the envKey from our key definitions
      const allKeys = [
        ...buildKeysData().video,
        ...buildKeysData().ai,
        ...buildKeysData().vision,
      ];
      const keyDef = allKeys.find(k => k.id === keyId);
      if (!keyDef) {
        setToast({ message: `Key ID desconocido: ${keyId}`, type: 'error' });
        return;
      }

      // Save to localStorage
      setStoredKey(keyDef.envKey, keyValue);

      // Sync to server via /api/admin/keys endpoint
      try {
        const res = await fetch('/api/admin/keys', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ keyId, envKey: keyDef.envKey, value: keyValue }),
        });
        if (res.ok) {
          setToast({
            message: `${keyDef.name} guardada y sincronizada con el servidor.`,
            type: 'success',
          });
        } else {
          setToast({
            message: `${keyDef.name} guardada en el navegador (error al sincronizar con servidor).`,
            type: 'success',
          });
        }
      } catch {
        // Server sync failed, but localStorage save succeeded
        setToast({
          message: `${keyDef.name} guardada en el navegador (servidor no disponible).`,
          type: 'success',
        });
      }

      // Reload to update UI
      const data = buildKeysData();
      setKeysData(data);
    } catch (err) {
      setToast({ message: 'Error al guardar la clave', type: 'error' });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div style={{
      position: 'fixed',
      inset: 0,
      background: BG_PRIMARY,
      fontFamily: "'Inter', sans-serif",
      display: 'flex',
      flexDirection: 'column',
      color: TEXT_PRIMARY,
      height: '100dvh',
      height: '100vh',
      overflow: 'hidden',
    }}>
      {/* Override body overflow for admin page */}
      <style>{`
        body { overflow: hidden !important; position: fixed !important; width: 100% !important; }
        html { overflow: hidden !important; }
      `}</style>
      {/* ─── Top Bar ─── */}
      <header style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '14px 28px',
        background: 'rgba(5,5,10,0.9)',
        backdropFilter: 'blur(12px)',
        WebkitBackdropFilter: 'blur(12px)',
        borderBottom: `1px solid ${BORDER}`,
        zIndex: 20,
        flexShrink: 0,
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <a href="/" style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: 36,
            height: 36,
            borderRadius: 10,
            background: ACCENT_DIM,
            border: `1px solid ${BORDER_ACCENT}`,
            color: TEXT_PRIMARY,
            textDecoration: 'none',
            fontSize: 16,
            transition: 'all 0.2s',
          }}>
            ←
          </a>
          <div>
            <span style={{
              fontFamily: "'Orbitron', sans-serif",
              fontSize: 16,
              fontWeight: 800,
              letterSpacing: 2,
              color: TEXT_PRIMARY,
            }}>
              NEXA AI
            </span>
            <span style={{
              fontSize: 10,
              color: TEXT_MUTED,
              marginLeft: 12,
              fontFamily: "'JetBrains Mono', monospace",
              letterSpacing: 0.5,
            }}>
              Panel de Control
            </span>
          </div>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <span style={{
            fontSize: 10,
            color: TEXT_MUTED,
            fontFamily: "'JetBrains Mono', monospace",
          }}>
            {currentTime}
          </span>
          <button
            onClick={onLogout}
            style={{
              padding: '8px 18px',
              background: 'rgba(239,68,68,0.1)',
              border: '1px solid rgba(239,68,68,0.2)',
              borderRadius: 10,
              color: '#ef4444',
              fontSize: 11,
              fontWeight: 600,
              cursor: 'pointer',
              fontFamily: "'Inter', sans-serif",
              transition: 'all 0.2s',
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.background = 'rgba(239,68,68,0.2)';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.background = 'rgba(239,68,68,0.1)';
            }}
          >
            Cerrar Sesión
          </button>
        </div>
      </header>

      {/* ─── Scrollable Content Area ─── */}
      <main style={{
        flex: 1,
        minHeight: 0,
        overflowY: 'auto',
        overflowX: 'hidden',
        padding: '28px',
        WebkitOverflowScrolling: 'touch',
        overscrollBehaviorY: 'contain',
        touchAction: 'pan-y',
      }}>
        <div style={{
          maxWidth: 900,
          margin: '0 auto',
          display: 'flex',
          flexDirection: 'column',
          gap: 24,
        }}>

          {/* ── Section: System Status Overview ── */}
          <GlassCard title="Estado del Sistema" icon="📊" style={{ animation: 'fadeIn 0.4s ease' }}>
            <div style={{
              display: 'flex',
              gap: 16,
              flexWrap: 'wrap',
            }}>
              <StatCard
                icon="🟢"
                label="Servidor"
                value="Activo"
                color="#22c55e"
              />
              <StatCard
                icon="⚡"
                label="Motor"
                value="Next.js 16 + Turbopack"
                color={ACCENT}
              />
              <StatCard
                icon="🧠"
                label="Modelo IA"
                value="Groq Llama 3.3 70B"
                color="#f55036"
              />
              <StatCard
                icon="🎬"
                label="Video Providers"
                value={keysData
                  ? `${keysData.summary.videoConfigured}/${keysData.summary.videoTotal}`
                  : '—'
                }
                color="#a855f7"
              />
              <StatCard
                icon="🤖"
                label="AI Providers"
                value={keysData
                  ? `${keysData.summary.aiConfigured}/${keysData.summary.aiTotal}`
                  : '—'
                }
                color="#3b82f6"
              />
              <StatCard
                icon="🎨"
                label="Imágenes"
                value="Pollinations (Free)"
                color="#ec4899"
              />
            </div>

            {/* Note about Pollinations */}
            <div style={{
              marginTop: 16,
              padding: '12px 16px',
              background: 'rgba(0,229,160,0.04)',
              border: `1px solid ${BORDER_ACCENT}`,
              borderRadius: 12,
              fontSize: 11,
              color: TEXT_SECONDARY,
              lineHeight: 1.6,
            }}>
              <strong style={{ color: ACCENT }}>ℹ️ Nota:</strong> Las imágenes se generan gratuitamente con Pollinations AI.
              Los modelos de IA principales (Groq) funcionan sin costo adicional.
              Para generación de video real, se requieren claves API de los proveedores listados abajo.
            </div>
          </GlassCard>

          {/* ── Section: Video API Keys (MOST IMPORTANT) ── */}
          <GlassCard
            title="Claves API — Generación de Video"
            icon="🔑"
            style={{ animation: 'fadeIn 0.5s ease 0.1s both' }}
          >
            <p style={{
              fontSize: 12,
              color: TEXT_SECONDARY,
              margin: '0 0 16px',
              lineHeight: 1.5,
            }}>
              Configura las claves API para habilitar la generación de video real.
              Las claves guardadas aquí se almacenan en memoria del servidor (runtime) y se pierden al reiniciar.
              Para persistencia, añádelas a <code style={{
                background: 'rgba(0,229,160,0.1)',
                color: ACCENT,
                padding: '1px 6px',
                borderRadius: 4,
                fontFamily: "'JetBrains Mono', monospace",
                fontSize: 11,
              }}>.env.local</code> o a las variables de entorno de Vercel.
            </p>

            <div style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 12,
            }}>
              {loading ? (
                <div style={{ textAlign: 'center', padding: 24, color: TEXT_MUTED, fontSize: 13 }}>
                  Cargando estado de claves...
                </div>
              ) : keysData ? (
                <>
                  {/* Kling AI dual-key component */}
                  {keysData.video.find(k => k.id === 'kling_access') && keysData.video.find(k => k.id === 'kling_secret') && (
                    <KlingDualKeyInput
                      accessStatus={keysData.video.find(k => k.id === 'kling_access')!}
                      secretStatus={keysData.video.find(k => k.id === 'kling_secret')!}
                      onSave={handleSaveKey}
                      saving={saving}
                    />
                  )}
                  {/* Other video keys (excluding Kling individual entries) */}
                  {keysData.video
                    .filter(key => key.id !== 'kling_access' && key.id !== 'kling_secret')
                    .map((key) => (
                      <VideoKeyInput
                        key={key.id}
                        keyStatus={key}
                        onSave={handleSaveKey}
                        saving={saving}
                      />
                    ))
                  }
                </>
              ) : (
                <div style={{ textAlign: 'center', padding: 24, color: '#ef4444', fontSize: 13 }}>
                  Error al cargar las claves API.
                </div>
              )}
            </div>
          </GlassCard>

          {/* ── Section: AI Model Keys (read-only) ── */}
          <GlassCard
            title="Claves API — Modelos de IA"
            icon="🤖"
            style={{ animation: 'fadeIn 0.5s ease 0.2s both' }}
          >
            <p style={{
              fontSize: 12,
              color: TEXT_SECONDARY,
              margin: '0 0 16px',
              lineHeight: 1.5,
            }}>
              Estado de las claves API de los modelos de IA. Estas se configuran en las variables de entorno del servidor.
            </p>

            <div style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 8,
            }}>
              {loading ? (
                <div style={{ textAlign: 'center', padding: 24, color: TEXT_MUTED, fontSize: 13 }}>
                  Cargando estado de modelos...
                </div>
              ) : keysData ? (
                keysData.ai.map((key) => (
                  <AiKeyRow key={key.id} keyStatus={key} />
                ))
              ) : (
                <div style={{ textAlign: 'center', padding: 24, color: '#ef4444', fontSize: 13 }}>
                  Error al cargar los modelos.
                </div>
              )}
            </div>
          </GlassCard>

          {/* ── Section: Vision Model Keys ── */}
          <GlassCard
            title="Claves API — Modelos de Visión"
            icon="👁️"
            style={{ animation: 'fadeIn 0.5s ease 0.2s both' }}
          >
            <p style={{
              fontSize: 12,
              color: TEXT_SECONDARY,
              margin: '0 0 16px',
              lineHeight: 1.5,
            }}>
              Modelos de visión para análisis de imágenes. GLM-4.6V es gratuito vía HuggingFace con 128K tokens de contexto y capacidades multimodulares nativas.
            </p>

            <div style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 12,
            }}>
              {loading ? (
                <div style={{ textAlign: 'center', padding: 24, color: TEXT_MUTED, fontSize: 13 }}>
                  Cargando estado de modelos de visión...
                </div>
              ) : keysData && keysData.vision ? (
                keysData.vision.map((key) => (
                  <VideoKeyInput
                    key={key.id}
                    keyStatus={key}
                    onSave={handleSaveKey}
                    saving={saving}
                  />
                ))
              ) : (
                <div style={{ textAlign: 'center', padding: 24, color: '#ef4444', fontSize: 13 }}>
                  Error al cargar los modelos de visión.
                </div>
              )}
            </div>
          </GlassCard>

          {/* ── Section: System Info ── */}
          <GlassCard
            title="Información del Sistema"
            icon="⚙️"
            style={{ animation: 'fadeIn 0.5s ease 0.3s both' }}
          >
            <div style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 8,
            }}>
              {[
                { label: 'Plataforma', value: 'NEXA AI v4.2 — Intelligence Reborn' },
                { label: 'Framework', value: 'Next.js 16 (App Router) + Turbopack' },
                { label: 'Runtime', value: 'Node.js (Vercel Edge Functions)' },
                { label: 'Base de datos', value: 'Supabase (PostgreSQL)' },
                { label: 'IA Principal', value: 'Groq — Llama 3.3 70B Versatile' },
                { label: 'IA Fallback', value: 'OpenRouter → Gemini → DeepSeek → OpenAI' },
                { label: 'Modelo de Visión', value: 'GLM-4.6V (128K ctx, MIT) → Gemini → GPT-4o' },
                { label: 'Generación de imágenes', value: 'Pollinations AI (gratuito)' },
                { label: 'Generación de video', value: 'Runway ML / Stability AI / Kling / Luma / Pika' },
                { label: 'Búsqueda web', value: 'Wikipedia + Google (SERPAPI)' },
                { label: 'Voz', value: 'Web Speech API (navegador)' },
                { label: 'Motor ML (On-Device)', value: 'Qualcomm Nexa SDK (Snapdragon NPU)' },
                { label: 'Smart Routing', value: 'Cloud ↔ On-Device automático' },
              ].map((item, i) => (
                <div key={i} style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  padding: '10px 14px',
                  background: i % 2 === 0 ? 'rgba(255,255,255,0.015)' : 'transparent',
                  borderRadius: 8,
                }}>
                  <span style={{
                    fontSize: 12,
                    color: TEXT_SECONDARY,
                    fontWeight: 500,
                  }}>
                    {item.label}
                  </span>
                  <span style={{
                    fontSize: 11,
                    color: TEXT_PRIMARY,
                    fontFamily: "'JetBrains Mono', monospace",
                    textAlign: 'right',
                    maxWidth: '55%',
                  }}>
                    {item.value}
                  </span>
                </div>
              ))}
            </div>
          </GlassCard>

          {/* ── Section: Environment Variables Reference ── */}
          <GlassCard
            title="Variables de Entorno (Referencia)"
            icon="📋"
            style={{ animation: 'fadeIn 0.5s ease 0.4s both' }}
          >
            <p style={{
              fontSize: 12,
              color: TEXT_SECONDARY,
              margin: '0 0 16px',
              lineHeight: 1.5,
            }}>
              Para producción, configura estas variables en <code style={{
                background: 'rgba(0,229,160,0.1)',
                color: ACCENT,
                padding: '1px 6px',
                borderRadius: 4,
                fontFamily: "'JetBrains Mono', monospace",
                fontSize: 11,
              }}>.env.local</code> o en el dashboard de Vercel:
            </p>

            <div style={{
              padding: '14px 16px',
              background: 'rgba(0,0,0,0.4)',
              borderRadius: 12,
              border: `1px solid ${BORDER}`,
              fontFamily: "'JetBrains Mono', monospace",
              fontSize: 11,
              color: ACCENT,
              lineHeight: 1.8,
              whiteSpace: 'pre-wrap',
              overflowX: 'auto',
            }}>
{`# ── AI Models ──
GROQ_API_KEY=gsk_...
GOOGLE_AI_API_KEY=AIza...
OPENAI_API_KEY=sk-...
DEEPSEEK_API_KEY=sk-...
ANTHROPIC_API_KEY=sk-ant-...

# ── Vision Models (NEW) ──
HUGGINGFACE_API_KEY=hf_...     # GLM-4.6V (free, 128K ctx, MIT)

# ── Video Generation ──
RUNWAY_API_KEY=...
STABILITY_API_KEY=sk-...
KLING_ACCESS_KEY=...
KLING_SECRET_KEY=...
LUMA_API_KEY=...
PIKA_API_KEY=...

# ── Services ──
SUPABASE_URL=https://...
SUPABASE_ANON_KEY=...
NEXT_PUBLIC_SUPABASE_URL=...
NEXT_PUBLIC_SUPABASE_ANON_KEY=...`}
            </div>
          </GlassCard>

          {/* Footer spacing */}
          <div style={{ height: 20 }} />
        </div>
      </main>

      {/* ─── Scroll indicator (visible when scrolled down) ─── */}
      <button
        onClick={() => {
          const main = document.querySelector('main');
          if (main) main.scrollTo({ top: 0, behavior: 'smooth' });
        }}
        style={{
          position: 'fixed',
          bottom: 24,
          right: 24,
          width: 40,
          height: 40,
          borderRadius: 12,
          background: 'rgba(10,10,20,0.85)',
          border: `1px solid ${BORDER}`,
          color: ACCENT,
          fontSize: 18,
          cursor: 'pointer',
          zIndex: 90,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          backdropFilter: 'blur(8px)',
          WebkitBackdropFilter: 'blur(8px)',
          boxShadow: '0 4px 16px rgba(0,0,0,0.4)',
        }}
        title="Ir arriba"
      >
        ↑
      </button>

      {/* ─── Toast Notification ─── */}
      {toast && (
        <div style={{
          position: 'fixed',
          bottom: 24,
          left: '50%',
          transform: 'translateX(-50%)',
          padding: '12px 24px',
          background: toast.type === 'success'
            ? 'rgba(34,197,94,0.15)'
            : 'rgba(239,68,68,0.15)',
          border: `1px solid ${toast.type === 'success'
            ? 'rgba(34,197,94,0.3)'
            : 'rgba(239,68,68,0.3)'
          }`,
          borderRadius: 14,
          color: toast.type === 'success' ? '#22c55e' : '#ef4444',
          fontSize: 12,
          fontWeight: 600,
          fontFamily: "'Inter', sans-serif",
          zIndex: 100,
          backdropFilter: 'blur(12px)',
          WebkitBackdropFilter: 'blur(12px)',
          animation: 'fadeIn 0.3s ease',
          maxWidth: '90%',
          textAlign: 'center',
        }}>
          {toast.message}
        </div>
      )}
    </div>
  );
}

// ─── Main Page Component ────────────────────────────────────

export default function AdminPage() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  // Check session storage for existing login
  useEffect(() => {
    const session = sessionStorage.getItem('nexa_admin_auth');
    if (session === 'true') {
      setIsAuthenticated(true);
    }
  }, []);

  const handleLogin = () => {
    sessionStorage.setItem('nexa_admin_auth', 'true');
    setIsAuthenticated(true);
  };

  const handleLogout = () => {
    sessionStorage.removeItem('nexa_admin_auth');
    setIsAuthenticated(false);
  };

  if (!isAuthenticated) {
    return <LoginScreen onLogin={handleLogin} />;
  }

  return <AdminDashboard onLogout={handleLogout} />;
}
