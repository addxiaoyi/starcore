# StarCore 国策系统扩展方案

> 参考真实世界政治体系，设计更完善的国策系统
> 版本: 2.0 | 日期: 2026-06-25

---

## 📊 当前系统分析

### 现有结构
- **PolicyCategory**: ECONOMY, MILITARY, INTERNAL, DIPLOMACY (4类)
- **PolicyEffectScope**: GLOBAL, TERRITORY, NATION, PLAYER (4级)
- **效果机制**: modifier (加成百分比)

### 问题
1. 政策类型过于简单
2. 效果缺乏真实世界关联
3. 缺少政策间的相互作用
4. 缺少政策成本与持续时间机制
5. 缺少政策民意支持度影响

---

## 🏛️ 新版国策体系设计

### 一、政策分类扩展 (PolicyCategory v2)

```java
public enum PolicyCategory {
    // 内政 (Internal Affairs)
    ADMINISTRATION,      // 行政
    EDUCATION,          // 教育
    HEALTHCARE,         // 医疗
    HOUSING,            // 住房
    SOCIAL_WELFARE,     // 社会福利
    LABOR,              // 劳工/就业
    
    // 经济 (Economy)
    FISCAL,             // 财政
    MONETARY,           // 货币
    TRADE,              // 贸易
    INDUSTRY,           // 产业
    TAXATION,           // 税收
    
    // 军事/国防 (Military)
    DEFENSE,            // 国防
    INTELLIGENCE,       // 情报
    RECRUITMENT,        // 征兵
    ARMS,               // 军备
    
    // 外交 (Diplomacy)
    FOREIGN_POLICY,     // 外交
    IMMIGRATION,        // 移民
    CULTURAL_EXCHANGE,  // 文化交流
    
    // 资源/环境 (Resources/Environment)
    RESOURCE_MANAGEMENT, // 资源管理
    ENVIRONMENTAL,       // 环保
    
    // 宗教/文化 (Religion/Culture)
    RELIGION,           // 宗教
    CULTURE,            // 文化
    PROPAGANDA          // 宣传
}
```

### 二、政策效果类型扩展 (PolicyEffect v2)

```java
public enum PolicyEffectType {
    // 经济效果
    TAX_RATE_MODIFIER,              // 税率调整
    PRODUCTION_BONUS,               // 生产加成
    TRADE_INCOME_MODIFIER,         // 贸易收入调整
    INTEREST_RATE,                  // 利率
    INFLATION_CONTROL,              // 通胀控制
    
    // 人口效果
    POPULATION_GROWTH,              // 人口增长
    HAPPINESS_MODIFIER,             // 幸福度
    PRODUCTIVITY,                   // 生产效率
    
    // 军事效果
    DEFENSE_BONUS,                  // 防御加成
    ATTACK_BONUS,                   // 攻击加成
    RECRUIT_SPEED,                  // 招募速度
    UNIT_MAINTENANCE_COST,          // 维护费用
    CONSCRIPTION_RATE,              // 征兵率
    
    // 科技效果
    RESEARCH_SPEED,                  // 研究速度
    TECH_COST_REDUCTION,            // 科技成本
    
    // 外交效果
    DIPLOMATIC_REPUTATION,          // 外交声誉
    TRADE_AGREEMENT_BONUS,          // 贸易协定加成
    ALLIANCE_STRENGTH,              // 联盟强度
    ESPIONAGE_RESISTANCE,           // 反间谍
    
    // 稳定性
    STABILITY,                      // 稳定性
    REVOLUTION_RISK,                // 革命风险
    CORRUPTION,                     // 腐败程度
    
    // 特殊效果
    CULTURE_SPREAD,                 // 文化传播
    RELIGIOUS_INFLUENCE,            // 宗教影响力
    PROPAGANDA_EFFECTIVENESS,       // 宣传效果
    APPROVAL_RATING,                // 支持率
    DISSIDENTS_SUPPRESSION          // 异见者镇压
}
```

