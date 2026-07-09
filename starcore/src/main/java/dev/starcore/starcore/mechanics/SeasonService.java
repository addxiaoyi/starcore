package dev.starcore.starcore.mechanics;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

/**
 * 四季服务
 * 管理游戏世界的四季变化及其效果
 */
public class SeasonService implements Listener {

    private final JavaPlugin plugin;
    private final Map<String, Season> worldSeasons; // 每个世界的当前季节
    private final Map<String, SeasonEffect> worldEffects;

    // 配置项
    private boolean enabled = true;
    private int daysPerSeason = 7; // 每个季节持续7天
    private boolean autoChange = true; // 自动季节变化
    private long lastSeasonChange = 0;

    public SeasonService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.worldSeasons = new HashMap<>();
        this.worldEffects = new HashMap<>();
    }

    /**
     * 初始化服务
     */
    public void initialize() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // 为所有世界初始化季节
        for (World world : Bukkit.getWorlds()) {
            Season season = Season.fromGameDays(world.getFullTime() / 24000L, daysPerSeason);
            worldSeasons.put(world.getName(), season);
            worldEffects.put(world.getName(), new SeasonEffect(season));
        }

        // 启动季节检查任务
        if (autoChange) {
            Bukkit.getScheduler().runTaskTimer(plugin, this::checkSeasonChange, 200L, 200L);
        }

        // 启动效果应用任务（每10秒）
        Bukkit.getScheduler().runTaskTimer(plugin, this::applySeasonEffects, 200L, 200L);

        plugin.getLogger().info("四季系统已启用");
    }

    /**
     * 检查并更新季节变化
     */
    private void checkSeasonChange() {
        if (!enabled) return;

        long currentTime = System.currentTimeMillis();
        long timeSinceChange = currentTime - lastSeasonChange;

        // 检查每个世界
        for (World world : Bukkit.getWorlds()) {
            long days = world.getFullTime() / 24000L;
            Season newSeason = Season.fromGameDays(days, daysPerSeason);
            Season currentSeason = worldSeasons.get(world.getName());

            // 季节发生变化
            if (newSeason != currentSeason) {
                changeSeason(world, newSeason);
            }
        }
    }

    /**
     * 改变世界的季节
     */
    public void changeSeason(World world, Season newSeason) {
        Season oldSeason = worldSeasons.get(world.getName());
        worldSeasons.put(world.getName(), newSeason);

        // 创建新的季节效果
        SeasonEffect effect = new SeasonEffect(newSeason);
        worldEffects.put(world.getName(), effect);

        // 移除旧季节效果
        if (oldSeason != null) {
            SeasonEffect oldEffect = new SeasonEffect(oldSeason);
            for (Player player : world.getPlayers()) {
                oldEffect.removeFromPlayer(player);
            }
        }

        // 广播季节变化
        String message = String.format(
            "§6§l[四季系统] §r%s世界的季节已变更为 %s%s",
            world.getName(),
            newSeason.getColorCode(),
            newSeason.getDisplayName()
        );

        for (Player player : world.getPlayers()) {
            player.sendMessage(message);
            player.sendMessage(effect.getDescription());
        }

        lastSeasonChange = System.currentTimeMillis();
        plugin.getLogger().info(world.getName() + " 季节变更: " + newSeason.getDisplayName());
    }

    /**
     * 应用季节效果到所有玩家
     */
    private void applySeasonEffects() {
        if (!enabled) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            SeasonEffect effect = worldEffects.get(world.getName());

            if (effect != null) {
                effect.applyToPlayer(player);
            }
        }
    }

    /**
     * 玩家加入时应用季节效果
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();
        World world = player.getWorld();
        Season season = worldSeasons.get(world.getName());

        if (season != null) {
            player.sendMessage("§6[四季系统] §e当前季节: " + season.getColorCode() + season.getDisplayName());
        }
    }

    /**
     * 作物生长事件 - 应用季节加成
     */
    @EventHandler
    public void onBlockGrow(BlockGrowEvent event) {
        if (!enabled) return;

        World world = event.getBlock().getWorld();
        SeasonEffect effect = worldEffects.get(world.getName());

        if (effect != null && effect.hasModifier("crop_growth")) {
            double modifier = effect.getModifier("crop_growth");

            // 春季加速生长，冬季减缓生长
            if (modifier < 1.0 && ThreadLocalRandom.current().nextDouble() > modifier) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * 方块破坏事件 - 应用挖掘和收获加成
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();
        World world = player.getWorld();
        SeasonEffect effect = worldEffects.get(world.getName());

        if (effect == null) return;

        // 秋季收获加成
        if (effect.hasModifier("harvest_yield")) {
            String blockType = event.getBlock().getType().name();
            if (blockType.contains("CROPS") || blockType.contains("WHEAT") ||
                blockType.contains("CARROT") || blockType.contains("POTATO") ||
                blockType.contains("BEETROOT")) {

                double modifier = effect.getModifier("harvest_yield");
                // 通过设置方块掉落增加来模拟收获加成
                // 实际实现可能需要监听掉落事件
            }
        }
    }

    /**
     * 实体死亡事件 - 应用经验加成
     */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!enabled) return;
        if (event.getEntity().getKiller() == null) return;

        Player killer = event.getEntity().getKiller();
        World world = killer.getWorld();
        SeasonEffect effect = worldEffects.get(world.getName());

        if (effect != null && effect.hasModifier("experience_gain")) {
            double modifier = effect.getModifier("experience_gain");
            int originalExp = event.getDroppedExp();
            event.setDroppedExp((int) (originalExp * modifier));
        }
    }

    /**
     * 获取世界当前季节
     */
    public Season getSeason(World world) {
        return worldSeasons.getOrDefault(world.getName(), Season.SPRING);
    }

    /**
     * 获取世界的季节效果
     */
    public SeasonEffect getSeasonEffect(World world) {
        return worldEffects.get(world.getName());
    }

    /**
     * 手动设置世界季节
     */
    public void setSeason(World world, Season season) {
        changeSeason(world, season);
    }

    /**
     * 获取距离下次季节变化的剩余天数
     */
    public int getDaysUntilNextSeason(World world) {
        long days = world.getFullTime() / 24000L;
        int daysInSeason = (int) (days % daysPerSeason);
        return daysPerSeason - daysInSeason;
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        // 移除所有玩家的季节效果
        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            SeasonEffect effect = worldEffects.get(world.getName());
            if (effect != null) {
                effect.removeFromPlayer(player);
            }
        }

        worldSeasons.clear();
        worldEffects.clear();
    }

    // Getters and Setters

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setDaysPerSeason(int days) {
        this.daysPerSeason = days;
    }

    public int getDaysPerSeason() {
        return daysPerSeason;
    }

    public void setAutoChange(boolean autoChange) {
        this.autoChange = autoChange;
    }
}
