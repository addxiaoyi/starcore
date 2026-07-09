#!/bin/bash

echo "========================================"
echo "   STARCORE 测试服务器一键启动"
echo "========================================"
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查Java
echo -e "${YELLOW}[1/6]${NC} 检查 Java 环境..."
if ! command -v java &> /dev/null; then
    echo -e "${RED}[错误]${NC} 未找到 Java！"
    echo "请先安装 Java 21: https://adoptium.net/"
    exit 1
fi
echo -e "${GREEN}[✓]${NC} Java 环境正常"
echo ""

# 创建目录
echo -e "${YELLOW}[2/6]${NC} 创建测试目录..."
mkdir -p starcore-test/plugins
cd starcore-test
echo -e "${GREEN}[✓]${NC} 目录创建完成"
echo ""

# 下载 Paper
echo -e "${YELLOW}[3/6]${NC} 下载 Paper 1.21.1..."
if [ ! -f "paper.jar" ]; then
    echo "正在下载... 这可能需要几分钟"
    wget -q --show-progress https://api.papermc.io/v2/projects/paper/versions/1.21.1/builds/latest/downloads/paper-1.21.1-latest.jar -O paper.jar
    if [ $? -ne 0 ]; then
        echo -e "${RED}[错误]${NC} 下载失败！请检查网络连接"
        exit 1
    fi
else
    echo -e "${GREEN}[✓]${NC} Paper 已存在，跳过下载"
fi
echo -e "${GREEN}[✓]${NC} Paper 下载完成"
echo ""

# 下载 Spark
echo -e "${YELLOW}[4/6]${NC} 下载性能分析插件 Spark..."
cd plugins
if [ ! -f "spark-bukkit.jar" ]; then
    wget -q --show-progress https://ci.lucko.me/job/spark/lastSuccessfulBuild/artifact/spark-bukkit/build/libs/spark-bukkit.jar
fi
cd ..
echo -e "${GREEN}[✓]${NC} Spark 下载完成"
echo ""

# 配置服务器
echo -e "${YELLOW}[5/6]${NC} 配置服务器..."
echo "eula=true" > eula.txt
cat > server.properties << EOF
server-port=25967
motd=STARCORE 测试服务器
max-players=20
online-mode=false
EOF
echo -e "${GREEN}[✓]${NC} 配置完成"
echo ""

# 检查 STARCORE 插件
echo -e "${YELLOW}[6/6]${NC} 检查 STARCORE 插件..."
if [ ! -f "plugins/starcore.jar" ]; then
    echo ""
    echo "========================================"
    echo -e "${YELLOW}[重要]${NC} 请将 starcore.jar 放入以下目录："
    echo "$(pwd)/plugins/"
    echo ""
    echo "然后重新运行此脚本"
    echo "========================================"
    exit 0
fi
echo -e "${GREEN}[✓]${NC} STARCORE 插件已就位"
echo ""

# 启动服务器
echo "========================================"
echo "   准备启动服务器！"
echo "========================================"
echo ""
echo "服务器配置："
echo "- 端口: 25967"
echo "- 内存: 4GB"
echo "- 插件: STARCORE + Spark"
echo ""
echo "按 Enter 启动服务器..."
read

echo ""
echo "正在启动..."
echo ""

java -Xms4G -Xmx4G \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions \
  -XX:G1HeapRegionSize=8M \
  -jar paper.jar --nogui

echo ""
echo "服务器已关闭"
