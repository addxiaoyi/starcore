# STARCORE 用户体验缺失分析

从玩家和服主（用户）视角，STARCORE 技术上很完善，但**缺少面向最终用户的文档和引导**。

---

## 🎮 玩家视角缺失（严重）

### ❌ 1. 新手教程/快速开始指南
**现状：** 只有技术文档（README.md），没有玩家友好的教程  
**影响：** 玩家不知道如何开始游戏

**参考 BetterNations 的优秀实践：**
- 清晰的游戏玩法说明
- 分步骤的新手指南
- 命令示例和效果说明

**应该包含：**
```markdown
## 新手入门 - 10分钟上手

### 第一步：创建你的国家
1. 准备 10000 金币
2. 找一个好位置（建议在你的基地）
3. 输入命令：`/sc n c 我的国家`
4. 看到信标灯光表示成功！

### 第二步：圈地保护你的领地
1. 站在你想保护的区块
2. 输入：`/sc n cl`
3. 你会收到扣费提示，再次确认
4. 看到标题提示表示圈地成功

### 第三步：邀请朋友加入
...
```

---

### ❌ 2. 命令手册/Wiki
**现状：** README 里有命令列表，但只是简写缩写  
**影响：** 玩家看不懂 `/sc n [c|cl|t|ok|x|un|ls|i|here]` 是什么意思

**需要：**
```markdown
## 命令完整手册

### 国家管理 `/starcore nation` (别名：`/sc n`)

| 命令 | 完整命令 | 说明 | 权限 | 费用 |
|------|---------|------|------|------|
| `/sc n c <名称>` | `/sc nation create` | 创建国家 | 所有玩家 | 10000金币 |
| `/sc n cl` | `/sc nation claim` | 圈地 | 国家成员 | 500金币/区块 |
| `/sc n t` | `/sc nation tool` | 获取圈地工具 | 国家成员 | 免费 |
| `/sc n ok` | `/sc nation confirm` | 确认圈地 | 国家成员 | 根据区块数 |
| `/sc n x` | `/sc nation cancel` | 取消操作 | 国家成员 | 免费 |
| `/sc n un` | `/sc nation unclaim` | 取消圈地 | 国家领袖 | 免费 |

### 示例：
**创建国家：**
```
/sc n c 天朝上国
```

**批量圈地：**
```
/sc n t              # 获取工具
# 左键点击第一个角落的方块
# 右键点击对角线的方块
/sc n ok            # 确认圈地
```
```

---

### ❌ 3. 游戏机制说明
**现状：** 只有代码和配置，没有玩家可读的机制说明  
**影响：** 玩家不理解国策、外交、战争如何运作

**需要：**
```markdown
## 游戏机制详解

### 国策系统
国策是国家的长期发展方向，可以提供各种加成效果。

**如何使用：**
1. 打开国策树：`/sc po t` 或 `/sc 国策 菜单`
2. 点击你想激活的国策
3. 确认消耗金币和国策点
4. 国策立即生效

**国策分类：**
- 🏭 **经济国策** - 提升金币收入、降低税收成本
- ⚔️ **军事国策** - 提升战斗力、降低战争损失
- 🏛️ **内政国策** - 增加人口上限、提升建筑速度
- 🤝 **外交国策** - 改善外交关系、降低外交成本

**国策效果：**
- 某些国策互相冲突（如"和平主义"和"军国主义"）
- 国策可以清除，但需要等待冷却时间
- 高级国策需要先激活前置国策

**示例国策：**
- **"重商主义"** - 金库收入 +20%
- **"精兵政策"** - 军队战斗力 +15%
- **"开放边境"** - 外交关系改善速度 +30%
```

---

### ❌ 4. 常见问题 FAQ
**现状：** 无  
**影响：** 玩家遇到问题无处求助

**需要：**
```markdown
## 常见问题 FAQ

### Q: 我创建了国家，但圈不了地？
A: 检查以下几点：
1. 你是国家成员吗？用 `/sc n i` 查看
2. 你有足够的金币吗？用 `/sc eco b` 查看余额
3. 这块地是否被其他国家占领？用 `/sc n here` 查看
4. 国家是否达到圈地上限？用 `/sc rsc s` 查看容量

### Q: 如何邀请朋友加入国家？
A: 让你的朋友输入 `/sc n join 你的国家名称`，然后国家需要通过决议。

### Q: 国策激活后多久生效？
A: 立即生效，你可以在 `/sc po s` 中看到当前激活的国策。

### Q: 战争如何发起？
A: 只有国家领袖或外交官可以宣战，使用 `/sc w d 敌国名称`

### Q: 如何查看我的国家有多少钱？
A: 使用 `/sc tr s` 查看国库状态
```

