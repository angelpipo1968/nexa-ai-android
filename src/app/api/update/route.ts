import { NextRequest, NextResponse } from 'next/server';

const CONFIG = {
  latestVersionCode: 51,
  latestVersionName: '5.1',
  downloadUrl: 'https://github.com/angelpipo1968/nexa-ai-android/releases/latest/download/nexa-ai.apk',
  releaseNotes: 'NEXA AI v5.1 — Mejoras de rendimiento, nuevas herramientas y correcciones de errores',
  forceUpdate: false,
};

export async function GET(request: NextRequest) {
  const current = parseInt(request.nextUrl.searchParams.get('currentVersion') || '0');
  const available = current < CONFIG.latestVersionCode;

  return NextResponse.json({
    updateAvailable: available,
    currentVersion: current,
    latestVersionCode: CONFIG.latestVersionCode,
    latestVersionName: CONFIG.latestVersionName,
    downloadUrl: CONFIG.downloadUrl,
    releaseNotes: CONFIG.releaseNotes,
    forceUpdate: CONFIG.forceUpdate && available,
    checkedAt: new Date().toISOString(),
  }, { headers: { 'Cache-Control': 'public, max-age=300' } });
}
