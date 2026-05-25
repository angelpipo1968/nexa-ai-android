'use client';

import * as Sentry from '@sentry/nextjs';
import { useEffect } from 'react';

export default function GlobalError({ error }: { error: Error & { digest?: string } }) {
    useEffect(() => {
        Sentry.captureException(error);
    }, [error]);

    return (
        <html>
            <body>
                <div style={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    minHeight: '100vh',
                    background: '#0a0a0a',
                    color: '#f0f0f0',
                    fontFamily: "'Inter', sans-serif",
                    padding: 24,
                    textAlign: 'center',
                }}>
                    <div style={{
                        width: 64,
                        height: 64,
                        borderRadius: 16,
                        background: 'rgba(239,68,68,0.1)',
                        border: '1px solid rgba(239,68,68,0.2)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontSize: 28,
                        marginBottom: 20,
                    }}>
                        ⚠️
                    </div>
                    <h1 style={{ fontSize: 24, fontWeight: 700, marginBottom: 8 }}>
                        Error inesperado
                    </h1>
                    <p style={{ fontSize: 14, color: '#888', maxWidth: 400, lineHeight: 1.6 }}>
                        Ha ocurrido un error inesperado en la aplicación. Por favor, recarga la página para continuar.
                    </p>
                    {error.digest && (
                        <p style={{ fontSize: 11, color: '#555', marginTop: 12, fontFamily: 'monospace' }}>
                            ID: {error.digest}
                        </p>
                    )}
                    <button
                        onClick={() => window.location.reload()}
                        style={{
                            marginTop: 24,
                            padding: '12px 24px',
                            background: '#00e5a0',
                            border: 'none',
                            borderRadius: 12,
                            color: '#000',
                            fontSize: 14,
                            fontWeight: 600,
                            cursor: 'pointer',
                        }}
                    >
                        Recargar página
                    </button>
                </div>
            </body>
        </html>
    );
}