---

## 🖥️ 服主（管理员）视角缺失（中等）

### ❌ 5. 服务器配置指南
**现状：** config.yml 有注释，但缺少配置场景说明  
**影响：** 服主不知道如何调整游戏平衡

**需要：**
```markdown
## 服务器配置指南

### 推荐配置方案

#### 小型服务器（10-50人）
```yaml
nation:
  economy:
    creation-cost: 5000        # 降低门槛
    claim-cost: 100            # 便宜的圈地
  limits:
    max-nations-per-player: 1  # 避免混乱
    max-claims-per-nation: 100 # 限制扩张
```

#### 中型服务器（50-200人）
```yaml
nation:
  economy:
    creation-cost: 10000       # 标准门槛
    claim-cost: 500            # 适中的圈地成本
  limits:
    max-nations-per-player: 2  # 允许多个国家
    max-claims-per-nation: 500 # 鼓励扩张
```

#### 大型服务器（200+人）
```yaml
nation:
  economy:
    creation-cost: 50000       # 高门槛
    claim-cost: 1000           # 昂贵的圈地
  limits:
    max-nations-per-player: 3  # 灵活管理
    max-claims-per-nation: 2000 # 大规模国家
  resources:
    enabled: true              # 启用资源系统
    max-districts: 10          # 资源竞争
```

### 游戏平衡调整

**如果玩家抱怨国家创建太贵：**
→ 降低 `nation.economy.creation-cost`

**如果服务器到处都是空国家：**
→ 增加 `nation.economy.creation-cost`  
→ 启用自动清理不活跃国家

**如果国家扩张太快：**
→ 增加 `nation.economy.claim-cost`  
→ 降低 `nation.limits.max-claims-per-nation`

**如果战争太频繁：**
→ 调整 `war.cooldown`  
→ 增加战争成本
```

---

### ❌ 6. 权限配置说明
**现状：** plugin.yml 里有权限节点，但没有说明  
**影响：** 服主不知道如何分配权限

**需要：**
```markdown
## 权限配置指南

### 基础权限（所有玩家）
```yaml
permissions:
  starcore.command: true          # 基础命令使用
  starcore.nation.create: true    # 创建国家
  starcore.nation.join: true      # 加入国家
  starcore.map.web: true          # 查看地图
```

### VIP 权限
```yaml
permissions:
  starcore.nation.create: true
  starcore.limits.nations: 3      # 可创建3个国家
  starcore.limits.claims: 1000    # 圈地上限1000
```

### 管理员权限
```yaml
permissions:
  starcore.admin.*: true          # 所有管理命令
  starcore.admin.debug: true      # 调试命令
  starcore.admin.economy: true    # 经济管理
```

### 权限组推荐
**LuckPerms 示例：**
```
/lp group default permission set starcore.command true
/lp group vip permission set starcore.limits.nations 3
/lp group admin permission set starcore.admin.* true
```
```

---

### ❌ 7. 数据备份和迁移指南
**现状：** 有备份脚本，但没有使用说明  
**影响：** 服主不敢升级，怕数据丢失

**需要：**
```markdown
## 数据备份与迁移

### 日常备份（推荐每天自动）
```powershell
# Windows 服务器
.\scripts\backup-starcore-runtime.ps1 -AsZip

# 自动化：添加到 Windows 计划任务
```

```bash
# Linux 服务器
./scripts/backup-starcore-runtime.sh

# 添加到 crontab（每天凌晨3点）
0 3 * * * /path/to/backup-starcore-runtime.sh
```

### 升级前备份
```powershell
# 1. 停止服务器
# 2. 备份数据
.\scripts\backup-starcore-runtime.ps1 -AsZip

# 3. 记录备份文件名
# 例如：starcore-backup-20260615-120000.zip

# 4. 升级插件
# 5. 启动服务器
# 6. 检查是否正常
```

### 数据恢复（出问题时）
```powershell
# 1. 停止服务器
# 2. 恢复备份
.\scripts\restore-starcore-runtime.ps1 `
  -BackupPath "backups/starcore-backup-20260615-120000.zip" `
  -ReplaceExisting

# 3. 启动服务器
# 4. 验证数据
```

### 数据库迁移（SQLite → MySQL）
```yaml
# 1. 在 config.yml 中配置 MySQL
database:
  type: mysql
  mysql:
    host: localhost
    database: starcore
    username: root
    password: your_password

# 2. 重启服务器
# 3. STARCORE 自动导入旧数据
# 4. 检查日志确认迁移成功
```
```

---

### ❌ 8. 性能优化指南
**现状：** 有性能监控，但缺少优化建议  
**影响：** 服主不知道如何优化卡顿

