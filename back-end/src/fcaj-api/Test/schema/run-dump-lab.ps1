<#
Create a NEW, isolated MySQL 8.4 Docker lab, restore a reviewed dump, compare
with a Flyway candidate and run ORM validation. Never connects to local MySQL/RDS.
Always stops (does not remove) the lab container, retaining its data for review.
The dump and data-bearing statement files stay outside Git or under target/.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$DumpPath,
    [Parameter(Mandatory=$true)][string]$JdkHome,
    [ValidatePattern('^mysql@sha256:[a-f0-9]{64}$')][string]$Legacy80Image
)
$ErrorActionPreference='Stop'
$project=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$repo=(Resolve-Path (Join-Path $project '../../..')).Path
$docker='C:/Program Files/Docker/Docker/resources/bin/docker.exe'
$image='mysql@sha256:76f0358884cb92d9a086d24e5671c38048d3fef40f2588703d73eb55392d6b69'
$labId='fcaj-migration-lab-'+[guid]::NewGuid().ToString('N').Substring(0,12)
$work=Join-Path $project ('target/'+$labId)
New-Item -ItemType Directory -Path $work | Out-Null
$manifest=Join-Path $repo 'docs/checks/schema-inventory-2026-10-27/dump-static-comparison.json'
node (Join-Path $PSScriptRoot 'prepare-dump-lab.mjs') $DumpPath $manifest (Join-Path $work 'restore-statements')
if($LASTEXITCODE -ne 0) { throw 'Dump preparation failed.' }
$classpath=(Get-Content -Raw (Join-Path $project 'target/schema-tool-classpath.txt')).Trim()
$classes=Join-Path $work 'classes'
New-Item -ItemType Directory -Path $classes | Out-Null
$sources=@(Get-ChildItem (Join-Path $project 'src/main/java/com/fcaj/hackathon/entity') -Filter '*.java' |
    Sort-Object Name | Select-Object -ExpandProperty FullName)
& (Join-Path $JdkHome 'bin/javac.exe') --release 21 -encoding UTF-8 -proc:full -cp $classpath -d $classes @sources (Join-Path $project 'src/main/java/com/fcaj/hackathon/db/SchemaFingerprint.java') (Join-Path $project 'src/main/java/com/fcaj/hackathon/db/DatabaseMigrationMain.java') (Join-Path $PSScriptRoot 'DumpMigrationLab.java')
if($LASTEXITCODE -ne 0) { throw 'Lab utility compile failed; no container started.' }
$previousPassword=$env:MYSQL_ROOT_PASSWORD
$previousUrl=$env:FCAJ_LAB_JDBC_URL
$previousBackup=$env:FCAJ_LAB_BACKUP_PATH
$containerStarted=$false
$legacyStarted=$false
$legacyName=$labId+'-legacy80'
$previousLegacyUrl=$env:FCAJ_LEGACY_JDBC_URL
$previousLegacyContainer=$env:FCAJ_LEGACY_CONTAINER
try {
    $env:MYSQL_ROOT_PASSWORD=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    $containerId=& $docker run --detach --name $labId --label 'fcaj.purpose=migration-lab' --env MYSQL_ROOT_PASSWORD --env 'MYSQL_ROOT_HOST=%' --publish '127.0.0.1::3306' $image --lower-case-table-names=1 --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci
    if($LASTEXITCODE -ne 0) { throw 'Cannot create new lab container.' }
    $containerStarted=$true
    $binding=& $docker port $labId 3306/tcp
    if($LASTEXITCODE -ne 0 -or $binding -notmatch '^127\.0\.0\.1:(\d+)$') { throw 'Unexpected lab port binding.' }
    $port=$Matches[1]
    $env:FCAJ_LAB_JDBC_URL="jdbc:mysql://127.0.0.1:$port/"
    $env:FCAJ_LAB_BACKUP_PATH=(Resolve-Path -LiteralPath $DumpPath).Path
    $env:FCAJ_LEGACY_JDBC_URL=$null
    $env:FCAJ_LEGACY_CONTAINER=$null
    if($Legacy80Image) {
        & $docker run --detach --name $legacyName --label 'fcaj.purpose=legacy-upgrade-lab' --env MYSQL_ROOT_PASSWORD --env 'MYSQL_ROOT_HOST=%' --publish '127.0.0.1::3306' $Legacy80Image --lower-case-table-names=1 --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci | Out-Null
        if($LASTEXITCODE -ne 0) { throw 'Cannot create isolated legacy server.' }
        $legacyStarted=$true
        $legacyBinding=& $docker port $legacyName 3306/tcp
        if($legacyBinding -notmatch '^127\.0\.0\.1:(\d+)$') { throw 'Unexpected legacy lab port binding.' }
        $env:FCAJ_LEGACY_JDBC_URL="jdbc:mysql://127.0.0.1:$($Matches[1])/"
        $env:FCAJ_LEGACY_CONTAINER=$legacyName
    }
    & (Join-Path $JdkHome 'bin/java.exe') ("-Dlogback.configurationFile="+(Join-Path $PSScriptRoot 'lab-logback.xml')) -cp ($classes+[IO.Path]::PathSeparator+$classpath+[IO.Path]::PathSeparator+(Join-Path $project 'src/main/resources')) DumpMigrationLab $work (Join-Path $project 'src/main/resources/db/migration/V1__create_core_schema.sql') $labId
    if($LASTEXITCODE -ne 0) { throw 'Lab checks failed; inspect sanitized evidence in target.' }
    Write-Output "Lab evidence: $work"
} finally {
    if($legacyStarted) { & $docker stop --time 15 $legacyName | Out-Null; Write-Output "Stopped legacy lab retained: $legacyName" }
    if($containerStarted) {
        & $docker stop --time 15 $labId | Out-Null
        if($LASTEXITCODE -ne 0) { throw "Could not stop lab container $labId; stop it explicitly after review." }
        Write-Output "Stopped lab container retained: $labId"
    }
    $env:MYSQL_ROOT_PASSWORD=$previousPassword
    $env:FCAJ_LAB_JDBC_URL=$previousUrl
    $env:FCAJ_LAB_BACKUP_PATH=$previousBackup
    $env:FCAJ_LEGACY_JDBC_URL=$previousLegacyUrl
    $env:FCAJ_LEGACY_CONTAINER=$previousLegacyContainer
}
