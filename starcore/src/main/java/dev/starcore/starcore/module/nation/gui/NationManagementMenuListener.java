package dev.starcore.starcore.module.nation.gui;
import java.util.Optional;
import java.util.UUID;

import dev.starcore.starcore.foundation.animation.GuiAnimationManager;
import dev.starcore.starcore.foundation.animation.LoadingAnimationManager;
import dev.starcore.starcore.foundation.animation.MenuTransitionAnimator;
import dev.starcore.starcore.foundation.animation.SoundFeedbackManager;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.government.GovernmentService;
import dev.starcore.starcore.module.government.model.GovernmentType;
import dev.starcore.starcore.module.nation.NationModule;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.nation.model.NationMember;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 国家管理 GUI 事件监听器
 * 实现国家核心管理功能：
 * - 政体变更 (changeGovernmentType)
 * - 税率调整 (adjustTaxRate)
 * - 权限管理 (managePermissions)
 * - 国家解散 (confirmDisband)
 */
public final class NationManagementMenuListener implements Listener {
    private final NationService nationService;
    private final NationModule nationModule;
    private final MessageService messages;
    private final TriumphNationMenu triumphNationMenu;
    private final GovernmentService governmentService;
    private final PacketEventsAnvilProvider anvilProvider;

    // 动画管理器
    private final GuiAnimationManager animationManager;
    private final SoundFeedbackManager soundManager;
    private final MenuTransitionAnimator transitionAnimator;
    private final LoadingAnimationManager loadingManager;

    // 记录打开的菜单（改为按玩家 UUID 索引，便于 PlayerQuitEvent 清理）
    private final Map<UUID, NationManagementMenu> openMenus = new ConcurrentHashMap<>();
    // 解散确认追踪（防止误操作）
    private final Map<UUID, NationId> pendingDisbandConfirm = new ConcurrentHashMap<>();
    // 权限管理模式追踪
    private final Map<UUID, NationId> permissionManageMode = new ConcurrentHashMap<>();
    // 等待政体选择的玩家
    private final Map<UUID, NationId> waitingForGovernmentType = new ConcurrentHashMap<>();
    // 等待税率输入的玩家
    private final Map<UUID, NationId> waitingForTaxRate = new ConcurrentHashMap<>();

    public NationManagementMenuListener(NationService nationService, NationModule nationModule,
                                       MessageService messages, TriumphNationMenu triumphNationMenu,
                                       GovernmentService governmentService,
                                       PacketEventsAnvilProvider anvilProvider,
                                       GuiAnimationManager animationManager,
                                       SoundFeedbackManager soundManager,
                                       MenuTransitionAnimator transitionAnimator,
                                       LoadingAnimationManager loadingManager) {
        this.nationService = nationService;
        this.nationModule = nationModule;
        this.messages = messages;
        this.triumphNationMenu = triumphNationMenu;
        this.governmentService = governmentService;
        this.anvilProvider = anvilProvider;
        this.animationManager = animationManager;
        this.soundManager = soundManager;
        this.transitionAnimator = transitionAnimator;
        this.loadingManager = loadingManager;
    }

    /**
     * 打开国家管理菜单
     * audit C-067: 点击路由已通过 NationManagementMenu 工厂类（createMainMenu / createMemberListMenu）
     *   分离 UI 构建与事件路由，无需进一步抽取到独立的 NationMenuFactory。
     *   openMenu/openMainMenu/openMemberList 三个入口均沿用此模式。
     */
    public void openMenu(Player player, Nation nation) {
        // 播放菜单打开动画
        animationManager.playMenuOpenAnimation(player, "国家管理");

        NationManagementMenu menu = NationManagementMenu.createMainMenu(nation, messages);
        openMenus.put(player.getUniqueId(), menu);
        player.openInventory(menu.getInventory());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        NationManagementMenu menu = openMenus.get(player.getUniqueId());

        if (menu == null) {
            return;
        }

        event.setCancelled(true);

        // 检查点击的是否是GUI内部
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        int slot = event.getSlot();

        // 播放点击动画
        animationManager.playItemClickAnimation(player, clickedItem);

        // 根据当前页面和点击位置处理
        handleClick(player, menu, slot, clickedItem);
    }

