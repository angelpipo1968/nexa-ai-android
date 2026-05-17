# sync-env.ps1 — Sincroniza todas las API keys a Vercel de una sola vez
# Uso: .\sync-env.ps1
# Requiere: Vercel CLI instalado (npm i -g vercel)
# Las keys se separan por coma para rotación automática

Write-Host "🚀 Sincronizando API keys a Vercel..." -ForegroundColor Cyan
Write-Host ""

# ─── CONFIGURACIÓN ───
# Editá estos valores con tus keys reales (separadas por coma si tenés varias)

$envVars = @{
    # Groq (3 keys separadas por coma)
    "GROQ_API_KEY" = "gsk_xxx,gsk_yyy,gsk_zzz"

    # OpenAI (3 keys separadas por coma)
    "OPENAI_API_KEY" = "sk-xxx,sk-yyy,sk-zzz"

    # Anthropic (3 keys separadas por coma)
    "ANTHROPIC_API_KEY" = "sk-ant-xxx,sk-ant-yyy,sk-ant-zzz"

    # Google Gemini (3 keys separadas por coma)
    "GEMINI_API_KEY" = "AIza-xxx,AIza-yyy,AIza-zzz"
}

# ─── SINCRONIZACIÓN ───
$success = 0
$failed = 0

foreach ($name in $envVars.Keys) {
    $value = $envVars[$name]

    # Saltear si no se editó (tiene placeholder)
    if ($value -match "xxx|yyy|zzz") {
        Write-Host "⏭️  Saltando $name (placeholder detectado)" -ForegroundColor Yellow
        continue
    }

    Write-Host "📤 Subiendo $name..." -NoNewline

    # Eliminar la variable existente primero (ignorar error si no existe)
    echo $value | vercel env rm $name production --yes 2>$null

    # Agregar la nueva
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
Write-Host "💡 Después de sincronizar, hacés redeploy en Vercel:" -ForegroundColor Yellow
Write-Host "   vercel --prod" -ForegroundColor White
