package dev.starcore.starcore.foundation.animation.listener;

import dev.starcore.starcore.foundation.animation.ScreenShake;
import dev.starcore.starcore.foundation.animation.ScreenShakeManager;
import dev.starcore.starcore.module.war.event.WarDeclaredEvent;
import dev.starcore.starcore.module.war.event.WarStartedEvent;
import dev.starcore.starcore.module.war.event.WarEndedEvent;
import dev.starcore.starcore.war.War;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 战争事件震动监听器
 * 在宣战、战争开始、战争结束、战斗、死亡、爆炸等事件时触发屏幕震动
 */
public final class WarShakeListener implements Listener {
    private final Plugin plugin;

    // 统计信息
    private int totalWarDeclared = 0;
    private int totalWarStarted = 0;
    private int totalWarEnded = 0;
    private int totalKills = 0;
    private int totalDeaths = 0;

    // 追踪玩家的近期击杀数
    private final Map<UUID, Integer> recentKills = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastKillTime = new ConcurrentHashMap<>();
    private static final long KILL_COMBO_WINDOW = 10000; // 10秒内连杀

    public WarShakeListener(Plugin plugin) {
        this.plugin = plugin;
    }

    // ==================== 战争事件监听 ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWarDeclared(WarDeclaredEvent event) {
        War war = event.getWar();
        totalWarDeclared++;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // 为所有在线玩家播放宣战震动
            for (Player player : Bukkit.getOnlinePlayers()) {
                ScreenShake.shake(player, ScreenShakeManager.ShakeType.WAR_DECLARE);
            }

            // 获取国家名称
            String aggressorNation = getNationDisplayName(war.aggressor());
            String defenderNation = getNationDisplayName(war.defender());

            Component message = Component.text()
                .append(Component.text("[宣战] ", NamedTextColor.DARK_RED))
                .append(Component.text(aggressorNation, NamedTextColor.RED))
                .append(Component.text(" 向 ", NamedTextColor.GRAY))
                .append(Component.text(defenderNation, NamedTextColor.BLUE))
                .append(Component.text(" 宣战！", NamedTextColor.DARK_RED))
                .build();

            Bukkit.broadcast(message);