**需要：**
```markdown
## 服务器性能优化

### 快速诊断
```bash
# 1. 查看 TPS
/tps

# 2. 查看 STARCORE 性能
/sc debug performance

# 3. 查看缓存状态
/sc debug cache
```

### 常见性能问题

#### 问题：TPS 低于 18
**可能原因：**
- 国家数量过多
- 圈地区块过多
- 资源刷新频率过高

**解决方案：**
```yaml
# config.yml
nation:
  limits:
    max-nations: 50           # 限制国家总数
    max-claims-per-nation: 500 # 限制单国家圈地

resources:
  refresh-interval: 600       # 降低刷新频率（秒）
```

#### 问题：内存占用过高
**查看缓存：**
```bash
/sc debug cache
```

**优化缓存：**
```yaml
# 如果缓存命中率 < 70%，增加缓存大小
# 如果内存不足，减小缓存大小
# 代码级优化需要开发者介入
```

#### 问题：数据库查询慢
**使用 Spark 分析：**
```bash
/spark profiler start
# 等待 30 秒
/spark profiler stop
# 查看报告链接
```

**优化建议：**
- 使用 MySQL 而非 SQLite（大型服务器）
- 启用数据库连接池
- 定期清理旧数据

### 推荐服务器配置

**小型服务器（10-50人）：**
- RAM: 4GB
- CPU: 2核
- 数据库：SQLite

**中型服务器（50-200人）：**
- RAM: 8GB
- CPU: 4核
- 数据库：MySQL

**大型服务器（200+人）：**
- RAM: 16GB+
- CPU: 8核+
- 数据库：MySQL（独立服务器）
```

---

## 🎨 视觉/UI 缺失（轻微）

### ❌ 9. 游戏内引导界面
**现状：** 纯命令操作，缺少 GUI 引导  
**建议：** 添加新手引导 NPC 或欢迎菜单

### ❌ 10. 可视化效果
**现状：** 有地图，但缺少游戏内视觉反馈  
**建议：**
- 圈地时显示粒子效果边界
- 战争时的视觉提示（烟花、粒子）
- 国策激活时的特效

---

## 📊 社区/支持缺失（中等）

### ❌ 11. Discord/QQ 群支持
**现状：** 无社区渠道  
**影响：** 用户遇到问题无法求助

### ❌ 12. 示例视频/演示
**现状：** 无视频教程  
**影响：** 新玩家不知道游戏能干什么

### ❌ 13. 更新日志（面向用户）
**现状：** 只有开发文档  
**需要：** 用户友好的更新说明

```markdown
## STARCORE v0.2.0 更新内容

### 🎉 新功能
- ✅ 新增国策树 GUI，可视化查看和激活国策
- ✅ 新增战争宣战冷却时间，防止频繁骚扰
- ✅ 新增自动备份功能

### 🔧 改进
- 圈地价格根据距离世界中心动态调整
- 优化大型国家的地图加载速度
- 改进新手提示信息

### 🐛 修复
- 修复玩家离线时无法被踢出国家的问题
- 修复国策效果不叠加的bug
- 修复Web地图上资源显示错误

### ⚠️ 注意事项
- 本次更新需要重启服务器
- 建议先备份数据
- 部分配置项已调整，请检查 config.yml
```

---

## 🎯 优先级建议

### 🔴 紧急（必须立即补充）
1. **玩家新手教程** - 10分钟快速上手
2. **命令完整手册** - 每个命令的详细说明
3. **常见问题 FAQ** - 至少20个常见问题

### 🟡 重要（1周内补充）
4. **游戏机制详解** - 国策、外交、战争如何玩
5. **服务器配置指南** - 不同规模的推荐配置
6. **权限配置说明** - 如何给玩家分配权限

### 🟢 建议（逐步补充）
7. **数据备份指南** - 服主必备
8. **性能优化指南** - 解决卡顿问题
9. **视频教程** - B站/YouTube
10. **社区建设** - Discord/QQ群

---

## 📝 总结

STARCORE **技术上非常完善**，代码质量 A+，功能齐全。

但从用户视角看，**缺少文档和引导**，就像一辆高性能跑车没有说明书：
- ❌ 玩家不知道怎么开始玩
- ❌ 服主不知道怎么配置
- ❌ 出问题了不知道怎么解决

**建议下一步：**
1. 立即创建 `docs/PLAYER_GUIDE.md` - 玩家指南
2. 立即创建 `docs/ADMIN_GUIDE.md` - 管理员指南
3. 录制一个 10 分钟的演示视频
4. 建立 Discord 或 QQ 群

这些文档比再写代码功能更重要，因为**没人会用的功能等于不存在**。
