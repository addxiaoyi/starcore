package dev.starcore.starcore.module.army.fatigue;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.fatigue.event.FatigueLevelChangedEvent;
import dev.starcore.starcore.module.army.fatigue.event.PlayerFatigueChangedEvent;
import dev.starcore.starcore.module.army.fatigue.event.PlayerForcedRestEvent;
import dev.starcore.starcore.module.army.fatigue.model.FatigueLevel;
import dev.starcore.starcore.module.army.fatigue.model.FatigueType;
import dev.starcore.starcore.module.army.fatigue.model.PlayerFatigue;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 疲劳度核心服务
 * 负责管理玩家的疲劳度数据、状态更新和效果应用
 */
public final class FatigueService {
    private static final String PERSISTENCE_NAMESPACE = "fatigue";
    private static final String FATIGUE_STATE_FILE = "player_fatigue.dat";

    private final Plugin plugin;
    private final MessageService messages;
    private final PersistenceService persistenceService;
    private final StarCoreEventBus eventBus;
    private final FatigueConfig config;

    // 玩家疲劳度数据（内存中）
    private final ConcurrentHashMap<UUID, PlayerFatigue> fatigueData = new ConcurrentHashMap<>();
    // 上次位置记录（用于旅行疲劳计算）
    private final ConcurrentHashMap<UUID, Location> lastLocations = new ConcurrentHashMap<>();
    // 强制休息中的玩家
    private final ConcurrentHashMap<UUID, Long> forcedRestUntil = new ConcurrentHashMap<>();
    // 在线玩家上次更新时间
    private final ConcurrentHashMap<UUID, Long> lastUpdateTime = new ConcurrentHashMap<>();

    public FatigueService(
        Plugin plugin,
        MessageService messages,
        PersistenceService persistenceService,
        StarCoreEventBus eventBus,
        FatigueConfig config
    ) {
        this.plugin = plugin;
        this.messages = messages;
        this.persistenceService = persistenceService;
        this.eventBus = eventBus;
        this.config = config;

        // 如果有持久化服务，加载已有数据
        if (persistenceService != null) {
            loadFatigueData();
        }

        startPeriodicTasks();
    }

    // ==================== Public API ====================

    /**
     * 获取玩家疲劳度数据
     */
    public Optional<PlayerFatigue> getPlayerFatigue(UUID playerId) {
        return Optional.ofNullable(fatigueData.get(playerId));
    }

    /**
     * 获取或创建玩家疲劳度数据
     */
    public PlayerFatigue getOrCreatePlayerFatigue(UUID playerId) {
        return fatigueData.computeIfAbsent(playerId, PlayerFatigue::new);
    }

    /**
     * 添加体力疲劳
     */
    public void addPhysicalFatigue(Player player, int amount) {
        if (!config.enabled() || amount <= 0) return;

        PlayerFatigue fatigue = getOrCreatePlayerFatigue(player.getUniqueId());
        FatigueLevel previousLevel = fatigue.level();
        int previousValue = fatigue.physicalFatigue();

        fatigue.addPhysicalFatigue(amount);

        fireFatigueChangedEvent(player, FatigueType.PHYSICAL, previousValue, fatigue.physicalFatigue(), amount);
        checkLevelChange(player, fatigue, previousLevel);
        checkForcedRest(player, fatigue);
        persistPlayerFatigue(player.getUniqueId());
    }

    /**
     * 添加精神疲劳
     */
    public void addMentalFatigue(Player player, int amount) {
        if (!config.enabled() || amount <= 0) return;

        PlayerFatigue fatigue = getOrCreatePlayerFatigue(player.getUniqueId());
        FatigueLevel previousLevel = fatigue.level();
        int previousValue = fatigue.mentalFatigue();

        fatigue.addMentalFatigue(amount);

        fireFatigueChangedEvent(player, FatigueType.MENTAL, previousValue, fatigue.mentalFatigue(), amount);
        checkLevelChange(player, fatigue, previousLevel);
        checkForcedRest(player, fatigue);
        persistPlayerFatigue(player.getUniqueId());
    }

    /**
     * 添加战斗疲劳
     */
    public void addCombatFatigue(Player player, int amount) {
        if (!config.enabled() || amount <= 0) return;

        PlayerFatigue fatigue = getOrCreatePlayerFatigue(player.getUniqueId());
        FatigueLevel previousLevel = fatigue.level();
        int previousValue = fatigue.combatFatigue();

        fatigue.addCombatFatigue(amount);

        fireFatigueChangedEvent(player, FatigueType.COMBAT, previousValue, fatigue.combatFatigue(), amount);
        checkLevelChange(player, fatigue, previousLevel);
        checkForcedRest(player, fatigue);
        persistPlayerFatigue(player.getUniqueId());
    }

