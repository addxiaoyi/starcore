# 俘虏系统 - 详细设计方案

## 概述
击败敌方玩家有机会俘虏，俘虏可关押或索要赎金。俘虏期间玩家行动受限，但不会真正死亡。被俘期间可尝试逃跑、贿赂守卫或等待营救。

---

## 1. 数据库设计

### 主表: captives
```sql
CREATE TABLE captives (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    captive_id CHAR(36) NOT NULL UNIQUE,          -- 俘虏UUID
    captive_uuid CHAR(36) NOT NULL,               -- 被俘虏的玩家UUID
    captor_uuid CHAR(36) NOT NULL,                -- 俘虏者UUID
    nation_id CHAR(36) NOT NULL,                  -- 俘虏所属国家
    captor_nation_id CHAR(36) NOT NULL,           -- 俘虏方国家
    capture_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    capture_reason ENUM('BATTLE', 'ASSAULT', 'SIEGE', 'AMBUSH', 'BETRAYAL') NOT NULL,
    capture_context JSON,                         -- 战斗详情
    imprisonment_location VARCHAR(255),            -- 关押地点坐标
    prison_world VARCHAR(64),                     -- 所在世界
    
    -- 俘虏状态
    status ENUM('CAPTURED', 'ESCAPED', 'RANSOMED', 'RELEASED', 'EXECUTED', 'RESCUED') DEFAULT 'CAPTURED',
    status_change_time TIMESTAMP,
    status_change_reason VARCHAR(255),
    
    -- 赎金相关
    ransom_amount BIGINT,                          -- 赎金金额
    ransom_currency ENUM('GOLD', 'DIAMONDS', 'IRON') DEFAULT 'GOLD',
    ransom_status ENUM('PENDING', 'NEGOTIATING', 'PAID', 'EXPIRED', 'REFUSED') DEFAULT 'PENDING',
    ransom_deadline TIMESTAMP,                     -- 赎金截止时间
    
    -- 逃跑相关
    escape_attempts INT DEFAULT 0,                 -- 逃跑尝试次数
    last_escape_attempt TIMESTAMP,
    
    -- 囚犯待遇
    treatment_level ENUM('VIP', 'NORMAL', 'HARSH', 'SOLITARY') DEFAULT 'NORMAL',
    torture_applied INT DEFAULT 0,                 -- 酷刑应用次数
    
    -- 审讯相关
    intelligence_extracted BOOLEAN DEFAULT FALSE,  -- 是否已获取情报
    secrets_revealed JSON,                        -- 被泄露的情报
    
    INDEX idx_captive (captive_uuid),
    INDEX idx_captor (captor_uuid),
    INDEX idx_nation (nation_id),
    INDEX idx_status (status),
    INDEX idx_capture_time (capture_time)
);
```

### 监狱表: prisons
```sql
CREATE TABLE prisons (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    prison_id CHAR(36) NOT NULL UNIQUE,
    nation_id CHAR(36) NOT NULL,
    prison_name VARCHAR(64) NOT NULL,
    location_x INT NOT NULL,
    location_y INT NOT NULL,
    location_z INT NOT NULL,
    world VARCHAR(64) NOT NULL,
    
    -- 容量
    capacity INT DEFAULT 10,
    current_occupancy INT DEFAULT 0,
    
    -- 设施
    has_guards BOOLEAN DEFAULT FALSE,
    guard_count INT DEFAULT 0,
    has_escalation BOOLEAN DEFAULT FALSE,         -- 是否有逃生警报
    has_torture_chamber BOOLEAN DEFAULT FALSE,
    
    -- 安全等级
    security_level ENUM('MINIMUM', 'STANDARD', 'MAXIMUM', 'SUPERMAX') DEFAULT 'STANDARD',
    
    -- 维护
    maintenance_cost_per_day BIGINT DEFAULT 1000,
    last_maintenance TIMESTAMP,
    
    UNIQUE KEY uk_location (world, location_x, location_y, location_z),
    INDEX idx_nation (nation_id)
);
```

### 逃跑记录表: escape_attempts
```sql
CREATE TABLE escape_attempts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    attempt_id CHAR(36) NOT NULL UNIQUE,
    captive_id CHAR(36) NOT NULL,
    attempt_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    method ENUM('BRUTE_FORCE', 'BRIBE', 'LOCKPICK', 'DISTURBANCE', 'HELPER', 'TUNNEL') NOT NULL,
    success BOOLEAN DEFAULT FALSE,
    discovery_risk INT DEFAULT 0,                -- 暴露风险 0-100
    reward_given BIGINT DEFAULT 0,               -- 给协助者的奖励
    helper_uuid CHAR(36),                        -- 协助逃跑者UUID
    FOREIGN KEY (captive_id) REFERENCES captives(captive_id) ON DELETE CASCADE
);
```

