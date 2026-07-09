# 军队单位系统设计文档

**版本：** v1.0  
**日期：** 2026-06-15  
**状态：** 设计阶段

---

## 📋 概述

军队单位系统是 STARCORE 战争模块的核心战术玩法，提供具体的军队单位创建、管理、移动、战斗等功能。

---

## 🎯 设计目标

### 核心目标
1. 提供具体的战斗玩法（而非只有战争状态）
2. 简单易懂的操作（避免过于复杂）
3. 战略深度（兵种相克、地形影响）
4. 性能友好（避免大量实体）

### 参考
- **BetterNations** - 军队单位、驻扎、运输、攻城
- **Towny** - 简化的战争机制
- **文明系列** - 兵种和战斗

---

## 🏗️ 架构设计

### 模块结构

```
src/main/java/dev/starcore/starcore/module/army/
├── ArmyModule.java                    # 模块入口
├── ArmyService.java                   # 核心服务
├── model/
│   ├── ArmyUnit.java                  # 军队单位
│   ├── ArmyType.java                  # 兵种枚举
│   ├── ArmyState.java                 # 军队状态
│   ├── ArmySupply.java                # 补给
│   └── BattleResult.java              # 战斗结果
├── command/
│   ├── ArmyCommand.java               # /sc army 命令
│   └── ArmyCommandHandler.java        # 命令处理
├── gui/
│   ├── ArmyManagementMenu.java        # 军队管理 GUI
│   └── ArmyMenuListener.java          # GUI 监听器
├── battle/
│   ├── BattleCalculator.java          # 战斗计算
│   ├── BattleModifier.java            # 战斗修正
│   └── SiegeEngine.java               # 攻城引擎
├── entity/
│   ├── ArmyEntityManager.java         # 实体管理
│   └── ArmyEntityRenderer.java        # 实体渲染
└── storage/
    ├── ArmyStateStorage.java          # 持久化
    └── ArmyStateCodec.java            # 编解码
```

---

## 📊 核心概念

### 1. 军队单位（Army Unit）

**基本属性：**
```java
public class ArmyUnit {
    private UUID id;                    // 唯一ID
    private UUID nationId;              // 所属国家
    private ArmyType type;              // 兵种
    private int soldiers;               // 士兵数量
    private double health;              // 生命值（0-100）
    private double morale;              // 士气（0-100）
    private Location location;          // 位置
    private ArmyState state;            // 状态
    private int supply;                 // 补给（0-100）
    private Instant createdAt;          // 创建时间
}
```

**兵种类型：**
```java
public enum ArmyType {
    INFANTRY,      // 步兵 - 平衡型
    CAVALRY,       // 骑兵 - 高机动
    ARCHER,        // 弓箭手 - 远程
    SIEGE,         // 攻城器械 - 攻城
    DEFENSIVE      // 守军 - 防御
}
```

**军队状态：**
```java
public enum ArmyState {
    STATIONARY,    // 驻扎 - 防御+2, 补给消耗低
    MARCHING,      // 行军 - 可移动, 补给消耗高
    ATTACKING,     // 进攻 - 攻击领地
    SIEGING,       // 攻城 - 攻击城镇
    DEFENDING      // 防御 - 驻守领地
}
```

---

### 2. 兵种相克

**基础相克关系：**
```
步兵 → 弓箭手（克制）
弓箭手 → 骑兵（克制）
骑兵 → 步兵（克制）
攻城器械 → 城镇（有效）
```

**克制加成：** +30% 伤害

---

### 3. 战斗计算

**基础公式：**
```
伤害 = 基础攻击力 × 士兵数 × (生命值/100) × (士气/100) × 兵种克制 × 地形修正
```

**属性对比：**
| 兵种 | 攻击力 | 防御力 | 生命值 | 机动性 | 成本 |
|------|--------|--------|--------|--------|------|
| 步兵 | 10 | 15 | 100 | 1.0 | 100 |
| 骑兵 | 15 | 10 | 80 | 2.0 | 200 |
| 弓箭手 | 12 | 8 | 70 | 1.5 | 150 |
| 攻城 | 5 | 20 | 150 | 0.5 | 300 |
| 守军 | 8 | 25 | 120 | 0 | 50 |

---

### 4. 补给系统

