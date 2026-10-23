[CmdletBinding()]
param(
    [string]$RunDir = ('data/processed/sentiment-runs/' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')),
    [ValidateRange(1, 100)][int]$Epochs = 5,
    [ValidateRange(1, 128)][int]$BatchSize = 8,
    [ValidateRange(64, 512)][int]$MaxLength = 128,
    [switch]$Resume,
    [switch]$SkipDownload,
    [switch]$Promote
)

$ErrorActionPreference = 'Stop'
$taskAiRoot = Join-Path (Split-Path -Parent $PSScriptRoot) 'ai-service'
$taskPython = Join-Path $taskAiRoot '.venv/Scripts/python.exe'
if (-not (Test-Path -LiteralPath $taskPython)) {
    throw 'Create ai-service/.venv and install requirements.txt plus requirements-sentiment.txt first.'
}

function Invoke-SentimentPhase {
    param([string[]]$PhaseArguments)
    & $taskPython -u -m training.sentiment.pipeline @PhaseArguments
    if ($LASTEXITCODE -ne 0) { throw "Sentiment phase failed: $($PhaseArguments[0])" }
}

Push-Location -LiteralPath $taskAiRoot
try {
    New-Item -ItemType Directory -Force .cache/tmp | Out-Null
    $env:TMPDIR = (Resolve-Path .cache/tmp).Path
    $env:PYTHONUTF8 = '1'
    $env:OPENBLAS_NUM_THREADS = '1'
    $env:OMP_NUM_THREADS = '4'
    $env:MKL_NUM_THREADS = '4'
    $env:TOKENIZERS_PARALLELISM = 'false'
    $taskEnvironmentSnapshot = & $taskPython -m pip freeze --all
    if ($LASTEXITCODE -ne 0) { throw 'Could not capture the Python environment.' }
    if (-not $SkipDownload) { Invoke-SentimentPhase -PhaseArguments @('download') }
    if (-not $Resume) { Invoke-SentimentPhase -PhaseArguments @('prepare') }
    $env:HF_HUB_OFFLINE = '1'
    $env:TRANSFORMERS_OFFLINE = '1'
    $taskTrainArguments = @('train', '--run-dir', $RunDir, '--epochs', "$Epochs", '--batch-size', "$BatchSize", '--max-length', "$MaxLength")
    if ($Resume) { $taskTrainArguments += '--resume' }
    Invoke-SentimentPhase -PhaseArguments $taskTrainArguments
    $taskRunPath = if ([IO.Path]::IsPathRooted($RunDir)) { $RunDir } else { Join-Path $taskAiRoot $RunDir }
    $taskSnapshotName = if ($Resume) { 'environment-resume-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ') + '.txt' } else { 'environment.txt' }
    $taskEnvironmentSnapshot | Set-Content -LiteralPath (Join-Path $taskRunPath $taskSnapshotName) -Encoding utf8
    Invoke-SentimentPhase -PhaseArguments @('evaluate', '--run-dir', $RunDir)
    if ($Promote) { Invoke-SentimentPhase -PhaseArguments @('promote', '--run-dir', $RunDir) }
    Write-Output "Completed. Reports: $taskAiRoot/$RunDir"
} finally {
    Pop-Location
}
