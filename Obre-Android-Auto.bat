@echo off
setlocal
echo Connecta i desbloqueja el Pixel. A Android Auto, prem "Start head unit server".
echo.

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\android-auto-dhu.ps1" -Action Start
set "DHU_EXIT=%ERRORLEVEL%"
if not "%DHU_EXIT%"=="0" (
    echo.
    echo El DHU no s'ha pogut iniciar o ha perdut la connexio.
    pause
)
exit /b %DHU_EXIT%