    private void handleClick(Player player, NationManagementMenu menu, int slot, ItemStack item) {
        Nation nation = menu.getNation();

        switch (menu.getCurrentPage()) {
            case MAIN -> handleMainMenuClick(player, menu, slot, item);
            case MEMBERS -> handleMemberListClick(player, menu, slot, item);
            case SETTINGS -> handleSettingsClick(player, menu, slot, item);
        }

        // 通用导航按钮
        if (slot == 45 && menu.getCurrentPage() != NationManagementMenu.MenuPage.MAIN) {
            // 返回主菜单
            openMainMenu(player, nation);
        } else if (slot == 49) {
            // 关闭菜单
            player.closeInventory();
        } else if (slot == 53 && menu.getCurrentPage() == NationManagementMenu.MenuPage.MAIN) {
            // 帮助（53号位在主菜单是帮助，在成员列表是邀请）
            showHelp(player);
        }
    }

    private void handleMainMenuClick(Player player, NationManagementMenu menu, int slot, ItemStack item) {
        Nation nation = menu.getNation();

        // 播放导航音效
        soundManager.playNavigate(player);

        switch (slot) {
            case 10 -> openMemberList(player, nation);  // 成员列表
            case 12 -> openTreasury(player, nation);    // 国库
            case 14 -> openTerritories(player, nation); // 领地
            case 16 -> openSettings(player, nation);    // 设置
            case 20 -> openPolicies(player, nation);    // 国策
            case 22 -> openTechnology(player, nation);  // 科技
            case 24 -> openDiplomacy(player, nation);   // 外交
        }
    }

    private void handleMemberListClick(Player player, NationManagementMenu menu, int slot, ItemStack item) {
        Nation nation = menu.getNation();

        if (slot >= 9 && slot < 45) {
            // 点击了某个成员
            // 这里可以打开成员详情菜单，暂时显示消息
            player.sendMessage(Component.text(
                messages.format("nation.gui.member.click-info"),
                NamedTextColor.YELLOW
            ));
        } else if (slot == 53) {
            // 邀请玩家
            player.closeInventory();
            player.sendMessage(Component.text(
                messages.format("nation.gui.invite.hint"),
                NamedTextColor.YELLOW
            ));
        }
    }

    private void handleSettingsClick(Player player, NationManagementMenu menu, int slot, ItemStack item) {
        Nation nation = menu.getNation();
        UUID playerId = player.getUniqueId();

        // 检查权限
        if (!nation.hasPermission(playerId, "admin")) {
            player.sendMessage(Component.text(
                messages.format("nation.gui.no-permission"),
                NamedTextColor.RED
            ));
            return;
        }

        switch (slot) {
            case 10 -> changeGovernmentType(player, nation);
            case 12 -> adjustTaxRate(player, nation);
            case 14 -> managePermissions(player, nation);
            case 16 -> confirmDisband(player, nation);
        }
    }

    // ==================== 菜单导航方法 ====================

    private void openMainMenu(Player player, Nation nation) {
        NationManagementMenu menu = NationManagementMenu.createMainMenu(nation, messages);
        openMenus.put(player.getUniqueId(), menu);
        player.openInventory(menu.getInventory());
    }

    private void openMemberList(Player player, Nation nation) {
        NationManagementMenu menu = NationManagementMenu.createMemberListMenu(nation, messages);
        openMenus.put(player.getUniqueId(), menu);
        player.openInventory(menu.getInventory());
    }

    private void openTreasury(Player player, Nation nation) {
        // 集成财政模块的 GUI
        player.closeInventory();
        triumphNationMenu.openTreasurySubmenu(player, nation);
    }

    private void openTerritories(Player player, Nation nation) {
        // 显示领地列表
        player.closeInventory();
        triumphNationMenu.openTerritorySubmenu(player, nation, 1);
    }

