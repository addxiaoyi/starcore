package dev.starcore.starcore.module.army.weather.listener;

import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.module.army.ArmyService;
import dev.starcore.starcore.module.army.model.ArmyUnit;
import dev.starcore.starcore.module.army.weather.WeatherTacticsService;
import dev.starcore.starcore.module.army.weather.model.ArmyWeatherState;
import dev.starcore.starcore.module.army.weather.event.ArmyWeatherTacticsEvent;
import dev.starcore.starcore.module.army.weather.event.WeatherBattleAdvantageEvent;
import dev.starcore.starcore.module.army.weather.event.WeatherTacticsUpgradedEvent;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.weather.WeatherControlService;
import dev.starcore.starcore.module.weather.event.WeatherChangeEvent;
import dev.starcore.starcore.module.weather.event.WeatherResourceEffectEvent;
import dev.starcore.starcore.module.weather.model.WeatherType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.weather.ThunderChangeEvent;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 天气战术监听器
 * 处理与天气相关的游戏事件，更新军队状态并应用战术效果
 */
public final class WeatherTacticsListener implements Listener {

    private final WeatherTacticsService tacticsService;
    private final WeatherControlService weatherService;
    private final ArmyService armyService;
    private final NationService nationService;
    private final StarCoreEventBus eventBus;

    // 玩家天气冷却记录
    private final Map<UUID, Long> playerWeatherCooldowns = new ConcurrentHashMap<>();
    // 冷却时间（毫秒）
    private static final long PLAYER_WEATHER_NOTIFY_COOLDOWN = 60_000L; // 1分钟

    // 移动事件检测间隔
    private static final int MOVE_CHECK_INTERVAL = 20; // 每秒检查一次

    public WeatherTacticsListener(
        WeatherTacticsService tacticsService,
        WeatherControlService weatherService,
        @Nullable ArmyService armyService,
        NationService nationService,
        StarCoreEventBus eventBus
    ) {
        this.tacticsService = tacticsService;
        this.weatherService = weatherService;
        this.armyService = armyService;
        this.nationService = nationService;
        this.eventBus = eventBus;

        // 订阅事件
        if (eventBus != null) {
            eventBus.subscribe(ArmyWeatherTacticsEvent.class, this::onArmyWeatherTactics);
            eventBus.subscribe(WeatherTacticsUpgradedEvent.class, this::onTacticsUpgraded);
            eventBus.subscribe(WeatherChangeEvent.class, this::onWeatherChangedEvent);
            eventBus.subscribe(WeatherResourceEffectEvent.class, this::onWeatherResourceEffect);
        }
    }

    // ==================== 事件处理 ====================

    /**
     * 处理军队天气战术事件
     */
    private void onArmyWeatherTactics(ArmyWeatherTacticsEvent event) {
        // 可以在这里添加额外的处理逻辑，如通知玩家、更新UI等
    }

    /**
     * 处理战术升级事件
     */
    private void onTacticsUpgraded(WeatherTacticsUpgradedEvent event) {
        // 战术升级时可以通知相关玩家
    }

    /**
     * 处理天气变化事件
     */
    private void onWeatherChanged(WeatherChangeEvent event) {
        // 更新所有受影响军队的天气状态
        if (event.worldName() != null) {
            updateArmiesWeatherForWorld(event.worldName(), event.newWeather());
        }
    }

    /**
     * 处理天气资源效果事件
     */
    private void onWeatherResourceEffect(WeatherResourceEffectEvent event) {
        // 更新相关军队的天气状态
        if (event.worldName() != null) {
            updateArmiesWeatherForWorld(event.worldName(), event.weather());
        }
    }

    // ==================== 游戏事件监听 ====================

