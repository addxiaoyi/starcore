package dev.starcore.starcore.module.army.raid;
import java.util.Optional;

import dev.starcore.starcore.module.army.raid.model.*;
import dev.starcore.starcore.module.army.raid.event.RaidStartEvent;
import dev.starcore.starcore.module.army.raid.event.RaidEndEvent;
import dev.starcore.starcore.module.army.raid.event.RaidAlertEvent;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.economy.InternalEconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.nation.permission.NationPermission;
import dev.starcore.starcore.util.PermissionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 夜间突袭服务实现
 */
public final class NightRaidServiceImpl implements NightRaidService {
    private static final String PERSISTENCE_NAMESPACE = "nightraid";
    private static final String RAID_STATE_FILE = "raids.dat";
    private static final String COOLDOWN_FILE = "cooldowns.dat";

    private final Plugin plugin;
    private final NationService nationService;
    private final OnlinePlayerDirectory playerDirectory;
    private final MessageService messages;
    private final StarCoreEventBus eventBus;
    private final InternalEconomyService economyService;
    private final PersistenceService persistenceService;
    private final NightRaidConfig config;

    // 所有突袭（内存中）
    private final Map<UUID, Raid> raids = new ConcurrentHashMap<>();
    // 国家突袭索引
    private final Map<UUID, Set<UUID>> nationRaids = new ConcurrentHashMap<>();
    // 国家冷却记录
    private final Map<UUID, RaidCooldown> cooldowns = new ConcurrentHashMap<>();
    // 活跃警报
    private final Map<UUID, RaidAlert> alerts = new ConcurrentHashMap<>();
    // 玩家参与的突袭
    private final Map<UUID, UUID> playerRaids = new ConcurrentHashMap<>();

    // 突袭计数器（用于限制每个国家的突袭数量）
    private final Map<UUID, AtomicInteger> nationRaidCounts = new ConcurrentHashMap<>();

    public NightRaidServiceImpl(
        Plugin plugin,
        NationService nationService,
        OnlinePlayerDirectory playerDirectory,
        MessageService messages,
        StarCoreEventBus eventBus,
        InternalEconomyService economyService,
        ConfigurationSection configSection,
        PersistenceService persistenceService
    ) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.playerDirectory = playerDirectory;
        this.messages = messages;
        this.eventBus = eventBus;
        this.economyService = economyService;
        this.config = NightRaidConfig.fromConfig(configSection);
        this.persistenceService = persistenceService;

        // 加载数据
        loadData();

