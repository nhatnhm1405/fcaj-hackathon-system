<#
.SYNOPSIS
    Run the SEAL app in code-first mode and seed one demo scenario (S1/S25/S3).

.DESCRIPTION
    Bundles the manual steps into a single command:
      1. Validate local DB/bootstrap settings before any database reset
      2. DROP DATABASE seal_hackathon  (so DemoSeeder reseeds from scratch)
      3. set SEED_SCENARIO = chosen scenario
      4. ./mvnw.cmd spring-boot:run     (Flyway creates schema, Hibernate validates, seeders run)

    Why drop first: DemoSeeder is guarded by the demo accounts -> if the DB still has
    old data it SKIPS seeding. A clean drop means a clean reseed.

.PARAMETER Scenario
    NONE | S1 | S25 | S3  (default NONE = real run, no fake data)
      NONE  reference roles/rubric only; admin creation requires explicit bootstrap configuration
      S1    all demo accounts + OPEN event + track/round/criteria + 15 forming teams.
            Everything from here — SETUP config, leftover grouping, track draw
            (default RANDOM; SELF_SELECT also works), starting the event,
            submitting, and judge scoring with the round timer running — is
            demoed live through the app rather than pre-seeded.
      S25   + prelim finalized + final round fully scored,
            ready to calculate the final ranking then award    (IN_PROGRESS)
      S3    + rankings + final scores/results + prizes         (COMPLETED)
            A fresh S3 run requires BOOTSTRAP_ADMIN_ENABLED=true plus externally
            supplied BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD. With
            -NoDrop, an existing global admin may be reused by configuring its email.

    Process environment overrides .env aliases. This runner accepts plain key=value
    settings in .env; use process environment for escaped/continued values. Quotation
    marks in .env values are literal, as in Java properties; they are not stripped.
    The runner is restricted to the local localhost:3306/seal_hackathon database.

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

