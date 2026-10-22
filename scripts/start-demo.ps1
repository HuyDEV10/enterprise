param(
    [ValidateRange(1024, 65535)][int]$DbPort = 5434,
    [ValidateRange(1024, 65535)][int]$ServerPort = 8080,
    [switch]$ResetDemo,
    [switch]$ConfigureOnly
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot
$configPath = Join-Path $projectRoot ".env.demo"

function New-RandomBase64 {
    $bytes = New-Object byte[] 48
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes) } finally { $random.Dispose() }
    return [Convert]::ToBase64String($bytes)
}

function Read-DemoConfig {
    $result = @{}
    if (Test-Path $configPath) {
        foreach ($line in Get-Content -LiteralPath $configPath -Encoding UTF8) {
            if ($line.Trim() -and -not $line.Trim().StartsWith("#") -and $line.Contains("=")) {
                $parts = $line.Split("=", 2)
                $result[$parts[0].Trim()] = $parts[1]
            }
        }
    }
    return $result
}

$config = Read-DemoConfig
if (-not $config.ContainsKey("DEMO_PASSWORD_BASE64")) {
    Write-Host "Create a password for the local demo accounts (12+ characters, max 72 UTF-8 bytes)."
    Write-Host "This is stored in the ignored .env.demo file and is never printed."
    $first = Read-Host "Demo password" -AsSecureString
    $second = Read-Host "Confirm password" -AsSecureString
    $password = (New-Object System.Net.NetworkCredential("", $first)).Password
    $confirmation = (New-Object System.Net.NetworkCredential("", $second)).Password
    if ($password -cne $confirmation) { throw "Passwords do not match. Run the script again." }
    if ($password.Length -lt 12 -or [Text.Encoding]::UTF8.GetByteCount($password) -gt 72 -or $password.Contains("`n") -or $password.Contains("`r")) {
        throw "Use 12+ characters, at most 72 UTF-8 bytes, without newlines."
    }
    $config["DEMO_PASSWORD_BASE64"] = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($password))
    $config["JWT_SECRET"] = New-RandomBase64
    $config["DB_PASSWORD"] = New-RandomBase64
    $password = $null
    $confirmation = $null
}
foreach ($required in @("DEMO_PASSWORD_BASE64", "JWT_SECRET", "DB_PASSWORD")) {
    if (-not $config.ContainsKey($required) -or [string]::IsNullOrWhiteSpace($config[$required])) {
        throw "Missing $required in .env.demo. Restore this file or configure a new isolated demo environment."
    }
}
$config["DB_NAME"] = "enterprise_demo"
$config["DB_USER"] = "postgres"
# Retain configured ports on normal restarts; explicit parameters take precedence.
if ($PSBoundParameters.ContainsKey("DbPort") -or -not $config.ContainsKey("DB_PORT")) { $config["DB_PORT"] = "$DbPort" }
if ($PSBoundParameters.ContainsKey("ServerPort") -or -not $config.ContainsKey("SERVER_PORT")) { $config["SERVER_PORT"] = "$ServerPort" }
$dbPortValue = [int]$config["DB_PORT"]
$serverPortValue = [int]$config["SERVER_PORT"]
if ($dbPortValue -lt 1024 -or $dbPortValue -gt 65535 -or $serverPortValue -lt 1024 -or $serverPortValue -gt 65535 -or $dbPortValue -eq $serverPortValue) {
    throw "DB_PORT and SERVER_PORT must be different ports in the range 1024..65535."
}
$lines = @("# Local demo only. Do not commit or share this file.")
foreach ($key in @("DB_PORT", "DB_NAME", "DB_USER", "DB_PASSWORD", "SERVER_PORT", "JWT_SECRET", "DEMO_PASSWORD_BASE64")) {
    $lines += "$key=$($config[$key])"
}
[IO.File]::WriteAllLines($configPath, [string[]]$lines, (New-Object Text.UTF8Encoding($false)))
if ($ConfigureOnly) { Write-Host "Demo configuration is ready in .env.demo (secrets hidden)."; return }

foreach ($command in @("docker", "java")) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) { throw "$command was not found. Install prerequisites from docs/LOCAL-DEMO-WINDOWS.md." }
}
& docker info --format '{{.ServerVersion}}' *> $null
if ($LASTEXITCODE -ne 0) { throw "Start Docker Desktop and wait until the engine is ready." }

$keys = @("DB_PORT", "DB_NAME", "DB_USER", "DB_PASSWORD", "SERVER_PORT", "JWT_SECRET", "DEMO_PASSWORD",
    "SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD",
    "SPRING_PROFILES_ACTIVE", "BOOTSTRAP_ADMIN_ENABLED", "RISK_MONITORING_ENABLED", "AI_SERVICE_BASE_URL")
$previous = @{}
foreach ($key in $keys) { $previous[$key] = [Environment]::GetEnvironmentVariable($key, "Process") }
try {
    foreach ($key in @("DB_PORT", "DB_NAME", "DB_USER", "DB_PASSWORD", "SERVER_PORT", "JWT_SECRET")) {
        [Environment]::SetEnvironmentVariable($key, $config[$key], "Process")
    }
    $env:DEMO_PASSWORD = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($config["DEMO_PASSWORD_BASE64"]))
    # Pass profiles explicitly too: production settings inherited by the shell must not win silently.
    $env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:$dbPortValue/enterprise_demo"
    $env:SPRING_DATASOURCE_USERNAME = $config["DB_USER"]
    $env:SPRING_DATASOURCE_PASSWORD = $config["DB_PASSWORD"]
    $env:SPRING_PROFILES_ACTIVE = "demo"
    $env:BOOTSTRAP_ADMIN_ENABLED = "false"
    $env:RISK_MONITORING_ENABLED = "false"
    $env:AI_SERVICE_BASE_URL = "http://localhost:8000"
    $compose = @("compose", "--project-name", "enterprise-demo", "--env-file", $configPath, "--file", "docker-compose.demo.yml")
    if ($ResetDemo) {
        Write-Host "Resetting only the enterprise-demo PostgreSQL volume."
        & docker @compose down --volumes
        if ($LASTEXITCODE -ne 0) { throw "Could not reset the isolated demo database." }
    }
    & docker @compose up -d --wait --wait-timeout 90 postgres
    if ($LASTEXITCODE -ne 0) { throw "Demo PostgreSQL did not become ready. Check Docker Desktop and port $dbPortValue." }
    Write-Host "Demo PostgreSQL: localhost:$dbPortValue / enterprise_demo"
    Write-Host "Starting backend: http://localhost:$serverPortValue"
    Write-Host "Wait for the backend dataset-ready log, then sign in as demo_admin with the password you chose."
    & .\mvnw.cmd -f backend/pom.xml spring-boot:run "-Dspring-boot.run.profiles=demo"
    if ($LASTEXITCODE -ne 0) { throw "Backend exited with an error. Inspect the log above." }
} finally {
    foreach ($key in $keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], "Process") }
}
