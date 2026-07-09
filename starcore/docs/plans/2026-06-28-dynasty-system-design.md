# 王朝世袭系统 - 详细设计方案

## 概述
玩家创建的王国可升级为"王朝"，创建世袭系统让王位可传承。创建需消耗大量资源，继承者可通过培养王子/公主获得，继承时触发特殊仪式事件。

---

## 1. 数据库设计

### 主表: dynasties
```sql
CREATE TABLE dynasties (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dynasty_id CHAR(36) NOT NULL UNIQUE,          -- 王朝UUID
    nation_id CHAR(36) NOT NULL,                  -- 关联国家
    dynasty_name VARCHAR(64) NOT NULL,             -- 王朝名
    founder_uuid CHAR(36) NOT NULL,               -- 创始人UUID
    founder_title VARCHAR(32) DEFAULT '开国君主',  -- 创始人称号
    current_sovereign_uuid CHAR(36),               -- 当前君主UUID
    heir_uuid CHAR(36),                           -- 继承人UUID
    prestige INT DEFAULT 0,                        -- 王朝威望
    legacy_points INT DEFAULT 0,                   -- 遗产点数
    creation_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_succession_time TIMESTAMP,
    stability INT DEFAULT 100,                     -- 王朝稳定性 0-100
    succession_type ENUM('PRIMOGENITURE', 'ELECTIVE', '_APPOINTED') DEFAULT 'PRIMOGENITURE',
    modifier_config JSON,                          -- 王朝加成配置
    UNIQUE KEY uk_nation (nation_id),
    INDEX idx_founder (founder_uuid),
    INDEX idx_sovereign (current_sovereign_uuid)
);
```

### 继承者表: dynasty_heirs
```sql
CREATE TABLE dynasty_heirs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    heir_uuid CHAR(36) NOT NULL,                  -- 继承人UUID
    dynasty_id CHAR(36) NOT NULL,
    relationship_type ENUM('SON', 'DAUGHTER', 'BROTHER', 'SISTER', 'COUSIN', 'ADOPTED') DEFAULT 'SON',
    birth_date TIMESTAMP,
    name VARCHAR(32),
    claim_strength INT DEFAULT 50,                -- 继承权强度 0-100
    education_level INT DEFAULT 0,                 -- 教育程度 0-100
    charisma INT DEFAULT 10,                       -- 个人魅力
    politics INT DEFAULT 10,                       -- 政治手腕
    martial INT DEFAULT 10,                        -- 军事才能
    is_crowned BOOLEAN DEFAULT FALSE,
    status ENUM('TRAINING', 'READY', 'COMPETING', 'DETHRONED', 'DECEASED') DEFAULT 'TRAINING',
    training_start_time TIMESTAMP,
    total_investment BIGINT DEFAULT 0,            -- 总投入金币
    FOREIGN KEY (dynasty_id) REFERENCES dynasties(dynasty_id) ON DELETE CASCADE,
    INDEX idx_dynasty (dynasty_id),
    INDEX idx_status (status)
);
```

### 继承权竞争表: succession_competitions
```sql
CREATE TABLE succession_competitions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dynasty_id CHAR(36) NOT NULL,
    competition_id CHAR(36) NOT NULL UNIQUE,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    voting_deadline TIMESTAMP,
    competitors JSON,                             -- [{heir_uuid, votes, support}]
    is_active BOOLEAN DEFAULT TRUE,
    winner_uuid CHAR(36),
    FOREIGN KEY (dynasty_id) REFERENCES dynasties(dynasty_id) ON DELETE CASCADE
);
```

### 继承事件表: succession_events
```sql
CREATE TABLE succession_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id CHAR(36) NOT NULL UNIQUE,
    dynasty_id CHAR(36) NOT NULL,
    event_type ENUM('SUCCESSION', 'DOWNFALL', 'USURPATION', 'CHALLENGE', 'REBELLION') NOT NULL,
    old_sovereign_uuid CHAR(36),
    new_sovereign_uuid CHAR(36),
    heir_involved_uuid CHAR(36),
    event_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    description TEXT,
    prestige_change INT DEFAULT 0,
    stability_change INT DEFAULT 0,
    FOREIGN KEY (dynasty_id) REFERENCES dynasties(dynasty_id) ON DELETE CASCADE,
    INDEX idx_dynasty_events (dynasty_id, event_time DESC)
);
```