### 三、政策互斥/协同矩阵

```java
public class PolicyInteractionMatrix {
    // 互斥政策（不能同时激活）
    public static final Map<String, Set<String>> MUTUALLY_EXCLUSIVE = Map.of(
        "isolationism", Set.of("globalism", "expansionism"),
        "free_trade", Set.of("protectionism", "autarky"),
        "multilateralism", Set.of("unilateralism"),
        "welfare_state", Set.of("laissez_faire"),
        "mandatory_service", Set.of("professional_army"),
        "state_religion", Set.of("secularism", "freedom_of_religion"),
        "authoritarianism", Set.of("democracy", "libertarianism"),
        "open_immigration", Set.of("closed_borders"),
        "environmental_regulation", Set.of("industrial_boom")
    );
    
    // 协同政策（同时激活有额外加成）
    public static final Map<String, Set<String>> SYNERGIES = Map.of(
        "universal_healthcare", Set.of("education_reform", "social_housing"),
        "nationalism", Set.of("strong_military", "cultural_heritage"),
        "free_trade", Set.of("diplomatic_openness", "immigration_open"),
        "education_reform", Set.of("tech_investment", "immigration_open"),
        "infrastructure_investment", Set.of("industrial_policy", "trade_infrastructure")
    );
}
```

---

## 📜 真实世界政策参考实现

### 1. 财政政策 (Fiscal Policy)

```java
public enum FiscalPolicy {
    // 税收政策
    PROGRESSIVE_TAX("累进税制", -0.1, 0.05),           // 高收入者多缴税
    FLAT_TAX("单一税制", 0.0, -0.02),                   // 统一税率
    REGRESSIVE_TAX("逆进税制", 0.05, 0.03),             // 低收入者负担重
    WEALTH_TAX("财富税", -0.15, 0.08),                  // 对资产征税
    CORPORATE_TAX_HIKE("提高企业税", -0.1, 0.06),       // 企业多缴税
    CORPORATE_TAX_CUT("降低企业税", 0.15, -0.04),       // 吸引投资
    
    // 支出政策
    INFRASTRUCTURE_SPENDING("基础设施投资", 0.2, 0.1),  // 建设道路/桥梁
    EDUCATION_SPENDING("教育投入", 0.15, 0.08),          // 学校/大学
    DEFENSE_SPENDING("国防开支", 0.1, -0.05),            // 军费
    WELFARE_EXPENDITURE("福利支出", 0.0, 0.12),         // 社会福利
    
    // 预算政策
    AUSTERITY("紧缩政策", -0.2, 0.05),                  // 削减开支
    STIMULUS("刺激政策", 0.3, -0.08),                    // 增加开支
    BALANCED_BUDGET("平衡预算", 0.0, 0.0),               // 收支平衡
    DEFICIT_SPENDING("赤字支出", 0.25, -0.1),           // 借钱花
    BUDGET_SURPLUS("预算盈余", -0.1, 0.15);             // 存钱
    
    private final String name;
    private final double economicImpact;  // 对经济的直接影响
    private final double approvalImpact;  // 对民意的影响
}
```

### 2. 货币政策 (Monetary Policy)

```java
public enum MonetaryPolicy {
    // 利率政策
    LOW_INTEREST_RATES("低利率政策", 0.15, -0.05),      // 刺激借贷
    HIGH_INTEREST_RATES("高利率政策", -0.1, 0.08),       // 抑制通胀
    TIGHT_MONEY("紧缩银根", -0.15, 0.05),               // 减少货币供应
    
    // 货币发行
    QUANTITATIVE_EASING("量化宽松", 0.2, -0.1),         // 印钱买债
    CURRENCY_DEVALUATION("货币贬值", 0.1, -0.08),       // 出口导向
    CURRENCY_REVALUATION("货币升值", -0.05, 0.05),       // 进口导向
    
    // 汇率政策
    FIXED_EXCHANGE_RATE("固定汇率", 0.0, 0.0),           // 钉住某国
    FLOATING_EXCHANGE_RATE("浮动汇率", 0.0, 0.0),       // 市场决定
    CURRENCY_MANIPULATION("汇率操控", 0.15, -0.15);     // 压低汇率
    
    private final String name;
    private final double growthImpact;
    private final double stabilityImpact;
}
```