### 俘虏历史表: captive_history
```sql
CREATE TABLE captive_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    captive_uuid CHAR(36) NOT NULL,
    capture_count INT DEFAULT 0,
    total_captivity_time BIGINT DEFAULT 0,       -- 总被囚禁时间(秒)
    ransom_paid_total BIGINT DEFAULT 0,
    escape_count INT DEFAULT 0,
    rescue_count INT DEFAULT 0,
    
    -- 统计
    longest_captivity BIGINT DEFAULT 0,           -- 最长被囚禁时间
    most_captured_by CHAR(36),                    -- 被谁俘虏最多
    most_captured_by_count INT DEFAULT 0,
    
    -- 声誉
    reputation INT DEFAULT 0,                      -- 俘虏声誉
    loyalty_to_captors INT DEFAULT 0,             -- 对俘虏者的忠诚(被洗脑程度)
    
    UNIQUE KEY uk_player (captive_uuid)
);
```

---

## 2. 核心类设计

### 接口: CaptiveService
```java
package dev.starcore.starcore.module.prison;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaptiveService {
    
    // ===== 俘虏操作 =====
    /**
     * 俘虏玩家
     * @param target 被俘虏的玩家
     * @param captor 俘虏者
     * @param context 战斗上下文
     * @return 俘虏记录
     */
    Captive capturePlayer(Player target, Player captor, CaptureContext context);
    
    /**
     * 获取玩家的俘虏状态
     */
    Optional<Captive> getCaptiveStatus(UUID playerId);
    
    /**
     * 检查玩家是否被俘虏
     */
    boolean isCaptured(UUID playerId);
    
    /**
     * 将俘虏转移到监狱
     */
    void transferToPrison(UUID captiveId, UUID prisonId);
    
    // ===== 赎金系统 =====
    /**
     * 设置赎金
     * @param captiveId 俘虏ID
     * @param amount 赎金金额
     * @param currency 货币类型
     * @param deadlineHours 截止时间(小时)
     */
    void setRansom(UUID captiveId, long amount, CurrencyType currency, int deadlineHours);
    
    /**
     * 支付赎金释放俘虏
     */
    RansomResult payRansom(UUID captiveId, Player payer);
    
    /**
     * 拒绝赎金
     */
    void refuseRansom(UUID captiveId);
    
    /**
     * 谈判赎金
     */
    NegotiationResult negotiateRansom(UUID captiveId, long offer);
    
    // ===== 逃跑系统 =====
    /**
     * 尝试逃跑
     */
    EscapeResult attemptEscape(UUID captiveId, EscapeMethod method);
    
    /**
     * 协助他人逃跑
     */
    void assistEscape(UUID captiveId, UUID helperId);
    
    /**
     * 获取逃跑成功率
     */
    double getEscapeChance(UUID captiveId);
    
    // ===== 监狱管理 =====
    /**
     * 建造监狱
     */
    Prison buildPrison(UUID nationId, String name, Location location, int capacity);
    
    /**
     * 获取国家所有监狱
     */
    List<Prison> getNationPrisons(UUID nationId);
    
    /**
     * 升级监狱设施
     */
    void upgradePrison(UUID prisonId, PrisonUpgrade upgrade);
    
    /**
     * 获取监狱中的俘虏
     */
    List<Captive> getPrisoners(UUID prisonId);
    
    // ===== 囚犯待遇 =====
    /**
     * 设置囚犯待遇等级
     */
    void setTreatmentLevel(UUID captiveId, TreatmentLevel level);
    
    /**
     * 审讯俘虏(获取情报)
     */
    IntelligenceResult interrogate(UUID captiveId, UUID interrogatorId, InterrogationMethod method);
    
    /**
     * 处决俘虏
     */
    ExecutionResult executeCaptive(UUID captiveId, UUID executorId, ExecutionMethod method);
    
    /**
     * 释放俘虏
     */
    void releaseCaptive(UUID captiveId, UUID releaserId, String reason);
}
```