---

## 2. 核心类设计

### 接口: DynastyService
```java
package dev.starcore.starcore.module.dynasty;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DynastyService {
    
    // ===== 创建与管理 =====
    /**
     * 将国家升级为王朝
     * @param nationId 国家ID
     * @param player 创建者(将成为第一任君主)
     * @param dynastyName 王朝名称
     * @return 创建的王朝
     */
    Dynasty createDynasty(UUID nationId, Player player, String dynastyName);
    
    /**
     * 获取国家的王朝
     */
    Optional<Dynasty> getDynastyByNation(UUID nationId);
    
    /**
     * 获取玩家的王朝
     */
    Optional<Dynasty> getDynastyByPlayer(UUID playerId);
    
    /**
     * 检查玩家是否在王朝中担任君主
     */
    boolean isRuler(UUID playerId);
    
    // ===== 继承者管理 =====
    /**
     * 培养继承人(王子/公主)
     * @param nationId 国家ID
     * @param heirName 继承人名称
     * @param relationship 关系类型
     * @param playerId 操作玩家
     */
    DynastyHeir trainHeir(UUID nationId, String heirName, 
                          HeirRelationship relationship, UUID playerId);
    
    /**
     * 投入资源培养继承人
     */
    void investInHeir(UUID heirId, BigDecimal amount, InvestmentType type);
    
    /**
     * 获取继承人列表
     */
    List<DynastyHeir> getHeirs(UUID dynastyId);
    
    /**
     * 指定继承人
     */
    void appointHeir(UUID dynastyId, UUID heirId, UUID appointorId);
    
    // ===== 继承流程 =====
    /**
     * 触发继承事件
     * @return 继承结果
     */
    SuccessionResult triggerSuccession(UUID dynastyId, SuccessionTrigger trigger);
    
    /**
     * 竞争继承投票
     */
    void castVote(UUID competitionId, UUID voterId, UUID candidateId);
    
    /**
     * 获取继承竞争详情
     */
    Optional<SuccessionCompetition> getCompetition(UUID competitionId);
    
    // ===== 王朝状态 =====
    /**
     * 获取/更新王朝威望
     */
    int getPrestige(UUID dynastyId);
    void modifyPrestige(UUID dynastyId, int delta);
    
    /**
     * 获取/更新稳定性
     */
    int getStability(UUID dynastyId);
    void modifyStability(UUID dynastyId, int delta);
    
    // ===== 事件监听 =====
    void addListener(DynastyEventListener listener);
    void removeListener(DynastyEventListener listener);
}
```

### 模型类
```java
// Dynasty.java - 王朝模型
public record Dynasty(
    UUID dynastyId,
    UUID nationId,
    String name,
    UUID founderUuid,
    String founderTitle,
    UUID currentSovereignUuid,
    UUID heirUuid,
    int prestige,
    int legacyPoints,
    long creationTime,
    long lastSuccessionTime,
    int stability,
    SuccessionType successionType,
    DynastyModifiers modifiers
) {}

// DynastyHeir.java - 继承人模型
public record DynastyHeir(
    UUID heirId,
    UUID playerUuid,
    UUID dynastyId,
    HeirRelationship relationship,
    String name,
    int claimStrength,      // 继承权强度
    int educationLevel,     // 教育程度
    int charisma,           // 魅力
    int politics,           // 政治手腕
    int martial,            // 军事才能
    HeirStatus status,
    long trainingStartTime,
    BigDecimal totalInvestment
) {}

// SuccessionResult.java - 继承结果
public record SuccessionResult(
    UUID competitionId,
    UUID oldSovereignUuid,
    UUID newSovereignUuid,
    UUID winnerHeirId,
    SuccessionType type,
    List<UUID> affectedPlayers,
    DynastyModifiers prestigeBonus,
    String ceremonyNarrative
) {}
```