    /**
     * 添加旅行疲劳
     */
    public void addTravelFatigue(Player player, int amount) {
        if (!config.enabled() || amount <= 0) return;

        PlayerFatigue fatigue = getOrCreatePlayerFatigue(player.getUniqueId());
        FatigueLevel previousLevel = fatigue.level();
        int previousValue = fatigue.travelFatigue();

        fatigue.addTravelFatigue(amount);

        fireFatigueChangedEvent(player, FatigueType.TRAVEL, previousValue, fatigue.travelFatigue(), amount);
        checkLevelChange(player, fatigue, previousLevel);
        checkForcedRest(player, fatigue);
        persistPlayerFatigue(player.getUniqueId());
    }

    /**
     * 恢复玩家疲劳
     */
    public void rest(Player player) {
        if (!config.enabled()) return;

        PlayerFatigue fatigue = getOrCreatePlayerFatigue(player.getUniqueId());
        fatigue.rest();

        fireFatigueChangedEvent(player, FatigueType.PHYSICAL, 0, 0, 0);
        persistPlayerFatigue(player.getUniqueId());
    }

    /**
     * 恢复玩家疲劳（指定类型和数量）
     */
    public void rest(Player player, FatigueType type, int amount) {
        if (!config.enabled() || amount <= 0) return;

        PlayerFatigue fatigue = getOrCreatePlayerFatigue(player.getUniqueId());
        int previousValue = switch (type) {
            case PHYSICAL -> fatigue.physicalFatigue();
            case MENTAL -> fatigue.mentalFatigue();
            case COMBAT -> fatigue.combatFatigue();
            case TRAVEL -> fatigue.travelFatigue();
        };

        fatigue.rest(type, amount);

        fireFatigueChangedEvent(player, type, previousValue, switch (type) {
            case PHYSICAL -> fatigue.physicalFatigue();
            case MENTAL -> fatigue.mentalFatigue();
            case COMBAT -> fatigue.combatFatigue();
            case TRAVEL -> fatigue.travelFatigue();
        }, -amount);
        persistPlayerFatigue(player.getUniqueId());
    }

    /**
     * 使用休息物品
     */
    public boolean useRestItem(Player player) {
        if (!config.enabled()) return false;

        PlayerFatigue fatigue = getOrCreatePlayerFatigue(player.getUniqueId());
        int recovery = config.restItemRecoveryAmount();
        fatigue.rest(recovery);

        persistPlayerFatigue(player.getUniqueId());
        return true;
    }

    /**
     * 检查玩家是否处于强制休息中
     */
    public boolean isInForcedRest(UUID playerId) {
        Long until = forcedRestUntil.get(playerId);
        if (until == null) return false;

        if (System.currentTimeMillis() >= until) {
            forcedRestUntil.remove(playerId);
            return false;
        }
        return true;
    }

    /**
     * 获取强制休息剩余时间（秒）
     */
    public int getForcedRestRemaining(UUID playerId) {
        Long until = forcedRestUntil.get(playerId);
        if (until == null) return 0;

        long remaining = (until - System.currentTimeMillis()) / 1000;
        return remaining > 0 ? (int) remaining : 0;
    }

    /**
     * 玩家加入时调用
     */
    public void onPlayerJoin(Player player) {
        UUID playerId = player.getUniqueId();

        // 确保有疲劳数据
        PlayerFatigue fatigue = getOrCreatePlayerFatigue(playerId);
        lastUpdateTime.put(playerId, System.currentTimeMillis());

        // 显示状态消息
        if (config.showJoinMessage() && config.enabled()) {
            showFatigueStatus(player, fatigue);
        }
    }

    /**
     * 玩家移动时调用（计算旅行疲劳）
     */
    public void onPlayerMove(Player player) {
        if (!config.enabled()) return;

        UUID playerId = player.getUniqueId();
        Location current = player.getLocation();
        Location last = lastLocations.get(playerId);

        if (last != null && current.getWorld().equals(last.getWorld())) {
            double distance = current.distance(last);
            if (distance >= config.travelFatigueCheckInterval() / 20.0) {
                // 计算需要增加的旅行疲劳
                int fatigueToAdd = (int) (distance / (config.travelFatigueCheckInterval() / 20.0) * config.travelAccumulationRate());
                if (fatigueToAdd > 0) {
                    addTravelFatigue(player, fatigueToAdd);
                }
            }
        }

        lastLocations.put(playerId, current.clone());
    }

