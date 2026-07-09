# 限时事件系统 - 详细设计方案

## 概述
服务器定期举办限时事件：节日活动、季节活动、纪念日活动。事件期间有特殊任务、稀有掉落、独特玩法和丰厚奖励。

---

## 1. 数据库设计

### 事件定义表: timed_events
```sql
CREATE TABLE timed_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id CHAR(36) NOT NULL UNIQUE,
    
    -- 事件信息
    event_key VARCHAR(64) NOT NULL,              -- 事件标识符
    event_name VARCHAR(128) NOT NULL,           -- 显示名称
    event_name_zh VARCHAR(128),                -- 中文名
    description TEXT,                           -- 描述
    description_zh TEXT,
    
    -- 事件类型
    event_category ENUM('FESTIVAL', 'SEASONAL', 'ANNIVERSARY', 'COLLABORATION', 'SPECIAL') NOT NULL,
    
    -- 时间配置
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    timezone VARCHAR(32) DEFAULT 'Asia/Shanghai',
    
    -- 重复规则
    recurrence_rule VARCHAR(255),               -- Cron表达式
    is_recurring BOOLEAN DEFAULT FALSE,
    
    -- 优先级
    priority INT DEFAULT 0,                     -- 高优先级事件覆盖低优先级
    
    -- 状态
    status ENUM('DRAFT', 'SCHEDULED', 'ACTIVE', 'PAUSED', 'ENDED', 'CANCELLED') DEFAULT 'DRAFT',
    
    -- 配置
    event_config JSON,                          -- 事件配置
    
    -- 统计
    participant_count INT DEFAULT 0,
    total_rewards_distributed BIGINT DEFAULT 0,
    
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_time TIMESTAMP,
    
    INDEX idx_key (event_key),
    INDEX idx_time (start_time, end_time),
    INDEX idx_status (status),
    INDEX idx_category (event_category)
);
```

### 事件任务表: event_quests
```sql
CREATE TABLE event_quests (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    quest_id CHAR(36) NOT NULL UNIQUE,
    event_id CHAR(36) NOT NULL,
    
    -- 任务信息
    quest_key VARCHAR(64) NOT NULL,
    title VARCHAR(128) NOT NULL,
    description TEXT,
    description_zh TEXT,
    
    -- 任务目标
    objective_type ENUM('COLLECT', 'KILL', 'BUILD', 'TRADE', 'EXPLORE', 'CUSTOM') NOT NULL,
    objective_target VARCHAR(64),               -- 目标ID或类型
    objective_count INT DEFAULT 1,              -- 目标数量
    
    -- 奖励
    rewards JSON,                             -- 奖励配置
    reward_claimable INT DEFAULT 1,           -- 可领取次数
    
    -- 限制
    player_level_req INT DEFAULT 1,
    nation_required BOOLEAN DEFAULT FALSE,
    daily_limit INT DEFAULT 0,                 -- 每日次数限制
    
    -- 状态
    is_enabled BOOLEAN DEFAULT TRUE,
    auto_complete BOOLEAN DEFAULT FALSE,       -- 是否自动完成
    
    -- 时间限制
    daily_reset BOOLEAN DEFAULT FALSE,         -- 每日重置
    time_window_start TIME,
    time_window_end TIME,
    
    INDEX idx_event (event_id),
    INDEX idx_objective (objective_type),
    INDEX idx_enabled (is_enabled)
);
```

### 玩家事件进度表: player_event_progress
```sql
CREATE TABLE player_event_progress (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    progress_id CHAR(36) NOT NULL UNIQUE,
    
    player_uuid CHAR(36) NOT NULL,
    event_id CHAR(36) NOT NULL,
    quest_id CHAR(36) NOT NULL,
    
    -- 进度
    current_progress INT DEFAULT 0,
    target_progress INT NOT NULL,
    completion_count INT DEFAULT 0,            -- 完成次数
    last_completion_time TIMESTAMP,
    
    -- 领取状态
    reward_claimed BOOLEAN DEFAULT FALSE,
    reward_claim_time TIMESTAMP,
    
    -- 每日进度
    daily_progress INT DEFAULT 0,
    daily_reset_time TIMESTAMP,
    
    -- 时间戳
    started_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP,
    
    UNIQUE KEY uk_player_quest (player_uuid, quest_id),
    INDEX idx_player (player_uuid),
    INDEX idx_event (event_id)
);
```

