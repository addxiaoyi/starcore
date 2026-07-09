package dev.starcore.starcore.module.diplomacy.alliance.gui;

import dev.starcore.starcore.util.ColorCodes;
import dev.starcore.starcore.module.diplomacy.alliance.AllianceService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 联盟菜单交互监听器
 * 处理玩家在联盟 GUI 中的点击事件
 */
public class AllianceMenuListener implements Listener {

    private final AllianceService allianceService;
    private final NationService nationService;
    private final ConcurrentMap<UUID, AllianceMenuState> playerStates = new ConcurrentHashMap<>();

    public AllianceMenuListener(AllianceService allianceService, NationService nationService) {
        this.allianceService = allianceService;
        this.nationService = nationService;
    }

    /**
     * 菜单状态跟踪
     */
    private record AllianceMenuState(String menuType, int page) {}

    /**
     * 跟踪玩家菜单状态
     */
    public void trackState(UUID playerId, String menuType, int page) {
        playerStates.put(playerId, new AllianceMenuState(menuType, page));
    }

    /**
     * 清除玩家菜单状态
     */
    public void clearState(UUID playerId) {
        playerStates.remove(playerId);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        String title = event.getView().title().toString();

        // 检查是否是联盟菜单
        if (!title.contains("联盟")) {
            return;
        }

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        // 处理边框点击
        if (clickedItem.getType() == Material.BLACK_STAINED_GLASS_PANE) {
            return;
        }

        // 根据菜单标题处理点击
        if (title.contains("联盟管理")) {
            handleMainMenuClick(player, clickedItem);
        } else if (title.contains("联盟列表")) {
            handleAllianceListClick(player, clickedItem, event.isRightClick());
        } else if (title.contains("待处理邀请")) {
            handlePendingInvitesClick(player, clickedItem, event.isRightClick());
        } else if (title.contains("选择邀请国家")) {
            handleSendInviteClick(player, clickedItem);
        } else if (title.contains("联盟详情")) {
            handleAllianceDetailClick(player, clickedItem);
        }
    }

    private void handleMainMenuClick(Player player, ItemStack item) {
        Material material = item.getType();
        String name = getItemName(item);

        AllianceMenu menu = new AllianceMenu(allianceService, nationService, player.getUniqueId());

        if (name.contains("联盟列表")) {
            trackState(player.getUniqueId(), "alliance_list", 0);
            menu.openAllianceListMenu(player);
        } else if (name.contains("待处理邀请")) {
            trackState(player.getUniqueId(), "pending_invites", 0);
            menu.openPendingInvitesMenu(player);
        } else if (name.contains("联盟统计")) {
            // 发送统计消息
            AllianceService.AllianceStats stats = allianceService.getStats();
            player.sendMessage(ColorCodes.GOLD_BOLD + "==== 联盟统计 ====");
            player.sendMessage(ColorCodes.SECONDARY + "总联盟数: " + ColorCodes.GREEN + stats.totalAlliances());
            player.sendMessage(ColorCodes.SECONDARY + "待处理邀请: " + ColorCodes.YELLOW + stats.totalInvitesPending());
            player.sendMessage(ColorCodes.SECONDARY + "最大联盟规模: " + ColorCodes.GREEN + stats.largestAllianceSize());
            player.sendMessage(ColorCodes.SECONDARY + "最活跃国家: " + ColorCodes.AQUA + stats.mostActiveNation());
        } else if (name.contains("发送联盟邀请")) {
            trackState(player.getUniqueId(), "send_invite", 0);
            menu.openSendInviteMenu(player, 0);
        } else if (name.contains("联盟指南")) {
            player.sendMessage(ColorCodes.GOLD_BOLD + "==== 联盟指南 ====");
            player.sendMessage(ColorCodes.YELLOW + "1. 发送联盟邀请 " + ColorCodes.SECONDARY + "- 找到你想结盟的国家并发送邀请");
            player.sendMessage(ColorCodes.YELLOW + "2. 等待接受 " + ColorCodes.SECONDARY + "- 被邀请方需要在邀请过期前接受");
            player.sendMessage(ColorCodes.YELLOW + "3. 联盟生效 " + ColorCodes.SECONDARY + "- 接受后你们成为盟友");
            player.sendMessage(ColorCodes.YELLOW + "4. 解除联盟 " + ColorCodes.SECONDARY + "- 如需解除，点击联盟列表中的国家");
            player.sendMessage(ColorCodes.SECONDARY + "注意: 解除联盟后将有24小时冷却时间");
        } else if (name.contains("刷新")) {
            menu.openMainMenu(player);
        }
    }