    /**
     * 处理天气变化事件（内部事件总线）
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWeatherChangedEvent(dev.starcore.starcore.module.weather.event.WeatherChangeEvent event) {
        // 只在变为非晴天时更新
        if (event.newWeather() == dev.starcore.starcore.module.weather.model.WeatherType.CLEAR) {
            return;
        }

        // 获取世界名称
        String worldName = event.worldName();

        // 查找控制这个世界的国家
        var worldState = weatherService.getWorldWeatherState(worldName);
        if (worldState != null && worldState.getControlledByNation() != null) {
            // 国家控制的天气，更新军队状态
            updateArmiesWeatherForNation(worldState.getControlledByNation().value(), event.newWeather());
        }
    }

    /**
     * 监听雷暴变化事件（Bukkit事件）
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onThunderChange(ThunderChangeEvent event) {
        if (!event.toThunderState()) {
            return; // 雷暴结束
        }

        String worldName = event.getWorld().getName();
        var worldState = weatherService.getWorldWeatherState(worldName);

        if (worldState != null && worldState.getControlledByNation() != null) {
            // 雷暴天气更新
            updateArmiesWeatherForNation(worldState.getControlledByNation().value(), WeatherType.THUNDER);
        }
    }

    /**
     * 监听玩家移动事件 - 用于天气通知
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // 只在玩家真正移动时检查（同一方块内移动不触发）
        if (isSameBlock(event.getFrom(), event.getTo())) {
            return;
        }

        Player player = event.getPlayer();

        // 检查冷却
        long lastNotify = playerWeatherCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (System.currentTimeMillis() - lastNotify < PLAYER_WEATHER_NOTIFY_COOLDOWN) {
            return;
        }

        // 检查玩家是否在移动的军队附近
        Optional<ArmyUnit> nearbyArmy = findNearbyArmy(player);
        if (nearbyArmy.isPresent()) {
            ArmyUnit army = nearbyArmy.get();
            WeatherType currentWeather = getWeatherForLocation(event.getTo());

            // 更新军队天气状态
            tacticsService.getTacticsEffect(army.id()); // 触发状态更新

            // 记录冷却
            playerWeatherCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 更新世界中所有军队的天气状态
     */
    private void updateArmiesWeatherForWorld(String worldName, WeatherType weather) {
        // 获取该世界的所有军队
        // 这里需要与 ArmyService 集成
    }

    /**
     * 更新特定国家所有军队的天气状态
     */
    private void updateArmiesWeatherForNation(UUID nationId, WeatherType weather) {
        // 获取该国家的所有军队并更新天气状态
        // 需要与 ArmyService 集成
    }

    /**
     * 确定世界当前的天气类型
     */
    private WeatherType determineWeatherType(org.bukkit.World world) {
        if (!world.hasStorm()) {
            return WeatherType.CLEAR;
        }
        if (world.isThundering()) {
            return WeatherType.THUNDER;
        }
        // 根据环境判断是否为雪天（下界和天空不会下雪）
        if (world.getEnvironment() == org.bukkit.World.Environment.NORMAL) {
            // 检查温度（Y坐标较高时可能是雪）
            if (world.getHighestBlockAt(world.getSpawnLocation()).getY() > 100) {
                return WeatherType.SNOW;
            }
        }
        return WeatherType.RAIN;
    }

    /**
     * 获取位置的天气
     */
    private WeatherType getWeatherForLocation(org.bukkit.Location location) {
        if (location.getWorld() == null) {
            return WeatherType.CLEAR;
        }
        return determineWeatherType(location.getWorld());
    }

    /**
     * 查找附近可用的军队
     */
    private Optional<ArmyUnit> findNearbyArmy(Player player) {
        // 获取玩家国家的军队
        Optional<UUID> nationId = nationService.nationOf(player.getUniqueId())
            .map(n -> n.id().value());

        if (nationId.isEmpty()) {
            return Optional.empty();
        }

        List<ArmyUnit> armies = armyService.getNationArmies(nationId.get());
        org.bukkit.Location playerLoc = player.getLocation();

        return armies.stream()
            .filter(army -> army.location().getWorld().equals(playerLoc.getWorld()))
            .filter(army -> army.location().distance(playerLoc) < 50) // 50格内
            .findFirst();
    }

    /**
     * 检查两点是否为同一方块
     */
    private boolean isSameBlock(org.bukkit.Location from, org.bukkit.Location to) {
        if (from.getWorld() != to.getWorld()) {
            return false;
        }
        return from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ();
    }

    /**
     * 清理过期冷却记录
     */
    public void cleanupExpiredCooldowns() {
        long now = System.currentTimeMillis();
        playerWeatherCooldowns.entrySet().removeIf(
            entry -> now - entry.getValue() > PLAYER_WEATHER_NOTIFY_COOLDOWN * 2
        );
    }

    /**
     * 获取天气战斗修正
     */
    public WeatherBattleAdvantageEvent.WeatherBattleModifier getBattleModifier(
        UUID attackerId, UUID defenderId, WeatherType weather) {

        WeatherTacticsService.WeatherBattleModifier modifier =
            tacticsService.calculateBattleModifier(null, null, weather);

        return new WeatherBattleAdvantageEvent.WeatherBattleModifier(
            modifier.attackerBonus(),
            modifier.defenderBonus(),
            modifier.moraleModifier(),
            modifier.description()
        );
    }

    // E-056 修复: 玩家退出时清理天气战术冷却
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerWeatherCooldowns.remove(event.getPlayer().getUniqueId());
    }
}