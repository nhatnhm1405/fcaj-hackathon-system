@echo off
setlocal

set "ROOT=%~dp0..\.."
for %%I in ("%ROOT%") do set "ROOT=%%~fI"

set "BACKEND=%ROOT%\back-end\src\seal-api"
set "FRONTEND=%ROOT%\front-end\src\seal-web"
set "REPORT_DIR=%ROOT%\E2E testing\scenario-1\playwright-report\02_in_progress_phase"
set "REVIEW_SCRIPT=%~dp0open-review-edge.ps1"
set "API_PORT=8080"
set "WEB_PORT=5173"
set "API_URL=http://localhost:%API_PORT%"
set "WEB_URL=http://localhost:%WEB_PORT%"
set "REVIEW_URL=%WEB_URL%/coordinator/scoring"
if not defined E2E_SLOW_MO_MS set "E2E_SLOW_MO_MS=1500"
if not defined E2E_STEP_PAUSE_MS set "E2E_STEP_PAUSE_MS=3000"
if not defined E2E_TEST_TIMEOUT_MS set "E2E_TEST_TIMEOUT_MS=900000"
if not defined S1_CONTEST_SECONDS set "S1_CONTEST_SECONDS=600"
if not defined S1_JUDGING_SECONDS set "S1_JUDGING_SECONDS=3600"
set "SERVERS_STARTED=0"
set "TEST_STARTED=0"
set "TEST_EXIT=0"

echo [S1-02] Workspace: %ROOT%
echo [S1-02] Using common demo ports %API_PORT% and %WEB_PORT%.

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$client=New-Object System.Net.Sockets.TcpClient; try { $async=$client.BeginConnect('127.0.0.1',%API_PORT%,$null,$null); $ok=$async.AsyncWaitHandle.WaitOne(500); if ($ok) { $client.EndConnect($async); exit 0 }; exit 1 } catch { exit 1 } finally { $client.Close() }"
set "API_RUNNING=%ERRORLEVEL%"
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$client=New-Object System.Net.Sockets.TcpClient; try { $async=$client.BeginConnect('127.0.0.1',%WEB_PORT%,$null,$null); $ok=$async.AsyncWaitHandle.WaitOne(500); if ($ok) { $client.EndConnect($async); exit 0 }; exit 1 } catch { exit 1 } finally { $client.Close() }"
set "WEB_RUNNING=%ERRORLEVEL%"

if not "%API_RUNNING%"=="0" (
  echo [S1-02] Backend is not running. Starting backend without DB reset.
  start "SEAL API - S1 (%API_PORT%)" powershell.exe -NoExit -ExecutionPolicy Bypass -Command "$env:SERVER_PORT='%API_PORT%'; $env:APP_FRONTEND_URL='%WEB_URL%'; Set-Location -LiteralPath '%BACKEND%'; .\run-demo.ps1 S1 -NoDrop"
  set "SERVERS_STARTED=1"
)

if not "%WEB_RUNNING%"=="0" (
  echo [S1-02] Frontend is not running. Starting frontend dev server.
  start "SEAL WEB - S1 (%WEB_PORT%)" powershell.exe -NoExit -ExecutionPolicy Bypass -Command "$env:VITE_API_URL='%API_URL%'; Set-Location -LiteralPath '%FRONTEND%'; npm.cmd run dev -- --host 127.0.0.1 --port %WEB_PORT% --strictPort"
  set "SERVERS_STARTED=1"
)

echo [S1-02] Waiting for S1 SETUP/IN_PROGRESS backend/frontend...
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; function Wait-S1Backend($Url, $Minutes) { $deadline=(Get-Date).AddMinutes($Minutes); do { try { $body=Invoke-RestMethod -UseBasicParsing -Uri $Url -TimeoutSec 5; $event=$body.data | Where-Object { $_.name -eq 'SEAL Summer 2026' } | Select-Object -First 1; if ($event -and @('SETUP','IN_PROGRESS') -contains $event.status) { Write-Host ('[S1-02] backend ready with S1 ' + $event.status + ' state.'); return } } catch { }; Start-Sleep -Seconds 2 } while ((Get-Date) -lt $deadline); throw 'Timeout waiting for backend S1 SETUP/IN_PROGRESS state. Run 00_open_phase.bat then 01_setup_phase.bat first.' }; function Wait-Url($Name, $Url, $Minutes) { $deadline=(Get-Date).AddMinutes($Minutes); do { try { $r=Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 5; if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 500) { Write-Host ('[S1-02] ' + $Name + ' ready: ' + $Url); return } } catch { }; Start-Sleep -Seconds 2 } while ((Get-Date) -lt $deadline); throw ('Timeout waiting for ' + $Name + ' at ' + $Url) }; Wait-S1Backend '%API_URL%/api/events' 5; Wait-Url 'frontend' '%WEB_URL%/login' 3"
if errorlevel 1 (
  echo [S1-02] Server readiness check failed.
  set "TEST_EXIT=1"
  goto :finish
)

if not exist "%FRONTEND%\node_modules\@playwright\test" (
  echo [S1-02] Missing Playwright dependency. Run npm install in %FRONTEND% first.
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

echo [S1-02] Running %RUN_MODE% Playwright in-progress phase demo.
set "TEST_STARTED=1"
call npx.cmd playwright test e2e/scenario-1/02_in_progress_phase.spec.ts %PW_HEADED% --project=chromium
set "TEST_EXIT=%ERRORLEVEL%"

:finish
if "%TEST_STARTED%"=="1" (
  echo [S1-02] HTML report: %REPORT_DIR%\index.html
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%REVIEW_SCRIPT%" -Url "%REVIEW_URL%"
  if errorlevel 1 echo [S1-02] WARNING: Could not open the Edge review window.
) else (
  echo [S1-02] Playwright did not run, so no new HTML report was created.
)
if "%SERVERS_STARTED%"=="1" (
  echo [S1-02] Backend/frontend windows are left running after this demo step.
) else (
  echo [S1-02] Reused existing backend/frontend windows.
)
if "%TEST_EXIT%"=="0" (
  echo [S1-02] Demo completed successfully.
) else (
  echo [S1-02] Demo stopped with exit code %TEST_EXIT%.
)
echo.
if /i not "%E2E_NO_PAUSE%"=="1" pause
exit /b %TEST_EXIT%
