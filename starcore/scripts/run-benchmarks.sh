#!/bin/bash
# Performance Benchmark Execution Script
# 性能基准测试执行脚本

set -e

echo "========================================"
echo "存储层性能基准测试"
echo "========================================"
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${PROJECT_ROOT}"

echo "项目路径: ${PROJECT_ROOT}"
echo ""

# 检查 Maven
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}❌ Maven 未安装或不在 PATH 中${NC}"
    exit 1
fi

echo "Maven 版本:"
mvn --version | head -n 1
echo ""

# 清理旧的测试结果
echo "清理旧的测试结果..."
rm -f performance-benchmark-report.md
rm -rf target/surefire-reports/dev.starcore.starcore.benchmark.*
echo ""

# 运行基准测试
echo "========================================"
echo "开始执行基准测试..."
echo "========================================"
echo ""

# 设置 JVM 参数以获得更稳定的测试结果
export MAVEN_OPTS="-Xms512m -Xmx2048m -XX:+UseG1GC"

# 运行测试
if mvn test -Dtest=StoragePerformanceBenchmark -q; then
    echo ""
    echo -e "${GREEN}✅ 基准测试执行成功${NC}"
else
    echo ""
    echo -e "${RED}❌ 基准测试执行失败${NC}"
    exit 1
fi

echo ""
echo "========================================"
echo "测试结果"
echo "========================================"
echo ""

# 检查报告是否生成
if [ -f "performance-benchmark-report.md" ]; then
    echo -e "${GREEN}✅ 性能报告已生成: performance-benchmark-report.md${NC}"
    echo ""
    echo "报告内容预览:"
    echo "----------------------------------------"
    cat performance-benchmark-report.md
    echo "----------------------------------------"
    echo ""

    # 检查历史数据
    if [ -f "performance-benchmark-history.csv" ]; then
        echo "历史数据对比:"
        tail -n 5 performance-benchmark-history.csv
    else
        echo -e "${YELLOW}⚠️  未找到历史数据，这是首次运行基准测试${NC}"

        # 创建历史数据文件
        echo "timestamp,scenario,duration_ms,jvm_version,os_name" > performance-benchmark-history.csv
    fi

    echo ""
    echo -e "${GREEN}✅ 基准测试完成${NC}"

    # 提取关键指标
    echo ""
    echo "========================================"
    echo "关键性能指标"
    echo "========================================"

    if grep -q "SQL 加载性能与 Properties 相当" performance-benchmark-report.md; then
        echo -e "${GREEN}✅ SQL 性能达标 (与 Properties 相当)${NC}"
        exit 0
    elif grep -q "SQL 加载略慢于 Properties" performance-benchmark-report.md; then
        echo -e "${YELLOW}⚠️  SQL 性能可接受 (略慢于 Properties)${NC}"
        exit 0
    elif grep -q "SQL 加载明显慢于 Properties" performance-benchmark-report.md; then
        echo -e "${RED}❌ SQL 性能需要优化${NC}"
        exit 1
    else
        echo -e "${YELLOW}⚠️  无法自动判断性能结果，请手动查看报告${NC}"
        exit 0
    fi

else
    echo -e "${RED}❌ 性能报告未生成${NC}"
    exit 1
fi