**补给消耗：**
- 驻扎：1 补给/小时
- 行军：3 补给/小时
- 战斗：5 补给/小时

**补给来源：**
1. 国库资源
2. 驻扎在己方领地（自动补给）
3. 手动补给（命令）

**补给不足影响：**
- 士气下降：-10/小时
- 战斗力下降：-20%
- 最终溃散

---

### 5. 攻城战

**攻城流程：**
```
1. 军队移动到敌方城镇相邻区块
2. 切换到"攻城"状态
3. 每小时对城镇造成伤害
4. 城镇生命值归零 → 占领
```

**城镇防御：**
- 城镇基础生命值：1000
- 守军加成：+100 × 守军数量
- 防御建筑加成：+500（如果有）

**攻城效率：**
- 攻城器械：100%
- 其他兵种：30%

---

## 🎮 玩家操作流程

### 创建军队

```
/sc army create <兵种> <数量>

示例：
/sc army create infantry 100    # 创建100名步兵
/sc army create cavalry 50      # 创建50名骑兵

费用：
- 步兵：100金币/人
- 骑兵：200金币/人
- 弓箭手：150金币/人
- 攻城器械：300金币/个
```

**限制：**
- 需要在己方领地
- 消耗国库金币
- 每个国家最多10支军队

---

### 移动军队

```
/sc army move <军队ID> <目标坐标>

示例：
/sc army move 1 100 200    # 移动军队1到 (100, 200)

或使用 GUI：
/sc army gui               # 打开军队管理界面
→ 点击军队
→ 点击"移动"
→ 在地图上选择目标
```

**移动规则：**
- 行军速度：基础1格/分钟 × 机动性
- 只能在陆地移动（不能穿水）
- 移动中消耗补给
- 移动时可以被攻击

---

### 攻击敌军

```
/sc army attack <军队ID> <敌军ID>

示例：
/sc army attack 1 2    # 军队1攻击军队2

或在 GUI 中：
→ 点击己方军队
→ 点击"攻击"
→ 选择目标敌军
```

**战斗规则：**
- 双方同时造成伤害
- 计算兵种相克
- 士气影响战斗力
- 失败方可能溃散

---

### 攻城

```
/sc army siege <军队ID> <城镇名>

示例：
/sc army siege 1 EnemyCapital

条件：
- 军队在城镇相邻区块
- 与该国家处于战争状态
- 补给充足
```

**攻城进度：**
- 每小时自动造成伤害
- 城镇生命值显示在 GUI
- 占领后获得城镇控制权

---

### 补给

```
/sc army supply <军队ID>

自动从国库扣除资源补给军队
```

---

## 💻 技术实现

### 1. 实体表示

**方案 A：盔甲架 + 方块（推荐）**
```java
// 驻扎时：染色玻璃方块
// 行军时：盔甲架实体（显示兵种）
// 优点：性能好，易于渲染
// 缺点：视觉效果一般
```

**方案 B：实体显示框架**
```java
// 使用 ArmorStand 显示物品
// 优点：视觉效果好
// 缺点：实体多时性能差
```

**选择：方案 A**

---

### 2. 持久化

```java
// SQL 表结构
CREATE TABLE starcore_armies (
    id VARCHAR(36) PRIMARY KEY,
    nation_id VARCHAR(36) NOT NULL,
    type VARCHAR(32) NOT NULL,
    soldiers INT NOT NULL,
    health DOUBLE NOT NULL,
    morale DOUBLE NOT NULL,
    location TEXT NOT NULL,
    state VARCHAR(32) NOT NULL,
    supply INT NOT NULL,
    created_at BIGINT NOT NULL
);

// 索引
CREATE INDEX idx_armies_nation ON starcore_armies(nation_id);
CREATE INDEX idx_armies_state ON starcore_armies(state);
```

---

### 3. 异步任务

**定时任务（每分钟）：**
```java
// 1. 行军中的军队移动
// 2. 攻城中的军队造成伤害
// 3. 补给消耗
// 4. 士气变化
// 5. 自动补给（己方领地）
```

**性能优化：**
- 批量处理军队状态
- 只处理活跃军队
- 异步数据库操作

---

### 4. GUI 设计

**主界面（54格）：**
```
[军队1] [军队2] [军队3] ...
[创建] [补给全部] [召回全部]
[< 上页] [关闭] [下页 >]
```

