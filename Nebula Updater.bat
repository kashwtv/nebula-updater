@echo off
cd /d "%~dp0"
where javaw >nul 2>nul
if %errorlevel%==0 (
  start "" javaw -jar "%~dp0nebula-updater.jar"
) else (
  echo Java 17+ is required. Install it from https://adoptium.net and run this again.
  pause
)
