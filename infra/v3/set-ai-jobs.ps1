# Turns AI jobs on or off in the backend's stored configuration. Other lines stay as they are and
# nothing secret is printed. Hosts pick the change up at the next seal-release.
# Usage: .\infra\v3\set-ai-jobs.ps1 -State on|off
param([Parameter(Mandatory)][ValidateSet('on', 'off')][string]$State)
$ErrorActionPreference = 'Stop'
$region = 'ap-southeast-1'
$name = '/fcaj/v3/backend/runtime-env'
$value = if ($State -eq 'on') { 'true' } else { 'false' }

$json = & aws ssm get-parameter --region $region --with-decryption --name $name --output json
if ($LASTEXITCODE -ne 0) { throw 'Cannot read the current configuration' }
$lines = (($json -join "`n") | ConvertFrom-Json).Parameter.Value -split "`r?`n"
$keys = 'APP_AI_JOBS_ENABLED', 'APP_AI_JOBS_DELIVERY_ENABLED'
foreach ($key in $keys) {
  if (@($lines -match "^$key=(true|false)$").Count -ne 1) { throw "Expected exactly one $key line; nothing was changed" }
  $lines = $lines -replace "^$key=(true|false)$", "$key=$value"
}

$file = [IO.Path]::GetTempFileName()
try {
  $body = @{ Name = $name; Type = 'SecureString'; Overwrite = $true; Value = ($lines -join "`n") } | ConvertTo-Json -Compress
  [IO.File]::WriteAllText($file, $body, (New-Object Text.UTF8Encoding $false))
  & aws ssm put-parameter --region $region --cli-input-json "file://$file" --output text | Out-Null
  if ($LASTEXITCODE -ne 0) { throw 'Cannot save the configuration' }
} finally { Remove-Item -LiteralPath $file -Force }
"OK AI jobs $State"