### 核心实现类: DynastyServiceImpl
```java
package dev.starcore.starcore.module.dynasty;

// 核心继承逻辑
public class DynastyServiceImpl implements DynastyService {
    
    private final JavaPlugin plugin;
    private final NationService nationService;
    private final TreasuryService treasuryService;
    private final DatabaseService databaseService;
    private final StarCoreEventBus eventBus;
    
    // 培养因子权重
    private static final double EDUCATION_WEIGHT = 0.3;
    private static final double CHARISMA_WEIGHT = 0.2;
    private static final double POLITICS_WEIGHT = 0.3;
    private static final double MARTIAL_WEIGHT = 0.2;
    
    @Override
    public DynastyHeir trainHeir(UUID nationId, String heirName,
                                 HeirRelationship relationship, UUID playerId) {
        Dynasty dynasty = getDynastyByNation(nationId)
            .orElseThrow(() -> new IllegalStateException("Nation has no dynasty"));
        
        // 检查是否达到培养上限
        int currentHeirs = getHeirs(dynasty.dynastyId()).size();
        if (currentHeirs >= dynasty.modifiers().maxHeirs()) {
            throw new IllegalArgumentException("Max heirs limit reached");
        }
        
        // 检查培养费用
        BigDecimal trainingCost = getConfig().trainingCostBase()
            .multiply(BigDecimal.valueOf(1 + currentHeirs * 0.5));
        if (!treasuryService.withdraw(nationId, trainingCost)) {
            throw new IllegalArgumentException("Insufficient treasury funds");
        }
        
        // 创建继承人
        DynastyHeir heir = new DynastyHeir(
            UUID.randomUUID(),
            null, // 继承人可能是AI角色
            dynasty.dynastyId(),
            relationship,
            heirName,
            50, // 初始继承权
            0,  // 初始教育
            10, // 基础属性
            10,
            10,
            HeirStatus.TRAINING,
            System.currentTimeMillis(),
            BigDecimal.ZERO
        );
        
        saveHeir(heir);
        
        // 触发事件
        eventBus.publish(new DynastyHeirBornEvent(dynasty, heir, playerId));
        
        return heir;
    }
    
    @Override
    public SuccessionResult triggerSuccession(UUID dynastyId, SuccessionTrigger trigger) {
        Dynasty dynasty = getDynastyById(dynastyId)
            .orElseThrow(() -> new IllegalArgumentException("Dynasty not found"));
        
        UUID oldSovereign = dynasty.currentSovereignUuid();
        List<DynastyHeir> eligibleHeirs = getEligibleHeirs(dynastyId);
        
        if (eligibleHeirs.isEmpty()) {
            return handleDynastyCollapse(dynasty, trigger);
        }
        
        return switch (dynasty.successionType()) {
            case PRIMOGENITURE -> handlePrimogeniture(dynasty, eligibleHeirs);
            case ELECTIVE -> handleElective(dynasty, eligibleHeirs);
            case APPOINTED -> handleAppointed(dynasty, eligibleHeirs);
        };
    }
    
    private SuccessionResult handlePrimogeniture(Dynasty dynasty, List<DynastyHeir> heirs) {
        // 长子继承制: 继承权最强的继承人继位
        DynastyHeir winner = heirs.stream()
            .max(Comparator.comparingInt(DynastyHeir::claimStrength))
            .orElseThrow();
        
        return completeSuccession(dynasty, winner, SuccessionType.PRIMOGENITURE);
    }
    
    private SuccessionResult handleElective(Dynasty dynasty, List<DynastyHeir> heirs) {
        // 选举制: 开启竞争，贵族投票
        SuccessionCompetition competition = createCompetition(dynasty, heirs);
        
        // 发布选举开始事件
        eventBus.publish(new SuccessionElectionStartedEvent(competition));
        
        return new SuccessionResult(
            competition.competitionId(),
            dynasty.currentSovereignUuid(),
            null, // 选举中
            null,
            SuccessionType.ELECTIVE,
            List.of(),
            DynastyModifiers.EMPTY,
            "继承权竞争已经开始，请在 " + competition.endTime() + " 前投票"
        );
    }
    
    private SuccessionResult completeSuccession(Dynasty dynasty, DynastyHeir winner, 
                                                 SuccessionType type) {
        // 计算威望变化
        int prestigeChange = calculateSuccessionPrestigeChange(dynasty, type);
        
        // 更新王朝状态
        Dynasty newDynasty = new Dynasty(
            dynasty.dynastyId(),
            dynasty.nationId(),
            dynasty.name(),
            dynasty.founderUuid(),
            dynasty.founderTitle(),
            winner.playerUuid() != null ? winner.playerUuid() : dynasty.currentSovereignUuid(),
            winner.heirId(),
            dynasty.prestige() + prestigeChange,
            dynasty.legacyPoints() + 10,
            dynasty.creationTime(),
            System.currentTimeMillis(),
            Math.max(0, dynasty.stability() - 5), // 继承降低稳定性
            dynasty.successionType(),
            dynasty.modifiers()
        );
        
        saveDynasty(newDynasty);
        
        // 触发仪式事件
        String narrative = generateCeremonyNarrative(dynasty, winner, type);
        eventBus.publish(new SuccessionCompletedEvent(newDynasty, winner, narrative));
        
        return new SuccessionResult(
            UUID.randomUUID(),
            dynasty.currentSovereignUuid(),
            winner.playerUuid(),
            winner.heirId(),
            type,
            List.of(winner.playerUuid()),
            new DynastyModifiers(prestigeChange > 0),
            narrative
        );
    }
    
    private SuccessionResult handleDynastyCollapse(Dynasty dynasty, SuccessionTrigger trigger) {
        // 无合格继承人: 王朝崩溃
        Dynasty collapsedDynasty = new Dynasty(
            dynasty.dynastyId(),
            dynasty.nationId(),
            dynasty.name(),
            dynasty.founderUuid(),
            dynasty.founderTitle(),
            null, // 无君主
            null,
            Math.max(0, dynasty.prestige() - 50),
            0,
            dynasty.creationTime(),
            System.currentTimeMillis(),
            0, // 稳定性归零
            dynasty.successionType(),
            dynasty.modifiers()
        );
        
        saveDynasty(collapsedDynasty);
        
        eventBus.publish(new DynastyCollapseEvent(collapsedDynasty, trigger));
        
        return new SuccessionResult(
            UUID.randomUUID(),
            dynasty.currentSovereignUuid(),
            null,
            null,
            SuccessionType.NONE,
            List.of(),
            DynastyModifiers.EMPTY,
            "王朝因无人继承而崩溃"
        );
    }
    
    // 继承权强度计算
    public int calculateClaimStrength(DynastyHeir heir) {
        double educationBonus = heir.educationLevel() * EDUCATION_WEIGHT;
        double charismaBonus = heir.charisma() * CHARISMA_WEIGHT;
        double politicsBonus = heir.politics() * POLITICS_WEIGHT;
        double martialBonus = heir.martial() * MARTIAL_WEIGHT;
        
        // 关系加成
        double relationshipMultiplier = switch (heir.relationship()) {
            case SON, DAUGHTER -> 1.0;
            case BROTHER, SISTER -> 0.8;
            case COUSIN -> 0.6;
            case ADOPTED -> 0.5;
        };
        
        int base = (int)(educationBonus + charismaBonus + politicsBonus + martialBonus);
        return (int)(base * relationshipMultiplier);
    }
    
    // 仪式叙事生成
    private String generateCeremonyNarrative(Dynasty dynasty, DynastyHeir winner, 
                                            SuccessionType type) {
        String title = winner.relationship().getInheritanceTitle();
        String kingdom = dynasty.name();
        
        return String.format(
            "在万众瞩目之下，%s从先王手中接过象征王权的%s，正式成为%s的新一任统治者。%s王朝的历史翻开了新的篇章。",
            winner.name(),
            winner.playerUuid() != null ? "王冠" : "摄政权杖",
            kingdom,
            kingdom
        );
    }
}
```

