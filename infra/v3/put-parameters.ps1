# Creates the v3 Parameter Store values once. Secrets are generated here, sent straight to
# SSM as SecureString, and never printed. Existing parameters are never overwritten.
# Usage: .\infra\v3\put-parameters.ps1 -Domain example.com -Release "<repo>@sha256:<digest> <commit>" [-DryRun]
param(
  [Parameter(Mandatory)][ValidatePattern('^[a-z0-9.-]+$')][string]$Domain,
  [Parameter(Mandatory)][ValidatePattern('^[0-9]{12}\.dkr\.ecr\.ap-southeast-1\.amazonaws\.com/[a-z0-9/_-]+@sha256:[0-9a-f]{64} [0-9a-f]{40}$')][string]$Release,
  [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
$region = 'ap-southeast-1'

function Get-AwsText([string[]]$Arguments) {
  $out = & aws @Arguments --region $region --output text
  if ($LASTEXITCODE -ne 0 -or -not $out) { throw "aws $($Arguments[0]) $($Arguments[1]) failed" }
  return ("$out").Trim()
}
function New-RandomBytes([int]$Count) {
  $bytes = New-Object byte[] $Count
  $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
  try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
  return , $bytes
}
# 48 hex characters = 192 random bits; letters and digits only.
function New-Password { return -join ((New-RandomBytes 24) | ForEach-Object { $_.ToString('x2') }) }

$account = Get-AwsText @('sts', 'get-caller-identity', '--query', 'Account')
$db = Get-AwsText @('cloudformation', 'describe-stacks', '--stack-name', 'fcaj-v3-base', '--query', "Stacks[0].Outputs[?OutputKey=='DatabaseAddress'].OutputValue")
if (-not $Release.StartsWith("$account.")) { throw 'Release image belongs to another account' }

$names = '/fcaj/v3/backend/runtime-env', '/fcaj/v3/backend/migration-env', '/fcaj/v3/backend/release', '/fcaj/v3/edge/origin-secret'
$existing = & aws ssm get-parameters --region $region --names $names --query 'Parameters[].Name' --output text
if ($LASTEXITCODE -ne 0) { throw 'Cannot check existing parameters' }
if ($existing) { throw "Already exists, nothing was changed: $existing" }

$jdbc = "jdbc:mysql://${db}:3306/seal_hackathon?sslMode=VERIFY_IDENTITY&serverTimezone=UTC&connectTimeout=5000&socketTimeout=30000"
$sqs = "https://sqs.$region.amazonaws.com/$account"
$runtime = @(
  'SPRING_PROFILES_ACTIVE=aws'
  "SPRING_DATASOURCE_URL=$jdbc"
  'SPRING_DATASOURCE_USERNAME=fcaj_v3_runtime'
  "SPRING_DATASOURCE_PASSWORD=$(New-Password)"
  "JWT_SECRET=$([Convert]::ToBase64String((New-RandomBytes 48)))"
  'JWT_COOKIE_SECURE=true'
  "APP_FRONTEND_URL=https://$Domain"
  'CSRF_COOKIE_DOMAIN='
  'SEED_SCENARIO=NONE'
  'BOOTSTRAP_ADMIN_ENABLED=false'
  "AWS_REGION=$region"
  "APP_STORAGE_BUCKET=fcaj-poc-data-$account-$region"
  'APP_STORAGE_PREFIX=poc'
  'APP_AI_JOBS_ENABLED=false'
  'APP_AI_JOBS_DELIVERY_ENABLED=false'
  "APP_AI_JOBS_JOBS_QUEUE_URL=$sqs/fcaj-poc-ai-jobs"
  "APP_AI_JOBS_RESULTS_QUEUE_URL=$sqs/fcaj-poc-ai-results"
) -join "`n"
$migration = @(
  "BE1_MIGRATION_JDBC_URL=$jdbc"
  'BE1_MIGRATION_USERNAME=fcaj_v3_migration'
  "BE1_MIGRATION_PASSWORD=$(New-Password)"
) -join "`n"
$values = @($runtime, $migration, $Release, (New-Password))
$types = 'SecureString', 'SecureString', 'String', 'SecureString'

for ($i = 0; $i -lt $names.Count; $i++) {
  if ($DryRun) {
    "--- $($names[$i]) ($($types[$i]), $($values[$i].Length) characters)"
    if ($types[$i] -eq 'String' -or $values[$i].Contains("`n")) { $values[$i] -replace '(?m)^(\w*(?:PASSWORD|SECRET))=.*$', '$1=***' }
    continue
  }
  # A private temp file keeps the secret out of the command line and the shell history.
  $file = [IO.Path]::GetTempFileName()
  try {
    $json = @{ Name = $names[$i]; Type = $types[$i]; Value = $values[$i] } | ConvertTo-Json -Compress
    [IO.File]::WriteAllText($file, $json, (New-Object Text.UTF8Encoding $false))
    & aws ssm put-parameter --region $region --cli-input-json "file://$file" --output text | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Failed at $($names[$i]); the parameters listed above already exist" }
    "OK $($names[$i])"
  } finally { Remove-Item -LiteralPath $file -Force }
}
