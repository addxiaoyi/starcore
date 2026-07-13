# StarCore 测试服务器

用于测试 StarCore Minecraft 插件的本地 Paper 服务器。

## 下载 Paper 服务器

由于网络限制，请手动下载 Paper 1.21.11：

1. 访问 https://papermc.io/downloads/paper
2. 选择版本 **1.21.11**
3. 下载 JAR 文件
4. 重命名为 `paper-1.21.11.jar`
5. 放入本目录

## 快速开始

### Windows
双击运行 `start.bat`

### Linux/Mac
```bash
chmod +x start.sh
./start.sh
```

## 启动后操作

1. 服务器启动后，输入 `stop` 停止
2. 插件配置会自动生成在 `plugins/StarCore/` 目录
3. 再次启动服务器

## 连接服务器

- 地址: `localhost:25565`
- 服务器离线模式，可直接进入

## 文件结构

```
test-server/
├── paper-1.21.11.jar    # 下载 Paper 服务器 (需要手动下载)
├── start.bat             # Windows 启动脚本
├── start.sh              # Linux/Mac 启动脚本
├── plugins/              # 插件目录 (自动创建)
│   └── starcore-*.jar   # StarCore 插件 (自动复制)
├── world/                # 游戏世界 (自动生成)
├── eula.txt             # EULA 同意 (自动创建)
└── server.properties     # 服务器配置 (自动创建)
```

## 清理重置

删除以下目录即可重置服务器：
```bash
rm -rf world plugins logs
```
