[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^http://localhost(?::\d+)?(?:/.*)?$')]
    [string]$Url,

    [switch]$ValidateOnly
)

$ErrorActionPreference = 'Stop'

$edgeCandidates = New-Object 'System.Collections.Generic.List[string]'
if (${env:ProgramFiles(x86)}) {
    $edgeCandidates.Add((Join-Path ${env:ProgramFiles(x86)} 'Microsoft\Edge\Application\msedge.exe'))
}
if ($env:ProgramFiles) {
    $edgeCandidates.Add((Join-Path $env:ProgramFiles 'Microsoft\Edge\Application\msedge.exe'))
}
if ($env:LOCALAPPDATA) {
    $edgeCandidates.Add((Join-Path $env:LOCALAPPDATA 'Microsoft\Edge\Application\msedge.exe'))
}

$edgePath = $edgeCandidates | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
if (-not $edgePath) {
    $edgeCommand = Get-Command msedge.exe -ErrorAction SilentlyContinue
    if ($edgeCommand) {
        $edgePath = $edgeCommand.Source
    }
}
if (-not $edgePath) {
    throw 'Microsoft Edge was not found. Install Edge or add msedge.exe to PATH.'
}

if (-not $env:LOCALAPPDATA) {
    throw 'LOCALAPPDATA is not available; cannot create the dedicated Edge review profile.'
}
$profilePath = Join-Path $env:LOCALAPPDATA 'SEAL-E2E\scenario-1-edge-review'

if ($ValidateOnly) {
    Write-Host "[S1-REVIEW] Edge: $edgePath"
    Write-Host "[S1-REVIEW] Profile: $profilePath"
    Write-Host "[S1-REVIEW] URL: $Url"
    exit 0
}

New-Item -ItemType Directory -Path $profilePath -Force | Out-Null
$edgeArguments = @(
    "--user-data-dir=`"$profilePath`""
    '--no-first-run'
    '--disable-features=msEdgeFirstRunExperience'
    '--new-window'
    $Url
)

Start-Process -FilePath $edgePath -ArgumentList $edgeArguments
Write-Host "[S1-REVIEW] Opened Edge review window: $Url"
Write-Host "[S1-REVIEW] This dedicated profile keeps the localhost login between phases."