    private void handleAllianceListClick(Player player, ItemStack item, boolean isRightClick) {
        Material material = item.getType();
        String name = getItemName(item);

        if (name.contains("返回")) {
            clearState(player.getUniqueId());
            AllianceMenu menu = new AllianceMenu(allianceService, nationService, player.getUniqueId());
            menu.openMainMenu(player);
            return;
        }

        if (material == Material.EMERALD) {
            // 点击了某个盟友
            Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
            if (myNationOpt.isEmpty()) return;

            // 从名称中提取盟友名称
            String allyName = name.replace(ColorCodes.GREEN, "").trim();
            Optional<Nation> targetNationOpt = nationService.nationByName(allyName);
            if (targetNationOpt.isEmpty()) {
                player.sendMessage(ColorCodes.ERROR + "找不到该国家");
                return;
            }

            Nation myNation = myNationOpt.get();
            NationId targetId = targetNationOpt.get().id();

            // 右键解除联盟
            if (isRightClick) {
                if (allianceService.breakAlliance(myNation.id(), targetId, myNation.id())) {
                    player.sendMessage(ColorCodes.ERROR + "已解除与 " + allyName + " 的联盟关系");
                    // 刷新列表
                    AllianceMenu menu = new AllianceMenu(allianceService, nationService, player.getUniqueId());
                    menu.openAllianceListMenu(player);
                }
            } else {
                // 左键查看详情
                AllianceMenu menu = new AllianceMenu(allianceService, nationService, player.getUniqueId());
                menu.openAllianceDetailMenu(player, targetId);
            }
        }
    }

    private void handlePendingInvitesClick(Player player, ItemStack item, boolean isRightClick) {
        Material material = item.getType();
        String name = getItemName(item);

        if (name.contains("返回")) {
            clearState(player.getUniqueId());
            AllianceMenu menu = new AllianceMenu(allianceService, nationService, player.getUniqueId());
            menu.openMainMenu(player);
            return;
        }

        if (material == Material.BEACON) {
            // 点击了某个邀请
            String inviterName = name.replace(ColorCodes.GOLD + "来自: ", "");
            Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
            if (myNationOpt.isEmpty()) return;

            NationId myNationId = myNationOpt.get().id();

            // 左键接受，右键拒绝
            if (isRightClick) {
                // 拒绝邀请
                allianceService.rejectInvite(myNationId, inviterName);
                player.sendMessage(ColorCodes.SECONDARY + "已拒绝来自 " + inviterName + " 的联盟邀请");
                // 刷新列表
                AllianceMenu menu = new AllianceMenu(allianceService, nationService, player.getUniqueId());
                menu.openPendingInvitesMenu(player);
            } else {
                // 接受邀请
                var result = allianceService.acceptInvite(myNationId, inviterName);
                player.sendMessage(result.success() ? ColorCodes.SUCCESS + result.message() : ColorCodes.ERROR + result.message());
                if (result.success()) {
                    // 刷新列表
                    AllianceMenu menu = new AllianceMenu(allianceService, nationService, player.getUniqueId());
                    menu.openPendingInvitesMenu(player);
                }
            }
        }
    }

