---
tags:
  - starcore
  - plugin
  - incomplete
  - priority/p0
  - module/nation
created: 2026-06-23
status: pending
taskId: 23
---

# P0: Nation 模型数据填充

## 📋 概述

`Nation.java` 中的多个 get 方法返回硬编码假数据，需要实现真实数据查询。

## 📁 文件位置

`src/main/java/dev/starcore/starcore/module/nation/model/Nation.java:136-223`

## ❌ 待实现方法

| 方法 | 当前返回值 | 需要实现 |
|------|-----------|----------|
| `getTerritoryCount()` | `0` | 从 TerritoryModule 查询 |
| `getBalance()` | `0.0` | 从 TreasuryModule 查询 |
| `getFoundedDate()` | `"2024-01-01"` | 添加字段并格式化 |
| `getPolicyCount()` | `0` | 从 PolicyModule 查询 |
| `getTechnologyCount()` | `0` | 从 TechnologyModule 查询 |
| `getAllyCount()` | `0` | 从 DiplomacyModule 查询 |
| `getWarCount()` | `0` | 从 WarModule 查询 |
| `getTaxRate()` | `0.0` | 从 TaxService 查询 |
| `getCapitalLocation()` | `null` | 从配置或领土获取 |
| `getTownLocation()` | `null` | 从领土获取 |
| `getTownNames()` | `[]` | 从领土获取 |

## 🔗 依赖

- TerritoryModule
- TreasuryModule
- PolicyModule
- TechnologyModule
- DiplomacyModule
- WarModule
- TaxService

## 📅 预估工时

1 天

## 进度

- [ ] 实现领土计数
- [ ] 实现国库余额查询
- [ ] 添加建国日期字段
- [ ] 实现政策计数
- [ ] 实现科技计数
- [ ] 实现外交计数
- [ ] 实现位置查询
