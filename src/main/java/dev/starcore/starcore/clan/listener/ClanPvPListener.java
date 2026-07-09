package dev.starcore.starcore.clan.listener;

import dev.starcore.starcore.clan.Clan;
import dev.starcore.starcore.clan.ClanManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Clan PvP监听器
 * 处理Clan成员之间的PvP规则和统计
 */
public class ClanPvPListener implements Listener {

    // 颜色代码常量
    private static final String C_RED = "§c";     // 错误/警告
    private static final String C_GOLD = "§6";    // 强调
    private static final String C_YELLOW = "§e";  // 信息
    private static final String C_GRAY = "§7";    // 次要信息
    private static final String C_WHITE = "§f";   // 普通文本

    private final ClanManager clanManager;

    public ClanPvPListener(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    /**
     * 监听玩家伤害
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        Clan attackerClan = clanManager.getPlayerClan(attacker);
        Clan victimClan = clanManager.getPlayerClan(victim);

        // 双方都没有Clan，允许
        if (attackerClan == null && victimClan == null) {
            return;
        }

        // 同Clan检查
        if (attackerClan != null && victimClan != null &&
            attackerClan.getId().equals(victimClan.getId())) {

            // 检查友军伤害设置
            if (!attackerClan.isFriendlyFire()) {
                event.setCancelled(true);
                attacker.sendMessage(C_RED + "不能攻击Clan成员！");
                return;
            }
        }

        // 联盟Clan检查
        if (attackerClan != null && victimClan != null &&
            attackerClan.isAlly(victimClan.getId()) &&
            victimClan.isAlly(attackerClan.getId())) {

            event.setCancelled(true);
            attacker.sendMessage(C_RED + "不能攻击盟友Clan成员！");
            return;
        }

        // PvP检查
        if (attackerClan != null && !attackerClan.isPvpEnabled()) {
            event.setCancelled(true);
            attacker.sendMessage(C_RED + "你的Clan禁用了PvP");
            return;
        }

        if (victimClan != null && !victimClan.isPvpEnabled()) {
            event.setCancelled(true);
            attacker.sendMessage(C_RED + "对方的Clan禁用了PvP");
            return;
        }
    }

    /**
     * 监听玩家死亡
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // 更新受害者Clan统计
        Clan victimClan = clanManager.getPlayerClan(victim);
        if (victimClan != null) {
            victimClan.addDeath();
            victimClan.updateActiveTime();
        }

        // 更新击杀者Clan统计
        if (killer != null) {
            Clan killerClan = clanManager.getPlayerClan(killer);
            if (killerClan != null) {
                killerClan.addKill();
                killerClan.updateActiveTime();

                // 通知Clan
                String victimClanTag = victimClan != null ?
                    victimClan.getColoredTag() : C_GRAY + "无";

                for (java.util.UUID memberId : killerClan.getMembers()) {
                    Player member = org.bukkit.Bukkit.getPlayer(memberId);
                    if (member != null && member.isOnline()) {
                        member.sendMessage(String.format(
                            C_GOLD + "[Clan] " + C_YELLOW + "%s " + C_GRAY + "击杀了 %s " + C_YELLOW + "%s",
                            killer.getName(),
                            victimClanTag,
                            victim.getName()
                        ));
                    }
                }
            }
        }
    }
}
