package dev.starcore.starcore.event.random.effect;

import dev.starcore.starcore.event.random.EventEffect;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.treasury.TreasuryService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 国家效果
 * 对国家层面的属性和状态产生影响
 */
public class NationEffect implements EventEffect {

    public enum EffectType {
        MORALE_CHANGE,        // 士气变化
        DEFENSE_BONUS,       // 防御加成
        COMBAT_BONUS,        // 战斗加成
        ECONOMY_BONUS,       // 经济加成
        DAMAGE_OVER_TIME,    // 持续损失
        BUILDING_DAMAGE,     // 建筑损坏
        RESOURCE_DRAIN,      // 资源消耗
        REPUTATION_CHANGE    // 声望变化
    }

    private final JavaPlugin plugin;
    private NationService nationService;
    private TreasuryService treasuryService;

    private final EffectType effectType;
    private final double value;
    private final int duration;

    // 使用实例变量存储临时状态（替代 nation.data()）
    private static final Map<UUID, NationTempState> nationStates = new ConcurrentHashMap<>();

    public NationEffect(JavaPlugin plugin, NationService nationService,
                       EffectType effectType, double value, int duration) {
        this(plugin, nationService, effectType, value, duration, null);
    }

    public NationEffect(JavaPlugin plugin, NationService nationService,
                       EffectType effectType, double value, int duration, String message) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.effectType = effectType;
        this.value = value;
        this.duration = duration;
    }

    public void setNationService(NationService nationService) {
        this.nationService = nationService;
    }

    public void setTreasuryService(TreasuryService treasuryService) {
        this.treasuryService = treasuryService;
    }

    @Override
    public boolean apply(Player player, Location location) {
        if (nationService == null || player == null) {
            return false;
        }

        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            return false;
        }

        return applyNationEffect(nationOpt.get(), player);
    }

    /**
     * 对指定国家应用效果
     */
    public boolean applyToNation(UUID nationId) {
        if (nationService == null) {
            return false;
        }

        Optional<Nation> nationOpt = nationService.nationById(NationId.of(nationId));
        if (nationOpt.isEmpty()) {
            return false;
        }

        return applyNationEffect(nationOpt.get(), null);
    }

    private boolean applyNationEffect(Nation nation, Player triggerPlayer) {
        switch (effectType) {
            case MORALE_CHANGE:
                applyMoraleChange(nation);
                return true;

            case DEFENSE_BONUS:
                applyDefenseBonus(nation);
                return true;

            case COMBAT_BONUS:
                applyCombatBonus(nation);
                return true;

            case DAMAGE_OVER_TIME:
                applyDamageOverTime(nation);
                return true;

            case BUILDING_DAMAGE:
                applyBuildingDamage(nation);
                return true;

            case RESOURCE_DRAIN:
                applyResourceDrain(nation);
                return true;

            case REPUTATION_CHANGE:
                applyReputationChange(nation);
                return true;

            default:
                return false;
        }
    }

    /**
     * 士气变化
     */
    private void applyMoraleChange(Nation nation) {
        NationTempState state = getOrCreateState(nation.id());
        double currentMorale = state.morale;
        double newMorale = Math.max(0, Math.min(100, currentMorale + value));
        state.morale = newMorale;

        String status = value >= 0 ? "提升" : "下降";
        plugin.getLogger().info(String.format("国家 %s 士气 %s: %.1f%% -> %.1f%%",
            nation.name(), status, currentMorale, newMorale));
    }

    /**
     * 防御加成
     */
    private void applyDefenseBonus(Nation nation) {
        NationTempState state = getOrCreateState(nation.id());
        state.defenseBonus += value;

        if (duration > 0) {
            state.defenseBonusExpire = System.currentTimeMillis() + (duration * 1000L);
        }

        plugin.getLogger().info(String.format("国家 %s 获得 %.0f%% 防御加成 (持续 %d 秒)",
            nation.name(), value * 100, duration));
    }

    /**
     * 战斗加成
     */
    private void applyCombatBonus(Nation nation) {
        NationTempState state = getOrCreateState(nation.id());
        state.combatBonus += value;

        if (duration > 0) {
            state.combatBonusExpire = System.currentTimeMillis() + (duration * 1000L);
        }

        plugin.getLogger().info(String.format("国家 %s 获得 %.0f%% 战斗加成 (持续 %d 秒)",
            nation.name(), value * 100, duration));
    }

    /**
     * 持续损失（用于围城效果）
     */
    private void applyDamageOverTime(Nation nation) {
        if (duration > 0) {
            NationTempState state = getOrCreateState(nation.id());
            state.underSiege = true;
            state.siegeDamageRate = value;
            state.siegeExpire = System.currentTimeMillis() + (duration * 1000L);

            // 广播围城开始
            String msg = String.format("§c⚔️ 【围城警报】%s 正在遭受围攻！每分钟损失 %.1f%% 资源！",
                nation.name(), value);
            broadcastToNation(nation, msg);

            // 调度定时扣减效果
            scheduleSiegeDamage(nation.id().value(), duration);
        }
    }

    /**
     * 调度围城伤害
     */
    private void scheduleSiegeDamage(UUID nationId, int durationInSeconds) {
        int intervalTicks = 1200; // 1分钟 = 1200 ticks
        int maxTicks = durationInSeconds * 20;
        int[] elapsed = {0};

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            elapsed[0] += intervalTicks;
            if (elapsed[0] >= maxTicks) {
                // 围城结束
                endSiege(nationId);
                return;
            }

            // 执行伤害
            Optional<Nation> nationOpt = nationService.nationById(NationId.of(nationId));
            NationTempState state = nationStates.get(nationId);
            if (nationOpt.isEmpty() || state == null || !state.underSiege) {
                return; // 围城已结束
            }

            Nation nation = nationOpt.get();
            // 扣除资源（基于损失率）
            double damageRate = state != null ? state.siegeDamageRate : value;
            applySiegeTick(nation, damageRate);

        }, intervalTicks, intervalTicks);
    }

    private void applySiegeTick(Nation nation, double damageRate) {
        // 简化的围城伤害逻辑：扣除经济
        if (treasuryService != null) {
            BigDecimal balance = treasuryService.balance(nation.id());
            double loss = balance.doubleValue() * (damageRate / 100.0);
            treasuryService.withdraw(nation.id(), BigDecimal.valueOf(loss));
        }

        // 士气下降
        NationTempState state = getOrCreateState(nation.id());
        state.morale = Math.max(0, state.morale - 1.0);

        // 广播状态
        String msg = String.format("§c⚔️ 【围城】%s 正在遭受攻击！资源 -%.1f%%, 士气 -1",
            nation.name(), damageRate);
        broadcastToNation(nation, msg);
    }

    private void endSiege(UUID nationId) {
        Optional<Nation> nationOpt = nationService.nationById(NationId.of(nationId));
        if (nationOpt.isPresent()) {
            Nation nation = nationOpt.get();
            NationTempState state = nationStates.get(nationId);
            if (state != null) {
                state.underSiege = false;
                state.siegeDamageRate = 0;
                state.siegeExpire = 0;
            }

            broadcastToNation(nation, "§a⚔️ 【围城结束】蛮族撤退了！");
        }
    }

    /**
     * 建筑损坏
     */
    private void applyBuildingDamage(Nation nation) {
        NationTempState state = getOrCreateState(nation.id());
        state.buildingsDamaged = true;
        state.buildingDamageLevel = (int) value;

        plugin.getLogger().info(String.format("国家 %s 建筑受损，等级: %d", nation.name(), (int) value));
    }

    /**
     * 资源消耗
     */
    private void applyResourceDrain(Nation nation) {
        NationTempState state = getOrCreateState(nation.id());
        state.resourceDrainActive = true;
        state.resourceDrainRate = value;

        plugin.getLogger().info(String.format("国家 %s 资源消耗增加 %.0f%%", nation.name(), value * 100));
    }

    /**
     * 声望变化
     */
    private void applyReputationChange(Nation nation) {
        NationTempState state = getOrCreateState(nation.id());
        double currentRep = state.reputation;
        double newRep = Math.max(0, Math.min(100, currentRep + value));
        state.reputation = newRep;

        plugin.getLogger().info(String.format("国家 %s 声望变化: %.1f -> %.1f", nation.name(), currentRep, newRep));
    }

    /**
     * 向国家成员广播消息
     */
    private void broadcastToNation(Nation nation, String message) {
        if (message == null) return;

        for (var member : nation.members()) {
            Player player = Bukkit.getPlayer(member.playerId());
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }

        // 也要通知创始人
        Player founder = Bukkit.getPlayer(nation.founderId());
        if (founder != null && founder.isOnline()) {
            founder.sendMessage("§6[创始人] " + message);
        }
    }

    /**
     * 获取或创建国家的临时状态
     */
    private NationTempState getOrCreateState(NationId nationId) {
        return nationStates.computeIfAbsent(nationId.value(),
            k -> new NationTempState());
    }

    /**
     * 国家临时状态（存储在内存中）
     */
    private static class NationTempState {
        double morale = 100.0;
        double defenseBonus = 0.0;
        long defenseBonusExpire = 0;
        double combatBonus = 0.0;
        long combatBonusExpire = 0;
        boolean underSiege = false;
        double siegeDamageRate = 0;
        long siegeExpire = 0;
        boolean buildingsDamaged = false;
        int buildingDamageLevel = 0;
        boolean resourceDrainActive = false;
        double resourceDrainRate = 0;
        double reputation = 50.0;
    }

    @Override
    public String toString() {
        return String.format("NationEffect{type=%s, value=%.2f, duration=%d}", effectType, value, duration);
    }

    @Override
    public String getType() {
        return "nation";
    }

    @Override
    public String getDescription() {
        return String.format("国家效果 [类型=%s, 数值=%.2f, 持续=%ds]", effectType, value, duration);
    }
}
