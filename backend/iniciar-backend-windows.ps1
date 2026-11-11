$ErrorActionPreference = 'Stop'

$backendDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $backendDir

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker no está instalado o no está disponible en PATH. Instala/inicia Docker Desktop y vuelve a ejecutar este script.'
}

$envPath = Join-Path $backendDir '.env'
if (-not (Test-Path $envPath)) {
    Copy-Item (Join-Path $backendDir '.env.example') $envPath
    Write-Host 'Se creó backend/.env desde .env.example.' -ForegroundColor Yellow
    Write-Host 'Edita backend/.env, reemplaza las contraseñas y JWT_SECRET de ejemplo y vuelve a ejecutar este script.' -ForegroundColor Yellow
    exit 1
}

$envText = Get-Content $envPath -Raw
if ($envText -match 'change_this|replace_with_at_least_32_random_bytes') {
    throw 'backend/.env todavía contiene secretos de ejemplo. Cámbialos por valores únicos y seguros.'
}

Write-Host 'Iniciando PostgreSQL y API...' -ForegroundColor Cyan
docker compose up --build -d
if ($LASTEXITCODE -ne 0) { throw 'docker compose no pudo iniciar los servicios.' }

Write-Host 'Esperando respuesta de /health...' -ForegroundColor Cyan
$healthy = $false
for ($attempt = 1; $attempt -le 30; $attempt++) {
    try {
        $response = Invoke-RestMethod -Uri 'http://localhost:8080/health' -TimeoutSec 3
        if ($response.status -eq 'ok' -and $response.database -eq 'connected') {
            $healthy = $true
            break
        }
    } catch {
        Start-Sleep -Seconds 2
    }
}

if (-not $healthy) {
    docker compose ps
    docker compose logs --tail 80 api db
    throw 'La API no quedó saludable. Revisa los logs anteriores y confirma que el puerto 8080 esté libre.'
}

Write-Host 'Backend listo. El emulador Android puede usar http://10.0.2.2:8080.' -ForegroundColor Green
Write-Host 'Para probar desde el PC: http://localhost:8080/health' -ForegroundColor Green
