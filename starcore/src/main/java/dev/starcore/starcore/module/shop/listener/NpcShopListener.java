package dev.starcore.starcore.module.shop.listener;

import dev.starcore.starcore.module.shop.model.Shop;
import dev.starcore.starcore.module.shop.service.NpcShopService;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.Plugin;

/**
 * NPC商店交互监听器
 * 处理玩家与NPC的交互以打开商店
 */
public class NpcShopListener implements Listener {
    private final NpcShopService npcShopService;
    private final Plugin plugin;

    public NpcShopListener(NpcShopService npcShopService, Plugin plugin) {
        this.npcShopService = npcShopService;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // 检查Citizens是否可用
        if (!isCitizensAvailable()) {
            return;
        }

        // 检查是否是右键点击NPC
        if (!(event.getRightClicked() instanceof org.bukkit.entity.NPC npcEntity)) {
            return;
        }

        // 获取NPC
        NPC npc = CitizensAPI.getNPCRegistry().getNPC(npcEntity);
        if (npc == null) {
            return;
        }

        // 检查NPC是否有绑定的商店
        int npcId = npc.getId();
        if (!npcShopService.hasShop(npcId)) {
            return;
        }

        // 阻止默认行为（如果需要）
        event.setCancelled(true);

        // 打开商店
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            npcShopService.openNpcShopGui(player, npcId);
        });
    }

    private boolean isCitizensAvailable() {
        return Bukkit.getPluginManager().getPlugin("Citizens") != null;
    }
}
