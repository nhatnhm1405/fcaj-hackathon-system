<#
.SYNOPSIS
    Run the SEAL app in code-first mode and seed one demo scenario (S1/S25/S3).

.DESCRIPTION
    Bundles the manual steps into a single command:
      1. Read DB_USERNAME / DB_PASSWORD from .env (per-machine password -> not hardcoded)
      2. DROP DATABASE seal_hackathon  (so DemoSeeder reseeds from scratch)
      3. set SEED_SCENARIO = chosen scenario
      4. ./mvnw.cmd spring-boot:run     (Hibernate recreates schema + seeds)

    Why drop first: DemoSeeder is guarded by the demo accounts -> if the DB still has
    old data it SKIPS seeding. A clean drop means a clean reseed.

.PARAMETER Scenario
    NONE | S1 | S25 | S3  (default NONE = real run, no fake data)
      NONE  bootstrap admin only
      S1    all demo accounts + OPEN event + track/round/criteria + 15 forming teams.
            Everything from here — SETUP config, leftover grouping, track draw
            (default RANDOM; SELF_SELECT also works), starting the event,
            submitting, and judge scoring with the round timer running — is
            demoed live through the app rather than pre-seeded.
      S25   + prelim finalized + final round fully scored,
            ready to calculate the final ranking then award    (IN_PROGRESS)
      S3    + rankings + final scores/results + prizes         (COMPLETED)

.PARAMETER Force
    Drop the DB without a confirmation prompt (handy during a live demo).

.PARAMETER NoDrop
    Skip the drop step (keep the current DB; seeding is skipped if data already exists).

.EXAMPLE
    ./run-demo.ps1 S3            # full demo: scores, rankings, prizes
.EXAMPLE
    ./run-demo.ps1 S1 -Force     # live-flow demo from OPEN registration onward
.EXAMPLE
    ./run-demo.ps1               # NONE - clean run
#>
[CmdletBinding()]
param(
    [ValidateSet('NONE', 'S1', 'S25', 'S3')]
    [string]$Scenario = 'NONE',
    [switch]$Force,
    [switch]$NoDrop
)

$ErrorActionPreference = 'Stop'
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$DbName = 'seal_hackathon'

function Write-Step($msg) { Write-Host "`n>> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "   OK  $msg" -ForegroundColor Green }
function Write-Warn2($msg){ Write-Host "   !   $msg" -ForegroundColor Yellow }

function Resolve-MavenCommand {
    $mvnOnPath = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($mvnOnPath) { return $mvnOnPath.Source }

    $wrapperProps = Join-Path $ScriptDir '.mvn\wrapper\maven-wrapper.properties'
    if (Test-Path $wrapperProps) {
        $distributionUrl = (Get-Content -Raw $wrapperProps | ConvertFrom-StringData).distributionUrl
        if ($distributionUrl) {
            $distributionName = $distributionUrl -replace '^.*/',''
            $distributionMain = $distributionName -replace '\.[^.]*$','' -replace '-bin$',''
            $m2Root = if ($env:MAVEN_USER_HOME) { $env:MAVEN_USER_HOME } else { Join-Path $HOME '.m2' }
            $cachedDists = Join-Path $m2Root "wrapper\dists\$distributionMain"
            if (Test-Path $cachedDists) {
                $cachedMaven = Get-ChildItem -Path $cachedDists -Recurse -Filter 'mvn.cmd' -ErrorAction SilentlyContinue |
                    Where-Object { $_.FullName -match '\\bin\\mvn\.cmd$' } |
                    Sort-Object FullName -Descending |
                    Select-Object -First 1
                if ($cachedMaven) { return $cachedMaven.FullName }
            }
        }
    }

    return (Join-Path $ScriptDir 'mvnw.cmd')
}

# ─── 1. Doc DB creds tu .env ──────────────────────────────────────────────────
$envFile = Join-Path $ScriptDir '.env'
$dbUser = 'root'; $dbPass = ''
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*DB_USERNAME\s*=\s*(.*)$') { $dbUser = $Matches[1].Trim().Trim('"') }
        if ($_ -match '^\s*DB_PASSWORD\s*=\s*(.*)$') { $dbPass = $Matches[1].Trim().Trim('"') }
    }
    Write-Ok ".env loaded (DB_USERNAME='$dbUser')"
} else {
    Write-Warn2 ".env not found -> falling back to user='root', no password"
}

# ─── 2. Dinh vi mysql.exe (PATH -> Program Files) ────────────────────────────
function Find-Mysql {
    $cmd = Get-Command mysql -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = Get-ChildItem 'C:\Program Files\MySQL' -Recurse -Filter 'mysql.exe' -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match 'Server' } |
        Sort-Object FullName -Descending
    if ($candidates) { return $candidates[0].FullName }
    return $null
}

# ─── 3. Drop DB ──────────────────────────────────────────────────────────────
if (-not $NoDrop) {
    $mysql = Find-Mysql
    if (-not $mysql) {
        Write-Warn2 "mysql.exe not found -> cannot drop the DB automatically."
        Write-Warn2 "Drop it manually:  DROP DATABASE $DbName;  then re-run with -NoDrop"
        throw "mysql.exe not found"
    }
    Write-Ok "mysql: $mysql"

    if (-not $Force) {
        $ans = Read-Host "About to DROP DATABASE '$DbName' (all current data is lost). Type 'y' to continue"
        if ($ans -ne 'y') { Write-Warn2 "Cancelled."; return }
    }

    Write-Step "Dropping database '$DbName'..."
    $args = @("-u$dbUser")
    if ($dbPass) { $args += "-p$dbPass" }
    $args += @('-e', "DROP DATABASE IF EXISTS ``$DbName``;")
    & $mysql @args
    if ($LASTEXITCODE -ne 0) { throw "Drop failed (check DB_USERNAME/DB_PASSWORD in .env)" }
    Write-Ok "Dropped '$DbName' (the app will recreate it)"
} else {
    Write-Warn2 "-NoDrop: skipping drop. If the DB already has demo data, seeding will be SKIPPED."
}

# ─── 4. Set kich ban + chay app ──────────────────────────────────────────────
$env:SEED_SCENARIO = $Scenario
Write-Step "Starting app  |  SEED_SCENARIO=$Scenario"
Write-Ok "Demo accounts: coordinator@fpt.edu.vn / judge1@ / p1@ ...  (password: Test@1234)"
Write-Ok "Bootstrap admin: admin@fpt.edu.vn / Test@1234"

Set-Location $ScriptDir
$mavenCmd = Resolve-MavenCommand
Write-Ok "Maven: $mavenCmd"
& $mavenCmd spring-boot:run