### 事件商店表: event_shops
```sql
CREATE TABLE event_shops (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    shop_id CHAR(36) NOT NULL UNIQUE,
    event_id CHAR(36) NOT NULL,
    
    -- 商店信息
    shop_name VARCHAR(64) NOT NULL,
    currency_type VARCHAR(32) NOT NULL,         -- 货币类型
    currency_id VARCHAR(64),                   -- 自定义货币ID
    
    -- 开放时间
    open_hours JSON,                          -- {"start": "09:00", "end": "22:00"}
    days_of_week JSON,                        -- [1,2,3,4,5,6,7]
    
    -- 限购
    daily_limit_per_item INT DEFAULT 1,
    total_limit_per_item INT DEFAULT 0,
    
    -- 状态
    is_active BOOLEAN DEFAULT TRUE,
    
    INDEX idx_event (event_id)
);
```

### 事件掉落表: event_drops
```sql
CREATE TABLE event_drops (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    drop_id CHAR(36) NOT NULL UNIQUE,
    event_id CHAR(36) NOT NULL,
    
    -- 掉落信息
    item_id VARCHAR(64) NOT NULL,              -- 物品ID
    item_type ENUM('MATERIAL', 'EQUIPMENT', 'CURRENCY', 'COSMETIC', 'TOKEN') NOT NULL,
    
    -- 掉落条件
    source_type ENUM('MOB', 'BLOCK', 'FISHING', 'TRADING', 'QUEST', 'LOGIN') NOT NULL,
    source_id VARCHAR(64),                    -- 来源ID
    conditions JSON,                          -- 额外条件
    
    -- 掉落率
    base_chance DECIMAL(5,4) DEFAULT 0.0001, -- 基础概率
    luck_multiplier DECIMAL(3,2) DEFAULT 1.00, -- 幸运加成
    guaranteed_drops INT DEFAULT 0,           -- 必掉数量
    
    -- 限制
    daily_limit INT DEFAULT 0,
    total_limit INT DEFAULT 0,
    
    -- 稀有度
    rarity ENUM('COMMON', 'UNCOMMON', 'RARE', 'EPIC', 'LEGENDARY') DEFAULT 'COMMON',
    
    INDEX idx_event (event_id),
    INDEX idx_source (source_type, source_id)
);
```

---

## 2. 核心类设计

### 接口: TimedEventService
```java
package dev.starcore.starcore.module.event.timed;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface TimedEventService {
    
    // ===== 事件管理 =====
    /**
     * 创建事件
     */
    TimedEvent createEvent(TimedEventConfig config);
    
    /**
     * 获取当前活动事件
     */
    List<TimedEvent> getActiveEvents();
    
    /**
     * 获取玩家可参与的事件
     */
    List<TimedEvent> getAvailableEvents(UUID playerId);
    
    /**
     * 获取事件详情
     */
    Optional<TimedEvent> getEvent(UUID eventId);
    
    /**
     * 启动事件
     */
    void startEvent(UUID eventId);
    
    /**
     * 结束事件
     */
    void endEvent(UUID eventId);
    
    // ===== 事件任务 =====
    /**
     * 获取玩家任务进度
     */
    PlayerQuestProgress getQuestProgress(UUID playerId, UUID questId);
    
    /**
     * 更新任务进度
     */
    void updateProgress(UUID playerId, UUID questId, int delta);
    
    /**
     * 完成任务并领取奖励
     */
    QuestCompletionResult completeQuest(UUID playerId, UUID questId);
    
    /**
     * 领取任务奖励
     */
    RewardClaimResult claimReward(UUID playerId, UUID questId);
    
    // ===== 掉落系统 =====
    /**
     * 检查掉落
     */
    List<EventDrop> checkDrops(UUID playerId, DropSource source);
    
    /**
     * 记录掉落
     */
    void recordDrop(UUID playerId, UUID dropId, int quantity);
    
    // ===== 事件商店 =====
    /**
     * 获取事件商店物品
     */
    List<EventShopItem> getShopItems(UUID eventId);
    
    /**
     * 购买物品
     */
    PurchaseResult purchase(UUID playerId, UUID shopItemId, int quantity);
    
    // ===== 排行榜 =====
    /**
     * 获取事件排行榜
     */
    List<EventLeaderboardEntry> getLeaderboard(UUID eventId, String rankingType, int limit);
    
    /**
     * 更新玩家排名分数
     */
    void updateScore(UUID playerId, UUID eventId, String scoreType, long delta);
    
    // ===== 通知 =====
    /**
     * 发送事件开始通知
     */
    void notifyEventStart(TimedEvent event);
    
    /**
     * 发送事件即将结束通知
     */
    void notifyEventEnding(TimedEvent event, int hoursRemaining);
}
```

