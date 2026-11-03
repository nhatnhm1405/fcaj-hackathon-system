@echo off
setlocal

set "ROOT=%~dp0..\.."
for %%I in ("%ROOT%") do set "ROOT=%%~fI"

set "BACKEND=%ROOT%\back-end\src\seal-api"
set "FRONTEND=%ROOT%\front-end\src\seal-web"
set "REPORT_DIR=%ROOT%\E2E testing\scenario-1\playwright-report\00_open_phase"
set "REVIEW_SCRIPT=%~dp0open-review-edge.ps1"
set "API_PORT=8080"
set "WEB_PORT=5173"
set "API_URL=http://localhost:%API_PORT%"
set "WEB_URL=http://localhost:%WEB_PORT%"
set "REVIEW_URL=%WEB_URL%/coordinator/teams"
if not defined E2E_SLOW_MO_MS set "E2E_SLOW_MO_MS=1500"
if not defined E2E_STEP_PAUSE_MS set "E2E_STEP_PAUSE_MS=3000"
if not defined E2E_TEST_TIMEOUT_MS set "E2E_TEST_TIMEOUT_MS=900000"
set "SERVERS_STARTED=0"
set "TEST_STARTED=0"
set "TEST_EXIT=0"

echo [S1-00] Workspace: %ROOT%
echo [S1-00] Checking that common demo ports %API_PORT% and %WEB_PORT% are free.
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; function Test-Port([int]$Port) { $client=New-Object System.Net.Sockets.TcpClient; try { $async=$client.BeginConnect('127.0.0.1',$Port,$null,$null); $ok=$async.AsyncWaitHandle.WaitOne(500); if ($ok) { $client.EndConnect($async) }; return $ok } catch { return $false } finally { $client.Close() } }; if (Test-Port %API_PORT%) { Write-Host '[S1-00] Port %API_PORT% is already in use. Close the existing S1 demo backend window before running 00_open_phase.bat.'; exit 20 }; if (Test-Port %WEB_PORT%) { Write-Host '[S1-00] Port %WEB_PORT% is already in use. Close the existing S1 demo frontend window before running 00_open_phase.bat.'; exit 21 }"
if errorlevel 1 (
  echo [S1-00] Pre-flight check failed.
  set "TEST_EXIT=1"
  goto :finish
)

echo [S1-00] Starting backend demo seed S1 with database reset.
start "SEAL API - S1 (%API_PORT%)" powershell.exe -NoExit -ExecutionPolicy Bypass -Command "$env:SERVER_PORT='%API_PORT%'; $env:APP_FRONTEND_URL='%WEB_URL%'; Set-Location -LiteralPath '%BACKEND%'; .\run-demo.ps1 S1 -Force"
set "SERVERS_STARTED=1"

echo [S1-00] Starting frontend dev server.
start "SEAL WEB - S1 (%WEB_PORT%)" powershell.exe -NoExit -ExecutionPolicy Bypass -Command "$env:VITE_API_URL='%API_URL%'; Set-Location -LiteralPath '%FRONTEND%'; npm.cmd run dev -- --host 127.0.0.1 --port %WEB_PORT% --strictPort"

echo [S1-00] Waiting for backend and frontend to be ready...
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; function Wait-S1Backend($Url, $Minutes) { $deadline=(Get-Date).AddMinutes($Minutes); do { try { $body=Invoke-RestMethod -UseBasicParsing -Uri $Url -TimeoutSec 5; $event=$body.data | Where-Object { $_.name -eq 'SEAL Summer 2026' } | Select-Object -First 1; if ($event -and $event.status -eq 'OPEN') { Write-Host '[S1-00] backend ready with S1 OPEN seed.'; return } } catch { }; Start-Sleep -Seconds 2 } while ((Get-Date) -lt $deadline); throw 'Timeout waiting for backend S1 OPEN seed' }; function Wait-Url($Name, $Url, $Minutes) { $deadline=(Get-Date).AddMinutes($Minutes); do { try { $r=Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 5; if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 500) { Write-Host ('[S1-00] ' + $Name + ' ready: ' + $Url); return } } catch { }; Start-Sleep -Seconds 2 } while ((Get-Date) -lt $deadline); throw ('Timeout waiting for ' + $Name + ' at ' + $Url) }; Wait-S1Backend '%API_URL%/api/events' 5; Wait-Url 'frontend' '%WEB_URL%/login' 3"
if errorlevel 1 (
  echo [S1-00] Server readiness check failed.
  set "TEST_EXIT=1"
  goto :finish
)

if not exist "%FRONTEND%\node_modules\@playwright\test" (
  echo [S1-00] Missing Playwright dependency. Run npm install in %FRONTEND% first.
  set "TEST_EXIT=1"
  goto :finish
)

cd /d "%FRONTEND%"
set "E2E_API_URL=%API_URL%"
set "E2E_BASE_URL=%WEB_URL%"
set "PLAYWRIGHT_HTML_REPORT=%REPORT_DIR%"
set "PLAYWRIGHT_HTML_OPEN=never"
if /i "%E2E_HEADLESS%"=="1" (
  set "PW_HEADED="
  set "RUN_MODE=headless"
) else (
  set "PW_HEADED=--headed"
  set "RUN_MODE=headed"
)

echo [S1-00] Running %RUN_MODE% Playwright demo with %E2E_SLOW_MO_MS%ms slow motion and %E2E_STEP_PAUSE_MS%ms step pauses.
set "TEST_STARTED=1"
call npx.cmd playwright test e2e/scenario-1/00_open_account_team_flow.spec.ts %PW_HEADED% --project=chromium
set "TEST_EXIT=%ERRORLEVEL%"

:finish
if "%TEST_STARTED%"=="1" (
  echo [S1-00] HTML report: %REPORT_DIR%\index.html
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%REVIEW_SCRIPT%" -Url "%REVIEW_URL%"
  if errorlevel 1 echo [S1-00] WARNING: Could not open the Edge review window.
) else (
  echo [S1-00] Playwright did not run, so no new HTML report was created.
)
if "%SERVERS_STARTED%"=="1" (
  echo [S1-00] Backend and frontend windows are left running for the next demo step.
) else (
  echo [S1-00] No backend/frontend windows were started.
)
if "%TEST_EXIT%"=="0" (
  echo [S1-00] Demo completed successfully.
) else (
  echo [S1-00] Demo stopped with exit code %TEST_EXIT%.
)
echo.
if /i not "%E2E_NO_PAUSE%"=="1" pause
exit /b %TEST_EXIT%