        // 启动定时任务
        startPeriodicTasks();
    }

    @Override
    public boolean isEnabled() {
        return config.allowNightOnly() && isWithinRaidWindow();
    }

    @Override
    public boolean isWithinRaidWindow() {
        if (!config.allowNightOnly()) {
            return true;
        }
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return hour >= config.nightStartHour() || hour < config.nightEndHour();
    }

    @Override
    public double calculateRaidCost(int participantCount) {
        return config.raidCostMultiplier() * participantCount;
    }

    @Override
    public boolean canRaid(NationId nationId) {
        // 检查冷却
        RaidCooldown cooldown = cooldowns.get(nationId.value());
        if (cooldown != null && !cooldown.canRaidAgain(config.cooldownHours())) {
            return false;
        }

        // 检查突袭数量限制
        int count = nationRaidCounts.getOrDefault(nationId.value(), new AtomicInteger(0)).get();
        return count < config.maxRaidsPerNation();
    }

    @Override
    public Raid createRaid(NationId attackerNationId, NationId targetNationId, Location raidLocation, UUID attackerId) {
        // 权限校验：必须是攻击方国家成员且有 LEADER 级别
        Player attackerPlayer = Bukkit.getPlayer(attackerId);
        if (attackerPlayer != null) {
            if (!PermissionUtil.hasNationPermission(attackerPlayer, attackerNationId.value(),
                    NationPermission.RAID_INITIATE, null)) {
                throw new SecurityException("你没有权限发动突袭");
            }
        } else {
            // 离线玩家降级校验
            var nationOpt = nationService.nationOf(attackerId);
            if (nationOpt.isEmpty() || !nationOpt.get().id().value().equals(attackerNationId.value())) {
                throw new SecurityException("你不是攻击方国家成员");
            }
            if (!nationOpt.get().founderId().equals(attackerId)) {
                throw new SecurityException("发动突袭需要 LEADER 权限");
            }
        }

        // 检查是否可以发起突袭
        String reason = canInitiateRaid(attackerNationId, targetNationId);
        if (reason != null) {
            throw new IllegalStateException(reason);
        }

        // 扣费
        if (economyService != null) {
            double cost = calculateRaidCost(1); // 基础费用
            if (cost > 0 && !economyService.withdraw(attackerId, java.math.BigDecimal.valueOf(cost))) {
                throw new IllegalStateException("突袭费用不足");
            }
        }

        // 创建突袭
        Raid raid = Raid.create(attackerNationId, targetNationId, raidLocation, attackerId);

        // 添加发起者为攻击者
        Nation attackerNation = nationService.nationById(attackerNationId).orElseThrow();
        String playerName = playerDirectory.displayName(attackerId).orElse("Unknown");
        raid.addAttacker(RaidParticipant.create(attackerId, playerName, attackerNationId));

        // 注册
        raids.put(raid.id(), raid);
        nationRaids.computeIfAbsent(attackerNationId.value(), k -> ConcurrentHashMap.newKeySet()).add(raid.id());
        nationRaids.computeIfAbsent(targetNationId.value(), k -> ConcurrentHashMap.newKeySet()).add(raid.id());
        playerRaids.put(attackerId, raid.id());
        nationRaidCounts.computeIfAbsent(attackerNationId.value(), k -> new AtomicInteger(0)).incrementAndGet();

        // 创建警报
        if (config.notifyTarget()) {
            RaidAlert alert = RaidAlert.create(targetNationId, attackerNationId, 1, formatLocation(raidLocation), config.preparationTimeSeconds());
            alerts.put(targetNationId.value(), alert);
            eventBus.publish(new RaidAlertEvent(alert, formatAlertMessage(alert)));
        }

        // 记录事件
        raid.addEvent(new RaidEvent(Instant.now(), RaidEventType.RAID_CREATED, attackerId, null,
            attackerNation.name() + " 对 " + nationService.nationById(targetNationId).map(Nation::name).orElse("Unknown") + " 发起了突袭"));

        // 发布开始事件
        eventBus.publish(new RaidStartEvent(raid, attackerNationId, targetNationId));

        // 安排突袭开始
        scheduleRaidStart(raid);

        // 持久化
        persistRaid(raid);

        return raid;
    }

    @Override
    public void joinRaid(UUID raidId, Player player, NationId nationId, boolean isAttacker) {
        Raid raid = raids.get(raidId);
        if (raid == null) {
            throw new IllegalArgumentException("Raid not found");
        }

        if (!raid.isPending()) {
            throw new IllegalStateException("Raid is not in preparation phase");
        }

        // 检查国家是否匹配
        Nation playerNation = nationService.nationById(nationId).orElseThrow();
        boolean correctSide = isAttacker
            ? nationId.equals(raid.attackerNationId())
            : nationId.equals(raid.targetNationId());

        if (!correctSide) {
            throw new IllegalStateException("Nation is not on the correct side");
        }

        // 检查参与者数量
        int count = isAttacker ? raid.attackerCount() : raid.defenderCount();
        if (count >= config.maxRaidParticipants()) {
            throw new IllegalStateException("Side is full");
        }

        // 添加参与者
        String playerName = playerDirectory.displayName(player.getUniqueId()).orElse(player.getName());
        RaidParticipant participant = RaidParticipant.create(player.getUniqueId(), playerName, nationId);

        if (isAttacker) {
            raid.addAttacker(participant);
            raid.addEvent(new RaidEvent(Instant.now(), RaidEventType.PLAYER_JOINED_ATTACKER, player.getUniqueId(), null, playerName + " 加入了攻击方"));
        } else {
            raid.addDefender(participant);
            raid.addEvent(new RaidEvent(Instant.now(), RaidEventType.PLAYER_JOINED_DEFENDER, player.getUniqueId(), null, playerName + " 加入了防御方"));
        }

        playerRaids.put(player.getUniqueId(), raidId);
        persistRaid(raid);
    }

    @Override
    public void leaveRaid(UUID raidId, Player player) {
        Raid raid = raids.get(raidId);
        if (raid == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        boolean wasAttacker = raid.isAttacker(playerId);
        boolean wasDefender = raid.isDefender(playerId);

        if (wasAttacker) {
            raid.removeAttacker(playerId);
            raid.addEvent(new RaidEvent(Instant.now(), RaidEventType.PLAYER_LEFT, playerId, null, player.getName() + " 离开了突袭"));
        } else if (wasDefender) {
            raid.removeDefender(playerId);
            raid.addEvent(new RaidEvent(Instant.now(), RaidEventType.PLAYER_LEFT, playerId, null, player.getName() + " 离开了突袭"));
        }

        playerRaids.remove(playerId);

        // 检查是否需要取消突袭
        if (raid.attackerCount() < config.minRaidParticipants() && raid.isPending()) {
            endRaid(raidId, "Not enough attackers");
        }

        persistRaid(raid);
    }

    @Override
    public void startRaid(UUID raidId) {
        Raid raid = raids.get(raidId);
        if (raid == null) {
            return;
        }

        if (!raid.isPending()) {
            return;
        }

        // 检查最低人数
        if (raid.attackerCount() < config.minRaidParticipants()) {
            endRaid(raidId, "Not enough attackers");
            return;
        }

        // 开始突袭
        raid.start();
        raid.addEvent(new RaidEvent(Instant.now(), RaidEventType.COMBAT_STARTED, null, null, "突袭战斗开始"));

        // 发布事件
        eventBus.publish(new RaidStartEvent(raid, raid.attackerNationId(), raid.targetNationId()));

        // 安排突袭结束
        scheduleRaidEnd(raid);

        persistRaid(raid);
    }

    @Override
    public void endRaid(UUID raidId, String reason) {
        Raid raid = raids.get(raidId);
        if (raid == null) {
            return;
        }

        // 确定结果
        RaidResult result = raid.determineResult();
        raid.end(reason);

        // 更新冷却
        RaidCooldown cooldown = cooldowns.get(raid.attackerNationId().value());
        if (cooldown == null) {
            cooldowns.put(raid.attackerNationId().value(), RaidCooldown.fresh(raid.attackerNationId()));
        } else {
            cooldowns.put(raid.attackerNationId().value(), cooldown.update());
        }

        // 清理玩家参与记录
        for (UUID playerId : playerRaids.keySet()) {
            if (playerRaids.get(playerId).equals(raidId)) {
                playerRaids.remove(playerId);
            }
        }

        // 减少计数
        AtomicInteger count = nationRaidCounts.get(raid.attackerNationId().value());
        if (count != null) {
            count.decrementAndGet();
        }

        // 发布结束事件
        boolean attackerVictory = result == RaidResult.ATTACKER_VICTORY;
        eventBus.publish(new RaidEndEvent(raid, raid.attackerNationId(), raid.targetNationId(), attackerVictory));

        // 移除警报
        alerts.remove(raid.targetNationId().value());

        persistRaid(raid);
        persistCooldowns();
    }

    @Override
    public Optional<Raid> getRaid(UUID raidId) {
        return Optional.ofNullable(raids.get(raidId));
    }

    @Override
    public List<Raid> getNationRaids(NationId nationId) {
        Set<UUID> raidIds = nationRaids.getOrDefault(nationId.value(), Collections.emptySet());
        return raidIds.stream()
            .map(raids::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    @Override
    public List<Raid> getRaidsNear(Location location, double radius) {
        return raids.values().stream()
            .filter(Raid::isActive)
            .filter(raid -> raid.location().getWorld().equals(location.getWorld()))
            .filter(raid -> raid.location().distance(location) <= radius)
            .collect(Collectors.toList());
    }

    @Override
    public Collection<Raid> getActiveRaids() {
        return raids.values().stream()
            .filter(Raid::isActive)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<RaidAlert> getLatestAlert(NationId nationId) {
        return Optional.ofNullable(alerts.get(nationId.value()));
    }

    @Override
    public void acknowledgeAlert(UUID alertId) {
        alerts.values().stream()
            .filter(a -> a.id().equals(alertId))
            .findFirst()
            .ifPresent(alert -> alerts.put(alert.targetNationId().value(), alert.acknowledge()));
    }

    @Override
    public NightRaidConfig getConfig() {
        return config;
    }

    @Override
    public Optional<RaidParticipant> getParticipant(UUID playerId) {
        UUID raidId = playerRaids.get(playerId);
        if (raidId == null) {
            return Optional.empty();
        }
        Raid raid = raids.get(raidId);
        if (raid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(raid.getParticipant(playerId));
    }

    @Override
    public List<Raid> getPlayerActiveRaids(UUID playerId) {
        UUID raidId = playerRaids.get(playerId);
        if (raidId == null) {
            return Collections.emptyList();
        }
        Raid raid = raids.get(raidId);
        if (raid == null || !raid.isActive()) {
            return Collections.emptyList();
        }
        return List.of(raid);
    }

    @Override
    public String canInitiateRaid(NationId attackerNationId, NationId targetNationId) {
        // 检查时间窗口
        if (!isWithinRaidWindow()) {
            return "突袭只能在夜间进行";
        }

        // 检查同一国家
        if (attackerNationId.equals(targetNationId)) {
            return "不能对自己发起突袭";
        }

        // 检查攻击方是否存在
        if (nationService.nationById(attackerNationId).isEmpty()) {
            return "攻击方国家不存在";
        }

        // 检查目标方是否存在
        if (nationService.nationById(targetNationId).isEmpty()) {
            return "目标国家不存在";
        }

        // 检查冷却
        if (!canRaid(attackerNationId)) {
            return "国家正在冷却中，请稍后再试";
        }

        // 检查突袭数量限制
        int count = nationRaidCounts.getOrDefault(attackerNationId.value(), new AtomicInteger(0)).get();
        if (count >= config.maxRaidsPerNation()) {
            return "已达到最大突袭数量限制";
        }

        return null; // 可以发起
    }

    @Override
    public void saveAll() {
        for (Raid raid : raids.values()) {
            persistRaid(raid);
        }
        persistCooldowns();
    }

    @Override
    public void shutdown() {
        saveAll();
    }

    // ==================== 私有方法 ====================

    private void startPeriodicTasks() {
        // 每分钟检查突袭状态
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            checkExpiredRaids();
        }, 20L * 60, 20L * 60);

        // 每5分钟保存一次
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            saveAll();
        }, 20L * 60 * 5, 20L * 60 * 5);
    }

    private void checkExpiredRaids() {
        for (Raid raid : raids.values()) {
            if (raid.isExpired() && raid.isActive()) {
                endRaid(raid.id(), "Raid expired");
            }
        }
    }

    private void scheduleRaidStart(Raid raid) {
        long delayTicks = config.preparationTimeSeconds() * 20L;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (raids.containsKey(raid.id())) {
                startRaid(raid.id());
            }
        }, delayTicks);
    }

    private void scheduleRaidEnd(Raid raid) {
        long delayTicks = config.raidDurationMinutes() * 60 * 20L;
        raid.setExpiresAt(Instant.now().plusSeconds(config.raidDurationMinutes() * 60L));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (raids.containsKey(raid.id()) && raid.isActive()) {
                endRaid(raid.id(), "Raid duration ended");
            }
        }, delayTicks);
    }

    private void loadData() {
        if (persistenceService == null) {
            return;
        }

        try {
            // 加载突袭数据
            var raidProps = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, RAID_STATE_FILE);
            for (String key : raidProps.stringPropertyNames()) {
                try {
                    // 简化加载：跳过详细加载，主要用于计数
                    plugin.getLogger().info("Found raid record: " + key);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load raid " + key + ": " + e.getMessage());
                }
            }

            // 加载冷却数据
            var cooldownProps = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, COOLDOWN_FILE);
            for (String key : cooldownProps.stringPropertyNames()) {
                try {
                    UUID nationId = UUID.fromString(key);
                    long lastRaid = Long.parseLong(cooldownProps.getProperty(key));
                    RaidCooldown cooldown = new RaidCooldown(
                        new NationId(nationId),
                        Instant.ofEpochSecond(lastRaid),
                        0
                    );
                    cooldowns.put(nationId, cooldown);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load cooldown " + key + ": " + e.getMessage());
                }
            }

            plugin.getLogger().info("Loaded raid data: " + raids.size() + " raids, " + cooldowns.size() + " cooldowns");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load raid data: " + e.getMessage());
        }
    }

    private void persistRaid(Raid raid) {
        if (persistenceService == null) {
            return;
        }

        try {
            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, RAID_STATE_FILE);
            String key = raid.id().toString();
            // 简化持久化：只存储基本信息和状态
            String value = String.format("%s|%s|%s|%s|%s",
                raid.attackerNationId().value(),
                raid.targetNationId().value(),
                raid.phase(),
                raid.status(),
                raid.startedAt() != null ? raid.startedAt().getEpochSecond() : 0);
            props.setProperty(key, value);
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, RAID_STATE_FILE, props);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to persist raid " + raid.id() + ": " + e.getMessage());
        }
    }

    private void persistCooldowns() {
        if (persistenceService == null) {
            return;
        }

        try {
            var props = new Properties();
            for (Map.Entry<UUID, RaidCooldown> entry : cooldowns.entrySet()) {
                props.setProperty(entry.getKey().toString(), String.valueOf(entry.getValue().lastRaidTime().getEpochSecond()));
            }
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, COOLDOWN_FILE, props);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to persist cooldowns: " + e.getMessage());
        }
    }

    private String formatLocation(Location location) {
        return String.format("%s (%d, %d, %d)",
            location.getWorld().getName(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ());
    }

    private String formatAlertMessage(RaidAlert alert) {
        String attackerName = nationService.nationById(alert.attackerNationId())
            .map(Nation::name)
            .orElse("Unknown");
        return String.format("警告：%s 正在对你的领土发起突袭！预计 %d 秒后开始。",
            attackerName, alert.warningSeconds());
    }
}