### 模型类
```java
// Captive.java
public record Captive(
    UUID captiveId,
    UUID captiveUuid,
    UUID captorUuid,
    UUID nationId,
    UUID captorNationId,
    long captureTime,
    CaptureReason reason,
    CaptureContext context,
    Location imprisonmentLocation,
    CaptiveStatus status,
    long statusChangeTime,
    RansomeInfo ransomeInfo,
    EscapeInfo escapeInfo,
    TreatmentLevel treatmentLevel,
    InterrogationInfo interrogationInfo
) {}

// Prison.java
public record Prison(
    UUID prisonId,
    UUID nationId,
    String name,
    Location location,
    int capacity,
    int currentOccupancy,
    PrisonFacilities facilities,
    SecurityLevel securityLevel,
    long maintenanceCostPerDay
) {}

// EscapeResult.java
public record EscapeResult(
    boolean success,
    EscapeMethod method,
    double chance,
    int riskLevel,
    String narrative,
    List<Consequence> consequences
) {}

// RansomResult.java
public record RansomResult(
    boolean success,
    long amount,
    CurrencyType currency,
    String narrative
) {}
```

### 核心实现: CaptiveServiceImpl
```java
public class CaptiveServiceImpl implements CaptiveService {
    
    private final JavaPlugin plugin;
    private final DatabaseService databaseService;
    private final TreasuryService treasuryService;
    private final NationService nationService;
    private final StarCoreEventBus eventBus;
    private final StarCoreScheduler scheduler;
    
    // 配置
    private CaptiveConfig config;
    
    @Override
    public Captive capturePlayer(Player target, Player captor, CaptureContext context) {
        // 检查是否已被俘虏
        if (isCaptured(target.getUniqueId())) {
            throw new IllegalStateException("Player is already captured");
        }
        
        // 检查俘虏者是否是国家成员
        Optional<Nation> captorNation = nationService.nationOf(captor.getUniqueId());
        if (captorNation.isEmpty()) {
            throw new IllegalStateException("Captor must be a nation member");
        }
        
        // 检查目标是否是国家成员
        Optional<Nation> targetNation = nationService.nationOf(target.getUniqueId());
        
        // 判定俘虏成功概率
        double captureChance = calculateCaptureChance(context);
        if (Math.random() > captureChance) {
            // 俘虏失败
            captor.sendMessage(Component.text("俘虏失败！", NamedTextColor.RED));
            return null;
        }
        
        // 创建俘虏记录
        Captive captive = new Captive(
            UUID.randomUUID(),
            target.getUniqueId(),
            captor.getUniqueId(),
            targetNation.map(Nation::id).map(NationId::value).orElse(null),
            captorNation.get().id().value(),
            System.currentTimeMillis(),
            context.reason(),
            context,
            captor.getLocation(), // 默认关押在俘虏者位置
            CaptiveStatus.CAPTURED,
            System.currentTimeMillis(),
            new RansomeInfo(RansomStatus.PENDING, null, null, null),
            new EscapeInfo(0, null),
            TreatmentLevel.NORMAL,
            new InterrogationInfo(false, null, null)
        );
        
        // 保存到数据库
        saveCaptive(captive);
        
        // 传送俘虏到监狱
        transferToPrison(captive.captiveId(), findAvailablePrison(captorNation.get().id().value()));
        
        // 触发事件
        eventBus.publish(new PlayerCapturedEvent(captive, captor, target));
        
        return captive;
    }
    
    private double calculateCaptureChance(CaptureContext context) {
        double baseChance = config.baseCaptureChance(); // 30%
        
        // 战斗结果加成
        baseChance += switch (context.battleOutcome()) {
            case TOTAL_VICTORY -> 30;
            case MAJOR_VICTORY -> 20;
            case VICTORY -> 10;
            case PYRRHIC_VICTORY -> -10;
            case DEFEAT -> -20;
        };
        
        // 等级差修正
        int levelDiff = context.captorLevel() - context.targetLevel();
        baseChance += levelDiff * 2; // 每高1级+2%
        
        // 人数修正
        if (context.attackerCount() > 1) {
            baseChance += Math.min(15, context.attackerCount() * 3);
        }
        
        // 装备修正
        if (context.captorHasNets()) baseChance += 10;
        if (context.targetHasEscapeItems()) baseChance -= 15;
        
        return Math.max(0.05, Math.min(0.95, baseChance / 100.0));
    }
    
    @Override
    public void transferToPrison(UUID captiveId, UUID prisonId) {
        Captive captive = getCaptive(captiveId)
            .orElseThrow(() -> new IllegalArgumentException("Captive not found"));
        Prison prison = getPrison(prisonId)
            .orElseThrow(() -> new IllegalArgumentException("Prison not found"));
        
        if (prison.currentOccupancy() >= prison.capacity()) {
            throw new IllegalStateException("Prison is full");
        }
        
        // 更新俘虏位置
        Captive updated = new Captive(
            captive.captiveId(),
            captive.captiveUuid(),
            captive.captorUuid(),
            captive.nationId(),
            captive.captorNationId(),
            captive.captureTime(),
            captive.reason(),
            captive.context(),
            prison.location(),
            captive.status(),
            System.currentTimeMillis(),
            captive.ransomeInfo(),
            captive.escapeInfo(),
            captive.treatmentLevel(),
            captive.interrogationInfo()
        );
        
        saveCaptive(updated);
        
        // 更新监狱占用
        updatePrisonOccupancy(prisonId, prison.currentOccupancy() + 1);
        
        // 传送玩家
        Player captivePlayer = Bukkit.getPlayer(captive.captiveUuid());
        if (captivePlayer != null) {
            captivePlayer.teleport(prison.location());
            captivePlayer.sendTitle(
                "§c你已被俘虏",
                "§7你正在 " + prison.name() + " 服刑",
                10, 60, 10
            );
            applyCaptiveRestrictions(captivePlayer);
        }
        
        eventBus.publish(new CaptiveTransferredEvent(captive, prison));
    }
    
    private void applyCaptiveRestrictions(Player player) {
        // 移除所有物品
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        
        // 设置特殊游戏模式
        player.setGameMode(GameMode.ADVENTURE);
        
        // 移除飞翔能力
        if (player.isGliding()) player.setGliding(false);
        player.setAllowFlight(false);
        
        // 添加俘虏标签
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // 设置消耗品限制等
        }, 20L);
    }
    
    @Override
    public EscapeResult attemptEscape(UUID captiveId, EscapeMethod method) {
        Captive captive = getCaptive(captiveId)
            .orElseThrow(() -> new IllegalArgumentException("Captive not found"));
        
        if (captive.status() != CaptiveStatus.CAPTURED) {
            throw new IllegalStateException("Captive is not currently captured");
        }
        
        Prison prison = findPrisonByLocation(captive.imprisonmentLocation());
        
        // 计算逃跑成功率
        double baseChance = calculateEscapeChance(captive, prison, method);
        double randomRoll = Math.random();
        boolean success = randomRoll <= baseChance;
        
        // 记录逃跑尝试
        recordEscapeAttempt(captive, method, success);
        
        // 风险等级
        int riskLevel = calculateRiskLevel(captive, prison, method);
        
        if (success) {
            return handleSuccessfulEscape(captive, prison, method, riskLevel);
        } else {
            return handleFailedEscape(captive, prison, method, riskLevel);
        }
    }
    
    private double calculateEscapeChance(Captive captive, Prison prison, EscapeMethod method) {
        double baseChance = switch (method) {
            case BRUTE_FORCE -> 5;      // 硬闯
            case BRIBE -> 40;            // 贿赂
            case LOCKPICK -> 25;         // 撬锁
            case DISTURBANCE -> 20;      // 制造混乱
            case HELPER -> 60;           // 有人帮助
            case TUNNEL -> 35;           // 挖地道
        };
        
        // 逃跑尝试次数越多，成功率越低
        baseChance -= captive.escapeInfo().attemptCount() * 2;
        
        // 待遇等级影响
        if (captive.treatmentLevel() == TreatmentLevel.VIP) baseChance += 15;
        if (captive.treatmentLevel() == TreatmentLevel.SOLITARY) baseChance -= 20;
        
        // 监狱安全等级影响
        baseChance -= switch (prison.securityLevel()) {
            case MINIMUM -> 0;
            case STANDARD -> -10;
            case MAXIMUM -> -20;
            case SUPERMAX -> -35;
        };
        
        // 守卫数量影响
        if (prison.facilities().guardCount() > 0) {
            baseChance -= prison.facilities().guardCount() * 3;
        }
        
        // 警报系统
        if (prison.facilities().hasEscalation()) baseChance -= 15;
        
        return Math.max(1, Math.min(95, baseChance));
    }
    
    private EscapeResult handleSuccessfulEscape(Captive captive, Prison prison, 
                                               EscapeMethod method, int riskLevel) {
        // 更新俘虏状态
        Captive escaped = new Captive(
            captive.captiveId(),
            captive.captiveUuid(),
            captive.captorUuid(),
            captive.nationId(),
            captive.captorNationId(),
            captive.captureTime(),
            captive.reason(),
            captive.context(),
            captive.imprisonmentLocation(),
            CaptiveStatus.ESCAPED,
            System.currentTimeMillis(),
            captive.ransomeInfo(),
            new EscapeInfo(
                captive.escapeInfo().attemptCount() + 1,
                System.currentTimeMillis()
            ),
            captive.treatmentLevel(),
            captive.interrogationInfo()
        );
        saveCaptive(escaped);
        
        // 传送玩家到安全位置
        Player player = Bukkit.getPlayer(captive.captiveUuid());
        if (player != null) {
            // 传送到最近的友好领土
            Location safeLocation = findSafeLocation(player);
            player.teleport(safeLocation);
            removeCaptiveRestrictions(player);
            
            player.sendTitle("§a逃跑成功！", "§7你成功逃出了监狱", 10, 60, 10);
        }
        
        // 触发事件
        eventBus.publish(new CaptiveEscapedEvent(escaped, method));
        
        return new EscapeResult(
            true,
            method,
            0,
            riskLevel,
            generateEscapeNarrative(escaped, prison, method),
            List.of(Consequence.FREEDOM, Consequence.NATIONAL_SHAME)
        );
    }
    
    private EscapeResult handleFailedEscape(Captive captive, Prison prison,
                                            EscapeMethod method, int riskLevel) {
        // 逃跑失败后果
        List<Consequence> consequences = new ArrayList<>();
        
        // 暴露风险可能导致待遇恶化
        if (riskLevel > 50) {
            setTreatmentLevel(captive.captiveId(), TreatmentLevel.HARSH);
            consequences.add(Consequence.TREATMENT_WORSENED);
        }
        
        // 可能被处决
        if (riskLevel >= 90 && Math.random() < 0.1) {
            executeCaptive(captive.captiveId(), captive.captorUuid(), ExecutionMethod.EXECUTION);
            consequences.add(Consequence.EXECUTED);
        }
        
        Player player = Bukkit.getPlayer(captive.captiveUuid());
        if (player != null) {
            player.sendMessage(Component.text(
                "逃跑失败了！被守卫发现...",
                NamedTextColor.RED
            ));
        }
        
        // 通知俘虏者
        Player captor = Bukkit.getPlayer(captive.captorUuid());
        if (captor != null && riskLevel > 30) {
            captor.sendMessage(Component.text(
                "[警报] 你的俘虏试图逃跑！",
                NamedTextColor.YELLOW
            ));
        }
        
        eventBus.publish(new EscapeAttemptFailedEvent(captive, method, riskLevel));
        
        return new EscapeResult(
            false,
            method,
            0,
            riskLevel,
            "逃跑失败，被抓回监狱",
            consequences
        );
    }
    
    @Override
    public RansomResult payRansom(UUID captiveId, Player payer) {
        Captive captive = getCaptive(captiveId)
            .orElseThrow(() -> new IllegalArgumentException("Captive not found"));
        
        if (captive.ransomeInfo().status() != RansomStatus.PENDING &&
            captive.ransomeInfo().status() != RansomStatus.NEGOTIATING) {
            throw new IllegalStateException("Ransom cannot be paid");
        }
        
        RansomeInfo ransom = captive.ransomeInfo();
        
        // 检查赎金截止
        if (ransom.deadline() != null && System.currentTimeMillis() > ransom.deadline()) {
            throw new IllegalStateException("Ransom deadline has passed");
        }
        
        // 扣款
        if (!treasuryService.withdraw(payer.getUniqueId(), ransom.amount())) {
            throw new IllegalArgumentException("Insufficient funds");
        }
        
        // 释放俘虏
        Captive released = new Captive(
            captive.captiveId(),
            captive.captiveUuid(),
            captive.captorUuid(),
            captive.nationId(),
            captive.captorNationId(),
            captive.captureTime(),
            captive.reason(),
            captive.context(),
            captive.imprisonmentLocation(),
            CaptiveStatus.RANSOMED,
            System.currentTimeMillis(),
            new RansomeInfo(RansomStatus.PAID, ransom.amount(), payer.getUniqueId(), null),
            captive.escapeInfo(),
            captive.treatmentLevel(),
            captive.interrogationInfo()
        );
        saveCaptive(released);
        
        // 传送玩家
        Player captivePlayer = Bukkit.getPlayer(captive.captiveUuid());
        if (captivePlayer != null) {
            Location home = captivePlayer.getBedSpawnLocation();
            if (home == null) home = Bukkit.getWorld("world").getSpawnLocation();
            captivePlayer.teleport(home);
            removeCaptiveRestrictions(captivePlayer);
            captivePlayer.sendTitle("§a你已被释放", "§7赎金已支付", 10, 60, 10);
        }
        
        // 事件
        eventBus.publish(new CaptiveRansomPaidEvent(released, payer));
        
        return new RansomResult(true, ransom.amount(), ransom.currency(), "赎金已支付，俘虏已释放");
    }
}
```