### 模型类
```java
// TimedEvent.java
public record TimedEvent(
    UUID eventId,
    String eventKey,
    String name,
    String nameZh,
    String description,
    String descriptionZh,
    EventCategory category,
    LocalDateTime startTime,
    LocalDateTime endTime,
    String timezone,
    String recurrenceRule,
    boolean isRecurring,
    int priority,
    EventStatus status,
    EventConfig config,
    EventStatistics statistics
) {
    public boolean isActive() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of(timezone));
        return status == EventStatus.ACTIVE && 
               now.isAfter(startTime) && now.isBefore(endTime);
    }
    
    public Duration remainingTime() {
        return Duration.between(LocalDateTime.now(), endTime);
    }
}

// EventConfig.java
public record EventConfig(
    Map<RewardType, Double> rewardMultiplier,     // 奖励倍率
    Map<String, Double> dropRateMultiplier,       // 掉落率加成
    List<Modifier> modifiers,                    // 特殊修饰符
    List<String> participatingWorlds,             // 参与世界
    boolean enableLeaderboard,
    String leaderboardType,
    Map<String, Object> customSettings
) {}

// PlayerQuestProgress.java
public record PlayerQuestProgress(
    UUID progressId,
    UUID playerId,
    UUID eventId,
    UUID questId,
    int currentProgress,
    int targetProgress,
    int completionCount,
    long lastCompletionTime,
    boolean rewardClaimed,
    long rewardClaimTime,
    int dailyProgress,
    long dailyResetTime
) {
    public boolean isComplete() {
        return currentProgress >= targetProgress;
    }
    
    public double getProgressPercent() {
        return (double) currentProgress / targetProgress * 100;
    }
}

// EventDrop.java
public record EventDrop(
    UUID dropId,
    UUID eventId,
    ItemStack item,
    DropSource source,
    String sourceId,
    double baseChance,
    int guaranteedDrops,
    Rarity rarity,
    int dailyLimit,
    int dailyClaimed
) {}
```