    /**
     * 玩家攻击时计算攻击力惩罚
     */
    public double getAttackModifier(UUID playerId) {
        if (!config.enabled()) return 1.0;

        PlayerFatigue fatigue = getPlayerFatigue(playerId).orElse(null);
        if (fatigue == null) return 1.0;

        return fatigue.getAttackPenalty();
    }

    /**
     * 获取防御力惩罚
     */
    public double getDefenseModifier(UUID playerId) {
        if (!config.enabled()) return 1.0;

        PlayerFatigue fatigue = getPlayerFatigue(playerId).orElse(null);
        if (fatigue == null) return 1.0;

        return fatigue.getDefensePenalty();
    }

    /**
     * 获取移动速度惩罚
     */
    public double getSpeedModifier(UUID playerId) {
        if (!config.enabled()) return 1.0;

        PlayerFatigue fatigue = getPlayerFatigue(playerId).orElse(null);
        if (fatigue == null) return 1.0;

        return fatigue.getSpeedPenalty();
    }

    /**
     * 获取经验惩罚
     */
    public double getExpModifier(UUID playerId) {
        if (!config.enabled()) return 1.0;

        PlayerFatigue fatigue = getPlayerFatigue(playerId).orElse(null);
        if (fatigue == null) return 1.0;

        return fatigue.getExpPenalty();
    }

    /**
     * 获取配置
     */
    public FatigueConfig getConfig() {
        return config;
    }

    /**
     * 重置玩家所有疲劳度
     */
    public void resetFatigue(UUID playerId) {
        PlayerFatigue fatigue = getOrCreatePlayerFatigue(playerId);
        fatigue.resetAll();
        persistPlayerFatigue(playerId);
    }

    /**
     * 添加所有类型疲劳（管理员用）
     */
    public void addFatigue(UUID playerId, int amount) {
        if (!config.enabled() || amount <= 0) return;
        PlayerFatigue fatigue = getOrCreatePlayerFatigue(playerId);
        fatigue.addPhysicalFatigue(amount);
        fatigue.addMentalFatigue(amount);
        fatigue.addCombatFatigue(amount);
        fatigue.addTravelFatigue(amount);
        persistPlayerFatigue(playerId);
    }

    /**
     * 减少所有类型疲劳（管理员用）
     */
    public void reduceFatigue(UUID playerId, int amount) {
        if (!config.enabled() || amount <= 0) return;
        PlayerFatigue fatigue = getOrCreatePlayerFatigue(playerId);
        fatigue.rest(amount);
        persistPlayerFatigue(playerId);
    }

    /**
     * 获取玩家总体疲劳度
     */
    public int getFatigue(UUID playerId) {
        PlayerFatigue fatigue = getPlayerFatigue(playerId).orElse(null);
        return fatigue != null ? fatigue.overallFatigue() : 0;
    }

    /**
     * 获取玩家疲劳等级
     */
    public FatigueLevel getFatigueLevel(UUID playerId) {
        PlayerFatigue fatigue = getPlayerFatigue(playerId).orElse(null);
        return fatigue != null ? fatigue.level() : FatigueLevel.FRESH;
    }

    /**
     * 获取玩家疲劳惩罚（攻击力惩罚）
     */
    public double getFatiguePenalty(UUID playerId) {
        PlayerFatigue fatigue = getPlayerFatigue(playerId).orElse(null);
        return fatigue != null ? fatigue.getAttackPenalty() : 1.0;
    }

