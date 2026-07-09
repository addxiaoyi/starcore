@echo off
chcp 65001 >nul
echo ========================================
echo    STARCORE 测试服务器一键启动
echo ========================================
echo.

:: 检查Java
echo [1/6] 检查 Java 环境...
java -version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 Java！
    echo 请先安装 Java 21: https://adoptium.net/
    pause
    exit /b 1
)
echo [✓] Java 环境正常
echo.

:: 创建目录
echo [2/6] 创建测试目录...
mkdir starcore-test 2>nul
cd starcore-test
mkdir plugins 2>nul
echo [✓] 目录创建完成
echo.

:: 下载 Paper
echo [3/6] 下载 Paper 1.21.1...
if not exist paper.jar (
    echo 正在下载... 这可能需要几分钟
    powershell -Command "& {Invoke-WebRequest -Uri 'https://api.papermc.io/v2/projects/paper/versions/1.21.1/builds/latest/downloads/paper-1.21.1-latest.jar' -OutFile 'paper.jar'}"
    if errorlevel 1 (
        echo [错误] 下载失败！请检查网络连接
        pause
        exit /b 1
    )
) else (
    echo [✓] Paper 已存在，跳过下载
)
echo [✓] Paper 下载完成
echo.

:: 下载 Spark
echo [4/6] 下载性能分析插件 Spark...
cd plugins
if not exist spark-bukkit.jar (
    powershell -Command "& {Invoke-WebRequest -Uri 'https://ci.lucko.me/job/spark/lastSuccessfulBuild/artifact/spark-bukkit/build/libs/spark-bukkit.jar' -OutFile 'spark-bukkit.jar'}"
)
cd ..
echo [✓] Spark 下载完成
echo.

:: 配置服务器
echo [5/6] 配置服务器...
echo eula=true > eula.txt
echo server-port=25967 > server.properties
echo motd=STARCORE 测试服务器 >> server.properties
echo max-players=20 >> server.properties
echo [✓] 配置完成
echo.

:: 检查 STARCORE 插件
echo [6/6] 检查 STARCORE 插件...
if not exist plugins\starcore.jar (
    echo.
    echo ========================================
    echo [重要] 请将 starcore.jar 放入以下目录：
    echo %cd%\plugins\
    echo.
    echo 然后重新运行此脚本
    echo ========================================
    pause
    exit /b 0
)
echo [✓] STARCORE 插件已就位
echo.

:: 启动服务器
echo ========================================
echo    准备启动服务器！
echo ========================================
echo.
echo 服务器配置：
echo - 端口: 25967
echo - 内存: 4GB
echo - 插件: STARCORE + Spark
echo.
echo 按任意键启动服务器...
pause >nul

echo.
echo 正在启动...
echo.

java -Xms4G -Xmx4G ^
  -XX:+UseG1GC ^
  -XX:MaxGCPauseMillis=200 ^
  -XX:+UnlockExperimentalVMOptions ^
  -XX:G1HeapRegionSize=8M ^
  -jar paper.jar --nogui

echo.
echo 服务器已关闭
pause
