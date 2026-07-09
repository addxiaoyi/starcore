# 🌟 StarCore

*Minecraft Paper/Nukkit 国家战略与政策引擎*

[![Java](https://img.shields.io/badge/Java-21-orange)](https://adoptium.net/)
[![Minecraft](https://img.shields.io/badge/1.21.11-1.21.11-green)](https://www.minecraft.net/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)
[![Build](https://img.shields.io/github/actions/workflow/status/addxiaoyi/starcore/build.yml)](https://github.com/addxiaoyi/starcore/actions)

---

## 功能

| 模块 | 说明 |
|------|------|
| Nation | 国家创建、成员管理、首都选举 |
| Territory | 领地圈地、租约、金铲子可视化 |
| Treasury | 国库管理、税收、预算 |
| Diplomacy | 外交关系、联盟、停战 |
| War | 宣战、停战协议 |
| Army | 军队管理、军阵、教条 |
| Officer | 官职任命、权限管理 |
| Policy | 国策效果系统 |
| Technology | 科技研发、赛季科技树 |
| Resolution | 投票决议、公投 |
| Government | 政体类型（君主/共和/独裁） |

---

## 安装

**要求**: Java 21+, Minecraft 1.21.11+, Vault

```bash
# 构建
mvn clean package -DskipTests

# 放入服务器 plugins/ 目录
cp starcore/target/starcore-*.jar /path/to/plugins/
```

---

## 快速开始

```bash
/sc n create <国家名>      # 创建国家
/sc claim tool            # 获取圈地工具
/sc treasury deposit 1000 # 存款
```

---

## 技术栈

- **Paper 1.21.11** - 服务器
- **HikariCP** - 数据库连接池
- **Caffeine** - 缓存
- **ProtocolLib** - 数据包处理
- **Vault** - 经济集成

---

## 许可证

[MIT](LICENSE)
