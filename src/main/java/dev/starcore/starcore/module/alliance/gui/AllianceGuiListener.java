package dev.starcore.starcore.module.alliance.gui;

import dev.starcore.starcore.module.alliance.AllianceGui;
import dev.starcore.starcore.module.alliance.AllianceService;
import dev.starcore.starcore.module.alliance.AllianceService.*;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.util.ColorCodes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 联盟系统 GUI 事件监听器
 *
 * 处理联盟 GUI 菜单的点击事件：
 * - 主菜单操作
 * - 联盟列表浏览
 * - 成员管理
 * - 邀请处理
 * - 外交关系设置
 */
public class AllianceGuiListener implements Listener {

    private final AllianceService allianceService;
    private final NationService nationService;

    // 打开的菜单状态追踪（线程安全）
    private final Map<UUID, MenuState> playerMenus = new ConcurrentHashMap<>();

    public AllianceGuiListener(AllianceService allianceService, NationService nationService) {
        this.allianceService = allianceService;
        this.nationService = nationService;
    }

    /**
     * 菜单状态
     */
    private static class MenuState {
        final String menuType;
        final UUID targetId;
        final int page;

        MenuState(String menuType, UUID targetId, int page) {
            this.menuType = menuType;
            this.targetId = targetId;
            this.page = page;
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Component title = event.getView().title();
        String titleStr = PlainTextComponentSerializer.plainText().serialize(title);

        // 检查是否是联盟菜单
        if (!titleStr.contains("联盟")) {
            return;
        }

        event.setCancelled(true);

        int slot = event.getRawSlot();
        ItemStack item = event.getCurrentItem();

        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        // 获取菜单类型
        String menuType = getMenuType(titleStr);
        UUID playerId = player.getUniqueId();

        switch (menuType) {
            case "联盟管理" -> handleMainMenu(player, slot, item);
            case "所有联盟" -> handleAllianceListMenu(player, slot, item, titleStr);
            case "详情" -> handleAllianceInfoMenu(player, slot, item, titleStr);
            case "成员列表" -> handleMemberListMenu(player, slot, item, titleStr);
            case "待处理邀请" -> handlePendingInvitesMenu(player, slot, item);
            case "邀请国家" -> handleInviteNationMenu(player, slot, item);
            default -> {
                // 通用返回按钮处理
                if (isBackButton(item)) {
                    openMainMenu(player);
                }
            }
        }
    }

    /**
     * 处理主菜单点击
     */
    private void handleMainMenu(Player player, int slot, ItemStack item) {
        String itemName = getItemName(item);

        switch (slot) {
            case 20 -> {
                // 联盟信息/创建加入
                Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
                if (myNationOpt.isEmpty()) {
                    player.sendMessage("§c你需要先加入一个国家");
                    return;
                }

                Optional<Alliance> myAllianceOpt = allianceService.getNationAlliance(myNationOpt.orElseThrow().id());
                if (myAllianceOpt.isPresent()) {
                    openAllianceInfo(player, myAllianceOpt.orElseThrow().id());
                } else {
                    openAllianceList(player, 0);
                }
            }
            case 22 -> {
                // 成员列表
                Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
                if (myNationOpt.isEmpty()) {
                    return;
                }

                Optional<Alliance> myAllianceOpt = allianceService.getNationAlliance(myNationOpt.orElseThrow().id());
                if (myAllianceOpt.isPresent()) {
                    openMemberList(player, myAllianceOpt.orElseThrow().id(), 0);
                }
            }
            case 24 -> {
                // 联盟外交
                Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
                if (myNationOpt.isEmpty()) {
                    return;
                }

                Optional<Alliance> myAllianceOpt = allianceService.getNationAlliance(myNationOpt.orElseThrow().id());
                if (myAllianceOpt.isPresent()) {
                    // 暂时只打开联盟详情
                    openAllianceInfo(player, myAllianceOpt.orElseThrow().id());
                }
            }
            case 38 -> {
                // 待处理邀请
                openPendingInvites(player);
            }
            case 40 -> {
                // 所有联盟列表
                openAllianceList(player, 0);
            }
            case 42 -> {
                // 联盟公告
                Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
                if (myNationOpt.isEmpty()) {
                    return;
                }

                Optional<Alliance> myAllianceOpt = allianceService.getNationAlliance(myNationOpt.orElseThrow().id());
                if (myAllianceOpt.isPresent()) {
                    player.closeInventory();
                    player.sendMessage("§6请输入公告内容:");
                    player.sendMessage("§7使用 /alliance announcement <内容> 来发布公告");
                }
            }
            case 49 -> {
                // 帮助
                player.closeInventory();
                player.sendMessage("§6=== 联盟系统帮助 ===");
                player.sendMessage("§e/fed create <名称> §7- 创建联盟");
                player.sendMessage("§e/fed info §7- 查看联盟信息");
                player.sendMessage("§e/fed members §7- 查看成员");
                player.sendMessage("§e/fed invite <国家> §7- 邀请加入");
                player.sendMessage("§e/fed accept §7- 接受邀请");
                player.sendMessage("§e/fed leave §7- 离开联盟");
            }
        }
    }

