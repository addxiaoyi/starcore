package dev.starcore.starcore.module.resolution;

import dev.starcore.starcore.module.diplomacy.DiplomacyRelation;
import dev.starcore.starcore.module.government.GovernmentService;
import dev.starcore.starcore.module.government.model.GovernmentType;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.resolution.model.Resolution;
import dev.starcore.starcore.module.resolution.model.ResolutionKind;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Enhanced Resolution system GUI event listener.
 *
 * Complete GUI features:
 * - Main menu navigation
 * - Resolution list browsing (open, pending, history)
 * - Full proposal creation via GUI (rename, join, government, diplomacy)
 * - Resolution signing/voting
 * - Resolution detail view
 * - Chat-based input for text fields
 */
public class ResolutionGuiListener implements org.bukkit.event.Listener {

    private final ResolutionModule resolutionModule;
    private final NationService nationService;
    private final GovernmentService governmentService;
    private final ResolutionInputHandler inputHandler;

    // Player menu state tracking
    private final Map<UUID, MenuState> playerMenus = new ConcurrentHashMap<>();

    // Pending diplomacy relation selection
    private final Map<UUID, DiplomacyRelation> pendingDiplomacyRelation = new ConcurrentHashMap<>();

    public ResolutionGuiListener(ResolutionModule resolutionModule, NationService nationService, GovernmentService governmentService) {
        this.resolutionModule = resolutionModule;
        this.nationService = nationService;
        this.governmentService = governmentService;
        this.inputHandler = new ResolutionInputHandler(resolutionModule, nationService, governmentService);
    }

    /**
     * Get the input handler for chat processing
     */
    public ResolutionInputHandler getInputHandler() {
        return inputHandler;
    }

    /**
     * Menu state for tracking player navigation
     */
    private static class MenuState {
        final String menuType;
        final UUID nationId;
        final UUID resolutionId;
        final int page;
        final String subData; // Additional context data

        MenuState(String menuType, UUID nationId, UUID resolutionId, int page) {
            this(menuType, nationId, resolutionId, page, null);
        }