---

## 3. 命令设计

### /captive 命令
```
/captive info              - 查看自己是否被俘虏
/captive status            - 查看俘虏状态详情
/captive ransom <金额>     - 向俘虏者提出赎金
/captive escape <方法>      - 尝试逃跑 (brute/bribe/lockpick/disturbance/helper)
/captive appeal            - 向俘虏者求情
/captive request <消息>     - 发送请求给俘虏者

# 俘虏者命令
/captive list              - 列出所有俘虏
/captive transfer <俘虏ID> <监狱ID> - 转移俘虏
/captive ransom <俘虏ID> <金额>    - 索要赎金
/captive negotiate <俘虏ID> <金额>  - 谈判赎金
/captive treat <俘虏ID> <等级>     - 设置待遇 (vip/normal/harsh/solitary)
/captive interrogate <俘虏ID> <方式> - 审讯俘虏
/captive release <俘虏ID>          - 释放俘虏
/captive execute <俘虏ID>         - 处决俘虏

# 监狱管理
/prison create <名称> [容量]       - 建造监狱
/prison list                       - 列出监狱
/prison upgrade <监狱ID> <设施>    - 升级设施
/prison stats <监狱ID>             - 查看监狱统计
```

### CaptiveCommand.java 核心逻辑
```java
public class CaptiveCommand implements CommandExecutor {
    
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
                case "info", "status" -> handleInfo(player);
                case "ransom" -> handleRansom(player, args);
                case "escape" -> handleEscape(player, args);
                case "appeal" -> handleAppeal(player);
                case "request" -> handleRequest(player, args);
                case "list" -> handleList(player);
                case "transfer" -> handleTransfer(player, args);
                case "treat" -> handleTreat(player, args);
                case "interrogate" -> handleInterrogate(player, args);
                case "release" -> handleRelease(player, args);
                case "execute" -> handleExecute(player, args);
                default -> { showHelp(player); yield true; }
            };
        } catch (Exception e) {
            player.sendMessage(Component.text("执行失败: " + e.getMessage(), NamedTextColor.RED));
            return true;
        }
    }
    
    private boolean handleEscape(Player player, String[] args) {
        Captive captive = captiveService.getCaptiveStatus(player.getUniqueId())
            .orElse(null);
        
        if (captive == null) {
            player.sendMessage(Component.text("你没有被俘虏", NamedTextColor.RED));
            return true;
        }
        
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /captive escape <方法>", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("方法: brute, bribe, lockpick, disturbance, helper", NamedTextColor.GRAY));
            return true;
        }
        
        EscapeMethod method = EscapeMethod.valueOf(args[1].toUpperCase());
        
        // 检查是否在冷却
        if (captive.escapeInfo().lastAttempt() != null) {
            long cooldown = config.escapeCooldown() * 1000L;
            if (System.currentTimeMillis() - captive.escapeInfo().lastAttempt() < cooldown) {
                long remaining = (cooldown - (System.currentTimeMillis() - captive.escapeInfo().lastAttempt())) / 1000;
                player.sendMessage(Component.text("逃跑冷却中，请等待 " + remaining + " 秒", NamedTextColor.YELLOW));
                return true;
            }
        }
        
        // 显示成功率
        double chance = captiveService.getEscapeChance(captive.captiveId());
        player.sendMessage(Component.text()
            .append(Component.text("逃跑方法: ", NamedTextColor.GRAY))
            .append(Component.text(method.name(), NamedTextColor.AQUA)));
        player.sendMessage(Component.text()
            .append(Component.text("预估成功率: ", NamedTextColor.GRAY))
            .append(Component.text(String.format("%.1f%%", chance), 
                chance > 50 ? NamedTextColor.GREEN : chance > 20 ? NamedTextColor.YELLOW : NamedTextColor.RED)));
        
        // 确认逃跑
        new ConfirmationGui("确认逃跑?", () -> {
            EscapeResult result = captiveService.attemptEscape(captive.captiveId(), method);
            
            if (result.success()) {
                player.sendMessage(Component.text(result.narrative(), NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text(result.narrative(), NamedTextColor.RED));
                if (result.consequences().contains(Consequence.TREATMENT_WORSENED)) {
                    player.sendMessage(Component.text("你的待遇变差了...", NamedTextColor.DARK_RED));
                }
            }
        }).confirm(player);
        
        return true;
    }
}
```