### 3. 贸易政策 (Trade Policy)

```java
public enum TradePolicy {
    // 关税政策
    FREE_TRADE("自由贸易", 0.2, 0.05),
    PROTECTIONISM("保护主义", -0.1, 0.1),
    TARIFF_ON_LUXURIES("奢侈品关税", 0.05, 0.05),
    TARIFF_ON_ESSENTIALS("必需品关税", -0.05, -0.08),
    
    // 贸易协定
    FREE_TRADE_AGREEMENT("自由贸易协定", 0.15, 0.1),
    CUSTOMS_UNION("关税同盟", 0.1, 0.08),
    COMMON_MARKET("共同市场", 0.25, 0.12),
    TRADE_EMBARGO("贸易禁运", -0.2, -0.15),
    
    // 进出口限制
    EXPORT_SUBSIDIES("出口补贴", 0.1, -0.05),
    IMPORT_QUOTAS("进口配额", -0.05, 0.05),
    EXPORT_CONTROLS("出口管制", -0.1, 0.0),
    DUMPING_PROTECTION("反倾销税", 0.0, 0.08)
}
```

### 4. 军事/国防政策 (Defense Policy)

```java
public enum DefensePolicy {
    // 征兵制度
    MANDATORY_SERVICE("义务役", 0.15, -0.05),           // 全民服兵役
    PROFESSIONAL_ARMY("职业军队", 0.2, 0.0),           // 志愿兵
    CONSCRIPTION_EXEMPTION("免役政策", 0.0, 0.1),      // 可以用钱免役
    UNIVERSAL_SERVICE("全民服役", 0.1, -0.08),          // 包括非军事服务
    
    // 军事预算
    MILITARY_BUILDUP("军事扩张", 0.1, -0.15),          // 增加军费
    MILITARY_CUTBACK("裁军", -0.15, 0.1),               // 减少军费
    STRATEGIC_RESERVES("战略储备", 0.05, 0.05),         // 武器储备
    
    // 军事策略
    DEFENSIVE_STRATEGY("防御战略", 0.1, 0.1),           // 防守为主
    OFFENSIVE_STRATEGY("进攻战略", 0.2, -0.2),         // 主动出击
    NON_ALIGNMENT("不结盟", 0.0, 0.1),                  // 不参与战争
    MILITARY_ALLIANCE("军事同盟", 0.25, -0.05),        // 结盟
    
    // 核武器政策
    NUCLEAR_PROLIFERATION("核扩散", -0.1, -0.2),
    NUCLEAR_DISARMAMENT("核裁军", 0.0, 0.15),
    NUCLEAR_DETERRENCE("核威慑", 0.1, 0.05)
}
```

### 5. 外交政策 (Foreign Policy)

```java
public enum ForeignPolicy {
    // 国际立场
    ISOLATIONISM("孤立主义", 0.0, 0.1),                // 不干涉他国
    GLOBALISM("全球主义", 0.15, -0.05),                  // 积极参与国际
    EXPANSIONISM("扩张主义", 0.2, -0.2),                 // 领土扩张
    NEO_COLONIALISM("新殖民主义", 0.15, -0.25),         // 经济控制
    
    // 国际合作
    MULTILATERALISM("多边主义", 0.1, 0.05),             // 通过国际组织
    UNILATERALISM("单边主义", 0.05, -0.1),              // 单独行动
    BILATERALISM("双边主义", 0.05, 0.05),               // 与个别国家合作
    
    // 制裁与援助
    ECONOMIC_SANCTIONS("经济制裁", -0.1, 0.0),
    FOREIGN_AID("对外援助", -0.1, 0.15),
    DEBT_Trap_Diplomacy("债务外交", 0.1, -0.1),
    
    // 软实力
    CULTURAL_DIPLOMACY("文化外交", 0.1, 0.1),
    PUBLIC_DIPLOMACY("公共外交", 0.05, 0.1),
    PROPAGANDA("对外宣传", 0.05, -0.1)
}
```

