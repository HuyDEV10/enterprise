# Self-contained PowerShell configuration tests; never calls Docker or starts Java.
$ErrorActionPreference = "Stop"
$source = Join-Path (Split-Path -Parent $PSScriptRoot) "start-demo.ps1"
$tokens = $null
$parseErrors = $null
[void][Management.Automation.Language.Parser]::ParseFile($source, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count -ne 0) { throw "start-demo.ps1 has parsing errors." }
$originalDirectory = Get-Location
$fixture = Join-Path ([IO.Path]::GetTempPath()) ("enterprise-demo-config-" + [guid]::NewGuid().ToString("N"))
$fixtureScripts = Join-Path $fixture "scripts"
[void](New-Item -ItemType Directory -Path $fixtureScripts -Force)
Copy-Item $source (Join-Path $fixtureScripts "start-demo.ps1")
$script = Join-Path $fixtureScripts "start-demo.ps1"
$config = Join-Path $fixture ".env.demo"
$global:EnterpriseDemoFixturePassword = 'Fixture-$#''-Unicode-12345'
function Read-Host {
    param([string]$Prompt, [switch]$AsSecureString)
    return ConvertTo-SecureString $global:EnterpriseDemoFixturePassword -AsPlainText -Force
}
function Require([bool]$condition, [string]$message) { if (-not $condition) { throw $message } }
try {
    & $script -ConfigureOnly
    Require (Test-Path $config) "Configuration was not created."
    $first = [IO.File]::ReadAllText($config)
    Require (-not $first.Contains($global:EnterpriseDemoFixturePassword)) "Raw password must not be embedded in dotenv syntax."
    $encoded = (($first -split "`n" | Where-Object { $_.StartsWith("DEMO_PASSWORD_BASE64=") }) -split "=", 2)[1].Trim()
    Require ([Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($encoded)) -ceq $global:EnterpriseDemoFixturePassword) "Password characters did not round-trip."
    & $script -ConfigureOnly
    Require ([IO.File]::ReadAllText($config) -ceq $first) "A normal restart must preserve keys and passwords."
    & $script -ConfigureOnly -DbPort 5435 -ServerPort 8081
    $ports = [IO.File]::ReadAllText($config)
    Require ($ports.Contains("DB_PORT=5435") -and $ports.Contains("SERVER_PORT=8081")) "Explicit ports were ignored."
    & $script -ConfigureOnly
    Require ([IO.File]::ReadAllText($config) -ceq $ports) "Configured ports must survive a normal restart."
    Remove-Item $config -Force
    $global:EnterpriseDemoFixturePassword = "short"
    $rejected = $false
    try { & $script -ConfigureOnly } catch { $rejected = $true }
    Require $rejected "Short passwords must be rejected."
    Require (-not (Test-Path $config)) "Invalid credentials must not leave a partial configuration."
    Write-Host "PASS: parser, password characters, repeat configuration, port persistence, invalid password rollback."
} finally {
    Set-Location $originalDirectory
    Remove-Item $fixture -Recurse -Force
    Remove-Variable EnterpriseDemoFixturePassword -Scope Global -ErrorAction SilentlyContinue
}