### 核心实现: TimedEventServiceImpl
```java
public class TimedEventServiceImpl implements TimedEventService, Listener {
    
    private final JavaPlugin plugin;
    private final DatabaseService databaseService;
    private final EconomyService economyService;
    private final StarCoreScheduler scheduler;
    private final StarCoreEventBus eventBus;
    
    private TimedEventConfig globalConfig;
    
    // 缓存
    private final Map<UUID, TimedEvent> activeEventsCache = new ConcurrentHashMap<>();
    private final Map<UUID, List<EventQuest>> eventQuestsCache = new ConcurrentHashMap<>();
    
    @Override
    public void onEnable() {
        // 加载活跃事件
        loadActiveEvents();
        
        // 启动事件检查定时器
        scheduler.runTaskTimer(
            () -> checkEventStatus(),
            20L * 60, // 每分钟检查
            20L * 60
        );
        
        // 重置每日进度定时器
        scheduler.runTaskTimer(
            () -> resetDailyProgress(),
            20L * 60 * 60 * 12, // 中午重置
            20L * 60 * 60 * 24  // 每天
        );
    }
    
    @Override
    public List<TimedEvent> getActiveEvents() {
        // 返回缓存的活跃事件
        return activeEventsCache.values().stream()
            .filter(TimedEvent::isActive)
            .collect(Collectors.toList());
    }
    
    @Override
    public void updateProgress(UUID playerId, UUID questId, int delta) {
        PlayerQuestProgress progress = getOrCreateProgress(playerId, questId);
        
        // 检查是否超过每日限制
        if (progress.dailyProgress() + delta > getQuest(questId).dailyLimit() && 
            getQuest(questId).dailyLimit() > 0) {
            return; // 超过每日限制
        }
        
        // 更新进度
        int newProgress = Math.min(
            progress.currentProgress() + delta,
            progress.targetProgress()
        );
        int newDailyProgress = progress.dailyProgress() + delta;
        
        PlayerQuestProgress updated = new PlayerQuestProgress(
            progress.progressId(),
            progress.playerId(),
            progress.eventId(),
            progress.questId(),
            newProgress,
            progress.targetProgress(),
            progress.completionCount(),
            progress.lastCompletionTime(),
            progress.rewardClaimed(),
            progress.rewardClaimTime(),
            newDailyProgress,
            progress.dailyResetTime()
        );
        
        saveProgress(updated);
        
        // 检查是否完成
        if (newProgress >= progress.targetProgress() && progress.currentProgress() < progress.targetProgress()) {
            // 触发完成事件
            eventBus.publish(new QuestCompletedEvent(updated));
        }
        
        // 发送进度更新通知
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            sendProgressNotification(player, updated);
        }
    }
    
    @Override
    public QuestCompletionResult completeQuest(UUID playerId, UUID questId) {
        PlayerQuestProgress progress = getQuestProgress(playerId, questId);
        if (progress == null || !progress.isComplete()) {
            return new QuestCompletionResult(false, "任务未完成", null);
        }
        
        if (progress.completionCount() > 0 && !getQuest(questId).isRepeatable()) {
            return new QuestCompletionResult(false, "任务已完成且不可重复", null);
        }
        
        EventQuest quest = getQuest(questId);
        TimedEvent event = getEvent(progress.eventId()).orElse(null);
        
        // 应用事件奖励加成
        List<ItemStack> rewards = applyRewardMultipliers(
            quest.rewards(), 
            event != null ? event.config().rewardMultiplier() : Map.of()
        );
        
        // 发放奖励
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            for (ItemStack reward : rewards) {
                player.getInventory().addItem(reward);
            }
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
        
        // 更新进度
        PlayerQuestProgress updated = new PlayerQuestProgress(
            progress.progressId(),
            progress.playerId(),
            progress.eventId(),
            progress.questId(),
            progress.targetProgress(),
            progress.targetProgress(),
            progress.completionCount() + 1,
            System.currentTimeMillis(),
            false, // 奖励未领取
            0,
            0,
            getNextDailyResetTime()
        );
        saveProgress(updated);
        
        // 更新排行榜
        if (event != null && event.config().enableLeaderboard()) {
            updateScore(playerId, event.eventId(), quest.questKey(), 1);
        }
        
        // 事件
        eventBus.publish(new QuestCompletedEvent(updated));
        
        return new QuestCompletionResult(true, "任务完成！", rewards);
    }
    
    @Override
    public List<EventDrop> checkDrops(UUID playerId, DropSource source) {
        List<EventDrop> drops = new ArrayList<>();
        
        // 获取活跃事件
        for (TimedEvent event : getActiveEvents()) {
            List<EventDrop> eventDrops = getDropsForSource(event.eventId(), source);
            
            for (EventDrop drop : eventDrops) {
                // 检查玩家是否已达到每日限制
                if (drop.dailyLimit() > 0) {
                    int claimed = getDailyDropCount(playerId, drop.dropId());
                    if (claimed >= drop.dailyLimit()) continue;
                }
                
                // 计算实际掉落概率
                double actualChance = calculateDropChance(drop, playerId, source);
                
                // 随机判定
                if (Math.random() < actualChance) {
                    drops.add(drop);
                    recordDrop(playerId, drop.dropId(), 1);
                }
            }
        }
        
        return drops;
    }
    
    private double calculateDropChance(EventDrop drop, UUID playerId, DropSource source) {
        double chance = drop.baseChance();
        
        // 幸运加成
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            double luck = getPlayerLuck(playerId);
            chance *= (1 + luck * drop.luckMultiplier());
        }
        
        // 事件掉落率加成
        TimedEvent event = getEvent(drop.eventId()).orElse(null);
        if (event != null) {
            String dropKey = source.type() + ":" + source.id();
            Double multiplier = event.config().dropRateMultiplier().get(dropKey);
            if (multiplier != null) {
                chance *= multiplier;
            }
        }
        
        return Math.min(1.0, chance);
    }
    
    @Override
    public PurchaseResult purchase(UUID playerId, UUID shopItemId, int quantity) {
        EventShopItem item = getShopItem(shopItemId)
            .orElseThrow(() -> new IllegalArgumentException("物品不存在"));
        
        TimedEvent event = getEvent(item.eventId()).orElse(null);
        if (event == null || !event.isActive()) {
            return new PurchaseResult(false, "活动未开放", 0);
        }
        
        EventShop shop = getShop(item.shopId()).orElse(null);
        
        // 检查购买时间
        if (shop != null && !isWithinShopHours(shop)) {
            return new PurchaseResult(false, "商店未营业", 0);
        }
        
        // 检查每日/总购买限制
        int dailyBought = getDailyPurchaseCount(playerId, shopItemId);
        int totalBought = getTotalPurchaseCount(playerId, shopItemId);
        
        if (dailyBought + quantity > shop.dailyLimitPerItem()) {
            return new PurchaseResult(false, "超过每日购买限制", 0);
        }
        
        if (shop.totalLimitPerItem() > 0 && totalBought + quantity > shop.totalLimitPerItem()) {
            return new PurchaseResult(false, "超过总购买限制", 0);
        }
        
        // 计算价格
        long totalPrice = item.price() * quantity;
        
        // 扣款
        if (!deductCurrency(playerId, item.currencyType(), item.currencyId(), totalPrice)) {
            return new PurchaseResult(false, "货币不足", 0);
        }
        
        // 发放物品
        Player player = Bukkit.getPlayer(playerId);
        ItemStack purchased = item.item().clone();
        purchased.setAmount(quantity);
        
        if (player != null) {
            player.getInventory().addItem(purchased);
            player.sendMessage(Component.text()
                .append(Component.text("购买成功: ", NamedTextColor.GREEN))
                .append(Component.text(item.item().getItemMeta().getDisplayName(), NamedTextColor.GOLD))
                .append(Component.text(" x" + quantity, NamedTextColor.WHITE))
            );
        }
        
        // 记录购买
        recordPurchase(playerId, shopItemId, quantity, totalPrice);
        
        return new PurchaseResult(true, "购买成功", totalPrice);
    }
    
    // ===== 事件状态检查 =====
    private void checkEventStatus() {
        LocalDateTime now = LocalDateTime.now();
        
        // 检查需要开始的事件
        List<TimedEvent> toStart = getScheduledEvents().stream()
            .filter(e -> !e.isActive() && e.startTime().isBefore(now))
            .collect(Collectors.toList());
        
        for (TimedEvent event : toStart) {
            startEvent(event.eventId());
        }
        
        // 检查需要结束的事件
        List<TimedEvent> toEnd = getActiveEvents().stream()
            .filter(e -> e.endTime().isBefore(now))
            .collect(Collectors.toList());
        
        for (TimedEvent event : toEnd) {
            endEvent(event.eventId());
        }
        
        // 检查即将结束的事件
        for (TimedEvent event : getActiveEvents()) {
            Duration remaining = event.remainingTime();
            
            // 1小时前通知
            if (remaining.toHours() == 1) {
                notifyEventEnding(event, 1);
            }
            
            // 10分钟前通知
            if (remaining.toMinutes() == 10) {
                notifyEventEnding(event, 10);
            }
            
            // 1分钟前通知
            if (remaining.toMinutes() == 1) {
                notifyEventEnding(event, 1);
            }
        }
    }
    
    @Override
    public void startEvent(UUID eventId) {
        TimedEvent event = getEvent(eventId).orElse(null);
        if (event == null) return;
        
        TimedEvent started = new TimedEvent(
            event.eventId(),
            event.eventKey(),
            event.name(),
            event.nameZh(),
            event.description(),
            event.descriptionZh(),
            event.category(),
            event.startTime(),
            event.endTime(),
            event.timezone(),
            event.recurrenceRule(),
            event.isRecurring(),
            event.priority(),
            EventStatus.ACTIVE,
            event.config(),
            event.statistics()
        );
        
        saveEvent(started);
        activeEventsCache.put(eventId, started);
        
        // 加载事件任务和掉落到缓存
        loadEventDataToCache(eventId);
        
        // 广播开始
        broadcastEventStart(started);
        
        // 发布事件
        eventBus.publish(new EventStartedEvent(started));
    }
    
    @Override
    public void endEvent(UUID eventId) {
        TimedEvent event = activeEventsCache.get(eventId);
        if (event == null) return;
        
        // 清理缓存
        activeEventsCache.remove(eventId);
        eventQuestsCache.remove(eventId);
        
        // 保存最终统计
        saveEventStatistics(event);
        
        // 广播结束
        broadcastEventEnd(event);
        
        // 发放未领取奖励
        distributeUnclaimedRewards(eventId);
        
        // 发布事件
        eventBus.publish(new EventEndedEvent(event));
        
        // 检查是否需要重复
        if (event.isRecurring() && event.recurrenceRule() != null) {
            scheduleNextOccurrence(event);
        }
    }
    
    private void broadcastEventStart(TimedEvent event) {
        String message = String.format(
            "\n" +
            " §e§l╔══════════════════════════════════════╗\n" +
            " §e§l║        🎉 %s 开始！🎉                 ║\n" +
            " §e§l╠══════════════════════════════════════╣\n" +
            " §e§l║  %s\n" +
            " §e§l║  持续时间: %s\n" +
            " §e§l║  参与方式: %s\n" +
            " §e§l╚══════════════════════════════════════╝\n",
            event.nameZh() != null ? event.nameZh() : event.name(),
            event.descriptionZh() != null ? event.descriptionZh() : event.description(),
            formatDuration(Duration.between(event.startTime(), event.endTime())),
            "输入 /event 查看详情"
        );
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(Component.text(message));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
        }
    }
    
    // ===== 事件监听: 自动触发任务进度 =====
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        for (TimedEvent timedEvent : getActiveEvents()) {
            List<EventQuest> quests = eventQuestsCache.get(timedEvent.eventId());
            if (quests == null) continue;
            
            for (EventQuest quest : quests) {
                if (quest.objectiveType() == ObjectiveType.COLLECT &&
                    quest.objectiveTarget().equals(event.getBlock().getType().name())) {
                    
                    updateProgress(event.getPlayer().getUniqueId(), quest.questId(), 1);
                }
            }
        }
    }
    
    @EventHandler
    public void onEntityKill(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        
        for (TimedEvent timedEvent : getActiveEvents()) {
            List<EventQuest> quests = eventQuestsCache.get(timedEvent.eventId());
            if (quests == null) continue;
            
            for (EventQuest quest : quests) {
                if (quest.objectiveType() == ObjectiveType.KILL) {
                    String target = quest.objectiveTarget();
                    EntityType entityType = EntityType.valueOf(target);
                    
                    for (Entity entity : event.getEntity().getWorld()
                            .getEntitiesByClasses(entityType.getEntityClass())) {
                        // 检查是否是目标实体
                        if (event.getEntity().getType() == entityType) {
                            updateProgress(killer.getUniqueId(), quest.questId(), 1);
                        }
                    }
                }
            }
        }
    }
    
    @EventHandler
    public void onDropCheck(PlayerAttemptPickupItemEvent event) {
        // 检查事件掉落
        List<EventDrop> drops = checkDrops(
            event.getPlayer().getUniqueId(),
            new DropSource(DropSourceType.MOB, event.getEntity().getType().name())
        );
        
        for (EventDrop drop : drops) {
            // 给予掉落物品
            event.getPlayer().getInventory().addItem(drop.item());
            
            // 播放特效
            event.getPlayer().playSound(
                event.getPlayer().getLocation(), 
                Sound.ENTITY_PLAYER_LEVELUP, 
                0.5f, 
                1.5f
            );
            
            // 发送通知
            sendDropNotification(event.getPlayer(), drop);
        }
    }
}
```

