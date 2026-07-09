# StarCore - Minecraft 国家领地核心插件

## 项目概述

**StarCore** 是一个 Paper/Nukkit 原生的国家战略与政策引擎，提供完整的多人王国管理、领土系统、外交战争、财政科技等玩法。

- **语言**: Java 21
- **平台**: Paper 1.21.11+, Nukkit
- **构建**: Maven
- **版本**: 0.1.0-SNAPSHOT

## 目录结构

```
D--qwq----mapadd/
├── starcore/                    # 主插件项目
│   ├── CLAUDE.md               # 架构文档
│   ├── docs/                   # 详细文档
│   │   ├── MODULE_INDEX.md     # 模块索引
│   │   └── DATABASE_SCHEMA.md  # 数据库架构
│   ├── src/main/java/          # 源代码 (677 个 Java 文件)
│   └── src/main/resources/     # 配置和资源
├── ExcellentCrates-spigot/      # 抽箱插件 (子模块)
├── ProtectorAPI/               # 领地保护 API (子模块)
├── starcore-remote/             # 远程通信 (子模块)
└── map/                         # 地图数据
```

## 快速开始

```bash
# 构建
cd starcore
mvn clean package

# 生成的 JAR
# target/starcore-0.1.0-SNAPSHOT.jar
```

## 核心模块 (13个)

| 模块 | 功能 |
|------|------|
| Nation | 国家创建、成员管理 |
| Map | 网页地图、领地可视化 |
| Treasury | 国库、税收 |
| Diplomacy | 外交关系、联盟 |
| War | 宣战、停战 |
| Army | 军队管理 |
| Officer | 官职任命 |
| Policy | 国家政策 |
| Technology | 科技研发 |
| Resolution | 投票决议 |
| Resource | 资源采集 |
| Event | 事件记录 |
| Government | 政体类型 |

## 文档

- [starcore/CLAUDE.md](starcore/CLAUDE.md) - 完整架构文档
- [starcore/docs/MODULE_INDEX.md](starcore/docs/MODULE_INDEX.md) - 模块索引
- [starcore/docs/DATABASE_SCHEMA.md](starcore/docs/DATABASE_SCHEMA.md) - 数据库架构

## 配置

主要配置文件位于 `starcore/src/main/resources/`:
- `config.yml` - 全局配置
- `titles.yml` - 称号配置
- `achievements.yml` - 成就配置
- `technologies.yml` - 科技树
- `resources.yml` - 资源类型
- `lang/messages_zh_cn.yml` - 中文语言包
