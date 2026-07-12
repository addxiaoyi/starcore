# 🌟 StarCore

**Paper/Nukkit 原生国家战略与政策引擎** — 完整的多人王国管理、领土系统、外交战争、财政科技玩法。

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-green.svg)](https://www.minecraft.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Build](https://github.com/addxiaoyi/starcore/workflows/Build/badge.svg)](https://github.com/addxiaoyi/starcore/actions)

---

## ✨ 核心功能

| 模块 | 功能 |
|------|------|
| **Nation** | 国家创建、成员管理、首都选举 |
| **Territory** | 领地圈地、租约、可视化预览 |
| **Treasury** | 国库管理、税收、预算 |
| **Diplomacy** | 外交关系、联盟、停战 |
| **War** | 宣战、战争规则、停战协议 |
| **Army** | 军队管理、军阵、教条 |
| **Officer** | 官职任命、权限管理 |
| **Policy** | 国家政策、效果应用 |
| **Technology** | 科技研发、赛季科技树 |
| **Resolution** | 投票决议、公投系统 |
| **Resource** | 资源区块、采集、加工 |
| **Event** | 事件记录、查询、统计 |
| **Government** | 政体类型（君主/共和/独裁） |

---

## 🚀 快速开始

### 构建

```bash
mvn clean package
```

生成文件：`target/starcore-0.1.0-SNAPSHOT.jar`

### 前置要求

- **Java** 21+
- **Minecraft** 1.21.11+ (Paper/Spigot)
- **Vault**（必需）

### 集成支持

| 集成 | 说明 |
|------|------|
| Vault | 经济系统 |
| PlaceholderAPI | 占位符扩展 |
| squaremap/Pl3xMap/dynmap | 地图渲染 |
| ProtectorAPI/WorldGuard | 领地保护 |
| Citizens | NPC 集成 |
| PacketEvents | 数据包处理 |

---

## 📋 主要命令

### 国家系统
| 命令 | 描述 |
|------|------|
| `/nation` | 国家管理 |
| `/treasury` | 国库管理 |
| `/government` | 政体管理 |

### 军事外交
| 命令 | 描述 |
|------|------|
| `/war` | 战争系统 |
| `/army` | 军队管理 |
| `/diplomacy` | 外交关系 |

### 领土科技
| 命令 | 描述 |
|------|------|
| `/territory` / `/claim` | 领地管理 |
| `/technology` | 科技研发 |
| `/policy` | 政策系统 |
| `/resolution` | 投票决议 |

### 社交系统
| 命令 | 描述 |
|------|------|
| `/social` | 社交菜单 |
| `/zone` | 区域系统 |
| `/visualizer` | 交互可视化 |

---

## 🏗️ 项目架构

```
src/main/java/dev/starcore/starcore/
├── core/                    # 核心框架
│   ├── module/             # 模块系统
│   ├── database/           # 数据库服务
│   ├── event/              # 事件总线
│   ├── scheduler/          # 任务调度
│   └── service/            # 核心服务
├── foundation/             # 基础设施
│   ├── player/             # 玩家数据
│   ├── territory/          # 领土
│   ├── economy/            # 经济
│   ├── message/            # 消息服务
│   └── permission/         # 权限
├── module/                 # 业务模块
│   ├── nation/             # 国家系统
│   ├── war/                # 战争系统
│   ├── army/               # 军事系统
│   ├── diplomacy/          # 外交系统
│   ├── treasury/           # 国库系统
│   ├── technology/         # 科技系统
│   ├── policy/             # 政策系统
│   └── ...
├── integration/             # 插件集成
└── api/                    # 公开API
```

### 核心服务

| 服务 | 说明 |
|------|------|
| `StarCoreContext` | 全局上下文，持有所有核心服务引用 |
| `ModuleManager` | 模块生命周期管理 |
| `StarCoreEventBus` | 事件总线，跨模块通信 |
| `DatabaseService` | 数据库连接池（MySQL/SQLite/Redis） |
| `StarCoreScheduler` | 异步任务调度（Folia 兼容） |

---

## 📊 项目统计

| 指标 | 数量 |
|------|------|
| Java 类文件 | 677+ |
| 核心模块 | 13 |
| 集成插件支持 | 6 |

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

### 开发环境

1. 安装 JDK 21+
2. 克隆仓库：`git clone https://github.com/addxiaoyi/starcore.git`
3. 构建：`mvn clean package`
4. 复制 JAR 到服务器 plugins 目录

---

## 📄 许可证

[MIT License](LICENSE)

---

## 🔗 链接

- [问题反馈](https://github.com/addxiaoyi/starcore/issues)