# Helpers are independent of MySQL/Maven so preflight can be tested without running
# any destructive command. Return values containing credentials must never be logged.
function Read-DemoEnvSettings([string]$Path) {
    $settings = @{}
    if (-not (Test-Path -LiteralPath $Path)) { return $settings }
    $relevant = @('DB_USERNAME', 'DB_PASSWORD', 'BOOTSTRAP_ADMIN_ENABLED',
        'BOOTSTRAP_ADMIN_EMAIL', 'BOOTSTRAP_ADMIN_PASSWORD', 'spring.datasource.url',
        'spring.datasource.username', 'spring.datasource.password',
        'app.bootstrap.admin.enabled', 'app.bootstrap.admin.email', 'app.bootstrap.admin.password')
    foreach ($line in (Get-Content -LiteralPath $Path -Encoding UTF8)) {
        if ($line -match '^\s*(?:#|!|$)') { continue }
        if ($line -match '^\s*([A-Za-z0-9_.-]+)(?:\s|:|=|$)') {
            $key = $Matches[1]
            if ($key -match '^(?:spring\.(?:profiles|config)\.|SPRING_(?:PROFILES|CONFIG)_)') {
                throw 'run-demo does not accept profile/config overrides inside .env. Select the standard local profile in process environment.'
            }
            if ($key -notin $relevant) { continue }
            if ($line -notmatch '^\s*([A-Za-z0-9_.-]+)\s*=\s*(.*)$' -or $line.Contains('\')) {
                throw "run-demo requires plain key=value for $key in .env. Supply escaped/continued values through process environment instead."
            }
            $settings[$key] = $Matches[2]
        }
    }
    return $settings
}

function Resolve-DemoSetting([hashtable]$ProcessSettings, [hashtable]$FileSettings,
        [string]$Property, [string]$Alias, [string]$Default = '') {
    $canonicalEnv = $Property.Replace('.', '_').Replace('-', '').ToUpperInvariant()
    if ($ProcessSettings.ContainsKey($canonicalEnv)) { return $ProcessSettings[$canonicalEnv] }
    if ($FileSettings.ContainsKey($Property)) { return $FileSettings[$Property] }
    if ($Alias -and $ProcessSettings.ContainsKey($Alias)) { return $ProcessSettings[$Alias] }
    if ($Alias -and $FileSettings.ContainsKey($Alias)) { return $FileSettings[$Alias] }
    return $Default
}

function Assert-DemoPreflight([string]$RequestedScenario, [bool]$KeepDatabase,
        [hashtable]$ProcessSettings, [hashtable]$FileSettings) {
    $profiles = $ProcessSettings['SPRING_PROFILES_ACTIVE']
    if (-not $profiles) { $profiles = $ProcessSettings['SPRING_PROFILES_DEFAULT'] }
    if ($profiles) {
        $selected = @($profiles.Split(',') | ForEach-Object { $_.Trim() })
        if ('local' -notin $selected -or @($selected | Where-Object { $_ -notin @('local', 'oauth-google', 'oauth-github') }).Count -gt 0) {
            throw 'run-demo requires the local profile; AWS/test/custom profiles cannot be used by this reset helper.'
        }
    }
    foreach ($override in @('SPRING_APPLICATION_JSON', 'SPRING_CONFIG_LOCATION',
            'SPRING_CONFIG_ADDITIONAL_LOCATION', 'SPRING_CONFIG_IMPORT', 'SPRING_CONFIG_NAME',
            'SPRING_PROFILES_INCLUDE', 'SPRING_DATASOURCE_JNDI_NAME')) {
        if ($ProcessSettings[$override]) {
            throw "run-demo cannot safely resolve $override. Use the standard local configuration for this reset helper."
        }
    }
    $dbUrl = Resolve-DemoSetting $ProcessSettings $FileSettings 'spring.datasource.url' ''
    if ($dbUrl -and $dbUrl -notmatch '^jdbc:mysql://localhost:3306/seal_hackathon(?:\?|$)') {
        throw 'run-demo supports only the local localhost:3306/seal_hackathon datasource.'
    }
    $enabled = Resolve-DemoSetting $ProcessSettings $FileSettings 'app.bootstrap.admin.enabled' 'BOOTSTRAP_ADMIN_ENABLED' 'false'
    if ($enabled -notin @('true', 'false')) {
        throw 'BOOTSTRAP_ADMIN_ENABLED must be true or false without trailing whitespace.'
    }
    $email = Resolve-DemoSetting $ProcessSettings $FileSettings 'app.bootstrap.admin.email' 'BOOTSTRAP_ADMIN_EMAIL'
    $password = Resolve-DemoSetting $ProcessSettings $FileSettings 'app.bootstrap.admin.password' 'BOOTSTRAP_ADMIN_PASSWORD'
    if (@($email, $password) | Where-Object { $_ -and $_.Contains('${') }) {
        throw 'run-demo requires resolved bootstrap values; nested property placeholders are not supported by this preflight.'
    }
    if ($RequestedScenario -eq 'S3' -and -not $KeepDatabase -and $enabled -ne 'true') {
        throw 'A fresh S3 demo requires BOOTSTRAP_ADMIN_ENABLED=true and externally supplied admin credentials before the database can be dropped.'
    }
    if ($enabled -eq 'true' -or $RequestedScenario -eq 'S3') {
        try {
            $normalizedEmail = $email.Trim().ToLowerInvariant()
            $address = [System.Net.Mail.MailAddress]::new($normalizedEmail)
            if ($normalizedEmail.Length -gt 255 -or $address.Address -ne $normalizedEmail -or
                    $normalizedEmail -notmatch '^[^\s@<>]+@[^\s@<>]+$' -or
                    $address.User.StartsWith('.') -or $address.User.EndsWith('.') -or $address.User.Contains('..')) {
                throw 'Invalid email'
            }
        } catch {
            throw 'BOOTSTRAP_ADMIN_EMAIL must be a valid email address for bootstrap or the S3 demo.'
        }
    }
    if ($enabled -eq 'true' -and ([string]::IsNullOrWhiteSpace($password) -or $password.Length -lt 8 -or
            [System.Text.Encoding]::UTF8.GetByteCount($password) -gt 72)) {
        throw 'BOOTSTRAP_ADMIN_PASSWORD must contain at least 8 characters and at most 72 UTF-8 bytes before the database can be dropped.'
    }
}

# ─── 1. Validate configuration BEFORE finding mysql or prompting for DROP ─────
$envFile = Join-Path $ScriptDir '.env'
$fileSettings = Read-DemoEnvSettings $envFile
$processSettings = @{}
Get-ChildItem Env: | ForEach-Object { $processSettings[$_.Name] = $_.Value }
Assert-DemoPreflight $Scenario ([bool]$NoDrop) $processSettings $fileSettings
$dbUser = Resolve-DemoSetting $processSettings $fileSettings 'spring.datasource.username' 'DB_USERNAME' 'root'
$dbPass = Resolve-DemoSetting $processSettings $fileSettings 'spring.datasource.password' 'DB_PASSWORD'
Write-Ok 'Local configuration preflight passed; credential values are not displayed.'

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
    $mysqlArgs = @('--host=127.0.0.1', '--port=3306', "-u$dbUser", '-e', "DROP DATABASE IF EXISTS ``$DbName``;")
    $previousMysqlPassword = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $dbPass
        & $mysql @mysqlArgs
        if ($LASTEXITCODE -ne 0) { throw 'Drop failed (check local database configuration).' }
    } finally {
        $env:MYSQL_PWD = $previousMysqlPassword
    }
    Write-Ok "Dropped '$DbName' (the app will recreate it)"
} else {
    Write-Warn2 "-NoDrop: skipping drop. If the DB already has demo data, seeding will be SKIPPED."
}

# ─── 4. Set kich ban + chay app ──────────────────────────────────────────────
$env:SEED_SCENARIO = $Scenario
Write-Step "Starting app  |  SEED_SCENARIO=$Scenario"
Write-Ok 'Demo accounts follow the selected scenario. See local demo documentation for sign-in details.'
Write-Ok 'Bootstrap administrator is controlled by BOOTSTRAP_ADMIN_* configuration; credentials are not displayed.'

Set-Location $ScriptDir
$mavenCmd = Resolve-MavenCommand
Write-Ok "Maven: $mavenCmd"
& $mavenCmd spring-boot:run
