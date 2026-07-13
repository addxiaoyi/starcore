@echo off
REM StarCore Plugin Test Server Launcher for Windows
REM Paper 1.21.11 Server for testing StarCore plugin

set SERVER_DIR=%~dp0
set JAR_FILE=%SERVER_DIR%paper-1.21.11.jar

echo ==========================================
echo   StarCore Test Server Launcher
echo ==========================================

REM Check if Paper JAR exists
if not exist "%JAR_FILE%" (
    echo ERROR: paper-1.21.11.jar not found!
    echo.
    echo Please download Paper 1.21.11:
    echo   1. Visit: https://papermc.io/downloads/paper
    echo   2. Select version: 1.21.11
    echo   3. Download the JAR file
    echo   4. Save it as: paper-1.21.11.jar
    echo   5. Place it in this directory
    echo.
    echo Or download directly with your browser:
    echo   https://papermc.io/downloads/paper
    pause
    exit /b 1
)

REM Copy StarCore plugin to plugins folder
set PLUGIN_JAR=%SERVER_DIR%..\target\starcore-0.1.0-SNAPSHOT.jar
set PLUGINS_DIR=%SERVER_DIR%plugins

if not exist "%PLUGINS_DIR%" mkdir "%PLUGINS_DIR%"

if exist "%PLUGIN_JAR%" (
    copy /Y "%PLUGIN_JAR%" "%PLUGINS_DIR%\"
    echo [OK] StarCore plugin copied to plugins/
) else (
    echo [WARN] StarCore JAR not found at: %PLUGIN_JAR%
    echo        Build with: cd .. && mvn clean package
)

REM Create eula.txt if not exists
if not exist "%SERVER_DIR%eula.txt" (
    echo eula=true > "%SERVER_DIR%eula.txt"
    echo [OK] EULA accepted (eula.txt created)
)

REM Create server.properties if not exists
if not exist "%SERVER_DIR%server.properties" (
    (
        echo server-port=25565
        echo enable-query=true
        echo query.port=25565
        echo level-name=world
        echo enable-rcon=false
        echo level-seed=
        echo server-ip=
        echo allow-flight=false
        echo level-type=FLAT
        echo view-distance=10
        echo spawn-protection=0
        echo online-mode=false
        echo max-players=10
        echo allow-nether=true
        echo motd=StarCore Test Server
    ) > "%SERVER_DIR%server.properties"
    echo [OK] Server properties created
)

REM Start the server
echo.
echo Starting Paper server...
echo ==========================================
java -Xmx2G -Xms1G -jar "%JAR_FILE%" --nogui
pause