    private void openSettings(Player player, Nation nation) {
        // 打开设置子菜单
        player.closeInventory();
        triumphNationMenu.openSettingsSubmenu(player, nation);
    }

    private void openPolicies(Player player, Nation nation) {
        // 集成国策模块的 GUI
        player.closeInventory();
        triumphNationMenu.openPolicySubmenu(player, nation);
    }

    private void openTechnology(Player player, Nation nation) {
        // 集成科技模块的 GUI
        player.closeInventory();
        triumphNationMenu.openTechnologySubmenu(player, nation);
    }

    private void openDiplomacy(Player player, Nation nation) {
        // 集成外交模块的 GUI
        player.closeInventory();
        triumphNationMenu.openDiplomacySubmenu(player, nation, 1);
    }

    // ==================== 核心操作方法 ====================

    /**
     * 变更政体类型
     * 打开政体选择 GUI，让玩家选择新的政体类型
     */
    private void changeGovernmentType(Player player, Nation nation) {
        player.closeInventory();

        // 检查政体服务是否可用
        if (governmentService == null) {
            player.sendMessage(Component.text("Government module not enabled", NamedTextColor.RED));
            return;
        }

        // 获取当前政体
        GovernmentType currentType = nation.governmentType();

        // 构建政体选择说明
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== Government Type System ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("Current: " + currentType.displayName() + " (" + currentType.name() + ")", NamedTextColor.YELLOW));
        player.sendMessage(Component.text(""));

        int slot = 0;
        for (GovernmentType type : GovernmentType.values()) {
            String status = type == currentType ? " [Current]" : "";
            String desc = getGovernmentTypeDescription(type);
            player.sendMessage(Component.text("  " + (slot + 1) + ". " + type.displayName() + " (" + type.name() + ")" + status, NamedTextColor.GRAY));
            player.sendMessage(Component.text("     " + desc, NamedTextColor.DARK_GRAY));
            slot++;
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("Enter number 1-" + GovernmentType.values().length + " to select government type", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Or enter \"cancel\" to cancel", NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));