    private void handleSendInviteClick(Player player, ItemStack item) {
        Material material = item.getType();
        String name = getItemName(item);

        if (name.contains("返回")) {
            clearState(player.getUniqueId());
            AllianceMenu menu = new AllianceMenu(allianceService, nationService, player.getUniqueId());
            menu.openMainMenu(player);
            return;
        }

        if (name.contains("上一页")) {
            AllianceMenuState state = playerStates.get(player.getUniqueId());
            int page = state != null ? state.page() - 1 : 0;
            if (page >= 0) {
                trackState(player.getUniqueId(), "send_invite", page);
                AllianceMenu menu = new AllianceMenu(allianceService, nationService, player.getUniqueId());
                menu.openSendInviteMenu(player, page);
            }
            return;
        }

        if (name.contains("下一页")) {
            AllianceMenuState state = playerStates.get(player.getUniqueId());
            int page = state != null ? state.page() + 1 : 1;
            trackState(player.getUniqueId(), "send_invite", page);
            AllianceMenu menu = new AllianceMenu(allianceService, nationService, player.getUniqueId());
            menu.openSendInviteMenu(player, page);
            return;
        }

        // 点击了某个国家
        String nationName = name.replace(ColorCodes.WHITE, "");
        Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
        Optional<Nation> targetNationOpt = nationService.nationByName(nationName);
        if (myNationOpt.isEmpty() || targetNationOpt.isEmpty()) return;

        Nation myNation = myNationOpt.get();
        NationId myNationId = myNation.id();
        NationId targetId = targetNationOpt.get().id();

        if (myNationId.equals(targetId)) {
            player.sendMessage(ColorCodes.ERROR + "不能邀请自己的国家");
            return;
        }

        // 发送邀请
        var result = allianceService.sendInvite(myNationId, targetId);
        player.sendMessage(result.success() ? ColorCodes.SUCCESS + result.message() : ColorCodes.ERROR + result.message());

        if (result.success()) {
            // 通知目标国家
            broadcastToNation(targetId, String.format(
                ColorCodes.GOLD + "%s " + ColorCodes.YELLOW + "向你们发送了联盟邀请！",
                myNation.name()
            ));
        }

        // 刷新列表
        AllianceMenuState state = playerStates.get(player.getUniqueId());
        int page = state != null ? state.page() : 0;
        AllianceMenu menu = new AllianceMenu(allianceService, nationService, player.getUniqueId());
        menu.openSendInviteMenu(player, page);
    }

    private void handleAllianceDetailClick(Player player, ItemStack item) {
        // 处理联盟详情菜单点击
        String name = getItemName(item);

        if (name.contains("返回")) {
            // 返回联盟列表
            AllianceMenu menu = new AllianceMenu(allianceService, nationService, player.getUniqueId());
            menu.openAllianceListMenu(player);
            return;
        }

        if (name.contains("升级")) {
            player.sendMessage(ColorCodes.YELLOW + "联盟升级功能正在开发中...");
        } else if (name.contains("解除")) {
            // 解除联盟
            Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
            if (myNationOpt.isEmpty()) return;

            // 获取点击的盟友名称
            String allyName = name.contains("盟国:") ?
                name.substring(name.indexOf("盟国:") + 4).trim() : "";

            if (!allyName.isEmpty()) {
                Optional<Nation> targetOpt = nationService.nationByName(allyName);
                if (targetOpt.isPresent()) {
                    boolean success = allianceService.breakAlliance(
                        myNationOpt.get().id(),
                        targetOpt.get().id(),
                        myNationOpt.get().id()
                    );

                    if (success) {
                        player.sendMessage(ColorCodes.ERROR + "已解除与 " + allyName + " 的联盟关系");
                        // 通知对方国家
                        broadcastToNation(targetOpt.get().id(),
                            ColorCodes.ERROR + myNationOpt.get().name() + " 解除与你们的联盟关系");
                        // 返回联盟列表
                        AllianceMenu menu = new AllianceMenu(allianceService, nationService, player.getUniqueId());
                        menu.openAllianceListMenu(player);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            // 清理状态
            clearState(player.getUniqueId());
        }
    }

    // audit H-001: 修复 PlayerQuitEvent 未清理 playerStates Map 导致的内存泄漏
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearState(event.getPlayer().getUniqueId());
    }

    // 辅助方法
    private String getItemName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return "";
        }
        var displayName = item.getItemMeta().displayName();
        if (displayName == null) {
            return "";
        }
        return displayName.toString();
    }

    private void broadcastToNation(NationId nationId, String message) {
        Optional<Nation> nationOpt = nationService.nationById(nationId);
        if (nationOpt.isEmpty()) {
            return;
        }

        Nation nation = nationOpt.get();
        for (var member : nation.members()) {
            var player = Bukkit.getPlayer(member.playerId());
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }
}
