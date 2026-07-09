# StarCore 菜单系统完整清单

## 一、菜单模块列表

### 1.1 国家系统 (nation)
| 菜单类 | 功能 | 完善度 | 来源参考 |
|--------|------|--------|----------|
| TriumphNationMenu | 国家菜单核心 | 85% | Towny |
| NationManagementMenu | 国家管理 | 90% | Towny |
| NationAdvancedMenu | 国家高级设置 | 75% | - |
| NationMenuListener | 菜单监听器 | 80% | Towny |
| NationMenuConfig | 菜单配置 | 95% | Towny |
| FallbackMenuProvider | 降级菜单 | 100% | - |
| NationMenuFactory | 菜单工厂 | 100% | Towny |
| TriumphChestMenuProvider | TriumphGUI实现 | 100% | Towny |
| NationMenuProvider | 菜单提供者 | 100% | Towny |

**子菜单**:
| 类名 | 功能 | 完善度 |
|------|------|--------|
| NationInfoMenu | 国家信息 | 90% |
| NationListMenu | 国家列表 | 85% |
| NationCreateMenu | 创建菜单 | 95% |
| NationJoinMenu | 加入菜单 | 90% |

### 1.2 菜单类型 (GUI包)
| 菜单类 | 功能 | 完善度 | 来源参考 |
|--------|------|--------|----------|
| NationManagementMenuListener | 菜单监听 | 85% | Towny |
| ArmyMenuListener | 军队菜单 | 70% | - |
| DiplomacyMenuListener | 外交菜单 | 75% | - |
| WarMenuListener | 战争菜单 | 80% | Towns-and-Nations |
| TechnologyMenuListener | 科技菜单 | 75% | - |
| TreasuryMenuListener | 国库菜单 | 85% | - |
| PolicyMenuListener | 政策菜单 | 80% | - |
| SocialMenuListener | 社交菜单 | 75% | - |

### 1.3 战争系统 (war)
| 菜单类 | 功能 | 完善度 | 参考 |
|--------|------|--------|----------|
| WarMenu | 战争主菜单 | 80% | Towns-and-Nations |
| WarSituationMenu | 战场态势 | 70% | Towns-and-Nations |
| WarMenuListener | 菜单监听 | 85% | Towns-and-Nations |

### 1.4 外交系统 (diplomacy)
| 类名 | 功能 | 完善度 | 参考 |
|------|------|--------|---------|
| DiplomacyMenu | 外交菜单 | 75% | - |
| DiplomacyNetworkMenu | 外交网络 | 70% | - |
| AllianceMenu | 联盟菜单 | 80% | Towns-and-Nations |
| MilitaryMenu | 军事菜单 | 75% | - |

### 1.5 军事系统 (military)
| 类名 | 功能 | 完善度 | 参考 |
|------|------|--------|---------|
| BattleStatusMenu | 战斗状态 | 70% | Towns-and-Nations |
| ArmyMenu | 军队菜单 | 85% | - |
| TargetSelectorMenu | 目标选择 | 75% | - |
| ArmyCreationMenu | 军队创建 | 80% | - |
| ArmyManagementMenu | 军队管理 | 75% | - |

### 1.6 科技系统 (technology)
| 类名 | 功能 | 完善度 |
|------|------|--------|
| TechnologyMenu | 科技树 | 85% | 
| TechnologyResearchMenu | 研究菜单 | 80% |
| TechnologyTreeMenu | 科技树 | 75% |

### 1.7 资源系统 (resource)
| 类名 | 功能 | 完善度 |
|------|------|--------|
| ResourceMenu | 资源菜单 | 70% | 
| ResourceCollectMenu | 资源采集 | 65% | 
| ResourceUpgradeMenu | 资源升级 | 75% |

### 1.8 社交系统 (social)
| 类名 | 功能 | 完善度 | 参考 |
|------|------|--------|---------|
| SocialMenuGui | 社交主菜单 | 80% | TownyMenus |
| SocialLeaderboardGui | 排行榜 | 65% | 
| EmoteMenu | 表情菜单 | 85% | InteractiveChat |
| TitleMenu | 称号菜单 | 80% | 