---

## 3. 命令设计

### /dynasty 命令
```
/dynasty create <王朝名>     - 将当前国家升级为王朝 (需消耗1000万金币+特殊道具)
/dynasty info                - 查看当前王朝信息
/dynasty heir list           - 列出所有继承人
/dynasty heir train <关系> <名称> - 培养新继承人 (王子/公主/兄弟/收养)
/dynasty heir invest <继承人ID> <金币> - 投入金币培养继承人
/dynasty heir invest <继承人ID> education - 投入教育
/dynasty heir invest <继承人ID> charisma - 投入魅力培养
/dynasty heir appoint <继承人ID>        - 指定为继承人
/dynasty succession type <类型>         - 修改继承类型 (长子/选举/指定)
/dynasty prestige            - 查看王朝威望排名
/dynasty history             - 查看王朝历史事件
/dynasty ceremony            - 查看/预览继承仪式
```

### 命令处理器: DynastyCommand.java
```java
public class DynastyCommand implements CommandExecutor, TabCompleter {
    
    private final DynastyService dynastyService;
    private final NationService nationService;
    private final DynastyGuiFactory guiFactory;
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("仅玩家可用", NamedTextColor.RED));
            return true;
        }
        
        if (args.length == 0) {
            showHelp(player);
            return true;
        }
        
        String sub = args[0].toLowerCase();
        try {
            return switch (sub) {
                case "create" -> handleCreate(player, args);
                case "info" -> handleInfo(player);
                case "heir" -> handleHeir(player, args);
                case "succession" -> handleSuccession(player, args);
                case "prestige" -> handlePrestige(player);
                case "history" -> handleHistory(player);
                case "ceremony" -> handleCeremony(player);
                default -> { showHelp(player); yield true; }
            };
        } catch (Exception e) {
            player.sendMessage(Component.text("执行失败: " + e.getMessage(), NamedTextColor.RED));
            return true;
        }
    }
    
    private boolean handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("/dynasty create <王朝名>", NamedTextColor.YELLOW));
            return true;
        }
        
        String dynastyName = args[1];
        
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("你必须加入一个国家", NamedTextColor.RED));
            return true;
        }
        
        // 检查权限
        if (!nationService.isLeader(player.getUniqueId())) {
            player.sendMessage(Component.text("只有国家领袖可以创建王朝", NamedTextColor.RED));
            return true;
        }
        
        // 检查是否已有王朝
        if (dynastyService.getDynastyByNation(nationOpt.get().id().value()).isPresent()) {
            player.sendMessage(Component.text("该国家已有王朝", NamedTextColor.RED));
            return true;
        }
        
        Dynasty dynasty = dynastyService.createDynasty(
            nationOpt.get().id().value(),
            player,
            dynastyName
        );
        
        player.sendMessage(Component.text()
            .append(Component.text("恭喜！", NamedTextColor.GOLD))
            .append(Component.text(dynasty.name()))
            .append(Component.text("王朝已建立！", NamedTextColor.GREEN)));
        
        return true;
    }
    
    private boolean handleHeir(Player player, String[] args) {
        if (args.length < 2) {
            showHeirHelp(player);
            return true;
        }
        
        String action = args[1].toLowerCase();
        
        return switch (action) {
            case "list" -> handleHeirList(player);
            case "train" -> handleHeirTrain(player, args);
            case "invest" -> handleHeirInvest(player, args);
            case "appoint" -> handleHeirAppoint(player, args);
            default -> { showHeirHelp(player); yield true; }
        };
    }
}
```

