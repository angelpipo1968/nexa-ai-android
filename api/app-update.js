// Vercel Serverless Function: /api/app-update
// Returns the latest app version info for auto-update

export default function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET');
  res.setHeader('Cache-Control', 's-maxage=300, stale-while-revalidate');

  res.status(200).json({
    versionCode: 2,
    versionName: "2.1",
    downloadUrl: "https://github.com/angelpipo1968/nexa-ai-android/releases/latest",
    changelog: "✅ Login con email y contraseña\n✅ Registro de nuevos usuarios\n✅ Sidebar con historial de chats\n✅ Tema oscuro/claro\n✅ Selección de voz hombre/mujer\n✅ Idioma Español/English\n✅ Auto-actualización desde nexa-ai.dev",
    forceUpdate: false
  });
}
