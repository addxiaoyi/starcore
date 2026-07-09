# STARCORE 发布渠道文案包

日期：`2026-06-05`

这份文档用于直接整理 Modrinth / Hangar / SpigotMC 的发布文案、摘要和素材提示，避免发版时临时拼字。

如需把这里的内容直接导出成平台成品文件，可执行：

```powershell
.\scripts\build-starcore-release-channel-assets.ps1
```

## 一句话定位

STARCORE 是一个面向 RPG / 国家经营服务器的 Paper 原生国家战略插件，内置国家、政体、外交、资源区块、网页地图、原生圈地与保护系统，不依赖 Towny、WorldGuard、WorldEdit、Lands。

## 核心卖点

- 原生国家与领地体系，不依赖 Towny
- 游戏内圈地、圈地工具、网页拖拽圈地共用一套规则
- 国家资源区块、信标、迁移核心、等级成长
- 中文配置、中文命令、中文语言文件
- 现实时间同步、纪元系统、政体/外交/战争/科技/国策
- 默认 SQLite，支持切换 MySQL
- 可选 `ProtectorAPI` 软依赖桥接，不把外部保护插件绑死成核心依赖

## 兼容信息

- 平台：Paper
- Java：21
- 当前构建目标：`1.21.11-R0.1-SNAPSHOT`
- 插件版本：`0.1.0-SNAPSHOT`
- 软依赖：
  - `ProtectorAPI`（可选）

## Modrinth 文案

### 标题副描述

`Paper-native nations, claims, web map, and resource districts for RPG servers`

### 简短描述

`Independent nation strategy plugin with native claims, web map dragging, resource districts, Chinese config, and optional ProtectorAPI bridge.`

### 长描述

STARCORE is a Paper-native national strategy plugin built for RPG and geopolitical Minecraft servers. It ships its own nation, territory, protection, map, treasury, diplomacy, policy, technology, war, officer, event, epoch, and real-time sync systems without requiring Towny, WorldGuard, WorldEdit, Lands, Vault, or WorldDynamics at runtime.

What you get:

- Native nation and territory gameplay
- In-game claim tool and web map drag claiming on one shared validation path
- Nation resource districts with protected beacons, migration flow, and nation XP growth
- Structured commands with short aliases and Chinese language coverage
- Chinese-commented config defaults
- Real-time clock sync and configurable epoch duration
- SQLite by default, MySQL optional
- Optional `ProtectorAPI` compatibility bridge through softdepend

STARCORE is designed to keep its own core stable and maintainable first. External plugins may be studied for architecture ideas, but the shipped runtime core remains independent.

### 推荐标签

- `paper`
- `rpg`
- `economy`
- `world-management`
- `utility`

## Hangar 文案

### 概述

STARCORE is an independent Paper plugin for RPG servers that need nations, native claims, web map workflows, resource districts, and geopolitical progression without pulling in Towny-style runtime dependencies.

### 功能列表

- Native nation creation and claim progression
- Claim tool + command claim + web claim
- Resource district beacons and migration
- Treasury, diplomacy, policy, technology, war, officer, and event modules
- Epoch and real-time world clock sync
- Chinese language defaults and Chinese-commented config
- SQLite default storage with MySQL option
- Optional ProtectorAPI bridge

### 安装说明

1. Put `starcore-0.1.0-SNAPSHOT.jar` into `plugins/`
2. Start the server once
3. Review `plugins/STARCORE/config.yml`
4. Set `map.web.public-url` if players access the map externally
5. If needed, install `ProtectorAPI` as an optional soft dependency

## SpigotMC 文案

### 资源简介

STARCORE 是一个面向 RPG / 国家经营服务器的 Paper 原生插件，提供国家、政体、外交、国策、科技、战争、资源区块、网页地图、原生圈地与保护，不依赖 Towny、WorldGuard、WorldEdit、Lands。

### 亮点

- 独立原生圈地核心
- 左键/右键区块选择圈地工具
- 网页地图拖拽圈地并回服确认
- 资源区块信标与迁移机制
- 中文命令、中文配置注释、Tab 补全
- 现实时间同步与纪元系统
- 默认 SQLite，支持 MySQL
- 可选 ProtectorAPI 软依赖兼容

### 适合的服务器

- RPG 生存服
- 国家战争服
- 地缘政治 / 经营类服务器
- 需要网页地图交互但不想依赖 Towny 生态的服务器

## 发布更新日志模板

### 首发版本模板

```text
[STARCORE] First public build

- Native nation / claim / protection core
- In-game claim tool and web map drag claim flow
- Nation resource districts with beacon migration flow
- Treasury, diplomacy, policy, technology, war, officer, and event modules
- Chinese-commented config and Chinese command coverage
- SQLite default storage with optional MySQL switch
- Optional ProtectorAPI runtime bridge
- Unified release verification pipeline and smoke evidence
```

### 小版本更新模板

```text
[STARCORE] Maintenance and verification update

- Improved release verification workflow
- Updated runtime selfcheck coverage
- Refined ProtectorAPI compatibility smoke
- Isolated single bridge failures from the shared external protection chain
- Refreshed release documentation and operation entry points
```

## 当前可用截图素材

- 浏览器地图截图：
  - `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260606-045357.png`
- 浏览器 DOM 工件：
  - `D:\qwq\项目\mapadd\test-server-paper-1.21.11\zcode-starcore-map-browser-smoke-20260606-045357.dom.html`

## 当前可引用验证证据

- 总体自动验证摘要：
  - [LATEST_VERIFICATION_SUMMARY.md](/D:/qwq/项目/mapadd/starcore/docs/LATEST_VERIFICATION_SUMMARY.md)
- 运维/发布入口：
  - [OPERATIONS_RUNBOOK_2026-06-05.md](/D:/qwq/项目/mapadd/starcore/docs/OPERATIONS_RUNBOOK_2026-06-05.md)
  - [RELEASE_CHECKLIST_2026-06-05.md](/D:/qwq/项目/mapadd/starcore/docs/RELEASE_CHECKLIST_2026-06-05.md)
- 最新完整 smoke：
  - `target/smoke-harness-20260606-045357/smoke-summary.json`

## 发布前最后核对

- jar 是否来自本次 `mvn -q package`
- `LATEST_VERIFICATION_SUMMARY.md` 是否为本次最新生成
- 公开发版包是否已使用 `verify-starcore-release.ps1 -RequireRealSparkProfile`，并确认 Spark summary 不是 `sample-verification` 或 `manual`
- 是否确认 `ProtectorAPI` 仍然只是 softdepend
- 是否挑选了至少一张网页地图截图
- 是否在平台说明中明确：
  - 不依赖 Towny / WorldGuard / WorldEdit / Lands
  - 默认中文配置注释
  - 默认 SQLite，可切换 MySQL
