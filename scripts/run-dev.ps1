param(
    [switch]$ResetDatabase
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker CLI was not found. Start/install Docker Desktop first."
}

function Read-DotEnv {
    param([string]$Path)

    $values = @{}
    if (-not (Test-Path $Path)) {
        return $values
    }

    foreach ($line in Get-Content $Path) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#") -or -not $trimmed.Contains("=")) {
            continue
        }

        $parts = $trimmed.Split("=", 2)
        $values[$parts[0].Trim()] = $parts[1].Trim().Trim('"').Trim("'")
    }

    return $values
}

function Resolve-Setting {
    param(
        [string]$Name,
        [string]$DefaultValue,
        [hashtable]$DotEnv
    )

    $processValue = [Environment]::GetEnvironmentVariable($Name, "Process")
    if (-not [string]::IsNullOrWhiteSpace($processValue)) {
        return $processValue
    }

    if ($DotEnv.ContainsKey($Name) -and -not [string]::IsNullOrWhiteSpace($DotEnv[$Name])) {
        return $DotEnv[$Name]
    }

    return $DefaultValue
}

$dotEnv = Read-DotEnv (Join-Path $projectRoot ".env")
$dbPort = Resolve-Setting "DB_PORT" "5433" $dotEnv
$dbName = Resolve-Setting "DB_NAME" "enterprise_risk_db" $dotEnv
$dbUser = Resolve-Setting "DB_USER" "postgres" $dotEnv
$dbPassword = Resolve-Setting "DB_PASSWORD" "postgres" $dotEnv
$serverPort = Resolve-Setting "SERVER_PORT" "8080" $dotEnv

# DB_* is the only local database configuration source used by Docker and Spring Boot.
$env:DB_PORT = $dbPort
$env:DB_NAME = $dbName
$env:DB_USER = $dbUser
$env:DB_PASSWORD = $dbPassword
$env:SERVER_PORT = $serverPort

if ($ResetDatabase) {
    Write-Host "Resetting project PostgreSQL volume..."
    docker compose down -v --remove-orphans
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to reset Docker Compose resources."
    }
}

Write-Host "Starting PostgreSQL on localhost:$dbPort..."
docker compose up -d postgres
if ($LASTEXITCODE -ne 0) {
    throw "Failed to start PostgreSQL container."
}

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

# POSTGRES_PASSWORD only applies when a PostgreSQL volume is initialized for the first time.
# If the user already has a volume with an older password, synchronize the database role
# through the container's local socket so existing data does not need to be deleted.
$escapedUser = $dbUser.Replace('"', '""')
$escapedPassword = $dbPassword.Replace("'", "''")
$alterRoleSql = "ALTER ROLE `"$escapedUser`" WITH PASSWORD '$escapedPassword';"

Write-Host "Synchronizing PostgreSQL role password with project configuration..."
docker exec $container psql --username $dbUser --dbname postgres --set ON_ERROR_STOP=1 --command $alterRoleSql
if ($LASTEXITCODE -ne 0) {
    throw "Could not synchronize PostgreSQL password. If this development volume is disposable, run .\scripts\run-dev.ps1 -ResetDatabase once."
}

Write-Host "Verifying PostgreSQL TCP authentication..."
docker exec --env "PGPASSWORD=$dbPassword" $container psql --host 127.0.0.1 --port 5432 --username $dbUser --dbname $dbName --set ON_ERROR_STOP=1 --command "SELECT 1;"
if ($LASTEXITCODE -ne 0) {
    throw "PostgreSQL TCP authentication verification failed."
}

Write-Host "PostgreSQL authentication is ready. Starting Spring Boot on localhost:$serverPort..."
& .\mvnw.cmd -f .\backend\pom.xml spring-boot:run
if ($LASTEXITCODE -ne 0) {
    throw "Spring Boot exited with code $LASTEXITCODE."
}
