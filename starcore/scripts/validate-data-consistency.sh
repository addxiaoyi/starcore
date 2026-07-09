#!/bin/bash
# 数据一致性验证脚本
# 用于验证 Properties 和 SQL 数据库中的数据完全一致

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 默认配置
DATA_DIR="./plugins/starcore"
DB_URL=""
DB_USER=""
DB_PASSWORD=""
MODULES=""
OUTPUT=""
JAR_FILE="target/starcore-0.1.0-SNAPSHOT.jar"

# 打印帮助信息
print_help() {
    cat << EOF
数据一致性验证脚本

用法:
  ./validate-data-consistency.sh [选项]

选项:
  --data-dir <path>       StarCore 数据目录 (默认: ./plugins/starcore)
  --db-url <url>          数据库 JDBC URL (必需)
  --db-user <username>    数据库用户名 (必需)
  --db-password <pwd>     数据库密码 (必需)
  --modules <list>        要验证的模块，逗号分隔 (默认: 全部)
  --output <file>         输出报告文件路径 (默认: 自动生成)
  --jar <path>            JAR 文件路径 (默认: target/starcore-0.1.0-SNAPSHOT.jar)
  --help, -h              显示此帮助信息

示例:
  # 验证所有模块
  ./validate-data-consistency.sh \\
    --db-url jdbc:mysql://localhost:3306/starcore \\
    --db-user root \\
    --db-password password

  # 验证指定模块
  ./validate-data-consistency.sh \\
    --db-url jdbc:mysql://localhost:3306/starcore \\
    --db-user root \\
    --db-password password \\
    --modules nation,diplomacy,policy

  # SQLite 示例
  ./validate-data-consistency.sh \\
    --db-url jdbc:sqlite:./plugins/starcore/starcore.db \\
    --db-user "" \\
    --db-password ""

支持的模块:
  nation, diplomacy, policy, resource, technology, treasury,
  war, officer, event, resolution

EOF
}

# 解析命令行参数
while [[ $# -gt 0 ]]; do
    case $1 in
        --help|-h)
            print_help
            exit 0
            ;;
        --data-dir)
            DATA_DIR="$2"
            shift 2
            ;;
        --db-url)
            DB_URL="$2"
            shift 2
            ;;
        --db-user)
            DB_USER="$2"
            shift 2
            ;;
        --db-password)
            DB_PASSWORD="$2"
            shift 2
            ;;
        --modules)
            MODULES="$2"
            shift 2
            ;;
        --output)
            OUTPUT="$2"
            shift 2
            ;;
        --jar)
            JAR_FILE="$2"
            shift 2
            ;;
        *)
            echo -e "${RED}错误: 未知参数 $1${NC}"
            print_help
            exit 1
            ;;
    esac
done

# 验证必需参数
if [ -z "$DB_URL" ]; then
    echo -e "${RED}错误: 缺少 --db-url 参数${NC}"
    print_help
    exit 1
fi

# 检查 JAR 文件是否存在
if [ ! -f "$JAR_FILE" ]; then
    echo -e "${RED}错误: JAR 文件不存在: $JAR_FILE${NC}"
    echo -e "${YELLOW}提示: 请先编译项目: mvn clean package${NC}"
    exit 1
fi

# 检查数据目录是否存在
if [ ! -d "$DATA_DIR" ]; then
    echo -e "${RED}错误: 数据目录不存在: $DATA_DIR${NC}"
    exit 1
fi

# 构建命令参数
CMD_ARGS=(
    "--data-dir" "$DATA_DIR"
    "--db-url" "$DB_URL"
    "--db-user" "$DB_USER"
    "--db-password" "$DB_PASSWORD"
)

if [ -n "$MODULES" ]; then
    CMD_ARGS+=("--modules" "$MODULES")
fi

if [ -n "$OUTPUT" ]; then
    CMD_ARGS+=("--output" "$OUTPUT")
fi

# 打印配置信息
echo -e "${BLUE}========================================"
echo -e "  数据一致性验证工具"
echo -e "========================================${NC}"
echo -e "数据目录:   ${GREEN}$DATA_DIR${NC}"
echo -e "数据库:     ${GREEN}$DB_URL${NC}"
echo -e "JAR 文件:   ${GREEN}$JAR_FILE${NC}"
if [ -n "$MODULES" ]; then
    echo -e "验证模块:   ${GREEN}$MODULES${NC}"
else
    echo -e "验证模块:   ${GREEN}全部${NC}"
fi
if [ -n "$OUTPUT" ]; then
    echo -e "输出报告:   ${GREEN}$OUTPUT${NC}"
else
    echo -e "输出报告:   ${GREEN}自动生成${NC}"
fi
echo ""

# 执行验证
echo -e "${BLUE}开始验证...${NC}"
echo ""

if java -cp "$JAR_FILE" \
    dev.starcore.starcore.core.database.DataConsistencyValidatorCommand \
    "${CMD_ARGS[@]}"; then
    echo ""
    echo -e "${GREEN}========================================"
    echo -e "  验证成功完成"
    echo -e "========================================${NC}"

    # 查找生成的报告文件
    if [ -z "$OUTPUT" ]; then
        REPORT_FILE=$(ls -t data-consistency-report-*.md 2>/dev/null | head -1)
        if [ -n "$REPORT_FILE" ]; then
            echo -e "报告文件: ${GREEN}$REPORT_FILE${NC}"
        fi
    else
        echo -e "报告文件: ${GREEN}$OUTPUT${NC}"
    fi

    exit 0
else
    echo ""
    echo -e "${RED}========================================"
    echo -e "  验证失败"
    echo -e "========================================${NC}"
    exit 1
fi
