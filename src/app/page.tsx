'use client';
import { NexaApp } from '@/components/NexaApp';

// Force Next.js to generate this page as static HTML for Capacitor
export function generateStaticParams() {
    return [{}];
}

export default function Home() {
    return <NexaApp />;
}
