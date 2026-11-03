<# New private MySQL 8.4 instance only. No existing DB, queue or bucket is contacted. #>
[CmdletBinding()]
param([string]$JdkHome='C:/Program Files/Java/jdk-21.0.10')
$ErrorActionPreference='Stop'
$project=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$docker='C:/Program Files/Docker/Docker/resources/bin/docker.exe'
$image='mysql@sha256:76f0358884cb92d9a086d24e5671c38048d3fef40f2588703d73eb55392d6b69'
$name='fcaj-ai-lab-'+[guid]::NewGuid().ToString('N').Substring(0,12)
$previousPassword=$env:MYSQL_ROOT_PASSWORD
$previousUrl=$env:FCAJ_AI_TEST_JDBC_URL
$previousJava=$env:JAVA_HOME
$started=$false
Push-Location $project
try {
    $env:JAVA_HOME=$JdkHome
    New-Item -ItemType Directory -Force -Path (Join-Path $project 'target') | Out-Null
    # The fresh-DB regression also invokes the exact packaged CLI, not only test classes.
    & ./mvnw.cmd -o -B -DskipTests package *> target/mysql-candidate-package.log
    if($LASTEXITCODE -ne 0) { throw 'Cannot package candidate for isolated MySQL tests.' }
    $env:MYSQL_ROOT_PASSWORD=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    & $docker run --detach --name $name --label 'fcaj.purpose=ai-integration-lab' --env MYSQL_ROOT_PASSWORD --env 'MYSQL_ROOT_HOST=%' --env 'MYSQL_DATABASE=fcaj_ai_integration_lab' --publish '127.0.0.1::3306' --health-cmd 'mysqladmin ping -h 127.0.0.1 --silent' --health-interval 2s --health-timeout 2s --health-retries 60 $image --lower-case-table-names=1 --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci | Out-Null
    if($LASTEXITCODE -ne 0) { throw 'Cannot create isolated MySQL lab.' }
    $started=$true
    $ready=$false
    for($attempt=0;$attempt -lt 60;$attempt++) {
        $health=& $docker inspect --format '{{.State.Health.Status}}' $name
        if($health -eq 'healthy') { $ready=$true;break }
        Start-Sleep -Seconds 2
    }
    if(-not $ready) { throw 'Isolated MySQL lab did not become ready.' }
    $binding=& $docker port $name 3306/tcp
    if($binding -notmatch '^127\.0\.0\.1:(\d+)$') { throw 'Unexpected lab port binding.' }
    $env:FCAJ_AI_TEST_JDBC_URL="jdbc:mysql://127.0.0.1:$($Matches[1])/fcaj_ai_integration_lab?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=5000&socketTimeout=30000"
    $log=Join-Path $project "target/$name-test.log"
    & ./mvnw.cmd -o -B '-Dspring.profiles.active=test' '-Dlogging.level.root=WARN' '-Dsurefire.runOrder=alphabetical' '-Dtest=AiJobIntegrationTest,FileReferenceConcurrencyTest,FreshDatabaseDeploymentTest' test *> $log
    $result=$LASTEXITCODE
    Get-Content -LiteralPath $log -Tail 35
    if($result -ne 0) { throw 'MySQL integration tests failed; review the lab log.' }
    Write-Output "PASS: private MySQL V1/V2/V3, job integration and file-reference concurrency. Log: $log"
} finally {
    if($started) {
        & $docker stop --time 15 $name | Out-Null
        if($LASTEXITCODE -ne 0) { Write-Warning "Stop retained lab container explicitly: $name" }
        else { Write-Output "Stopped and retained: $name" }
    }
    $env:MYSQL_ROOT_PASSWORD=$previousPassword
    $env:FCAJ_AI_TEST_JDBC_URL=$previousUrl
    $env:JAVA_HOME=$previousJava
    Pop-Location
}
