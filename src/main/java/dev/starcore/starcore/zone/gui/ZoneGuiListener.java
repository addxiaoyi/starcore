package dev.starcore.starcore.zone.gui;
import java.util.Optional;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.zone.ZoneEffect;
import dev.starcore.starcore.zone.ZoneModule;
import dev.starcore.starcore.zone.ZoneSnapshot;
import dev.starcore.starcore.zone.ZoneType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 经济区GUI点击监听器
 */
public class ZoneGuiListener implements Listener {

    private final ZoneModule zoneModule;
    private final MessageService messages;
    private final Map<UUID, UUID> playerOpenZone = new ConcurrentHashMap<>();
    private final Map<UUID, ZoneType> playerPendingZoneType = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerAwaitingName = new ConcurrentHashMap<>();

    public ZoneGuiListener(ZoneModule zoneModule, MessageService messages) {
        this.zoneModule = zoneModule;
        this.messages = messages;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();
        if (!title.contains("经济区")) {
            return;
        }

        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        // 获取玩家所在国家
        NationId nationId = getPlayerNation(player);
        if (nationId == null) {
            player.sendMessage("§c你不在任何国家中");
            return;
        }

        ZoneGui gui = new ZoneGui(zoneModule, messages, player, nationId);

        // 根据菜单类型处理点击
        if (title.equals("§6§l经济区管理")) {
            handleMainMenuClick(player, event.getSlot(), item, gui, nationId);
        } else if (title.startsWith("§5§l经济区详情")) {
            handleZoneInfoClick(player, event.getSlot(), item, gui, nationId);
        } else if (title.equals("§a§l创建经济区")) {
            handleCreateZoneClick(player, event.getSlot(), item, gui, nationId);
        } else if (title.startsWith("§b§l特效管理")) {
            handleEffectsMenuClick(player, event.getSlot(), item, gui, nationId);
        }
    }

    private void handleMainMenuClick(Player player, int slot, ItemStack item, ZoneGui gui, NationId nationId) {
        switch (slot) {
            case 53 -> player.closeInventory();
            case 49 -> gui.openCreateZoneMenu();
            case 48 -> {
                // 统计信息
                player.sendMessage("§6§l=== 经济区统计 ===");
                player.sendMessage("§7经济区数量: §f" + zoneModule.zoneCountFor(nationId) + "/" + zoneModule.zoneLimitFor(nationId));
                player.sendMessage("§7总税收加成: §a+" + String.format("%.1f%%", zoneModule.getTotalTaxBonus(nationId) * 100));
                player.sendMessage("§7总产出加成: §a+" + String.format("%.1f%%", zoneModule.getTotalProductionBonus(nationId) * 100));
            }
            default -> {
                if (slot >= 0 && slot < 45) {
                    // 点击经济区
                    String zoneName = extractZoneName(item);
                    if (zoneName != null) {
                        zoneModule.zonesOf(nationId).stream()
                            .filter(z -> z.name().equals(zoneName))
                            .findFirst()
                            .ifPresent(z -> {
                                playerOpenZone.put(player.getUniqueId(), z.id());
                                gui.openZoneInfo(z.id());
                            });
                    }
                }
            }
        }
    }

    private void handleZoneInfoClick(Player player, int slot, ItemStack item, ZoneGui gui, NationId nationId) {
        UUID zoneId = playerOpenZone.get(player.getUniqueId());
        if (zoneId == null) {
            player.closeInventory();
            return;
        }

        switch (slot) {
            case 31 -> gui.openMainMenu();
            case 11 -> {
                // 升级
                if (zoneModule.upgradeZone(zoneId)) {
                    player.sendMessage("§a经济区升级成功!");
                    gui.openZoneInfo(zoneId);
                } else {
                    player.sendMessage("§c升级失败，请检查国库余额");
                }
            }
            case 13 -> gui.openEffectsMenu(zoneId);
            case 15 -> {
                // 删除经济区
                Optional<ZoneSnapshot> zoneOpt = zoneModule.zoneById(zoneId);
                if (zoneOpt.isPresent()) {
                    ZoneSnapshot zone = zoneOpt.get();
                    boolean success = zoneModule.deleteZone(zoneId);
                    if (success) {
                        player.sendMessage("§c已删除经济区: " + zone.name());
                        player.closeInventory();
                    } else {
                        player.sendMessage("§c删除经济区失败!");
                    }
                }
            }
            case 22 -> {
                // 启用/停用
                Optional<ZoneSnapshot> zoneOpt = zoneModule.zoneById(zoneId);
                if (zoneOpt.isPresent()) {
                    ZoneSnapshot zone = zoneOpt.get();
                    if (zone.active()) {
                        zoneModule.disableZone(zoneId);
                        player.sendMessage("§e经济区已停用");
                    } else {
                        zoneModule.enableZone(zoneId);
                        player.sendMessage("§a经济区已启用");
                    }
                    gui.openZoneInfo(zoneId);
                }
            }
        }
    }

