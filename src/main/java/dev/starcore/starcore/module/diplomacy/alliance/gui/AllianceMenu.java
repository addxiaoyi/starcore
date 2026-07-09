package dev.starcore.starcore.module.diplomacy.alliance.gui;

import dev.starcore.starcore.module.diplomacy.alliance.AllianceService;
import dev.starcore.starcore.module.diplomacy.alliance.AllianceService.AllianceInviteInfo;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.util.ColorCodes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 联盟管理 GUI 菜单
 *
 * 提供可视化界面管理联盟功能：
 * - 查看联盟列表
 * - 查看待处理邀请
 * - 联盟操作
 */
public class AllianceMenu {

    public static final String MENU_TITLE = ColorCodes.TITLE + "联盟管理";
    public static final int MENU_SIZE = 54; // 6 行

    private final AllianceService allianceService;
    private final NationService nationService;
    private final UUID playerId;

    public AllianceMenu(AllianceService allianceService, NationService nationService, UUID playerId) {
        this.allianceService = allianceService;
        this.nationService = nationService;
        this.playerId = playerId;
    }

    /**
     * 打开主菜单
     */
    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, MENU_SIZE, Component.text("联盟管理").color(NamedTextColor.GOLD));

        // 填充边框
        fillBorder(inv);

        // 检查玩家是否在国家中
        Optional<Nation> myNationOpt = nationService.nationOf(playerId);
        if (myNationOpt.isEmpty()) {
            inv.setItem(22, createItem(Material.BARRIER, ColorCodes.ERROR + "你不在任何国家中", ColorCodes.INFO + "你需要先加入一个国家才能使用联盟功能"));
            player.openInventory(inv);
            return;
        }

        Nation myNation = myNationOpt.get();
        NationId myNationId = myNation.id();

        // 我的联盟列表
        inv.setItem(20, createAllianceListItem(myNationId));

        // 待处理邀请
        inv.setItem(22, createPendingInvitesItem(myNationId));

        // 联盟统计
        inv.setItem(24, createStatsItem());

        // 创建联盟邀请按钮
        inv.setItem(38, createItem(Material.LAPIS_LAZULI, ColorCodes.HIGHLIGHT + "发送联盟邀请", ColorCodes.INFO + "点击选择一个国家发送邀请"));

        // 联盟操作指南
        inv.setItem(42, createItem(Material.BOOK, ColorCodes.AQUA + "联盟指南", ColorCodes.INFO + "查看如何建立和管理联盟"));

        // 刷新按钮
        inv.setItem(49, createItem(Material.COMPASS, ColorCodes.SUCCESS + "刷新", ColorCodes.INFO + "刷新联盟信息"));

        player.openInventory(inv);
    }

    /**
     * 打开联盟列表菜单
     */
    public void openAllianceListMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, MENU_SIZE, Component.text("联盟列表").color(NamedTextColor.GREEN));

        fillBorder(inv);

        Optional<Nation> myNationOpt = nationService.nationOf(playerId);
        if (myNationOpt.isEmpty()) {
            player.closeInventory();
            return;
        }

        Nation myNation = myNationOpt.get();
        NationId myNationId = myNation.id();

        // 获取盟友列表
        var allies = allianceService.getAllies(myNationId);

        if (allies.isEmpty()) {
            inv.setItem(22, createItem(Material.PAPER, ColorCodes.GRAY + "暂无联盟", ColorCodes.INFO + "你的国家还没有与其他国家建立联盟"));
        } else {
            int slot = 10;
            for (NationId allyId : allies) {
                if (slot > 43) break; // 避免超出菜单范围

                Optional<Nation> allyOpt = nationService.nationById(allyId);
                if (allyOpt.isEmpty()) continue;

                Nation ally = allyOpt.get();
                Optional<AllianceService.AllianceInfo> infoOpt = allianceService.getAllianceInfo(myNationId, allyId);
                String duration = infoOpt.map(info -> info.durationDays() + " 天").orElse("未知");

                List<String> lore = new ArrayList<>();
                lore.add(ColorCodes.GRAY + "持续时间: " + ColorCodes.WHITE + duration);
                lore.add("");
                lore.add(ColorCodes.HIGHLIGHT + "点击查看详情");
                lore.add(ColorCodes.ERROR + "右键解除联盟");

                inv.setItem(slot, createItem(Material.EMERALD, ColorCodes.SUCCESS + ally.name(), lore.toArray(new String[0])));

                slot++;
                if (slot % 9 == 8) slot += 2; // 跳过边框
            }
        }

        // 返回按钮
        inv.setItem(49, createItem(Material.ARROW, ColorCodes.ERROR + "返回", ColorCodes.INFO + "返回上一级菜单"));

        player.openInventory(inv);
    }

    /**
     * 打开待处理邀请菜单
     */
    public void openPendingInvitesMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, MENU_SIZE, Component.text("待处理邀请").color(NamedTextColor.YELLOW));

        fillBorder(inv);

        Optional<Nation> myNationOpt = nationService.nationOf(playerId);
        if (myNationOpt.isEmpty()) {
            player.closeInventory();
            return;
        }

        Nation myNation = myNationOpt.get();
        NationId myNationId = myNation.id();

        List<AllianceInviteInfo> pending = allianceService.getPendingInvites(myNationId);

        if (pending.isEmpty()) {
            inv.setItem(22, createItem(Material.PAPER, ColorCodes.GRAY + "暂无待处理邀请", ColorCodes.INFO + "没有国家向你们发送联盟邀请"));
        } else {
            int slot = 10;
            for (AllianceInviteInfo invite : pending) {
                if (slot > 43) break;

                long hours = invite.remainingMs() / (60 * 60 * 1000);
                List<String> lore = new ArrayList<>();
                lore.add(ColorCodes.GRAY + "邀请时间: " + ColorCodes.WHITE + invite.invitedAt());
                lore.add(ColorCodes.GRAY + "剩余时间: " + ColorCodes.HIGHLIGHT + hours + " 小时");
                lore.add("");
                lore.add(ColorCodes.SUCCESS + "左键接受邀请");
                lore.add(ColorCodes.ERROR + "右键拒绝邀请");

                inv.setItem(slot, createItem(Material.BEACON, ColorCodes.GOLD + "来自: " + invite.inviterName(), lore.toArray(new String[0])));

                slot++;
                if (slot % 9 == 8) slot += 2;
            }
        }

        // 返回按钮
        inv.setItem(49, createItem(Material.ARROW, ColorCodes.ERROR + "返回", ColorCodes.INFO + "返回上一级菜单"));

        player.openInventory(inv);
    }

    /**
     * 打开发送邀请菜单（国家列表）
     */
    public void openSendInviteMenu(Player player, int page) {
        Inventory inv = Bukkit.createInventory(null, MENU_SIZE, Component.text("选择邀请国家").color(NamedTextColor.AQUA));

        fillBorder(inv);

        Optional<Nation> myNationOpt = nationService.nationOf(playerId);
        if (myNationOpt.isEmpty()) {
            player.closeInventory();
            return;
        }

        Nation myNation = myNationOpt.get();
        NationId myNationId = myNation.id();

        List<Nation> nations = new ArrayList<>(nationService.nations());
        nations.removeIf(n -> n.id().equals(myNationId)); // 移除自己的国家

        int itemsPerPage = 36;
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, nations.size());

        if (startIndex >= nations.size()) {
            player.closeInventory();
            return;
        }

        int slot = 10;
        for (int i = startIndex; i < endIndex; i++) {
            if (slot > 43) break;

            Nation targetNation = nations.get(i);

            // 检查状态
            boolean isAllied = allianceService.areAllied(myNationId, targetNation.id());
            boolean hasInvite = allianceService.hasPendingInvite(targetNation.id());
            boolean inCooldown = allianceService.isInCooldown(myNationId, targetNation.id());

            List<String> lore = new ArrayList<>();
            if (isAllied) {
                lore.add(ColorCodes.SUCCESS + "已是联盟");
            } else if (hasInvite) {
                lore.add(ColorCodes.HIGHLIGHT + "已有待处理邀请");
            } else if (inCooldown) {
                long remaining = allianceService.getRemainingCooldownMs(myNationId, targetNation.id());
                long hours = remaining / (60 * 60 * 1000);
                lore.add(ColorCodes.ERROR + "外交冷却中 (" + hours + "小时)");
            } else {
                lore.add(ColorCodes.SUCCESS + "可发送邀请");
            }

            Material material = isAllied ? Material.EMERALD : Material.NETHER_STAR;
            inv.setItem(slot, createItem(material, ColorCodes.WHITE + targetNation.name(), lore.toArray(new String[0])));

            slot++;
            if (slot % 9 == 8) slot += 2;
        }

        // 分页控制
        if (page > 0) {
            inv.setItem(45, createItem(Material.ARROW, ColorCodes.HIGHLIGHT + "上一页", ColorCodes.GRAY + "第 " + page + " 页"));
        }
        if (endIndex < nations.size()) {
            inv.setItem(53, createItem(Material.ARROW, ColorCodes.HIGHLIGHT + "下一页", ColorCodes.GRAY + "第 " + (page + 1) + " 页"));
        }

        // 返回按钮
        inv.setItem(49, createItem(Material.ARROW, ColorCodes.ERROR + "返回", ColorCodes.INFO + "返回上一级菜单"));

        player.openInventory(inv);
    }

    /**
     * 打开联盟详情菜单
     */
    public void openAllianceDetailMenu(Player player, NationId allyId) {
        Inventory inv = Bukkit.createInventory(null, 36, Component.text("联盟详情").color(NamedTextColor.AQUA));

        fillBorder(inv);

        Optional<Nation> myNationOpt = nationService.nationOf(playerId);
        Optional<Nation> allyNationOpt = nationService.nationById(allyId);
        if (myNationOpt.isEmpty() || allyNationOpt.isEmpty()) {
            player.closeInventory();
            return;
        }

        Nation myNation = myNationOpt.get();
        Nation allyNation = allyNationOpt.get();

        // 获取联盟信息
        Optional<AllianceService.AllianceInfo> infoOpt = allianceService.getAllianceInfo(myNation.id(), allyId);

        // 标题
        inv.setItem(4, createItem(Material.EMERALD, ColorCodes.TITLE + "盟国详情", ColorCodes.INFO + "查看与 " + allyNation.name() + " 的联盟信息"));

        // 盟国信息
        List<String> allyLore = new ArrayList<>();
        allyLore.add(ColorCodes.GRAY + "国家名称: " + ColorCodes.WHITE + allyNation.name());
        allyLore.add(ColorCodes.GRAY + "成员数量: " + ColorCodes.WHITE + allyNation.members().size());
        allyLore.add("");
        allyLore.add(ColorCodes.HIGHLIGHT + "点击操作");
        allyLore.add(ColorCodes.ERROR + "右键解除联盟");
        inv.setItem(10, createItem(Material.EMERALD, ColorCodes.SUCCESS + "盟国: " + allyNation.name(), allyLore.toArray(new String[0])));

        // 联盟信息
        if (infoOpt.isPresent()) {
            AllianceService.AllianceInfo info = infoOpt.get();
            List<String> infoLore = new ArrayList<>();
            infoLore.add("§7成立时间: §f" + info.formedAt());
            infoLore.add("§7持续时间: §a" + info.durationDays() + " 天");
            infoLore.add("§7联盟等级: §e" + getAllianceLevel(info.durationDays()));
            inv.setItem(13, createItem(Material.NETHER_STAR, "§b联盟信息", infoLore.toArray(new String[0])));
        } else {
            inv.setItem(13, createItem(Material.PAPER, "§7联盟信息", "暂无详细信息"));
        }

        // 联合行动
        inv.setItem(16, createItem(Material.DIAMOND, "§e联合行动", "查看与该盟国的联合行动", "如联合军事演习等"));

        // 操作按钮
        inv.setItem(28, createItem(Material.BOOK, "§b联盟历史", "查看联盟历史记录"));

        inv.setItem(30, createItem(Material.LAPIS_LAZULI, "§e发送消息", "向盟国发送消息"));

        inv.setItem(32, createItem(Material.REDSTONE, "§c解除联盟", "解除与该国的联盟关系", "注意: 解除后将进入 24 小时冷却"));

        // 返回按钮
        inv.setItem(31, createItem(Material.ARROW, "§c返回", "返回联盟列表"));

        player.openInventory(inv);
    }

    /**
     * 根据持续时间获取联盟等级
     */
    private String getAllianceLevel(long days) {
        if (days >= 365) return "§6传说";
        if (days >= 180) return "§e史诗";
        if (days >= 90) return "§b稀有";
        if (days >= 30) return "§a优秀";
        if (days >= 7) return "§7普通";
        return "§8新结盟";
    }

    // ==================== 辅助方法 ====================

    private ItemStack createAllianceListItem(NationId nationId) {
        int allyCount = allianceService.getAllies(nationId).size();
        List<String> lore = new ArrayList<>();
        lore.add("§7当前盟国数量: §a" + allyCount);
        lore.add("");
        lore.add("§e点击查看联盟列表");
        return createItem(Material.EMERALD, "§a联盟列表", lore.toArray(new String[0]));
    }

    private ItemStack createPendingInvitesItem(NationId nationId) {
        int inviteCount = allianceService.getPendingInvites(nationId).size();
        List<String> lore = new ArrayList<>();
        lore.add("§7待处理邀请: §e" + inviteCount);
        lore.add("");
        lore.add("§e点击查看邀请");
        return createItem(Material.BEACON, "§e待处理邀请", lore.toArray(new String[0]));
    }

    private ItemStack createStatsItem() {
        AllianceService.AllianceStats stats = allianceService.getStats();
        List<String> lore = new ArrayList<>();
        lore.add("§7总联盟数: §a" + stats.totalAlliances());
        lore.add("§7最大联盟规模: §a" + stats.largestAllianceSize());
        lore.add("§7最活跃国家: §b" + stats.mostActiveNation());
        lore.add("");
        lore.add("§e点击查看详细统计");
        return createItem(Material.NETHER_STAR, "§b联盟统计", lore.toArray(new String[0]));
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
            if (lore.length > 0) {
                List<Component> loreList = new ArrayList<>();
                for (String line : lore) {
                    loreList.add(Component.text(line).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(loreList);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fillBorder(Inventory inv) {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
            filler.setItemMeta(meta);
        }

        for (int i = 0; i < 9; i++) {
            inv.setItem(i, filler);
        }
        inv.setItem(9, filler);
        inv.setItem(17, filler);
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, filler);
        }
        inv.setItem(36, filler);
        inv.setItem(44, filler);
    }
}
