@echo off
if "%TURNSTILE_SECRET%"=="" set TURNSTILE_SECRET=dev-placeholder
cd /d "%~dp0server"
mvnw.cmd clean spring-boot:run
pause