---

## 4. 配置文件格式

### config/dynasty.yml
```yaml
# 王朝系统配置

# 创建条件
creation:
  # 最低国家等级
  min-nation-level: 3
  # 最低人口
  min-population: 10
  # 最低领土
  min-claims: 50
  # 消耗金币
  cost:
    gold: 10000000
    # 需要特殊道具
    items:
      - "STARCREATIVE_CROWN"
      - "DYNASTY_CHARTER"

# 继承人系统
heir:
  # 最大继承人数量
  max-heirs: 5
  # 基础培养费用
  training-cost-base: 100000
  # 每增加一个继承人费用倍率
  training-cost-multiplier: 1.5
  
  # 培养因子
  factors:
    education:
      weight: 0.3
      investment-return: 2  # 每投入1000金币增加1点
    charisma:
      weight: 0.2
      investment-return: 1.5
    politics:
      weight: 0.3
      investment-return: 2
    martial:
      weight: 0.2
      investment-return: 1.5
  
  # 培养周期
  training:
    # 达到满级需要的总投入
    max-level-investment: 5000000
    # AI继承人自动培养速度 (点数/天)
    auto-train-rate: 1

# 继承系统
succession:
  # 继承类型
  types:
    primogeniture:
      # 长子继承
      enabled: true
      stability-loss: 5
    elective:
      # 选举继承
      enabled: true
      voting-period-hours: 72
      min-voters: 3
      stability-loss: 10
      prestige-cost: 5000
    appointed:
      # 指定继承
      enabled: true
      stability-loss: 15
      # 指定后需等待天数
      cooldown-days: 7

# 王朝加成
modifiers:
  # 不同威望等级的加成
  prestige-tiers:
    0-100:
      tax-modifier: 0.95
      recruit-modifier: 0.95
      defense-modifier: 1.0
    101-500:
      tax-modifier: 0.90
      recruit-modifier: 0.90
      defense-modifier: 1.05
    501-1000:
      tax-modifier: 0.85
      recruit-modifier: 0.85
      defense-modifier: 1.10
    1001+:
      tax-modifier: 0.80
      recruit-modifier: 0.80
      defense-modifier: 1.15

# 稳定性系统
stability:
  # 每日衰减
  daily-decay: 1
  # 稳定事件
  stabilizing-events:
    - succession
    - alliance-formed
    - territory-gained
    - major-victory
  # 不稳定事件
  destabilizing-events:
    - succession-conflict
    - territory-lost
    - major-defeat
    - heir-died

# 仪式系统
ceremony:
  # 是否播放仪式动画
  enable-animation: true
  # 仪式时长(秒)
  duration: 30
  # 广播范围
  broadcast-range: 500
  # 仪式特效
  effects:
    - "firework"
    - "title-grant"
    - "broadcast"

# 称号配置
titles:
  founder: "开国君主"
  sovereign: "君主"
  heir-prince: "王子"
  heir-princess: "公主"
  prince-regent: "摄政王"
  queen-consort: "王后"
  prince-consort: "王夫"
```

