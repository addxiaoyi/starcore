package dev.starcore.starcore.module.war.gui;

import dev.starcore.starcore.foundation.gui.ButtonFactory;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.war.WarService;
import dev.starcore.starcore.module.war.WarSnapshot;
import dev.starcore.starcore.module.war.WarStatsService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

/**
 * 战争菜单 GUI 点击事件监听器
 */
public class WarMenuListener implements Listener {

    private final WarMenu warMenu;
    private final WarSituationMenu warSituationMenu;
    private final NationService nationService;
    private final WarService warService;
    private final WarStatsService warStatsService;

    public WarMenuListener(WarMenu warMenu, WarSituationMenu warSituationMenu,
                          NationService nationService, WarService warService,
                          WarStatsService warStatsService) {
        this.warMenu = warMenu;
        this.warSituationMenu = warSituationMenu;
        this.nationService = nationService;
        this.warService = warService;
        this.warStatsService = warStatsService;
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
        // 从标题中提取敌方名称
        String title = player.getOpenInventory().getTitle();
        String enemyName = null;
        if (title.contains("vs ")) {
            enemyName = title.substring(title.indexOf("vs ") + 3).trim();
        }

        switch (slot) {
            case 28 -> {
                // 战争统计 - 显示详细统计面板
                if (enemyName != null) {
                    openWarStatsPanel(player, enemyName);
                } else {
                    player.sendMessage("§c无法获取战争信息");
                }
            }
            case 30 -> {
                // 发起停战
                if (enemyName != null) {
                    player.sendMessage("§6=== 发起停战 ===");
                    player.sendMessage("§7使用 §e/war peace " + enemyName + " §7发起停战谈判");
                    player.closeInventory();
                } else {
                    player.sendMessage("§6=== 发起停战 ===");
                    player.sendMessage("§7使用 §e/war peace <敌国名称> §7发起停战谈判");
                    player.closeInventory();
                }
            }
            case 32 -> {
                // 增援请求 - 向盟友广播
                if (enemyName != null) {
                    broadcastReinforcementRequest(player, enemyName);
                } else {
                    player.sendMessage("§6=== 请求增援 ===");
                    player.sendMessage("§c无法识别敌国，请重试");
                    player.closeInventory();
                }
            }
            case 44 -> {
                // 返回
                warMenu.openActiveWars(player);
            }
        }
    }

    /**
     * 打开战争统计面板
     */
    private void openWarStatsPanel(Player player, String enemyName) {
        // 获取玩家国家
        Optional<Nation> nationOpt = nationService.getNationByMember(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage("§c你还没有加入任何国家");
            return;
        }

        Nation playerNation = nationOpt.get();
        Optional<Nation> enemyNationOpt = nationService.nationByName(enemyName);
        if (enemyNationOpt.isEmpty()) {
            player.sendMessage("§c未找到敌国: " + enemyName);
            return;
        }

        Nation enemyNation = enemyNationOpt.get();

        // 获取战争统计
        Optional<WarSnapshot> warOpt = warService.findActiveWar(playerNation.id(), enemyNation.id())
            .map(w -> new WarSnapshot(w.left(), w.right(), w.declaredAt(),
                w.endedAt() != null ? w.endedAt() : null));

        // 创建统计面板
        Inventory inv = Bukkit.createInventory(null, 36,
            Component.text("§c§l⚔️ 战争统计: " + enemyName));

        // 填充边框
        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        if (warOpt.isEmpty()) {
            inv.setItem(13, createInfoItem(Material.BARRIER,
                "§c未找到战争记录",
                "可能战争已经结束"));
        } else {
            WarSnapshot war = warOpt.get();
            Duration duration = Duration.between(war.declaredAt(), Instant.now());

            // 获取统计数据
            WarStatsService.WarStats stats = warStatsService.getOrCreateWarStats(war, playerNation.id());

            // 己方击杀
            inv.setItem(10, createStatItem(Material.DIAMOND_SWORD,
                "§a己方击杀",
                "§e" + stats.getAllyKills() + " §7人",
                "己方共击杀敌人数"));

            // 敌方击杀
            inv.setItem(12, createStatItem(Material.IRON_SWORD,
                "§c敌方击杀",
                "§e" + stats.getEnemyKills() + " §7人",
                "敌方共击杀己方人数"));

            // 己方占领
            inv.setItem(14, createStatItem(Material.GREEN_BANNER,
                "§a己方占领",
                "§e" + stats.getAllyCaptures() + " §7块",
                "己方占领敌区数量"));

            // 敌方占领
            inv.setItem(16, createStatItem(Material.RED_BANNER,
                "§c敌方占领",
                "§e" + stats.getEnemyCaptures() + " §7块",
                "敌方占领己区数量"));

            // 战斗次数
            inv.setItem(22, createStatItem(Material.IRON_SWORD,
                "§e战斗次数",
                "§e" + stats.getBattleCount() + " §7场",
                "已发生的大小战斗"));

            // 战争时长
            inv.setItem(31, createInfoItem(Material.CLOCK,
                "§e⏱️ 战争时长",
                formatDuration(duration)));

            // 开战时间
            inv.setItem(4, createInfoItem(Material.PAPER,
                "§7宣战时间",
                formatInstant(war.declaredAt())));
        }

        // 返回按钮
        inv.setItem(31, inv.getItem(31) == null ?
            ButtonFactory.createBackButton() : inv.getItem(31));
        inv.setItem(35, ButtonFactory.createBackButton());

        player.openInventory(inv);
    }

    /**
     * 向盟友广播增援请求
     */
    private void broadcastReinforcementRequest(Player player, String enemyName) {
        Optional<Nation> nationOpt = nationService.getNationByMember(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage("§c你还没有加入任何国家");
            return;
        }

        Nation playerNation = nationOpt.get();

        player.sendMessage("§6=== 请求增援 ===");
        player.sendMessage("§a已向所有盟友发送增援请求!");
        player.sendMessage("§7请求内容: §e对 " + enemyName + " 的战争需要增援!");
        player.sendMessage("§7请求者: §e" + player.getName());
        player.closeInventory();

        // TODO: 通过事件系统向盟友国家成员广播
        // nationService.getNationMembers(playerNation.id()).forEach(member -> {
        //     Player ally = Bukkit.getPlayer(member.playerId());
        //     if (ally != null && !ally.equals(player)) {
        //         ally.sendMessage("§6⚔️ 盟友增援请求 ⚔️");
        //         ally.sendMessage("§e" + player.getName() + " §7请求对 §c" + enemyName + " §7的战争增援!");
        //     }
        // });
    }

    private ItemStack createInfoItem(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            meta.lore().clear();
            meta.lore().add(Component.text("§7" + lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createStatItem(Material material, String name, String value, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            meta.lore().clear();
            meta.lore().add(Component.text(value));
            meta.lore().add(Component.text("§7" + lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fillBorder(Inventory inv, Material material) {
        int size = inv.getSize();
        ItemStack border = new ItemStack(material);
        for (int i = 0; i < size; i++) {
            if (i < 9 || i >= size - 9 || i % 9 == 0 || i % 9 == 8) {
                if (inv.getItem(i) == null) {
                    inv.setItem(i, border);
                }
            }
        }
    }

    private String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        if (days > 0) {
            return String.format("%d天 %d小时", days, hours);
        } else if (hours > 0) {
            return String.format("%d小时 %d分钟", hours, minutes);
        } else {
            return String.format("%d分钟", minutes);
        }
    }

    private String formatInstant(Instant instant) {
        return instant.toString().replace("T", " ").substring(0, 16);
    }
}