    /**
     * 处理联盟列表菜单点击
     */
    private void handleAllianceListMenu(Player player, int slot, ItemStack item, String title) {
        if (isBackButton(item)) {
            openMainMenu(player);
            return;
        }

        // 提取页码
        int currentPage = 0;
        try {
            int start = title.indexOf("第") + 1;
            int end = title.indexOf("页");
            if (start > 0 && end > start) {
                currentPage = Integer.parseInt(title.substring(start, end)) - 1;
            }
        } catch (Exception e) {
            Logger.getLogger(AllianceGuiListener.class.getName()).warning("解析页码失败: " + e.getMessage());
        }
                        // 静默跳过，保持数据兼容

        // 分页按钮
        if (slot == 45) {
            // 上一页
            openAllianceList(player, currentPage - 1);
            return;
        }
        if (slot == 53) {
            // 下一页
            openAllianceList(player, currentPage + 1);
            return;
        }

        // 联盟条目（槽位 10-43，但跳过边框）
        if (slot >= 10 && slot <= 43) {
            // 计算实际索引
            int borderOffsets = (slot / 9) * 2;
            if (slot % 9 == 0) return; // 边框
            int index = (slot - 10) - borderOffsets + (currentPage * 36);

            Collection<Alliance> alliances = allianceService.getAllAlliances();
            List<Alliance> allianceList = new ArrayList<>(alliances);

            if (index >= 0 && index < allianceList.size()) {
                Alliance alliance = allianceList.get(index);
                openAllianceInfo(player, alliance.id());
            }
        }
    }

    /**
     * 处理联盟详情菜单点击
     */
    private void handleAllianceInfoMenu(Player player, int slot, ItemStack item, String title) {
        if (isBackButton(item)) {
            openAllianceList(player, 0);
            return;
        }

        // 提取联盟 ID（从标题）
        // 简化处理：从列表菜单进入时会设置状态

        // 申请加入按钮
        if (slot == 40) {
            Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
            if (myNationOpt.isEmpty()) {
                player.sendMessage("§c你不在任何国家中");
                return;
            }

            if (allianceService.isInAlliance(myNationOpt.orElseThrow().id())) {
                player.sendMessage("§c你已在联盟中");
                return;
            }

            if (allianceService.hasPendingInvite(myNationOpt.orElseThrow().id())) {
                player.sendMessage("§c你已有待处理的邀请");
                return;
            }

            // 实现申请加入逻辑 - 向联盟发送加入申请
            NationId nationId = myNationOpt.orElseThrow().id();
            String allianceName = getItemName(item);
            Optional<Alliance> targetAlliance = allianceService.getAllianceByName(allianceName);

            if (targetAlliance.isEmpty()) {
                player.sendMessage("§c联盟不存在: " + allianceName);
                return;
            }

            // 发送申请 - 使用 inviteNation 方法
            InviteResult result = allianceService.inviteNation(targetAlliance.orElseThrow().id(), nationId);
            if (result.success()) {
                player.sendMessage("§a已向联盟 §e" + allianceName + " §a发送加入申请");
                player.sendMessage("§7等待联盟管理员审批...");
            } else {
                player.sendMessage("§c" + result.message());
            }
        }
    }

