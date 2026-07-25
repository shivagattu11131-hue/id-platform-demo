@echo off
echo ============================================
echo  RAKSUL ID PLATFORM - TEST SUITE
echo ============================================
echo.

set PASS=0
set FAIL=0
set DIR=%~dp0

:: -----------------------------------------------
echo [1/3] Legacy Main Site Tests (pytest)
echo -----------------------------------------------
cd /d "%DIR%legacy-main-site"
python -m pytest tests/ -v --tb=short --no-header -q
if %ERRORLEVEL% EQU 0 (
    echo [PASS] Legacy Main Site - 25 tests passed
    set /a PASS+=1
) else (
    echo [FAIL] Legacy Main Site tests failed
    set /a FAIL+=1
)
echo.

:: -----------------------------------------------
echo [2/3] Legacy MA Site Tests (pytest)
echo -----------------------------------------------
cd /d "%DIR%legacy-ma-site"
python -m pytest tests/ -v --tb=short --no-header -q
if %ERRORLEVEL% EQU 0 (
    echo [PASS] Legacy MA Site - 24 tests passed
    set /a PASS+=1
) else (
    echo [FAIL] Legacy MA Site tests failed
    set /a FAIL+=1
)
echo.

:: -----------------------------------------------
echo [3/3] ID Platform Tests (Maven/JUnit)
echo -----------------------------------------------
cd /d "%DIR%id-platform"
call mvn test -q 2>nul
if %ERRORLEVEL% EQU 0 (
    echo [PASS] ID Platform - 34 tests passed
    set /a PASS+=1
) else (
    echo [FAIL] ID Platform tests failed
    set /a FAIL+=1
)
echo.

:: -----------------------------------------------
echo ============================================
echo  RESULTS: %PASS% passed, %FAIL% failed (3 suites)
echo ============================================
cd /d "%DIR%"
if %FAIL% GTR 0 exit /b 1
