@echo off
REM 数据一致性验证脚本 - Windows 版本
REM 用于验证 Properties 和 SQL 数据库中的数据完全一致

setlocal enabledelayedexpansion

REM 默认配置
set "DATA_DIR=.\plugins\starcore"
set "DB_URL="
set "DB_USER="
set "DB_PASSWORD="
set "MODULES="
set "OUTPUT="
set "JAR_FILE=target\starcore-0.1.0-SNAPSHOT.jar"

REM 解析命令行参数
:parse_args
if "%~1"=="" goto check_args
if /i "%~1"=="--help" goto print_help
if /i "%~1"=="-h" goto print_help
if /i "%~1"=="--data-dir" (
    set "DATA_DIR=%~2"
    shift
    shift
    goto parse_args
)
if /i "%~1"=="--db-url" (
    set "DB_URL=%~2"
    shift
    shift
    goto parse_args
)
if /i "%~1"=="--db-user" (
    set "DB_USER=%~2"
    shift
    shift
    goto parse_args
)
if /i "%~1"=="--db-password" (
    set "DB_PASSWORD=%~2"
    shift
    shift
    goto parse_args
)
if /i "%~1"=="--modules" (
    set "MODULES=%~2"
    shift
    shift
    goto parse_args
)
if /i "%~1"=="--output" (
    set "OUTPUT=%~2"
    shift
    shift
    goto parse_args
)
if /i "%~1"=="--jar" (
    set "JAR_FILE=%~2"
    shift
    shift
    goto parse_args
)
echo [错误] 未知参数: %~1
goto print_help

:check_args
REM 验证必需参数
if "%DB_URL%"=="" (
    echo [错误] 缺少 --db-url 参数
    goto print_help
)

REM 检查 JAR 文件是否存在
if not exist "%JAR_FILE%" (
    echo [错误] JAR 文件不存在: %JAR_FILE%
    echo [提示] 请先编译项目: mvn clean package
    exit /b 1
)

REM 检查数据目录是否存在
if not exist "%DATA_DIR%" (
    echo [错误] 数据目录不存在: %DATA_DIR%
    exit /b 1
)

REM 构建命令参数
set "CMD_ARGS=--data-dir "%DATA_DIR%" --db-url "%DB_URL%" --db-user "%DB_USER%" --db-password "%DB_PASSWORD%""

if not "%MODULES%"=="" (
    set "CMD_ARGS=!CMD_ARGS! --modules "%MODULES%""
)

if not "%OUTPUT%"=="" (
    set "CMD_ARGS=!CMD_ARGS! --output "%OUTPUT%""
)

REM 打印配置信息
echo ========================================
echo   数据一致性验证工具
echo ========================================
echo 数据目录:   %DATA_DIR%
echo 数据库:     %DB_URL%
echo JAR 文件:   %JAR_FILE%
if not "%MODULES%"=="" (
    echo 验证模块:   %MODULES%
) else (
    echo 验证模块:   全部
)
if not "%OUTPUT%"=="" (
    echo 输出报告:   %OUTPUT%
) else (
    echo 输出报告:   自动生成
)
echo.

REM 执行验证
echo 开始验证...
echo.

java -cp "%JAR_FILE%" dev.starcore.starcore.core.database.DataConsistencyValidatorCommand %CMD_ARGS%

if %errorlevel% equ 0 (
    echo.
    echo ========================================
    echo   验证成功完成
    echo ========================================
    if "%OUTPUT%"=="" (
        for /f "delims=" %%i in ('dir /b /o-d data-consistency-report-*.md 2^>nul ^| findstr /n "^" ^| findstr "^1:"') do (
            set "REPORT_FILE=%%i"
            set "REPORT_FILE=!REPORT_FILE:*:=!"
            echo 报告文件: !REPORT_FILE!
        )
    ) else (
        echo 报告文件: %OUTPUT%
    )
    exit /b 0
) else (
    echo.
    echo ========================================
    echo   验证失败
    echo ========================================
    exit /b 1
)

:print_help
echo 数据一致性验证脚本
echo.
echo 用法:
echo   validate-data-consistency.bat [选项]
echo.
echo 选项:
echo   --data-dir ^<path^>       StarCore 数据目录 (默认: .\plugins\starcore)
echo   --db-url ^<url^>          数据库 JDBC URL (必需)
echo   --db-user ^<username^>    数据库用户名 (必需)
echo   --db-password ^<pwd^>     数据库密码 (必需)
echo   --modules ^<list^>        要验证的模块，逗号分隔 (默认: 全部)
echo   --output ^<file^>         输出报告文件路径 (默认: 自动生成)
echo   --jar ^<path^>            JAR 文件路径 (默认: target\starcore-0.1.0-SNAPSHOT.jar)
echo   --help, -h              显示此帮助信息
echo.
echo 示例:
echo   REM 验证所有模块
echo   validate-data-consistency.bat ^
echo     --db-url jdbc:mysql://localhost:3306/starcore ^
echo     --db-user root ^
echo     --db-password password
echo.
echo   REM 验证指定模块
echo   validate-data-consistency.bat ^
echo     --db-url jdbc:mysql://localhost:3306/starcore ^
echo     --db-user root ^
echo     --db-password password ^
echo     --modules nation,diplomacy,policy
echo.
echo 支持的模块:
echo   nation, diplomacy, policy, resource, technology, treasury,
echo   war, officer, event, resolution
echo.
exit /b 0
