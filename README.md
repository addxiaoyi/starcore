# 🌟 StarCore - Minecraft 国家领地核心插件

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-green.svg)](https://www.minecraft.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Build](https://github.com/addxiaoyi/starcore/actions/workflows/build.yml/badge.svg)](https://github.com/addxiaoyi/starcore/actions/workflows/build.yml)

**StarCore** 是 Paper/Nukkit 原生的国家战略与政策引擎，提供完整的多人王国管理、领土系统、外交战争、财政科技等玩法。

---

## ✨ 核心功能

### 🏛️ 13 个核心模块

| 模块 | 功能 |
|------|------|
| Nation | 国家创建、成员管理、首都选举 |
| Territory | 领地圈地、租约、可视化预览 |
| Treasury | 国库管理、税收、预算 |
| Diplomacy | 外交关系、联盟、停战 |
| War | 宣战、战争规则、停战协议 |
| Army | 军队管理、军阵、教条 |
| Officer | 官职任命、权限管理 |
| Policy | 国家政策、效果应用 |
| Technology | 科技研发、赛季科技树 |
| Resolution | 投票决议、公投系统 |
| Resource | 资源区块、采集、加工 |
| Event | 事件记录、查询、统计 |
| Government | 政体类型（君主/共和/独裁） |

### 🎮 特色功能

- **🗺️ 实时地图** - Web 可视化领地地图、资源标记
- **⛏️ 可视化圈地** - 金铲子预览、价格计算、确认机制
- **💬 视觉反馈** - Title、粒子、音效、BossBar
- **🎨 GUI 菜单** - 配置驱动的现代菜单系统
- **📊 事件追踪** - 完整的历史记录与导出

### 🔌 集成支持

| 集成 | 功能 |
|------|------|
| Vault | 经济系统 |
| PlaceholderAPI | 占位符变量 |
| ProtectorAPI | 领地保护 |
| squaremap/Pl3xMap/dynmap | 地图渲染 |
| WorldGuard | 区域保护 |

---

## 📦 安装

### 前置要求

- Java 21+
- Minecraft 1.21.11+ (Paper/Spigot)
- **Vault**（必需）

### 快速安装

```bash
# 1. 下载或构建 JAR
mvn clean package -DskipTests

# 2. 放入服务器 plugins/ 目录
cp starcore/target/starcore-*.jar /path/to/server/plugins/

# 3. 重启服务器
```

---

## 🚀 快速开始

```bash
# 国家管理
/sc n create <国家名>       # 创建国家
/sc n info                  # 查看国家信息
/sc n invite <玩家>         # 邀请玩家

# 领地圈地
/sc claim tool              # 获取圈地工具
/sc claim confirm           # 确认圈地

# 财政
/sc treasury deposit <金额> # 存款到国库
/sc treasury balance        # 查看国库

# 外交
/sc diplomacy ally <国家>   # 添加联盟
/sc diplomacy enemy <国家>  # 宣战
```

---

## 🏗️ 项目结构

```
starcore/
├── src/main/java/dev/starcore/starcore/
│   ├── foundation/        # 基础设施（经济、存储、反馈）
│   ├── module/            # 13个核心业务模块
│   ├── integration/       # 插件集成
│   └── api/               # 公开API
└── src/main/resources/
    ├── config.yml         # 主配置
    ├── lang/              # 语言文件
    └── db/migration/       # 数据库迁移
```

---

## 🔧 开发

```bash
# 克隆
git clone https://github.com/addxiaoyi/starcore.git
cd starcore

# 构建
mvn clean package

# 生成: target/starcore-0.1.0-SNAPSHOT.jar
```

### 技术栈

| 技术 | 用途 |
|------|------|
| Java 21 | 语言 |
| Paper 1.21.11 | 服务器 |
| Maven | 构建工具 |
| HikariCP | 数据库连接池 |
| SQLite/MySQL | 数据存储 |
| Vault | 经济集成 |

---

## 📊 统计数据

- **677+** 个 Java 类文件
- **13** 个核心模块
- **6** 个集成插件
- **1000+** 个审计问题已修复

---

## 📄 许可证

[MIT License](LICENSE)

---

## 🔗 链接

- [文档](starcore/README.md)
- [问题反馈](https://github.com/addxiaoyi/starcore/issues)
- [构建状态](https://github.com/addxiaoyi/starcore/actions)

---

*Made with ❤️*
