---
tags:
  - starcore
  - plugin
  - incomplete
  - priority/p1
  - module/gui
created: 2026-06-23
status: pending
taskId: 24
---

# P1: GUI 菜单集成

## 📋 概述

将各子模块的 GUI 集成到 TriumphNationMenu 主菜单中。

## 📁 文件位置

`src/main/java/dev/starcore/starcore/module/nation/gui/NationManagementMenuListener.java:168-198`

## ❌ 待集成

| 槽位 | 功能 | 状态 |
|------|------|------|
| 48 | 财政管理 | `// TODO: 集成财政模块的GUI` |
| 50 | 领土管理 | `// TODO: 显示领地列表` |
| 52 | 国策 | `// TODO: 集成国策模块的GUI` |
| 53 | 科技 | `// TODO: 集成科技模块的GUI` |
| 54 | 外交 | `// TODO: 集成外交模块的GUI` |

## ✅ 已有菜单

- TriumphNationMenu 主菜单 ✅
- 改名功能 ✅
- 成员管理 (基础) ✅

## 📋 任务清单

### 1. 财政管理子菜单
- [ ] 创建财政管理子菜单
- [ ] 显示国库余额
- [ ] 存款功能
- [ ] 取款功能
- [ ] 税收记录

### 2. 领土列表
- [ ] 显示领土列表
- [ ] 分页支持
- [ ] 点击查看详情

### 3. 国策 GUI
- [ ] [[P1-国策科技GUI|参见国策科技GUI任务]]

### 4. 科技 GUI
- [ ] [[P1-国策科技GUI|参见国策科技GUI任务]]

### 5. 外交 GUI
- [ ] 查看外交关系
- [ ] 发起外交请求

## 📅 预估工时

1-2 天

## 进度

- [ ] 财政管理子菜单
- [ ] 领土列表显示
- [ ] 国策 GUI
- [ ] 科技 GUI
- [ ] 外交 GUI
