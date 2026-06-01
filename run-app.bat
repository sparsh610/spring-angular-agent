@echo off
setlocal

if exist ".env" (
  for /f "usebackq tokens=1,* delims==" %%A in (".env") do (
    echo %%A | findstr /b "#" >nul
    if errorlevel 1 set "%%A=%%B"
  )
) else (
  echo .env file not found. Create .env before starting the app.
  exit /b 1
)

mvn spring-boot:run
