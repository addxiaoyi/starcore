# StarCore 宠物/坐骑系统

## 功能概述

StarCore 宠物/坐骑系统提供完整的宠物养成体验，包括宠物召唤、属性成长、升级进化、骑乘系统和宠物商店。

## 主要功能

### 1. 宠物召唤
- `/pet list` - 打开宠物列表界面
- `/pet summon [名称]` - 召唤指定宠物跟随
- `/pet despawn [名称]` - 收起宠物
- 宠物会跟随主人移动，远距离自动传送
- 支持同时召唤多个宠物

### 2. 宠物属性
每只宠物拥有以下属性：
- **生命值 (HP)** - 宠物的生命上限
- **攻击力 (ATK)** - 宠物造成的伤害
- **防御力 (DEF)** - 宠物的伤害减免
- **移动速度 (SPD)** - 宠物的移动速度
- **等级 (Lv)** - 宠物当前等级（最高150级）
- **经验值 (EXP)** - 用于升级的经验

### 3. 宠物升级
- 升级方式：
  - 喂养宠物（`/pet feed`）
  - 击杀怪物
- 升级公式：每级需要 100 × level² 经验
- 达到特定等级会自动进化稀有度：
  - Lv.25 → 优秀
  - Lv.50 → 稀有
  - Lv.75 → 史诗
  - Lv.100 → 传说

### 4. 骑乘系统
支持的骑乘宠物：
- 马 (Horse)
- 驴 (Donkey)
- 骡子 (Mule)
- 羊驼 (Llama)
- 猪 (Pig)
- 炽足兽 (Strider)
- 骷髅马 (Skeleton Horse)
- 僵尸马 (Zombie Horse)
- 幻翼 (Phantom) - 可飞行

### 5. 宠物商店
- `/pet shop` - 打开宠物商店
- 支持购买各类型宠物
- 不同稀有度价格不同：
  - 普通 (Common): 0 - 100
  - 优秀 (Uncommon): 100 - 500
  - 稀有 (Rare): 500 - 2,500
  - 史诗 (Epic): 2,500 - 10,000
  - 传说 (Legendary): 10,000 - 50,000
  - 神话 (Mythic): 50,000+

## 稀有度系统

| 稀有度 | 颜色 | 属性倍率 | 最高等级 |
|--------|------|----------|----------|
| 普通 (Common) | f | 1.0x | 50 |
| 优秀 (Uncommon) | a | 1.2x | 75 |
| 稀有 (Rare) | 6 | 1.5x | 100 |
| 史诗 (Epic) | 5 | 2.0x | 125 |
| 传说 (Legendary) | 6 | 3.0x | 150 |
| 神话 (Mythic) | c | 5.0x | 150 |

## 宠物类型

### 伴侣型 (Companion)
- 狼、猫、狐狸、豹猫、鹦鹉、兔子
- 特点：可爱、忠诚、攻击力中等

### 骑乘型 (Mount)
- 马、驴、骡子、羊驼、猪、炽足兽
- 特点：可骑乘、速度快

### 飞行型 (Flying)
- 幻翼
- 特点：可飞行、高机动性

### 水生型 (Aquatic)
- 海豚、海龟、美西螈
- 特点：水下生存能力强

### 特殊型 (Special)
- 雪傀儡、铁傀儡、烈焰人、凋零骷髅
- 特点：属性高、功能特殊

## 命令列表

| 命令 | 功能 | 权限 |
|------|------|------|
| `/pet` | 显示帮助 | starcore.pet |
| `/pet list` | 宠物列表 | starcore.pet.list |
| `/pet shop` | 宠物商店 | starcore.pet.shop |
| `/pet summon [名称]` | 召唤宠物 | starcore.pet.summon |
| `/pet despawn [名称]` | 收起宠物 | - |
| `/pet feed` | 喂养宠物 | - |
| `/pet rename <名称>` | 重命名宠物 | - |
| `/pet info [名称]` | 宠物详情 | - |
| `/pet upgrade` | 升级稀有度 | starcore.pet.upgrade |
| `/pet stats` | 查看统计 | - |
| `/pet give <玩家> <类型> [稀有度]` | 给予宠物 | starcore.pet.give |
| `/pet delete <名称>` | 删除宠物 | - |

## 配置文件

- `pets.yml` - 宠物系统配置
- `pet_data.yml` - 宠物数据存储（自动生成）

## 数据库表

系统会自动创建以下数据库表（如果使用MySQL）：
- `starcore_pets` - 宠物数据表
- `starcore_player_pet_settings` - 玩家宠物设置
- `starcore_pet_attribute_bonuses` - 属性加成表
- `starcore_pet_transactions` - 交易记录表
- `starcore_pet_exp_log` - 经验记录表

## API 接口

其他插件可通过以下方式获取宠物服务：

```java
import dev.starcore.starcore.pet.PetService;

// 获取宠物服务
PetService petService = context.serviceRegistry()
    .find(PetService.class)
    .orElse(null);

// 使用服务
PlayerPets playerPets = petService.getPlayerPets(player.getUniqueId());
Map<String, Double> bonuses = petService.getPetBonuses(player.getUniqueId());
```