---

## 3. 命令设计

### /event 命令
```
/event list              - 查看当前活动事件
/event info <事件ID>     - 查看事件详情
/event quests            - 查看事件任务
/event quest <任务ID>    - 查看任务详情
/event progress          - 查看我的任务进度
/event claim <任务ID>    - 领取任务奖励

/event shop              - 打开事件商店
/event shop <事件ID>     - 查看特定事件商店

/event leaderboard <事件ID> - 查看事件排行榜
/event rewards           - 查看我的事件积分

# 管理命令
/admin event create <配置> - 创建事件
/admin event start <ID>   - 启动事件
/admin event end <ID>     - 结束事件
/admin event quest add    - 添加任务
```

### EventCommand.java 核心逻辑
```java
public class EventCommand implements CommandExecutor {
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                showEventList(player);
            } else {
                sender.sendMessage("用法: /event list");
            }
            return true;
        }
        
        String sub = args[0].toLowerCase();
        
        return switch (sub) {
            case "list" -> showEventList(sender);
            case "info" -> showEventInfo(sender, args);
            case "quests", "quest" -> showQuests(sender);
            case "progress" -> showProgress(sender);
            case "claim" -> claimReward(sender, args);
            case "shop" -> openShop(sender, args);
            case "leaderboard", "lb" -> showLeaderboard(sender, args);
            default -> { sendHelp(sender); yield true; }
        };
    }
    
    private void showEventList(CommandSender sender) {
        List<TimedEvent> events = timedEventService.getActiveEvents();
        
        sender.sendMessage(Component.text("\n§6§l═══ 当前活动事件 ═══\n", NamedTextColor.GOLD));
        
        if (events.isEmpty()) {
            sender.sendMessage(Component.text("当前没有进行中的活动事件", NamedTextColor.GRAY));
        } else {
            for (TimedEvent event : events) {
                Duration remaining = event.remainingTime();
                String timeStr = remaining.toDays() > 0 ? 
                    remaining.toDays() + "天" : 
                    remaining.toHours() + "小时";
                
                String categoryIcon = switch (event.category()) {
                    case FESTIVAL -> "🎊";
                    case SEASONAL -> "🌿";
                    case ANNIVERSARY -> "🎂";
                    case COLLABORATION -> "🤝";
                    case SPECIAL -> "⭐";
                };
                
                sender.sendMessage(Component.text()
                    .append(Component.text(categoryIcon + " ", NamedTextColor.WHITE))
                    .append(Component.text(event.nameZh() != null ? event.nameZh() : event.name(), NamedTextColor.YELLOW))
                    .append(Component.text(" [" + timeStr + "]", NamedTextColor.GRAY))
                );
            }
        }
        
        sender.sendMessage(Component.text("\n输入 /event info <ID> 查看详情\n", NamedTextColor.GRAY));
    }
    
    private void showQuests(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("仅玩家可用");
            return;
        }
        
        List<TimedEvent> events = timedEventService.getActiveEvents();
        
        sender.sendMessage(Component.text("\n§6§l═══ 活动任务 ═══\n", NamedTextColor.GOLD));
        
        for (TimedEvent event : events) {
            sender.sendMessage(Component.text()
                .append(Component.text("§6§l【" + event.name() + "】", NamedTextColor.GOLD))
            );
            
            List<EventQuest> quests = getQuestsForEvent(event.eventId());
            for (EventQuest quest : quests) {
                PlayerQuestProgress progress = timedEventService.getQuestProgress(
                    player.getUniqueId(), quest.questId()
                );
                
                boolean completed = progress != null && progress.isComplete();
                boolean claimed = progress != null && progress.rewardClaimed();
                
                String status = completed ? (claimed ? "§a[已领取]§r" : "§e[可领取]§r") : 
                                progress != null ? "§7[进行中]§r" : "§7[未开始]§r";
                
                sender.sendMessage(Component.text()
                    .append(Component.text(status + " ", NamedTextColor.WHITE))
                    .append(Component.text(quest.title(), NamedTextColor.YELLOW))
                    .append(Component.text(" (" + (progress != null ? progress.currentProgress() : 0) + 
                        "/" + quest.objectiveCount() + ")", NamedTextColor.GRAY))
                );
            }
            
            sender.sendMessage(Component.text(""));
        }
    }
}
```

