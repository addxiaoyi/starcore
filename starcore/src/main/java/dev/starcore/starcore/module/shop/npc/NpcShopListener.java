package dev.starcore.starcore.module.shop.npc;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.Plugin;

/**
 * NPC商店交互监听器
 * 处理玩家与NPC的交互以打开商店界面
 */
public class NpcShopListener implements Listener {

    private final Plugin plugin;
    private final NpcShopService npcShopService;

    public NpcShopListener(Plugin plugin, NpcShopService npcShopService) {
        this.plugin = plugin;
        this.npcShopService = npcShopService;
    }

    /**
     * 处理玩家右键点击NPC
     */
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // 检查Citizens插件是否可用
        if (!isCitizensAvailable()) {
            return;
        }

        // 检查是否点击的是NPC实体
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

        // 阻止默认行为
        event.setCancelled(true);

        // 异步打开商店
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            npcShopService.onNpcClick(player, npcId);
        });
    }

    /**
     * 处理村民NPC交互（备用方式）
     */
    @EventHandler
    public void onVillagerInteract(PlayerInteractEntityEvent event) {
        // 如果Citizens不可用，检查是否是村民
        if (isCitizensAvailable()) {
            return;
        }

        if (!(event.getRightClicked() instanceof Villager villager)) {
            return;
        }

        // 检查村民是否有自定义名称
        Component customName = villager.customName();
        if (customName == null) {
            return;
        }

        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(customName);

        // 检查名称是否以[商店]开头
        if (!name.startsWith("[商店]")) {
            return;
        }

        // 阻止默认行为
        event.setCancelled(true);

        Player player = event.getPlayer();
        player.sendMessage(Component.text("请先使用 /npcshop open <商店ID> 打开商店", NamedTextColor.YELLOW));
    }

    /**
     * 检查Citizens插件是否可用
     */
    private boolean isCitizensAvailable() {
        return plugin.getServer().getPluginManager().getPlugin("Citizens") != null;
    }
}
