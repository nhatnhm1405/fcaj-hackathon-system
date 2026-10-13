@echo off
setlocal

set "ROOT=%~dp0..\.."
for %%I in ("%ROOT%") do set "ROOT=%%~fI"

set "BACKEND=%ROOT%\back-end\src\seal-api"
set "FRONTEND=%ROOT%\front-end\src\seal-web"
set "REPORT_DIR=%ROOT%\E2E testing\scenario-3\playwright-report\00_s3_completed_results_exports"
set "DOWNLOAD_DIR=%ROOT%\E2E testing\scenario-3\downloads"
set "REVIEW_SCRIPT=%ROOT%\E2E testing\scenario-1\open-review-edge.ps1"
set "API_PORT=8080"
set "WEB_PORT=5173"
set "API_URL=http://localhost:%API_PORT%"
set "WEB_URL=http://localhost:%WEB_PORT%"
set "REVIEW_URL=%WEB_URL%/leaderboard"
if not defined E2E_SLOW_MO_MS set "E2E_SLOW_MO_MS=1200"
if not defined E2E_STEP_PAUSE_MS set "E2E_STEP_PAUSE_MS=2500"
if not defined E2E_TEST_TIMEOUT_MS set "E2E_TEST_TIMEOUT_MS=600000"
set "SERVERS_STARTED=0"
set "TEST_STARTED=0"
set "TEST_EXIT=0"

echo [S3] Workspace: %ROOT%
echo [S3] WARNING: This launcher resets the demo database and seeds S3.
echo [S3] Checking that demo ports %API_PORT% and %WEB_PORT% are free.
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; function Test-Port([int]$Port) { $client=New-Object System.Net.Sockets.TcpClient; try { $async=$client.BeginConnect('127.0.0.1',$Port,$null,$null); $ok=$async.AsyncWaitHandle.WaitOne(500); if ($ok) { $client.EndConnect($async) }; return $ok } catch { return $false } finally { $client.Close() } }; if (Test-Port %API_PORT%) { Write-Host '[S3] Port %API_PORT% is already in use. Close the existing demo backend window first.'; exit 20 }; if (Test-Port %WEB_PORT%) { Write-Host '[S3] Port %WEB_PORT% is already in use. Close the existing demo frontend window first.'; exit 21 }"
if errorlevel 1 (
  echo [S3] Pre-flight check failed.
  set "TEST_EXIT=1"
  goto :finish
)

echo [S3] Starting backend with the independent S3 COMPLETED snapshot.
start "SEAL API - S3 (%API_PORT%)" powershell.exe -NoExit -ExecutionPolicy Bypass -Command "$env:SERVER_PORT='%API_PORT%'; $env:APP_FRONTEND_URL='%WEB_URL%'; Set-Location -LiteralPath '%BACKEND%'; .\run-demo.ps1 S3 -Force"
set "SERVERS_STARTED=1"

echo [S3] Starting frontend dev server.
start "SEAL WEB - S3 (%WEB_PORT%)" powershell.exe -NoExit -ExecutionPolicy Bypass -Command "$env:VITE_API_URL='%API_URL%'; Set-Location -LiteralPath '%FRONTEND%'; npm.cmd run dev -- --host 127.0.0.1 --port %WEB_PORT% --strictPort"

echo [S3] Waiting for the S3 backend and frontend...
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; function Wait-S3Backend($Url, $Minutes) { $deadline=(Get-Date).AddMinutes($Minutes); do { try { $body=Invoke-RestMethod -UseBasicParsing -Uri $Url -TimeoutSec 5; $event=$body.data | Where-Object { $_.name -eq 'SEAL Summer 2026' } | Select-Object -First 1; if ($event -and $event.status -eq 'COMPLETED') { Write-Host '[S3] backend ready with S3 COMPLETED seed.'; return } } catch { }; Start-Sleep -Seconds 2 } while ((Get-Date) -lt $deadline); throw 'Timeout waiting for backend S3 seed' }; function Wait-Url($Name, $Url, $Minutes) { $deadline=(Get-Date).AddMinutes($Minutes); do { try { $r=Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 5; if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 500) { Write-Host ('[S3] ' + $Name + ' ready: ' + $Url); return } } catch { }; Start-Sleep -Seconds 2 } while ((Get-Date) -lt $deadline); throw ('Timeout waiting for ' + $Name + ' at ' + $Url) }; Wait-S3Backend '%API_URL%/api/events' 5; Wait-Url 'frontend' '%WEB_URL%/login' 3"
if errorlevel 1 (
  echo [S3] Server readiness check failed.
  set "TEST_EXIT=1"
  goto :finish
)

if not exist "%FRONTEND%\node_modules\@playwright\test" (
  echo [S3] Missing Playwright dependency. Run npm install in %FRONTEND% first.
  set "TEST_EXIT=1"
  goto :finish
)

cd /d "%FRONTEND%"
set "E2E_API_URL=%API_URL%"
set "E2E_BASE_URL=%WEB_URL%"
set "E2E_DOWNLOAD_DIR=%DOWNLOAD_DIR%"
set "PLAYWRIGHT_HTML_REPORT=%REPORT_DIR%"
set "PLAYWRIGHT_HTML_OPEN=never"
if /i "%E2E_HEADLESS%"=="1" (
  set "PW_HEADED="
  set "RUN_MODE=headless"
) else (
  set "PW_HEADED=--headed"
  set "RUN_MODE=headed"
)

echo [S3] Running %RUN_MODE% Playwright flow: completed results, history, winner and participant exports.
set "TEST_STARTED=1"
call npx.cmd playwright test e2e/scenario-3/00_s3_completed_results_exports.spec.ts %PW_HEADED% --project=chromium
set "TEST_EXIT=%ERRORLEVEL%"

:finish
if "%TEST_STARTED%"=="1" (
  echo [S3] HTML report: %REPORT_DIR%\index.html
  echo [S3] Downloaded CSV files: %DOWNLOAD_DIR%
  if exist "%REVIEW_SCRIPT%" (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%REVIEW_SCRIPT%" -Url "%REVIEW_URL%"
    if errorlevel 1 echo [S3] WARNING: Could not open the Edge review window.
  )
) else (
  echo [S3] Playwright did not run, so no new report or downloads were created.
)
if "%SERVERS_STARTED%"=="1" (
  echo [S3] Backend and frontend windows remain open for review.
) else (
  echo [S3] No backend/frontend windows were started.
)
if "%TEST_EXIT%"=="0" (
  echo [S3] Scenario completed successfully.
) else (
  echo [S3] Scenario stopped with exit code %TEST_EXIT%.
)
echo.
if /i not "%E2E_NO_PAUSE%"=="1" pause
exit /b %TEST_EXIT%
