---
tags:
  - starcore
  - plugin
  - incomplete
  - priority/p2
  - module/city
created: 2026-06-23
status: pending
taskId: 30
---

# P2: City 系统完善

## 📋 概述

完善 City (城市) 系统，实现与 Nation 的关联及经济/传送功能。

## 📁 涉及文件

- `src/main/java/dev/starcore/starcore/city/command/CityCommand.java`

## ❌ 待实现

| 行号 | 功能 | 状态 |
|------|------|------|
| 140 | 需要 Nation ID | `// TODO: 需要Nation ID` |
| 368 | 从玩家扣款 | `// TODO: 从玩家扣款（需要Vault）` |
| 388 | 给玩家加钱 | `// TODO: 给玩家加钱（需要Vault）` |
| 409 | 设置 City 出生点 | `// TODO: 设置City出生点` |
| 421 | 传送到 City 出生点 | `// TODO: 传送到City出生点` |

## 📋 任务清单

### 1. Nation 关联
- [ ] City 创建时关联 Nation
- [ ] Nation 解散时处理 City
- [ ] 城市数据模型添加 NationId 字段

### 2. 经济集成
- [ ] 城市创建费用
- [ ] 城市升级费用
- [ ] 城市维护费用

### 3. 传送功能
- [ ] 设置城市出生点
- [ ] 传送命令实现
- [ ] 传送冷却/费用

### 4. GUI 菜单
- [ ] 城市信息菜单
- [ ] 城市管理菜单

## 🔗 依赖

- [[P1-Vault经济集成]]
- NationModule

## 📅 预估工时

1-2 天

## 进度

- [ ] Nation 关联
- [ ] 经济功能
- [ ] 传送功能
- [ ] GUI 菜单
