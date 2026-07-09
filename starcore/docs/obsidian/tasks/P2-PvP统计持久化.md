---
tags:
  - starcore
  - plugin
  - incomplete
  - priority/p2
  - module/pvp
created: 2026-06-23
status: pending
taskId: 28
---

# P2: PvP 统计持久化

## 📋 概述

将 PvP 统计数据和决斗记录持久化到数据库。

## 📁 涉及文件

- `src/main/java/dev/starcore/starcore/pvp/stats/PvPStatsService.java:129,140`
- `src/main/java/dev/starcore/starcore/pvp/duel/DuelService.java:316`

## ❌ 当前状态

```java
// PvPStatsService
// 行 129
public void saveStats(Player player, PvPStats stats) {
    // TODO: 实际保存到数据库或文件
}

// 行 140
public PvPStats loadStats(Player player) {
    // TODO: 实际从数据库或文件加载
}

// DuelService
// 行 316
public void saveDuelHistory(DuelResult result) {
    // TODO: 实际保存到数据库或文件
}
```

## 📋 任务清单

### 1. PvP 统计
- [ ] 实现统计保存到数据库
- [ ] 实现统计加载
- [ ] 实现统计更新

### 2. 决斗记录
- [ ] 实现决斗历史保存
- [ ] 实现决斗记录查询

### 3. 数据库表
```sql
CREATE TABLE pvp_stats (
    player_uuid VARCHAR(36) PRIMARY KEY,
    kills INT DEFAULT 0,
    deaths INT DEFAULT 0,
    win_streak INT DEFAULT 0,
    best_streak INT DEFAULT 0,
    total_damage DOUBLE DEFAULT 0,
    updated_at TIMESTAMP
);

CREATE TABLE duel_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    player1_uuid VARCHAR(36),
    player2_uuid VARCHAR(36),
    winner_uuid VARCHAR(36),
    duration INT,
    timestamp TIMESTAMP
);
```

## 📅 预估工时

0.5 天

## 进度

- [ ] PvP 统计持久化
- [ ] 决斗记录持久化
