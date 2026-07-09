package dev.starcore.starcore.nation.listener;

import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.nation.economy.NationEconomy;
import dev.starcore.starcore.util.ColorCodes;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 经济事件监听器
 * 处理Nation经济相关事件
 */
public class EconomyEventListener implements Listener {

    private final NationService nationService;
    private final OnlinePlayerDirectory onlinePlayerDirectory;

    // Nation经济实例缓存
    private final Map<UUID, NationEconomy> economyCache = new ConcurrentHashMap<>();

    public EconomyEventListener(NationService nationService, OnlinePlayerDirectory onlinePlayerDirectory) {
        this.nationService = nationService;
        this.onlinePlayerDirectory = onlinePlayerDirectory;
    }

    /**
     * 玩家加入时检查Nation经济状态
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        org.bukkit.entity.Player player = event.getPlayer();

        // 获取玩家的 Nation
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            return;
        }
        Nation nation = nationOpt.get();
        UUID nationId = nation.id().value();

        // 获取Nation经济
        NationEconomy economy = economyCache.computeIfAbsent(
            nationId,
            id -> new NationEconomy(id)
        );

        // 检查破产状态
        if (economy.isBankrupt()) {
            player.sendMessage("§c§l[警告] §c你的Nation已破产！");
            player.sendMessage("§7Nation功能受限，请尽快还清债务");
        }
        // 检查危急状态
        else if (economy.getStatus() == NationEconomy.EconomyStatus.CRITICAL) {
            player.sendMessage("§c§l[警告] §c你的Nation经济危急！");
            player.sendMessage("§7债务: §c" + String.format("%.2f", economy.getDebt()) + " 金币");
        }
        // 检查警告状态
        else if (economy.getStatus() == NationEconomy.EconomyStatus.WARNING) {
            player.sendMessage("§e§l[提醒] §e你的Nation债务较高");
            player.sendMessage("§7债务: §e" + String.format("%.2f", economy.getDebt()) + " 金币");
        }
        // 检查资金不足
        else if (economy.getStatus() == NationEconomy.EconomyStatus.LOW_FUNDS) {
            player.sendMessage("§e§l[提醒] §e你的Nation资金不足");
            player.sendMessage("§7余额: §e" + String.format("%.2f", economy.getBalance()) + " 金币");
        }
    }

    /**
     * 玩家离开时清理缓存（如果需要）
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        org.bukkit.entity.Player player = event.getPlayer();

        // 检查 Nation 是否还有在线成员
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            return;
        }
        Nation nation = nationOpt.get();
        UUID nationId = nation.id().value();

        // 如果 Nation 没有在线成员，清理缓存
        if (!hasOnlineMembers(nationId)) {
            NationEconomy economy = economyCache.remove(nationId);
            if (economy != null) {
                // 保存经济数据到 Nation 对象
                nation.setTreasuryBalance(java.math.BigDecimal.valueOf(economy.getBalance()));
            }
        }
    }

    /**
     * 注册Nation经济
     */
    public void registerEconomy(UUID nationId, NationEconomy economy) {
        economyCache.put(nationId, economy);
    }

    /**
     * 获取Nation经济
     */
    public NationEconomy getEconomy(UUID nationId) {
        return economyCache.get(nationId);
    }

    /**
     * 移除Nation经济
     */
    public void removeEconomy(UUID nationId) {
        economyCache.remove(nationId);
    }

    // ==================== 辅助方法 ====================

    /**
     * 检查 Nation 是否还有在线成员
     */
    private boolean hasOnlineMembers(UUID nationId) {
        Optional<Nation> nationOpt = nationService.nationById(NationId.of(nationId));
        if (nationOpt.isEmpty()) {
            return false;
        }

        Nation nation = nationOpt.get();

        // 检查是否有成员在线
        for (var member : nation.members()) {
            if (onlinePlayerDirectory.findOnlinePlayer(member.playerId()).isPresent()) {
                return true;
            }
        }

        return false;
    }
}
