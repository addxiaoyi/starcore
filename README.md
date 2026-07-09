# 🌟 StarCore

**Minecraft 国家战略与政策引擎** | Paper 1.21.11+ | Java 21

[![Build](https://github.com/addxiaoyi/starcore/actions/workflows/build.yml/badge.svg)](https://github.com/addxiaoyi/starcore/actions)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

## ✨ 核心功能

| 模块 | 功能 |
|------|------|
| **Nation** | 国家创建、成员管理、首都选举 |
| **Territory** | 领地圈地、可视化预览 |
| **Treasury** | 国库管理、税收 |
| **Diplomacy** | 外交关系、联盟、停战 |
| **War** | 宣战、战争规则 |
| **Army** | 军队管理 |
| **Officer** | 官职任命、权限管理 |
| **Policy** | 国家政策 |
| **Technology** | 科技研发 |
| **Resolution** | 投票决议 |
| **Resource** | 资源区块 |
| **Government** | 政体类型（君主/共和/独裁） |

## 🎮 特色

- 🗺️ 实时地图 (squaremap/Pl3xMap)
- ⛏️ 可视化圈地工具
- 🎨 配置驱动 GUI 菜单
- 📊 完整事件追踪

## 📦 安装

```bash
# 构建
mvn clean package -DskipTests

# 或下载 Release
cp starcore/target/starcore-*.jar plugins/
```

**依赖**: Java 21+ | Paper 1.21.11+ | Vault

## 🚀 快速开始

```bash
/sc n create <国家名>    # 创建国家
/sc claim tool          # 获取圈地工具
/sc treasury deposit    # 存款
```

## 🛠️ 技术栈

- Java 21 | Paper 1.21.11 | Maven
- HikariCP | SQLite/MySQL
- Vault | PlaceholderAPI | ProtectorAPI

## 📄 许可证

[MIT](LICENSE)

---

*Made with ❤️*
