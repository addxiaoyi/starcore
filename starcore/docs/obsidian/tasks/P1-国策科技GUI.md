---
tags:
  - starcore
  - plugin
  - incomplete
  - priority/p1
  - module/policy
  - module/technology
created: 2026-06-23
status: pending
taskId: 27
---

# P1: 国策/科技 GUI

## 📋 概述

创建国策树和科技树的 GUI 菜单，集成到 TriumphNationMenu。

## 📁 涉及模块

- `src/main/java/dev/starcore/starcore/module/policy/`
- `src/main/java/dev/starcore/starcore/module/technology/`

## ✅ 已完成

### 国策模块 (90%)
- Policy.java - 政策数据模型
- PolicyEffect.java - 政策效果
- PolicyTree.java - 国策树结构

### 科技模块 (90%)
- Technology.java - 科技数据模型
- TechEffect.java - 科技效果
- TechTree.java - 科技树结构

## ❌ 待完成

### 国策 GUI
- [ ] 创建 PolicyTreeMenu 类
- [ ] 显示国策树视图
- [ ] 可研究/已研究/锁定状态
- [ ] 点击研究功能

### 科技 GUI
- [ ] 创建 TechTreeMenu 类
- [ ] 显示科技树视图
- [ ] 前置科技解锁判定
- [ ] 点击研究功能

## 📐 GUI 设计

### 国策树布局
```
┌─────────────────────────────────────┐
│  国策树  [返回]                      │
├─────────────────────────────────────┤
│  [政策1]  [政策2]  [政策3]          │
│     ↓         ↓         ↓          │
│  [政策4]  [政策5]  [政策6] [政策7]   │
│                                     │
│  [已研究 ✓] [可研究 →] [锁定 🔒]    │
└─────────────────────────────────────┘
```

### 科技树布局
```
┌─────────────────────────────────────┐
│  科技树  [返回]                      │
├─────────────────────────────────────┤
│  [基础科技]                          │
│      ↓                              │
│  [中级科技]  [中级科技]              │
│      ↓         ↓                    │
│  [高级科技]  [高级科技]  [高级科技]  │
└─────────────────────────────────────┘
```

## 📅 预估工时

1-2 天

## 进度

- [ ] 国策树 GUI
- [ ] 科技树 GUI
- [ ] 集成到主菜单
