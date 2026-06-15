import type { Metadata, Viewport } from 'next'
import './globals.css'

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  maximumScale: 1,
  userScalable: false,
  themeColor: '#0a0a0f',
}

export const metadata: Metadata = {
  title: 'NEXA PRO — AI Assistant',
  description: 'Advanced AI assistant with 37+ tools: Chat, Vision, Voice, Code, Flights, Weather & more.',
  appleWebApp: {
    capable: true,
    statusBarStyle: 'black-translucent',
    title: 'NEXA PRO',
  },
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="es">
      <head>
        <link rel="icon" href="/favicon.ico" />
        <link rel="apple-touch-icon" href="/icons/apple-touch-icon.png" />
        <meta name="apple-mobile-web-app-capable" content="yes" />
        <meta name="mobile-web-app-capable" content="yes" />
      </head>
      <body style={{margin:0,background:'#0a0a0f',color:'#e2e8f0',fontFamily:"'Segoe UI',system-ui,-apple-system,sans-serif",minHeight:'100vh',overscrollBehavior:'none'}}>
        {children}
      </body>
    </html>
  )
}