---

## 5. 事件系统

### DynastyEventListener.java
```java
package dev.starcore.starcore.module.dynasty.listener;

public class DynastyEventListener implements Listener {
    
    private final DynastyService dynastyService;
    private final StarCoreScheduler scheduler;
    private final MessageService messages;
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onHeirBorn(DynastyHeirBornEvent event) {
        Dynasty dynasty = event.getDynasty();
        DynastyHeir heir = event.getHeir();
        
        // 全服公告
        String message = messages.format("dynasty.heir.born",
            dynasty.name(),
            heir.name(),
            heir.relationship().getDisplayName()
        );
        
        Bukkit.broadcast(Component.text(message, NamedTextColor.GOLD));
        
        // 给继承人授权
        if (heir.playerUuid() != null) {
            Player heirPlayer = Bukkit.getPlayer(heir.playerUuid());
            if (heirPlayer != null) {
                heirPlayer.sendTitle(
                    messages.format("dynasty.heir.title"),
                    messages.format("dynasty.heir.subtitle", dynasty.name()),
                    10, 60, 10
                );
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSuccession(SuccessionCompletedEvent event) {
        Dynasty dynasty = event.getDynasty();
        DynastyHeir winner = event.getWinner();
        
        // 播放仪式动画
        if (getConfig().ceremony().enableAnimation()) {
            playSuccessionCeremony(dynasty, winner);
        }
        
        // 更新国家统治者
        nationService.updateRuler(dynasty.nationId(), winner.playerUuid());
        
        // 更新玩家称号
        if (winner.playerUuid() != null) {
            Player newRuler = Bukkit.getPlayer(winner.playerUuid());
            if (newRuler != null) {
                nationService.grantRulerTitle(newRuler);
            }
        }
        
        // 发送全服公告
        Bukkit.broadcast(Component.text(event.getNarrative(), NamedTextColor.GOLD));
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDynastyCollapse(DynastyCollapseEvent event) {
        Dynasty dynasty = event.getDynasty();
        
        // 收回王朝称号
        if (dynasty.currentSovereignUuid() != null) {
            Player lastRuler = Bukkit.getPlayer(dynasty.currentSovereignUuid());
            if (lastRuler != null) {
                lastRuler.sendMessage(Component.text(
                    "你的王朝已经崩溃...",
                    NamedTextColor.DARK_RED
                ));
            }
        }
        
        // 发布服务器公告
        String message = String.format(
            "[重大事件] %s王朝因无人继承而崩溃！",
            dynasty.name()
        );
        Bukkit.broadcast(Component.text(message, NamedTextColor.RED));
    }
    
    @EventHandler
    public void onElectionStarted(SuccessionElectionStartedEvent event) {
        SuccessionCompetition competition = event.getCompetition();
        
        // 创建投票GUI
        competition.getEligibleVoters().forEach(playerId -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(Component.text()
                    .append(Component.text("[继承选举] ", NamedTextColor.YELLOW))
                    .append(Component.text("继承权竞争已开始，使用 ", NamedTextColor.GRAY))
                    .append(Component.text("/dynasty heir vote", NamedTextColor.AQUA))
                    .append(Component.text(" 投票", NamedTextColor.GRAY))
                );
            }
        });
    }
    
    private void playSuccessionCeremony(Dynasty dynasty, DynastyHeir winner) {
        // 在王宫位置播放烟花
        Location palace = findDynastyPalace(dynasty.nationId());
        if (palace != null) {
            for (int i = 0; i < 20; i++) {
                final int delay = i * 3;
                scheduler.runTaskLater(20L * delay, () -> {
                    palace.getWorld().playEffect(palace, Effect.ENDERDRAGON_SHOOT, 0);
                });
            }
        }
    }
}
```

