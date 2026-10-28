[CmdletBinding()]
param(
    [ValidateSet('inspect','migrate','baseline-v1','verify-deploy','initialize-empty')][string]$Action='inspect',
    [Parameter(Mandatory=$true)][string]$Catalog,
    [string]$JdkHome='C:/Program Files/Java/jdk-21.0.10'
)
$ErrorActionPreference='Stop'
$project=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$previousJava=$env:JAVA_HOME
Push-Location $project
try {
    $env:JAVA_HOME=$JdkHome
    & ./mvnw.cmd -q -DskipTests compile dependency:build-classpath '-Dmdep.includeScope=runtime' '-Dmdep.outputFile=target/operator-classpath.txt'
    if($LASTEXITCODE -ne 0) { throw 'Cannot build migration runner.' }
    $classpath=(Join-Path $project 'target/classes')+[IO.Path]::PathSeparator+(Get-Content -Raw target/operator-classpath.txt).Trim()
    & (Join-Path $JdkHome 'bin/java.exe') -cp $classpath com.fcaj.hackathon.db.DatabaseMigrationMain $Action $Catalog
    if($LASTEXITCODE -ne 0) { throw 'Migration runner rejected or failed the operation.' }
} finally { $env:JAVA_HOME=$previousJava;Pop-Location }
