package dev.starcore.starcore.module.diplomacy.military.gui;

import dev.starcore.starcore.foundation.gui.ButtonFactory;
import dev.starcore.starcore.module.diplomacy.military.MilitaryAllianceService;
import dev.starcore.starcore.module.diplomacy.military.MilitaryAllianceService.*;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 军事联盟 GUI 菜单监听器
 * 处理玩家与军事联盟菜单的交互
 */
public class MilitaryAllianceMenuListener implements Listener {

    private final MilitaryAllianceMenu menu;
    private final MilitaryAllianceService allianceService;
    private final NationService nationService;

    private final Map<UUID, MenuState> playerStates = new ConcurrentHashMap<>();

    public MilitaryAllianceMenuListener(
            MilitaryAllianceService allianceService,
            NationService nationService
    ) {
        this.allianceService = allianceService;
        this.nationService = nationService;
        this.menu = new MilitaryAllianceMenu(allianceService, nationService);
    }

    /**
     * 菜单状态跟踪
     */
    private record MenuState(String menuType, int page, NationId targetId, PactType pactType) {}

    /**
     * 获取菜单实例
     */
    public MilitaryAllianceMenu getMenu() {
        return menu;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!title.contains("军事联盟") && !title.contains("条约") && !title.contains("签约")) {
            return;
        }

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        // 播放点击音效
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);

        // 处理边框点击
        if (clickedItem.getType() == Material.BLUE_STAINED_GLASS_PANE ||
            clickedItem.getType() == Material.BLACK_STAINED_GLASS_PANE ||
            clickedItem.getType() == Material.YELLOW_STAINED_GLASS_PANE) {
            return;
        }

        // 处理返回按钮
        if (isBackButton(clickedItem)) {
            handleBackButton(player);
            return;
        }

        // 处理上一页/下一页
        if (isPrevButton(clickedItem)) {
            handlePrevButton(player, title);
            return;
        }
        if (isNextButton(clickedItem)) {
            handleNextButton(player, title);
            return;
        }

        // 根据菜单标题处理点击
        if (title.contains("军事联盟中心") || title.contains("⚔️ 军事联盟") && !title.contains("条约")) {
            handleMainMenuClick(player, clickedItem);
        } else if (title.contains("我的条约") || title.contains("⚔️ 我的条约")) {
            handleMyPactsClick(player, clickedItem, event.isRightClick());
        } else if (title.contains("待处理邀请") || title.contains("⚔️ 待处理邀请")) {
            handlePendingInvitesClick(player, clickedItem, event.isRightClick());
        } else if (title.contains("选择签约国家") || title.contains("⚔️ 选择签约国家")) {
            handleSelectNationClick(player, clickedItem);
        } else if (title.contains("选择条约类型") || title.contains("⚔️ 选择条约类型")) {
            handleSelectPactTypeClick(player, clickedItem);
        } else if (title.contains("条约详情") || title.contains("⚔️ 条约详情")) {
            handlePactDetailClick(player, clickedItem, event.isRightClick());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            playerStates.remove(player.getUniqueId());
        }
    }

    // ==================== 菜单点击处理 ====================

    private void handleMainMenuClick(Player player, ItemStack item) {
        String name = getItemName(item);

        if (name.contains("我的条约")) {
            playerStates.put(player.getUniqueId(), new MenuState("my_pacts", 0, null, null));
            menu.openMyPactsMenu(player, 0);
        } else if (name.contains("待处理邀请")) {
            playerStates.put(player.getUniqueId(), new MenuState("pending", 0, null, null));
            menu.openPendingInvitesMenu(player);
        } else if (name.contains("发起签约")) {
            playerStates.put(player.getUniqueId(), new MenuState("select_nation", 0, null, null));
            menu.openSelectNationMenu(player, 0);
        }
    }

    private void handleMyPactsClick(Player player, ItemStack item, boolean isRightClick) {
        String name = getItemName(item);
        Material material = item.getType();

        // 检查是否是分页按钮
        if (name.contains("上一页")) {
            MenuState state = playerStates.get(player.getUniqueId());
            int page = state != null ? state.page() - 1 : 0;
            if (page >= 0) {
                playerStates.put(player.getUniqueId(), new MenuState("my_pacts", page, null, null));
                menu.openMyPactsMenu(player, page);
            }
            return;
        }
        if (name.contains("下一页")) {
            MenuState state = playerStates.get(player.getUniqueId());
            int page = state != null ? state.page() + 1 : 1;
            playerStates.put(player.getUniqueId(), new MenuState("my_pacts", page, null, null));
            menu.openMyPactsMenu(player, page);
            return;
        }

        // 检查是否是条约物品
        if (material == Material.PAPER || material == Material.IRON_SWORD ||
            material == Material.DIAMOND_SWORD || material == Material.NETHER_STAR) {
            // 获取玩家国家ID
            Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
            if (myNationOpt.isEmpty()) return;
            NationId playerNationId = myNationOpt.get().id();

            // 从名称中提取国家名称
            String nationName = name.replace("§b⚔️ ", "").trim();
            Optional<Nation> targetOpt = nationService.nationByName(nationName);
            if (targetOpt.isEmpty()) return;

            NationId targetId = targetOpt.get().id();

            if (isRightClick) {
                // 右键解除条约
                handleBreakPact(player, targetId);
            } else {
                // 左键查看详情
                playerStates.put(player.getUniqueId(), new MenuState("pact_detail", 0, targetId, null));
                menu.openPactDetailMenu(player, targetId);
            }
        }
    }

    private void handlePendingInvitesClick(Player player, ItemStack item, boolean isRightClick) {
        String name = getItemName(item);
        Material material = item.getType();

        if (material == Material.BEACON && name.contains("来自:")) {
            // 提取邀请方名称
            String inviterName = name.replace("§e来自: ", "").trim();
            // 获取玩家国家ID
            Optional<Nation> myNationOpt = nationService.nationOf(player.getUniqueId());
            if (myNationOpt.isEmpty()) return;
            NationId playerNationId = myNationOpt.get().id();

            if (isRightClick) {
                // 拒绝邀请
                allianceService.rejectPactInvite(playerNationId, inviterName);
                player.sendMessage("§7已拒绝来自 " + inviterName + " 的军事联盟邀请");
                menu.openPendingInvitesMenu(player);
            } else {
                // 接受邀请
                PactResult result = allianceService.acceptPactInvite(playerNationId, inviterName);
                if (result.success()) {
                    player.sendMessage("§a" + result.message());
                    // 通知对方
                    broadcastToNation(inviterName, "§a你的军事联盟邀请已被 " +
                        nationService.nationById(playerNationId).map(Nation::name).orElse("某国") + " 接受！");
                } else {
                    player.sendMessage("§c" + result.message());
                }
                menu.openPendingInvitesMenu(player);
            }
        }
    }

    private void handleSelectNationClick(Player player, ItemStack item) {
        String name = getItemName(item);

        // 检查分页按钮
        if (name.contains("上一页")) {
            MenuState state = playerStates.get(player.getUniqueId());
            int page = state != null ? state.page() - 1 : 0;
            if (page >= 0) {
                playerStates.put(player.getUniqueId(), new MenuState("select_nation", page, null, null));
                menu.openSelectNationMenu(player, page);
            }
            return;
        }
        if (name.contains("下一页")) {
            MenuState state = playerStates.get(player.getUniqueId());
            int page = state != null ? state.page() + 1 : 1;
            playerStates.put(player.getUniqueId(), new MenuState("select_nation", page, null, null));
            menu.openSelectNationMenu(player, page);
            return;
        }

        // 点击了国家
        if (item.getType() == Material.DIAMOND) {
            String nationName = name.replace("§f", "").trim();
            Optional<Nation> targetOpt = nationService.nationByName(nationName);
            if (targetOpt.isEmpty()) {
                player.sendMessage("§c找不到该国家");
                return;
            }

            NationId targetId = targetOpt.get().id();
            playerStates.put(player.getUniqueId(), new MenuState("select_pact_type", 0, targetId, null));
            menu.openSelectPactTypeMenu(player, targetId);
        }
    }

    private void handleSelectPactTypeClick(Player player, ItemStack item) {
        String name = getItemName(item);
        Material material = item.getType();

        // 提取条约类型
        PactType pactType = null;
        if (name.contains("观察员国") || material == Material.PAPER) {
            pactType = PactType.OBSERVER;
        } else if (name.contains("防御同盟") || material == Material.IRON_SWORD) {
            pactType = PactType.DEFENSIVE;
        } else if (name.contains("全面同盟") || material == Material.DIAMOND_SWORD) {
            pactType = PactType.FULL_ALLIANCE;
        } else if (name.contains("军事一体化") || material == Material.NETHER_STAR) {
            pactType = PactType.INTEGRATED;
        }

        if (pactType != null) {
            MenuState state = playerStates.get(player.getUniqueId());
            NationId targetId = state != null ? state.targetId() : null;
            if (targetId == null) {
                player.sendMessage("§c未选择目标国家");
                return;
            }

            // 发送签约邀请
            NationId playerNationId = nationService.nationOf(player.getUniqueId())
                .map(Nation::getId)
                .orElse(null);
            if (playerNationId == null) {
                player.sendMessage("§c你不在任何国家中");
                return;
            }

            PactInviteResult result = allianceService.sendPactInvite(playerNationId, targetId, pactType);
            player.sendMessage(result.success() ? "§a" + result.message() : "§c" + result.message());

            if (result.success()) {
                // 通知目标国家
                String targetName = nationService.nationById(targetId)
                    .map(Nation::name)
                    .orElse("某国");
                broadcastToNation(targetId, String.format(
                    "§e%s §a向你发送了 %s 军事联盟邀请！",
                    nationService.nationById(playerNationId).map(Nation::name).orElse("某国"),
                    pactType.displayName()
                ));
            }

            // 返回主菜单
            playerStates.put(player.getUniqueId(), new MenuState("main", 0, null, null));
            menu.openMainMenu(player);
        }
    }

    private void handlePactDetailClick(Player player, ItemStack item, boolean isRightClick) {
        String name = getItemName(item);

        if (name.contains("升级条约")) {
            player.sendMessage("§e请在选择条约类型菜单中选择要升级到的类型");
            // 可以扩展为打开升级菜单
        } else if (name.contains("解除条约")) {
            MenuState state = playerStates.get(player.getUniqueId());
            NationId targetId = state != null ? state.targetId() : null;
            if (targetId != null) {
                handleBreakPact(player, targetId);
            }
        }
    }

    // ==================== 导航处理 ====================

    private void handleBackButton(Player player) {
        MenuState state = playerStates.get(player.getUniqueId());
        if (state == null) {
            menu.openMainMenu(player);
            return;
        }

        switch (state.menuType()) {
            case "my_pacts", "pending" -> menu.openMainMenu(player);
            case "select_nation" -> menu.openMainMenu(player);
            case "select_pact_type" -> {
                if (state.targetId() != null) {
                    playerStates.put(player.getUniqueId(), new MenuState("select_nation", 0, null, null));
                    menu.openSelectNationMenu(player, 0);
                } else {
                    menu.openMainMenu(player);
                }
            }
            case "pact_detail" -> {
                playerStates.put(player.getUniqueId(), new MenuState("my_pacts", 0, null, null));
                menu.openMyPactsMenu(player, 0);
            }
            default -> menu.openMainMenu(player);
        }
    }

    private void handlePrevButton(Player player, String title) {
        MenuState state = playerStates.get(player.getUniqueId());
        int page = state != null ? Math.max(0, state.page() - 1) : 0;

        if (title.contains("我的条约")) {
            playerStates.put(player.getUniqueId(), new MenuState("my_pacts", page, null, null));
            menu.openMyPactsMenu(player, page);
        } else if (title.contains("选择签约国家")) {
            playerStates.put(player.getUniqueId(), new MenuState("select_nation", page, null, null));
            menu.openSelectNationMenu(player, page);
        }
    }

    private void handleNextButton(Player player, String title) {
        MenuState state = playerStates.get(player.getUniqueId());
        int page = state != null ? state.page() + 1 : 1;

        if (title.contains("我的条约")) {
            playerStates.put(player.getUniqueId(), new MenuState("my_pacts", page, null, null));
            menu.openMyPactsMenu(player, page);
        } else if (title.contains("选择签约国家")) {
            playerStates.put(player.getUniqueId(), new MenuState("select_nation", page, null, null));
            menu.openSelectNationMenu(player, page);
        }
    }

    // ==================== 业务操作 ====================

    private void handleBreakPact(Player player, NationId targetId) {
        NationId playerNationId = nationService.nationOf(player.getUniqueId())
            .map(Nation::getId)
            .orElse(null);
        if (playerNationId == null) {
            player.sendMessage("§c你不在任何国家中");
            return;
        }

        String targetName = nationService.nationById(targetId)
            .map(Nation::name)
            .orElse("某国");

        boolean success = allianceService.breakPact(playerNationId, targetId, playerNationId);
        if (success) {
            player.sendMessage("§c已解除与 " + targetName + " 的军事条约");
            broadcastToNation(targetId, "§c" +
                nationService.nationById(playerNationId).map(Nation::name).orElse("某国") +
                " 解除与你们的军事条约");

            // 返回条约列表
            playerStates.put(player.getUniqueId(), new MenuState("my_pacts", 0, null, null));
            menu.openMyPactsMenu(player, 0);
        } else {
            player.sendMessage("§c解除条约失败");
        }
    }

    // ==================== 辅助方法 ====================

    private boolean isBackButton(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        var displayName = item.getItemMeta().displayName();
        if (displayName == null) return false;
        String name = PlainTextComponentSerializer.plainText().serialize(displayName);
        return name.contains("返回");
    }

    private boolean isPrevButton(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        var displayName = item.getItemMeta().displayName();
        if (displayName == null) return false;
        String name = PlainTextComponentSerializer.plainText().serialize(displayName);
        return name.contains("上一页");
    }

    private boolean isNextButton(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        var displayName = item.getItemMeta().displayName();
        if (displayName == null) return false;
        String name = PlainTextComponentSerializer.plainText().serialize(displayName);
        return name.contains("下一页");
    }

    private String getItemName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return "";
        var displayName = item.getItemMeta().displayName();
        if (displayName == null) return "";
        return PlainTextComponentSerializer.plainText().serialize(displayName);
    }

    private void broadcastToNation(String nationName, String message) {
        nationService.nations().stream()
            .filter(n -> n.name().equals(nationName))
            .findFirst()
            .ifPresent(nation -> {
                for (var member : nation.members()) {
                    var player = Bukkit.getPlayer(member.playerId());
                    if (player != null) {
                        player.sendMessage(message);
                    }
                }
            });
    }

    private void broadcastToNation(NationId nationId, String message) {
        nationService.nationById(nationId).ifPresent(nation -> {
            for (var member : nation.members()) {
                var player = Bukkit.getPlayer(member.playerId());
                if (player != null) {
                    player.sendMessage(message);
                }
            }
        });
    }

    // audit H-001: 修复 PlayerQuitEvent 未清理 playerStates Map 导致的内存泄漏和状态泄漏
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerStates.remove(event.getPlayer().getUniqueId());
    }
}