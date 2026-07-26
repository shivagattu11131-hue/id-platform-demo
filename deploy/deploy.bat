@echo off
REM ============================================================
REM  Raksul ID Platform - Build and Upload to VM (Docker)
REM  Run this from the project root on Windows
REM ============================================================

set VM_USER=opc
set VM_IP=
set SSH_KEY=C:\Users\shiva\Downloads\ppkv2.ppk
set REMOTE_DIR=/opt/raksul-id-platform

echo ============================================
echo  Raksul ID Platform - Docker Deployment
echo ============================================
echo.

if "%VM_IP%"=="" (
    set /p VM_IP="Enter your VM public IP: "
)

echo VM IP: %VM_IP%
echo SSH Key: %SSH_KEY%
echo.

REM --- Step 1: Create deployment archive ---
echo [1/2] Creating deployment archive...
cd /d "%~dp0.."
tar -cf deploy\docker-release.tar ^
    id-platform\src id-platform\pom.xml ^
    legacy-main-site legacy-ma-site ^
    Dockerfile.id-platform Dockerfile.legacy-main-site Dockerfile.legacy-ma-site ^
    docker-compose.yml .dockerignore ^
    deploy\docker-deploy.sh
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to create archive!
    pause
    exit /b 1
)
echo   Archive created: deploy\docker-release.tar
echo.

REM --- Step 2: Upload to VM ---
echo [2/2] Uploading to VM...
echo   Uploading to %VM_USER%@%VM_IP%:%REMOTE_DIR%
pscp -i "%SSH_KEY%" deploy\docker-release.tar %VM_USER%@%VM_IP%:/tmp/raksul-docker-release.tar
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Upload failed! Check your SSH key and IP.
    pause
    exit /b 1
)

echo.
echo ============================================
echo  Upload complete!
echo ============================================
echo.
echo  Next: SSH into your VM and run:
echo    plink -i "%SSH_KEY%" %VM_USER%@%VM_IP% "sudo mkdir -p %REMOTE_DIR% && sudo tar -xf /tmp/raksul-docker-release.tar -C %REMOTE_DIR% && sudo chmod +x %REMOTE_DIR%/deploy/docker-deploy.sh && cd %REMOTE_DIR%/deploy && sudo bash docker-deploy.sh"
echo ============================================
pause