    /**
     * 获取疲劳统计数据
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("totalPlayers", fatigueData.size());
        stats.put("criticalCount", fatigueData.values().stream().filter(f -> f.level() == FatigueLevel.CRITICAL).count());
        stats.put("exhaustedCount", fatigueData.values().stream().filter(f -> f.level() == FatigueLevel.EXHAUSTED).count());
        stats.put("averageFatigue", fatigueData.values().stream().mapToInt(PlayerFatigue::overallFatigue).average().orElse(0));
        return stats;
    }

    // ==================== Private Methods ====================

    private void fireFatigueChangedEvent(Player player, FatigueType type, int previousValue, int newValue, int change) {
        if (eventBus != null) {
            PlayerFatigueChangedEvent event = new PlayerFatigueChangedEvent(
                player.getUniqueId(),
                player.getName(),
                type,
                previousValue,
                newValue,
                change
            );
            eventBus.publish(event);
        }
    }

    private void checkLevelChange(Player player, PlayerFatigue fatigue, FatigueLevel previousLevel) {
        FatigueLevel newLevel = fatigue.level();
        if (previousLevel != newLevel && eventBus != null) {
            FatigueLevelChangedEvent event = new FatigueLevelChangedEvent(
                player.getUniqueId(),
                player.getName(),
                previousLevel,
                newLevel,
                fatigue
            );
            eventBus.publish(event);

            // 发送等级变化消息
            if (config.showLevelUpMessage() && previousLevel.ordinal() < newLevel.ordinal()) {
                sendLevelUpMessage(player, newLevel);
            }
        }
    }

    private void checkForcedRest(Player player, PlayerFatigue fatigue) {
        if (!config.forcedRestEnabled()) return;
        if (isInForcedRest(player.getUniqueId())) return;

        if (fatigue.needsForcedRest() && fatigue.overallFatigue() >= config.forcedRestThreshold()) {
            // 触发强制休息
            long until = System.currentTimeMillis() + config.forcedRestDuration() * 1000L;
            forcedRestUntil.put(player.getUniqueId(), until);

            if (eventBus != null) {
                PlayerForcedRestEvent event = new PlayerForcedRestEvent(
                    player.getUniqueId(),
                    player.getName(),
                    config.forcedRestDuration(),
                    fatigue.overallFatigue()
                );
                eventBus.publish(event);
            }

            // 发送消息
            player.sendMessage(net.kyori.adventure.text.Component.text(
                messages.format("fatigue.forced-rest",
                    config.forcedRestDuration()
                ),
                net.kyori.adventure.text.format.NamedTextColor.RED
            ));
        }
    }

    private void sendLevelUpMessage(Player player, FatigueLevel newLevel) {
        String color = newLevel.colorCode();
        String message = messages.format(fatigueLevelChangeKey(newLevel),
            newLevel.displayName()
        );

        player.sendMessage(net.kyori.adventure.text.Component.text(
            "[" + newLevel.displayName() + "] " + newLevel.description(),
            net.kyori.adventure.text.format.NamedTextColor.YELLOW
        ));

        // 临界警告
        if (config.showCriticalWarning() && newLevel == FatigueLevel.CRITICAL) {
            player.sendMessage(net.kyori.adventure.text.Component.text(
                messages.format("fatigue.critical-warning"),
                net.kyori.adventure.text.format.NamedTextColor.RED
            ));
        }
    }

    private String fatigueLevelChangeKey(FatigueLevel level) {
        return "fatigue.level-change." + level.name().toLowerCase();
    }

    private void showFatigueStatus(Player player, PlayerFatigue fatigue) {
        player.sendMessage(net.kyori.adventure.text.Component.text(""));
        player.sendMessage(net.kyori.adventure.text.Component.text(
            "=== " + messages.format("fatigue.status-title") + " ===",
            net.kyori.adventure.text.format.NamedTextColor.GOLD
        ));
        player.sendMessage(net.kyori.adventure.text.Component.text(
            messages.format("fatigue.status.overall", fatigue.overallFatigue()),
            net.kyori.adventure.text.format.NamedTextColor.GRAY
        ));
        player.sendMessage(net.kyori.adventure.text.Component.text(
            messages.format("fatigue.status.level", fatigue.level().displayName()),
            net.kyori.adventure.text.format.NamedTextColor.GRAY
        ));
        player.sendMessage(net.kyori.adventure.text.Component.text(
            messages.format("fatigue.status.physical", fatigue.physicalFatigue()),
            net.kyori.adventure.text.format.NamedTextColor.GRAY
        ));
        player.sendMessage(net.kyori.adventure.text.Component.text(
            messages.format("fatigue.status.mental", fatigue.mentalFatigue()),
            net.kyori.adventure.text.format.NamedTextColor.GRAY
        ));
        player.sendMessage(net.kyori.adventure.text.Component.text(
            messages.format("fatigue.status.combat", fatigue.combatFatigue()),
            net.kyori.adventure.text.format.NamedTextColor.GRAY
        ));
        player.sendMessage(net.kyori.adventure.text.Component.text(
            messages.format("fatigue.status.travel", fatigue.travelFatigue()),
            net.kyori.adventure.text.format.NamedTextColor.GRAY
        ));
        player.sendMessage(net.kyori.adventure.text.Component.text(""));
    }

    // ==================== Periodic Tasks ====================

    private void startPeriodicTasks() {
        // 在线疲劳累积任务
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                UUID playerId = player.getUniqueId();

                // 检查强制休息
                if (isInForcedRest(playerId)) {
                    continue;
                }

                PlayerFatigue fatigue = fatigueData.get(playerId);
                if (fatigue == null) continue;

                // 累积疲劳
                fatigue.addPhysicalFatigue(config.onlinePhysicalAccumulation());
                fatigue.addMentalFatigue(config.onlineMentalAccumulation());

                // 恢复疲劳
                fatigue.rest(config.baseRecoveryPerMinute());

                persistPlayerFatigue(playerId);
            }
        }, 20L * config.onlineAccumulationInterval() / 60,
           20L * config.onlineAccumulationInterval() / 60);

        // ActionBar 更新任务
        if (config.showActionBar()) {
            plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    updateActionBar(player);
                }
            }, 20L, 20L * config.actionBarUpdateInterval() / 20);
        }
    }

    private void updateActionBar(Player player) {
        if (!config.showActionBar()) return;

        PlayerFatigue fatigue = fatigueData.get(player.getUniqueId());
        if (fatigue == null) return;

        // 构建 ActionBar 文本
        String color = fatigue.level().colorCode();
        String bar = String.format("%s体力: %d | 精神: %d | 战斗: %d | 旅行: %d | 总体: %d%%",
            color,
            fatigue.physicalFatigue(),
            fatigue.mentalFatigue(),
            fatigue.combatFatigue(),
            fatigue.travelFatigue(),
            fatigue.overallFatigue()
        );

        player.sendActionBar(net.kyori.adventure.text.Component.text(bar));
    }

    // ==================== Persistence ====================

    private void loadFatigueData() {
        try {
            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, FATIGUE_STATE_FILE);
            for (String key : props.stringPropertyNames()) {
                try {
                    UUID playerId = UUID.fromString(key);
                    String[] values = props.getProperty(key).split(",");
                    if (values.length >= 8) {
                        PlayerFatigue fatigue = new PlayerFatigue(
                            playerId,
                            Integer.parseInt(values[0]),
                            Integer.parseInt(values[1]),
                            Integer.parseInt(values[2]),
                            Integer.parseInt(values[3]),
                            Long.parseLong(values[4]),
                            Long.parseLong(values[5]),
                            Long.parseLong(values[6])
                        );
                        fatigueData.put(playerId, fatigue);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load fatigue for key " + key + ": " + e.getMessage());
                }
            }
            plugin.getLogger().info("Loaded fatigue data for " + fatigueData.size() + " players");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load fatigue data: " + e.getMessage());
        }
    }

    private void persistPlayerFatigue(UUID playerId) {
        if (persistenceService == null) return;

        PlayerFatigue fatigue = fatigueData.get(playerId);
        if (fatigue == null) return;

        try {
            String key = playerId.toString();
            String value = String.format("%d,%d,%d,%d,%d,%d,%d",
                fatigue.physicalFatigue(),
                fatigue.mentalFatigue(),
                fatigue.combatFatigue(),
                fatigue.travelFatigue(),
                fatigue.lastActivityTime(),
                fatigue.totalPlayTime(),
                fatigue.lastRestTime()
            );

            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, FATIGUE_STATE_FILE);
            props.setProperty(key, value);
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, FATIGUE_STATE_FILE, props);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to persist fatigue for " + playerId + ": " + e.getMessage());
        }
    }

    /**
     * 保存所有数据
     */
    public void saveAll() {
        if (persistenceService == null) return;

        try {
            var props = new java.util.Properties();
            for (Map.Entry<UUID, PlayerFatigue> entry : fatigueData.entrySet()) {
                PlayerFatigue f = entry.getValue();
                String value = String.format("%d,%d,%d,%d,%d,%d,%d",
                    f.physicalFatigue(),
                    f.mentalFatigue(),
                    f.combatFatigue(),
                    f.travelFatigue(),
                    f.lastActivityTime(),
                    f.totalPlayTime(),
                    f.lastRestTime()
                );
                props.setProperty(entry.getKey().toString(), value);
            }
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, FATIGUE_STATE_FILE, props);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save all fatigue data: " + e.getMessage());
        }
    }

    /**
     * 关闭服务时保存所有数据
     */
    public void shutdown() {
        saveAll();
    }
}
