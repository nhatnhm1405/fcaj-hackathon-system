<#
Offline entity-only compilation and Hibernate MySQL 8.4 DDL export.
Does not compile the full application or represent a passing backend test suite.
Run after: ./mvnw.cmd dependency:build-classpath -Dmdep.outputFile=target/schema-tool-classpath.txt -DincludeScope=test
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$JdkHome,
    [Parameter(Mandatory = $true)][string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$classpathFile = Join-Path $projectRoot 'target/schema-tool-classpath.txt'
if (-not (Test-Path -LiteralPath $classpathFile)) {
    throw 'Resolve the Maven test classpath first; see script documentation.'
}
$classpath = (Get-Content -Raw -LiteralPath $classpathFile).Trim()
$javac = Join-Path $JdkHome 'bin/javac.exe'
$java = Join-Path $JdkHome 'bin/java.exe'
$entitySources = Join-Path $projectRoot 'src/main/java/com/fcaj/hackathon/entity'
$compileDir = Join-Path $projectRoot ('target/schema-tool-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $compileDir | Out-Null
$sources = @(Get-ChildItem -LiteralPath $entitySources -Filter '*.java' | Sort-Object Name |
    Select-Object -ExpandProperty FullName)
& $javac --release 21 -encoding UTF-8 -proc:full -cp $classpath -d $compileDir @sources (Join-Path $PSScriptRoot 'SchemaInventoryTool.java')
if ($LASTEXITCODE -ne 0) { throw 'Entity-only compilation failed.' }
$revision = git -C $projectRoot rev-parse HEAD
if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve source revision.' }
& $java -cp ($compileDir + [IO.Path]::PathSeparator + $classpath) SchemaInventoryTool $entitySources $OutputDirectory $revision
if ($LASTEXITCODE -ne 0) { throw 'Offline export failed.' }
