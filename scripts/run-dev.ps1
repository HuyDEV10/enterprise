param(
    [switch]$ResetDatabase
)

$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker CLI was not found. Start/install Docker Desktop first."
}

if ($ResetDatabase) {
    Write-Host "Resetting project PostgreSQL volume..."
    docker compose down -v --remove-orphans
}

Write-Host "Starting PostgreSQL..."
docker compose up -d postgres

$container = "enterprise_risk_postgres"
$healthy = $false
for ($i = 0; $i -lt 30; $i++) {
    $status = docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $container 2>$null
    if ($status -eq "healthy") {
        $healthy = $true
        break
    }
    Start-Sleep -Seconds 2
}

if (-not $healthy) {
    docker compose logs postgres
    throw "PostgreSQL did not become healthy."
}

Write-Host "PostgreSQL is healthy on localhost:5433. Starting Spring Boot..."
& .\mvnw.cmd -f .\backend\pom.xml spring-boot:run
