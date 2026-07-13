# 🌟 StarCore

**Paper/Nukkit 原生国家战略与政策引擎** — 完整的多人王国管理、领土系统、外交战争、财政科技玩法。

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-green.svg)](https://www.minecraft.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Build](https://github.com/addxiaoyi/starcore/workflows/Build/badge.svg)](https://github.com/addxiaoyi/starcore/actions)
[![Stars](https://img.shields.io/github/stars/addxiaoyi/starcore?style=social)](https://github.com/addxiaoyi/starcore)
[![Last Commit](https://img.shields.io/github/last-commit/addxiaoyi/starcore/main)](https://github.com/addxiaoyi/starcore/commits/main)

---

## ✨ 核心功能

| 模块 | 功能 | 状态 |
|------|------|------|
| **Nation** | 国家创建、成员管理、首都选举 | ✅ |
| **Territory** | 领地圈地、租约、可视化预览 | ✅ |
| **Treasury** | 国库管理、税收、预算 | ✅ |
| **Diplomacy** | 外交关系、联盟、停战 | ✅ |
| **War** | 宣战、战争规则、停战协议 | ✅ |
| **Army** | 军队管理、军阵、教条 | ✅ |
| **Military** | 统一军事指挥中心、战场态势 | ✅ |
| **Officer** | 官职任命、权限管理 | ✅ |
| **Policy** | 国家政策、效果应用 | ✅ |
| **Technology** | 科技研发、赛季科技树 | ✅ |
| **Resolution** | 投票决议、公投系统 | ✅ |
| **Resource** | 资源区块、采集、加工 | ✅ |
| **Government** | 政体类型（君主/共和/独裁） | ✅ |
| **Social** | 好友、邮件、声望、影响力 | ✅ |

---

## 🚀 快速开始

```bash
# 构建插件
cd starcore
mvn clean package

# 启动测试服务器
cd ../test-server
# 1. 手动下载 Paper 1.21.11 JAR 到本目录
# 2. 双击 start.bat (Windows) 或 ./start.sh (Linux)
```

**生成**: `starcore/target/starcore-0.1.0-SNAPSHOT.jar`

### 前置要求

| 组件 | 版本 | 说明 |
|------|------|------|
| Java | 21+ | 推荐 Eclipse Temurin |
| Minecraft | 1.21.11+ | Paper/Spigot/Folia |
| Vault | 最新 | 经济必需 |

### 集成支持

- 💰 **Vault** — 经济系统
- 📋 **PlaceholderAPI** — 占位符扩展
- 🗺️ **squaremap/Pl3xMap/dynmap** — 网页地图
- 🛡️ **ProtectorAPI/WorldGuard** — 领地保护

---

## 📁 项目结构

```
starcore/
├── src/main/java/dev/starcore/starcore/
│   ├── core/           # 核心框架 (模块/事件/调度)
│   ├── foundation/     # 基础设施 (经济/存储/反馈)
│   ├── module/         # 业务模块 (国家/战争/科技...)
│   ├── integration/    # 插件集成 (Vault/PAPI)
│   └── api/            # 公开API
├── src/main/resources/
│   ├── config.yml      # 主配置
│   ├── lang/           # 多语言文件
│   └── db/migration/   # 数据库迁移
└── pom.xml
```

---

## 📊 统计数据

| 指标 | 数量 |
|------|------|
| Java 类 | 677+ |
| 核心模块 | 14 |
| 集成插件 | 6 |
| 测试用例 | 154 |

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/amazing`)
3. 提交更改 (`git commit -m 'Add amazing feature'`)
4. 推送分支 (`git push origin feature/amazing`)
5. 创建 Pull Request

---

## 📄 许可证

[MIT License](LICENSE) — 免费商用，请注明作者