### 6. 内政政策 (Internal Policy)

```java
public enum InternalPolicy {
    // 行政
    CENTRALIZATION("中央集权", 0.05, -0.1),
    DECENTRALIZATION("权力下放", 0.0, 0.1),
    BUREAUCRACY_REFORM("官僚改革", 0.1, 0.05),
    CORRUPTION_CRACKDOWN("反腐行动", 0.15, 0.15),
    
    // 社会政策
    UNIVERSAL_HEALTHCARE("全民医保", 0.1, 0.15),
    PRIVATIZED_HEALTHCARE("医疗私有化", 0.1, -0.1),
    UNIVERSAL_EDUCATION("全民教育", 0.15, 0.15),
    EDUCATION_PRIVATIZATION("教育私有化", 0.1, -0.1),
    
    // 社会福利
    WELFARE_STATE("福利国家", 0.0, 0.2),
    WORKFARE("工作福利", 0.1, 0.05),
    LAISSEZ_FAIRE("自由放任", 0.15, -0.15),
    
    // 住房
    PUBLIC_HOUSING("公共住房", 0.05, 0.15),
    HOUSING_SUBSIDIES("住房补贴", 0.05, 0.1),
    LAND_VALUE_TAX("土地增值税", 0.1, 0.0),
    
    // 劳工
    WORKER_PROTECTION("劳工保护", -0.05, 0.15),
    FLEXIBLE_LABOR("弹性劳动", 0.15, -0.05),
    MINIMUM_WAGE("最低工资", -0.05, 0.1),
    UNION_RIGHTS("工会权利", 0.0, 0.1)
}
```

### 7. 宗教/文化政策

```java
public enum ReligiousCulturalPolicy {
    // 宗教政策
    STATE_RELIGION("国教", 0.05, 0.05),
    SECULARISM("政教分离", 0.1, 0.05),
    FREEDOM_OF_RELIGION("宗教自由", 0.1, 0.1),
    RELIGIOUS_SUPPRESSION("宗教压制", -0.1, -0.2),
    
    // 文化政策
    CULTURAL_HERITAGE_PROTECTION("文物保护", 0.05, 0.15),
    ARTS_FUNDING("艺术资助", 0.1, 0.1),
    NATIONALISM_PROMOTION("民族主义宣传", 0.0, -0.05),
    CULTURAL_LIBERALIZATION("文化自由化", 0.1, 0.05),
    
    // 媒体政策
    FREE_PRESS("新闻自由", 0.15, 0.1),
    STATE_MEDIA("国营媒体", 0.0, -0.1),
    MEDIA_REGULATION("媒体监管", 0.0, -0.05),
    INTERNET_CENSORSHIP("网络审查", -0.1, -0.15),
    
    // 宣传
    PROPAGANDA_MACHINE("宣传机器", 0.0, -0.2),
    CENSORSHIP("审查制度", -0.05, -0.2),
    CENSORSHIP_LIGHT("轻度审查", 0.0, -0.05)
}
```

### 8. 资源/环境政策

