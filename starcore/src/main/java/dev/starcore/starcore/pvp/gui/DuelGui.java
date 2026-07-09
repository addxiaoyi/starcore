package dev.starcore.starcore.pvp.gui;

import java.util.concurrent.ConcurrentHashMap;
import dev.starcore.starcore.pvp.duel.DuelService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * 决斗选择界面
 */
public class DuelGui implements Listener {
    private final DuelService duelService;
    private final Map<UUID, DuelGuiState> guiStates = new ConcurrentHashMap<>();

    public DuelGui(DuelService duelService) {
        this.duelService = duelService;
    }

    /**
     * 打开决斗选择界面
     */
    public void openDuelSelection(Player player, Player target) {
        DuelGuiState state = new DuelGuiState(target.getUniqueId(), "default", 0, 1);
        guiStates.put(player.getUniqueId(), state);

        Inventory inv = Bukkit.createInventory(null, 27, Component.text("决斗: " + target.getName(), NamedTextColor.GOLD));

        // 标题
        inv.setItem(4, createItem(Material.DIAMOND_SWORD, "§6发起决斗", " ",
            "§7挑战者: §f" + player.getName(),
            "§7对手: §f" + target.getName()
        ));

        // Kit 选择
        Collection<DuelService.DuelKit> kits = duelService.getAvailableKits();
        int slot = 10;
        for (DuelService.DuelKit kit : kits) {
            if (slot > 16) break;
            boolean selected = kit.id().equals("default");
            ItemStack item = createKitItem(kit, selected);
            inv.setItem(slot, item);
            slot++;
        }

        // BO 选择
        inv.setItem(19, createItem(Material.PAPER, "§6BO 1 (单局)", " ",
            "§7胜者直接获胜"
        ));
        inv.setItem(20, createItem(Material.PAPER, "§aBO 3 (三局两胜)", " ",
            "§7先赢两局获胜"
        ));
        inv.setItem(21, createItem(Material.PAPER, "§bBO 5 (五局三胜)", " ",
            "§7先赢三局获胜"
        ));

        // 赌注
        inv.setItem(22, createItem(Material.GOLD_INGOT, "§6赌注: §f0", " ",
            "§7点击设置赌注"
        ));

        // 确认按钮
        inv.setItem(26, createItem(Material.LIME_STAINED_GLASS_PANE, "§a确认发起决斗", " ",
            "§7点击发起决斗"
        ));

        player.openInventory(inv);
    }

    /**
     * 打开决斗请求列表
     */
    public void openDuelRequests(Player player) {
        List<DuelService.DuelRequest> requests = duelService.getDuelRequests(player.getUniqueId());

        int size = Math.min(54, 9 + ((requests.size() - 1) / 9 + 1) * 9);
        Inventory inv = Bukkit.createInventory(null, size, Component.text("决斗请求", NamedTextColor.YELLOW));

        if (requests.isEmpty()) {
            inv.setItem(22, createItem(Material.BARRIER, "§c暂无决斗请求", " ",
                "§7当前没有玩家向你发起决斗"
            ));
        } else {
            int slot = 0;
            for (DuelService.DuelRequest request : requests) {
                Player challenger = Bukkit.getPlayer(request.challengerId());
                String challengerName = challenger != null ? challenger.getName() : "未知玩家";

                ItemStack item = createItem(Material.PLAYER_HEAD, "§6" + challengerName, " ",
                    "§7赌注: §f" + request.wager(),
                    "§7装备: §f" + request.kitName(),
                    "§7BO: §f" + request.bestOf(),
                    " ",
                    "§a左键: 接受",
                    "§c右键: 拒绝"
                );
                inv.setItem(slot, item);
                slot++;
            }
        }

        player.openInventory(inv);
    }

