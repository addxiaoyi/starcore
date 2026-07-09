package dev.starcore.starcore.module.satellite.listener;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.diplomacy.DiplomacyRelation;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.satellite.SatelliteService;
import dev.starcore.starcore.module.satellite.SatelliteService.SatelliteDefenseStatus;
import dev.starcore.starcore.module.war.WarService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 卫星国事件监听器
 * 处理卫星国相关的游戏事件
 */
public final class SatelliteListener implements Listener {

    private final SatelliteService satelliteService;
    private final NationService nationService;
    private final Optional<DiplomacyService> diplomacyService;
    private final Optional<WarService> warService;
    private final MessageService messages;

    // 独立宣言冷却记录
    private final Map<UUID, Long> independenceCooldowns = new ConcurrentHashMap<>();
    // 独立宣言冷却时间（毫秒）: 24小时
    private static final long INDEPENDENCE_COOLDOWN_MS = 24 * 60 * 60 * 1000L;

    public SatelliteListener(
        SatelliteService satelliteService,
        NationService nationService,
        Optional<DiplomacyService> diplomacyService,
        Optional<WarService> warService,
        MessageService messages
    ) {
        this.satelliteService = satelliteService;
        this.nationService = nationService;
        this.diplomacyService = diplomacyService;
        this.warService = warService;
        this.messages = messages;
    }

    /**
     * 处理玩家攻击事件
     * 如果攻击者是宗主国成员，保护其卫星国免受攻击
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = null;
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        }

        if (attacker == null) {
            return;
        }

        Optional<Nation> victimNationOpt = nationService.nationOf(victim.getUniqueId());
        Optional<Nation> attackerNationOpt = nationService.nationOf(attacker.getUniqueId());

        if (victimNationOpt.isEmpty() || attackerNationOpt.isEmpty()) {
            return;
        }

        Nation victimNation = victimNationOpt.get();
        Nation attackerNation = attackerNationOpt.get();

        // 检查是否攻击了宗主国（需要保护卫星国）
        // 如果受害者是宗主国成员，检查攻击者是否是卫星国成员
        if (satelliteService.hasRelation(victimNation.id(), attackerNation.id())) {
            // 攻击者是受害者的卫星国，不能攻击宗主国
            SatelliteDefenseStatus defense = satelliteService.getDefenseStatus(attackerNation.id());
            if (defense.isProtected()) {
                event.setCancelled(true);
                attacker.sendMessage(Component.text(
                    "不能攻击宗主国成员！你的国家受 " + victimNation.name() + " 的保护。",
                    NamedTextColor.RED
                ));
            }
        }

        // 检查是否攻击了卫星国（需要保护卫星国）
        // 如果受害者是卫星国成员，检查攻击者是否是宗主国
        if (satelliteService.hasRelation(attackerNation.id(), victimNation.id())) {
            // 攻击者是受害者的宗主国，不能攻击自己的卫星国
            event.setCancelled(true);
            attacker.sendMessage(Component.text(
                "不能攻击自己的卫星国成员！",
                NamedTextColor.RED
            ));
        }

        // 检查卫星国成员是否攻击宗主国成员
        Optional<NationId> victimSuzerainOpt = satelliteService.suzerainOf(victimNation.id());
        if (victimSuzerainOpt.isPresent() && victimSuzerainOpt.get().equals(attackerNation.id())) {
            // 受害者是卫星国成员，攻击者是其宗主国
            event.setCancelled(true);
            attacker.sendMessage(Component.text(
                "不能攻击自己的卫星国成员！",
                NamedTextColor.RED
            ));
        }

        // 检查宗主国成员是否攻击卫星国成员
        Optional<NationId> attackerSuzerainOpt = satelliteService.suzerainOf(attackerNation.id());
        if (attackerSuzerainOpt.isPresent() && attackerSuzerainOpt.get().equals(victimNation.id())) {
            // 攻击者是卫星国成员，受害者是其宗主国
            event.setCancelled(true);
            attacker.sendMessage(Component.text(
                "不能攻击宗主国成员！",
                NamedTextColor.RED
            ));
        }
    }

    /**
     * 处理玩家移动事件
     * 显示进入卫星国领土的消息
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // 只检查跨方块移动
        if (isSameBlock(event.getFrom(), event.getTo())) {
            return;
        }

        Player player = event.getPlayer();
        Optional<Nation> playerNationOpt = nationService.nationOf(player.getUniqueId());

        if (playerNationOpt.isEmpty()) {
            return;
        }

        Nation playerNation = playerNationOpt.get();

        // 检查玩家是否正在进入宗主国领土
        Optional<NationId> suzerainOpt = satelliteService.suzerainOf(playerNation.id());
        if (suzerainOpt.isPresent()) {
            NationId suzerainId = suzerainOpt.get();
            Optional<Nation> suzerainNationOpt = nationService.nationById(suzerainId);

            if (suzerainNationOpt.isPresent()) {
                // 检查当前位置是否在宗主国领土内
                suzerainNationOpt.get().capitalLocation();
                // 这里可以添加领土检测逻辑
                // 目前简化处理
            }
        }
    }

    /**
     * 处理玩家退出事件
     * 清理冷却记录
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 清理玩家相关的临时数据
        // 独立宣言冷却不需要清理，因为是基于国家而非玩家的
    }

    /**
     * 检查两个国家是否处于战争状态
     */
    private boolean isAtWar(NationId nation1, NationId nation2) {
        if (warService.isPresent()) {
            return !warService.get().activeWarsOf(nation1).stream()
                .filter(wr -> wr.left().equals(nation2) || wr.right().equals(nation2))
                .toList()
                .isEmpty();
        }

        if (diplomacyService.isPresent()) {
            return diplomacyService.get().relationBetween(nation1, nation2) == DiplomacyRelation.WAR;
        }

        return false;
    }

    /**
     * 检查宗主国是否允许卫星国独立
     */
    public boolean canGainIndependence(UUID playerId) {
        Optional<Nation> nationOpt = nationService.nationOf(playerId);
        if (nationOpt.isEmpty()) {
            return false;
        }

        Nation nation = nationOpt.get();
        Optional<NationId> suzerainOpt = satelliteService.suzerainOf(nation.id());
        if (suzerainOpt.isEmpty()) {
            return false;
        }

        SatelliteDefenseStatus status = satelliteService.getDefenseStatus(nation.id());
        if (!status.isProtected()) {
            return false;
        }

        // 检查冷却时间
        Long lastAttempt = independenceCooldowns.get(playerId);
        if (lastAttempt != null && System.currentTimeMillis() - lastAttempt < INDEPENDENCE_COOLDOWN_MS) {
            return false;
        }

        return true;
    }

    /**
     * 记录独立宣言尝试
     */
    public void recordIndependenceAttempt(UUID playerId) {
        independenceCooldowns.put(playerId, System.currentTimeMillis());
    }

    /**
     * 获取独立宣言剩余冷却时间（秒）
     */
    public long getIndependenceCooldownRemaining(UUID playerId) {
        Long lastAttempt = independenceCooldowns.get(playerId);
        if (lastAttempt == null) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - lastAttempt;
        return Math.max(0, (INDEPENDENCE_COOLDOWN_MS - elapsed) / 1000);
    }

    /**
     * 检查两点是否为同一方块
     */
    private boolean isSameBlock(org.bukkit.Location from, org.bukkit.Location to) {
        return from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ()
            && from.getWorld().equals(to.getWorld());
    }
}