@echo off
echo ============================================
echo  RAKSUL ID PLATFORM DEMO - STARTUP SCRIPT
echo ============================================
echo.

echo [1/4] Cleaning up old databases...
del /q "legacy-main-site\main_site.db" 2>nul
del /q "legacy-ma-site\ma_site.db" 2>nul
del /q "id-platform\id_platform_db.mv.db" 2>nul
del /q "id-platform\id_platform_db.trace.db" 2>nul
echo Done.

echo.
echo [2/4] Installing Python dependencies...
pip install -q flask flask-cors requests
echo Done.

echo.
echo [3/4] Starting Legacy Main Site (Port 3001)...
start "Main Site" cmd /c "cd legacy-main-site && python app.py"
timeout /t 3 /nobreak >nul

echo [4/4] Starting Legacy MA Site (Port 3002)...
start "MA Site" cmd /c "cd legacy-ma-site && python app.py"
timeout /t 3 /nobreak >nul

echo.
echo ============================================
echo  SERVICES STARTING:
echo    Main Site:  http://localhost:3001
echo    MA Site:    http://localhost:3002
echo    ID Platform: http://localhost:3000
echo.
echo  To start ID Platform, run in a new terminal:
echo    cd id-platform ^&^& mvn spring-boot:run
echo.
echo  To run the demo:
echo    cd scripts ^&^& python demo.py
echo ============================================
pause
