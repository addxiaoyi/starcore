package dev.starcore.starcore.module.war.gui;

import dev.starcore.starcore.module.army.ArmyService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.war.WarService;
import dev.starcore.starcore.module.war.situation.WarSituationService;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Optional;

/**
 * 战况预览菜单 GUI 点击事件监听器
 */
public class WarSituationListener implements Listener {

    private final WarSituationMenu situationMenu;
    private final WarService warService;
    private final ArmyService armyService;
    private final NationService nationService;
    private final WarSituationService situationService;

    public WarSituationListener(
        WarSituationMenu situationMenu,
        WarService warService,
        ArmyService armyService,
        NationService nationService,
        WarSituationService situationService
    ) {
        this.situationMenu = situationMenu;
        this.warService = warService;
        this.armyService = armyService;
        this.nationService = nationService;
        this.situationService = situationService;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory inv = event.getInventory();
        if (inv == null) {
            return;
        }

        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());

        // 检查是否是我们的战况 GUI
        if (!isSituationGui(title)) {
            return;
        }

        event.setCancelled(true);

        // 播放点击音效
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }

        int rawSlot = event.getRawSlot();
        int slot = event.getSlot();

        // 处理战况中心主菜单
        if (title.contains("战况中心") || title.contains("战况")) {
            handleMainMenuClick(player, rawSlot);
            return;
        }

        // 处理战争总览
        if (title.contains("战争总览")) {
            handleWarOverviewClick(player, rawSlot, clickedItem);
            return;
        }

        // 处理军队状态
        if (title.contains("军队状态")) {
            handleArmyStatusClick(player, rawSlot, clickedItem);
            return;
        }

        // 处理战场态势
        if (title.contains("战场态势")) {
            handleBattlefieldClick(player, rawSlot, clickedItem);
            return;
        }

        // 处理伤亡报告
        if (title.contains("伤亡报告")) {
            handleCasualtyClick(player, rawSlot, clickedItem);
            return;
        }
    }

    private boolean isSituationGui(String title) {
        return title.contains("战况") ||
               title.contains("战争总览") ||
               title.contains("军队状态") ||
               title.contains("战场态势") ||
               title.contains("伤亡报告");
    }

    private void handleMainMenuClick(Player player, int slot) {
        switch (slot) {
            case 19 -> {
                // 战争总览
                situationMenu.openWarOverview(player);
            }
            case 21 -> {
                // 军队状态
                situationMenu.openArmyStatus(player);
            }
            case 23 -> {
                // 战场态势
                situationMenu.openBattlefieldSituation(player);
            }
            case 25 -> {
                // 伤亡报告
                situationMenu.openCasualtyReport(player);
            }
            case 35 -> {
                // 返回按钮 - 关闭GUI
                player.closeInventory();
            }
        }
    }

    private void handleWarOverviewClick(Player player, int slot, ItemStack item) {
        // 返回按钮
        int size = player.getOpenInventory().getTopInventory().getSize();
        if (slot == size - 9 + 4) {
            situationMenu.openMainMenu(player);
            return;
        }

        // 点击战争项 - 打开战场态势
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.lore() == null) {
            return;
        }

        String itemName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        // 从名称中提取敌方名称 "⚔️ vs XXX"
        if (itemName.contains("vs ")) {
            String enemyName = itemName.substring(itemName.indexOf("vs ") + 3).trim();
            // 移除emoji
            enemyName = enemyName.replaceAll("[\\p{So}\\p{Cn}]", "").trim();

            Optional<Nation> nationOpt = nationService.nationByName(enemyName);
            nationOpt.ifPresent(nation -> {
                // 打开针对该敌国的战场态势
                situationMenu.openBattlefieldSituation(player);
            });
        }
    }

    private void handleArmyStatusClick(Player player, int slot, ItemStack item) {
        // 返回按钮
        int size = player.getOpenInventory().getTopInventory().getSize();
        if (slot == size - 9 + 4) {
            situationMenu.openMainMenu(player);
            return;
        }

        // 点击军队项 - 显示详细信息（可通过进一步点击实现）
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.lore() == null) {
            return;
        }

        String itemName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        // 显示军队提示信息
        if (itemName.contains("#")) {
            player.sendMessage("§e选择该军队查看详细信息");
        }
    }

    private void handleBattlefieldClick(Player player, int slot, ItemStack item) {
        // 返回按钮
        if (slot == 40) {
            situationMenu.openMainMenu(player);
            return;
        }

        // 点击战场项 - 显示战场详情
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.lore() == null) {
            return;
        }

        String itemName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        if (itemName.contains("战场")) {
            // 获取战场详细信息
            player.sendMessage("§6=== 战场详情 ===");
            player.sendMessage("§7正在获取战场详细数据...");
        }
    }

    private void handleCasualtyClick(Player player, int slot, ItemStack item) {
        // 返回按钮
        if (slot == 40) {
            situationMenu.openMainMenu(player);
            return;
        }

        // 点击伤亡项 - 显示详细伤亡报告
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.lore() == null) {
            return;
        }

        String itemName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        if (itemName.contains("vs ")) {
            String enemyName = itemName.substring(itemName.indexOf("vs ") + 3).trim();

            Optional<Nation> nationOpt = nationService.nationByName(enemyName);
            if (nationOpt.isPresent()) {
                NationId nationId = nationOpt.get().getId();
                String summary = situationService.getSummary(nationId);
                player.sendMessage("§6=== 伤亡详情 vs " + enemyName + " ===");
                for (String line : summary.split("\n")) {
                    player.sendMessage(line);
                }
            }
        }
    }
}