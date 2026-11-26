# Turns Google or GitHub sign-in on or off in the backend's stored configuration. The client ID and
# secret are typed here, sent straight to SSM as SecureString, and never printed. Other lines stay as
# they are. Hosts pick the change up at the next seal-release.
# Usage: .\infra\v3\set-oauth.ps1 -Provider github|google -State on|off
param(
  [Parameter(Mandatory)][ValidateSet('github', 'google')][string]$Provider,
  [Parameter(Mandatory)][ValidateSet('on', 'off')][string]$State
)
$ErrorActionPreference = 'Stop'
$region = 'ap-southeast-1'
$name = '/fcaj/v3/backend/runtime-env'
$prefix = $Provider.ToUpperInvariant()

$json = & aws ssm get-parameter --region $region --with-decryption --name $name --output json
if ($LASTEXITCODE -ne 0) { throw 'Cannot read the current configuration' }
$lines = @((($json -join "`n") | ConvertFrom-Json).Parameter.Value -split "`r?`n")
$current = @($lines -match '^SPRING_PROFILES_ACTIVE=')
if ($current.Count -ne 1) { throw 'Expected exactly one SPRING_PROFILES_ACTIVE line; nothing was changed' }
$profiles = @($current[0].Substring('SPRING_PROFILES_ACTIVE='.Length) -split ',' | Where-Object { $_ -and $_ -ne "oauth-$Provider" })
$kept = @($lines | Where-Object { $_ -notmatch "^(SPRING_PROFILES_ACTIVE|${prefix}_CLIENT_(ID|SECRET))=" })
$added = @()

if ($State -eq 'on') {
  $idPattern = if ($Provider -eq 'google') { '^[0-9]+-[a-z0-9]+\.apps\.googleusercontent\.com$' } else { '^[A-Za-z0-9]{16,40}$' }
  $id = (Read-Host 'Client ID').Trim()
  if ($id -notmatch $idPattern) { throw "This does not look like a $Provider client ID; nothing was changed" }
  $secure = Read-Host 'Client secret (hidden)' -AsSecureString
  $secret = (New-Object Net.NetworkCredential('', $secure)).Password.Trim()
  if ($secret -notmatch '^[A-Za-z0-9_-]{20,100}$') { throw "This does not look like a $Provider client secret; nothing was changed" }
  $profiles += "oauth-$Provider"
  $added = @("${prefix}_CLIENT_ID=$id", "${prefix}_CLIENT_SECRET=$secret")
}

$value = (@("SPRING_PROFILES_ACTIVE=$($profiles -join ',')") + $kept + $added) -join "`n"
$file = [IO.Path]::GetTempFileName()
try {
  $body = @{ Name = $name; Type = 'SecureString'; Overwrite = $true; Value = $value } | ConvertTo-Json -Compress
  [IO.File]::WriteAllText($file, $body, (New-Object Text.UTF8Encoding $false))
  & aws ssm put-parameter --region $region --cli-input-json "file://$file" --output text | Out-Null
  if ($LASTEXITCODE -ne 0) { throw 'Cannot save the configuration' }
} finally { Remove-Item -LiteralPath $file -Force }
"OK $Provider sign-in $State"
