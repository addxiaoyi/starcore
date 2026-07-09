package dev.starcore.starcore.module.alliance;

import dev.starcore.starcore.module.alliance.AllianceService.*;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/**
 * 联盟系统 GUI 菜单
 *
 * 提供可视化界面管理联盟功能：
 * - 联盟信息展示
 * - 成员管理
 * - 邀请处理
 * - 联盟外交关系
 * - 公告发布
 */
public class AllianceGui {

    // 菜单常量
    public static final String MENU_TITLE = "§6§l联盟管理";
    public static final int MENU_SIZE = 54; // 6 行

    // 菜单类型
    public enum MenuType {
        MAIN,
        ALLIANCE_LIST,
        ALLIANCE_INFO,
        MEMBER_LIST,
        INVITE_SEND,
        INVITE_PENDING,
        RELATIONS
    }

    private final AllianceService allianceService;
    private final NationService nationService;
    private final UUID playerId;
    private final Player player;

    public AllianceGui(AllianceService allianceService, NationService nationService, Player player) {
        this.allianceService = allianceService;
        this.nationService = nationService;
        this.playerId = player.getUniqueId();
        this.player = player;
    }

    // ==================== 主菜单 ====================

    /**
     * 打开主菜单
     */
    public void openMainMenu() {
        Inventory inv = Bukkit.createInventory(null, MENU_SIZE,
            Component.text("联盟管理").color(NamedTextColor.GOLD));

        fillBorder(inv);

        // 检查玩家是否在国家中
        Optional<Nation> myNationOpt = nationService.nationOf(playerId);
        if (myNationOpt.isEmpty()) {
            inv.setItem(22, createItem(Material.BARRIER, "§c你不在任何国家中",
                "你需要先加入一个国家才能使用联盟功能"));
            player.openInventory(inv);
            return;
        }

        Nation myNation = myNationOpt.orElseThrow(() -> new IllegalStateException("Player must be in a nation"));
        Optional<Alliance> myAllianceOpt = allianceService.getNationAlliance(myNation.id());

        // 获取统计数据
        int allianceCount = allianceService.getAllianceCount();
        int myMemberCount = myAllianceOpt.map(a -> allianceService.getAllianceMembers(a.id()).size()).orElse(0);
        int pendingCount = allianceService.getPendingInvites(myNation.id()).size();

        // 联盟信息按钮
        if (myAllianceOpt.isPresent()) {
            Alliance myAlliance = myAllianceOpt.orElseThrow();
            List<String> lore = new ArrayList<>();
            lore.add("§7联盟名称: §f" + myAlliance.name());
            lore.add("§7成员数: §a" + myMemberCount);
            lore.add("");
            lore.add("§e点击查看联盟详情");
            inv.setItem(20, createItem(Material.EMERALD, "§a联盟信息", lore.toArray(new String[0])));
        } else {
            List<String> lore = new ArrayList<>();
            lore.add("§7你的国家还未加入任何联盟");
            lore.add("§7可以创建或加入现有联盟");
            lore.add("");
            lore.add("§e点击查看所有联盟");
            inv.setItem(20, createItem(Material.EMERALD_BLOCK, "§e创建/加入联盟", lore.toArray(new String[0])));
        }

        // 成员列表按钮
        if (myAllianceOpt.isPresent()) {
            inv.setItem(22, createMemberListItem(myAllianceOpt.orElseThrow()));
        } else {
            inv.setItem(22, createItem(Material.PLAYER_HEAD, "§b成员列表",
                "§7加入联盟后查看"));
        }

        // 联盟外交按钮
        if (myAllianceOpt.isPresent()) {
            int friendlyCount = allianceService.getFriendlyAlliances(myAllianceOpt.orElseThrow().id()).size();
            int hostileCount = allianceService.getHostileAlliances(myAllianceOpt.orElseThrow().id()).size();
            List<String> lore = new ArrayList<>();
            lore.add("§7友好联盟: §a" + friendlyCount);
            lore.add("§7敌对联盟: §c" + hostileCount);
            lore.add("");
            lore.add("§e点击管理外交关系");
            inv.setItem(24, createItem(Material.WHITE_BANNER, "§c联盟外交", lore.toArray(new String[0])));
        } else {
            inv.setItem(24, createItem(Material.WHITE_BANNER, "§7联盟外交",
                "§7加入联盟后使用"));
        }

        // 待处理邀请
        if (pendingCount > 0) {
            List<String> lore = new ArrayList<>();
            lore.add("§7待处理邀请: §e" + pendingCount);
            lore.add("");
            lore.add("§a点击查看邀请");
            inv.setItem(38, createItem(Material.BEACON, "§e待处理邀请 §c" + pendingCount,
                lore.toArray(new String[0])));
        } else {
            inv.setItem(38, createItem(Material.BEACON, "§7待处理邀请",
                "§7暂无邀请"));
        }

        // 所有联盟列表
        List<String> lore = new ArrayList<>();
        lore.add("§7当前联盟总数: §f" + allianceCount);
        lore.add("");
        lore.add("§e点击查看所有联盟");
        inv.setItem(40, createItem(Material.NETHER_STAR, "§b所有联盟", lore.toArray(new String[0])));

        // 公告按钮
        if (myAllianceOpt.isPresent()) {
            Optional<AllianceAnnouncement> ann = allianceService.getAnnouncement(myAllianceOpt.orElseThrow().id());
            List<String> annLore = new ArrayList<>();
            if (ann.isPresent()) {
                AllianceAnnouncement announcement = ann.orElseThrow();
                annLore.add("§7当前公告:");
                annLore.add("§e" + truncate(announcement.content(), 30));
            } else {
                annLore.add("§7暂无公告");
            }
            annLore.add("");
            annLore.add("§e点击发布/查看公告");
            inv.setItem(42, createItem(Material.BOOK, "§6联盟公告", annLore.toArray(new String[0])));
        } else {
            inv.setItem(42, createItem(Material.BOOK, "§7联盟公告",
                "§7加入联盟后使用"));
        }

        // 帮助按钮
        inv.setItem(49, createItem(Material.BOOK, "§a帮助", "§7查看联盟系统帮助"));

        player.openInventory(inv);
    }

