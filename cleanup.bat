@echo off
echo ============================================
echo  RAKSUL ID PLATFORM - CLEANUP SCRIPT
echo ============================================
echo.

echo [1/3] Force stopping all Python and Java processes...
taskkill /F /IM python.exe >nul 2>&1
taskkill /F /IM java.exe >nul 2>&1
echo Done.

echo.
echo [2/3] Cleaning up old databases...
del /q "legacy-main-site\main_site.db" 2>nul
del /q "legacy-ma-site\ma_site.db" 2>nul
del /q "id-platform\id_platform_db.mv.db" 2>nul
del /q "id-platform\id_platform_db.trace.db" 2>nul
echo Done.

echo.
echo [3/3] Verifying ports are free...
timeout /t 2 /nobreak >nul

set PORT_FREE=1
netstat -ano | findstr ":3000.*LISTENING" >nul 2>&1 && echo   [WARN] Port 3000 still in use && set PORT_FREE=0
netstat -ano | findstr ":3001.*LISTENING" >nul 2>&1 && echo   [WARN] Port 3001 still in use && set PORT_FREE=0
netstat -ano | findstr ":3002.*LISTENING" >nul 2>&1 && echo   [WARN] Port 3002 still in use && set PORT_FREE=0

if %PORT_FREE%==1 (
    echo   [OK] All ports are free!
)

echo.
echo ============================================
echo  CLEANUP COMPLETE
echo.
echo  Now restart all services:
echo    Terminal 1: cd legacy-main-site ^&^& python app.py
echo    Terminal 2: cd legacy-ma-site ^&^& python app.py
echo    Terminal 3: cd id-platform ^&^& mvn spring-boot:run
echo    Terminal 4: python scripts/demo.py
echo ============================================
pause