---

## 4. 配置文件

### config/prison.yml
```yaml
# 俘虏系统配置

# 俘虏设置
capture:
  # 基础俘虏成功率
  base-chance: 30
  # 是否需要在战斗胜利后俘虏
  require-victory: true
  # 俘虏后自动传送到监狱
  auto-transfer: true
  # 俘虏后自动移除装备
  strip-equipment: true

# 俘虏限制
restrictions:
  # 禁止使用的物品类型
  blocked-items:
    - ENDER_PEARL
    - FIREWORK
    - SPLASH_POTION
  # 禁止的命令
  blocked-commands:
    - home
    - spawn
    - tpa
    - warp
  # 移动限制(圈大小)
  movement-radius: 10
  # 是否禁止飞行
  disable-flight: true
  # 是否禁止潜行
  allow-sneaking: true

# 监狱设置
prison:
  # 建造费用
  build-cost: 500000
  # 默认容量
  default-capacity: 10
  # 最大容量
  max-capacity: 100
  # 维护费用/天
  maintenance-cost: 1000
  # 每日扣除
  daily-deduction: true

# 逃跑系统
escape:
  # 逃跑冷却(秒)
  cooldown: 300
  # 逃跑尝试最大次数
  max-attempts: 10
  # 超过最大次数自动处置
  auto-execute-after-max: true
  # 逃跑成功后惩罚俘虏者
  punish-captor: true
  # 逃跑成功率上限
  max-chance: 95

# 赎金系统
ransom:
  # 默认赎金倍率(基于俘虏者等级)
  default-multiplier: 10000
  # 赎金截止时间(小时)
  deadline-hours: 72
  # 最低赎金
  min-ransom: 100000
  # 最高赎金
  max-ransom: 10000000
  # 是否允许谈判
  allow-negotiation: true
  # 谈判折扣范围
  negotiation-range: 0.3

# 待遇系统
treatment:
  levels:
    vip:
      # 逃跑成功率加成
      escape-bonus: 15
      # 每日供应食物
      daily-food: true
    normal:
      escape-bonus: 0
      daily-food: true
    harsh:
      escape-bonus: -10
      daily-food: false
      # 体力消耗加速
      stamina-drain: 1.5
    solitary:
      escape-bonus: -20
      daily-food: false
      # 心理状态影响
      sanity-drain: 2.0

# 审讯系统
interrogation:
  # 审讯成功率
  success-rate: 40
  # 每次审讯时间(秒)
  duration: 60
  # 可获取的情报类型
  intel-types:
    - nation-location
    - troop-count
    - treasury-balance
    - ally-info
    - secret-plans
  # 俘虏抗拒度衰减
  resistance-decay: 5

# 处决系统
execution:
  # 是否允许处决
  allowed: true
  # 处决方式
  methods:
    - BEHEADING
    - HANGING
    - FIRING_SQUAD
  # 处决后惩罚
  after-effects:
    # 对俘虏者国家的影响
    nation-prestige-loss: 20
    # 对俘虏者个人声誉影响
    captor-reputation-loss: 10
    # 是否掉落物品
    drop-items: true
```