    /**
     * 打开所有联盟列表
     */
    public void openAllianceListMenu(int page) {
        Inventory inv = Bukkit.createInventory(null, MENU_SIZE,
            Component.text("所有联盟").color(NamedTextColor.GREEN));

        fillBorder(inv);

        Collection<Alliance> alliances = allianceService.getAllAlliances();
        List<Alliance> allianceList = new ArrayList<>(alliances);

        int itemsPerPage = 36;
        int totalPages = Math.max(1, (allianceList.size() + itemsPerPage - 1) / itemsPerPage);
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, allianceList.size());

        Optional<Nation> myNationOpt = nationService.nationOf(playerId);
        Optional<UUID> myAllianceId = myNationOpt.flatMap(n -> allianceService.getNationAlliance(n.id()).map(Alliance::id));

        if (startIndex < allianceList.size()) {
            int slot = 10;
            for (int i = startIndex; i < endIndex; i++) {
                if (slot > 43) break;

                Alliance alliance = allianceList.get(i);
                List<AllianceMember> members = allianceService.getAllianceMembers(alliance.id());
                int memberCount = members.size();

                List<String> lore = new ArrayList<>();
                lore.add("§7成员数: §a" + memberCount);
                lore.add("§7盟主: §f" + getNationName(alliance.leaderId()));

                boolean isMyAlliance = myAllianceId.map(id -> id.equals(alliance.id())).orElse(false);
                boolean isMember = false;
                boolean canJoin = false;
                if (myNationOpt.isPresent()) {
                    Optional<Alliance> checkAllianceOpt = allianceService.getNationAlliance(myNationOpt.orElseThrow().id());
                    isMember = checkAllianceOpt.isPresent() && allianceService.isInAlliance(myNationOpt.orElseThrow().id());
                    canJoin = !isMember && !isMyAlliance;
                }

                if (isMyAlliance) {
                    lore.add("");
                    lore.add("§b[你的联盟]");
                } else if (isMember) {
                    lore.add("");
                    lore.add("§e[已加入其他联盟]");
                } else if (canJoin) {
                    lore.add("");
                    lore.add("§a点击申请加入");
                }

                Material material = isMyAlliance ? Material.EMERALD : Material.DIAMOND;
                inv.setItem(slot, createItem(material, "§f" + alliance.name(), lore.toArray(new String[0])));

                slot++;
                if (slot % 9 == 8) slot += 2;
            }
        } else {
            inv.setItem(22, createItem(Material.PAPER, "§7暂无更多联盟", "没有更多的联盟可以显示"));
        }

