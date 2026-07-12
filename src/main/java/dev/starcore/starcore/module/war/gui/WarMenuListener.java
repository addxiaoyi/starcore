package dev.starcore.starcore.module.war.gui;

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
 * 战争菜单 GUI 点击事件监听器
 */
public class WarMenuListener implements Listener {

    private final WarMenu warMenu;
    private final WarSituationMenu warSituationMenu;
    private final dev.starcore.starcore.module.nation.NationService nationService;

    public WarMenuListener(WarMenu warMenu, WarSituationMenu warSituationMenu, dev.starcore.starcore.module.nation.NationService nationService) {
        this.warMenu = warMenu;
        this.warSituationMenu = warSituationMenu;
        this.nationService = nationService;
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

        // 检查是否是我们的 GUI
        if (!isWarGui(title)) {
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

        // 处理主菜单
        if (title.contains("战争中心") || title.equals("War Center")) {
            handleMainMenuClick(player, rawSlot);
            return;
        }

        // 处理进行中的战争
        if (title.contains("进行中的战争") || title.contains("Active Wars")) {
            handleActiveWarsClick(player, rawSlot, clickedItem);
            return;
        }

        // 处理宣战选择
        if (title.contains("宣战选择") || title.contains("Declare War")) {
            handleDeclareWarClick(player, rawSlot, clickedItem);
            return;
        }

        // 处理条约管理
        if (title.contains("条约管理") || title.contains("Treaty")) {
            handleTreatyClick(player, rawSlot);
            return;
        }

        // 处理战争详情
        if (title.contains("vs ")) {
            handleWarDetailClick(player, rawSlot);
            return;
        }
    }

    private boolean isWarGui(String title) {
        return title.contains("战争") ||
               title.contains("War") ||
               title.contains("Treaty") ||
               title.contains("Active Wars") ||
               title.contains("Declare");
    }

    private void handleMainMenuClick(Player player, int slot) {
        switch (slot) {
            case 19 -> {
                // 进行中的战争
                warMenu.openActiveWars(player);
            }
            case 21 -> {
                // 战争历史
                warMenu.openWarHistory(player);
            }
            case 23 -> {
                // 战况预览
                if (warSituationMenu != null) {
                    warSituationMenu.openMainMenu(player);
                } else {
                    player.sendMessage("§c战况预览功能暂不可用");
                }
            }
            case 25 -> {
                // 停战协议
                warMenu.openTreatyMenu(player);
            }
            case 31 -> {
                // 返回按钮
                player.closeInventory();
            }
        }
    }

    private void handleActiveWarsClick(Player player, int slot, ItemStack item) {
        // 返回按钮
        int size = player.getOpenInventory().getTopInventory().getSize();
        if (slot == size - 9 + 4) {
            warMenu.openMainMenu(player);
            return;
        }

        // 解析战争目标
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.lore() == null) {
            return;
        }

        String itemName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        // 从名称中提取敌方名称 "⚔️ vs XXX"
        if (itemName.contains("vs ")) {
            String enemyName = itemName.substring(itemName.indexOf("vs ") + 3).trim();

            if (nationService != null) {
                Optional<dev.starcore.starcore.module.nation.model.Nation> nationOpt =
                    nationService.nationByName(enemyName);

                nationOpt.ifPresent(nation -> warMenu.openWarDetail(player, nation.id()));
            }
        }
    }

    private void handleDeclareWarClick(Player player, int slot, ItemStack item) {
        // 返回按钮
        int size = player.getOpenInventory().getTopInventory().getSize();
        if (slot == size - 9 + 4) {
            warMenu.openMainMenu(player);
            return;
        }

        // 解析选择的目标
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.lore() == null) {
            return;
        }

        // 检查是否可宣战
        String loreStr = meta.lore().stream()
            .map(l -> PlainTextComponentSerializer.plainText().serialize(l))
            .reduce("", (a, b) -> a + b);

        if (loreStr.contains("无法向盟国宣战")) {
            player.sendMessage("§c无法向盟国宣战!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        String itemName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        // 提取国家名称
        String nationName = itemName.replaceAll("[§][a-f0-9]", "").trim();

        player.sendMessage("§6=== 宣战确认 ===");
        player.sendMessage("§7你选择了向 §e" + nationName + " §7宣战");
        // 分隔
        player.sendMessage("§7使用指令确认: §e/war declare <你的国家> " + nationName);
        // 分隔
        player.sendMessage("§7或者在游戏中进行操作...");

        // 关闭 GUI，让玩家执行操作
        player.closeInventory();
    }

    private void handleTreatyClick(Player player, int slot) {
        switch (slot) {
            case 19 -> {
                // 发起停战谈判
                player.sendMessage("§6=== 停战谈判 ===");
                player.sendMessage("§7使用 §e/war peace <敌国名称> §7发起停战谈判");
            }
            case 21 -> {
                // 查看条约列表
                player.sendMessage("§6=== 条约列表 ===");
                player.sendMessage("§7暂无已签订的条约");
            }
            case 23 -> {
                // 违反条约
                player.sendMessage("§6=== 违约后果 ===");
                player.sendMessage("§c违反停战条约将导致:");
                player.sendMessage("§c- 国际声望大幅下降");
                player.sendMessage("§c- 所有盟国关系破裂");
                player.sendMessage("§c- 受到严厉的经济制裁");
                player.sendMessage("§c- 可能引发多方联合讨伐");
            }
            case 35 -> {
                // 返回
                warMenu.openMainMenu(player);
            }
        }
    }

    private void handleWarDetailClick(Player player, int slot) {
        switch (slot) {
            case 28 -> {
                // 战争统计
                player.sendMessage("§6=== 战争统计 ===");
                player.sendMessage("§7详细战争统计功能开发中...");
            }
            case 30 -> {
                // 发起停战
                player.sendMessage("§6=== 发起停战 ===");
                player.sendMessage("§7使用 §e/war peace <敌国名称> §7发起停战谈判");
                player.closeInventory();
            }
            case 32 -> {
                // 增援请求
                player.sendMessage("§6=== 请求增援 ===");
                player.sendMessage("§7向所有盟友发送增援请求...");
                player.sendMessage("§e功能开发中");
                player.closeInventory();
            }
            case 44 -> {
                // 返回
                warMenu.openActiveWars(player);
            }
        }
    }
}