            // 显示副标题提示
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.showTitle(Title.title(
                    Component.text("战争警报!", NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, true),
                    Component.text(aggressorNation + " vs " + defenderNation, NamedTextColor.GRAY),
                    Title.Times.times(
                        Duration.ofMillis(300),
                        Duration.ofSeconds(3),
                        Duration.ofMillis(500)
                    )
                ));
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWarStarted(WarStartedEvent event) {
        War war = event.getWar();
        totalWarStarted++;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // 战争开始时为所有玩家播放剧烈震动
            for (Player player : Bukkit.getOnlinePlayers()) {
                ScreenShake.shake(player, ScreenShakeManager.ShakeType.HEAVY);
            }

            Component message = Component.text()
                .append(Component.text("[战争] ", NamedTextColor.RED))
                .append(Component.text(war.name(), NamedTextColor.GOLD))
                .append(Component.text(" 战争爆发！", NamedTextColor.RED))
                .build();

            Bukkit.broadcast(message);
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWarEnded(WarEndedEvent event) {
        War war = event.getWar();
        WarEndedEvent.WarEndReason reason = event.getReason();
        totalWarEnded++;

        // 根据结束原因决定震动类型
        ScreenShakeManager.ShakeType type;
        NamedTextColor color;
        String resultText;

        switch (reason) {
            case SURRENDER -> {
                type = ScreenShakeManager.ShakeType.VICTORY;
                color = NamedTextColor.GOLD;
                resultText = "胜利";
            }
            case PEACE_TREATY -> {
                type = ScreenShakeManager.ShakeType.LIGHT;
                color = NamedTextColor.GREEN;
                resultText = "和平协议";
            }
            case TIMEOUT, MAX_DURATION -> {
                type = ScreenShakeManager.ShakeType.MEDIUM;
                color = NamedTextColor.YELLOW;
                resultText = "超时结束";
            }
            case ADMIN_FORCE -> {
                type = ScreenShakeManager.ShakeType.ALERT;
                color = NamedTextColor.DARK_PURPLE;
                resultText = "强制终止";
            }
            default -> {
                type = ScreenShakeManager.ShakeType.MEDIUM;
                color = NamedTextColor.GRAY;
                resultText = "结束";
            }
        }

        final String finalResultText = resultText;
        final ScreenShakeManager.ShakeType finalType = type;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                ScreenShake.shake(player, finalType);
            }

            Component message = Component.text()
                .append(Component.text("[战争] ", color))
                .append(Component.text(war.name(), NamedTextColor.GOLD))
                .append(Component.text(" 战争" + finalResultText + "！", color))
                .build();

            Bukkit.broadcast(message);
        }, 1L);
    }

    // ==================== 战斗事件监听 ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        // 检查是否是暴击（伤害 > 8）
        if (event.getDamage() > 8.0) {
            // 受害者播放暴击震动
            ScreenShake.shake(victim, ScreenShakeManager.ShakeType.CRITICAL_HIT);

            // 如果攻击者是玩家，也给他播放打击震动
            if (event.getDamager() instanceof Player attacker) {
                ScreenShake.shake(attacker, ScreenShakeManager.ShakeType.HIT);
            }
        } else {
            // 普通打击
            ScreenShake.shake(victim, ScreenShakeManager.ShakeType.HIT);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        totalDeaths++;

        // 死亡震动
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            ScreenShake.shake(victim, ScreenShakeManager.ShakeType.DEATH);
        }, 1L);

        // 如果是被玩家击杀
        if (killer != null && killer != victim) {
            totalKills++;
            recordKill(killer);

            // 给击杀者播放连击震动
            int recentKillsCount = getRecentKills(killer.getUniqueId());
            if (recentKillsCount > 3) {
                ScreenShake.shake(killer, ScreenShakeManager.ShakeType.COMBO);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // 复活时轻微震动
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            ScreenShake.shake(player, ScreenShakeManager.ShakeType.LIGHT);
        }, 5L);
    }

    // ==================== 爆炸事件监听 ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) {
        Location loc = event.getLocation();
        double radius = event.blockList().size() / 5.0;

        // 根据爆炸规模决定震动类型
        ScreenShakeManager.ShakeType type;
        if (radius > 20) {
            type = ScreenShakeManager.ShakeType.NUCLEAR;
        } else if (radius > 10) {
            type = ScreenShakeManager.ShakeType.EXPLOSION;
        } else {
            type = ScreenShakeManager.ShakeType.TNT;
        }

        // 范围内玩家震动
        for (Player player : loc.getWorld().getPlayers()) {
            if (player.getLocation().distance(loc) <= radius + 10) {
                ScreenShake.shake(player, type);
            }
        }
    }

    // ==================== Boss/稀有生物生成监听 ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossSpawn(CreatureSpawnEvent event) {
        EntityType type = event.getEntityType();

        // Boss 生物生成
        if (type == EntityType.WITHER ||
            type == EntityType.ELDER_GUARDIAN ||
            type == EntityType.ENDER_DRAGON) {

            Location loc = event.getLocation();

            // 范围内所有玩家震动
            for (Player player : loc.getWorld().getPlayers()) {
                if (player.getLocation().distance(loc) <= 50) {
                    ScreenShake.shake(player, ScreenShakeManager.ShakeType.BOSS_APPEAR);
                }
            }

            // 广播消息
            String bossName = switch (type) {
                case WITHER -> "凋零";
                case ELDER_GUARDIAN -> "远古守卫";
                case ENDER_DRAGON -> "末影龙";
                default -> "Boss";
            };

            Bukkit.broadcast(Component.text("[警告] " + bossName + " 出现了！",
                NamedTextColor.DARK_RED));
        }
    }

    // ==================== 玩家退出监听（用于清理） ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 清理玩家的震动状态
        ScreenShake.stop(event.getPlayer());
        // 清理连杀数据
        UUID playerId = event.getPlayer().getUniqueId();
        recentKills.remove(playerId);
        lastKillTime.remove(playerId);
    }

    // ==================== 统计信息方法 ====================

    /**
     * 获取宣战次数
     */
    public int getTotalWarDeclared() {
        return totalWarDeclared;
    }

    /**
     * 获取战争开始次数
     */
    public int getTotalWarStarted() {
        return totalWarStarted;
    }

    /**
     * 获取战争结束次数
     */
    public int getTotalWarEnded() {
        return totalWarEnded;
    }

    /**
     * 获取总击杀数
     */
    public int getTotalKills() {
        return totalKills;
    }

    /**
     * 获取总死亡数
     */
    public int getTotalDeaths() {
        return totalDeaths;
    }

    /**
     * 重置统计
     */
    public void resetStats() {
        totalWarDeclared = 0;
        totalWarStarted = 0;
        totalWarEnded = 0;
        totalKills = 0;
        totalDeaths = 0;
        recentKills.clear();
        lastKillTime.clear();
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 追踪玩家的近期击杀数
     */
    private int getRecentKills(UUID playerId) {
        Long lastTime = lastKillTime.get(playerId);
        if (lastTime == null) return 0;

        long now = System.currentTimeMillis();
        if (now - lastTime > KILL_COMBO_WINDOW) {
            recentKills.remove(playerId);
            lastKillTime.remove(playerId);
            return 0;
        }

        return recentKills.getOrDefault(playerId, 0);
    }

    /**
     * 记录一次击杀
     */
    public void recordKill(Player killer) {
        UUID id = killer.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastTime = lastKillTime.get(id);

        if (lastTime != null && now - lastTime <= KILL_COMBO_WINDOW) {
            recentKills.merge(id, 1, Integer::sum);
        } else {
            recentKills.put(id, 1);
        }
        lastKillTime.put(id, now);
    }

    /**
     * 获取国家显示名称
     */
    private String getNationDisplayName(dev.starcore.starcore.module.nation.model.NationId nationId) {
        if (nationId == null) {
            return "未知";
        }
        // 优先使用名称缓存服务获取名称，如果不可用则使用 UUID 前8位
        return nationId.toString().substring(0, 8);
    }
}