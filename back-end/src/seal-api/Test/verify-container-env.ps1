<# Synthetic environment only, network disabled. No backend, DB or AWS process is started. #>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$CandidateImage,
    [string]$JdkHome='C:/Program Files/Java/jdk-21.0.10'
)
$ErrorActionPreference='Stop'
$project=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$lab=Join-Path $project ('target/env-probe-'+[guid]::NewGuid().ToString('N').Substring(0,12))
New-Item -ItemType Directory -Path $lab -Force | Out-Null
$utf8=[Text.UTF8Encoding]::new($false)
$java=@'
public class EnvProbe {
  public static void main(String[] args) {
    if (!"synthetic$literal\"quotes".equals(System.getenv("SPRING_DATASOURCE_PASSWORD"))) {
      System.err.println("FAIL synthetic raw environment mismatch"); System.exit(1);
    }
    System.out.println("PASS synthetic raw environment inside candidate Java runtime; no DB/TLS/AWS probe");
  }
}
'@
[IO.File]::WriteAllText((Join-Path $lab 'EnvProbe.java'),$java,$utf8)
[IO.File]::WriteAllText((Join-Path $lab 'synthetic.env'),'SPRING_DATASOURCE_PASSWORD=synthetic$literal"quotes'+"`n",$utf8)
[IO.File]::WriteAllText((Join-Path $lab 'synthetic.p12'),'SYNTHETIC NOT A TRUSTSTORE',$utf8)
$mount=$lab.Replace('\','/')
$override=@"
services:
  seal-backend:
    network_mode: none
    read_only: true
    volumes:
      - type: bind
        source: '$mount'
        target: /probe
        read_only: true
"@
[IO.File]::WriteAllText((Join-Path $lab 'probe.yml'),$override,$utf8)
& (Join-Path $JdkHome 'bin/javac.exe') -d $lab (Join-Path $lab 'EnvProbe.java')
if($LASTEXITCODE -ne 0) { throw 'Cannot compile synthetic env probe' }
$values=@{
    CANDIDATE_BACKEND_IMAGE=$CandidateImage
    CANDIDATE_REVISION=('c'*40)
    DEPLOY_RUNTIME_ENV_FILE=(Join-Path $lab 'synthetic.env')
    DEPLOY_RUNTIME_JDBC_URL='jdbc:mysql://rds.example.test:3306/fcaj_poc?sslMode=VERIFY_IDENTITY'
    DEPLOY_DB_TRUSTSTORE_FILE=(Join-Path $lab 'synthetic.p12')
    DEPLOY_SPRING_PROFILES_ACTIVE='aws'
    DEPLOY_TARGET_BIND_IP='10.20.30.40'
}
$prior=@{}
try {
    foreach($key in $values.Keys) { $prior[$key]=[Environment]::GetEnvironmentVariable($key); [Environment]::SetEnvironmentVariable($key,$values[$key]) }
    foreach($file in @('compose.aws.example.yml','compose.aws.alb-direct.example.yml')) {
        & 'C:/Program Files/Docker/Docker/resources/bin/docker.exe' compose -p ('seal-env-probe-'+(Split-Path $lab -Leaf)) -f (Join-Path $project "deploy/$file") -f (Join-Path $lab 'probe.yml') run --rm --pull never --no-deps --entrypoint java seal-backend -cp /probe EnvProbe
        if($LASTEXITCODE -ne 0) { throw "Synthetic env probe failed: $file" }
    }
} finally {
    foreach($key in $prior.Keys) { [Environment]::SetEnvironmentVariable($key,$prior[$key]) }
}
