package dev.starcore.starcore.event.random.effect;

import dev.starcore.starcore.event.random.EventEffect;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 围城效果
 * 管理国家围城状态的持续效果
 */
public class SiegeEffect implements EventEffect {

    public enum Action {
        START,      // 开始围城
        CANCEL,     // 取消围城
        DAMAGE,     // 造成伤害
        ESCALATE    // 升级围城
    }

    private final JavaPlugin plugin;
    private NationService nationService;
    private final Action action;
    private final double damagePerMinute;
    private final double moraleDrain;
    private final int duration;  // 持续时间（秒）

    // 活跃围城追踪（使用独立Map存储，不再依赖nation.data()）
    private static final Map<UUID, SiegeState> activeSieges = new ConcurrentHashMap<>();
    // 围城期间的士气（独立存储）
    private static final Map<UUID, Double> siegeMorale = new ConcurrentHashMap<>();

    public SiegeEffect(JavaPlugin plugin, NationService nationService,
                     Action action, double damagePerMinute, double moraleDrain, int duration) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.action = action;
        this.damagePerMinute = damagePerMinute;
        this.moraleDrain = moraleDrain;
        this.duration = duration;
    }

    public void setNationService(NationService nationService) {
        this.nationService = nationService;
    }

    @Override
    public boolean apply(Player player, Location location) {
        if (nationService == null) {
            plugin.getLogger().warning("NationService 不可用，无法执行围城效果");
            return false;
        }

        // 获取玩家所属国家
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            return false;
        }

        Nation nation = nationOpt.get();
        return applyToNation(nation.id().value());
    }

    /**
     * 对指定国家应用围城效果
     */
    public boolean applyToNation(UUID nationId) {
        if (nationService == null) {
            return false;
        }

        Optional<Nation> nationOpt = nationService.nationById(NationId.of(nationId));
        if (nationOpt.isEmpty()) {
            return false;
        }

        Nation nation = nationOpt.get();

        switch (action) {
            case START:
                return startSiege(nation);
            case CANCEL:
                return cancelSiege(nationId);
            case DAMAGE:
                return applySiegeDamage(nation);
            case ESCALATE:
                return escalateSiege(nation);
            default:
                return false;
        }
    }

    /**
     * 开始围城
     */
    private boolean startSiege(Nation nation) {
        SiegeState state = new SiegeState(
            nation.id().value(),
            damagePerMinute,
            moraleDrain,
            System.currentTimeMillis() + (duration * 1000L)
        );

        activeSieges.put(nation.id().value(), state);
        siegeMorale.put(nation.id().value(), 100.0);

        // 广播围城开始
        String msg = String.format(
            "§c⚔️ ════════════════════════════════════\n" +
            "§c  【紧急军情】蛮族大军围攻 %s！\n" +
            "§c  预计持续: %d 分钟\n" +
            "§c  每分钟损失: %.1f%% 经济, %.1f 士气\n" +
            "§c⚔️ ════════════════════════════════════",
            nation.name(), duration / 60, damagePerMinute, moraleDrain
        );

        broadcastToNation(nation, msg);

        // 调度围城周期
        scheduleSiegeTicks(nation.id().value(), duration);

        plugin.getLogger().info("国家 " + nation.name() + " 遭受围城，持续 " + duration + " 秒");
        return true;
    }

    /**
     * 取消围城
     */
    private boolean cancelSiege(UUID nationId) {
        if (!activeSieges.containsKey(nationId)) {
            return false;
        }

        activeSieges.remove(nationId);
        siegeMorale.remove(nationId);

        Optional<Nation> nationOpt = nationService.nationById(NationId.of(nationId));
        if (nationOpt.isPresent()) {
            Nation nation = nationOpt.get();
            broadcastToNation(nation, "§a⚔️ 【围城解除】蛮族撤退了！危机解除！");
        }

        return true;
    }

    /**
     * 应用围城伤害
     */
    private boolean applySiegeDamage(Nation nation) {
        SiegeState state = activeSieges.get(nation.id().value());
        if (state == null) {
            return false;
        }

        // 士气下降
        Double currentMorale = siegeMorale.get(nation.id().value());
        if (currentMorale == null) {
            currentMorale = 100.0;
        }
        siegeMorale.put(nation.id().value(), Math.max(0, currentMorale - state.moraleDrain));

        return true;
    }

    /**
     * 升级围城（增加压力）
     */
    private boolean escalateSiege(Nation nation) {
        SiegeState state = activeSieges.get(nation.id().value());
        if (state == null) {
            return false;
        }

        // 升级效果：伤害翻倍
        state.damageRate *= 1.5;
        state.moraleDrain *= 1.3;

        String msg = String.format(
            "§c⚔️ 【紧急】围城升级！%s 遭受更强攻势！",
            nation.name()
        );
        broadcastToNation(nation, msg);

        return true;
    }

    /**
     * 调度围城周期伤害
     */
    private void scheduleSiegeTicks(UUID nationId, int durationInSeconds) {
        int intervalTicks = 1200; // 1分钟
        int maxTicks = durationInSeconds * 20;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // 检查围城是否仍然活跃
            SiegeState state = activeSieges.get(nationId);
            if (state == null) {
                return; // 围城已结束
            }

            if (System.currentTimeMillis() > state.expireTime) {
                cancelSiege(nationId);
                return;
            }

            // 应用伤害
            Optional<Nation> nationOpt = nationService.nationById(NationId.of(nationId));
            if (nationOpt.isPresent()) {
                applySiegeDamage(nationOpt.get());
            }

            // 继续调度
            scheduleSiegeTicks(nationId, (int)((state.expireTime - System.currentTimeMillis()) / 1000));

        }, intervalTicks);
    }

    /**
     * 向国家成员广播
     */
    private void broadcastToNation(Nation nation, String message) {
        if (message == null) return;

        for (var member : nation.members()) {
            Player player = Bukkit.getPlayer(member.playerId());
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }

        // 通知创始人
        Player founder = Bukkit.getPlayer(nation.founderId());
        if (founder != null && founder.isOnline()) {
            founder.sendMessage("§6[创始人] " + message);
        }
    }

    /**
     * 获取当前活跃围城
     */
    public static Map<UUID, SiegeState> getActiveSieges() {
        return new HashMap<>(activeSieges);
    }

    /**
     * 检查国家是否处于围城状态
     */
    public static boolean isUnderSiege(UUID nationId) {
        return activeSieges.containsKey(nationId);
    }

    /**
     * 获取国家围城期间的士气
     */
    public static double getSiegeMorale(UUID nationId) {
        return siegeMorale.getOrDefault(nationId, 100.0);
    }

    /**
     * 围城状态
     */
    public static class SiegeState {
        public final UUID nationId;
        public double damageRate;
        public double moraleDrain;
        public final long startTime;
        public long expireTime;

        public SiegeState(UUID nationId, double damageRate, double moraleDrain, long expireTime) {
            this.nationId = nationId;
            this.damageRate = damageRate;
            this.moraleDrain = moraleDrain;
            this.startTime = System.currentTimeMillis();
            this.expireTime = expireTime;
        }

        public int getRemainingSeconds() {
            return (int) Math.max(0, (expireTime - System.currentTimeMillis()) / 1000);
        }
    }

    @Override
    public String toString() {
        return String.format("SiegeEffect{action=%s, damage=%.1f/min, morale=%.1f, duration=%ds}",
            action, damagePerMinute, moraleDrain, duration);
    }

    @Override
    public String getType() {
        return "siege";
    }

    @Override
    public String getDescription() {
        return String.format("围城效果 [行动=%s, 伤害=%.1f%%/分, 士气=%.1f/分, 持续=%ds]",
            action, damagePerMinute, moraleDrain, duration);
    }
}