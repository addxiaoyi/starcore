#!/bin/bash

# StarCore 快速部署脚本
# 用于将插件部署到测试服务器

echo "======================================"
echo "  StarCore 测试服务器部署工具"
echo "======================================"
echo ""

# 配置变量（请根据实际情况修改）
SERVER_PATH="${1:-/path/to/your/minecraft/server}"
PLUGIN_NAME="starcore-0.1.0-SNAPSHOT.jar"
JAR_PATH="target/$PLUGIN_NAME"

# 检查 JAR 文件是否存在
if [ ! -f "$JAR_PATH" ]; then
    echo "❌ 错误：找不到插件文件 $JAR_PATH"
    echo "请先运行 'mvn clean package' 打包插件"
    exit 1
fi

echo "📦 找到插件文件：$JAR_PATH"
echo "📊 文件大小：$(du -h $JAR_PATH | cut -f1)"
echo ""

# 检查服务器路径
if [ ! -d "$SERVER_PATH" ]; then
    echo "⚠️  警告：服务器路径不存在：$SERVER_PATH"
    echo ""
    echo "用法："
    echo "  ./deploy-to-test.sh /path/to/minecraft/server"
    echo ""
    echo "或者编辑脚本修改 SERVER_PATH 变量"
    exit 1
fi

# 创建 plugins 目录（如果不存在）
mkdir -p "$SERVER_PATH/plugins"

# 备份旧版本（如果存在）
if [ -f "$SERVER_PATH/plugins/$PLUGIN_NAME" ]; then
    BACKUP_NAME="$PLUGIN_NAME.backup.$(date +%Y%m%d_%H%M%S)"
    echo "📋 备份旧版本：$BACKUP_NAME"
    cp "$SERVER_PATH/plugins/$PLUGIN_NAME" "$SERVER_PATH/plugins/$BACKUP_NAME"
fi

# 复制新版本
echo "📤 部署插件到服务器..."
cp "$JAR_PATH" "$SERVER_PATH/plugins/"

if [ $? -eq 0 ]; then
    echo "✅ 部署成功！"
    echo ""
    echo "下一步："
    echo "1. 启动或重启 Minecraft 服务器"
    echo "2. 查看日志确认插件加载：tail -f $SERVER_PATH/logs/latest.log"
    echo "3. 在游戏中执行：/starcore 验证插件是否正常工作"
    echo ""
    echo "部署位置：$SERVER_PATH/plugins/$PLUGIN_NAME"
else
    echo "❌ 部署失败！"
    exit 1
fi
