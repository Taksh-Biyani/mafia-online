@echo off
if "%TURNSTILE_SECRET%"=="" set TURNSTILE_SECRET=dev-placeholder
cd /d "%~dp0server"
mvnw.cmd spring-boot:run
pause
