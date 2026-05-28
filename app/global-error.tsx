'use client';

export default function GlobalError({
    error,
    reset,
}: {
    error: Error & { digest?: string };
    reset: () => void;
}) {
    return (
        <html lang="es">
            <body style={{ background: '#000', color: '#fff', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100vh', fontFamily: 'system-ui' }}>
                <h2 style={{ marginBottom: 16 }}>Algo salió mal</h2>
                <button
                    onClick={reset}
                    style={{ background: '#00e5a0', color: '#000', border: 'none', padding: '10px 24px', borderRadius: 8, cursor: 'pointer', fontWeight: 600 }}
                >
                    Intentar de nuevo
                </button>
            </body>
        </html>
    );
}
