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

```bash
# 构建
mvn clean package

# 生成: target/starcore-0.1.0-SNAPSHOT.jar
```

### 前置要求

- Java 21+
- Minecraft 1.21.11+ (Paper/Spigot)
- **Vault**（必需）

### 集成支持

- Vault 经济系统
- PlaceholderAPI 占位符
- squaremap/Pl3xMap/dynmap 地图渲染
- ProtectorAPI/WorldGuard 领地保护

---

## 📁 项目结构

```
src/main/java/dev/starcore/starcore/
├── foundation/        # 基础设施（经济、存储、反馈）
├── module/           # 核心业务模块
├── integration/      # 插件集成
└── api/             # 公开API
```

---

## 📊 项目统计

| 指标 | 数量 |
|------|------|
| Java 类文件 | 1800+ |
| 核心模块 | 13 |
| 集成插件支持 | 6 |

---

## 📄 许可证

[MIT License](LICENSE)

---

## 🔗 链接

- [问题反馈](https://github.com/addxiaoyi/starcore/issues)