    /**
     * 打开决斗设置界面
     */
    public void openDuelSettings(Player player) {
        Inventory inv = Bukkit.createInventory(null, 36, Component.text("决斗设置", NamedTextColor.GOLD));

        // 状态
        inv.setItem(0, createItem(Material.BOOK, "§6决斗规则", " ",
            "§71. 选择装备 Kit",
            "§72. 设置赌注（可选）",
            "§73. 选择 BO 局数",
            "§74. 确认发起决斗"
        ));

        // 竞技场状态
        int available = duelService.getAvailableArenaCount();
        inv.setItem(8, createItem(Material.END_PORTAL_FRAME, "§6竞技场状态", " ",
            "§7可用竞技场: §f" + available,
            "§7状态: " + (available > 0 ? "§a可用" : "§c不可用")
        ));

        // 奖励信息
        inv.setItem(18, createItem(Material.GOLD_INGOT, "§6奖励信息", " ",
            "§7胜利基础奖励: §f10 金币",
            "§7赌注奖励: §f100%",
            " ",
            "§7胜利可获得赌注金额"
        ));

        // 帮助
        inv.setItem(26, createItem(Material.BLAZE_POWDER, "§6帮助", " ",
            "§7使用 /duel <玩家> 发起决斗",
            "§7使用 /duel accept 接受决斗",
            "§7使用 /duel stats 查看统计"
        ));

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();

        // 处理决斗请求列表
        if (title.contains("决斗请求")) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            List<DuelService.DuelRequest> requests = duelService.getDuelRequests(player.getUniqueId());

            if (slot >= 0 && slot < requests.size()) {
                DuelService.DuelRequest request = requests.get(slot);
                Player challenger = Bukkit.getPlayer(request.challengerId());

                if (event.isLeftClick()) {
                    // 接受
                    if (challenger != null) {
                        try {
                            duelService.acceptDuelRequest(player.getUniqueId(), challenger.getUniqueId());
                            player.sendMessage(Component.text("已接受 " + challenger.getName() + " 的决斗", NamedTextColor.GREEN));
                        } catch (Exception e) {
                            player.sendMessage(Component.text("无法接受决斗: " + e.getMessage(), NamedTextColor.RED));
                        }
                    }
                } else {
                    // 拒绝
                    duelService.rejectDuelRequest(player.getUniqueId(), request.challengerId());
                    player.sendMessage(Component.text("已拒绝决斗请求", NamedTextColor.YELLOW));
                    if (challenger != null) {
                        challenger.sendMessage(Component.text(player.getName() + " 拒绝了你的决斗请求", NamedTextColor.RED));
                    }
                }
                player.closeInventory();
            }
            return;
        }

        // 处理决斗设置界面
        if (title.contains("决斗设置")) {
            event.setCancelled(true);
            return;
        }

        // 处理决斗选择界面
        if (title.contains("决斗:")) {
            event.setCancelled(true);
            DuelGuiState state = guiStates.get(player.getUniqueId());
            if (state == null) return;

            int slot = event.getRawSlot();
            Player target = Bukkit.getPlayer(state.targetId);
            if (target == null || !target.isOnline()) {
                player.sendMessage(Component.text("目标玩家已离线", NamedTextColor.RED));
                player.closeInventory();
                return;
            }

            // Kit 选择 (10-16)
            if (slot >= 10 && slot <= 16) {
                Collection<DuelService.DuelKit> kits = duelService.getAvailableKits();
                int index = slot - 10;
                int i = 0;
                for (DuelService.DuelKit kit : kits) {
                    if (i == index) {
                        state.kitName = kit.id();
                        openDuelSelection(player, target);
                        return;
                    }
                    i++;
                }
            }

            // BO 选择
            if (slot == 19) state.bestOf = 1;
            if (slot == 20) state.bestOf = 3;
            if (slot == 21) state.bestOf = 5;

            // 确认
            if (slot == 26) {
                try {
                    duelService.sendDuelRequest(player.getUniqueId(), target.getUniqueId(),
                        state.wager, state.kitName, state.bestOf);
                    player.sendMessage(Component.text("已向 " + target.getName() + " 发送决斗请求", NamedTextColor.GREEN));
                    target.sendMessage(Component.text(player.getName() + " 向你发起了决斗！", NamedTextColor.YELLOW));
                } catch (Exception e) {
                    player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
                }
                player.closeInventory();
            }
        }
    }

    /**
     * 创建物品
     */
    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            List<Component> loreList = new ArrayList<>();
            for (String line : lore) {
                if (!line.isEmpty()) {
                    loreList.add(Component.text(line));
                }
            }
            meta.lore(loreList);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 创建 Kit 物品
     */
    private ItemStack createKitItem(DuelService.DuelKit kit, boolean selected) {
        Material mat = Material.DIAMOND_CHESTPLATE;
        if (kit.armor() != null && !kit.armor().isEmpty()) {
            ItemStack armor = kit.armor().values().iterator().next();
            if (armor != null) mat = armor.getType();
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text((selected ? "§a" : "§7") + kit.displayName()));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§7ID: §f" + kit.id()));
            lore.add(Component.text("§7武器: §f" + kit.weapons().size() + " 把"));
            lore.add(Component.text("§7药水效果: §f" + kit.effects().size() + " 个"));
            if (selected) {
                lore.add(Component.text("§a✓ 已选择"));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * GUI 状态（可变类）
     */
    private static class DuelGuiState {
        UUID targetId;
        String kitName;
        double wager;
        int bestOf;

        DuelGuiState(UUID targetId, String kitName, double wager, int bestOf) {
            this.targetId = targetId;
            this.kitName = kitName;
            this.wager = wager;
            this.bestOf = bestOf;
        }
    }

    // E-051 修复: 玩家退出时清理决斗 GUI 状态
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        guiStates.remove(event.getPlayer().getUniqueId());
    }
}