    /**
     * 处理成员列表菜单点击
     */
    private void handleMemberListMenu(Player player, int slot, ItemStack item, String title) {
        if (isBackButton(item)) {
            openMainMenu(player);
            return;
        }

        // 提取页码
        int currentPage = 0;
        try {
            int start = title.indexOf("第") + 1;
            int end = title.indexOf("页");
            if (start > 0 && end > start) {
                currentPage = Integer.parseInt(title.substring(start, end)) - 1;
            }
        } catch (Exception e) {
            Logger.getLogger(AllianceGuiListener.class.getName()).warning("解析页码失败: " + e.getMessage());
        }
                        // 静默跳过，保持数据兼容

        // 分页按钮
        if (slot == 45) {
            // 上一页
            // 需要获取当前联盟 ID
            player.closeInventory();
            return;
        }
        if (slot == 53) {
            // 下一页
            player.closeInventory();
            return;
        }
    }

    /**
     * 处理待处理邀请菜单点击
     */
    private void handlePendingInvitesMenu(Player player, int slot, ItemStack item) {
        if (isBackButton(item)) {
            openMainMenu(player);
            return;
        }

        if (slot >= 10 && slot <= 43) {
            // 计算邀请索引
            int borderOffsets = (slot / 9) * 2;
            if (slot % 9 == 0) return;
            int index = (slot - 10) - borderOffsets;

            Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
            if (myNationOpt.isEmpty()) {
                return;
            }

            List<AllianceInviteInfo> invites = allianceService.getPendingInvites(myNationOpt.orElseThrow().id());
            if (index >= 0 && index < invites.size()) {
                AllianceInviteInfo invite = invites.get(index);

                if (item.getType() == Material.BEACON) {
                    // 左键接受
                    AllianceResult result = allianceService.acceptInvite(myNationOpt.orElseThrow().id(), invite.allianceId());
                    player.sendMessage(result.success() ? "§a" + result.message() : "§c" + result.message());
                    player.closeInventory();
                }
            }
        }
    }

    /**
     * 处理邀请国家菜单点击
     */
    private void handleInviteNationMenu(Player player, int slot, ItemStack item) {
        if (isBackButton(item)) {
            openMainMenu(player);
            return;
        }

        // 分页按钮处理
        // ...
    }

    // ==================== 菜单打开方法 ====================

    private void openMainMenu(Player player) {
        AllianceGui gui = new AllianceGui(allianceService, nationService, player);
        gui.openMainMenu();
    }

    private void openAllianceList(Player player, int page) {
        AllianceGui gui = new AllianceGui(allianceService, nationService, player);
        gui.openAllianceListMenu(page);
    }

    private void openAllianceInfo(Player player, UUID allianceId) {
        AllianceGui gui = new AllianceGui(allianceService, nationService, player);
        gui.openAllianceInfoMenu(allianceId);
    }

    private void openMemberList(Player player, UUID allianceId, int page) {
        AllianceGui gui = new AllianceGui(allianceService, nationService, player);
        gui.openMemberListMenu(allianceId, page);
    }

    private void openPendingInvites(Player player) {
        AllianceGui gui = new AllianceGui(allianceService, nationService, player);
        gui.openPendingInvitesMenu();
    }

    // ==================== 辅助方法 ====================

    private String getMenuType(String title) {
        if (title.contains("联盟管理")) return "联盟管理";
        if (title.contains("所有联盟")) return "所有联盟";
        if (title.contains("详情")) return "详情";
        if (title.contains("成员列表")) return "成员列表";
        if (title.contains("待处理邀请")) return "待处理邀请";
        if (title.contains("邀请国家")) return "邀请国家";
        if (title.contains("公告")) return "公告";
        return "未知";
    }

    private String getItemName(ItemStack item) {
        if (item == null || !item.hasItemMeta() || item.getItemMeta().displayName() == null) {
            return "";
        }
        return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
    }

    private boolean isBackButton(ItemStack item) {
        if (item == null) return false;
        return item.getType() == Material.ARROW && getItemName(item).contains("返回");
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // 清理菜单状态
        if (event.getPlayer() instanceof Player player) {
            playerMenus.remove(player.getUniqueId());
        }
    }

    // audit H-001: 修复 PlayerQuitEvent 未清理 playerMenus Map 导致的内存泄漏
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerMenus.remove(event.getPlayer().getUniqueId());
    }
}