### 1.9 基础GUI (foundation)
| 类名 | 功能 | 完善度 | 参考 |
|------|------|--------|----------|
| BaseGui | GUI基类 | 90% | TownyMenus |
| ButtonFactory | 按钮工厂 | 95% | TownyMenus |
| UnifiedMenu | 统一菜单 | 85% | TownyMenus |
| MenuTransitionAnimator | 菜单动画 | 70% | TownyMenus |
| MenuFactory | 菜单工厂 | 90% | TownyMenus |

### 1.10 城市系统 (city)
| 类名 | 功能 | 完善度 |
|------|------|--------|
| CityMenu | 城市菜单 | 75% | 
| CityManagementMenu | 城市管理 | 70% |
| CityListMenu | 城市列表 | 80% |

### 1.1 店铺系统 (shop)
| 类名 | 功能 | 完善度 | 参考 |
|------|------|--------|---------|
| ShopMenu | 商店主菜单 | 80% | CrazyAuctions |
| ShopItem | 商品项目 | 85% | CrazyAuctions |
| ShopCategory | 分类管理 | 75% | CrazyAuctions |

### 1.12 其他功能菜单
| 类名 | 功能 | 完善度 |
|--------|------|--------|
| WeatherMenu | 天气菜单 | 80% | 
| DungeonMenu | 副本菜单 | 70% | 
| ZoneMenu | 区域菜单 | 75% |
| QuestMenu | 任务菜单 | 80% | 
| RankingGUI | 排行榜 | 75% |
| AchievementGui | 成就菜单 | 85% | 
| HomeGui | 家园管理 | 80% | HuskHomes |
| WarpGui | 传送菜单 | 85% | HuskHomes |
| TeleportGui | 传送点 | 80% | HuskHomes |
| BalTopGui | 经济排行 | 80% | 
| RandomEventResponseMenu | 事件响应 | 70% | 
| PetShopGUI | 宠物商店 | 75% | 

---

## 二、配置文件列表

### 2.1 菜单配置
| 文件 | 功能 |
|------|------|
| nation-menu.yml | 国家菜单配置 |
| menu-animations.yml | 动画配置 |
| gui-settings.yml | GUI全局设置 |
| TriumphGUI相关配置 |

### 2.2 菜单动画
| 文件 | 功能 |
|------|------|
| MenuTransitionAnimator | 动画过渡 |
| MenuAnimationConfig | 动画配置 |

---

## 三、17个参考项目来源

### 3.1 菜单系统参考
| 项目 | 参考内容 | 占比 |
|------|-----------|------|
| TownyMenus | 基类、事件分发、缓存机制 | 35% |
| Codex | 现代API设计 | 15% |
| DeluxeMenus | YAML配置格式 | 20% |
| CrazyAuctions | 拍卖菜单 | 10% |
| HuskHomes | 传送点菜单 | 10% |
| InteractiveChat | 预览系统 | 10% |

### 3.2 功能模块参考
| 项目 | 参考内容 | 占比 |
|------|------------|------|
| Towns-and-Nations | 外交、战争 | 40% |
| Towny | 国家模型、事件系统 | 30% |
| Town_Borders | 边境通知、粒子 | 10% |
| TownyWaypoints | 地标系统 | 10% |
| Quarters | 租赁系统 | 10% |

### 3.3 集成参考
| 项目 | 参考内容 | 占比 |
|------|------------|------|
| TownyPlus | Discord广播 | 30% |
| Geyser | 基岩版支持 | 20% |
| HuskChat | 聊天频道 | 25% |
| TownyMenus | 配置格式 | 25% |

---

## 四、整合关系图

