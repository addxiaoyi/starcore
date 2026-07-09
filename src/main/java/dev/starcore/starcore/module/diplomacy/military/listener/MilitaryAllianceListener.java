package dev.starcore.starcore.module.diplomacy.military.listener;

import dev.starcore.starcore.module.diplomacy.military.MilitaryAllianceService;
import dev.starcore.starcore.module.diplomacy.military.MilitaryAllianceService.PactType;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.war.WarService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Optional;

/**
 * 军事联盟事件监听器
 * 处理军事联盟相关的游戏事件
 */
public class MilitaryAllianceListener implements Listener {

    private final MilitaryAllianceService allianceService;
    private final NationService nationService;
    private final WarService warService;

    public MilitaryAllianceListener(
            MilitaryAllianceService allianceService,
            NationService nationService,
            WarService warService
    ) {
        this.allianceService = allianceService;
        this.nationService = nationService;
        this.warService = warService;
    }

    /**
     * 处理玩家伤害事件
     * 检查军事联盟保护机制
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // 获取攻击者和受害者
        Entity attacker = event.getDamager();
        Entity victim = event.getEntity();

        // 只处理玩家相关事件
        if (!(attacker instanceof Player) && !(victim instanceof Player)) {
            return;
        }

        // 获取国家ID
        Optional<NationId> attackerNation = getNationId(attacker);
        Optional<NationId> victimNation = getNationId(victim);

        if (attackerNation.isEmpty() || victimNation.isEmpty()) {
            return;
        }

        // 检查是否受军事联盟保护
        if (allianceService.isUnderProtection(attackerNation.get(), victimNation.get())) {
            // 如果受害者受保护且是防御同盟以上，通知攻击者
            if (attacker instanceof Player player) {
                player.sendMessage("§c该玩家正受到军事联盟保护（防御同盟）");
            }
        }
    }

    /**
     * 处理玩家死亡事件
     * 计算并应用防御加成
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Player killer = player.getKiller();

        if (killer == null) {
            return;
        }

        Optional<NationId> victimNation = getNationId(player);
        Optional<NationId> killerNation = getNationId(killer);

        if (victimNation.isEmpty() || killerNation.isEmpty()) {
            return;
        }

        // 检查是否有针对 killer 的军事联盟在保护 victim
        if (allianceService.isUnderProtection(killerNation.get(), victimNation.get())) {
            // 计算防御加成
            double defenseBonus = allianceService.getDefenseBonus(victimNation.get(), killerNation.get());

            // 应用防御加成（降低伤害）
            // 注意：这里只是通知，实际伤害减免需要在其他地方实现
            event.setDroppedExp((int) (event.getDroppedExp() * (1 - defenseBonus)));

            killer.sendMessage(String.format("§6[军事联盟] §c目标受军事联盟保护，减少经验掉落 %.0f%%", defenseBonus * 100));
        }
    }

    /**
     * 处理玩家加入事件
     * 通知玩家有关军事联盟状态
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());

        if (nationOpt.isEmpty()) {
            return;
        }

        Nation nation = nationOpt.get();
        NationId nationId = nation.id();

        // 检查是否有待处理的邀请
        if (allianceService.hasPendingInvite(nationId)) {
            var invites = allianceService.getPendingInvites(nationId);
            if (!invites.isEmpty()) {
                player.sendMessage("§6[军事联盟] §e你有 " + invites.size() + " 个待处理的军事联盟邀请");
                player.sendMessage("§7使用 /ma pending 查看详情");
            }
        }

        // 检查是否有盟国处于战争中
        var allies = allianceService.getMilitaryAllies(nationId, PactType.DEFENSIVE);
        for (NationId allyId : allies) {
            if (warService != null && nationId != null && warService.atWar(allyId, nationId)) {
                Optional<Nation> allyOpt = nationService.nationById(allyId);
                allyOpt.ifPresent(ally -> {
                    player.sendMessage("§6[军事联盟] §c盟国 " + ally.name() + " 正在战争中！");
                });
            }
        }
    }

    /**
     * 获取实体的国家ID
     */
    private Optional<NationId> getNationId(Entity entity) {
        if (entity instanceof Player player) {
            return nationService.nationOf(player.getUniqueId()).map(Nation::id);
        }
        return Optional.empty();
    }
}