---

## 5. 事件系统

```java
// PlayerCapturedEvent - 玩家被俘虏时触发
public record PlayerCapturedEvent(
    Captive captive,
    Player captor,
    Player captivePlayer
) implements CaptiveEvent {}

// CaptiveTransferredEvent - 俘虏被转移时触发
public record CaptiveTransferredEvent(
    Captive captive,
    Prison destination
) implements CaptiveEvent {}

// CaptiveEscapedEvent - 俘虏逃跑成功时触发
public record CaptiveEscapedEvent(
    Captive captive,
    EscapeMethod method
) implements CaptiveEvent {}

// EscapeAttemptFailedEvent - 逃跑失败时触发
public record EscapeAttemptFailedEvent(
    Captive captive,
    EscapeMethod method,
    int riskLevel
) implements CaptiveEvent {}

// CaptiveRansomPaidEvent - 赎金支付时触发
public record CaptiveRansomPaidEvent(
    Captive captive,
    Player payer
) implements CaptiveEvent {}

// CaptiveExecutedEvent - 俘虏被处决时触发
public record CaptiveExecutedEvent(
    Captive captive,
    Player executor,
    ExecutionMethod method
) implements CaptiveEvent {}

// CaptiveReleasedEvent - 俘虏被释放时触发
public record CaptiveReleasedEvent(
    Captive captive,
    Player releaser,
    String reason
) implements CaptiveEvent {}
```

