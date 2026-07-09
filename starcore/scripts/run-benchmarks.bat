@echo off
REM Performance Benchmark Execution Script (Windows)
REM 性能基准测试执行脚本 (Windows 版本)

setlocal enabledelayedexpansion

echo ========================================
echo 存储层性能基准测试
echo ========================================
echo.

REM 获取脚本所在目录
set "SCRIPT_DIR=%~dp0"
set "PROJECT_ROOT=%SCRIPT_DIR%.."

cd /d "%PROJECT_ROOT%"

echo 项目路径: %PROJECT_ROOT%
echo.

REM 检查 Maven
where mvn >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] Maven 未安装或不在 PATH 中
    exit /b 1
)

echo Maven 版本:
mvn --version | findstr /C:"Apache Maven"
echo.

REM 清理旧的测试结果
echo 清理旧的测试结果...
if exist performance-benchmark-report.md del /f /q performance-benchmark-report.md
if exist target\surefire-reports\dev.starcore.starcore.benchmark.* del /f /q target\surefire-reports\dev.starcore.starcore.benchmark.*
echo.

REM 运行基准测试
echo ========================================
echo 开始执行基准测试...
echo ========================================
echo.

REM 设置 JVM 参数
set "MAVEN_OPTS=-Xms512m -Xmx2048m -XX:+UseG1GC"

REM 运行测试
mvn test -Dtest=StoragePerformanceBenchmark -q
if %errorlevel% neq 0 (
    echo.
    echo [失败] 基准测试执行失败
    exit /b 1
)

echo.
echo [成功] 基准测试执行成功
echo.

echo ========================================
echo 测试结果
echo ========================================
echo.

REM 检查报告是否生成
if exist performance-benchmark-report.md (
    echo [成功] 性能报告已生成: performance-benchmark-report.md
    echo.
    echo 报告内容预览:
    echo ----------------------------------------
    type performance-benchmark-report.md
    echo ----------------------------------------
    echo.

    REM 检查历史数据
    if exist performance-benchmark-history.csv (
        echo 历史数据对比:
        powershell -Command "Get-Content performance-benchmark-history.csv | Select-Object -Last 5"
    ) else (
        echo [警告] 未找到历史数据，这是首次运行基准测试
        echo timestamp,scenario,duration_ms,jvm_version,os_name > performance-benchmark-history.csv
    )

    echo.
    echo [成功] 基准测试完成
    echo.

    REM 提取关键指标
    echo ========================================
    echo 关键性能指标
    echo ========================================

    findstr /C:"SQL 加载性能与 Properties 相当" performance-benchmark-report.md >nul
    if %errorlevel% equ 0 (
        echo [成功] SQL 性能达标 ^(与 Properties 相当^)
        exit /b 0
    )

    findstr /C:"SQL 加载略慢于 Properties" performance-benchmark-report.md >nul
    if %errorlevel% equ 0 (
        echo [警告] SQL 性能可接受 ^(略慢于 Properties^)
        exit /b 0
    )

    findstr /C:"SQL 加载明显慢于 Properties" performance-benchmark-report.md >nul
    if %errorlevel% equ 0 (
        echo [错误] SQL 性能需要优化
        exit /b 1
    )

    echo [警告] 无法自动判断性能结果，请手动查看报告
    exit /b 0

) else (
    echo [失败] 性能报告未生成
    exit /b 1
)
