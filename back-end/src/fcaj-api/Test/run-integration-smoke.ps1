<# Synthetic data, in-memory H2 and mocked GitHub/S3/SQS. No demo reset or AWS worker invocation. #>
[CmdletBinding()]
param([string]$JdkHome='C:/Program Files/Java/jdk-21.0.10')
$ErrorActionPreference='Stop'
if($env:FCAJ_AI_TEST_JDBC_URL) { throw 'Unset FCAJ_AI_TEST_JDBC_URL for this in-memory smoke run.' }
$project=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$previousJava=$env:JAVA_HOME
Push-Location $project
try {
    $env:JAVA_HOME=$JdkHome
    $socketDirectory=Join-Path $project 'target/socket-tmp'
    New-Item -ItemType Directory -Force -Path $socketDirectory | Out-Null
    & ./mvnw.cmd -o -B '-Dspring.profiles.active=test' '-Dtest=AwsMockWorkerTest,AiJobIntegrationTest' "-DargLine=-Djdk.net.unixdomain.tmpdir=$socketDirectory" test
    if($LASTEXITCODE -ne 0) { throw 'Local component smoke failed; inspect target/surefire-reports.' }
    Write-Output 'PASS: synthetic production-component smoke; AWS transport and application restart remain separate checks.'
} finally {
    $env:JAVA_HOME=$previousJava
    Pop-Location
}
