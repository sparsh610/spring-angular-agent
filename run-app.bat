@echo off
setlocal

set "APP_URL=http://localhost:8080"

if exist ".env" (
  for /f "usebackq tokens=1,* delims==" %%A in (".env") do (
    echo %%A | findstr /b "#" >nul
    if errorlevel 1 set "%%A=%%B"
  )
) else (
  echo .env file not found. Create .env before starting the app.
  exit /b 1
)

start "spring-angular-agent" cmd /k mvn spring-boot:run
echo Starting Spring Boot...
timeout /t 8 /nobreak >nul
start "" "%APP_URL%"
echo Opened %APP_URL%