        MenuState(String menuType, UUID nationId, UUID resolutionId, int page, String subData) {
            this.menuType = menuType;
            this.nationId = nationId;
            this.resolutionId = resolutionId;
            this.page = page;
            this.subData = subData;
        }
    }

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.NORMAL)
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Check if player is in input mode
        if (inputHandler.hasActiveSession(player.getUniqueId())) {
            event.setCancelled(true);
            player.closeInventory();
            player.sendMessage(Component.text("§c请在聊天中输入内容，或输入 §ecancel §c取消", NamedTextColor.RED));
            return;
        }

        Component title = event.getView().title();
        String titleStr = PlainTextComponentSerializer.plainText().serialize(title);

        // Check if this is a resolution menu
        if (!titleStr.contains("决议") && !titleStr.contains("Resolution")) {
            return;
        }

        event.setCancelled(true);

        int slot = event.getRawSlot();
        ItemStack item = event.getCurrentItem();

        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        String menuType = getMenuType(titleStr);
        UUID playerId = player.getUniqueId();

        switch (menuType) {
            case "决议系统" -> handleMainMenu(player, slot, item);
            case "我的提案" -> handleMyProposalsMenu(player, slot, item, titleStr);
            case "待签署" -> handlePendingSignaturesMenu(player, slot, item, titleStr);
            case "历史记录" -> handleHistoryMenu(player, slot, item, titleStr);
            case "创建提案" -> handleProposalTypeMenu(player, slot, item);
            case "决议详情" -> handleResolutionDetailMenu(player, slot, item, titleStr);
            case "国家改名" -> handleRenameMenu(player, slot, item);
            case "加入申请" -> handleJoinRequestMenu(player, slot, item);
            case "政体变更" -> handleGovernmentChangeMenu(player, slot, item);
            case "外交关系" -> handleDiplomacyRelationMenu(player, slot, item);
            case "外交目标" -> handleDiplomacyTargetMenu(player, slot, item);
            case "外交确认" -> handleDiplomacyConfirmMenu(player, slot, item);
            default -> {
                // Handle back button
                if (isBackButton(item)) {
                    openMainMenu(player);
                }
            }
        }
    }

    /**
     * Handle main menu clicks
     */
    private void handleMainMenu(Player player, int slot, ItemStack item) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());

        switch (slot) {
            case 20 -> {
                // My proposals
                if (nationOpt.isEmpty()) {
                    player.sendMessage(Component.text("§c你需要先加入一个国家才能使用决议系统", NamedTextColor.RED));
                    return;
                }
                openMyProposals(player, nationOpt.get().id().value(), 0);
            }
            case 22 -> {
                // Pending signatures (resolutions waiting for this player to sign)
                if (nationOpt.isEmpty()) {
                    player.sendMessage(Component.text("§c你需要先加入一个国家才能使用决议系统", NamedTextColor.RED));
                    return;
                }
                openPendingSignatures(player, nationOpt.get().id().value(), 0);
            }
            case 24 -> {
                // History
                if (nationOpt.isEmpty()) {
                    player.sendMessage(Component.text("§c你需要先加入一个国家才能使用决议系统", NamedTextColor.RED));
                    return;
                }
                openHistory(player, nationOpt.get().id().value(), 0);
            }
            case 40 -> {
                // Create new proposal
                if (nationOpt.isEmpty()) {
                    player.sendMessage(Component.text("§c你需要先加入一个国家才能使用决议系统", NamedTextColor.RED));
                    return;
                }
                openProposalTypeMenu(player, nationOpt.get().id().value());
            }
            case 49 -> {
                // Help
                player.closeInventory();
                showHelp(player);
            }
        }
    }

    /**
     * Show help information
     */
    private void showHelp(Player player) {
        player.sendMessage(Component.text("§6═══════════════════════════════════", NamedTextColor.GOLD));
        player.sendMessage(Component.text("§e决议系统帮助", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("§7决议系统 §f- 用于国家内的重要决策", NamedTextColor.GRAY));
        player.sendMessage(Component.text("§7- 提案需要满足政体要求的签名数量", NamedTextColor.GRAY));
        player.sendMessage(Component.text("§7- 提案通过后将自动执行", NamedTextColor.GRAY));
        player.sendMessage(Component.text("§7- 使用 §e/sc resolution §7命令查看所有操作", NamedTextColor.GRAY));
        player.sendMessage(Component.text("§6═══════════════════════════════════", NamedTextColor.GOLD));
    }

    /**
     * Handle my proposals menu clicks
     */
    private void handleMyProposalsMenu(Player player, int slot, ItemStack item, String title) {
        if (isBackButton(item)) {
            openMainMenu(player);
            return;
        }

        MenuState state = playerMenus.get(player.getUniqueId());
        if (state == null) return;
        UUID nationId = state.nationId;

        // Pagination
        int currentPage = getPageFromTitle(title);
        if (slot == 45) {
            openMyProposals(player, nationId, currentPage - 1);
            return;
        }
        if (slot == 53) {
            openMyProposals(player, nationId, currentPage + 1);
            return;
        }

        // Resolution item click
        if (slot >= 10 && slot <= 43 && slot % 9 != 0 && slot % 9 != 8) {
            UUID resolutionId = extractResolutionId(item);
            if (resolutionId != null) {
                openResolutionDetail(player, resolutionId);
            }
        }
    }

    /**
     * Handle pending signatures menu clicks
     */
    private void handlePendingSignaturesMenu(Player player, int slot, ItemStack item, String title) {
        if (isBackButton(item)) {
            openMainMenu(player);
            return;
        }

        MenuState state = playerMenus.get(player.getUniqueId());
        if (state == null) return;
        UUID nationId = state.nationId;

        // Pagination
        int currentPage = getPageFromTitle(title);
        if (slot == 45) {
            openPendingSignatures(player, nationId, currentPage - 1);
            return;
        }
        if (slot == 53) {
            openPendingSignatures(player, nationId, currentPage + 1);
            return;
        }

        // Resolution item click
        if (slot >= 10 && slot <= 43 && slot % 9 != 0 && slot % 9 != 8) {
            UUID resolutionId = extractResolutionId(item);
            if (resolutionId != null) {
                openResolutionDetail(player, resolutionId);
            }
        }
    }

    /**
     * Handle history menu clicks
     */
    private void handleHistoryMenu(Player player, int slot, ItemStack item, String title) {
        if (isBackButton(item)) {
            openMainMenu(player);
            return;
        }

        MenuState state = playerMenus.get(player.getUniqueId());
        if (state == null) return;
        UUID nationId = state.nationId;

        // Pagination
        int currentPage = getPageFromTitle(title);
        if (slot == 45) {
            openHistory(player, nationId, currentPage - 1);
            return;
        }
        if (slot == 53) {
            openHistory(player, nationId, currentPage + 1);
            return;
        }

        // Resolution item click
        if (slot >= 10 && slot <= 43 && slot % 9 != 0 && slot % 9 != 8) {
            UUID resolutionId = extractResolutionId(item);
            if (resolutionId != null) {
                openResolutionDetail(player, resolutionId);
            }
        }
    }

    /**
     * Handle proposal type selection menu
     */
    private void handleProposalTypeMenu(Player player, int slot, ItemStack item) {
        if (isBackButton(item)) {
            openMainMenu(player);
            return;
        }

        MenuState state = playerMenus.get(player.getUniqueId());
        if (state == null) return;
        UUID nationId = state.nationId;
        Nation nation = nationService.nationById(NationId.of(nationId)).orElse(null);
        if (nation == null) {
            player.sendMessage(Component.text("§c国家不存在", NamedTextColor.RED));
            return;
        }

        switch (slot) {
            case 11 -> {
                // Join request - need to input player name
                openJoinRequestMenu(player, nation);
            }
            case 13 -> {
                // Rename nation - need to input new name
                openRenameNationMenu(player, nation);
            }
            case 15 -> {
                // Government change - select type directly
                openGovernmentChangeMenu(player, nation);
            }
            case 30 -> {
                // Diplomacy - need nation and relation
                openDiplomacyTargetMenu(player, nation);
            }
        }
    }

    /**
     * Handle nation rename menu
     */
    private void handleRenameMenu(Player player, int slot, ItemStack item) {
        if (isBackButton(item)) {
            openProposalTypeMenu(player, playerMenus.get(player.getUniqueId()).nationId);
            return;
        }

        // Only confirm button triggers input
        if (slot != 24) return;

        MenuState state = playerMenus.get(player.getUniqueId());
        if (state == null) return;
        Nation nation = nationService.nationById(NationId.of(state.nationId)).orElse(null);
        if (nation == null) {
            player.sendMessage(Component.text("§c国家不存在", NamedTextColor.RED));
            return;
        }

        // Start input session
        inputHandler.startRenameSession(player, nation, newName -> {
            if (newName == null || newName.trim().isEmpty()) {
                player.sendMessage(Component.text("§c国家名称不能为空", NamedTextColor.RED));
                return;
            }
            String trimmedName = newName.trim();
            if (trimmedName.length() < 2 || trimmedName.length() > 20) {
                player.sendMessage(Component.text("§c国家名称长度必须在2-20个字符之间", NamedTextColor.RED));
                return;
            }
            try {
                resolutionModule.proposeRename(nation, player.getUniqueId(), player.getName(), trimmedName);
                player.sendMessage(Component.text("§a已提交国家改名提案: §f" + trimmedName, NamedTextColor.GREEN));
                openMyProposals(player, nation.id().value(), 0);
            } catch (Exception e) {
                player.sendMessage(Component.text("§c提案失败: " + e.getMessage(), NamedTextColor.RED));
            }
        });
    }

    /**
     * Handle join request menu
     */
    private void handleJoinRequestMenu(Player player, int slot, ItemStack item) {
        if (isBackButton(item)) {
            openProposalTypeMenu(player, playerMenus.get(player.getUniqueId()).nationId);
            return;
        }

        // Only confirm button triggers input
        if (slot != 24) return;

        MenuState state = playerMenus.get(player.getUniqueId());
        if (state == null) return;
        Nation nation = nationService.nationById(NationId.of(state.nationId)).orElse(null);
        if (nation == null) {
            player.sendMessage(Component.text("§c国家不存在", NamedTextColor.RED));
            return;
        }

        // Start input session
        inputHandler.startJoinSession(player, nation, targetPlayerName -> {
            if (targetPlayerName == null || targetPlayerName.trim().isEmpty()) {
                player.sendMessage(Component.text("§c玩家名称不能为空", NamedTextColor.RED));
                return;
            }
            String trimmedName = targetPlayerName.trim();
            org.bukkit.OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(trimmedName);
            UUID targetId = targetPlayer.getUniqueId();

            // Check if player already in a nation
            if (nationService.nationOf(targetId).isPresent()) {
                player.sendMessage(Component.text("§c该玩家已在其他国家", NamedTextColor.RED));
                return;
            }

            try {
                resolutionModule.proposeJoin(nation, player.getUniqueId(), player.getName(), targetId, trimmedName);
                player.sendMessage(Component.text("§a已提交加入申请提案: §f" + trimmedName, NamedTextColor.GREEN));
                openMyProposals(player, nation.id().value(), 0);
            } catch (Exception e) {
                player.sendMessage(Component.text("§c提案失败: " + e.getMessage(), NamedTextColor.RED));
            }
        });
    }

    /**
     * Handle government change menu - direct selection
     */
    private void handleGovernmentChangeMenu(Player player, int slot, ItemStack item) {
        if (isBackButton(item)) {
            openProposalTypeMenu(player, playerMenus.get(player.getUniqueId()).nationId);
            return;
        }

        MenuState state = playerMenus.get(player.getUniqueId());
        if (state == null) return;
        Nation nation = nationService.nationById(NationId.of(state.nationId)).orElse(null);
        if (nation == null) {
            player.sendMessage(Component.text("§c国家不存在", NamedTextColor.RED));
            return;
        }

        GovernmentType selectedType = getGovernmentTypeFromSlot(slot, item);
        if (selectedType == null) return;

        if (selectedType == nation.governmentType()) {
            player.sendMessage(Component.text("§c国家已经是 " + selectedType.displayName() + " 了", NamedTextColor.RED));
            return;
        }

        try {
            resolutionModule.proposeGovernmentChange(nation, player.getUniqueId(), player.getName(), selectedType);
            player.sendMessage(Component.text("§a已提交政体变更提案: §f" + selectedType.displayName(), NamedTextColor.GREEN));
            openMyProposals(player, nation.id().value(), 0);
        } catch (Exception e) {
            player.sendMessage(Component.text("§c提案失败: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    /**
     * Handle diplomacy target selection menu
     */
    private void handleDiplomacyTargetMenu(Player player, int slot, ItemStack item) {
        if (isBackButton(item)) {
            openProposalTypeMenu(player, playerMenus.get(player.getUniqueId()).nationId);
            return;
        }

        // Cancel button
        if (slot == 49) {
            pendingDiplomacyRelation.remove(player.getUniqueId());
            openProposalTypeMenu(player, playerMenus.get(player.getUniqueId()).nationId);
            return;
        }

        // Input button - start nation name input
        if (slot == 22) {
            MenuState state = playerMenus.get(player.getUniqueId());
            if (state == null) return;
            Nation nation = nationService.nationById(NationId.of(state.nationId)).orElse(null);
            if (nation == null) {
                player.sendMessage(Component.text("§c国家不存在", NamedTextColor.RED));
                return;
            }

            inputHandler.startInputSession(player, ResolutionInputHandler.InputType.DIPLOMACY_TARGET, nation, null,
                targetName -> {
                    Nation targetNation = nationService.nationByName(targetName).orElse(null);
                    if (targetNation == null) {
                        player.sendMessage(Component.text("§c国家不存在: " + targetName, NamedTextColor.RED));
                        openDiplomacyTargetMenu(player, nation);
                        return;
                    }
                    if (targetNation.id().equals(nation.id())) {
                        player.sendMessage(Component.text("§c不能对自己国家提案", NamedTextColor.RED));
                        openDiplomacyTargetMenu(player, nation);
                        return;
                    }
                    openDiplomacyRelationMenu(player, nation, targetNation);
                },
                msg -> {
                    pendingDiplomacyRelation.remove(player.getUniqueId());
                    openProposalTypeMenu(player, state.nationId);
                });
            return;
        }

        // Click on another nation
        if (slot >= 10 && slot <= 43 && slot % 9 != 0 && slot % 9 != 8) {
            String targetName = extractNationName(item);
            if (targetName != null) {
                MenuState state = playerMenus.get(player.getUniqueId());
                if (state == null) return;
                Nation nation = nationService.nationById(NationId.of(state.nationId)).orElse(null);
                if (nation == null) return;
                Nation targetNation = nationService.nationByName(targetName).orElse(null);
                if (targetNation != null) {
                    openDiplomacyRelationMenu(player, nation, targetNation);
                }
            }
        }
    }

    /**
     * Handle diplomacy relation selection menu
     */
    private void handleDiplomacyRelationMenu(Player player, int slot, ItemStack item) {
        if (isBackButton(item)) {
            MenuState state = playerMenus.get(player.getUniqueId());
            if (state != null && state.subData != null) {
                // Back to target selection with cached nation
                Nation nation = nationService.nationById(NationId.of(state.nationId)).orElse(null);
                Nation targetNation = nationService.nationByName(state.subData).orElse(null);
                if (nation != null && targetNation != null) {
                    openDiplomacyTargetMenu(player, nation);
                    return;
                }
            }
            openDiplomacyTargetMenu(player, null);
            return;
        }

        DiplomacyRelation selectedRelation = getDiplomacyRelationFromSlot(slot, item);
        if (selectedRelation == null) return;

        MenuState state = playerMenus.get(player.getUniqueId());
        if (state == null || state.subData == null) return;
        Nation nation = nationService.nationById(NationId.of(state.nationId)).orElse(null);
        Nation targetNation = nationService.nationByName(state.subData).orElse(null);
        if (nation == null || targetNation == null) {
            player.sendMessage(Component.text("§c国家数据异常", NamedTextColor.RED));
            return;
        }

        // Store the selected relation for confirmation
        pendingDiplomacyRelation.put(player.getUniqueId(), selectedRelation);
        openDiplomacyConfirmMenu(player, nation, targetNation, selectedRelation);
    }

    /**
     * Handle diplomacy confirmation menu
     */
    private void handleDiplomacyConfirmMenu(Player player, int slot, ItemStack item) {
        if (isBackButton(item)) {
            MenuState state = playerMenus.get(player.getUniqueId());
            if (state != null && state.subData != null) {
                Nation nation = nationService.nationById(NationId.of(state.nationId)).orElse(null);
                Nation targetNation = nationService.nationByName(state.subData).orElse(null);
                if (nation != null && targetNation != null) {
                    openDiplomacyRelationMenu(player, nation, targetNation);
                    return;
                }
            }
            pendingDiplomacyRelation.remove(player.getUniqueId());
            return;
        }

        MenuState state = playerMenus.get(player.getUniqueId());
        if (state == null || state.subData == null) return;
        Nation nation = nationService.nationById(NationId.of(state.nationId)).orElse(null);
        Nation targetNation = nationService.nationByName(state.subData).orElse(null);
        DiplomacyRelation relation = pendingDiplomacyRelation.get(player.getUniqueId());
        if (nation == null || targetNation == null || relation == null) {
            player.sendMessage(Component.text("§c数据异常", NamedTextColor.RED));
            return;
        }

        // Confirm button (slot 24)
        if (slot == 24) {
            try {
                resolutionModule.proposeDiplomacyChange(nation, player.getUniqueId(), player.getName(), targetNation, relation);
                player.sendMessage(Component.text("§a已提交外交关系提案: §f" + targetNation.name() + " §7-> §f" + relation.displayName(), NamedTextColor.GREEN));
                pendingDiplomacyRelation.remove(player.getUniqueId());
                openMyProposals(player, nation.id().value(), 0);
            } catch (Exception e) {
                player.sendMessage(Component.text("§c提案失败: " + e.getMessage(), NamedTextColor.RED));
            }
            return;
        }

        // Change relation button (slot 22) - go back to relation selection
        if (slot == 22) {
            pendingDiplomacyRelation.remove(player.getUniqueId());
            openDiplomacyRelationMenu(player, nation, targetNation);
        }
    }

    /**
     * Handle resolution detail menu clicks
     */
    private void handleResolutionDetailMenu(Player player, int slot, ItemStack item, String title) {
        if (isBackButton(item)) {
            // Go back to appropriate menu
            MenuState state = playerMenus.get(player.getUniqueId());
            if (state != null && state.nationId != null) {
                openPendingSignatures(player, state.nationId, 0);
            } else {
                openMainMenu(player);
            }
            return;
        }

        MenuState state = playerMenus.get(player.getUniqueId());
        UUID resolutionId = state != null ? state.resolutionId : null;
        if (resolutionId == null) {
            resolutionId = extractResolutionIdFromTitle(title);
        }
        if (resolutionId == null) {
            return;
        }

        Optional<Resolution> resolutionOpt = resolutionModule.find(resolutionId);
        if (resolutionOpt.isEmpty()) {
            player.sendMessage(Component.text("§c决议不存在或已过期", NamedTextColor.RED));
            return;
        }

        Resolution resolution = resolutionOpt.get();

        switch (slot) {
            case 22 -> {
                // Sign the resolution
                if (!resolution.isOpen()) {
                    player.sendMessage(Component.text("§c该决议已结束，无法签署", NamedTextColor.RED));
                    return;
                }
                boolean success = resolutionModule.sign(player.getUniqueId(), resolutionId);
                if (success) {
                    player.sendMessage(Component.text("§a成功签署决议!", NamedTextColor.GREEN));
                    if (resolution.state() == dev.starcore.starcore.module.resolution.model.ResolutionState.ENACTED) {
                        player.sendMessage(Component.text("§6决议已通过并执行!", NamedTextColor.GOLD));
                    }
                    openResolutionDetail(player, resolutionId);
                } else {
                    player.sendMessage(Component.text("§c签署失败 - 你可能没有权限或已签署", NamedTextColor.RED));
                }
            }
            case 24 -> {
                // Cancel proposal (only for proposer)
                if (!resolution.isOpen()) {
                    player.sendMessage(Component.text("§c该决议已结束，无法取消", NamedTextColor.RED));
                    return;
                }
                if (!resolution.proposerId().equals(player.getUniqueId())) {
                    player.sendMessage(Component.text("§c只有提案人可以取消该决议", NamedTextColor.RED));
                    return;
                }
                boolean success = resolutionModule.cancel(resolutionId, player.getUniqueId());
                if (success) {
                    player.sendMessage(Component.text("§a已取消提案", NamedTextColor.GREEN));
                    openPendingSignatures(player, resolution.nationId().value(), 0);
                }
            }
        }
    }

    @org.bukkit.event.EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            // Don't clear input sessions on inventory close - let chat input continue
        }
    }

    // ==================== Menu Opening Methods ====================

    private void openMainMenu(Player player) {
        playerMenus.remove(player.getUniqueId());
        player.openInventory(createMainMenu());
    }

    private void openMyProposals(Player player, UUID nationId, int page) {
        MenuState state = new MenuState("我的提案", nationId, null, page);
        playerMenus.put(player.getUniqueId(), state);
        player.openInventory(createMyProposalsMenu(player.getUniqueId(), nationId, page));
    }

    private void openPendingSignatures(Player player, UUID nationId, int page) {
        MenuState state = new MenuState("待签署", nationId, null, page);
        playerMenus.put(player.getUniqueId(), state);
        player.openInventory(createPendingSignaturesMenu(player.getUniqueId(), nationId, page));
    }

    private void openHistory(Player player, UUID nationId, int page) {
        MenuState state = new MenuState("历史记录", nationId, null, page);
        playerMenus.put(player.getUniqueId(), state);
        player.openInventory(createHistoryMenu(player.getUniqueId(), nationId, page));
    }

    private void openProposalTypeMenu(Player player, UUID nationId) {
        MenuState state = new MenuState("创建提案", nationId, null, 0);
        playerMenus.put(player.getUniqueId(), state);
        player.openInventory(createProposalTypeMenu());
    }

    private void openRenameNationMenu(Player player, Nation nation) {
        MenuState state = new MenuState("国家改名", nation.id().value(), null, 0);
        playerMenus.put(player.getUniqueId(), state);
        player.openInventory(createRenameNationMenu(nation));
    }

    private void openJoinRequestMenu(Player player, Nation nation) {
        MenuState state = new MenuState("加入申请", nation.id().value(), null, 0);
        playerMenus.put(player.getUniqueId(), state);
        player.openInventory(createJoinRequestMenu(nation));
    }

    private void openGovernmentChangeMenu(Player player, Nation nation) {
        MenuState state = new MenuState("政体变更", nation.id().value(), null, 0);
        playerMenus.put(player.getUniqueId(), state);
        player.openInventory(createGovernmentChangeMenu(nation));
    }

    private void openDiplomacyTargetMenu(Player player, Nation nation) {
        MenuState state = new MenuState("外交目标", nation.id().value(), null, 0);
        playerMenus.put(player.getUniqueId(), state);
        player.openInventory(createDiplomacyTargetMenu(nation));
    }

    private void openDiplomacyRelationMenu(Player player, Nation nation, Nation targetNation) {
        MenuState state = new MenuState("外交关系", nation.id().value(), null, 0, targetNation.name());
        playerMenus.put(player.getUniqueId(), state);
        player.openInventory(createDiplomacyRelationMenu(nation, targetNation));
    }

    private void openDiplomacyConfirmMenu(Player player, Nation nation, Nation targetNation, DiplomacyRelation relation) {
        MenuState state = new MenuState("外交确认", nation.id().value(), null, 0, targetNation.name());
        playerMenus.put(player.getUniqueId(), state);
        player.openInventory(createDiplomacyConfirmMenu(nation, targetNation, relation));
    }

    private void openResolutionDetail(Player player, UUID resolutionId) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        MenuState state = new MenuState("决议详情", nationOpt.map(Nation::id).map(NationId::value).orElse(null), resolutionId, 0);
        playerMenus.put(player.getUniqueId(), state);
        player.openInventory(createResolutionDetailMenu(player, resolutionId));
    }

    // ==================== Menu Creation Methods ====================

    private org.bukkit.inventory.Inventory createMainMenu() {
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, 54, Component.text("§6§l决议系统"));

        // Border
        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        // Title
        setItem(inv, 4, createInfoItem(Material.PAPER, "§6§l国家决议系统",
            "§7管理国家内的重要提案和投票",
            "§7- 提案需要满足政体要求",
            "§7- 通过后自动执行"));

        // My proposals
        setItem(inv, 20, createMenuItem(Material.BOOK, "§e§l我的提案",
            "§7查看我提交的所有提案",
            "§a点击打开"));

        // Pending signatures
        setItem(inv, 22, createMenuItem(Material.FEATHER, "§c§l待签署决议",
            "§7需要你签署的决议",
            "§a点击打开"));

        // History
        setItem(inv, 24, createMenuItem(Material.BOOKSHELF, "§b§l历史记录",
            "§7查看已完成的决议",
            "§a点击打开"));

        // Create new proposal
        setItem(inv, 40, createMenuItem(Material.NETHER_STAR, "§a§l创建新提案",
            "§7提交新的决议提案",
            "§e点击选择提案类型"));

        // Help
        setItem(inv, 49, createMenuItem(Material.BLAZE_ROD, "§6§l帮助",
            "§7查看决议系统帮助信息"));

        return inv;
    }

    private org.bukkit.inventory.Inventory createMyProposalsMenu(UUID playerId, UUID nationId, int page) {
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, 54,
            Component.text("§6§l我的提案 - 第" + (page + 1) + "页"));

        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        List<Resolution> resolutions = resolutionModule.proposedBy(playerId);
        int itemsPerPage = 28;
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, resolutions.size());

        int slot = 10;
        for (int i = startIndex; i < endIndex && slot < 44; slot++) {
            if (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
                continue;
            }
            if (i < endIndex) {
                Resolution res = resolutions.get(i);
                setItem(inv, slot, createResolutionItem(res));
            }
            i++;
        }

        // Pagination
        if (page > 0) {
            setItem(inv, 45, createMenuItem(Material.ARROW, "§c上一页", "§7上一页"));
        }
        if (endIndex < resolutions.size()) {
            setItem(inv, 53, createMenuItem(Material.ARROW, "§a下一页", "§7下一页"));
        }

        setItem(inv, 49, createBackButton());

        return inv;
    }

    private org.bukkit.inventory.Inventory createPendingSignaturesMenu(UUID playerId, UUID nationId, int page) {
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, 54,
            Component.text("§c§l待签署决议 - 第" + (page + 1) + "页"));

        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        // Get resolutions that are open and player might need to sign
        Optional<Nation> nationOpt = nationService.nationById(NationId.of(nationId));
        List<Resolution> pending = new ArrayList<>();

        if (nationOpt.isPresent()) {
            Nation nation = nationOpt.get();
            Collection<Resolution> openResolutions = resolutionModule.openResolutions(nation);
            for (Resolution res : openResolutions) {
                // Include if player hasn't signed yet and resolution is open
                if (!res.signatures().contains(playerId)) {
                    // Check if player is a member of the nation
                    boolean isMember = nation.members().stream()
                        .anyMatch(m -> m.playerId().equals(playerId));
                    if (isMember) {
                        pending.add(res);
                    }
                }
            }
        }

        int itemsPerPage = 28;
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, pending.size());

        int slot = 10;
        for (int i = startIndex; i < endIndex && slot < 44; slot++) {
            if (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
                continue;
            }
            if (i < endIndex) {
                Resolution res = pending.get(i);
                setItem(inv, slot, createResolutionItem(res));
            }
            i++;
        }

        // Pagination
        if (page > 0) {
            setItem(inv, 45, createMenuItem(Material.ARROW, "§c上一页", "§7上一页"));
        }
        if (endIndex < pending.size()) {
            setItem(inv, 53, createMenuItem(Material.ARROW, "§a下一页", "§7下一页"));
        }

        setItem(inv, 49, createBackButton());

        return inv;
    }

    private org.bukkit.inventory.Inventory createHistoryMenu(UUID playerId, UUID nationId, int page) {
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, 54,
            Component.text("§b§l历史记录 - 第" + (page + 1) + "页"));

        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        Optional<Nation> nationOpt = nationService.nationById(NationId.of(nationId));
        List<Resolution> history = nationOpt.map(resolutionModule::history).orElse(List.of());

        int itemsPerPage = 28;
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, history.size());

        int slot = 10;
        for (int i = startIndex; i < endIndex && slot < 44; slot++) {
            if (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
                continue;
            }
            if (i < endIndex) {
                Resolution res = history.get(i);
                setItem(inv, slot, createResolutionItem(res));
            }
            i++;
        }

        // Pagination
        if (page > 0) {
            setItem(inv, 45, createMenuItem(Material.ARROW, "§c上一页", "§7上一页"));
        }
        if (endIndex < history.size()) {
            setItem(inv, 53, createMenuItem(Material.ARROW, "§a下一页", "§7下一页"));
        }

        setItem(inv, 49, createBackButton());

        return inv;
    }

    private org.bukkit.inventory.Inventory createProposalTypeMenu() {
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, 36, Component.text("§a§l创建提案"));

        fillBorder(inv, Material.GRAY_STAINED_GLASS_PANE);

        // Join request proposal
        setItem(inv, 11, createMenuItem(Material.PLAYER_HEAD, "§e§l加入申请",
            "§7提案批准玩家加入国家",
            "§a点击创建"));

        // Rename proposal
        setItem(inv, 13, createMenuItem(Material.NAME_TAG, "§b§l国家改名",
            "§7提案更改国家名称",
            "§a点击创建"));

        // Government change proposal
        setItem(inv, 15, createMenuItem(Material.GOLDEN_APPLE, "§c§l政体变更",
            "§7提案更改国家政体类型",
            "§a点击创建"));

        // Diplomacy proposal
        setItem(inv, 30, createMenuItem(Material.BEACON, "§d§l外交关系",
            "§7提案更改与某国的外交关系",
            "§a点击创建"));

        setItem(inv, 22, createMenuItem(Material.BARRIER, "§c§l取消",
            "§7返回上一页"));

        setItem(inv, 31, createBackButton());

        return inv;
    }

    private org.bukkit.inventory.Inventory createRenameNationMenu(Nation nation) {
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, 36,
            Component.text("§b§l国家改名提案"));

        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        // Info
        setItem(inv, 4, createInfoItem(Material.NAME_TAG, "§e§l当前国家名称",
            "§7" + nation.name(),
            "",
            "§7输入新的国家名称来创建提案"));

        // Current name display
        setItem(inv, 13, createInfoItem(Material.PAPER, "§7当前名称: §f" + nation.name(),
            "§7点击下方按钮输入新名称"));

        // Input button
        setItem(inv, 24, createMenuItem(Material.NETHER_STAR, "§a§l输入新名称",
            "§7点击后在聊天中输入",
            "§e点击打开输入"));

        setItem(inv, 31, createBackButton());

        return inv;
    }

    private org.bukkit.inventory.Inventory createJoinRequestMenu(Nation nation) {
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, 36,
            Component.text("§e§l加入申请提案"));

        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        // Info
        setItem(inv, 4, createInfoItem(Material.PLAYER_HEAD, "§e§l目标国家",
            "§7" + nation.name(),
            "",
            "§7输入要加入的玩家名称来创建提案"));

        // Input button
        setItem(inv, 24, createMenuItem(Material.NETHER_STAR, "§a§l输入玩家名称",
            "§7点击后在聊天中输入",
            "§e点击打开输入"));

        setItem(inv, 31, createBackButton());

        return inv;
    }

    private org.bukkit.inventory.Inventory createGovernmentChangeMenu(Nation nation) {
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, 54,
            Component.text("§c§l政体变更提案"));

        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        // Current government info
        setItem(inv, 4, createInfoItem(Material.GOLDEN_APPLE, "§e§l当前政体",
            "§7" + nation.governmentType().displayName(),
            "",
            "§7选择一个政体来创建提案"));

        // Government type buttons - 6 types
        GovernmentType[] types = GovernmentType.values();
        int[] slots = {20, 22, 24, 29, 31, 33};
        Material[] materials = {Material.GOLDEN_HELMET, Material.BONE, Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.ENDER_EYE, Material.EMERALD};

        for (int i = 0; i < Math.min(types.length, slots.length); i++) {
            GovernmentType type = types[i];
            Material material = materials[i];
            boolean isCurrent = type == nation.governmentType();

            List<String> lore = new ArrayList<>();
            lore.add("§7" + type.name());
            if (isCurrent) {
                lore.add("§c当前政体");
            } else {
                lore.add("§a点击选择");
            }
            lore.add("");
            lore.add("§7提案签名要求: §f" + type.requiredSignatures(nation, null) + " 人");

            String name = "§f" + type.displayName();
            if (isCurrent) {
                name = "§7" + type.displayName() + " §c[当前]";
            }

            ItemStack item = createCustomItem(material, name, lore);
            setItem(inv, slots[i], item);
        }

        setItem(inv, 49, createBackButton());

        return inv;
    }

    private org.bukkit.inventory.Inventory createDiplomacyTargetMenu(Nation nation) {
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, 54,
            Component.text("§d§l外交关系提案 - 选择目标国家"));

        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        // Info
        setItem(inv, 4, createInfoItem(Material.BEACON, "§e§l外交目标选择",
            "§7选择要与哪个国家建立外交关系",
            "",
            "§7点击国家或手动输入名称"));

        // Input button
        setItem(inv, 22, createMenuItem(Material.NAME_TAG, "§a§l手动输入国家名称",
            "§7点击后在聊天中输入",
            "§e点击打开输入"));

        // List other nations
        Collection<Nation> allNations = nationService.nations();
        List<Nation> otherNations = allNations.stream()
            .filter(n -> !n.id().equals(nation.id()))
            .limit(14)
            .collect(Collectors.toList());

        int slot = 30;
        for (Nation targetNation : otherNations) {
            if (slot > 43) break;
            if (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
                continue;
            }
            List<String> lore = new ArrayList<>();
            lore.add("§7成员: §f" + targetNation.members().size());
            lore.add("§7政体: §f" + targetNation.governmentType().displayName());
            lore.add("");
            lore.add("§a点击选择");

            setItem(inv, slot, createCustomItem(Material.MAP, "§f" + targetNation.name(), lore));
            slot++;
        }

        // Cancel
        setItem(inv, 49, createMenuItem(Material.BARRIER, "§c§l取消", "§7返回上一页"));

        setItem(inv, 53, createBackButton());

        return inv;
    }

    private org.bukkit.inventory.Inventory createDiplomacyRelationMenu(Nation nation, Nation targetNation) {
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, 36,
            Component.text("§d§l外交关系 - " + targetNation.name()));

        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        // Info
        setItem(inv, 4, createInfoItem(Material.BEACON, "§e§l选择关系类型",
            "§7与 §f" + targetNation.name() + " §7的外交关系",
            "",
            "§7选择一个关系类型"));

        // Relation buttons
        DiplomacyRelation[] relations = DiplomacyRelation.values();
        int[] slots = {11, 12, 13, 15, 20, 21, 22, 23, 24};
        Material[] materials = {
            Material.WHITE_WOOL, Material.LIME_WOOL, Material.LIGHT_BLUE_WOOL,
            Material.YELLOW_WOOL, Material.ORANGE_WOOL, Material.RED_WOOL,
            Material.BROWN_WOOL, Material.GRAY_WOOL, Material.BLACK_WOOL
        };

        for (int i = 0; i < Math.min(relations.length, slots.length); i++) {
            Material material = i < materials.length ? materials[i] : Material.PAPER;
            DiplomacyRelation relation = relations[i];
            List<String> lore = new ArrayList<>();
            lore.add("§7" + relation.name());
            lore.add("");
            lore.add("§a点击选择");
            setItem(inv, slots[i], createCustomItem(material, "§f" + relation.displayName(), lore));
        }

        setItem(inv, 31, createBackButton());

        return inv;
    }

    private org.bukkit.inventory.Inventory createDiplomacyConfirmMenu(Nation nation, Nation targetNation, DiplomacyRelation relation) {
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, 36,
            Component.text("§a§l确认外交提案"));

        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        // Info
        setItem(inv, 4, createInfoItem(Material.BEACON, "§e§l提案确认",
            "§7目标国家: §f" + targetNation.name(),
            "§7关系类型: §f" + relation.displayName(),
            "",
            "§7确认创建此外交关系提案？"));

        // Confirm button
        setItem(inv, 24, createMenuItem(Material.LIME_STAINED_GLASS_PANE, "§a§l确认提案",
            "§7创建外交关系变更提案",
            "§e点击确认"));

        // Change button
        setItem(inv, 22, createMenuItem(Material.ARROW, "§e§l重新选择关系",
            "§7更改关系类型",
            "§e点击返回"));

        setItem(inv, 31, createBackButton());

        return inv;
    }

    private org.bukkit.inventory.Inventory createResolutionDetailMenu(Player player, UUID resolutionId) {
        Optional<Resolution> resOpt = resolutionModule.find(resolutionId);
        if (resOpt.isEmpty()) {
            return Bukkit.createInventory(null, 9, Component.text("§c决议不存在"));
        }

        Resolution res = resOpt.get();
        Optional<Nation> nationOpt = nationService.nationById(res.nationId());
        int requiredSigs = nationOpt.map(n -> governmentService.requiredSignatures(n, res)).orElse(1);

        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, 36,
            Component.text("§6§l决议详情"));

        fillBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        // Resolution info
        String statusColor = switch (res.state()) {
            case OPEN -> "§e";
            case ENACTED -> "§a";
            case EXPIRED -> "§6";
            case FAILED, CANCELLED -> "§c";
        };

        setItem(inv, 4, createInfoItem(Material.PAPER, "§6" + res.action().summary(),
            "§7类型: §f" + res.action().kind().name(),
            "§7状态: " + statusColor + res.state().name(),
            "§7提案人: §f" + res.proposerName(),
            "§7签名: §f" + res.signatures().size() + "/" + requiredSigs));

        // Sign button (if open and player hasn't signed)
        if (res.isOpen() && !res.signatures().contains(player.getUniqueId())) {
            setItem(inv, 22, createMenuItem(Material.FEATHER, "§a§l签署决议",
                "§7为这个提案签署同意",
                "§e点击签署"));
        } else if (res.isOpen()) {
            setItem(inv, 22, createMenuItem(Material.LIME_STAINED_GLASS_PANE, "§a§l已签署",
                "§7你已经签署了这个提案"));
        } else {
            setItem(inv, 22, createMenuItem(Material.BARRIER, "§7§l无法签署",
                "§c该决议已结束"));
        }

        // Cancel button (if open and player is proposer)
        if (res.isOpen() && res.proposerId().equals(player.getUniqueId())) {
            setItem(inv, 24, createMenuItem(Material.TNT, "§c§l取消提案",
                "§7取消这个提案",
                "§e点击取消"));
        }

        // Signatures list
        StringBuilder sigNames = new StringBuilder();
        for (UUID sigId : res.signatures()) {
            String name = Bukkit.getOfflinePlayer(sigId).getName();
            sigNames.append(name != null ? name : "Unknown").append(", ");
        }
        if (sigNames.length() > 0) {
            sigNames.setLength(sigNames.length() - 2);
        }

        setItem(inv, 13, createInfoItem(Material.PLAYER_HEAD, "§e§l已签名玩家",
            "§7" + (sigNames.length() > 0 ? sigNames.toString() : "§7暂无签名")));

        // Expiry info
        setItem(inv, 31, createInfoItem(Material.CLOCK, "§7§l时间信息",
            "§7创建时间: §f" + res.createdAt(),
            "§7过期时间: §f" + res.expiresAt()));

        setItem(inv, 27, createBackButton());

        return inv;
    }

    // ==================== Helper Methods ====================

    private String getMenuType(String title) {
        if (title.contains("决议系统")) return "决议系统";
        if (title.contains("我的提案")) return "我的提案";
        if (title.contains("待签署")) return "待签署";
        if (title.contains("历史记录")) return "历史记录";
        if (title.contains("创建提案")) return "创建提案";
        if (title.contains("国家改名")) return "国家改名";
        if (title.contains("加入申请")) return "加入申请";
        if (title.contains("政体变更")) return "政体变更";
        if (title.contains("外交关系") && title.contains("选择目标")) return "外交目标";
        if (title.contains("外交关系")) return "外交关系";
        if (title.contains("确认外交")) return "外交确认";
        if (title.contains("决议详情")) return "决议详情";
        return "决议系统";
    }

    private int getPageFromTitle(String title) {
        try {
            int start = title.indexOf("第") + 1;
            int end = title.indexOf("页");
            if (start > 0 && end > start) {
                return Integer.parseInt(title.substring(start, end)) - 1;
            }
        } catch (Exception e) {
            Logger.getLogger(ResolutionGuiListener.class.getName()).warning("解析页码失败: " + e.getMessage());
        }
        return 0;
    }

    private UUID extractResolutionId(ItemStack item) {
        if (item == null || !item.hasItemMeta() || item.getItemMeta().lore() == null) {
            return null;
        }
        for (Component loreComponent : item.getItemMeta().lore()) {
            String lore = PlainTextComponentSerializer.plainText().serialize(loreComponent);
            if (lore.contains("ID: ")) {
                try {
                    return UUID.fromString(lore.replace("§7ID: ", "").trim());
                } catch (Exception e) {
                    Logger.getLogger(ResolutionGuiListener.class.getName()).warning("解析决议ID失败: " + e.getMessage());
                }
            }
        }
        return null;
    }

    private String extractNationName(ItemStack item) {
        if (item == null || !item.hasItemMeta() || item.getItemMeta().displayName() == null) {
            return null;
        }
        String name = PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
        if (name.startsWith("§f")) {
            name = name.substring(2);
        }
        return name.trim();
    }

    private UUID extractResolutionIdFromTitle(String title) {
        return null;
    }

    private GovernmentType getGovernmentTypeFromSlot(int slot, ItemStack item) {
        int[] slots = {20, 22, 24, 29, 31, 33};
        GovernmentType[] types = GovernmentType.values();

        for (int i = 0; i < Math.min(slots.length, types.length); i++) {
            if (slots[i] == slot) {
                return types[i];
            }
        }
        return null;
    }

    private DiplomacyRelation getDiplomacyRelationFromSlot(int slot, ItemStack item) {
        int[] slots = {11, 12, 13, 15, 20, 21, 22, 23, 24};
        DiplomacyRelation[] relations = DiplomacyRelation.values();

        for (int i = 0; i < Math.min(slots.length, relations.length); i++) {
            if (slots[i] == slot) {
                return relations[i];
            }
        }
        return null;
    }

    private ItemStack createResolutionItem(Resolution res) {
        Material material = switch (res.state()) {
            case OPEN -> Material.PAPER;
            case ENACTED -> Material.GREEN_STAINED_GLASS_PANE;
            case EXPIRED -> Material.YELLOW_STAINED_GLASS_PANE;
            case FAILED, CANCELLED -> Material.RED_STAINED_GLASS_PANE;
        };

        String statusColor = switch (res.state()) {
            case OPEN -> "§e";
            case ENACTED -> "§a";
            case EXPIRED -> "§6";
            case FAILED, CANCELLED -> "§c";
        };

        List<String> lore = new ArrayList<>();
        lore.add("§7类型: §f" + res.action().kind().name());
        lore.add("§7状态: " + statusColor + res.state().name());
        lore.add("§7提案人: §f" + res.proposerName());
        lore.add("§7签名: §f" + res.signatures().size());
        lore.add("§7ID: §8" + res.id());

        return createCustomItem(material, res.action().summary(), lore);
    }

    private ItemStack createMenuItem(Material material, String name, String... lore) {
        return createCustomItem(material, name, Arrays.asList(lore));
    }

    private ItemStack createInfoItem(Material material, String name, String... lore) {
        return createCustomItem(material, name, Arrays.asList(lore));
    }

    private ItemStack createBackButton() {
        return createCustomItem(Material.ARROW, "§c§l返回", List.of("§7返回上一页"));
    }

    private ItemStack createCustomItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        meta.lore(lore.stream()
            .map(Component::text)
            .toList());
        item.setItemMeta(meta);
        return item;
    }

    private void setItem(org.bukkit.inventory.Inventory inv, int slot, ItemStack item) {
        if (slot >= 0 && slot < inv.getSize()) {
            inv.setItem(slot, item);
        }
    }

    private void fillBorder(org.bukkit.inventory.Inventory inv, Material material) {
        ItemStack border = new ItemStack(material);
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border);
            inv.setItem(45 + i, border);
        }
        for (int i = 1; i < 5; i++) {
            inv.setItem(i * 9, border);
            inv.setItem(i * 9 + 8, border);
        }
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

    // E-042 修复: 玩家退出时清理所有 Map 状态
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        playerMenus.remove(playerId);
        pendingDiplomacyRelation.remove(playerId);
    }
}