    private void handleCreateZoneClick(Player player, int slot, ItemStack item, ZoneGui gui, NationId nationId) {
        if (slot == 31) {
            gui.openMainMenu();
            return;
        }

        if (slot >= 0 && slot < 27) {
            String typeName = extractZoneTypeName(item);
            if (typeName != null) {
                ZoneType type = ZoneType.valueOf(typeName);
                if (type != null) {
                    playerPendingZoneType.put(player.getUniqueId(), type);
                    playerAwaitingName.put(player.getUniqueId(), "create");
                    player.closeInventory();
                    player.sendMessage("§a请在聊天框输入经济区名称 (输入 'cancel' 取消)");
                }
            }
        }
    }

    private void handleEffectsMenuClick(Player player, int slot, ItemStack item, ZoneGui gui, NationId nationId) {
        UUID zoneId = playerOpenZone.get(player.getUniqueId());
        if (zoneId == null) {
            player.closeInventory();
            return;
        }

        if (slot == 49) {
            gui.openZoneInfo(zoneId);
            return;
        }

        // 点击特效
        if ((slot >= 0 && slot < 27) || (slot >= 36 && slot < 54)) {
            ZoneEffect effect = extractEffect(item);
            if (effect != null) {
                Optional<ZoneSnapshot> zoneOpt = zoneModule.zoneById(zoneId);
                if (zoneOpt.isPresent() && zoneOpt.get().effects().contains(effect)) {
                    // 移除特效
                    zoneModule.removeEffect(zoneId, effect);
                    player.sendMessage("§c已移除特效: " + effect.getDisplayName());
                } else {
                    // 添加特效
                    if (zoneModule.addEffect(zoneId, effect)) {
                        player.sendMessage("§a已添加特效: " + effect.getDisplayName());
                    } else {
                        player.sendMessage("§c添加特效失败");
                    }
                }
                gui.openEffectsMenu(zoneId);
            }
        }
    }

    /**
     * 处理玩家聊天输入（经济区名称）
     */
    public void handleNameInput(Player player, String name) {
        if (name.equalsIgnoreCase("cancel")) {
            playerPendingZoneType.remove(player.getUniqueId());
            playerAwaitingName.remove(player.getUniqueId());
            player.sendMessage("§c已取消创建");
            return;
        }

        UUID uniqueId = player.getUniqueId();
        ZoneType pendingType = playerPendingZoneType.remove(uniqueId);

        if (pendingType != null) {
            String purpose = playerAwaitingName.remove(uniqueId);
            if ("create".equals(purpose)) {
                NationId nationId = getPlayerNation(player);
                if (nationId != null) {
                    try {
                        ZoneSnapshot zone = zoneModule.createZone(nationId, name, pendingType);
                        player.sendMessage("§a经济区 '" + name + "' 创建成功!");
                        player.sendMessage("§7类型: " + pendingType.getDisplayName());
                        player.sendMessage("§7税收加成: +" + String.format("%.1f%%", zone.taxBonus() * 100));
                        player.sendMessage("§7产出加成: +" + String.format("%.1f%%", zone.productionBonus() * 100));
                    } catch (IllegalStateException e) {
                        player.sendMessage("§c" + e.getMessage());
                    }
                }
            }
        }
    }

    private NationId getPlayerNation(Player player) {
        // 从NationModule获取玩家所在国家
        var nationService = zoneModule.getNationService();
        if (nationService != null) {
            return nationService.nationOf(player.getUniqueId()).map(n -> n.id()).orElse(null);
        }
        return null;
    }

    private String extractZoneName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String name = item.getItemMeta().getDisplayName();
            if (name.startsWith("§6§l")) {
                return name.replace("§6§l", "");
            }
        }
        return null;
    }

    private String extractZoneTypeName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String name = item.getItemMeta().getDisplayName();
            if (name.startsWith("§6§l")) {
                name = name.replace("§6§l", "");
                for (ZoneType type : ZoneType.values()) {
                    if (type.getDisplayName().equals(name)) {
                        return type.name();
                    }
                }
            }
        }
        return null;
    }

    private ZoneEffect extractEffect(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String name = item.getItemMeta().getDisplayName();
            // 移除颜色代码
            name = name.replaceAll("§.", "");
            for (ZoneEffect effect : ZoneEffect.values()) {
                if (effect.getDisplayName().equals(name)) {
                    return effect;
                }
            }
        }
        return null;
    }

    // E-040 修复: 玩家退出时清理所有 Map 状态
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        playerOpenZone.remove(playerId);
        playerPendingZoneType.remove(playerId);
        playerAwaitingName.remove(playerId);
    }
}