### 监听器
```java
public class CaptiveListener implements Listener {
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerCaptured(PlayerCapturedEvent event) {
        Player captive = event.getCaptivePlayer();
        
        // 发送全服公告(可选)
        if (getConfig().broadcastCaptures()) {
            String message = String.format(
                "[俘虏] %s 被 %s 俘虏！",
                captive.getName(),
                event.getCaptor().getName()
            );
            Bukkit.broadcast(Component.text(message, NamedTextColor.YELLOW));
        }
        
        // 添加俘虏成就
        achievementService.unlock(captive.getUniqueId(), "first_capture");
        achievementService.unlock(event.getCaptor().getUniqueId(), "first_captive");
    }
    
    @EventHandler
    public void onCaptiveEscaped(CaptiveEscapedEvent event) {
        // 通知俘虏者
        Player captor = Bukkit.getPlayer(event.getCaptive().captorUuid());
        if (captor != null) {
            captor.sendMessage(Component.text()
                .append(Component.text("[警报] ", NamedTextColor.RED))
                .append(Component.text(event.getCaptive().captiveUuid() + " 逃跑了！", NamedTextColor.YELLOW))
            );
        }
        
        // 给俘虏者添加负面buff
        if (getConfig().punishCaptor()) {
            Player captorP = Bukkit.getPlayer(event.getCaptive().captorUuid());
            if (captorP != null) {
                // 添加沮丧效果
                captorP.addPotionEffect(new PotionEffect(PotionEffectType.UNLUCK, 3600 * 20, 1));
            }
        }
    }
    
    @EventHandler
    public void onCaptiveExecuted(CaptiveExecutedEvent event) {
        Captive captive = event.getCaptive();
        
        // 全服公告
        String message = String.format(
            "[处决] %s 被 %s 处决了！",
            Bukkit.getPlayerName(captive.captiveUuid()),
            event.getExecutor().getName()
        );
        Bukkit.broadcast(Component.text(message, NamedTextColor.DARK_RED));
        
        // 俘虏者国家惩罚
        nationService.modifyPrestige(captive.captorNationId(), 
            -getConfig().execution().nationPrestigeLoss());
    }
}
```

