# sync-env.ps1 — Sincroniza todas las API keys a Vercel
# Uso: .\sync-env.ps1
# Requiere: Vercel CLI instalado (npm i -g vercel)
# Formato: KEY, KEY_2, KEY_3 (una key por variable)

Write-Host "🚀 Sincronizando API keys a Vercel..." -ForegroundColor Cyan
Write-Host ""

# ─── CONFIGURACIÓN ───
# Editá con tus keys reales. Cada key va en su propia variable.

$envVars = @{
    # ── GROQ (3 keys) ──
    "GROQ_API_KEY"   = "gsk_key1_aqui"
    "GROQ_API_KEY_2" = "gsk_key2_aqui"
    "GROQ_API_KEY_3" = "gsk_key3_aqui"

    # ── OPENAI (3 keys) ──
    "OPENAI_API_KEY"   = "sk_key1_aqui"
    "OPENAI_API_KEY_2" = "sk_key2_aqui"
    "OPENAI_API_KEY_3" = "sk_key3_aqui"

    # ── ANTHROPIC (3 keys) ──
    "ANTHROPIC_API_KEY"   = "sk-ant_key1_aqui"
    "ANTHROPIC_API_KEY_2" = "sk-ant_key2_aqui"
    "ANTHROPIC_API_KEY_3" = "sk-ant_key3_aqui"

    # ── GEMINI (3 keys) ──
    "GEMINI_API_KEY"   = "AIza_key1_aqui"
    "GEMINI_API_KEY_2" = "AIza_key2_aqui"
    "GEMINI_API_KEY_3" = "AIza_key3_aqui"
}

# ─── SINCRONIZACIÓN ───
$success = 0
$failed = 0

foreach ($name in $envVars.Keys) {
    $value = $envVars[$name]

    # Saltear si tiene placeholder
    if ($value -match "key[123]_aqui") {
        Write-Host "⏭️  Saltando $name (placeholder)" -ForegroundColor Yellow
        continue
    }

    Write-Host "📤 Subiendo $name..." -NoNewline

    # Eliminar existente (ignorar si no existe)
    echo $value | vercel env rm $name production --yes 2>$null

    # Agregar nueva
    $result = echo $value | vercel env add $name production --yes 2>&1

    if ($LASTEXITCODE -eq 0) {
        Write-Host " ✅" -ForegroundColor Green
        $success++
    } else {
        Write-Host " ❌" -ForegroundColor Red
        Write-Host "   Error: $result" -ForegroundColor Red
        $failed++
    }
}

Write-Host ""
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor DarkGray
Write-Host "✅ Sincronizadas: $success  |  ❌ Fallidas: $failed" -ForegroundColor Cyan
Write-Host ""
Write-Host "💡 Después, redeploy:" -ForegroundColor Yellow
Write-Host "   vercel --prod" -ForegroundColor White