---

## 4. 配置文件

### config/timed-events.yml
```yaml
# 限时事件系统配置

# 全局设置
global:
  # 最大同时进行的事件数
  max-concurrent-events: 5
  # 默认事件时区
  timezone: Asia/Shanghai
  # 事件检查间隔(秒)
  check-interval: 60

# 通知设置
notifications:
  # 事件开始通知
  event-start: true
  # 事件即将结束通知(hours): true/false
  event-ending:
    24: true
    12: true
    6: true
    1: true
    0.5: true
  # 任务完成通知
  quest-complete: true
  # 掉落通知
  drop-notification: true
  # 掉落通知消息
  drop-message: "§d✨ 活动掉落: §6{item} §7x{amount}"

# 奖励倍率
reward-multipliers:
  festival:
    experience: 2.0
    coin: 1.5
    drop: 1.5
  seasonal:
    experience: 1.5
    coin: 1.2
    drop: 1.2
  anniversary:
    experience: 3.0
    coin: 2.0
    drop: 2.0
  special:
    experience: 2.0
    coin: 1.5
    drop: 2.0

# 排行榜设置
leaderboard:
  # 排行榜类型
  types:
    - "quest_complete"    # 任务完成数
    - "drops_collected"   # 掉落收集
    - "score"            # 积分
  # 显示前N名
  display-top: 10
  # 更新间隔(秒)
  update-interval: 300

# 事件模板
event-templates:
  festival:
    icon: PLAY_BUTTON
    title-color: GOLD
    border-color: YELLOW
    particle-effect: firework
  
  seasonal:
    icon: SAPLING
    title-color: GREEN
    border-color: DARK_GREEN
    particle-effect: happy_villager
  
  anniversary:
    icon: CAKE
    title-color: LIGHT_PURPLE
    border-color: PURPLE
    particle-effect: totem_of_undying

# 预设事件(示例)
preset-events:
  - key: spring_festival
    name: 春节庆典
    category: FESTIVAL
    duration-days: 14
    quests:
      - objective: COLLECT
        target: RED_MUSHROOM
        count: 100
        reward: "10000 coins"
    drops:
      - source: MOB
        mob: ZOMBIE
        item: LUCKY_COIN
        chance: 0.01
        rarity: RARE

  - key: summer_event
    name: 夏日嘉年华
    category: SEASONAL
    duration-days: 30
    reward-multiplier:
      experience: 2.0
      coin: 1.5
```

---

## 5. 事件系统

```java
// EventStartedEvent - 事件开始时触发
public record EventStartedEvent(TimedEvent event) {}

// EventEndedEvent - 事件结束时触发
public record EventEndedEvent(TimedEvent event) {}

// QuestCompletedEvent - 任务完成时触发
public record QuestCompletedEvent(PlayerQuestProgress progress) {}

// RewardClaimedEvent - 奖励领取时触发
public record RewardClaimedEvent(UUID playerId, UUID questId, List<ItemStack> rewards) {}

// EventDropReceivedEvent - 活动掉落时触发
public record EventDropReceivedEvent(UUID playerId, EventDrop drop) {}
```

---

## 6. 实施计划

### Phase 1: 基础框架 (2天)
- 数据库表
- 事件管理
- 事件状态检查

### Phase 2: 任务系统 (2天)
- 任务定义和进度
- 奖励发放
- 每日重置

### Phase 3: 掉落系统 (1-2天)
- 掉落检测
- 概率计算
- 限制检查

### Phase 4: 商店和排行榜 (1-2天)
- 事件商店
- 积分系统
- 排行榜

### Phase 5: UI和通知 (1天)
- 命令界面
- 通知系统
- 活动面板

### 总工时: 约 8-10 人天