---

## 6. 测试用例

```java
class CaptiveServiceTest {
    
    @Test
    void testCapturePlayer_Success() {
        // Given: 战斗胜利后俘虏
        Player target = mock(Player.class);
        Player captor = mock(Player.class);
        when(target.getUniqueId()).thenReturn(UUID.randomUUID());
        when(captor.getUniqueId()).thenReturn(UUID.randomUUID());
        when(captor.getLocation()).thenReturn(new Location(Bukkit.getWorld("world"), 0, 64, 0));
        
        Nation captorNation = createTestNation();
        when(nationService.nationOf(captor.getUniqueId())).thenReturn(Optional.of(captorNation));
        when(nationService.nationOf(target.getUniqueId())).thenReturn(Optional.empty());
        
        CaptureContext context = new CaptureContext(
            CaptureReason.BATTLE,
            BattleOutcome.TOTAL_VICTORY,
            30, 20, // 等级
            5, 1,   // 人数
            false, false
        );
        
        // When
        Captive result = captiveService.capturePlayer(target, captor, context);
        
        // Then
        assertNotNull(result);
        assertEquals(target.getUniqueId(), result.captiveUuid());
        assertEquals(CaptiveStatus.CAPTURED, result.status());
    }
    
    @Test
    void testEscapeChance_Calculation() {
        // Given: 不同条件下的逃跑成功率
        Captive captive = createTestCaptive(TreatmentLevel.NORMAL);
        Prison prison = createTestPrison(SecurityLevel.MAXIMUM, 5);
        
        // When: 不同逃跑方法
        double bruteChance = captiveService.getEscapeChance(captive.captiveId()); // 默认brute
        
        // Then
        assertTrue(bruteChance < 20); // 最大安全等级硬闯成功率很低
    }
    
    @Test
    void testRansomNegotiation() {
        // Given: 赎金100万
        Captive captive = createTestCaptiveWithRansom(1000000);
        
        // When: 出价80万谈判
        NegotiationResult result = captiveService.negotiateRansom(
            captive.captiveId(), 800000);
        
        // Then
        assertTrue(result.success()); // 在30%折扣范围内
        assertEquals(700000, result.finalAmount()); // 100万 - 30% = 70万
    }
}
```

---

## 7. 实施计划

### Phase 1: 基础俘虏 (1-2天)
- 数据库表
- 俘虏/释放逻辑
- 基本命令

### Phase 2: 监狱系统 (1-2天)
- 监狱建造管理
- 俘虏转移
- 待遇系统

### Phase 3: 逃跑系统 (1天)
- 逃跑逻辑
- 成功率计算
- 逃跑事件

### Phase 4: 赎金系统 (1天)
- 赎金设置/支付
- 谈判逻辑

### Phase 5: 审讯与处决 (1天)
- 审讯系统
- 处决功能
- 情报系统

### 总工时: 约 6-8 人天