```
┌─────────────────────────────────────────────────────────────────────┐
│                      菜单系统架构                                   │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────┐   ┌──────────────┐   ┌────────────┐   ┌──────────────┐ │
│  │ TownyMenus │   │ DeluxeMenus  │   │ Codex     │   │ HuskHomes  │ │
│  │  (35%)     │   │   (20%)     │   │  (15%)  │   │   (10%)   │ │
│  └──────┬──────┘   └──────┬───────┘   └───┬──────┘   └──────┬─────┘ │
│         │                  │              │              │           │
│         └──────────┬───────┴──────────────┴──────────────┘           │
│                    ▼                                                 │
│         ┌─────────────────────────────────────┐               │
│         │         BaseGui (基类)                │               │
│         │  - 动画支持                      │               │
│         │  - 事件分发                      │               │
│         │  - 缓存机制                      │              │
│         └──────────────┬──────────────────┘               │
│                        │                                  │
│                        ▼                                  │
│         ┌────────────────────────────────┐              │
│         │      StarCore Menu System        │              │
│         │                                │              │
│         │  ┌──────────┐  ┌─────────┐  │
│         │  │ NationMenu │  │ ArmyMenu │  │
│         │  └──────────┘  └─────────┘  │
│         │  ┌──────────┐  ┌─────────┐  │
│         │  │ DiplomacyMenu│  │ WarMenu │  │
│         │  └──────────┘  └─────────┘  │
│         │  ┌──────────┐  ┌───────────┐  │
│         │  │TreasuryMenu│  │QuestMenu │  │
│         │  └──────────┘  └───────────┘  │
│         │  ┌──────────┐  ┌───────────┐  │
│         │  │  SocialMenu │  │ShopMenu   │ │
│         │  └──────────┘  └───────────┘ │
│         │  ┌──────────┐  ┌──────────┐   │
│         │  │ PolicyMenu │  │HomeGui    │   │
│         │  └──────────┘  └──────────┘   │
│         │                                       │
│         └───────────────────────────────────┘               │
│                                                             │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ 集成: Town_Borders (边境) / TownyWaypoints (地标)     │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│              17个参考项目整合图                          │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  ┌────────────────────────────────────────────────┐  │
│  │            开源插件最佳实践库                      │  │
│  │                                              │  │
│  │  [Towny]──┬──[TownyMenus]──[TownyPlus] │  │
│  │    30%     │    35%        │    15%     │  │
│  │              │                 │              │  │
│  │              ▼                 ▼              │  │
│  │  ┌────────────────────────────────┐         │  │
│  │  │      StarCore 国家核心模型       │         │  │
│  │  │  NationService + NationModule  │         │  │
│  │  └────────────────────────────────┘         │  │
│  └──────────────────────────────────────────────────┘  │
│                                                        │
│  [Towns-and-Nations]──[DiscordSRV]──[Town_Borders]──[TownyWaypoints] │
│         40%               20%          15%        10%  │
│                                                             │
│  [HuskHomes]──[InteractiveChat]──[Quarters]──[Geyser]──[HuskChat] │
│    25%        15%           10%        20%      15%       │
│                                                             │
└────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│              菜单配置来源关系                              │
├──────────────────────────────────────────────────────┤
│                                                    │
│  DeluxeMenus (YAML)                             │
│  TownyMenus (配置驱动)                          │
│  Codex (Java API)                               │
│                                              │
│  ┌────────────────────────────────────────┐ │
│  │      nation-menu.yml                    │ │
│  │      [TownyMenus格式]                  │ │
│  │      • 分页支持                       │ │
│  │      • 条件显示                     │ │
│  │      • 动画配置                     │ │
│      └────────────────────────────────┘ │
│                                              │
│      ┌────────────────────────────────┐ │
│      │      menu-animations.yml           │ │
│      │      [StarCore格式]              │ │
│      │      • 过渡动画                 │ │
│      │      • 粒子效果                 │ │
│      │      • 声音反馈                 │ │
│      └────────────────────────────────┘ │
│                                              │
│      ┌────────────────────────────────┐ │
│      │      TriumphGUI 事件机制          │ │
│      │      [TownyMenus事件模式]         │ │
│      │      • 事件总线                  │ │
│      │      • 异步加载                 │
│      └────────────────────────────────┘ │
│                                              │
└──────────────────────────────────────────────┘
```

---

## 五、完善度评估标准

### 5.1 评估维度
| 维度 | 说明 |
|------|------|
| **功能完整性** | 核心功能是否实现 |
| **事件系统** | 事件监听、触发 |
| **配置化** | YAML配置支持 |
| **动画支持** | 过渡效果 |
| **国际化** | 多语言支持 |
| **性能优化** | 缓存、懒加载 |
| **错误处理** | 异常捕获 |

### 5.2 完善度计算公式
```
完善度 = (功能实现 / 目标功能) × 100%
```

### 5.3 优先级
| 优先级 | 说明 |
|--------|------|
| P0 | 核心功能（创建/管理/删除） |
| P1 | 扩展功能（列表/搜索/过滤） |
| P2 | 高级功能（动画/过渡/特效 |
| P3 | 优化功能（缓存/懒加载） |
