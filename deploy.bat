@echo off
setlocal

set VM=opc@68.233.112.30
set SSH_KEY=C:\Users\shiva\Downloads\ppkv2_openssh
set PROJECT=%~dp0

REM ============================================
REM  Deploy ID Platform (Java)
REM ============================================
echo === [1/3] Building ID Platform JAR ===
cd /d "%PROJECT%id-platform"
call mvn clean package -DskipTests -q
if %errorlevel% neq 0 (
    echo Build failed!
    exit /b 1
)

echo === [1/3] Uploading ID Platform ===
scp -i "%SSH_KEY%" target\id-platform-*.jar %VM%:/tmp/id-platform.jar
if %errorlevel% neq 0 (
    echo Upload failed!
    exit /b 1
)

echo === [1/3] Replacing JAR and restarting ===
ssh -i "%SSH_KEY%" %VM% "sudo docker cp /tmp/id-platform.jar id-platform:/app/app.jar && sudo docker restart id-platform"

REM ============================================
REM  Deploy Legacy Main Site (Python)
REM ============================================
echo === [2/3] Uploading Legacy Main Site ===
scp -i "%SSH_KEY%" "%PROJECT%legacy-main-site\app.py" %VM%:/tmp/main-site-app.py
if %errorlevel% neq 0 (
    echo Upload failed!
    exit /b 1
)

echo === [2/3] Replacing app.py and restarting ===
ssh -i "%SSH_KEY%" %VM% "sudo docker cp /tmp/main-site-app.py legacy-main-site:/app/app.py && sudo docker restart legacy-main-site"

REM ============================================
REM  Deploy Legacy MA Site (Python)
REM ============================================
echo === [3/3] Uploading Legacy MA Site ===
scp -i "%SSH_KEY%" "%PROJECT%legacy-ma-site\app.py" %VM%:/tmp/ma-site-app.py
if %errorlevel% neq 0 (
    echo Upload failed!
    exit /b 1
)

echo === [3/3] Replacing app.py and restarting ===
ssh -i "%SSH_KEY%" %VM% "sudo docker cp /tmp/ma-site-app.py legacy-ma-site:/app/app.py && sudo docker restart legacy-ma-site"

REM ============================================
REM  Health Check
REM ============================================
echo.
echo === Waiting for services to start (30s) ===
timeout /t 30
echo === Health Check ===
ssh -i "%SSH_KEY%" %VM% "echo ID Platform:   $(curl -s -o /dev/null -w '%{http_code}' http://localhost:3000/) && echo Legacy Main:   $(curl -s -o /dev/null -w '%{http_code}' http://localhost:3001/) && echo Legacy MA:     $(curl -s -o /dev/null -w '%{http_code}' http://localhost:3002/)"
echo.
echo === Deploy complete ===