        // 分页控制
        if (page > 0) {
            inv.setItem(45, createItem(Material.ARROW, "§e上一页", "第 " + page + " 页"));
        }
        if (endIndex < allianceList.size()) {
            inv.setItem(53, createItem(Material.ARROW, "§e下一页", "第 " + (page + 2) + " 页"));
        }

        // 返回按钮
        inv.setItem(49, createItem(Material.ARROW, "§c返回", "返回上一级菜单"));

        player.openInventory(inv);
    }

    /**
     * 打开联盟详情菜单
     */
    public void openAllianceInfoMenu(UUID allianceId) {
        Optional<Alliance> allianceOpt = allianceService.getAlliance(allianceId);
        if (allianceOpt.isEmpty()) {
            player.sendMessage("§c联盟不存在");
            return;
        }

        Alliance alliance = allianceOpt.orElseThrow();
        List<AllianceMember> members = allianceService.getAllianceMembers(allianceId);

        Inventory inv = Bukkit.createInventory(null, MENU_SIZE,
            Component.text(alliance.name() + " - 详情").color(NamedTextColor.GOLD));

        fillBorder(inv);

        // 联盟基本信息
        int slot = 10;
        inv.setItem(slot++, createInfoItem("§7盟主", "§f" + getNationName(alliance.leaderId())));
        inv.setItem(slot++, createInfoItem("§7成员数", "§f" + members.size()));
        inv.setItem(slot++, createInfoItem("§7创建时间", "§f" + alliance.createdAt().toString()));

        if (alliance.emblem() != null && !alliance.emblem().isEmpty()) {
            inv.setItem(slot++, createInfoItem("§7徽章", "§f" + alliance.emblem()));
        }

        // 友好/敌对联盟
        var friendly = allianceService.getFriendlyAlliances(allianceId);
        var hostile = allianceService.getHostileAlliances(allianceId);

        inv.setItem(slot++, createInfoItem("§a友好联盟", "§f" + friendly.size()));
        inv.setItem(slot++, createInfoItem("§c敌对联盟", "§f" + hostile.size()));

        // 公告
        Optional<AllianceAnnouncement> ann = allianceService.getAnnouncement(allianceId);
        if (ann.isPresent()) {
            AllianceAnnouncement announcement = ann.orElseThrow();
            inv.setItem(4, createItem(Material.BOOK, "§6公告: §e" + truncate(announcement.content(), 20),
                "§7" + announcement.content()));
        }

        // 成员预览
        int previewSlot = 20;
        for (int i = 0; i < Math.min(9, members.size()); i++) {
            AllianceMember member = members.get(i);
            String roleStr = member.role() == AllianceMember.Role.LEADER ? "§6盟主" :
                            member.role() == AllianceMember.Role.OFFICER ? "§d官员" : "§7成员";
            inv.setItem(previewSlot++, createItem(Material.PLAYER_HEAD, roleStr + ": §f" + getNationName(member.nationId()),
                "§7加入时间: " + member.joinedAt()));
        }

        // 操作按钮
        Optional<Nation> myNationOpt = nationService.nationOf(playerId);
        Nation myNation = myNationOpt.orElse(null);
        boolean canJoin = myNation != null &&
                         !allianceService.isInAlliance(myNation.id()) &&
                         !allianceService.hasPendingInvite(myNation.id());

        if (canJoin) {
            inv.setItem(40, createItem(Material.LIME_DYE, "§a申请加入", "§7点击发送加入申请"));
        }

        // 返回按钮
        inv.setItem(49, createItem(Material.ARROW, "§c返回", "返回上一级菜单"));

        player.openInventory(inv);
    }

    /**
     * 打开成员列表菜单
     */
    public void openMemberListMenu(UUID allianceId, int page) {
        Optional<Alliance> allianceOpt = allianceService.getAlliance(allianceId);
        if (allianceOpt.isEmpty()) {
            player.sendMessage("§c联盟不存在");
            return;
        }

        Alliance alliance = allianceOpt.orElseThrow();
        List<AllianceMember> members = allianceService.getAllianceMembers(allianceId);

        Inventory inv = Bukkit.createInventory(null, MENU_SIZE,
            Component.text("成员列表 - " + alliance.name()).color(NamedTextColor.AQUA));

        fillBorder(inv);

        int itemsPerPage = 36;
        int totalPages = Math.max(1, (members.size() + 5) / 6);
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, members.size());

        int slot = 10;
        for (int i = startIndex; i < endIndex; i++) {
            if (slot > 43) break;

            AllianceMember member = members.get(i);
            String roleStr = switch (member.role()) {
                case LEADER -> "§6盟主";
                case OFFICER -> "§d官员";
                case MEMBER -> "§7成员";
            };

            List<String> lore = new ArrayList<>();
            lore.add(roleStr);
            lore.add("§7加入时间: §f" + member.joinedAt());
            lore.add("");
            lore.add("§e点击查看详情");

            Material material = member.role() == AllianceMember.Role.LEADER ? Material.GOLD_INGOT :
                               member.role() == AllianceMember.Role.OFFICER ? Material.IRON_INGOT : Material.IRON_NUGGET;
            inv.setItem(slot, createItem(material, "§f" + getNationName(member.nationId()), lore.toArray(new String[0])));

            slot++;
            if (slot % 9 == 8) slot += 2;
        }

        // 分页控制
        if (page > 0) {
            inv.setItem(45, createItem(Material.ARROW, "§e上一页", "第 " + page + " 页"));
        }
        if (endIndex < members.size()) {
            inv.setItem(53, createItem(Material.ARROW, "§e下一页", "第 " + (page + 2) + " 页"));
        }

        // 返回按钮
        inv.setItem(49, createItem(Material.ARROW, "§c返回", "返回上一级菜单"));

        player.openInventory(inv);
    }

    /**
     * 打开待处理邀请菜单
     */
    public void openPendingInvitesMenu() {
        Optional<Nation> myNationOpt = nationService.nationOf(playerId);
        if (myNationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何国家中");
            return;
        }

        List<AllianceInviteInfo> invites = allianceService.getPendingInvites(myNationOpt.orElseThrow().id());

        Inventory inv = Bukkit.createInventory(null, MENU_SIZE,
            Component.text("待处理邀请").color(NamedTextColor.YELLOW));

        fillBorder(inv);

        if (invites.isEmpty()) {
            inv.setItem(22, createItem(Material.PAPER, "§7暂无待处理邀请",
                "没有联盟向你发送邀请"));
        } else {
            int slot = 10;
            for (AllianceInviteInfo invite : invites) {
                if (slot > 43) break;

                Duration remaining = Duration.between(Instant.now(), invite.expiresAt());
                long hours = remaining.toHours();
                long minutes = remaining.toMinutes() % 60;

                List<String> lore = new ArrayList<>();
                lore.add("§7联盟: §f" + invite.allianceName());
                lore.add("§7剩余时间: §e" + hours + "小时 " + minutes + "分钟");
                lore.add("");
                lore.add("§a左键接受邀请");
                lore.add("§c右键拒绝邀请");

                inv.setItem(slot, createItem(Material.BEACON, "§6来自: §e" + invite.allianceName(),
                    lore.toArray(new String[0])));

                slot++;
                if (slot % 9 == 8) slot += 2;
            }
        }

        // 返回按钮
        inv.setItem(49, createItem(Material.ARROW, "§c返回", "返回上一级菜单"));

        player.openInventory(inv);
    }

    /**
     * 打开邀请国家菜单
     */
    public void openInviteNationMenu(int page) {
        Optional<Nation> myNationOpt = nationService.nationOf(playerId);
        if (myNationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何国家中");
            return;
        }

        Optional<Alliance> myAllianceOpt = allianceService.getNationAlliance(myNationOpt.orElseThrow().id());
        if (myAllianceOpt.isEmpty()) {
            player.sendMessage("§c你的国家不在任何联盟中");
            return;
        }

        Alliance myAlliance = myAllianceOpt.orElseThrow();

        // 检查是否是盟主或官员
        Optional<AllianceMember> myMemberOpt = allianceService.getAllianceMembers(myAlliance.id())
            .stream()
            .filter(m -> m.nationId().equals(myNationOpt.orElseThrow().id()))
            .findFirst();

        if (myMemberOpt.isEmpty() || myMemberOpt.orElseThrow().role() == AllianceMember.Role.MEMBER) {
            player.sendMessage("§c只有盟主和官员可以邀请国家");
            return;
        }

        List<Nation> nations = new ArrayList<>(nationService.nations());
        nations.removeIf(n -> n.id().equals(myNationOpt.orElseThrow().id())); // 移除自己的国家
        nations.removeIf(n -> allianceService.isInAlliance(n.id())); // 移除已在联盟的国家

        Inventory inv = Bukkit.createInventory(null, MENU_SIZE,
            Component.text("邀请国家 - " + myAlliance.name()).color(NamedTextColor.AQUA));

        fillBorder(inv);

        int itemsPerPage = 36;
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, nations.size());

        if (startIndex < nations.size()) {
            int slot = 10;
            for (int i = startIndex; i < endIndex; i++) {
                if (slot > 43) break;

                Nation targetNation = nations.get(i);

                List<String> lore = new ArrayList<>();
                lore.add("§7成员数: §f" + targetNation.members().size());
                lore.add("");
                lore.add("§a点击邀请");

                inv.setItem(slot, createItem(Material.LAPIS_LAZULI, "§f" + targetNation.name(),
                    lore.toArray(new String[0])));

                slot++;
                if (slot % 9 == 8) slot += 2;
            }
        }

        // 分页控制
        if (page > 0) {
            inv.setItem(45, createItem(Material.ARROW, "§e上一页", "第 " + page + " 页"));
        }
        if (endIndex < nations.size()) {
            inv.setItem(53, createItem(Material.ARROW, "§e下一页", "第 " + (page + 2) + " 页"));
        }

        // 返回按钮
        inv.setItem(49, createItem(Material.ARROW, "§c返回", "返回上一级菜单"));

        player.openInventory(inv);
    }

    // ==================== 辅助方法 ====================

    private ItemStack createMemberListItem(Alliance alliance) {
        List<AllianceMember> members = allianceService.getAllianceMembers(alliance.id());
        int memberCount = members.size();

        List<String> lore = new ArrayList<>();
        lore.add("§7成员数: §a" + memberCount);
        lore.add("");
        for (AllianceMember member : members) {
            if (lore.size() >= 6) {
                lore.add("§7...");
                break;
            }
            String roleStr = member.role() == AllianceMember.Role.LEADER ? "§6" : member.role() == AllianceMember.Role.OFFICER ? "§d" : "§7";
            lore.add(roleStr + "- " + getNationName(member.nationId()));
        }
        lore.add("");
        lore.add("§e点击查看所有成员");

        return createItem(Material.PLAYER_HEAD, "§b成员列表", lore.toArray(new String[0]));
    }

    private ItemStack createInfoItem(String label, String value) {
        return createItem(Material.PAPER, label + ": " + value, "");
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
            if (lore.length > 0 && lore[0] != null && !lore[0].isEmpty()) {
                List<Component> loreList = new ArrayList<>();
                for (String line : lore) {
                    if (line != null && !line.isEmpty()) {
                        loreList.add(Component.text(line).color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false));
                    }
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

    private String getNationName(NationId nationId) {
        return nationService.nationById(nationId)
            .map(Nation::name)
            .orElse("未知国家");
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}
