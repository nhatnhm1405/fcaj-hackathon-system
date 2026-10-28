[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateSet('SUCCEEDED','FAILED')][string]$Outcome,
    [string]$JdkHome='C:/Program Files/Java/jdk-21.0.10'
)
$ErrorActionPreference='Stop'
$project=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$previousJava=$env:JAVA_HOME
Push-Location $project
try {
    $env:JAVA_HOME=$JdkHome
    & ./mvnw.cmd -q -DskipTests compile dependency:build-classpath '-Dmdep.includeScope=runtime' '-Dmdep.outputFile=target/operator-classpath.txt'
    if($LASTEXITCODE -ne 0) { throw 'Cannot build mock worker runner.' }
    $classpath=(Join-Path $project 'target/classes')+[IO.Path]::PathSeparator+(Get-Content -Raw target/operator-classpath.txt).Trim()
    $mode=if($Outcome -eq 'SUCCEEDED') { '--one-success' } else { '--one-failure' }
    & (Join-Path $JdkHome 'bin/java.exe') -cp $classpath com.fcaj.hackathon.aijob.AwsMockWorkerMain $mode
    if($LASTEXITCODE -ne 0) { throw 'Mock worker failed; input message was not acknowledged.' }
} finally { $env:JAVA_HOME=$previousJava;Pop-Location }
