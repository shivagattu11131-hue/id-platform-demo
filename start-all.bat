@echo off
echo ============================================
echo  RAKSUL ID PLATFORM DEMO - STARTUP SCRIPT
echo ============================================
echo.

echo [1/5] Cleaning up old databases...
del /q "legacy-main-site\main_site.db" 2>nul
del /q "legacy-ma-site\ma_site.db" 2>nul
del /q "id-platform\id_platform_db.mv.db" 2>nul
del /q "id-platform\id_platform_db.trace.db" 2>nul
echo Done.

echo.
echo [2/5] Installing Python dependencies...
pip install -q flask flask-cors requests
echo Done.

echo.
echo [3/5] Starting ID Platform - Spring Boot (Port 3000)...
start "ID Platform" cmd /c "cd id-platform && mvn spring-boot:run"
echo Waiting for Spring Boot to start...
timeout /t 15 /nobreak >nul

echo.
echo [4/5] Starting Legacy Main Site (Port 3001)...
start "Main Site" cmd /c "cd legacy-main-site && python app.py"
timeout /t 3 /nobreak >nul

echo [5/5] Starting Legacy MA Site (Port 3002)...
start "MA Site" cmd /c "cd legacy-ma-site && python app.py"
timeout /t 3 /nobreak >nul

echo.
echo ============================================
echo  ALL SERVICES STARTING:
echo    ID Platform: http://localhost:3000 (Spring Boot)
echo    Main Site:   http://localhost:3001
echo    MA Site:     http://localhost:3002
echo.
echo  To run the demo:
echo    cd scripts ^&^& python demo.py
echo ============================================
pause
