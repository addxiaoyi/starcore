# StarCore 架构文档

## 项目概述

StarCore 是一个 Paper/Nukkit 原生的国家战略与政策引擎 Minecraft 服务器插件，提供完整的多人王国管理、领土系统、战争、外交等玩法。

- **语言**: Java 21
- **平台**: Paper 1.21.11+, Nukkit
- **构建**: Maven
- **版本**: 0.1.0-SNAPSHOT

## 模块架构

StarCore 采用**模块化架构**，所有功能都封装在可热插拔的模块中。

### 核心模块 (13个)

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

### 基础设施 (foundation/)

| 模块 | 功能 |
|------|------|
| player/ | 玩家数据、档案服务 |
| territory/ | 领土 claiming、区域保护 |
| economy/ | 经济系统余额存储 |
| message/ | 消息服务（多语言） |
| epoch/ | 纪元/时间系统 |
| permission/ | 权限系统 |

## 目录结构

```
src/main/java/dev/starcore/starcore/
├── core/              # 核心框架
│   ├── module/        # 模块系统
│   ├── database/      # 数据库服务
│   ├── event/         # 事件总线
│   └── service/       # 核心服务
├── foundation/        # 基础设施
├── module/            # 13个核心业务模块
├── integration/       # 插件集成
└── api/               # 公开API
```

## 配置系统

- `config.yml` - 插件全局配置
- `lang/messages_zh_cn.yml` - 中文消息
- `db/migration/` - 数据库迁移

## 构建

```bash
mvn clean package -DskipTests
# 生成的 JAR: target/starcore-0.1.0-SNAPSHOT.jar
```