        // 设置等待输入状态
        waitingForGovernmentType.put(player.getUniqueId(), nation.id());
    }

    /**
     * 获取政体类型描述
     */
    private String getGovernmentTypeDescription(GovernmentType type) {
        return switch (type) {
            case MONARCHY -> "Monarch holds all power, successor designated";
            case DICTATORSHIP -> "Dictatorship, highly centralized power";
            case REPUBLIC -> "Parliamentary republic, majority vote decisions";
            case DEMOCRACY -> "Democratic system, full member vote decisions";
            default -> "Unknown government type";
        };
    }

    /**
     * 处理政体选择输入
     */
    public void handleGovernmentTypeInput(Player player, String input) {
        NationId nationId = waitingForGovernmentType.remove(player.getUniqueId());
        if (nationId == null) {
            return;
        }

        Nation nation = nationModule.nationById(nationId).orElse(null);
        if (nation == null) {
            player.sendMessage(Component.text("Nation does not exist", NamedTextColor.RED));
            return;
        }

        // 解析输入
        int choice;
        try {
            choice = Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Please enter a valid number", NamedTextColor.RED));
            return;
        }

        GovernmentType[] types = GovernmentType.values();
        if (choice < 1 || choice > types.length) {
            player.sendMessage(Component.text("Please enter a number between 1-" + types.length, NamedTextColor.RED));
            return;
        }

        GovernmentType newType = types[choice - 1];
        GovernmentType currentType = nation.governmentType();

        if (newType == currentType) {
            player.sendMessage(Component.text("Nation is already " + newType.displayName(), NamedTextColor.YELLOW));
            return;
        }

        // 执行政体变更
        nation.setGovernmentType(newType);
        nationModule.saveState();

        // 播放成功动画
        animationManager.playSuccessAnimation(player, "政体已变更为 " + newType.displayName());

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("Government changed to: " + newType.displayName() + " (" + newType.name() + ")", NamedTextColor.GREEN));
        player.sendMessage(Component.text(""));
    }

    /**
     * 调整税率
     * 打开税率调整界面
     */
    private void adjustTaxRate(Player player, Nation nation) {
        player.closeInventory();

        // 构建税率调整说明
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== Tax Rate Adjustment ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("Current rate: " + String.format("%.1f%%", nation.getTaxRate() * 100), NamedTextColor.YELLOW));
        player.sendMessage(Component.text(""));

        // 获取政体对税率的限制
        String taxLimitInfo = getTaxLimitInfo(nation);
        if (taxLimitInfo != null) {
            player.sendMessage(Component.text(taxLimitInfo, NamedTextColor.GRAY));
        }

        player.sendMessage(Component.text("Enter new tax rate (0.0 - 1.0, e.g. 0.1 means 10%)", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Or enter \"cancel\" to cancel", NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));

        // 设置等待输入状态
        waitingForTaxRate.put(player.getUniqueId(), nation.id());
    }

    /**
     * 获取政体对税率的限制信息
     */
    private String getTaxLimitInfo(Nation nation) {
        GovernmentType type = nation.governmentType();
        if (type == null) {
            return null;
        }

        return switch (type) {
            case MONARCHY -> "Monarchy: Max rate 0.5 (50%)";
            case DICTATORSHIP -> "Dictatorship: Max rate 0.6 (60%)";
            case REPUBLIC -> "Republic: Max rate 0.4 (40%)";
            case DEMOCRACY -> "Democracy: Max rate 0.3 (30%)";
            default -> "Unknown: Max rate 0.5 (50%)";
        };
    }

    /**
     * 获取政体对税率的最大限制
     */
    private double getMaxTaxRate(GovernmentType type) {
        return switch (type) {
            case MONARCHY -> 0.5;
            case DICTATORSHIP -> 0.6;
            case REPUBLIC -> 0.4;
            case DEMOCRACY -> 0.3;
            default -> 0.5; // 默认最高税率50%
        };
    }

    /**
     * 处理税率输入
     */
    public void handleTaxRateInput(Player player, String input) {
        NationId nationId = waitingForTaxRate.remove(player.getUniqueId());
        if (nationId == null) {
            return;
        }

        Nation nation = nationModule.nationById(nationId).orElse(null);
        if (nation == null) {
            player.sendMessage(Component.text("Nation does not exist", NamedTextColor.RED));
            return;
        }

        // 解析输入
        double newRate;
        try {
            newRate = Double.parseDouble(input.trim());
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Please enter a valid number (0.0 - 1.0)", NamedTextColor.RED));
            return;
        }

        // 验证范围
        if (newRate < 0.0 || newRate > 1.0) {
            player.sendMessage(Component.text("Tax rate must be between 0.0 - 1.0", NamedTextColor.RED));
            return;
        }

        // 验证政体限制
        double maxRate = getMaxTaxRate(nation.governmentType());
        if (newRate > maxRate) {
            player.sendMessage(Component.text(nation.governmentType().displayName() + " max rate is " + String.format("%.0f%%", maxRate * 100), NamedTextColor.RED));
            return;
        }

        // 执行税率变更
        nation.setTaxRate(newRate);
        nationModule.saveState();

        // 播放成功动画
        animationManager.playSuccessAnimation(player, "税率已调整为 " + String.format("%.1f%%", newRate * 100));

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("Tax rate adjusted to: " + String.format("%.1f%%", newRate * 100), NamedTextColor.GREEN));
        player.sendMessage(Component.text(""));
    }

    /**
     * 管理权限
     * 打开成员权限管理列表
     */
    private void managePermissions(Player player, Nation nation) {
        player.closeInventory();

        UUID playerId = player.getUniqueId();

        // 检查是否是管理员
        NationMember self = nation.members().stream()
            .filter(m -> m.playerId().equals(playerId))
            .findFirst().orElse(null);

        if (self == null || !"admin".equals(self.rank())) {
            player.sendMessage(Component.text("Only admins can manage permissions", NamedTextColor.RED));
            return;
        }

        // 构建权限管理说明
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== Permission Management ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text(""));

        // 显示所有成员
        int index = 1;
        for (NationMember member : nation.members()) {
            String isSelf = member.playerId().equals(playerId) ? " [You]" : "";
            String rankColor = getRankColor(member.rank());
            player.sendMessage(Component.text(
                "  " + index + ". " + member.playerName() + isSelf + " [" + rankColor + member.rank() + "]",
                NamedTextColor.GRAY
            ));
            index++;
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("Commands:", NamedTextColor.GREEN));
        player.sendMessage(Component.text("  /setrank <player> <admin|officer|member>", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Example: /setrank PlayerName admin", NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("Tip: Use /nation gui for visual management", NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    /**
     * 获取等级对应的颜色
     */
    private String getRankColor(String rank) {
        return switch (rank.toLowerCase()) {
            case "admin" -> "c";
            case "officer" -> "a";
            default -> "7";
        };
    }

    /**
     * 确认解散国家
     * 需要二次确认，防止误操作
     */
    private void confirmDisband(Player player, Nation nation) {
        UUID playerId = player.getUniqueId();

        // 检查是否是创始人
        if (!nation.founderId().equals(playerId)) {
            player.sendMessage(Component.text("Only the nation founder can disband", NamedTextColor.RED));
            return;
        }

        // 检查是否在战争中
        if (nationModule.atWar(nation.id())) {
            player.sendMessage(Component.text("Cannot disband during wartime", NamedTextColor.RED));
            return;
        }

        // 记录待确认的解散请求
        pendingDisbandConfirm.put(playerId, nation.id());

        // 构建二次确认说明
        player.closeInventory();
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("WARNING: Nation Disband Confirmation", NamedTextColor.DARK_RED));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("You are about to disband nation: " + nation.name(), NamedTextColor.RED));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("This will cause the following:", NamedTextColor.RED));
        player.sendMessage(Component.text("  - All territories will be released", NamedTextColor.DARK_RED));
        player.sendMessage(Component.text("  - All members will be removed", NamedTextColor.DARK_RED));
        player.sendMessage(Component.text("  - All diplomatic relations will be cleared", NamedTextColor.DARK_RED));
        player.sendMessage(Component.text("  - All treasury funds will be cleared", NamedTextColor.DARK_RED));
        player.sendMessage(Component.text("  - All sub-city-states will be disbanded", NamedTextColor.DARK_RED));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("THIS ACTION CANNOT BE UNDONE!", NamedTextColor.DARK_RED));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("Enter \"disband " + nation.name() + "\" to confirm", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Or close this window to cancel", NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));

        // 打开铁砧确认界面
        anvilProvider.openAnvilInput(
            player,
            "Confirm: " + nation.name(),
            "",
            input -> {
                NationId storedNationId = pendingDisbandConfirm.remove(playerId);
                if (storedNationId == null) {
                    return;
                }

                Nation currentNation = nationModule.nationById(storedNationId).orElse(null);
                if (currentNation == null) {
                    player.sendMessage(Component.text("Nation does not exist", NamedTextColor.RED));
                    return;
                }

                // 验证输入
                String expectedConfirm = "disband " + currentNation.name();
                if (input == null || !input.trim().equalsIgnoreCase(expectedConfirm)) {
                    player.sendMessage(Component.text("Disband cancelled (input mismatch)", NamedTextColor.YELLOW));
                    return;
                }

                // 执行解散
                boolean success = nationModule.disbandNation(storedNationId, playerId);
                if (success) {
                    // 播放解散动画
                    animationManager.playFailureAnimation(player, "国家已解散");
                    player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 0.5f);
                    player.sendMessage(Component.text(""));
                    player.sendMessage(Component.text("Nation " + currentNation.name() + " has been disbanded", NamedTextColor.GREEN));
                    player.sendMessage(Component.text(""));
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    player.sendMessage(Component.text("Disband failed, possibly in war", NamedTextColor.RED));
                }
            },
            () -> {
                // 取消操作
                pendingDisbandConfirm.remove(playerId);
                player.sendMessage(Component.text("Disband cancelled", NamedTextColor.YELLOW));
            }
        );
    }

    /**
     * 处理 setrank 命令
     */
    public boolean handleSetRankCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /setrank <player> <admin|officer|member>", NamedTextColor.RED));
            return true;
        }

        String targetName = args[0];
        String newRank = args[1].toLowerCase();

        // 验证等级
        if (!newRank.equals("admin") && !newRank.equals("officer") && !newRank.equals("member")) {
            player.sendMessage(Component.text("Rank must be: admin, officer, member", NamedTextColor.RED));
            return true;
        }

        // 获取国家
        Optional<Nation> nationOpt = nationModule.getNationByMember(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("You are not in any nation", NamedTextColor.RED));
            return true;
        }

        Nation nation = nationOpt.get();

        // 检查权限
        NationMember self = nation.members().stream()
            .filter(m -> m.playerId().equals(player.getUniqueId()))
            .findFirst().orElse(null);

        if (self == null || !"admin".equals(self.rank())) {
            player.sendMessage(Component.text("Only admins can modify permissions", NamedTextColor.RED));
            return true;
        }

        // 查找目标成员
        NationMember target = nation.members().stream()
            .filter(m -> m.playerName().equalsIgnoreCase(targetName))
            .findFirst().orElse(null);

        if (target == null) {
            player.sendMessage(Component.text("Member not found: " + targetName, NamedTextColor.RED));
            return true;
        }

        // 不能修改自己
        if (target.playerId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("Cannot modify your own rank", NamedTextColor.RED));
            return true;
        }

        // 创始人不能被降级
        if (target.playerId().equals(nation.founderId()) && !newRank.equals("admin")) {
            player.sendMessage(Component.text("Cannot demote the founder", NamedTextColor.RED));
            return true;
        }

        // 执行修改
        nationModule.setMemberRank(nation.id(), target.playerId(), newRank);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        player.sendMessage(Component.text("Changed " + target.playerName() + "'s rank to: " + newRank, NamedTextColor.GREEN));

        return true;
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("nation.gui.help.title"), NamedTextColor.GOLD));
        player.sendMessage(Component.text(messages.format("nation.gui.help.line1"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("nation.gui.help.line2"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("nation.gui.help.line3"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            UUID playerId = player.getUniqueId();
            openMenus.remove(playerId);
            pendingDisbandConfirm.remove(playerId);
            waitingForGovernmentType.remove(playerId);
            waitingForTaxRate.remove(playerId);
            permissionManageMode.remove(playerId);
        }
    }

    // audit H-001: 修复 PlayerQuitEvent 未清理 Map 状态导致的内存泄漏和状态泄漏
    // 包括 pendingDisbandConfirm（解散确认状态）、waitingForGovernmentType（政体选择状态）、
    // waitingForTaxRate（税率输入状态）、permissionManageMode（权限管理模式）
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        pendingDisbandConfirm.remove(playerId);
        waitingForGovernmentType.remove(playerId);
        waitingForTaxRate.remove(playerId);
        permissionManageMode.remove(playerId);
        openMenus.remove(playerId);
    }

    /**
     * 获取待确认解散的国家ID
     */
    public Optional<NationId> getPendingDisbandNation(UUID playerId) {
        return Optional.ofNullable(pendingDisbandConfirm.get(playerId));
    }

    /**
     * 清除待确认解散状态
     */
    public void clearPendingDisband(UUID playerId) {
        pendingDisbandConfirm.remove(playerId);
    }

    /**
     * 检查玩家是否在等待政体选择输入
     */
    public boolean isWaitingForGovernmentType(UUID playerId) {
        return waitingForGovernmentType.containsKey(playerId);
    }

    /**
     * 检查玩家是否在等待税率输入
     */
    public boolean isWaitingForTaxRate(UUID playerId) {
        return waitingForTaxRate.containsKey(playerId);
    }
}