### 事件类定义
```java
// 事件包: dev.starcore.starcore.module.dynasty.event

public record DynastyHeirBornEvent(
    Dynasty dynasty,
    DynastyHeir heir,
    UUID trainerId
) implements DynasticEvent {}

public record SuccessionCompletedEvent(
    Dynasty dynasty,
    DynastyHeir winner,
    String narrative
) implements DynasticEvent {}

public record SuccessionElectionStartedEvent(
    SuccessionCompetition competition
) implements DynasticEvent {}

public record DynastyCollapseEvent(
    Dynasty dynasty,
    SuccessionTrigger trigger
) implements DynasticEvent {}

public record SuccessionVoteCastEvent(
    SuccessionCompetition competition,
    UUID voterId,
    UUID candidateId
) implements DynasticEvent {}
```

---

## 6. GUI 设计

### DynastyInfoGui.java
```java
public class DynastyInfoGui {
    
    public static void openDynastyMenu(Player player) {
        Dynasty dynasty = dynastyService.getDynastyByPlayer(player.getUniqueId())
            .orElse(null);
        
        if (dynasty == null) {
            player.sendMessage(Component.text("你不在任何王朝中", NamedTextColor.RED));
            return;
        }
        
        new StaticGuiBuilder()
            .title("§6§l【" + dynasty.name() + "】王朝")
            .size(6)
            .item(4, createDynastyIcon(dynasty))
            .item(19, createHeirPreview())
            .item(21, createPrestigeDisplay())
            .item(23, createStabilityMeter())
            .item(25, createSuccessionTypeButton())
            .item(37, createHistoryButton())
            .item(43, createSettingsButton())
            .build()
            .open(player);
    }
    
    private static GuiItem createDynastyIcon(Dynasty dynasty) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("prestige", dynasty.prestige());
        meta.put("stability", dynasty.stability());
        meta.put("succession", dynasty.successionType());
        
        return new GuiItem(
            new ItemStack(Material.NETHER_STAR),
            event -> {
                event.setCancelled(true);
                openDetailedInfo(event.getPlayer(), dynasty);
            },
            "§6§n" + dynasty.name(),
            "§7威望: §f" + dynasty.prestige(),
            "§7稳定性: §f" + dynasty.stability() + "%",
            "",
            "§a点击查看详细信息"
        );
    }
    
    private static GuiItem createHeirPreview() {
        List<DynastyHeir> heirs = dynasty.getHeirs();
        DynastyHeir currentHeir = dynasty.heirUuid() != null 
            ? heirs.stream().filter(h -> h.heirId().equals(dynasty.heirUuid())).findFirst().orElse(null)
            : null;
        
        String heirName = currentHeir != null ? currentHeir.name() : "未指定";
        String heirStatus = currentHeir != null ? currentHeir.status().name() : "无";
        
        return new GuiItem(
            new ItemStack(Material.PLAYER_HEAD),
            event -> openHeirList(event.getPlayer()),
            "§e§l继承人",
            "§7当前继承人: §f" + heirName,
            "§7状态: §f" + heirStatus,
            "",
            "§7继承人数量: §f" + heirs.size() + "/" + dynasty.modifiers().maxHeirs(),
            "",
            "§a点击管理继承人"
        );
    }
}
```

---

## 7. 测试用例

