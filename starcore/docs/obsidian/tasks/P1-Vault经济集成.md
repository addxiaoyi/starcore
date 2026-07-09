---
tags:
  - starcore
  - plugin
  - incomplete
  - priority/p1
  - module/economy
created: 2026-06-23
status: pending
taskId: 26
---

# P1: Vault 经济集成

## 📋 概述

将 Vault Economy API 集成到税收系统和任务系统中。

## 📁 涉及文件

### 税收系统
- `src/main/java/dev/starcore/starcore/nation/tax/TaxCollectionService.java:151,159`

### 任务系统
- `src/main/java/dev/starcore/starcore/quest/QuestService.java:188,207,210`
- `src/main/java/dev/starcore/starcore/quest/DailyQuestService.java:171`
- `src/main/java/dev/starcore/starcore/quest/CommissionService.java:55,147,180`

### City 系统
- `src/main/java/dev/starcore/starcore/city/command/CityCommand.java:368,388`

## ❌ 待实现

### TaxCollectionService
```java
// 行 151, 159
// TODO: 实际实现需要集成Vault
```

### QuestService
```java
// 行 188: 集成经济系统
// 行 207: 集成声望系统
// 行 210: 集成称号系统
```

## 📋 任务清单

### 1. Vault 依赖
- [ ] 检查 pom.xml 是否已有 Vault 依赖
- [ ] 添加 Vault API 依赖

### 2. 经济服务封装
- [ ] 创建 VaultEconomyService 封装类
- [ ] 实现余额查询
- [ ] 实现转账功能
- [ ] 实现扣款功能

### 3. 税收系统集成
- [ ] 集成 Vault 到 TaxCollectionService
- [ ] 测试自动扣税

### 4. 任务系统集成
- [ ] 任务奖励发放
- [ ] 任务发布费用
- [ ] 委托费用

## 🔗 依赖

- Vault API
- 经济插件 ( EssentialsX / CMI / etc.)

## 📅 预估工时

1 天

## 进度

- [ ] 添加 Vault 依赖
- [ ] 创建经济服务封装
- [ ] 税收系统集成
- [ ] 任务系统集成
