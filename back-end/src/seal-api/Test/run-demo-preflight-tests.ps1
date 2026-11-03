<#
Pure preflight checks. This test parses run-demo.ps1 and loads ONLY its three
configuration helper functions. It never runs the runner, mysql, Maven or DROP.
Run from any folder: pwsh -File Test/run-demo-preflight-tests.ps1
#>
$ErrorActionPreference = 'Stop'
$runnerPath = Join-Path (Split-Path -Parent $PSScriptRoot) 'run-demo.ps1'
$tokens = $null
$parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile($runnerPath, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count -gt 0) { throw 'run-demo.ps1 contains PowerShell syntax errors.' }
$helpers = @('Read-DemoEnvSettings', 'Resolve-DemoSetting', 'Assert-DemoPreflight')
foreach ($name in $helpers) {
    $definition = $ast.Find({ param($node)
        $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq $name
    }, $false)
    if (-not $definition) { throw "Missing preflight helper: $name" }
    . ([scriptblock]::Create($definition.Extent.Text))
}
$passed = 0
function Assert-Check([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
    $script:passed++
}
function Assert-Rejected([scriptblock]$Action, [string]$ExpectedMessage) {
    $failure = $null
    try { & $Action } catch { $failure = $_ }
    if (-not $failure -or -not $failure.Exception.Message.Contains($ExpectedMessage)) {
        throw "Expected safe preflight rejection containing: $ExpectedMessage"
    }
    $script:passed++
}

# The real invocation must precede the DROP statement, not just exist in the file.
$preflightCall = $ast.Find({ param($node)
    $node -is [System.Management.Automation.Language.CommandAst] -and $node.GetCommandName() -eq 'Assert-DemoPreflight'
}, $true)
$dropText = $ast.Find({ param($node)
    $node -is [System.Management.Automation.Language.ExpandableStringExpressionAst] -and $node.Value -like 'DROP DATABASE IF EXISTS*'
}, $true)
Assert-Check ($null -ne $preflightCall -and $null -ne $dropText -and
    $preflightCall.Extent.StartOffset -lt $dropText.Extent.StartOffset) 'Preflight must run before DROP.'

Assert-Rejected { Assert-DemoPreflight 'S3' $false @{} @{} } 'BOOTSTRAP_ADMIN_ENABLED=true'
Assert-Rejected { Assert-DemoPreflight 'S3' $false @{ BOOTSTRAP_ADMIN_ENABLED = 'true' } @{} } 'BOOTSTRAP_ADMIN_EMAIL'
$valid = @{ BOOTSTRAP_ADMIN_ENABLED = 'true'; BOOTSTRAP_ADMIN_EMAIL = 'operator@example.com';
    BOOTSTRAP_ADMIN_PASSWORD = 'test-only-external-password' }
Assert-DemoPreflight 'S3' $false $valid @{}
Assert-Check $true 'Valid fresh S3 settings should pass.'
Assert-DemoPreflight 'S3' $true @{ BOOTSTRAP_ADMIN_EMAIL = 'operator@example.com' } @{}
Assert-Check $true 'NoDrop may reuse an existing configured global admin.'
Assert-DemoPreflight 'NONE' $false @{} @{}
Assert-Check $true 'Bootstrap stays optional for ordinary startup.'
$invalidPassword = $valid.Clone()
$invalidPassword.BOOTSTRAP_ADMIN_PASSWORD = 'short'
Assert-Rejected { Assert-DemoPreflight 'S1' $false $invalidPassword @{} } 'BOOTSTRAP_ADMIN_PASSWORD'
$invalidPassword.BOOTSTRAP_ADMIN_PASSWORD = ([string][char]0x00e1) * 37
Assert-Rejected { Assert-DemoPreflight 'S3' $false $invalidPassword @{} } '72 UTF-8 bytes'
$invalidPassword.BOOTSTRAP_ADMIN_PASSWORD = '${OTHER_SECRET}'
Assert-Rejected { Assert-DemoPreflight 'S3' $false $invalidPassword @{} } 'nested property placeholders'
$wrongProfile = $valid.Clone()
$wrongProfile.SPRING_PROFILES_ACTIVE = 'aws'
Assert-Rejected { Assert-DemoPreflight 'S3' $false $wrongProfile @{} } 'local profile'
$wrongDatabase = $valid.Clone()
$wrongDatabase.SPRING_DATASOURCE_URL = 'jdbc:mysql://rds.example.com:3306/fcaj_poc'
Assert-Rejected { Assert-DemoPreflight 'S3' $false $wrongDatabase @{} } 'local localhost:3306/seal_hackathon'

$fileValues = @{ BOOTSTRAP_ADMIN_EMAIL = 'file@example.com'; DB_PASSWORD = 'file-password' }
$processValues = @{ BOOTSTRAP_ADMIN_EMAIL = 'process@example.com'; DB_PASSWORD = 'process-password' }
Assert-Check ((Resolve-DemoSetting $processValues $fileValues 'app.bootstrap.admin.email' 'BOOTSTRAP_ADMIN_EMAIL') -eq
    'process@example.com') 'Process aliases must override file aliases.'
$fileValues['app.bootstrap.admin.email'] = 'canonical-file@example.com'
Assert-Check ((Resolve-DemoSetting $processValues $fileValues 'app.bootstrap.admin.email' 'BOOTSTRAP_ADMIN_EMAIL') -eq
    'canonical-file@example.com') 'Direct imported properties must override placeholder aliases.'
$processValues.APP_BOOTSTRAP_ADMIN_EMAIL = 'canonical-process@example.com'
Assert-Check ((Resolve-DemoSetting $processValues $fileValues 'app.bootstrap.admin.email' 'BOOTSTRAP_ADMIN_EMAIL') -eq
    'canonical-process@example.com') 'Canonical process environment must have highest precedence.'
$processValues.SPRING_DATASOURCE_PASSWORD = 'canonical-process-password'
Assert-Check ((Resolve-DemoSetting $processValues $fileValues 'spring.datasource.password' 'DB_PASSWORD') -eq
    'canonical-process-password') 'Datasource process environment must override DB_PASSWORD aliases.'

$fixture = Join-Path ([System.IO.Path]::GetTempPath()) ('fcaj-demo-preflight-' + [guid]::NewGuid() + '.properties')
try {
    [System.IO.File]::WriteAllText($fixture, (@('# comment', 'DB_PASSWORD="literal-quotes"  ',
        'BOOTSTRAP_ADMIN_ENABLED=true') -join [Environment]::NewLine))
    $parsed = Read-DemoEnvSettings $fixture
    Assert-Check ($parsed.DB_PASSWORD -eq '"literal-quotes"  ') 'Java properties quotes and trailing spaces must stay literal.'
    Assert-Check ($parsed.BOOTSTRAP_ADMIN_ENABLED -eq 'true') 'Plain properties should parse.'
    [System.IO.File]::WriteAllText($fixture, 'BOOTSTRAP_ADMIN_PASSWORD=escaped\value')
    Assert-Rejected { Read-DemoEnvSettings $fixture } 'plain key=value'
    [System.IO.File]::WriteAllText($fixture, 'spring.config.import=another.properties')
    Assert-Rejected { Read-DemoEnvSettings $fixture } 'profile/config overrides'
} finally {
    Remove-Item -LiteralPath $fixture -ErrorAction SilentlyContinue
}
Write-Output "Passed $passed preflight checks. Runner, MySQL and Maven were not executed."