### DynastyServiceTest.java
```java
class DynastyServiceTest {
    
    @Test
    void testCreateDynasty_Success() {
        // Given: 一个3级国家，有10个成员，50块领土
        UUID nationId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        
        Nation nation = createTestNation(nationId, 3, 10, 50);
        when(nationService.getNationById(nationId)).thenReturn(Optional.of(nation));
        when(treasuryService.withdraw(eq(nationId), any())).thenReturn(true);
        
        // When: 创建王朝
        Dynasty dynasty = dynastyService.createDynasty(nationId, playerId, "Test Dynasty");
        
        // Then: 验证王朝创建
        assertNotNull(dynasty);
        assertEquals("Test Dynasty", dynasty.name());
        assertEquals(nationId, dynasty.nationId());
        assertEquals(playerId, dynasty.currentSovereignUuid());
        assertEquals(0, dynasty.prestige());
        assertEquals(100, dynasty.stability());
    }
    
    @Test
    void testCreateDynasty_InsufficientLevel() {
        // Given: 一个1级国家
        UUID nationId = UUID.randomUUID();
        Nation nation = createTestNation(nationId, 1, 5, 10);
        when(nationService.getNationById(nationId)).thenReturn(Optional.of(nation));
        
        // When/Then: 创建失败
        assertThrows(IllegalStateException.class, () -> 
            dynastyService.createDynasty(nationId, UUID.randomUUID(), "Fail")
        );
    }
    
    @Test
    void testTrainHeir_InvestmentCalculation() {
        // Given: 一个继承人
        Dynasty dynasty = createTestDynasty();
        DynastyHeir heir = createTestHeir(dynasty.dynastyId(), 0, 0, 10, 10, 10);
        
        // When: 投入100万金币到教育
        dynastyService.investInHeir(heir.heirId(), new BigDecimal("1000000"), 
            InvestmentType.EDUCATION);
        
        // Then: 验证属性变化
        DynastyHeir updated = dynastyService.getHeir(heir.heirId());
        // 100万/2000 = 500点 (假设investment-return=2)
        assertEquals(500, updated.educationLevel());
        
        // 验证继承权强度重新计算
        int newStrength = dynastyService.calculateClaimStrength(updated);
        assertTrue(newStrength > 50); // 基础50
    }
    
    @Test
    void testPrimogenitureSuccession() {
        // Given: 长子继承制，3个继承人
        Dynasty dynasty = createTestDynasty(SuccessionType.PRIMOGENITURE);
        List<DynastyHeir> heirs = List.of(
            createTestHeir(dynasty.dynastyId(), 80, 50, 50, 50, 50), // 长子，高继承权
            createTestHeir(dynasty.dynastyId(), 60, 50, 50, 50, 50),
            createTestHeir(dynasty.dynastyId(), 40, 50, 50, 50, 50)
        );
        
        // When: 触发继承
        SuccessionResult result = dynastyService.triggerSuccession(
            dynasty.dynastyId(), 
            SuccessionTrigger.NATURAL_DEATH
        );
        
        // Then: 验证长子继位
        assertEquals(heirs.get(0).heirId(), result.winnerHeirId());
        assertEquals(SuccessionType.PRIMOGENITURE, result.type());
        assertNotNull(result.narrative());
    }
    
    @Test
    void testDynastyCollapse_NoEligibleHeirs() {
        // Given: 无继承人
        Dynasty dynasty = createTestDynasty();
        when(dynastyService.getEligibleHeirs(dynasty.dynastyId()))
            .thenReturn(List.of());
        
        // When: 触发继承
        SuccessionResult result = dynastyService.triggerSuccession(
            dynasty.dynastyId(),
            SuccessionTrigger.ASSASSINATION
        );
        
        // Then: 验证王朝崩溃
        assertNull(result.newSovereignUuid());
        assertEquals(SuccessionType.NONE, result.type());
        
        // 验证威望大幅下降
        Dynasty updated = dynastyService.getDynastyById(dynasty.dynastyId()).orElseThrow();
        assertTrue(updated.prestige() < dynasty.prestige());
    }
}
```

---

## 8. 实施计划

### Phase 1: 基础功能 (1-2天)
- 数据库表创建
- DynastyService 接口和基础实现
- 基本命令 /dynasty create/info

### Phase 2: 继承者系统 (2-3天)
- 继承人培养逻辑
- 属性计算系统
- /dynasty heir 命令

### Phase 3: 继承流程 (2天)
- 三种继承类型实现
- 选举系统
- 继承事件触发

### Phase 4: UI和活动 (1-2天)
- GUI界面
- 仪式动画
- 事件监听器

### 总工时: 约 8-10 人天
