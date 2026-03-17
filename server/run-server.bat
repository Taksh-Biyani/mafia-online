@echo off
REM Mafia Game Server - Quick Launch Script
REM This script will build and run the Mafia Game server

echo.
echo ========================================
echo   Mafia Game Server - Launcher
echo ========================================
echo.

REM Check if we're in the right directory
if not exist "pom.xml" (
    echo ERROR: pom.xml not found!
    echo Please run this script from C:\Users\gfuel\IdeaProjects\mafia-online\server
    pause
    exit /b 1
)

echo Step 1: Cleaning previous builds...
call mvnw.cmd clean
if errorlevel 1 goto :error

echo.
echo Step 2: Building the project...
call mvnw.cmd package -DskipTests
if errorlevel 1 goto :error

echo.
echo Step 3: Running the application...
echo.

REM Detect local IP address for LAN display
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /i "IPv4" ^| findstr /v "127.0.0.1"') do (
    set LAN_IP=%%a
    goto :got_ip
)
:got_ip
set LAN_IP=%LAN_IP: =%

echo Starting Mafia Game Server...
echo.
echo  Local access : http://localhost:8080
echo  LAN access   : http://%LAN_IP%:8080
echo.
echo Share the LAN address with others on your WiFi to play together.
echo To stop the server, close the "Mafia Game Server" command window that opened.
echo.

REM Start the server in a new command window
start "Mafia Game Server" cmd /k "java -jar target/game-0.0.1-SNAPSHOT.jar"

echo Server started successfully!
echo.

goto :end

:error
echo.
echo ERROR: Build failed! Please check the error messages above.
pause
exit /b 1

:end
echo.
echo Server stopped.
pause