**军队详情（27格）：**
```
[军队信息]
[移动] [攻击] [攻城]
[补给] [解散] [返回]
```

---

## 📊 数据流

```
玩家命令
   ↓
ArmyCommand
   ↓
ArmyService
   ↓
┌─────────┬─────────┬─────────┐
│ 战斗计算 │ 移动逻辑 │ 补给系统 │
└─────────┴─────────┴─────────┘
   ↓
ArmyStateStorage（持久化）
   ↓
ArmyEntityManager（实体显示）
```

---

## 🧪 测试计划

### 单元测试
- `ArmyService` - 创建、移动、攻击
- `BattleCalculator` - 战斗计算
- `ArmyStateStorage` - 持久化

### 集成测试
- 创建军队 → 移动 → 攻击
- 攻城战完整流程
- 补给系统

### 性能测试
- 100支军队同时行军
- 10场战斗同时进行
- TPS 影响测试

---

## 📋 实施计划

### Phase 1：核心模型（2天）
- ✅ `ArmyUnit` - 军队单位类
- ✅ `ArmyType` - 兵种枚举
- ✅ `ArmyState` - 状态枚举
- ✅ `ArmyService` - 核心服务

### Phase 2：战斗系统（2天）
- ✅ `BattleCalculator` - 战斗计算
- ✅ 兵种相克
- ✅ 地形修正
- ✅ 战斗结果

### Phase 3：移动和补给（1天）
- ✅ 移动逻辑
- ✅ 补给系统
- ✅ 定时任务

### Phase 4：攻城战（1天）
- ✅ 攻城逻辑
- ✅ 城镇防御
- ✅ 占领机制

### Phase 5：GUI（2天）
- ✅ 军队管理界面
- ✅ 军队详情
- ✅ 操作按钮

### Phase 6：持久化（1天）
- ✅ 数据库存储
- ✅ 异步保存
- ✅ 加载逻辑

### Phase 7：测试和优化（1天）
- ✅ 单元测试
- ✅ 性能测试
- ✅ Bug 修复

**总计：10天（约2周）**

---

## ⚠️ 风险和挑战

### 技术挑战
1. **性能问题** - 大量军队时的 TPS 影响
   - 解决：批量处理，异步任务
   
2. **实体渲染** - 实体过多导致客户端卡顿
   - 解决：使用方块代替实体（驻扎时）
   
3. **数据同步** - 多个军队同时移动
   - 解决：使用并发安全的数据结构

### 设计挑战
1. **复杂度平衡** - 既要有深度又不能太复杂
   - 解决：简化操作，自动化部分流程
   
2. **平衡性** - 兵种、成本、战斗力平衡
   - 解决：可配置参数，后续调整

---

## 📝 配置示例

```yaml
army:
  enabled: true
  
  limits:
    max-armies-per-nation: 10
    max-soldiers-per-army: 1000
  
  units:
    infantry:
      cost: 100
      attack: 10
      defense: 15
      health: 100
      mobility: 1.0
    cavalry:
      cost: 200
      attack: 15
      defense: 10
      health: 80
      mobility: 2.0
    archer:
      cost: 150
      attack: 12
      defense: 8
      health: 70
      mobility: 1.5
    siege:
      cost: 300
      attack: 5
      defense: 20
      health: 150
      mobility: 0.5
      siege-bonus: 3.0
  
  supply:
    stationary-rate: 1    # 每小时
    marching-rate: 3
    combat-rate: 5
    auto-supply-in-territory: true
  
  combat:
    counter-bonus: 1.3    # 克制加成 30%
    morale-impact: 0.5    # 士气影响 50%
    terrain-bonus: 0.2    # 地形加成 20%
```

---

## 🎯 MVP 范围

**最小可行产品（1周）：**
1. ✅ 创建3种基础兵种（步兵/骑兵/弓箭手）
2. ✅ 军队移动（手动指定坐标）
3. ✅ 基础战斗（自动计算）
4. ✅ 简单 GUI（列表+详情）
5. ✅ 持久化

**暂缓功能：**
- 攻城战（v0.4.0）
- 地形修正（v0.4.0）
- 高级战术（v0.5.0）

---

**文档版本：** v1.0  
**作者：** Claude Code  
**状态：** ✅ 设计完成，准备实施