```java
public enum ResourceEnvPolicy {
    // 资源政策
    RESOURCE_NATIONALIZATION("资源国有化", -0.1, 0.05),
    RESOURCE_PRIVATIZATION("资源私有化", 0.15, -0.05),
    STRATEGIC_RESERVES("战略储备", 0.05, 0.1),
    RESOURCE_EXTRACTION_TAX("资源开采税", 0.1, 0.05),
    
    // 环境政策
    ENVIRONMENTAL_REGULATION("环保法规", -0.1, 0.1),
    GREEN_ENERGY_INVESTMENT("绿色能源投资", 0.15, 0.05),
    POLLUTION_TAX("污染税", 0.05, 0.05),
    ENVIRONMENTAL_DEREGULATION("环境松绑", 0.15, -0.1),
    
    // 农业政策
    AGRICULTURAL_SUBSIDIES("农业补贴", 0.05, 0.1),
    LAND_REFORM("土地改革", 0.0, -0.05),
    COLLECTIVIZATION("集体化", -0.1, -0.15),
    AGRIBUSINESS_SUPPORT("农业企业支持", 0.15, -0.05)
}
```

---

## 🔧 实现计划

### 阶段一：核心框架扩展
1. [ ] 扩展 `PolicyCategory` 枚举
2. [ ] 扩展 `PolicyEffectType` 枚举
3. [ ] 添加 `PolicyInteractionMatrix` 类
4. [ ] 修改 `PolicyDefinition` 支持新效果类型

### 阶段二：政策效果系统
1. [ ] 创建 `PolicyEffectCalculator` 计算器
2. [ ] 实现政策协同/互斥逻辑
3. [ ] 添加民意支持度影响
4. [ ] 实现政策成本计算

### 阶段三：真实世界模拟
1. [ ] 实现财政政策效果
2. [ ] 实现货币政策效果
3. [ ] 实现军事政策效果
4. [ ] 实现外交政策效果

### 阶段四：UI/命令界面
1. [ ] 更新政策 GUI
2. [ ] 添加政策比较功能
3. [ ] 实现政策建议系统

---

## 📊 效果计算公式

```java
// 税收收入 = 基础税率 * (1 + 税率政策加成) * (1 + 经济繁荣度)
// 防御强度 = 基础防御 * (1 + 军事政策加成) * (1 + 地形加成) * (1 - 腐败惩罚)
// 稳定度 = 100 - 革命风险 + 福利加成 - 腐败惩罚 + 经济增长率
// 科技速度 = 基础速度 * (1 + 教育投入) * (1 + 研究预算) * (1 - 审查惩罚)
```

---

## 🎯 预期效果

| 政策类型 | 影响维度 | 示例政策 |
|----------|----------|----------|
| 财政政策 | 经济、稳定性 | 累进税制、紧缩政策 |
| 货币政策 | 通胀、增长 | 低利率、量化宽松 |
| 贸易政策 | 收入、就业 | 自由贸易、保护主义 |
| 军事政策 | 防御、外交 | 义务役、军事同盟 |
| 外交政策 | 声誉、关系 | 孤立主义、文化外交 |
| 内政政策 | 幸福度、稳定 | 全民医保、反腐行动 |

---

## 📝 配置示例 (policies.yml)

```yaml
policies:
  # 经济政策
  progressive_tax:
    category: FISCAL
    display_name: "累进税制"
    description: "高收入者多缴税，减少贫富差距"
    effects:
      - type: TAX_RATE_MODIFIER
        value: 0.1
        target: high_income
      - type: APPROVAL_RATING
        value: 0.1
      - type: PRODUCTION_BONUS
        value: -0.05
    cost: 1000
    duration: -1  # 永久
    prerequisites: []
    conflicts: [regressive_tax, flat_tax]
    
  # 军事政策
  mandatory_military_service:
    category: RECRUITMENT
    display_name: "义务役制度"
    description: "所有公民需服兵役"
    effects:
      - type: RECRUIT_SPEED
        value: 0.5
      - type: APPROVAL_RATING
        value: -0.1
      - type: PRODUCTIVITY
        value: -0.1
      - type: DEFENSE_BONUS
        value: 0.2
    cost: 500
    duration: -1
    prerequisites: []
    conflicts: [professional_army]
```

---

*文档版本: 2.0 | 最后更新: 2026-06-25*
