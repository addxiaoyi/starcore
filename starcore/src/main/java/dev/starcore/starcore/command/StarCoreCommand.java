package dev.starcore.starcore.command;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleDescriptor;
import dev.starcore.starcore.core.module.ModuleStatus;
import dev.starcore.starcore.foundation.epoch.EpochService;
import dev.starcore.starcore.foundation.epoch.EpochSnapshot;
import dev.starcore.starcore.foundation.economy.InternalEconomyService;
import dev.starcore.starcore.foundation.feedback.BukkitInGameFeedbackService;
import dev.starcore.starcore.foundation.feedback.InGameFeedbackProfile;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.foundation.player.PlayerProfileService;
import dev.starcore.starcore.foundation.protection.ExternalProtectionBridgeStatus;
import dev.starcore.starcore.foundation.protection.ExternalProtectionService;
import dev.starcore.starcore.foundation.territory.model.ChunkClaimSelection;
import dev.starcore.starcore.foundation.territory.model.TerritoryClaim;
import dev.starcore.starcore.foundation.timesync.TimeSyncService;
import dev.starcore.starcore.module.diplomacy.DiplomacyRelation;
import dev.starcore.starcore.module.diplomacy.DiplomacyRelationSnapshot;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.event.EventService;
import dev.starcore.starcore.module.government.GovernmentService;
import dev.starcore.starcore.module.government.model.GovernmentType;
import dev.starcore.starcore.module.map.MapService;
import dev.starcore.starcore.module.map.model.WebClaimConfirmationResult;
import dev.starcore.starcore.module.nation.NationOperationalOverview;
import dev.starcore.starcore.module.nation.NationOperationalSupport;
import dev.starcore.starcore.module.nation.claimtool.ClaimToolService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.ClaimSelectionExplanation;
import dev.starcore.starcore.module.nation.model.ClaimSelectionPreview;
import dev.starcore.starcore.module.nation.model.ClaimSelectionReason;
import dev.starcore.starcore.module.nation.model.ClaimSelectionResult;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.nation.model.NationKind;
import dev.starcore.starcore.module.nation.resource.NationResourceDistrictOperationalSupport;
import dev.starcore.starcore.module.nation.resource.NationResourceDistrictService;
import dev.starcore.starcore.module.officer.OfficerAppointment;
import dev.starcore.starcore.module.officer.OfficerService;
import dev.starcore.starcore.module.policy.PolicyService;
import dev.starcore.starcore.module.policy.model.PolicyActivationResult;
import dev.starcore.starcore.module.policy.model.PolicyDefinition;
import dev.starcore.starcore.module.policy.model.PolicyEffect;
import dev.starcore.starcore.module.policy.model.PolicyCategory;
import dev.starcore.starcore.module.resolution.ResolutionService;
import dev.starcore.starcore.module.resolution.model.ChangeDiplomacyRelationAction;
import dev.starcore.starcore.module.resolution.model.ChangeGovernmentAction;
import dev.starcore.starcore.module.resolution.model.JoinNationRequestAction;
import dev.starcore.starcore.module.resolution.model.RenameNationAction;
import dev.starcore.starcore.module.resolution.model.Resolution;
import dev.starcore.starcore.module.resolution.model.ResolutionAction;
import dev.starcore.starcore.module.resolution.model.ResolutionState;
import dev.starcore.starcore.module.technology.TechnologyCost;
import dev.starcore.starcore.module.technology.TechnologyService;
import dev.starcore.starcore.module.treasury.TreasuryRewardResult;
import dev.starcore.starcore.module.treasury.TreasuryRewardService;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.starcore.starcore.module.war.WarService;
import dev.starcore.starcore.module.war.WarSnapshot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public final class StarCoreCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ECONOMY_AMOUNT_SUGGESTIONS = List.of("100", "500", "1000", "10000", "100000");
    private final StarCoreContext context;
    private final MessageService messages;
    private final EventCommandHandler eventCommands;
    private final ResourceCommandHandler resourceCommands;
    private final DebugCommandHandler debugCommands;

    public StarCoreCommand(StarCoreContext context) {
        this.context = context;
        this.messages = context.serviceRegistry().require(MessageService.class);
        this.eventCommands = new EventCommandHandler(context, messages);
        this.resourceCommands = new ResourceCommandHandler(context, messages);
        this.debugCommands = new DebugCommandHandler(context);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("starcore.command")) {
            dev.starcore.starcore.foundation.message.StarMessages.error(sender, msg("command.no-permission"));
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }
        switch (normalizeRoot(args[0])) {
            case "help" -> sendHelp(sender, label);
            case "status" -> sendStatus(sender);
            case "modules" -> sendModules(sender);
            // audit C-017: reload / debug 为管理员子命令，前置 starcore.admin 校验（reload() 内部亦校验，此处作为早期拒绝）
            case "reload" -> {
                if (!sender.hasPermission("starcore.admin")) {
                    dev.starcore.starcore.foundation.message.StarMessages.error(sender, msg("command.no-permission"));
                } else {
                    reload(sender);
                }
            }
            case "nation" -> handleNation(sender, label, args);
            case "resolution" -> handleResolution(sender, label, args);
            case "government" -> handleGovernment(sender, label, args);
            case "map" -> handleMap(sender, label, args);
            case "diplomacy" -> handleDiplomacy(sender, label, args);
            case "treasury" -> handleTreasury(sender, label, args);
            case "policy" -> handlePolicy(sender, label, args);
            case "resource" -> resourceCommands.handle(sender, label, args);
            case "technology" -> handleTechnology(sender, label, args);
            case "war" -> handleWar(sender, label, args);
            case "officer" -> handleOfficer(sender, label, args);
            case "event" -> handleEvent(sender, label, args);
            case "epoch" -> handleEpoch(sender, label, args);
            case "time" -> handleTimeSync(sender, label, args);
            case "economy" -> handleEconomy(sender, label, args);
            case "debug" -> handleDebug(sender, args);
            default -> sendHelp(sender, label);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // audit C-019: tab 补全前置权限校验，与 onCommand 一致
        if (!sender.hasPermission("starcore.command")) {
            return List.of();
        }
        if (args.length == 1) {
            return prefixMatches(StarCoreCommandAliases.rootSuggestions(), args[0]);
        }
        String root = normalizeRoot(args[0]);
        if (args.length == 2 && root.equals("nation")) {
            return prefixMatches(List.of("c", "cl", "t", "ok", "x", "un", "ls", "i", "here", "city", "join", "rn", "ct",
                "create", "claim", "tool", "confirm", "cancel", "unclaim", "claims", "info", "list", "rename",
                "建", "创建", "圈", "圈地", "工", "工具", "确", "确认", "取", "取消", "放", "放弃", "领", "领地",
                "此", "此处", "城", "城邦", "加", "加入", "改", "改名"), args[1]);
        }
        if (args.length == 3 && root.equals("nation")) {
            String subcommand = normalizeNationSubcommand(args[1]);
            if (subcommand.equals("city")) {
                return prefixMatches(List.of("c", "ls", "create", "list", "建", "创建", "列", "列表"), args[2]);
            }
            if (subcommand.equals("info") || subcommand.equals("join")) {
                return prefixMatches(nationNameSuggestions(), args[2]);
            }
        }
        if (args.length == 2 && root.equals("epoch")) {
            return prefixMatches(List.of("s", "status", "状", "状态"), args[1]);
        }
        if (args.length == 2 && root.equals("time")) {
            return prefixMatches(List.of("s", "status", "状", "状态"), args[1]);
        }
        if (args.length == 2 && root.equals("economy")) {
            return prefixMatches(List.of("b", "g", "t", "s", "balance", "give", "take", "set", "余", "余额", "给", "给予", "扣", "扣除", "设", "设置"), args[1]);
        }
        if (args.length == 2 && root.equals("debug")) {
            return debugCommands.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
        }
        if (args.length >= 3 && root.equals("debug")) {
            return debugCommands.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
        }
        if (args.length == 3 && root.equals("economy")) {
            String subcommand = normalizeEconomySubcommand(args[1]);
            if (subcommand.equals("balance") || subcommand.equals("give") || subcommand.equals("take") || subcommand.equals("set")) {
                return prefixMatches(onlinePlayerNameSuggestions(), args[2]);
            }
        }
        if (args.length == 4 && root.equals("economy")) {
            String subcommand = normalizeEconomySubcommand(args[1]);
            if (subcommand.equals("give") || subcommand.equals("take") || subcommand.equals("set")) {
                return prefixMatches(ECONOMY_AMOUNT_SUGGESTIONS, args[3]);
            }
        }
        if (args.length == 2 && root.equals("resolution")) {
            return prefixMatches(List.of("ls", "s", "list", "sign", "x", "cancel", "i", "info", "h", "history", "gui", "menu",
                "列", "列表", "签", "签署", "取", "取消", "信", "信息", "历", "历史", "菜", "菜单"), args[1]);
        }
        if (args.length == 2 && root.equals("government")) {
            return prefixMatches(List.of("i", "p", "info", "propose", "信", "信息", "提", "提案"), args[1]);
        }
        if (args.length == 2 && root.equals("map")) {
            return prefixMatches(List.of("s", "ex", "w", "ok", "x", "status", "export", "web", "confirm", "cancel", "状", "状态", "导", "导出", "网", "网页", "确", "确认", "取", "取消"), args[1]);
        }
        if (args.length == 2 && root.equals("diplomacy")) {
            return prefixMatches(List.of("s", "set", "ls", "p", "status", "list", "propose", "状", "状态", "设", "设置", "列", "列表", "提", "提案"), args[1]);
        }
        if (args.length == 2 && root.equals("treasury")) {
            return prefixMatches(List.of("s", "d", "w", "inc", "rw", "tax", "status", "deposit", "withdraw", "income", "reward", "税", "收税", "税收", "状", "状态", "存", "存入", "支", "支出", "收", "收入", "奖", "奖励"), args[1]);
        }
        if (args.length == 2 && root.equals("policy")) {
            return prefixMatches(List.of("s", "set", "x", "t", "gui", "menu", "tree", "status", "clear", "状", "状态", "设", "设置", "清", "清除", "菜", "菜单", "树"), args[1]);
        }
        if (root.equals("resource")) {
            return resourceCommands.complete(sender, args);
        }
        if (args.length == 2 && root.equals("technology")) {
            return prefixMatches(List.of("s", "u", "x", "t", "gui", "menu", "tree", "status", "unlock", "revoke", "状", "状态", "解", "解锁", "撤", "撤销", "菜", "菜单", "树"), args[1]);
        }
        if (args.length == 3 && root.equals("technology")) {
            return prefixMatches(nationNameSuggestions(), args[2]);
        }
        if (args.length == 4 && root.equals("technology")) {
            TechnologyService technologyService = context.serviceRegistry().find(TechnologyService.class).orElse(null);
            if (technologyService != null) {
                return prefixMatches(technologyService.availableTechnologies().stream().toList(), args[3]);
            }
        }
        if (args.length == 2 && root.equals("war")) {
            return prefixMatches(List.of("s", "d", "e", "status", "declare", "end", "状", "状态", "宣", "宣战", "停", "停战"), args[1]);
        }
        if (args.length == 2 && root.equals("officer")) {
            return prefixMatches(List.of("s", "a", "rm", "ls", "i", "gui", "status", "appoint", "remove", "list", "info", "menu", "状", "状态", "任", "任命", "移", "移除", "列", "列表", "详", "详情", "菜", "菜单"), args[1]);
        }
        if (root.equals("event")) {
            return eventCommands.complete(args);
        }
        if (args.length == 3 && root.equals("officer") && (normalizeOfficerSubcommand(args[1]).equals("appoint") || normalizeOfficerSubcommand(args[1]).equals("remove"))) {
            OfficerService officerService = context.serviceRegistry().find(OfficerService.class).orElse(null);
            if (officerService != null) {
                return prefixMatches(officerService.availableRoles().stream().toList(), args[2]);
            }
        }
        if (args.length == 4 && root.equals("officer") && normalizeOfficerSubcommand(args[1]).equals("appoint")) {
            return prefixMatches(onlinePlayerNameSuggestions(), args[3]);
        }
        if ((args.length == 3 || args.length == 4) && root.equals("war")) {
            return prefixMatches(nationNameSuggestions(), args[args.length - 1]);
        }
        if (args.length == 3 && root.equals("policy") && normalizePolicySubcommand(args[1]).equals("set")) {
            PolicyService policyService = context.serviceRegistry().find(PolicyService.class).orElse(null);
            if (policyService != null) {
                return prefixMatches(policyService.availablePolicies().stream().toList(), args[2]);
            }
        }
        if (args.length == 3 && root.equals("policy") && (normalizePolicySubcommand(args[1]).equals("status") || normalizePolicySubcommand(args[1]).equals("tree"))) {
            return prefixMatches(nationNameSuggestions(), args[2]);
        }
        if (args.length == 3 && root.equals("treasury")) {
            String subcommand = normalizeTreasurySubcommand(args[1]);
            List<String> suggestions = subcommand.equals("income") || subcommand.equals("tax") ? merged(nationNameSuggestions(), List.of("all", "全部", "所有")) : nationNameSuggestions();
            return prefixMatches(suggestions, args[2]);
        }
        if (args.length == 4 && root.equals("treasury")
            && (normalizeTreasurySubcommand(args[1]).equals("deposit") || normalizeTreasurySubcommand(args[1]).equals("withdraw") || normalizeTreasurySubcommand(args[1]).equals("reward"))) {
            return prefixMatches(ECONOMY_AMOUNT_SUGGESTIONS, args[3]);
        }
        if (args.length == 3 && root.equals("government") && normalizeGovernmentSubcommand(args[1]).equals("propose")) {
            return prefixMatches(merged(Arrays.stream(GovernmentType.values()).map(type -> type.name().toLowerCase(Locale.ROOT)).toList(), List.of("君主", "独裁", "共和", "民主")), args[2]);
        }
        if (args.length == 3 && root.equals("diplomacy")) {
            String subcommand = normalizeDiplomacySubcommand(args[1]);
            if (subcommand.equals("status") || subcommand.equals("set") || subcommand.equals("list") || subcommand.equals("propose")) {
                return prefixMatches(nationNameSuggestions(), args[2]);
            }
        }
        if (args.length == 4 && root.equals("diplomacy")) {
            String subcommand = normalizeDiplomacySubcommand(args[1]);
            if (subcommand.equals("status") || subcommand.equals("set")) {
                return prefixMatches(nationNameSuggestions(), args[3]);
            }
        }
        if (args.length == 4 && root.equals("diplomacy") && normalizeDiplomacySubcommand(args[1]).equals("propose")) {
            return prefixMatches(diplomacyRelationSuggestions(), args[3]);
        }
        if (args.length == 5 && root.equals("diplomacy") && normalizeDiplomacySubcommand(args[1]).equals("set")) {
            return prefixMatches(diplomacyRelationSuggestions(), args[4]);
        }
        return List.of();
    }

    private void handleNation(CommandSender sender, String label, String[] args) {
        NationService nationService = context.serviceRegistry().find(NationService.class).orElse(null);
        ResolutionService resolutionService = context.serviceRegistry().find(ResolutionService.class).orElse(null);
        if (nationService == null) {
            sender.sendMessage(coloredMsg("command.nation.disabled-service"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(coloredMsg("command.nation.usage"));
            return;
        }
        switch (normalizeNationSubcommand(args[1])) {
            case "create" -> nationCreate(sender, args, nationService);
            case "city" -> nationCity(sender, label, args, nationService);
            case "info" -> nationInfo(sender, args, nationService);
            case "claim" -> nationClaim(sender, args, nationService);
            case "tool" -> nationTool(sender);
            case "confirm" -> nationToolConfirm(sender);
            case "cancel" -> nationToolCancel(sender);
            case "unclaim" -> nationUnclaim(sender, args, nationService);
            case "claims" -> nationClaims(sender, nationService);
            case "here" -> nationHere(sender, nationService);
            case "list" -> nationList(sender, nationService);
            case "join" -> nationJoin(sender, args, nationService, resolutionService);
            case "rename" -> nationRename(sender, args, nationService, resolutionService);
            case "gui", "menu" -> nationGui(sender, nationService);
            default -> sender.sendMessage(coloredMsg("command.nation.usage-extended"));
        }
    }

    private void handleEpoch(CommandSender sender, String label, String[] args) {
        EpochService epochService = context.serviceRegistry().find(EpochService.class).orElse(null);
        if (epochService == null) {
            sender.sendMessage(Component.text(msg("command.epoch.disabled-service"), NamedTextColor.RED));
            return;
        }
        if (args.length >= 2 && !normalizeSimple(args[1]).equals("status")) {
            sender.sendMessage(Component.text(msg("command.epoch.usage", label), NamedTextColor.YELLOW));
            return;
        }
        EpochSnapshot snapshot = epochService.snapshot();
        sender.sendMessage(Component.text(msg("command.epoch.title"), NamedTextColor.GOLD));
        sender.sendMessage(Component.text(msg("command.epoch.status-label"), NamedTextColor.GRAY)
            .append(Component.text(snapshot.enabled() ? msg("command.epoch.enabled") : msg("command.epoch.disabled"), snapshot.enabled() ? NamedTextColor.GREEN : NamedTextColor.RED)));
        sender.sendMessage(Component.text(msg("command.epoch.current"), NamedTextColor.GRAY).append(Component.text(String.valueOf(snapshot.epochNumber()), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text(msg("command.epoch.started"), NamedTextColor.GRAY).append(Component.text(snapshot.epochStart().toString(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text(msg("command.epoch.next"), NamedTextColor.GRAY).append(Component.text(snapshot.nextEpochStart().toString(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text(msg("command.epoch.remaining"), NamedTextColor.GRAY).append(Component.text(epochService.localizedDuration(snapshot.remaining()), NamedTextColor.WHITE)));
    }

    private void handleTimeSync(CommandSender sender, String label, String[] args) {
        TimeSyncService timeSyncService = context.serviceRegistry().find(TimeSyncService.class).orElse(null);
        if (timeSyncService == null) {
            sender.sendMessage(Component.text(msg("command.time-sync.disabled-service"), NamedTextColor.RED));
            return;
        }
        if (args.length >= 2 && !normalizeSimple(args[1]).equals("status")) {
            sender.sendMessage(Component.text(msg("command.time-sync.usage", label), NamedTextColor.YELLOW));
            return;
        }
        boolean enabled = context.configuration().timeSyncEnabled();
        sender.sendMessage(Component.text(msg("command.time-sync.title"), NamedTextColor.GOLD));
        sender.sendMessage(Component.text(msg("command.time-sync.status-label"), NamedTextColor.GRAY)
            .append(Component.text(enabled ? msg("command.time-sync.enabled") : msg("command.time-sync.disabled"), enabled ? NamedTextColor.GREEN : NamedTextColor.RED)));
        sender.sendMessage(Component.text(msg("command.time-sync.summary"), NamedTextColor.GRAY)
            .append(Component.text(timeSyncService.summary(), NamedTextColor.WHITE)));
    }

    private void handleEconomy(CommandSender sender, String label, String[] args) {
        InternalEconomyService economyService = context.economyService();
        if (args.length < 2) {
            sender.sendMessage(Component.text(msg("command.economy.usage", label), NamedTextColor.YELLOW));
            return;
        }
        switch (normalizeEconomySubcommand(args[1])) {
            case "balance" -> economyBalance(sender, args, economyService);
            case "give" -> economyGive(sender, args, economyService);
            case "take" -> economyTake(sender, args, economyService);
            case "set" -> economySet(sender, args, economyService);
            default -> sender.sendMessage(Component.text(msg("command.economy.usage", label), NamedTextColor.YELLOW));
        }
    }

    private void handleResolution(CommandSender sender, String label, String[] args) {
        ResolutionService resolutionService = context.serviceRegistry().find(ResolutionService.class).orElse(null);
        NationService nationService = context.serviceRegistry().find(NationService.class).orElse(null);
        GovernmentService governmentService = context.serviceRegistry().find(GovernmentService.class).orElse(null);
        if (resolutionService == null || nationService == null) {
            sender.sendMessage(Component.text(msg("command.resolution.disabled-service"), NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            // Default to GUI if player, otherwise show usage
            if (sender instanceof Player player) {
                openResolutionGui(player, nationService, resolutionService, governmentService);
                return;
            }
            sender.sendMessage(Component.text(msg("command.resolution.usage", label), NamedTextColor.YELLOW));
            return;
        }
        switch (normalizeResolutionSubcommand(args[1])) {
            case "list" -> resolutionList(sender, args, nationService, resolutionService);
            case "sign" -> resolutionSign(sender, args, nationService, resolutionService);
            case "cancel", "x" -> resolutionCancel(sender, args, nationService, resolutionService);
            case "info", "i" -> resolutionInfo(sender, args, nationService, resolutionService);
            case "history", "h" -> resolutionHistory(sender, args, nationService, resolutionService);
            case "gui", "menu" -> {
                if (sender instanceof Player player) {
                    openResolutionGui(player, nationService, resolutionService, governmentService);
                } else {
                    sender.sendMessage(Component.text(msg("command.player-only"), NamedTextColor.RED));
                }
            }
            default -> sender.sendMessage(Component.text(msg("command.resolution.usage", label), NamedTextColor.YELLOW));
        }
    }

    private void openResolutionGui(Player player, NationService nationService, ResolutionService resolutionService, GovernmentService governmentService) {
        Nation nation = nationService.nationOf(player.getUniqueId()).orElse(null);
        if (nation == null) {
            player.sendMessage(coloredMsg("command.nation.not-in-nation"));
            return;
        }
        Collection<Resolution> openResolutions = resolutionService.openResolutions(nation);
        String title = msg("command.resolution.gui-title", nation.name());
        try {
            InventoryView view = MenuType.GENERIC_9X3.builder()
                .title(Component.text(title))
                .build(player);
            populateResolutionGui(view.getTopInventory(), nation, openResolutions, resolutionService, governmentService);
            view.open();
        } catch (RuntimeException | LinkageError exception) {
            Inventory inventory = Bukkit.createInventory(null, 27, title);
            populateResolutionGui(inventory, nation, openResolutions, resolutionService, governmentService);
            player.openInventory(inventory);
        }
    }

    private void populateResolutionGui(Inventory inventory, Nation nation, Collection<Resolution> resolutions, ResolutionService resolutionService, GovernmentService governmentService) {
        inventory.setItem(4, menuItem(Material.PAPER, msg("command.resolution.gui-overview-name"), List.of(
            msg("command.resolution.gui-overview-lore", resolutions.size())
        ), NamedTextColor.GOLD));

        if (resolutions.isEmpty()) {
            inventory.setItem(13, menuItem(Material.BARRIER, msg("command.resolution.gui-empty"), List.of(), NamedTextColor.RED));
            return;
        }

        int slot = 10;
        for (Resolution resolution : resolutions) {
            if (slot >= inventory.getSize() - 8) {
                break;
            }
            if (slot % 9 == 8) {
                slot += 2;
            }
            Material material = switch (resolution.action().kind()) {
                case JOIN_NATION -> Material.PLAYER_HEAD;
                case RENAME_NATION -> Material.NAME_TAG;
                case CHANGE_GOVERNMENT -> Material.GOLDEN_APPLE;
                case CHANGE_DIPLOMACY_RELATION -> Material.EMERALD;
                default -> Material.PAPER;
            };
            int requiredSigs = governmentService != null ? governmentService.requiredSignatures(nation, resolution) : 1;
            List<String> lore = new ArrayList<>();
            lore.add(msg("command.resolution.gui-id", resolution.id().toString().substring(0, 8) + "..."));
            lore.add(msg("command.resolution.gui-type", resolution.action().kind().name()));
            lore.add(msg("command.resolution.gui-proposer", resolution.proposerName()));
            lore.add(msg("command.resolution.gui-signatures", resolution.signatures().size(), requiredSigs));
            lore.add(msg("command.resolution.gui-state", resolution.state().name()));
            lore.add(msg("command.resolution.gui-expires", resolution.expiresAt().toString()));
            lore.add("");
            lore.add(msg("command.resolution.gui-action", "/sc resolution sign " + resolution.id()));

            inventory.setItem(slot, menuItem(material, resolution.action().summary(), lore, NamedTextColor.WHITE));
            slot++;
        }
    }

    private void handleMap(CommandSender sender, String label, String[] args) {
        MapService mapService = context.serviceRegistry().find(MapService.class).orElse(null);
        if (mapService == null) {
            sender.sendMessage(Component.text(msg("command.map.disabled-service"), NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text(msg("command.map.usage", label), NamedTextColor.YELLOW));
            return;
        }
        switch (normalizeMapSubcommand(args[1])) {
            case "status" -> sender.sendMessage(Component.text(msg("command.map.status-title"), NamedTextColor.GOLD)
                .append(Component.text(mapService.summary(), NamedTextColor.WHITE)));
            case "export" -> mapExport(sender, mapService);
            case "web" -> mapWeb(sender, mapService);
            case "confirm" -> mapConfirm(sender, label, args, mapService);
            case "cancel" -> mapCancel(sender, label, args, mapService);
            default -> sender.sendMessage(Component.text(msg("command.map.usage", label), NamedTextColor.YELLOW));
        }
    }

    private void handleGovernment(CommandSender sender, String label, String[] args) {
        GovernmentService governmentService = context.serviceRegistry().find(GovernmentService.class).orElse(null);
        NationService nationService = context.serviceRegistry().find(NationService.class).orElse(null);
        if (governmentService == null || nationService == null) {
            sender.sendMessage(Component.text(msg("command.government.disabled-service"), NamedTextColor.RED));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(msg("command.player-only"), NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text(msg("command.government.usage", label), NamedTextColor.YELLOW));
            return;
        }
        switch (normalizeGovernmentSubcommand(args[1])) {
            case "info" -> governmentInfo(sender, player, nationService, governmentService);
            case "propose" -> governmentPropose(sender, args, player, nationService, governmentService);
            default -> sender.sendMessage(Component.text(msg("command.government.usage", label), NamedTextColor.YELLOW));
        }
    }

    private void handleDiplomacy(CommandSender sender, String label, String[] args) {
        DiplomacyService diplomacyService = context.serviceRegistry().find(DiplomacyService.class).orElse(null);
        NationService nationService = context.serviceRegistry().find(NationService.class).orElse(null);
        if (diplomacyService == null || nationService == null) {
            sender.sendMessage(Component.text(msg("command.diplomacy.disabled-service"), NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            diplomacyList(sender, new String[]{"sc", "diplomacy", "list"}, nationService, diplomacyService);
            return;
        }
        switch (normalizeDiplomacySubcommand(args[1])) {
            case "status" -> diplomacyStatus(sender, args, nationService, diplomacyService);
            case "set" -> diplomacySet(sender, args, nationService, diplomacyService);
            case "list" -> diplomacyList(sender, args, nationService, diplomacyService);
            case "propose" -> diplomacyPropose(sender, args, nationService);
            default -> {
                sender.sendMessage(Component.text(msg("command.diplomacy.usage", label), NamedTextColor.YELLOW));
                diplomacyList(sender, new String[]{"sc", "diplomacy", "list"}, nationService, diplomacyService);
            }
        }
    }

    private void handleTreasury(CommandSender sender, String label, String[] args) {
        TreasuryService treasuryService = context.serviceRegistry().find(TreasuryService.class).orElse(null);
        NationService nationService = context.serviceRegistry().find(NationService.class).orElse(null);
        if (treasuryService == null || nationService == null) {
            sender.sendMessage(Component.text(msg("command.treasury.disabled-service"), NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            treasuryStatus(sender, new String[]{"sc", "treasury", "status"}, nationService, treasuryService);
            return;
        }
        switch (normalizeTreasurySubcommand(args[1])) {
            case "status" -> treasuryStatus(sender, args, nationService, treasuryService);
            case "deposit" -> treasuryDeposit(sender, args, nationService, treasuryService);
            case "withdraw" -> treasuryWithdraw(sender, args, nationService, treasuryService);
            case "income" -> treasuryIncome(sender, args, nationService, treasuryService);
            case "reward" -> treasuryReward(sender, args, nationService, treasuryService);
            case "tax" -> treasuryTax(sender, args, nationService, treasuryService);
            default -> {
                sender.sendMessage(Component.text(msg("command.treasury.usage", label), NamedTextColor.YELLOW));
                treasuryStatus(sender, new String[]{"sc", "treasury", "status"}, nationService, treasuryService);
            }
        }
    }

    private void handlePolicy(CommandSender sender, String label, String[] args) {
        PolicyService policyService = context.serviceRegistry().find(PolicyService.class).orElse(null);
        NationService nationService = context.serviceRegistry().find(NationService.class).orElse(null);
        TreasuryService treasuryService = context.serviceRegistry().find(TreasuryService.class).orElse(null);
        if (policyService == null || nationService == null) {
            sender.sendMessage(Component.text(msg("command.policy.disabled-service"), NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text(msg("command.policy.usage", label), NamedTextColor.YELLOW));
            return;
        }
        switch (normalizePolicySubcommand(args[1])) {
            case "status" -> policyStatus(sender, args, nationService, policyService);
            case "set" -> policySet(sender, args, nationService, policyService, treasuryService);
            case "clear" -> policyClear(sender, args, nationService, policyService);
            case "tree" -> policyTree(sender, args, nationService, policyService);
            default -> sender.sendMessage(Component.text(msg("command.policy.usage", label), NamedTextColor.YELLOW));
        }
    }

    private void handleTechnology(CommandSender sender, String label, String[] args) {
        TechnologyService technologyService = context.serviceRegistry().find(TechnologyService.class).orElse(null);
        NationService nationService = context.serviceRegistry().find(NationService.class).orElse(null);
        if (technologyService == null || nationService == null) {
            sender.sendMessage(Component.text(msg("command.technology.disabled-service"), NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            technologyStatus(sender, new String[]{"sc", "technology", "status"}, nationService, technologyService);
            return;
        }
        switch (normalizeTechnologySubcommand(args[1])) {
            case "status" -> technologyStatus(sender, args, nationService, technologyService);
            case "unlock" -> technologyUnlock(sender, args, nationService, technologyService);
            case "revoke" -> technologyRevoke(sender, args, nationService, technologyService);
            case "tree" -> technologyTree(sender, args, nationService, technologyService);
            default -> {
                sender.sendMessage(Component.text(msg("command.technology.usage", label), NamedTextColor.YELLOW));
                technologyStatus(sender, new String[]{"sc", "technology", "status"}, nationService, technologyService);
            }
        }
    }

    private void handleWar(CommandSender sender, String label, String[] args) {
        WarService warService = context.serviceRegistry().find(WarService.class).orElse(null);
        NationService nationService = context.serviceRegistry().find(NationService.class).orElse(null);
        if (warService == null || nationService == null) {
            sender.sendMessage(Component.text(msg("command.war.disabled-service"), NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text(msg("command.war.usage", label), NamedTextColor.YELLOW));
            return;
        }
        switch (normalizeWarSubcommand(args[1])) {
            case "status" -> warStatus(sender, args, nationService, warService);
            case "declare" -> warDeclare(sender, args, nationService, warService);
            case "end" -> warEnd(sender, args, nationService, warService);
            default -> sender.sendMessage(Component.text(msg("command.war.usage", label), NamedTextColor.YELLOW));
        }
    }

    private void handleOfficer(CommandSender sender, String label, String[] args) {
        OfficerService officerService = context.serviceRegistry().find(OfficerService.class).orElse(null);
        NationService nationService = context.serviceRegistry().find(NationService.class).orElse(null);
        if (officerService == null || nationService == null) {
            sender.sendMessage(Component.text(msg("officer.disabled-service"), NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text(msg("officer.usage", label), NamedTextColor.YELLOW));
            return;
        }
        switch (normalizeOfficerSubcommand(args[1])) {
            case "status" -> officerStatus(sender, nationService, officerService);
            case "appoint" -> officerAppoint(sender, args, nationService, officerService);
            case "remove" -> officerRemove(sender, args, nationService, officerService);
            case "list" -> officerList(sender, args, nationService, officerService);
            case "info" -> officerInfo(sender, args, nationService, officerService);
            case "gui", "menu" -> officerGui(sender, nationService, officerService);
            default -> sender.sendMessage(Component.text(msg("officer.usage", label), NamedTextColor.YELLOW));
        }
    }

    private void handleEvent(CommandSender sender, String label, String[] args) {
        eventCommands.handle(sender, label, args);
    }

    private void nationCreate(CommandSender sender, String[] args, NationService nationService) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(coloredMsg("command.player-only"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(coloredMsg("command.nation.create-usage"));
            return;
        }
        try {
            String reason = operationReason(commandReason(args, 3), "nation-created");
            Nation nation = nationService.createNation(player.getUniqueId(), player.getName(), args[2]);
            recordNationEvent(
                nation,
                "nation.created",
                player.getName() + " created nation " + nation.name() + " (reason: " + displayReason(reason) + ")",
                buildContext(
                    "actor", player.getName(),
                    "operation", "nation-create",
                    "target", nation.name(),
                    "targetId", nation.id().toString(),
                    "members", String.valueOf(nation.members().size()),
                    "claims", String.valueOf(nationService.claimCount(nation.id())),
                    "reason", reason
                )
            );
            sender.sendMessage(coloredMsg("command.nation.created")
                .append(Component.text(nation.name(), NamedTextColor.WHITE))
                .append(Component.text(" (" + nation.id() + ")", NamedTextColor.DARK_GRAY)));
            emitNationOperationFeedback("nation-created", player);
        } catch (RuntimeException exception) {
            sender.sendMessage(coloredMsg("command.nation.create-failed", exception.getMessage()));
            emitNationOperationFeedback("operation-failed", player);
        }
    }

    private void nationCity(CommandSender sender, String label, String[] args, NationService nationService) {
        if (args.length < 3) {
            sender.sendMessage(Component.text(msg("command.nation.city-usage", label), NamedTextColor.YELLOW));
            return;
        }
        switch (normalizeSimple(args[2])) {
            case "create" -> nationCityCreate(sender, args, nationService);
            case "list" -> nationCityList(sender, nationService);
            default -> sender.sendMessage(Component.text(msg("command.nation.city-usage", label), NamedTextColor.YELLOW));
        }
    }

    private void nationCityCreate(CommandSender sender, String[] args, NationService nationService) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(msg("command.player-only"), NamedTextColor.RED));
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(coloredMsg("command.nation.city-create-usage"));
            return;
        }
        try {
            String reason = operationReason(commandReason(args, 4), "city-state-created");
            Nation cityState = nationService.createCityState(player.getUniqueId(), player.getName(), args[3]);
            recordNationEvent(
                cityState,
                "city_state.created",
                player.getName() + " created city-state " + cityState.name() + " (reason: " + displayReason(reason) + ")",
                buildContext(
                    "actor", player.getName(),
                    "operation", "city-state-create",
                    "target", cityState.name(),
                    "targetId", cityState.id().toString(),
                    "members", String.valueOf(cityState.members().size()),
                    "claims", String.valueOf(nationService.claimCount(cityState.id())),
                    "reason", reason
                )
            );
            sender.sendMessage(coloredMsg("command.nation.city-created")
                .append(Component.text(cityState.name(), NamedTextColor.WHITE))
                .append(Component.text(" (" + cityState.id() + ")", NamedTextColor.DARK_GRAY)));
            emitNationOperationFeedback("city-created", player);
        } catch (RuntimeException exception) {
            sender.sendMessage(Component.text(msg("command.nation.city-create-failed", exception.getMessage()), NamedTextColor.RED));
            emitNationOperationFeedback("operation-failed", player);
        }
    }

    private void nationCityList(CommandSender sender, NationService nationService) {
        Nation parent = null;
        if (sender instanceof Player player) {
            parent = nationService.nationOf(player.getUniqueId()).orElse(null);
        }
        if (parent == null) {
            sender.sendMessage(coloredMsg("command.nation.city-list-no-nation"));
            return;
        }
        Collection<Nation> cityStates = nationService.cityStatesOf(parent.id());
        sender.sendMessage(Component.text(msg("command.nation.city-list-title", parent.name()), NamedTextColor.GOLD));
        if (cityStates.isEmpty()) {
            sender.sendMessage(Component.text(msg("command.nation.city-list-empty"), NamedTextColor.GRAY));
            return;
        }
        for (Nation cityState : cityStates) {
            sender.sendMessage(Component.text("- ", NamedTextColor.GRAY)
                .append(Component.text(cityState.name(), NamedTextColor.WHITE))
                .append(Component.text(msg("command.nation.city-list-detail", cityState.founderId()), NamedTextColor.DARK_GRAY)));
        }
    }

    private void nationInfo(CommandSender sender, String[] args, NationService nationService) {
        Nation targetNation;
        if (args.length < 3) {
            if (sender instanceof Player player) {
                targetNation = nationService.nationOf(player.getUniqueId()).orElse(null);
                if (targetNation == null) {
                    sender.sendMessage(coloredMsg("command.nation.not-in-nation"));
                    return;
                }
            } else {
                sender.sendMessage(coloredMsg("command.nation.info-usage"));
                return;
            }
        } else {
            Optional<Nation> nation = nationService.nationByName(args[2]);
            if (nation.isEmpty()) {
                sender.sendMessage(coloredMsg("command.nation.nation-not-found"));
                return;
            }
            targetNation = nation.get();
        }
        NationResourceDistrictService districtService = context.serviceRegistry().find(NationResourceDistrictService.class).orElse(null);
        NationOperationalOverview overview = nationOperationalOverview(nationService, districtService, targetNation);
        sender.sendMessage(Component.text(targetNation.kind().displayName() + ": ", NamedTextColor.GOLD).append(Component.text(targetNation.name(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("ID: ", NamedTextColor.GRAY).append(Component.text(targetNation.id().toString(), NamedTextColor.WHITE)));
        if (targetNation.parentNationId() != null) {
            nationService.nationById(targetNation.parentNationId()).ifPresent(parent -> sender.sendMessage(Component.text(msg("command.nation.parent-label"), NamedTextColor.GRAY).append(Component.text(parent.name(), NamedTextColor.WHITE))));
        }
        sender.sendMessage(Component.text(msg("command.nation.founder-label"), NamedTextColor.GRAY)
            .append(Component.text(overview.founderName(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text(msg("command.nation.members-label"), NamedTextColor.GRAY)
            .append(Component.text(String.valueOf(overview.memberCount()), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text(msg("command.nation.level-label"), NamedTextColor.GRAY)
            .append(Component.text(String.valueOf(overview.level()), NamedTextColor.WHITE))
            .append(Component.text(msg("command.nation.experience-detail", overview.experience()), NamedTextColor.DARK_GRAY)));
        sender.sendMessage(Component.text(msg("command.nation.level-progress-label"), NamedTextColor.GRAY)
            .append(Component.text(formatNationLevelProgress(overview), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text(msg("command.nation.claims-label"), NamedTextColor.GRAY)
            .append(Component.text(overview.claimCount() + "/" + overview.claimLimit(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text(msg("command.nation.city-states-label"), NamedTextColor.GRAY)
            .append(Component.text(overview.cityStateCount() + "/" + overview.cityStateLimit(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text(msg("command.nation.resource-districts-label"), NamedTextColor.GRAY)
            .append(Component.text(overview.resourceDistrictCount() + "/" + overview.resourceDistrictLimit(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text(msg("command.nation.government-label"), NamedTextColor.GRAY).append(Component.text(targetNation.governmentType().displayName(), NamedTextColor.WHITE)));
    }

    private void nationClaim(CommandSender sender, String[] args, NationService nationService) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(msg("command.player-only"), NamedTextColor.RED));
            return;
        }
        try {
            String reason = operationReason(commandReason(args, 2), "territory-claimed");
            ChunkClaimSelection selection = new ChunkClaimSelection(player.getWorld().getName(), player.getChunk().getX(), player.getChunk().getX(), player.getChunk().getZ(), player.getChunk().getZ());
            ClaimSelectionPreview preview = nationService.previewClaimSelection(player.getUniqueId(), selection);
            if (!preview.canSubmit()) {
                sender.sendMessage(Component.text(preview.overlapCount() > 0 ? msg("command.nation.claim-already-owned") : preview.message(), NamedTextColor.YELLOW));
                sendClaimExplanation(sender, preview);
                emitClaimFeedback("current-failed", player);
                return;
            }
            ClaimSelectionResult result = nationService.claimSelection(player.getUniqueId(), selection);
            Nation nation = nationService.nationOf(player.getUniqueId()).orElse(null);
            if (nation != null) {
                String target = player.getWorld().getName() + ':' + player.getChunk().getX() + ':' + player.getChunk().getZ();
                recordNationEvent(
                    nation,
                    "territory.claimed",
                    player.getName() + " claimed " + target + " (reason: " + displayReason(reason) + ")",
                    buildContext(
                        "actor", player.getName(),
                        "operation", "territory-claim",
                        "target", target,
                        "claims", String.valueOf(nationService.claimCount(nation.id())),
                        "reason", reason
                    )
                );
            }
            sender.sendMessage(coloredMsg("command.nation.claimed-current")
                .append(Component.text(" (" + result.price().toPlainString() + ")", NamedTextColor.GRAY)));
            emitClaimFeedback("current-confirmed", player);
        } catch (RuntimeException exception) {
            sender.sendMessage(Component.text(msg("command.nation.claim-failed", exception.getMessage()), NamedTextColor.RED));
            emitClaimFeedback("current-failed", player);
        }
    }

    private void nationTool(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(msg("command.player-only"), NamedTextColor.RED));
            return;
        }
        ClaimToolService claimToolService = context.serviceRegistry().find(ClaimToolService.class).orElse(null);
        if (claimToolService == null || !context.configuration().claimToolEnabled()) {
            sender.sendMessage(Component.text(msg("territory.tool.disabled"), NamedTextColor.RED));
            return;
        }
        Map<Integer, org.bukkit.inventory.ItemStack> leftovers = player.getInventory().addItem(claimToolService.createTool());
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        sender.sendMessage(Component.text(msg("territory.tool.received"), NamedTextColor.GREEN));
    }

    private void nationToolConfirm(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(msg("command.player-only"), NamedTextColor.RED));
            return;
        }
        ClaimToolService claimToolService = context.serviceRegistry().find(ClaimToolService.class).orElse(null);
        if (claimToolService == null) {
            sender.sendMessage(Component.text(msg("territory.tool.disabled"), NamedTextColor.RED));
            return;
        }
        try {
            ClaimSelectionResult result = claimToolService.confirm(player.getUniqueId());
            recordNationEvent(context.serviceRegistry().require(NationService.class).nationById(result.nationId()).orElse(null),
                "territory.claimed",
                result.selection().world() + ':' + result.selection().minChunkX() + ".." + result.selection().maxChunkX()
                    + ':' + result.selection().minChunkZ() + ".." + result.selection().maxChunkZ() + " claimed by " + player.getName());
            sender.sendMessage(Component.text(msg("territory.tool.confirmed", result.claimedChunks(), result.nationName(), result.price().toPlainString()), NamedTextColor.GREEN));
            emitClaimFeedback("tool-confirmed", player);
        } catch (RuntimeException exception) {
            sender.sendMessage(Component.text(msg("command.nation.tool-confirm-failed", exception.getMessage()), NamedTextColor.RED));
            emitClaimFeedback("tool-failed", player);
        }
    }

    private void nationToolCancel(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(msg("command.player-only"), NamedTextColor.RED));
            return;
        }
        ClaimToolService claimToolService = context.serviceRegistry().find(ClaimToolService.class).orElse(null);
        if (claimToolService == null || !claimToolService.clear(player.getUniqueId())) {
            sender.sendMessage(Component.text(msg("territory.tool.no-selection"), NamedTextColor.YELLOW));
            emitClaimFeedback("tool-failed", player);
            return;
        }
        sender.sendMessage(Component.text(msg("territory.tool.cancelled"), NamedTextColor.GREEN));
        emitClaimFeedback("tool-cancelled", player);
    }

    private void nationUnclaim(CommandSender sender, String[] args, NationService nationService) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(msg("command.player-only"), NamedTextColor.RED));
            return;
        }
        try {
            String reason = operationReason(commandReason(args, 2), "territory-unclaimed");
            boolean removed = nationService.unclaimCurrentChunk(player.getUniqueId(), player.getWorld().getName(), player.getChunk().getX(), player.getChunk().getZ());
            if (removed) {
                Nation nation = nationService.nationOf(player.getUniqueId()).orElse(null);
                if (nation != null) {
                    String target = player.getWorld().getName() + ':' + player.getChunk().getX() + ':' + player.getChunk().getZ();
                    recordNationEvent(
                        nation,
                        "territory.unclaimed",
                        player.getName() + " unclaimed " + target + " (reason: " + displayReason(reason) + ")",
                        buildContext(
                            "actor", player.getName(),
                            "operation", "territory-unclaim",
                            "target", target,
                            "claims", String.valueOf(nationService.claimCount(nation.id())),
                            "reason", reason
                        )
                    );
                }
                sender.sendMessage(coloredMsg("command.nation.unclaimed-current"));
            } else {
                sender.sendMessage(coloredMsg("command.nation.no-current-claim"));
            }
        } catch (RuntimeException exception) {
            sender.sendMessage(Component.text(msg("command.nation.unclaim-failed", exception.getMessage()), NamedTextColor.RED));
        }
    }

    private void nationClaims(CommandSender sender, NationService nationService) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(msg("command.player-only"), NamedTextColor.RED));
            return;
        }
        Nation nation = nationService.nationOf(player.getUniqueId()).orElse(null);
        if (nation == null) {
            sender.sendMessage(coloredMsg("command.nation.not-in-nation"));
            return;
        }
        List<TerritoryClaim> claims = nationService.claimsOf(nation.id()).stream()
            .sorted((left, right) -> left.coordinate().toString().compareTo(right.coordinate().toString()))
            .toList();
        sender.sendMessage(Component.text(msg("command.nation.claims-title", nation.name(), claims.size()), NamedTextColor.GOLD));
        claims.stream().limit(12).forEach(claim -> sender.sendMessage(Component.text("- " + claim.coordinate(), NamedTextColor.GRAY)));
        if (claims.size() > 12) {
            sender.sendMessage(Component.text(msg("command.nation.claims-limit-note"), NamedTextColor.DARK_GRAY));
        }
    }

    private void nationHere(CommandSender sender, NationService nationService) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(msg("command.player-only"), NamedTextColor.RED));
            return;
        }
        TerritoryClaim claim = nationService.claimAt(player.getWorld().getName(), player.getChunk().getX(), player.getChunk().getZ()).orElse(null);
        if (claim == null) {
            sender.sendMessage(coloredMsg("command.nation.no-current-claim"));
            return;
        }
        Nation owner = parseNationId(claim.ownerId()).flatMap(nationService::nationById).orElse(null);
        sender.sendMessage(Component.text(msg("command.nation.here-title"), NamedTextColor.GOLD)
            .append(Component.text(owner == null ? claim.ownerId() : owner.name(), NamedTextColor.WHITE))
            .append(Component.text(" @ " + claim.coordinate(), NamedTextColor.GRAY)));
    }

    private void nationList(CommandSender sender, NationService nationService) {
        sender.sendMessage(Component.text(msg("command.nation.list-title"), NamedTextColor.GOLD));
        for (Nation nation : nationService.nations()) {
            sender.sendMessage(Component.text("- ", NamedTextColor.GRAY)
                .append(Component.text(nation.name(), NamedTextColor.WHITE))
                .append(Component.text(msg(
                    "command.nation.list-detail",
                    nation.kind().displayName(),
                    nationService.levelOf(nation.id()),
                    nation.members().size(),
                    nationService.claimCount(nation.id()),
                    nationService.maxClaimsOf(nation.id()),
                    nation.governmentType().name()
                ), NamedTextColor.DARK_GRAY)));
        }
    }

    private void nationJoin(CommandSender sender, String[] args, NationService nationService, ResolutionService resolutionService) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(msg("command.player-only"), NamedTextColor.RED));
            return;
        }
        if (resolutionService == null) {
            sender.sendMessage(Component.text(msg("command.resolution.disabled-service"), NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(coloredMsg("command.nation.join-usage"));
            return;
        }
        if (nationService.nationOf(player.getUniqueId()).isPresent()) {
            sender.sendMessage(coloredMsg("command.nation.already-in-nation"));
            emitNationOperationFeedback("operation-failed", player);
            return;
        }
        Nation nation = nationService.nationByName(args[2]).orElse(null);
        if (nation == null) {
            sender.sendMessage(coloredMsg("command.nation.nation-not-found"));
            emitNationOperationFeedback("operation-failed", player);
            return;
        }
        String reason = operationReason(commandReason(args, 3), "resolution-proposed");
        Resolution resolution = resolutionService.proposeJoin(nation, player.getUniqueId(), player.getName(), player.getUniqueId(), player.getName());
        recordNationEvent(
            nation,
            "resolution.proposed",
            player.getName() + " proposed " + resolution.action().summary() + " (reason: " + displayReason(reason) + ")",
            resolutionContext(player, nation, resolution, "resolution-propose", reason)
        );
        sender.sendMessage(coloredMsg("command.nation.join-proposed")
            .append(Component.text(resolution.id().toString(), NamedTextColor.WHITE)));
        emitNationOperationFeedback("join-proposed", player);
    }

    private void nationRename(CommandSender sender, String[] args, NationService nationService, ResolutionService resolutionService) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(msg("command.player-only"), NamedTextColor.RED));
            return;
        }
        if (resolutionService == null) {
            sender.sendMessage(Component.text(msg("command.resolution.disabled-service"), NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(coloredMsg("command.nation.rename-usage"));
            return;
        }
        Nation nation = nationService.nationOf(player.getUniqueId()).orElse(null);
        if (nation == null) {
            sender.sendMessage(coloredMsg("command.nation.not-in-nation"));
            emitNationOperationFeedback("operation-failed", player);
            return;
        }
        String reason = operationReason(commandReason(args, 3), "resolution-proposed");
        Resolution resolution = resolutionService.proposeRename(nation, player.getUniqueId(), player.getName(), args[2]);
        recordNationEvent(
            nation,
            "resolution.proposed",
            player.getName() + " proposed " + resolution.action().summary() + " (reason: " + displayReason(reason) + ")",
            resolutionContext(player, nation, resolution, "resolution-propose", reason)
        );
        sender.sendMessage(coloredMsg("command.nation.rename-proposed")
            .append(Component.text(resolution.id().toString(), NamedTextColor.WHITE)));
        emitNationOperationFeedback("rename-proposed", player);
    }

    private void nationGui(CommandSender sender, NationService nationService) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(msg("command.player-only"), NamedTextColor.RED));
            return;
        }

        // 获取玩家所属国家（可选，访客模式允许为空）
        Optional<Nation> nationOpt = nationService.getNationByMember(player.getUniqueId());
        Nation nation = nationOpt.orElse(null);

        // 通过 NationService 实例打开菜单（NationModule 实现了 NationService）
        if (nationService instanceof dev.starcore.starcore.module.nation.NationModule nationModule) {
            nationModule.openManagementMenu(player, nation);
        } else {
            sender.sendMessage(coloredMsg("command.nation.gui-unavailable"));
        }
    }

    private void resolutionList(CommandSender sender, String[] args, NationService nationService, ResolutionService resolutionService) {
        Nation nation;
        if (args.length >= 3) {
            nation = nationService.nationByName(args[2]).orElse(null);
        } else if (sender instanceof Player player) {
            nation = nationService.nationOf(player.getUniqueId()).orElse(null);
        } else {
            sender.sendMessage(coloredMsg("command.resolution.list-usage"));
            return;
        }
        if (nation == null) {
            sender.sendMessage(coloredMsg("command.nation.not-in-nation"));
            return;
        }
        sender.sendMessage(Component.text(msg("command.resolution.list-title"), NamedTextColor.GOLD));
        Collection<Resolution> resolutions = resolutionService.openResolutions(nation);
        if (resolutions.isEmpty()) {
            sender.sendMessage(Component.text(msg("command.resolution.list-empty"), NamedTextColor.GRAY));
            return;
        }
        for (Resolution resolution : resolutions) {
            sender.sendMessage(Component.text("- ", NamedTextColor.GRAY)
                .append(Component.text(resolution.id().toString(), NamedTextColor.WHITE))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text(resolution.action().summary(), NamedTextColor.WHITE))
                .append(Component.text(msg("command.resolution.signatures-detail", resolution.signatures().size()), NamedTextColor.DARK_GRAY)));
        }
    }

    private void resolutionSign(CommandSender sender, String[] args, NationService nationService, ResolutionService resolutionService) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(msg("command.player-only"), NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text(msg("command.resolution.sign-usage"), NamedTextColor.YELLOW));
            return;
        }
        try {
            UUID resolutionId = UUID.fromString(args[2]);
            String reason = operationReason(commandReason(args, 3), "resolution-signed");
            boolean signed = resolutionService.sign(player.getUniqueId(), resolutionId);
            if (signed) {
                Resolution resolution = resolutionService.find(resolutionId).orElse(null);
                Nation nation = resolution == null ? null : nationService.nationById(resolution.nationId()).orElse(null);
                if (nation != null && resolution != null) {
                    recordNationEvent(
                        nation,
                        "resolution.signed",
                        player.getName() + " signed " + resolution.action().summary() + " (reason: " + displayReason(reason) + ")",
                        resolutionContext(player, nation, resolution, "resolution-sign", reason)
                    );
                    if (resolution.state() == ResolutionState.ENACTED) {
                        sender.sendMessage(Component.text(msg("command.resolution.enacted"), NamedTextColor.GREEN));
                    } else {
                        sender.sendMessage(Component.text(msg("command.resolution.signed"), NamedTextColor.GREEN));
                    }
                } else {
                    sender.sendMessage(Component.text(msg("command.resolution.signed"), NamedTextColor.GREEN));
                }
                emitNationOperationFeedback("proposal-signed", player);
            } else {
                sender.sendMessage(Component.text(msg("command.resolution.sign-failed"), NamedTextColor.RED));
                emitNationOperationFeedback("operation-failed", player);
            }
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(Component.text(msg("command.resolution.invalid-uuid"), NamedTextColor.RED));
        }
    }

    private void resolutionCancel(CommandSender sender, String[] args, NationService nationService, ResolutionService resolutionService) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(msg("command.player-only"), NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text(msg("command.resolution.cancel-usage"), NamedTextColor.YELLOW));
            return;
        }
        try {
            UUID resolutionId = UUID.fromString(args[2]);
            boolean cancelled = resolutionService.cancel(resolutionId, player.getUniqueId());
            if (cancelled) {
                sender.sendMessage(Component.text(msg("command.resolution.cancelled"), NamedTextColor.GREEN));
                emitNationOperationFeedback("resolution-cancelled", player);
            } else {
                sender.sendMessage(Component.text(msg("command.resolution.cancel-failed"), NamedTextColor.RED));
                emitNationOperationFeedback("operation-failed", player);
            }
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(Component.text(msg("command.resolution.invalid-uuid"), NamedTextColor.RED));
        }
    }

    private void resolutionInfo(CommandSender sender, String[] args, NationService nationService, ResolutionService resolutionService) {
        if (args.length < 3) {
            sender.sendMessage(Component.text(msg("command.resolution.info-usage"), NamedTextColor.YELLOW));
            return;
        }
        try {
            UUID resolutionId = UUID.fromString(args[2]);
            String details = resolutionService.details(resolutionId);
            sender.sendMessage(Component.text(details, NamedTextColor.WHITE));
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(Component.text(msg("command.resolution.invalid-uuid"), NamedTextColor.RED));
        }
    }

    private void resolutionHistory(CommandSender sender, String[] args, NationService nationService, ResolutionService resolutionService) {
        Nation nation;
        if (args.length >= 3) {
            nation = nationService.nationByName(args[2]).orElse(null);
        } else if (sender instanceof Player player) {
            nation = nationService.nationOf(player.getUniqueId()).orElse(null);
        } else {
            sender.sendMessage(coloredMsg("command.resolution.history-usage"));
            return;
        }
        if (nation == null) {
            sender.sendMessage(coloredMsg("command.nation.not-in-nation"));
            return;
        }
        sender.sendMessage(Component.text(msg("command.resolution.history-title", nation.name()), NamedTextColor.GOLD));
        List<Resolution> history = resolutionService.history(nation);
        if (history.isEmpty()) {
            sender.sendMessage(Component.text(msg("command.resolution.history-empty"), NamedTextColor.GRAY));
            return;
        }
        for (Resolution resolution : history) {
            sender.sendMessage(Component.text("- ", NamedTextColor.GRAY)
                .append(Component.text(resolution.action().summary(), NamedTextColor.WHITE))
                .append(Component.text(" [", NamedTextColor.DARK_GRAY))
                .append(Component.text(resolution.state().name(), NamedTextColor.YELLOW))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text(resolution.proposerName(), NamedTextColor.GRAY))
                .append(Component.text(" (" + resolution.createdAt() + ")", NamedTextColor.DARK_GRAY)));
        }
    }

    private void governmentInfo(CommandSender sender, Player player, NationService nationService, GovernmentService governmentService) {
        Nation nation = nationService.nationOf(player.getUniqueId()).orElse(null);
        if (nation == null) {
            sender.sendMessage(coloredMsg("command.nation.not-in-nation"));
            return;
        }
        GovernmentType governmentType = governmentService.governmentOf(nation);
        sender.sendMessage(Component.text(msg("command.government.current"), NamedTextColor.GOLD)
            .append(Component.text(governmentType.displayName(), NamedTextColor.WHITE))
            .append(Component.text(" (" + governmentType.name() + ")", NamedTextColor.DARK_GRAY)));
    }

    private void governmentPropose(CommandSender sender, String[] args, Player player, NationService nationService, GovernmentService governmentService) {
        if (args.length < 3) {
            sender.sendMessage(Component.text(msg("command.government.propose-usage"), NamedTextColor.YELLOW));
            return;
        }
        Nation nation = nationService.nationOf(player.getUniqueId()).orElse(null);
        if (nation == null) {
            sender.sendMessage(coloredMsg("command.nation.not-in-nation"));
            return;
        }
        ResolutionService resolutionService = context.serviceRegistry().find(ResolutionService.class).orElse(null);
        if (resolutionService == null) {
            sender.sendMessage(Component.text(msg("command.resolution.disabled-service"), NamedTextColor.RED));
            return;
        }
        try {
            String reason = operationReason(commandReason(args, 3), "resolution-proposed");
            GovernmentType type = parseGovernmentType(args[2]).orElseThrow(IllegalArgumentException::new);
            Resolution resolution = resolutionService.proposeGovernmentChange(nation, player.getUniqueId(), player.getName(), type);
            recordNationEvent(
                nation,
                "resolution.proposed",
                player.getName() + " proposed " + resolution.action().summary() + " (reason: " + displayReason(reason) + ")",
                resolutionContext(player, nation, resolution, "resolution-propose", reason)
            );
            sender.sendMessage(Component.text(msg("command.government.proposed"), NamedTextColor.GREEN)
                .append(Component.text(resolution.id().toString(), NamedTextColor.WHITE)));
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(Component.text(msg("command.government.unknown-type"), NamedTextColor.RED));
        } catch (RuntimeException exception) {
            sender.sendMessage(Component.text(msg("command.government.propose-failed", exception.getMessage()), NamedTextColor.RED));
        }
    }

    private void diplomacyStatus(CommandSender sender, String[] args, NationService nationService, DiplomacyService diplomacyService) {
        if (args.length < 4) {
            sender.sendMessage(Component.text(msg("command.diplomacy.status-usage"), NamedTextColor.YELLOW));
            return;
        }
        Nation left = nationService.nationByName(args[2]).orElse(null);
        Nation right = nationService.nationByName(args[3]).orElse(null);
        if (left == null || right == null) {
            sender.sendMessage(Component.text(msg("command.diplomacy.target-not-found"), NamedTextColor.RED));
            return;
        }
        DiplomacyRelation relation = diplomacyService.relationBetween(left.id(), right.id());
        sender.sendMessage(Component.text(msg("command.diplomacy.status-title"), NamedTextColor.GOLD)
            .append(Component.text(left.name() + " <-> " + right.name(), NamedTextColor.WHITE))
            .append(Component.text(" = " + relation.displayName() + " (" + relation.name() + ")", NamedTextColor.GRAY)));
    }

    private void diplomacySet(CommandSender sender, String[] args, NationService nationService, DiplomacyService diplomacyService) {
        if (args.length < 5) {
            sender.sendMessage(Component.text(msg("command.diplomacy.set-usage"), NamedTextColor.YELLOW));
            return;
        }
        Nation left = nationService.nationByName(args[2]).orElse(null);
        Nation right = nationService.nationByName(args[3]).orElse(null);
        if (left == null || right == null) {
            sender.sendMessage(Component.text(msg("command.diplomacy.target-not-found"), NamedTextColor.RED));
            return;
        }
        if (!canSetDiplomacyRelation(sender, left, right)) {
            sender.sendMessage(Component.text(msg("command.diplomacy.set-no-permission"), NamedTextColor.RED));
            emitDiplomacyFeedback("diplomacy-failed", sender);
            return;
        }
        try {
            DiplomacyRelation relation = parseDiplomacyRelation(args[4]).orElseThrow(IllegalArgumentException::new);

            // Admin bypasses resolution system, directly sets relation
            if (sender.hasPermission("starcore.admin")) {
                DiplomacyRelation applied = diplomacyService.setRelation(left.id(), right.id(), relation);
                String reason = operationReason(commandReason(args, 5), "diplomacy-set");
                recordNationEvent(
                    left,
                    "diplomacy.updated",
                    "Relation with " + right.name() + " set to " + applied.name() + " (reason: " + displayReason(reason) + ")",
                    diplomacyContext(sender, left, right, applied, "diplomacy-set", reason)
                );
                recordNationEvent(
                    right,
                    "diplomacy.updated",
                    "Relation with " + left.name() + " set to " + applied.name() + " (reason: " + displayReason(reason) + ")",
                    diplomacyContext(sender, right, left, applied, "diplomacy-set", reason)
                );
                sender.sendMessage(Component.text(msg("command.diplomacy.set-success"), NamedTextColor.GREEN)
                    .append(Component.text(left.name() + " <-> " + right.name(), NamedTextColor.WHITE))
                    .append(Component.text(" = " + applied.displayName(), NamedTextColor.GRAY)));
                emitDiplomacyFeedback("relation-updated", sender);
                return;
            }

            // Non-admin: create resolution for CHANGE_DIPLOMACY_RELATION
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text(msg("command.player-only"), NamedTextColor.RED));
                return;
            }
            ResolutionService resolutionService = context.serviceRegistry().find(ResolutionService.class).orElse(null);
            if (resolutionService == null) {
                sender.sendMessage(Component.text(msg("command.resolution.disabled-service"), NamedTextColor.RED));
                return;
            }
            String reason = operationReason(commandReason(args, 5), "resolution-proposed");
            Resolution resolution = resolutionService.proposeDiplomacyChange(left, player.getUniqueId(), player.getName(), right, relation);
            recordNationEvent(
                left,
                "resolution.proposed",
                player.getName() + " proposed " + resolution.action().summary() + " (reason: " + displayReason(reason) + ")",
                resolutionContext(player, left, resolution, "resolution-propose", reason)
            );
            sender.sendMessage(Component.text(msg("command.diplomacy.proposed"), NamedTextColor.GREEN)
                .append(Component.text(resolution.id().toString(), NamedTextColor.WHITE)));
            emitDiplomacyFeedback("relation-proposed", sender);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(Component.text(msg("command.diplomacy.unknown-relation"), NamedTextColor.RED));
            emitDiplomacyFeedback("diplomacy-failed", sender);
        } catch (RuntimeException exception) {
            sender.sendMessage(Component.text(msg("command.diplomacy.propose-failed", exception.getMessage()), NamedTextColor.RED));
            emitDiplomacyFeedback("diplomacy-failed", sender);
        }
    }

    private boolean canSetDiplomacyRelation(CommandSender sender, Nation left, Nation right) {
        if (sender.hasPermission("starcore.admin")) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        return canManageDiplomacyForNation(playerId, left) || canManageDiplomacyForNation(playerId, right);
    }

    private boolean canManageDiplomacyForNation(UUID playerId, Nation nation) {
        if (playerId == null || nation == null) {
            return false;
        }
        return nation.founderId().equals(playerId)
            || authorizedOfficerRole(nation.id(), playerId, context.configuration().nationDiplomacySetOfficerRoles()).isPresent();
    }

    private void diplomacyList(CommandSender sender, String[] args, NationService nationService, DiplomacyService diplomacyService) {
        Nation nation;
        if (args.length >= 3) {
            nation = nationService.nationByName(args[2]).orElse(null);
        } else if (sender instanceof Player player) {
            nation = nationService.nationOf(player.getUniqueId()).orElse(null);
        } else {
            nation = null;
        }
        if (nation == null) {
            sender.sendMessage(Component.text(msg("command.diplomacy.list-usage"), NamedTextColor.YELLOW));
            return;
        }
        sender.sendMessage(Component.text(msg("command.diplomacy.list-title", nation.name()), NamedTextColor.GOLD));
        List<DiplomacyRelationSnapshot> snapshots = diplomacyService.relationsOf(nation.id()).stream().toList();
        if (snapshots.isEmpty()) {
            sender.sendMessage(Component.text(msg("command.diplomacy.list-empty"), NamedTextColor.GRAY));
            return;
        }
        for (DiplomacyRelationSnapshot snapshot : snapshots) {
            Nation other = nationService.nationById(snapshot.target()).orElse(null);
            String otherName = other == null ? snapshot.target().toString() : other.name();
            sender.sendMessage(Component.text("- ", NamedTextColor.GRAY)
                .append(Component.text(otherName, NamedTextColor.WHITE))
                .append(Component.text(" = " + snapshot.relation().displayName() + " (" + snapshot.relation().name() + ")", NamedTextColor.DARK_GRAY)));
        }
    }

    private void diplomacyPropose(CommandSender sender, String[] args, NationService nationService) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(msg("command.player-only"), NamedTextColor.RED));
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(Component.text(msg("command.diplomacy.propose-usage"), NamedTextColor.YELLOW));
            return;
        }
        ResolutionService resolutionService = context.serviceRegistry().find(ResolutionService.class).orElse(null);
        if (resolutionService == null) {
            sender.sendMessage(Component.text(msg("command.resolution.disabled-service"), NamedTextColor.RED));
            return;
        }
        Nation nation = nationService.nationOf(player.getUniqueId()).orElse(null);
        Nation target = nationService.nationByName(args[2]).orElse(null);
        if (nation == null) {
            sender.sendMessage(coloredMsg("command.nation.not-in-nation"));
            return;
        }
        if (target == null) {
            sender.sendMessage(Component.text(msg("command.diplomacy.target-not-found"), NamedTextColor.RED));
            return;
        }
        if (nation.id().equals(target.id())) {
            sender.sendMessage(Component.text(msg("command.diplomacy.self-target"), NamedTextColor.RED));
            return;
        }
        try {
            DiplomacyRelation relation = parseDiplomacyRelation(args[3]).orElseThrow(IllegalArgumentException::new);
            String reason = operationReason(commandReason(args, 4), "resolution-proposed");
            Resolution resolution = resolutionService.proposeDiplomacyChange(nation, player.getUniqueId(), player.getName(), target, relation);
            recordNationEvent(
                nation,
                "resolution.proposed",
                player.getName() + " proposed " + resolution.action().summary() + " (reason: " + displayReason(reason) + ")",
                resolutionContext(player, nation, resolution, "resolution-propose", reason)
            );
            sender.sendMessage(Component.text(msg("command.diplomacy.proposed"), NamedTextColor.GREEN)
                .append(Component.text(resolution.id().toString(), NamedTextColor.WHITE)));
            emitDiplomacyFeedback("relation-proposed", player);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(Component.text(msg("command.diplomacy.unknown-relation"), NamedTextColor.RED));
            emitDiplomacyFeedback("diplomacy-failed", player);
        } catch (RuntimeException exception) {
            sender.sendMessage(Component.text(msg("command.diplomacy.propose-failed", exception.getMessage()), NamedTextColor.RED));
            emitDiplomacyFeedback("diplomacy-failed", player);
        }
    }

    private void treasuryStatus(CommandSender sender, String[] args, NationService nationService, TreasuryService treasuryService) {
        Nation nation = resolveTreasuryNation(sender, args, nationService);
        if (nation == null) {
            sender.sendMessage(Component.text(msg("command.treasury.status-usage"), NamedTextColor.YELLOW));
            return;
        }
        if (treasuryService == null) {
            sender.sendMessage(Component.text(msg("command.treasury.disabled-service"), NamedTextColor.RED));
            return;
        }
        sender.sendMessage(Component.text(msg("command.treasury.status-title"), NamedTextColor.GOLD)
            .append(Component.text(nation.name(), NamedTextColor.WHITE))
            .append(Component.text(" = " + treasuryService.balance(nation.id()).toPlainString(), NamedTextColor.GRAY)));
    }

    private void treasuryDeposit(CommandSender sender, String[] args, NationService nationService, TreasuryService treasuryService) {
        if (treasuryService == null) {
            sender.sendMessage(Component.text(msg("command.treasury.disabled-service"), NamedTextColor.RED));
            return;
        }
        if (!sender.hasPermission("starcore.admin")) {
            sender.sendMessage(Component.text(msg("command.treasury.no-admin"), NamedTextColor.RED));
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(Component.text(msg("command.treasury.deposit-usage"), NamedTextColor.YELLOW));
            return;
        }
        Nation nation = nationService.nationByName(args[2]).orElse(null);
        if (nation == null) {
            sender.sendMessage(Component.text(msg("command.treasury.nation-not-found"), NamedTextColor.RED));
            return;
        }
        Optional<BigDecimal> amount = parseNonNegativeAmount(args[3]);
        if (amount.isEmpty()) {
            sender.sendMessage(Component.text(msg("command.treasury.amount-non-negative"), NamedTextColor.RED));
            return;
        }
        treasuryService.deposit(nation.id(), amount.get());
        BigDecimal balance = treasuryService.balance(nation.id());
        String reason = commandReason(args, 4);
        recordNationEvent(
            nation,
            "treasury.deposit",
            msg("command.event.message.treasury-deposit", sender.getName(), amount.get().toPlainString(), displayReason(reason)),
            ledgerContext(sender.getName(), amount.get().toPlainString(), null, balance.toPlainString(), reason)
        );
        sender.sendMessage(Component.text(msg("command.treasury.deposited"), NamedTextColor.GREEN)
            .append(Component.text(nation.name(), NamedTextColor.WHITE))
            .append(Component.text(" +" + amount.get().toPlainString() + ", balance=" + balance.toPlainString(), NamedTextColor.GRAY)));
        emitTreasuryFeedback("treasury-deposited", sender, amount.get());
    }

    private void treasuryWithdraw(CommandSender sender, String[] args, NationService nationService, TreasuryService treasuryService) {
        if (treasuryService == null) {
            sender.sendMessage(Component.text(msg("command.treasury.disabled-service"), NamedTextColor.RED));
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(Component.text(msg("command.treasury.withdraw-usage"), NamedTextColor.YELLOW));
            return;
        }
        Nation nation = nationService.nationByName(args[2]).orElse(null);
        if (nation == null) {
            sender.sendMessage(Component.text(msg("command.treasury.nation-not-found"), NamedTextColor.RED));
            return;
        }
        if (!canWithdrawFromTreasury(sender, nation)) {
            sender.sendMessage(Component.text(msg("command.treasury.withdraw-no-permission"), NamedTextColor.RED));
            return;
        }
        Optional<BigDecimal> amount = parseAmount(args[3]);
        if (amount.isEmpty()) {
            sender.sendMessage(Component.text(msg("command.amount-positive"), NamedTextColor.RED));
            return;
        }
        if (!treasuryService.withdraw(nation.id(), amount.get())) {
            sender.sendMessage(Component.text(msg("command.treasury.insufficient"), NamedTextColor.RED));
            return;
        }
        BigDecimal balance = treasuryService.balance(nation.id());
        String reason = commandReason(args, 4);
        recordNationEvent(
            nation,
            "treasury.withdraw",
            msg("command.event.message.treasury-withdraw", sender.getName(), amount.get().toPlainString(), displayReason(reason)),
            ledgerContext(sender.getName(), amount.get().toPlainString(), null, balance.toPlainString(), reason)
        );
        sender.sendMessage(Component.text(msg("command.treasury.withdrawn"), NamedTextColor.GREEN)
            .append(Component.text(nation.name(), NamedTextColor.WHITE))
            .append(Component.text(" -" + amount.get().toPlainString() + ", balance=" + balance.toPlainString(), NamedTextColor.GRAY)));
        emitTreasuryFeedback("treasury-withdrawn", sender, amount.get());
    }

    private boolean canWithdrawFromTreasury(CommandSender sender, Nation nation) {
        if (sender.hasPermission("starcore.admin")) {
            return true;
        }
        if (!(sender instanceof Player player) || nation == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        return nation.founderId().equals(playerId)
            || authorizedTreasuryWithdrawOfficerRole(nation.id(), playerId).isPresent();
    }

    private Optional<String> authorizedTreasuryWithdrawOfficerRole(NationId nationId, UUID playerId) {
        return authorizedOfficerRole(nationId, playerId, context.configuration().nationTreasuryWithdrawOfficerRoles());
    }

    private Optional<String> authorizedOfficerRole(NationId nationId, UUID playerId, List<String> allowedRoles) {
        OfficerService officerService = context.serviceRegistry().find(OfficerService.class).orElse(null);
        if (officerService == null || nationId == null || playerId == null) {
            return Optional.empty();
        }
        return (allowedRoles == null ? List.<String>of() : allowedRoles).stream()
            .map(role -> role == null ? "" : role.trim().toLowerCase(Locale.ROOT).replace('_', '-'))
            .filter(role -> !role.isBlank())
            .filter(role -> officerService.officer(nationId, role)
                .map(appointment -> playerId.equals(appointment.playerId()))
                .orElse(false))
            .findFirst();
    }

    private void treasuryReward(CommandSender sender, String[] args, NationService nationService, TreasuryService treasuryService) {
        if (treasuryService == null) {
            sender.sendMessage(Component.text(msg("command.treasury.disabled-service"), NamedTextColor.RED));
            return;
        }
        if (!sender.hasPermission("starcore.admin")) {
            sender.sendMessage(Component.text(msg("command.treasury.no-admin"), NamedTextColor.RED));
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(Component.text(msg("command.treasury.reward-usage"), NamedTextColor.YELLOW));
            return;
        }
        Nation nation = nationService.nationByName(args[2]).orElse(null);
        if (nation == null) {
            sender.sendMessage(Component.text(msg("command.treasury.nation-not-found"), NamedTextColor.RED));
            return;
        }
        Optional<BigDecimal> amount = parseAmount(args[3]);
        if (amount.isEmpty()) {
            sender.sendMessage(Component.text(msg("command.amount-positive"), NamedTextColor.RED));
            return;
        }
        String reason = commandReason(args, 4);
        TreasuryRewardService rewardService = context.serviceRegistry().find(TreasuryRewardService.class).orElse(null);
        BigDecimal balance;
        if (rewardService != null) {
            TreasuryRewardResult result = rewardService.reward(nation.id(), amount.get(), sender.getName(), reason);
            balance = result.balance();
        } else {
            treasuryService.deposit(nation.id(), amount.get());
            balance = treasuryService.balance(nation.id());
            recordNationEvent(
                nation,
                "treasury.reward",
                msg("command.event.message.treasury-reward", sender.getName(), amount.get().toPlainString(), displayReason(reason)),
                ledgerContext(sender.getName(), amount.get().toPlainString(), null, balance.toPlainString(), reason)
            );
        }
        sender.sendMessage(Component.text(msg("command.treasury.rewarded"), NamedTextColor.GREEN)
            .append(Component.text(nation.name(), NamedTextColor.WHITE))
            .append(Component.text(" +" + amount.get().toPlainString() + ", balance=" + balance.toPlainString(), NamedTextColor.GRAY)));
        emitTreasuryFeedback("treasury-rewarded", sender, amount.get());
    }

    private void treasuryIncome(CommandSender sender, String[] args, NationService nationService, TreasuryService treasuryService) {
        if (treasuryService == null) {
            sender.sendMessage(Component.text(msg("command.treasury.disabled-service"), NamedTextColor.RED));
            return;
        }
        if (!sender.hasPermission("starcore.admin")) {
            sender.sendMessage(Component.text(msg("command.treasury.no-admin"), NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text(msg("command.treasury.income-usage"), NamedTextColor.YELLOW));
            return;
        }
        String reason = commandReason(args, 3);
        if (isAllToken(args[2])) {
            BigDecimal total = BigDecimal.ZERO;
            int settled = 0;
            for (Nation nation : nationService.nations()) {
                TreasuryIncomeSettlement settlement = settleTreasuryIncome(sender, nation, reason, nationService, treasuryService);
                if (settlement.amount().signum() > 0) {
                    total = total.add(settlement.amount());
                    settled++;
                }
            }
            if (settled == 0) {
                sender.sendMessage(Component.text(msg("command.treasury.income-zero"), NamedTextColor.YELLOW));
                return;
            }
            sender.sendMessage(Component.text(msg("command.treasury.income-all-settled", settled, total.setScale(2, java.math.RoundingMode.DOWN).toPlainString()), NamedTextColor.GREEN));
            emitTreasuryFeedback("treasury-income-settled", sender, total);
            return;
        }
        Nation nation = nationService.nationByName(args[2]).orElse(null);
        if (nation == null) {
            sender.sendMessage(Component.text(msg("command.treasury.nation-not-found"), NamedTextColor.RED));
            return;
        }
        TreasuryIncomeSettlement settlement = settleTreasuryIncome(sender, nation, reason, nationService, treasuryService);
        if (settlement.amount().signum() <= 0) {
            sender.sendMessage(Component.text(msg("command.treasury.income-zero"), NamedTextColor.YELLOW));
            return;
        }
        sender.sendMessage(Component.text(msg("command.treasury.income-settled"), NamedTextColor.GREEN)
            .append(Component.text(nation.name(), NamedTextColor.WHITE))
            .append(Component.text(msg("command.treasury.income-detail", settlement.amount().toPlainString(), settlement.base().toPlainString(), settlement.memberIncome().toPlainString(), settlement.claimIncome().toPlainString(), settlement.balance().toPlainString()), NamedTextColor.GRAY)));
        emitTreasuryFeedback("treasury-income-settled", sender, settlement.amount());
    }

    private TreasuryIncomeSettlement settleTreasuryIncome(CommandSender sender, Nation nation, String reason, NationService nationService, TreasuryService treasuryService) {
        if (treasuryService == null) {
            return new TreasuryIncomeSettlement(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        int members = nation.members().size();
        int claims = Math.max(0, nationService.claimCount(nation.id()));
        BigDecimal base = dailyIncomeBaseAmount();
        BigDecimal memberIncome = dailyIncomePerMember().multiply(BigDecimal.valueOf(members));
        BigDecimal claimIncome = dailyIncomePerClaim().multiply(BigDecimal.valueOf(claims));
        BigDecimal amount = base.add(memberIncome).add(claimIncome).setScale(2, java.math.RoundingMode.DOWN);
        if (amount.signum() <= 0) {
            return new TreasuryIncomeSettlement(amount, base, memberIncome, claimIncome, treasuryService.balance(nation.id()));
        }
        treasuryService.deposit(nation.id(), amount);
        BigDecimal balance = treasuryService.balance(nation.id());
        recordNationEvent(
            nation,
            "treasury.income",
            msg("command.event.message.treasury-income", sender.getName(), amount.toPlainString(), members, claims, displayReason(reason)),
            dailyIncomeLedgerContext(sender.getName(), amount.toPlainString(), balance.toPlainString(), members, claims, reason)
        );
        return new TreasuryIncomeSettlement(amount, base, memberIncome, claimIncome, balance);
    }

    private boolean isAllToken(String value) {
        return switch (normalizeToken(value)) {
            case "all", "*", "全部", "所有" -> true;
            default -> false;
        };
    }

    private record TreasuryIncomeSettlement(BigDecimal amount, BigDecimal base, BigDecimal memberIncome, BigDecimal claimIncome, BigDecimal balance) {
    }

    private void treasuryTax(CommandSender sender, String[] args, NationService nationService, TreasuryService treasuryService) {
        if (treasuryService == null) {
            sender.sendMessage(Component.text(msg("command.treasury.disabled-service"), NamedTextColor.RED));
            return;
        }
        if (!sender.hasPermission("starcore.admin")) {
            sender.sendMessage(Component.text(msg("command.treasury.no-admin"), NamedTextColor.RED));
            return;
        }
        if (!taxEnabled()) {
            sender.sendMessage(Component.text(msg("command.treasury.tax-disabled"), NamedTextColor.YELLOW));
            return;
        }
        if (context.economyService() == null) {
            sender.sendMessage(Component.text(msg("command.economy.disabled-service"), NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text(msg("command.treasury.tax-usage"), NamedTextColor.YELLOW));
            return;
        }
        String reason = commandReason(args, 3);
        if (isAllToken(args[2])) {
            BigDecimal total = BigDecimal.ZERO;
            int taxedMembers = 0;
            int skippedMembers = 0;
            int settledNations = 0;
            for (Nation nation : nationService.nations()) {
                TreasuryTaxSettlement settlement = settleTreasuryTax(sender, nation, reason, treasuryService);
                if (settlement.amount().signum() > 0) {
                    total = total.add(settlement.amount());
                    taxedMembers += settlement.taxedMembers();
                    skippedMembers += settlement.skippedMembers();
                    settledNations++;
                }
            }
            if (settledNations == 0) {
                sender.sendMessage(Component.text(msg("command.treasury.tax-zero"), NamedTextColor.YELLOW));
                return;
            }
            sender.sendMessage(Component.text(msg("command.treasury.tax-all-settled", settledNations, taxedMembers, skippedMembers, total.setScale(2, java.math.RoundingMode.DOWN).toPlainString()), NamedTextColor.GREEN));
            emitTreasuryFeedback("treasury-tax-settled", sender, total);
            return;
        }
        Nation nation = nationService.nationByName(args[2]).orElse(null);
        if (nation == null) {
            sender.sendMessage(Component.text(msg("command.treasury.nation-not-found"), NamedTextColor.RED));
            return;
        }
        TreasuryTaxSettlement settlement = settleTreasuryTax(sender, nation, reason, treasuryService);
        if (settlement.amount().signum() <= 0) {
            sender.sendMessage(Component.text(msg("command.treasury.tax-zero"), NamedTextColor.YELLOW));
            return;
        }
        sender.sendMessage(Component.text(msg("command.treasury.tax-settled"), NamedTextColor.GREEN)
            .append(Component.text(nation.name(), NamedTextColor.WHITE))
            .append(Component.text(msg("command.treasury.tax-detail", settlement.amount().toPlainString(), settlement.taxedMembers(), settlement.skippedMembers(), settlement.balance().toPlainString()), NamedTextColor.GRAY)));
        emitTreasuryFeedback("treasury-tax-settled", sender, settlement.amount());
    }

    private TreasuryTaxSettlement settleTreasuryTax(CommandSender sender, Nation nation, String reason, TreasuryService treasuryService) {
        if (treasuryService == null) {
            return new TreasuryTaxSettlement(BigDecimal.ZERO, 0, 0, BigDecimal.ZERO);
        }
        BigDecimal total = BigDecimal.ZERO;
        int taxedMembers = 0;
        int skippedMembers = 0;
        InternalEconomyService economyService = context.economyService();
        for (var member : nation.members()) {
            BigDecimal memberTax = taxAmountFor(economyService.balance(member.playerId()));
            if (memberTax.signum() <= 0) {
                skippedMembers++;
                continue;
            }
            if (!economyService.withdraw(member.playerId(), memberTax)) {
                skippedMembers++;
                continue;
            }
            total = total.add(memberTax);
            taxedMembers++;
        }
        total = total.setScale(2, java.math.RoundingMode.DOWN);
        BigDecimal balance = treasuryService.balance(nation.id());
        if (total.signum() <= 0) {
            return new TreasuryTaxSettlement(total, taxedMembers, skippedMembers, balance);
        }
        treasuryService.deposit(nation.id(), total);
        balance = treasuryService.balance(nation.id());
        recordNationEvent(
            nation,
            "treasury.tax",
            msg("command.event.message.treasury-tax", sender.getName(), total.toPlainString(), taxedMembers, skippedMembers, displayReason(reason)),
            taxLedgerContext(sender.getName(), total.toPlainString(), balance.toPlainString(), taxedMembers, skippedMembers, reason)
        );
        return new TreasuryTaxSettlement(total, taxedMembers, skippedMembers, balance);
    }

    private BigDecimal taxAmountFor(BigDecimal balance) {
        BigDecimal safeBalance = balance == null ? BigDecimal.ZERO : balance.setScale(2, java.math.RoundingMode.DOWN);
        BigDecimal protectedBalance = taxMinimumBalance();
        BigDecimal taxableBalance = safeBalance.subtract(protectedBalance).max(BigDecimal.ZERO).setScale(2, java.math.RoundingMode.DOWN);
        if (taxableBalance.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, java.math.RoundingMode.DOWN);
        }
        BigDecimal fixed = taxFixedAmount();
        BigDecimal percent = taxBalancePercent();
        BigDecimal percentageTax = taxableBalance.multiply(percent).divide(new BigDecimal("100.00"), 2, java.math.RoundingMode.DOWN);
        BigDecimal requested = fixed.add(percentageTax).setScale(2, java.math.RoundingMode.DOWN);
        if (requested.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, java.math.RoundingMode.DOWN);
        }
        if (requested.compareTo(taxableBalance) <= 0) {
            return requested;
        }
        return taxSkipInsufficientMembers() ? BigDecimal.ZERO.setScale(2, java.math.RoundingMode.DOWN) : taxableBalance;
    }

    private String taxLedgerContext(String actor, String amount, String balance, int taxedMembers, int skippedMembers, String reason) {
        List<String> entries = new ArrayList<>();
        addContextEntry(entries, "actor", actor);
        addContextEntry(entries, "amount", amount);
        addContextEntry(entries, "balance", balance);
        addContextEntry(entries, "taxedMembers", String.valueOf(taxedMembers));
        addContextEntry(entries, "skippedMembers", String.valueOf(skippedMembers));
        addContextEntry(entries, "fixed", taxFixedAmount().toPlainString());
        addContextEntry(entries, "percent", taxBalancePercent().toPlainString());
        addContextEntry(entries, "minimumBalance", taxMinimumBalance().toPlainString());
        addContextEntry(entries, "reason", reason);
        return String.join(";", entries);
    }

    private record TreasuryTaxSettlement(BigDecimal amount, int taxedMembers, int skippedMembers, BigDecimal balance) {
    }

    private Nation resolveTreasuryNation(CommandSender sender, String[] args, NationService nationService) {
        if (args.length >= 3) {
            return nationService.nationByName(args[2]).orElse(null);
        }
        if (sender instanceof Player player) {
            return nationService.nationOf(player.getUniqueId()).orElse(null);
        }
        return null;
    }

    private Optional<BigDecimal> parseAmount(String raw) {
        try {
            BigDecimal amount = new BigDecimal(raw);
            return amount.signum() > 0 ? Optional.of(amount) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private void economyBalance(CommandSender sender, String[] args, InternalEconomyService economyService) {
        UUID accountId;
        String accountName;
        if (args.length >= 3) {
            OfflinePlayer target = resolveOfflinePlayer(args[2]);
            accountId = target.getUniqueId();
            accountName = target.getName() == null ? args[2] : target.getName();
        } else if (sender instanceof Player player) {
            accountId = player.getUniqueId();
            accountName = player.getName();
        } else {
            sender.sendMessage(Component.text(msg("command.economy.balance-usage"), NamedTextColor.YELLOW));
            return;
        }
        sender.sendMessage(Component.text(msg("command.economy.balance-label"), NamedTextColor.GOLD)
            .append(Component.text(accountName, NamedTextColor.WHITE))
            .append(Component.text(msg("command.economy.balance-detail", economyService.balance(accountId).toPlainString()), NamedTextColor.GRAY)));
    }

    private void economyGive(CommandSender sender, String[] args, InternalEconomyService economyService) {
        if (!requireEconomyAdmin(sender)) {
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(Component.text(msg("command.economy.give-usage"), NamedTextColor.YELLOW));
            return;
        }
        OfflinePlayer target = resolveOfflinePlayer(args[2]);
        Optional<BigDecimal> amount = parseAmount(args[3]);
        if (amount.isEmpty()) {
            sender.sendMessage(Component.text(msg("command.amount-positive"), NamedTextColor.RED));
            return;
        }
        economyService.deposit(target.getUniqueId(), amount.get());
        sender.sendMessage(Component.text(msg("command.economy.given"), NamedTextColor.GREEN)
            .append(Component.text(displayName(target, args[2]), NamedTextColor.WHITE))
            .append(Component.text(msg("command.economy.delta-detail", "+", amount.get().toPlainString(), economyService.balance(target.getUniqueId()).toPlainString()), NamedTextColor.GRAY)));
    }

    private void economyTake(CommandSender sender, String[] args, InternalEconomyService economyService) {
        if (!requireEconomyAdmin(sender)) {
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(Component.text(msg("command.economy.take-usage"), NamedTextColor.YELLOW));
            return;
        }
        OfflinePlayer target = resolveOfflinePlayer(args[2]);
        Optional<BigDecimal> amount = parseAmount(args[3]);
        if (amount.isEmpty()) {
            sender.sendMessage(Component.text(msg("command.amount-positive"), NamedTextColor.RED));
            return;
        }
        if (!economyService.withdraw(target.getUniqueId(), amount.get())) {
            sender.sendMessage(Component.text(msg("command.economy.insufficient"), NamedTextColor.RED));
            return;
        }
        sender.sendMessage(Component.text(msg("command.economy.taken"), NamedTextColor.GREEN)
            .append(Component.text(displayName(target, args[2]), NamedTextColor.WHITE))
            .append(Component.text(msg("command.economy.delta-detail", "-", amount.get().toPlainString(), economyService.balance(target.getUniqueId()).toPlainString()), NamedTextColor.GRAY)));
    }

    private void economySet(CommandSender sender, String[] args, InternalEconomyService economyService) {
        if (!requireEconomyAdmin(sender)) {
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(Component.text(msg("command.economy.set-usage"), NamedTextColor.YELLOW));
            return;
        }
        OfflinePlayer target = resolveOfflinePlayer(args[2]);
        Optional<BigDecimal> amount = parseAmount(args[3]);
        if (amount.isEmpty()) {
            sender.sendMessage(Component.text(msg("command.amount-positive"), NamedTextColor.RED));
            return;
        }
        economyService.setBalance(target.getUniqueId(), amount.get());
        sender.sendMessage(Component.text(msg("command.economy.set"), NamedTextColor.GREEN)
            .append(Component.text(displayName(target, args[2]), NamedTextColor.WHITE))
            .append(Component.text(msg("command.economy.balance-detail", economyService.balance(target.getUniqueId()).toPlainString()), NamedTextColor.GRAY)));
    }

    private boolean requireEconomyAdmin(CommandSender sender) {
        if (sender.hasPermission("starcore.admin")) {
            return true;
        }
        sender.sendMessage(Component.text(msg("command.economy.no-admin"), NamedTextColor.RED));
        return false;
    }

    private OfflinePlayer resolveOfflinePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        return online == null ? Bukkit.getOfflinePlayer(name) : online;
    }

    private String displayName(OfflinePlayer player, String fallback) {
        return player.getName() == null ? fallback : player.getName();
    }

    private Optional<BigDecimal> parseNonNegativeAmount(String raw) {
        try {
            BigDecimal amount = new BigDecimal(raw);
            return amount.signum() >= 0 ? Optional.of(amount) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private void policyStatus(CommandSender sender, String[] args, NationService nationService, PolicyService policyService) {
        Nation nation = resolvePolicyNation(sender, args, nationService);
        if (nation == null) {
            sender.sendMessage(Component.text(msg("command.policy.status-usage"), NamedTextColor.YELLOW));
            return;
        }
        String active = policyService.activePolicyDefinition(nation.id())
            .map(definition -> definition.key() + " (" + definition.category().displayName() + " / " + definition.displayName() + ")")
            .orElse(msg("command.policy.none"));
        sender.sendMessage(Component.text(msg("command.policy.status-title"), NamedTextColor.GOLD)
            .append(Component.text(nation.name(), NamedTextColor.WHITE))
            .append(Component.text(" = " + active, NamedTextColor.GRAY)));
        sender.sendMessage(Component.text(msg("command.policy.available-label"), NamedTextColor.GRAY)
            .append(Component.text(formatPolicyDefinitions(policyService), NamedTextColor.WHITE)));
        Collection<String> unlocked = policyService.unlockedPolicies(nation.id());
        sender.sendMessage(Component.text(msg("command.policy.unlocked-label"), NamedTextColor.GRAY)
            .append(Component.text(unlocked.isEmpty() ? msg("command.policy.none") : String.join(", ", unlocked), NamedTextColor.WHITE)));
        Collection<PolicyEffect> effects = policyService.activePolicyEffects(nation.id());
        if (effects.isEmpty()) {
            sender.sendMessage(Component.text(msg("command.policy.effects-none"), NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text(msg("command.policy.effects-title"), NamedTextColor.GRAY));
        for (PolicyEffect effect : effects) {
            sender.sendMessage(Component.text("- ", NamedTextColor.GRAY)
                .append(Component.text(formatPolicyEffect(effect), NamedTextColor.WHITE)));
        }
    }

    private void policyTree(CommandSender sender, String[] args, NationService nationService, PolicyService policyService) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(msg("command.player-only"), NamedTextColor.RED));
            return;
        }
        Nation nation = resolvePolicyNation(sender, args, nationService);
        if (nation == null) {
            sender.sendMessage(Component.text(msg("command.policy.status-usage"), NamedTextColor.YELLOW));
            return;
        }
        List<PolicyDefinition> definitions = policyService.policyDefinitions().stream()
            .sorted((left, right) -> {
                int categoryCompare = Integer.compare(left.category().ordinal(), right.category().ordinal());
                return categoryCompare != 0 ? categoryCompare : left.key().compareTo(right.key());
            })
            .toList();
        Set<String> unlocked = Set.copyOf(policyService.unlockedPolicies(nation.id()));
        String activeKey = policyService.activePolicy(nation.id()).orElse("");
        openPolicyTreeMenu(player, nation, definitions, unlocked, activeKey);
    }

    private void openPolicyTreeMenu(Player player, Nation nation, List<PolicyDefinition> definitions, Set<String> unlocked, String activeKey) {
        String title = msg("command.policy.tree-title", nation.name());
        try {
            InventoryView view = MenuType.GENERIC_9X6.builder()
                .title(Component.text(title))
                .build(player);
            populatePolicyTree(view.getTopInventory(), definitions, unlocked, activeKey);
            view.open();
        } catch (RuntimeException | LinkageError exception) {
            Inventory inventory = Bukkit.createInventory(null, 54, title);
            populatePolicyTree(inventory, definitions, unlocked, activeKey);
            player.openInventory(inventory);
        }
    }

    private void populatePolicyTree(Inventory inventory, List<PolicyDefinition> definitions, Set<String> unlocked, String activeKey) {
        inventory.setItem(4, menuItem(Material.NETHER_STAR, msg("command.policy.tree-overview-name"), List.of(
            msg("command.policy.tree-overview-lore", definitions.size())
        ), NamedTextColor.GOLD));
        if (definitions.isEmpty()) {
            inventory.setItem(22, menuItem(Material.BARRIER, msg("command.policy.tree-empty"), List.of(), NamedTextColor.RED));
            return;
        }
        int slot = 10;
        for (PolicyDefinition definition : definitions) {
            if (slot >= inventory.getSize()) {
                break;
            }
            inventory.setItem(slot, policyTreeItem(definition, unlocked, activeKey));
            slot++;
            if (slot % 9 == 8) {
                slot += 2;
            }
        }
    }

    private ItemStack policyTreeItem(PolicyDefinition definition, Set<String> unlocked, String activeKey) {
        boolean active = definition.key().equals(activeKey);
        boolean unlockedPolicy = unlocked.contains(definition.key());
        boolean prerequisitesMet = unlocked.containsAll(definition.prerequisiteKeys());
        Material material = active ? Material.EMERALD_BLOCK
            : unlockedPolicy ? Material.LIME_CONCRETE
            : prerequisitesMet ? policyCategoryMaterial(definition.category())
            : Material.GRAY_DYE;
        String state = active ? msg("command.policy.tree-state-active")
            : unlockedPolicy ? msg("command.policy.tree-state-unlocked")
            : prerequisitesMet ? msg("command.policy.tree-state-available")
            : msg("command.policy.tree-state-locked");
        List<String> lore = new ArrayList<>();
        lore.add(msg("command.policy.tree-key", definition.key()));
        lore.add(state);
        lore.add(msg("command.policy.tree-category", definition.category().displayName()));
        lore.add(msg("command.policy.tree-cost", definition.treasuryCost().toPlainString()));
        lore.add(msg("command.policy.tree-prerequisites", definition.prerequisiteKeys().isEmpty() ? msg("command.policy.none") : String.join(", ", definition.prerequisiteKeys())));
        lore.add(msg("command.policy.tree-duration", definition.durationSeconds(), definition.cooldownSeconds()));
        definition.effects().stream()
            .limit(3)
            .map(this::formatPolicyEffect)
            .map(effect -> msg("command.policy.tree-effect", effect))
            .forEach(lore::add);
        if (definition.effects().size() > 3) {
            lore.add(msg("command.policy.tree-more-effects", definition.effects().size() - 3));
        }
        lore.add(msg("command.policy.tree-command", definition.key()));
        return menuItem(material, definition.displayName(), lore, active ? NamedTextColor.GREEN : NamedTextColor.WHITE);
    }

    private Material policyCategoryMaterial(PolicyCategory category) {
        return switch (category) {
            // 经济类
            case FISCAL, MONETARY, TRADE, INDUSTRY, TAXATION, ECONOMY -> Material.GOLD_INGOT;
            // 军事类
            case DEFENSE, INTELLIGENCE, RECRUITMENT, ARMS, MILITARY -> Material.IRON_SWORD;
            // 内政类
            case ADMINISTRATION, EDUCATION, HEALTHCARE, HOUSING, SOCIAL_WELFARE, LABOR, SOCIAL, POLITICAL -> Material.BOOK;
            // 外交类
            case FOREIGN_POLICY, IMMIGRATION, CULTURAL_EXCHANGE -> Material.WRITABLE_BOOK;
            // 资源/环境类
            case RESOURCE_MANAGEMENT, ENVIRONMENTAL -> Material.DIAMOND;
            // 宗教/文化类
            case RELIGION, CULTURE, PROPAGANDA -> Material.NETHER_STAR;
        };
    }

    private ItemStack menuItem(Material material, String name, List<String> lore, NamedTextColor nameColor) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, nameColor));
            meta.lore(lore.stream()
                .map(line -> Component.text(line, NamedTextColor.GRAY))
                .toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatPolicyEffect(PolicyEffect effect) {
        return msg(
            "command.policy.effect-format",
            effect.key(),
            effect.scope().name().toLowerCase(Locale.ROOT),
            effect.modifier(),
            effect.description()
        );
    }

    private String formatPolicyDefinitions(PolicyService policyService) {
        return policyService.policyDefinitions().stream()
            .map(this::formatPolicyDefinition)
            .toList()
            .stream()
            .reduce((left, right) -> left + ", " + right)
            .orElse(msg("command.policy.none"));
    }

    private String formatPolicyDefinition(PolicyDefinition definition) {
        String prerequisite = definition.prerequisiteKeys().isEmpty() ? msg("command.policy.none") : String.join("+", definition.prerequisiteKeys());
        return msg(
            "command.policy.definition-format",
            definition.key(),
            definition.category().displayName(),
            definition.treasuryCost().toPlainString(),
            prerequisite
        );
    }

    private void policySet(CommandSender sender, String[] args, NationService nationService, PolicyService policyService, TreasuryService treasuryService) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(msg("command.player-only"), NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text(msg("command.policy.set-usage"), NamedTextColor.YELLOW));
            emitStrategyFeedback("strategy-failed", player);
            return;
        }
        Nation nation = nationService.nationOf(player.getUniqueId()).orElse(null);
        if (nation == null) {
            sender.sendMessage(coloredMsg("command.nation.not-in-nation"));
            emitStrategyFeedback("strategy-failed", player);
            return;
        }
        if (!canSetPolicy(sender, nation)) {
            sender.sendMessage(Component.text(msg("command.policy.set-no-permission"), NamedTextColor.RED));
            emitStrategyFeedback("strategy-failed", player);
            return;
        }
        PolicyActivationResult result = policyService.activatePolicy(nation.id(), args[2], treasuryService);
        if (!result.successful()) {
            sender.sendMessage(Component.text(msg("command.policy.set-failed", result.message()), NamedTextColor.RED));
            sender.sendMessage(Component.text(msg("command.policy.available-inline", formatPolicyDefinitions(policyService)), NamedTextColor.GRAY));
            emitStrategyFeedback("strategy-failed", player);
            return;
        }
        PolicyDefinition activated = result.definition().orElseThrow();
        String reason = operationReason(commandReason(args, 3), "policy-set");
        recordNationEvent(
            nation,
            "policy.set",
            player.getName() + " activated policy " + activated.key() + " (reason: " + displayReason(reason) + ")",
            strategyContext(player.getName(), nation, "policy-set", "policy", activated.key(), reason)
        );
        sender.sendMessage(Component.text(msg("command.policy.set-success"), NamedTextColor.GREEN)
            .append(Component.text(nation.name(), NamedTextColor.WHITE))
            .append(Component.text(" -> " + activated.key() + " (cost=" + activated.treasuryCost().toPlainString() + ")", NamedTextColor.GRAY)));
        emitStrategyFeedback("policy-activated", player);
    }

    private void policyClear(CommandSender sender, String[] args, NationService nationService, PolicyService policyService) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(msg("command.player-only"), NamedTextColor.RED));
            return;
        }
        Nation nation = nationService.nationOf(player.getUniqueId()).orElse(null);
        if (nation == null) {
            sender.sendMessage(coloredMsg("command.nation.not-in-nation"));
            emitStrategyFeedback("strategy-failed", player);
            return;
        }
        if (!canClearPolicy(sender, nation)) {
            sender.sendMessage(Component.text(msg("command.policy.clear-no-permission"), NamedTextColor.RED));
            emitStrategyFeedback("strategy-failed", player);
            return;
        }
        String clearedPolicy = policyService.activePolicy(nation.id()).orElse("");
        policyService.clearActivePolicy(nation.id());
        String reason = operationReason(commandReason(args, 2), "policy-cleared");
        recordNationEvent(
            nation,
            "policy.cleared",
            player.getName() + " cleared active policy" + (clearedPolicy.isBlank() ? "" : " " + clearedPolicy)
                + " (reason: " + displayReason(reason) + ")",
            strategyContext(player.getName(), nation, "policy-clear", "policy", clearedPolicy, reason)
        );
        sender.sendMessage(Component.text(msg("command.policy.clear-success"), NamedTextColor.GREEN)
            .append(Component.text(nation.name(), NamedTextColor.WHITE)));
        emitStrategyFeedback("policy-cleared", player);
    }

    private Nation resolvePolicyNation(CommandSender sender, String[] args, NationService nationService) {
        if (args.length >= 3) {
            return nationService.nationByName(args[2]).orElse(null);
        }
        if (sender instanceof Player player) {
            return nationService.nationOf(player.getUniqueId()).orElse(null);
        }
        return null;
    }

    private boolean canSetPolicy(CommandSender sender, Nation nation) {
        return canManageNationStrategy(sender, nation, context.configuration().nationPolicySetOfficerRoles());
    }

    private boolean canClearPolicy(CommandSender sender, Nation nation) {
        return canManageNationStrategy(sender, nation, context.configuration().nationPolicyClearOfficerRoles());
    }

    private NationOperationalOverview nationOperationalOverview(
        NationService nationService,
        NationResourceDistrictService districtService,
        Nation nation
    ) {
        return NationOperationalSupport.overview(
            context == null ? null : context.configuration(),
            nationService,
            districtService,
            nation
        );
    }

    private String formatNationLevelProgress(NationOperationalOverview overview) {
        if (overview == null) {
            return msg("command.resource.none");
        }
        if (overview.maxLevelReached()) {
            return msg("command.nation.level-progress-max");
        }
        return msg(
            "command.nation.level-progress-detail",
            overview.currentLevelProgress(),
            overview.nextLevelExperienceRequired(),
            overview.remainingExperienceToNextLevel()
        );
    }

    private void technologyStatus(CommandSender sender, String[] args, NationService nationService, TechnologyService technologyService) {
        Nation nation = resolveTechnologyNation(sender, args, nationService);
        if (nation == null) {
            sender.sendMessage(Component.text(msg("command.technology.status-usage"), NamedTextColor.YELLOW));
            return;
        }
        Collection<String> unlocked = technologyService.unlockedTechnologies(nation.id());
        sender.sendMessage(Component.text(msg("command.technology.status-title"), NamedTextColor.GOLD)
            .append(Component.text(nation.name(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text(msg("command.technology.unlocked-label"), NamedTextColor.GRAY)
            .append(Component.text(unlocked.isEmpty() ? msg("command.technology.none") : String.join(", ", unlocked), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text(msg("command.technology.available-label"), NamedTextColor.GRAY)
            .append(Component.text(String.join(", ", technologyService.availableTechnologies()), NamedTextColor.WHITE)));
    }

    private void technologyTree(CommandSender sender, String[] args, NationService nationService, TechnologyService technologyService) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(msg("command.player-only"), NamedTextColor.RED));
            return;
        }
        Nation nation = resolveTechnologyNation(sender, args, nationService);
        if (nation == null) {
            sender.sendMessage(Component.text(msg("command.technology.tree-usage"), NamedTextColor.YELLOW));
            return;
        }
        List<String> technologies = technologyService.availableTechnologies().stream()
            .sorted()
            .toList();
        Set<String> unlocked = Set.copyOf(technologyService.unlockedTechnologies(nation.id()));
        openTechnologyTreeMenu(player, nation, technologies, unlocked, technologyService);
    }

    private void openTechnologyTreeMenu(Player player, Nation nation, List<String> technologies, Set<String> unlocked, TechnologyService technologyService) {
        String title = msg("command.technology.tree-title", nation.name());
        try {
            InventoryView view = MenuType.GENERIC_9X3.builder()
                .title(Component.text(title))
                .build(player);
            populateTechnologyTree(view.getTopInventory(), nation, technologies, unlocked, technologyService);
            view.open();
        } catch (RuntimeException | LinkageError exception) {
            Inventory inventory = Bukkit.createInventory(null, 27, title);
            populateTechnologyTree(inventory, nation, technologies, unlocked, technologyService);
            player.openInventory(inventory);
        }
    }

    private void populateTechnologyTree(Inventory inventory, Nation nation, List<String> technologies, Set<String> unlocked, TechnologyService technologyService) {
        inventory.setItem(4, menuItem(Material.REDSTONE_TORCH, msg("command.technology.tree-overview-name"), List.of(
            msg("command.technology.tree-overview-lore", technologies.size(), unlocked.size())
        ), NamedTextColor.GOLD));
        if (technologies.isEmpty()) {
            inventory.setItem(13, menuItem(Material.BARRIER, msg("command.technology.tree-empty"), List.of(), NamedTextColor.RED));
            return;
        }
        int slot = 10;
        for (String technology : technologies) {
            if (slot >= inventory.getSize()) {
                break;
            }
            inventory.setItem(slot, technologyTreeItem(nation, technology, unlocked.contains(technology), technologyService.costOf(technology).orElse(null)));
            slot++;
            if (slot % 9 == 8) {
                slot += 2;
            }
        }
    }

    private ItemStack technologyTreeItem(Nation nation, String technology, boolean unlocked, TechnologyCost cost) {
        Material material = unlocked ? Material.EMERALD_BLOCK : technologyMaterial(technology);
        List<String> lore = new ArrayList<>();
        lore.add(msg("command.technology.tree-key", technology));
        lore.add(unlocked ? msg("command.technology.tree-state-unlocked") : msg("command.technology.tree-state-locked"));
        lore.add(msg("command.technology.tree-treasury-cost", cost == null ? msg("command.technology.none") : cost.treasury().toPlainString()));
        lore.add(msg("command.technology.tree-resource-cost", cost == null ? msg("command.technology.none") : formatTechnologyResources(cost.resources())));
        lore.add(msg("command.technology.tree-command", nation.name(), technology));
        return menuItem(material, technology, lore, unlocked ? NamedTextColor.GREEN : NamedTextColor.WHITE);
    }

    private Material technologyMaterial(String technology) {
        return switch (technology) {
            case "logistics" -> Material.RAIL;
            case "steel_working" -> Material.IRON_INGOT;
            case "radio_command" -> Material.REDSTONE;
            case "mechanized_warfare" -> Material.MINECART;
            case "industrial_planning" -> Material.CRAFTING_TABLE;
            default -> Material.BOOK;
        };
    }

    private String formatTechnologyResources(Map<String, Long> resources) {
        if (resources.isEmpty()) {
            return msg("command.technology.none");
        }
        return resources.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .toList()
            .stream()
            .reduce((left, right) -> left + ", " + right)
            .orElse(msg("command.technology.none"));
    }

    private void technologyUnlock(CommandSender sender, String[] args, NationService nationService, TechnologyService technologyService) {
        if (args.length < 4) {
            sender.sendMessage(Component.text(msg("command.technology.unlock-usage"), NamedTextColor.YELLOW));
            emitStrategyFeedback("strategy-failed", sender);
            return;
        }
        Nation nation = nationService.nationByName(args[2]).orElse(null);
        if (nation == null) {
            sender.sendMessage(Component.text(msg("command.technology.nation-not-found"), NamedTextColor.RED));
            emitStrategyFeedback("strategy-failed", sender);
            return;
        }
        if (!canUnlockTechnology(sender, nation)) {
            sender.sendMessage(Component.text(msg("command.technology.unlock-no-permission"), NamedTextColor.RED));
            emitStrategyFeedback("strategy-failed", sender);
            return;
        }
        if (!technologyService.unlock(nation.id(), args[3])) {
            sender.sendMessage(Component.text(msg("command.technology.unknown", String.join(", ", technologyService.availableTechnologies())), NamedTextColor.RED));
            emitStrategyFeedback("strategy-failed", sender);
            return;
        }
        String technology = args[3].toLowerCase(Locale.ROOT);
        String reason = operationReason(commandReason(args, 4), "technology-unlocked");
        recordNationEvent(
            nation,
            "technology.unlocked",
            sender.getName() + " unlocked " + technology + " (reason: " + displayReason(reason) + ")",
            strategyContext(sender.getName(), nation, "technology-unlock", "technology", technology, reason)
        );
        sender.sendMessage(Component.text(msg("command.technology.unlocked-success"), NamedTextColor.GREEN)
            .append(Component.text(nation.name(), NamedTextColor.WHITE))
            .append(Component.text(" -> " + technology, NamedTextColor.GRAY)));
        emitStrategyFeedback("technology-unlocked", sender);
    }

    private void technologyRevoke(CommandSender sender, String[] args, NationService nationService, TechnologyService technologyService) {
        if (args.length < 4) {
            sender.sendMessage(Component.text(msg("command.technology.revoke-usage"), NamedTextColor.YELLOW));
            emitStrategyFeedback("strategy-failed", sender);
            return;
        }
        Nation nation = nationService.nationByName(args[2]).orElse(null);
        if (nation == null) {
            sender.sendMessage(Component.text(msg("command.technology.nation-not-found"), NamedTextColor.RED));
            emitStrategyFeedback("strategy-failed", sender);
            return;
        }
        if (!canRevokeTechnology(sender, nation)) {
            sender.sendMessage(Component.text(msg("command.technology.revoke-no-permission"), NamedTextColor.RED));
            emitStrategyFeedback("strategy-failed", sender);
            return;
        }
        if (!technologyService.revoke(nation.id(), args[3])) {
            sender.sendMessage(Component.text(msg("command.technology.revoke-failed"), NamedTextColor.RED));
            emitStrategyFeedback("strategy-failed", sender);
            return;
        }
        String technology = args[3].toLowerCase(Locale.ROOT);
        String reason = operationReason(commandReason(args, 4), "technology-revoked");
        recordNationEvent(
            nation,
            "technology.revoked",
            sender.getName() + " revoked " + technology + " (reason: " + displayReason(reason) + ")",
            strategyContext(sender.getName(), nation, "technology-revoke", "technology", technology, reason)
        );
        sender.sendMessage(Component.text(msg("command.technology.revoked-success"), NamedTextColor.GREEN)
            .append(Component.text(nation.name(), NamedTextColor.WHITE))
            .append(Component.text(" -> " + technology, NamedTextColor.GRAY)));
        emitStrategyFeedback("technology-revoked", sender);
    }

    private Nation resolveTechnologyNation(CommandSender sender, String[] args, NationService nationService) {
        if (args.length >= 3) {
            return nationService.nationByName(args[2]).orElse(null);
        }
        if (sender instanceof Player player) {
            return nationService.nationOf(player.getUniqueId()).orElse(null);
        }
        return null;
    }

    private boolean canUnlockTechnology(CommandSender sender, Nation nation) {
        return canManageNationStrategy(sender, nation, context.configuration().nationTechnologyUnlockOfficerRoles());
    }

    private boolean canRevokeTechnology(CommandSender sender, Nation nation) {
        return canManageNationStrategy(sender, nation, context.configuration().nationTechnologyRevokeOfficerRoles());
    }

    private boolean canManageNationStrategy(CommandSender sender, Nation nation, List<String> allowedRoles) {
        if (sender.hasPermission("starcore.admin")) {
            return true;
        }
        if (!(sender instanceof Player player) || nation == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        return nation.founderId().equals(playerId)
            || authorizedOfficerRole(nation.id(), playerId, allowedRoles).isPresent();
    }

    private void warStatus(CommandSender sender, String[] args, NationService nationService, WarService warService) {
        if (args.length >= 3) {
            Nation nation = nationService.nationByName(args[2]).orElse(null);
            if (nation == null) {
                sender.sendMessage(Component.text(msg("command.war.nation-not-found"), NamedTextColor.RED));
                return;
            }
            sender.sendMessage(Component.text(msg("command.war.status-title"), NamedTextColor.GOLD)
                .append(Component.text(nation.name(), NamedTextColor.WHITE)));
            List<WarSnapshot> wars = warService.activeWarsOf(nation.id()).stream().toList();
            if (wars.isEmpty()) {
                sender.sendMessage(Component.text(msg("command.war.none"), NamedTextColor.GRAY));
                return;
            }
            for (WarSnapshot war : wars) {
                sender.sendMessage(formatWarSnapshot(war, nationService));
            }
            return;
        }
        sender.sendMessage(Component.text(msg("command.war.active-title"), NamedTextColor.GOLD));
        List<WarSnapshot> wars = warService.activeWars().stream().toList();
        if (wars.isEmpty()) {
            sender.sendMessage(Component.text(msg("command.war.none"), NamedTextColor.GRAY));
            return;
        }
        for (WarSnapshot war : wars) {
            sender.sendMessage(formatWarSnapshot(war, nationService));
        }
    }

    private void warDeclare(CommandSender sender, String[] args, NationService nationService, WarService warService) {
        if (args.length < 4) {
            sender.sendMessage(Component.text(msg("command.war.declare-usage"), NamedTextColor.YELLOW));
            emitStrategyFeedback("strategy-failed", sender);
            return;
        }
        Nation left = nationService.nationByName(args[2]).orElse(null);
        Nation right = nationService.nationByName(args[3]).orElse(null);
        if (left == null || right == null) {
            sender.sendMessage(Component.text(msg("command.war.target-not-found"), NamedTextColor.RED));
            emitStrategyFeedback("strategy-failed", sender);
            return;
        }
        if (!canDeclareWar(sender, left, right)) {
            sender.sendMessage(Component.text(msg("command.war.declare-no-permission"), NamedTextColor.RED));
            emitStrategyFeedback("strategy-failed", sender);
            return;
        }
        if (!warService.declareWar(left.id(), right.id())) {
            sender.sendMessage(Component.text(msg("command.war.declare-failed"), NamedTextColor.RED));
            emitStrategyFeedback("strategy-failed", sender);
            return;
        }
        String reason = operationReason(commandReason(args, 4), "war-declared");
        recordNationEvent(
            left,
            "war.declared",
            "War declared against " + right.name() + " (reason: " + displayReason(reason) + ")",
            warContext(sender, left, right, "war-declare", reason)
        );
        recordNationEvent(
            right,
            "war.declared",
            "War declared against " + left.name() + " (reason: " + displayReason(reason) + ")",
            warContext(sender, right, left, "war-declare", reason)
        );
        sender.sendMessage(Component.text(msg("command.war.declared-success"), NamedTextColor.GREEN)
            .append(Component.text(left.name() + " vs " + right.name(), NamedTextColor.WHITE)));
        emitStrategyFeedback("war-declared", sender);
    }

    private void warEnd(CommandSender sender, String[] args, NationService nationService, WarService warService) {
        if (args.length < 4) {
            sender.sendMessage(Component.text(msg("command.war.end-usage"), NamedTextColor.YELLOW));
            emitStrategyFeedback("strategy-failed", sender);
            return;
        }
        Nation left = nationService.nationByName(args[2]).orElse(null);
        Nation right = nationService.nationByName(args[3]).orElse(null);
        if (left == null || right == null) {
            sender.sendMessage(Component.text(msg("command.war.target-not-found"), NamedTextColor.RED));
            emitStrategyFeedback("strategy-failed", sender);
            return;
        }
        if (!canEndWar(sender, left, right)) {
            sender.sendMessage(Component.text(msg("command.war.end-no-permission"), NamedTextColor.RED));
            emitStrategyFeedback("strategy-failed", sender);
            return;
        }
        if (!warService.endWar(left.id(), right.id())) {
            sender.sendMessage(Component.text(msg("command.war.end-failed"), NamedTextColor.RED));
            emitStrategyFeedback("strategy-failed", sender);
            return;
        }
        String reason = operationReason(commandReason(args, 4), "war-ended");
        recordNationEvent(
            left,
            "war.ended",
            "War ended with " + right.name() + " (reason: " + displayReason(reason) + ")",
            warContext(sender, left, right, "war-end", reason)
        );
        recordNationEvent(
            right,
            "war.ended",
            "War ended with " + left.name() + " (reason: " + displayReason(reason) + ")",
            warContext(sender, right, left, "war-end", reason)
        );
        sender.sendMessage(Component.text(msg("command.war.ended-success"), NamedTextColor.GREEN)
            .append(Component.text(left.name() + " vs " + right.name(), NamedTextColor.WHITE)));
        emitStrategyFeedback("war-ended", sender);
    }

    private boolean canDeclareWar(CommandSender sender, Nation left, Nation right) {
        return canManageWar(sender, left, right, context.configuration().nationWarDeclareOfficerRoles());
    }

    private boolean canEndWar(CommandSender sender, Nation left, Nation right) {
        return canManageWar(sender, left, right, context.configuration().nationWarEndOfficerRoles());
    }

    private boolean canManageWar(CommandSender sender, Nation left, Nation right, List<String> allowedRoles) {
        if (sender.hasPermission("starcore.admin")) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        return canManageWarForNation(playerId, left, allowedRoles) || canManageWarForNation(playerId, right, allowedRoles);
    }

    private boolean canManageWarForNation(UUID playerId, Nation nation, List<String> allowedRoles) {
        if (playerId == null || nation == null) {
            return false;
        }
        return nation.founderId().equals(playerId)
            || authorizedOfficerRole(nation.id(), playerId, allowedRoles).isPresent();
    }

    private Component formatWarSnapshot(WarSnapshot war, NationService nationService) {
        String leftName = nationService.nationById(war.left()).map(Nation::name).orElse(war.left().toString());
        String rightName = nationService.nationById(war.right()).map(Nation::name).orElse(war.right().toString());
        return Component.text("- ", NamedTextColor.GRAY)
            .append(Component.text(leftName + " vs " + rightName, NamedTextColor.WHITE))
            .append(Component.text(msg("command.war.since", war.declaredAt()), NamedTextColor.DARK_GRAY));
    }

    private void officerStatus(CommandSender sender, NationService nationService, OfficerService officerService) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(msg("command.player-only"), NamedTextColor.RED));
            return;
        }
        Nation nation = nationService.nationOf(player.getUniqueId()).orElse(null);
        if (nation == null) {
            sender.sendMessage(Component.text(msg("officer.not-in-nation"), NamedTextColor.RED));
            return;
        }
        sender.sendMessage(Component.text(msg("officer.status-title"), NamedTextColor.GOLD)
            .append(Component.text(nation.name(), NamedTextColor.WHITE)));
        if (officerService.officersOf(nation.id()).isEmpty()) {
            sender.sendMessage(Component.text(msg("officer.empty", String.join(", ", officerService.availableRoles())), NamedTextColor.GRAY));
            return;
        }
        for (OfficerAppointment appointment : officerService.officersOf(nation.id())) {
            sender.sendMessage(Component.text("- ", NamedTextColor.GRAY)
                .append(Component.text(appointment.role(), NamedTextColor.WHITE))
                .append(Component.text(" = " + appointment.playerName(), NamedTextColor.DARK_GRAY)));
        }
    }

    private void officerAppoint(CommandSender sender, String[] args, NationService nationService, OfficerService officerService) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(msg("command.player-only"), NamedTextColor.RED));
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(Component.text(msg("officer.appoint-usage"), NamedTextColor.YELLOW));
            return;
        }
        Nation nation = nationService.nationOf(player.getUniqueId()).orElse(null);
        if (nation == null) {
            sender.sendMessage(Component.text(msg("officer.not-in-nation"), NamedTextColor.RED));
            return;
        }
        if (!nationService.isFounder(player.getUniqueId(), nation.id()) && !sender.hasPermission("starcore.admin")) {
            sender.sendMessage(Component.text(msg("officer.no-appoint-permission"), NamedTextColor.RED));
            return;
        }
        Optional<PlayerIdentity> target = resolvePlayerIdentity(args[3]);
        if (target.isEmpty()) {
            sender.sendMessage(Component.text(msg("officer.player-not-found"), NamedTextColor.RED));
            return;
        }
        if (!nation.hasMember(target.get().playerId())) {
            sender.sendMessage(Component.text(msg("officer.member-only"), NamedTextColor.RED));
            return;
        }
        if (!officerService.appoint(nation.id(), args[2], target.get().playerId(), target.get().playerName())) {
            sender.sendMessage(Component.text(msg("officer.unknown-role", String.join(", ", officerService.availableRoles())), NamedTextColor.RED));
            return;
        }
        String role = normalizeOfficerRole(args[2]);
        String reason = operationReason(commandReason(args, 4), "officer-appointed");
        recordNationEvent(
            nation,
            "officer.appointed",
            target.get().playerName() + " appointed as " + role + " (reason: " + displayReason(reason) + ")",
            officerContext(player.getName(), role, target.get(), "officer-appoint", reason)
        );
        sender.sendMessage(Component.text(msg("officer.appointed-success"), NamedTextColor.GREEN)
            .append(Component.text(role, NamedTextColor.WHITE))
            .append(Component.text(" = " + target.get().playerName(), NamedTextColor.GRAY)));
        emitOfficerFeedback("officer-appointed", player);
    }

    private void officerRemove(CommandSender sender, String[] args, NationService nationService, OfficerService officerService) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(msg("command.player-only"), NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text(msg("officer.remove-usage"), NamedTextColor.YELLOW));
            return;
        }
        Nation nation = nationService.nationOf(player.getUniqueId()).orElse(null);
        if (nation == null) {
            sender.sendMessage(Component.text(msg("officer.not-in-nation"), NamedTextColor.RED));
            return;
        }
        if (!nationService.isFounder(player.getUniqueId(), nation.id()) && !sender.hasPermission("starcore.admin")) {
            sender.sendMessage(Component.text(msg("officer.no-remove-permission"), NamedTextColor.RED));
            return;
        }
        if (!officerService.remove(nation.id(), args[2])) {
            sender.sendMessage(Component.text(msg("officer.remove-failed"), NamedTextColor.RED));
            return;
        }
        String role = normalizeOfficerRole(args[2]);
        String reason = operationReason(commandReason(args, 3), "officer-removed");
        recordNationEvent(
            nation,
            "officer.removed",
            role + " appointment removed (reason: " + displayReason(reason) + ")",
            officerContext(player.getName(), role, null, "officer-remove", reason)
        );
        sender.sendMessage(Component.text(msg("officer.removed-success"), NamedTextColor.GREEN)
            .append(Component.text(role, NamedTextColor.WHITE)));
        emitOfficerFeedback("officer-removed", player);
    }

    /**
     * 列出所有国家的官员信息
     * /sc officer list [国家名]
     */
    private void officerList(CommandSender sender, String[] args, NationService nationService, OfficerService officerService) {
        Nation targetNation = null;

        if (args.length >= 3) {
            // 指定国家
            targetNation = nationService.nationByName(args[2]).orElse(null);
            if (targetNation == null) {
                sender.sendMessage(Component.text(msg("command.nation.nation-not-found"), NamedTextColor.RED));
                return;
            }
        } else if (sender instanceof Player player) {
            // 当前玩家所在国家
            targetNation = nationService.nationOf(player.getUniqueId()).orElse(null);
        }

        if (targetNation == null) {
            sender.sendMessage(Component.text(msg("officer.list-usage"), NamedTextColor.YELLOW));
            return;
        }

        sender.sendMessage(Component.text(msg("officer.list-title"), NamedTextColor.GOLD)
            .append(Component.text(targetNation.name(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("==============================================", NamedTextColor.GRAY));

        Collection<OfficerAppointment> officers = officerService.officersOf(targetNation.id());

        if (officers.isEmpty()) {
            sender.sendMessage(Component.text(msg("officer.empty", String.join(", ", officerService.availableRoles())), NamedTextColor.GRAY));
        } else {
            for (OfficerAppointment appointment : officers) {
                String roleName = getLocalizedRoleName(appointment.role(), officerService);
                sender.sendMessage(Component.text("  " + roleName + ": ", NamedTextColor.YELLOW)
                    .append(Component.text(appointment.playerName(), NamedTextColor.WHITE)));
            }
        }

        // 显示空缺职位
        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text(msg("officer.vacant-title"), NamedTextColor.GRAY));
        for (String roleId : officerService.availableRoles()) {
            final String roleIdFinal = roleId;
            boolean hasOfficer = officers.stream().anyMatch(o -> o.role().equals(roleIdFinal));
            if (!hasOfficer) {
                String roleName = getLocalizedRoleName(roleId, officerService);
                sender.sendMessage(Component.text("  " + roleName, NamedTextColor.DARK_GRAY));
            }
        }
    }

    /**
     * 查看特定官员详情
     * /sc officer info [角色名]
     */
    private void officerInfo(CommandSender sender, String[] args, NationService nationService, OfficerService officerService) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(msg("command.player-only"), NamedTextColor.RED));
            return;
        }

        Nation nation = nationService.nationOf(player.getUniqueId()).orElse(null);
        if (nation == null) {
            sender.sendMessage(Component.text(msg("officer.not-in-nation"), NamedTextColor.RED));
            return;
        }

        if (args.length < 3) {
            // 显示当前官员信息
            sender.sendMessage(Component.text(msg("officer.info-title", nation.name()), NamedTextColor.GOLD));

            Collection<OfficerAppointment> officers = officerService.officersOf(nation.id());
            if (officers.isEmpty()) {
                sender.sendMessage(Component.text(msg("officer.empty", String.join(", ", officerService.availableRoles())), NamedTextColor.GRAY));
            } else {
                sender.sendMessage(Component.text(msg("officer.current-officers"), NamedTextColor.GRAY));
                for (OfficerAppointment appointment : officers) {
                    String roleName = getLocalizedRoleName(appointment.role(), officerService);
                    boolean isOnline = Bukkit.getPlayer(appointment.playerId()) != null;
                    NamedTextColor nameColor = isOnline ? NamedTextColor.GREEN : NamedTextColor.GRAY;
                    NamedTextColor statusColor = isOnline ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY;

                    sender.sendMessage(Component.text("  " + roleName + ": ", NamedTextColor.YELLOW)
                        .append(Component.text(appointment.playerName(), nameColor))
                        .append(Component.text(" (" + (isOnline ? msg("officer.online") : msg("officer.offline")) + ")", statusColor)));
                }
            }
            return;
        }

        // 查看特定角色信息
        String roleId = normalizeOfficerRole(args[2]);
        Optional<OfficerAppointment> officerOpt = officerService.officer(nation.id(), roleId);

        sender.sendMessage(Component.text(msg("officer.info-title", nation.name()), NamedTextColor.GOLD));

        String roleName = getLocalizedRoleName(roleId, officerService);
        sender.sendMessage(Component.text(msg("officer.role") + " " + roleName, NamedTextColor.YELLOW));

        if (officerOpt.isPresent()) {
            OfficerAppointment appointment = officerOpt.get();
            boolean isOnline = Bukkit.getPlayer(appointment.playerId()) != null;

            sender.sendMessage(Component.text(msg("officer.officer") + " " + appointment.playerName(), NamedTextColor.GREEN));
            sender.sendMessage(Component.text(msg("officer.status") + " " + (isOnline ? msg("officer.online") : msg("officer.offline")),
                isOnline ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        } else {
            sender.sendMessage(Component.text(msg("officer.no-officer"), NamedTextColor.RED));
        }
    }

    /**
     * 打开官员管理 GUI
     * /sc officer gui
     */
    private void officerGui(CommandSender sender, NationService nationService, OfficerService officerService) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(msg("command.player-only"), NamedTextColor.RED));
            return;
        }

        Nation nation = nationService.nationOf(player.getUniqueId()).orElse(null);
        if (nation == null) {
            sender.sendMessage(Component.text(msg("officer.not-in-nation"), NamedTextColor.RED));
            return;
        }

        // 检查是否为创始人或有管理员权限
        if (!nationService.isFounder(player.getUniqueId(), nation.id()) && !sender.hasPermission("starcore.admin")) {
            sender.sendMessage(Component.text(msg("officer.no-gui-permission"), NamedTextColor.RED));
            return;
        }

        // 打开 GUI
        if (officerService instanceof dev.starcore.starcore.module.officer.OfficerModule officerModule) {
            officerModule.openOfficerMenu(player, nation);
        } else {
            sender.sendMessage(Component.text(msg("officer.gui-unavailable"), NamedTextColor.RED));
        }
    }

    /**
     * 获取角色的本地化名称
     */
    private String getLocalizedRoleName(String roleId, OfficerService officerService) {
        if (officerService instanceof dev.starcore.starcore.module.officer.OfficerModule officerModule) {
            return officerModule.getRoleDisplayName(roleId);
        }
        return roleId;
    }

    private Optional<PlayerIdentity> resolvePlayerIdentity(String playerName) {
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) {
            return Optional.of(new PlayerIdentity(online.getUniqueId(), online.getName()));
        }
        PlayerProfileService profiles = context.serviceRegistry().find(PlayerProfileService.class).orElse(null);
        if (profiles == null) {
            return Optional.empty();
        }
        return profiles.snapshot().entrySet().stream()
            .filter(entry -> entry.getValue().equalsIgnoreCase(playerName))
            .findFirst()
            .map(entry -> new PlayerIdentity(entry.getKey(), entry.getValue()));
    }

    private record PlayerIdentity(UUID playerId, String playerName) {
    }

    private void recordNationEvent(Nation nation, String type, String message) {
        recordNationEvent(nation, type, message, "");
    }

    private void recordNationEvent(Nation nation, String type, String message, String eventContext) {
        EventService eventService = context.serviceRegistry().find(EventService.class).orElse(null);
        if (eventService == null || nation == null) {
            return;
        }
        try {
            eventService.record(nation.id(), type, message, eventContext);
        } catch (RuntimeException ignored) {
        }
    }

    private String buildContext(String... keyValues) {
        List<String> entries = new ArrayList<>();
        if (keyValues == null) {
            return "";
        }
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            addContextEntry(entries, keyValues[index], keyValues[index + 1]);
        }
        return String.join(";", entries);
    }

    private String commandReason(String[] args, int startIndex) {
        if (args.length <= startIndex) {
            return "";
        }
        return String.join(" ", Arrays.copyOfRange(args, startIndex, args.length)).trim();
    }

    private String displayReason(String reason) {
        return reason == null || reason.isBlank() ? msg("command.event.reason-unspecified") : reason.trim();
    }

    private String operationReason(String reason, String fallback) {
        return reason == null || reason.isBlank() ? fallback : reason.trim();
    }

    private String ledgerContext(String actor, String amount, String resourceType, String balance, String reason) {
        List<String> entries = new ArrayList<>();
        addContextEntry(entries, "actor", actor);
        addContextEntry(entries, "amount", amount);
        addContextEntry(entries, "resource", resourceType);
        addContextEntry(entries, "balance", balance);
        addContextEntry(entries, "reason", reason);
        return String.join(";", entries);
    }

    private String diplomacyContext(CommandSender sender, Nation nation, Nation target, DiplomacyRelation relation, String operation, String reason) {
        List<String> entries = new ArrayList<>();
        addContextEntry(entries, "actor", sender == null ? null : sender.getName());
        addContextEntry(entries, "operation", operation);
        addContextEntry(entries, "target", target == null ? null : target.name());
        addContextEntry(entries, "targetId", target == null ? null : target.id().toString());
        addContextEntry(entries, "relation", relation == null ? null : relation.name());
        addContextEntry(entries, "relationDisplay", relation == null ? null : relation.displayName());
        addContextEntry(entries, "pairId", nationPairId(nation == null ? null : nation.id(), target == null ? null : target.id()));
        addContextEntry(entries, "reason", reason);
        return String.join(";", entries);
    }

    private String resolutionContext(CommandSender sender, Nation nation, Resolution resolution, String operation, String reason) {
        List<String> entries = new ArrayList<>();
        addContextEntry(entries, "actor", sender == null ? null : sender.getName());
        addContextEntry(entries, "operation", operation);
        addContextEntry(entries, "target", nation == null ? null : nation.name());
        addContextEntry(entries, "targetId", nation == null ? null : nation.id().toString());
        if (resolution != null) {
            addContextEntry(entries, "resolutionId", resolution.id().toString());
            addContextEntry(entries, "kind", resolution.action().kind().name().toLowerCase(Locale.ROOT));
            addContextEntry(entries, "state", resolution.state().name().toLowerCase(Locale.ROOT));
            addContextEntry(entries, "summary", resolution.action().summary());
            if (resolution.action() instanceof JoinNationRequestAction joinNationRequestAction) {
                addContextEntry(entries, "applicant", joinNationRequestAction.applicantName());
                addContextEntry(entries, "applicantId", joinNationRequestAction.applicantId().toString());
            } else if (resolution.action() instanceof RenameNationAction renameNationAction) {
                addContextEntry(entries, "from", renameNationAction.oldName());
                addContextEntry(entries, "to", renameNationAction.newName());
            } else if (resolution.action() instanceof ChangeGovernmentAction changeGovernmentAction) {
                addContextEntry(entries, "from", changeGovernmentAction.from().name());
                addContextEntry(entries, "to", changeGovernmentAction.to().name());
                addContextEntry(entries, "government", changeGovernmentAction.to().name());
            } else if (resolution.action() instanceof ChangeDiplomacyRelationAction changeDiplomacyRelationAction) {
                addContextEntry(entries, "relation", changeDiplomacyRelationAction.relation().name());
                addContextEntry(entries, "relationDisplay", changeDiplomacyRelationAction.relation().displayName());
                addContextEntry(entries, "pairId", nationPairId(nation == null ? null : nation.id(), changeDiplomacyRelationAction.targetNationId()));
                addContextEntry(entries, "target", changeDiplomacyRelationAction.targetNationName());
                addContextEntry(entries, "targetId", changeDiplomacyRelationAction.targetNationId().toString());
            }
        }
        addContextEntry(entries, "reason", reason);
        return String.join(";", entries);
    }

    private String strategyContext(String actor, Nation nation, String operation, String subjectKey, String subjectValue, String reason) {
        List<String> entries = new ArrayList<>();
        addContextEntry(entries, "actor", actor);
        addContextEntry(entries, "operation", operation);
        addContextEntry(entries, "target", nation == null ? null : nation.name());
        addContextEntry(entries, "targetId", nation == null ? null : nation.id().toString());
        addContextEntry(entries, subjectKey, subjectValue);
        addContextEntry(entries, "reason", reason);
        return String.join(";", entries);
    }

    private String warContext(CommandSender sender, Nation nation, Nation target, String operation, String reason) {
        List<String> entries = new ArrayList<>();
        addContextEntry(entries, "actor", sender == null ? null : sender.getName());
        addContextEntry(entries, "operation", operation);
        addContextEntry(entries, "target", target == null ? null : target.name());
        addContextEntry(entries, "targetId", target == null ? null : target.id().toString());
        addContextEntry(entries, "warId", warId(nation == null ? null : nation.id(), target == null ? null : target.id()));
        addContextEntry(entries, "reason", reason);
        return String.join(";", entries);
    }

    private String officerContext(String actor, String role, PlayerIdentity target, String operation, String reason) {
        List<String> entries = new ArrayList<>();
        addContextEntry(entries, "actor", actor);
        addContextEntry(entries, "operation", operation);
        addContextEntry(entries, "role", role);
        addContextEntry(entries, "member", target == null ? null : target.playerName());
        addContextEntry(entries, "target", target == null ? null : target.playerName());
        addContextEntry(entries, "targetId", target == null ? null : target.playerId().toString());
        addContextEntry(entries, "reason", reason);
        return String.join(";", entries);
    }

    private String warId(NationId left, NationId right) {
        return nationPairId(left, right);
    }

    private String nationPairId(NationId left, NationId right) {
        if (left == null || right == null) {
            return "";
        }
        String leftId = left.toString();
        String rightId = right.toString();
        return leftId.compareTo(rightId) <= 0 ? leftId + ":" + rightId : rightId + ":" + leftId;
    }

    private String normalizeOfficerRole(String role) {
        return role == null ? "" : role.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private String dailyIncomeLedgerContext(String actor, String amount, String balance, int members, int claims, String reason) {
        List<String> entries = new ArrayList<>();
        addContextEntry(entries, "actor", actor);
        addContextEntry(entries, "amount", amount);
        addContextEntry(entries, "balance", balance);
        addContextEntry(entries, "members", String.valueOf(members));
        addContextEntry(entries, "claims", String.valueOf(claims));
        addContextEntry(entries, "reason", reason);
        return String.join(";", entries);
    }

    private BigDecimal dailyIncomeBaseAmount() {
        return context.configuration() == null ? new BigDecimal("100.00") : context.configuration().nationDailyIncomeBaseAmount();
    }

    private BigDecimal dailyIncomePerMember() {
        return context.configuration() == null ? new BigDecimal("25.00") : context.configuration().nationDailyIncomePerMember();
    }

    private BigDecimal dailyIncomePerClaim() {
        return context.configuration() == null ? new BigDecimal("5.00") : context.configuration().nationDailyIncomePerClaim();
    }

    private boolean taxEnabled() {
        return context.configuration() != null && context.configuration().nationTaxEnabled();
    }

    private BigDecimal taxFixedAmount() {
        return context.configuration() == null ? BigDecimal.ZERO.setScale(2, java.math.RoundingMode.DOWN) : context.configuration().nationTaxFixedAmount();
    }

    private BigDecimal taxBalancePercent() {
        return context.configuration() == null ? BigDecimal.ZERO.setScale(2, java.math.RoundingMode.DOWN) : context.configuration().nationTaxBalancePercent();
    }

    private BigDecimal taxMinimumBalance() {
        return context.configuration() == null ? BigDecimal.ZERO.setScale(2, java.math.RoundingMode.DOWN) : context.configuration().nationTaxMinimumBalance();
    }

    private boolean taxSkipInsufficientMembers() {
        return context.configuration() == null || context.configuration().nationTaxSkipInsufficientMembers();
    }

    private void addContextEntry(List<String> entries, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        entries.add(key + "=" + sanitizeContextValue(value));
    }

    private String sanitizeContextValue(String value) {
        return value == null ? "" : value.replace(';', ',').replace('\n', ' ').replace('\r', ' ').trim();
    }

    private String formatEventContext(String context) {
        if (context == null || context.isBlank()) {
            return "";
        }
        String trimmed = context.trim();
        if (!trimmed.contains("=")) {
            return trimmed;
        }
        List<String> parts = new ArrayList<>();
        for (String entry : trimmed.split(";")) {
            int separator = entry.indexOf('=');
            if (separator <= 0 || separator == entry.length() - 1) {
                continue;
            }
            String key = entry.substring(0, separator).trim();
            String value = entry.substring(separator + 1).trim();
            if (!value.isBlank()) {
                parts.add(msg(eventContextMessageKey(contextKeyLabel(key)), value));
            }
        }
        return parts.isEmpty() ? trimmed : String.join(" | ", parts);
    }

    private String eventContextMessageKey(String suffix) {
        return "command.event.context." + suffix;
    }

    private String contextKeyLabel(String key) {
        return switch (normalizeToken(key)) {
            case "actor" -> "actor";
            case "amount" -> "amount";
            case "resource" -> "resource";
            case "balance" -> "balance";
            case "members" -> "members";
            case "claims" -> "claims";
            case "reason" -> "reason";
            default -> "other";
        };
    }

    private void mapExport(CommandSender sender, MapService mapService) {
        try {
            Path output = mapService.exportStaticSite();
            sender.sendMessage(Component.text(msg("command.map.exported"), NamedTextColor.GREEN)
                .append(Component.text(output.toString(), NamedTextColor.WHITE)));
        } catch (IOException exception) {
            sender.sendMessage(Component.text(msg("command.map.export-failed", exception.getMessage()), NamedTextColor.RED));
        }
    }

    private void mapWeb(CommandSender sender, MapService mapService) {
        Optional<String> address = mapService.webAddress();
        if (address.isEmpty()) {
            sender.sendMessage(Component.text(msg("command.map.web-disabled"), NamedTextColor.YELLOW));
            return;
        }
        String base = address.get();
        if (sender instanceof Player player) {
            boolean fullAccess = player.hasPermission("starcore.admin");
            Optional<String> boundAddress = mapService.bindViewerWebAddress(player.getUniqueId(), fullAccess, remoteAddressOf(player));
            if (boundAddress.isPresent()) {
                sender.sendMessage(Component.text(fullAccess ? msg("command.map.admin-address") : msg("command.map.personal-address"), NamedTextColor.GREEN)
                    .append(Component.text(boundAddress.get(), NamedTextColor.WHITE)));
                sender.sendMessage(Component.text(msg("command.map.ip-bound", context.configuration().mapWebIpAccessTtlMinutes()), NamedTextColor.GRAY));
                sendPublicUrlHintIfNeeded(sender, boundAddress.get());
                return;
            }
            Optional<String> viewerAddress = mapService.viewerWebAddress(player.getUniqueId(), fullAccess);
            if (viewerAddress.isPresent()) {
                sender.sendMessage(Component.text(fullAccess ? msg("command.map.admin-address") : msg("command.map.personal-address"), NamedTextColor.GREEN)
                    .append(Component.text(viewerAddress.get(), NamedTextColor.WHITE)));
                sender.sendMessage(Component.text(msg("command.map.signed-link-hint"), NamedTextColor.GRAY));
                sendPublicUrlHintIfNeeded(sender, viewerAddress.get());
                return;
            }
            sender.sendMessage(Component.text(msg("command.map.secret-missing"), NamedTextColor.YELLOW));
            return;
        }
        sender.sendMessage(Component.text(msg("command.map.public-address"), NamedTextColor.GREEN)
            .append(Component.text(base, NamedTextColor.WHITE)));
        sendPublicUrlHintIfNeeded(sender, base);
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text(msg("command.map.player-link-hint"), NamedTextColor.YELLOW));
        }
        sender.sendMessage(Component.text(msg("command.map.health-label"), NamedTextColor.GRAY)
            .append(Component.text(base + "api/map/health", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text(msg("command.map.stream-label"), NamedTextColor.GRAY)
            .append(Component.text(base + "api/map/stream", NamedTextColor.WHITE)));
    }

    private void mapConfirm(CommandSender sender, String label, String[] args, MapService mapService) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(msg("command.player-only"), NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text(msg("command.map.confirm-usage", label), NamedTextColor.YELLOW));
            return;
        }
        try {
            WebClaimConfirmationResult result = mapService.confirmWebClaim(player.getUniqueId(), args[2]);
            if (!result.confirmed()) {
                sendWebClaimFailure(sender, result, "command.map.confirm-failed");
                emitClaimFeedback("web-failed", player);
                return;
            }
            sender.sendMessage(Component.text(msg("command.map.confirmed"), NamedTextColor.GREEN)
                .append(Component.text(msg("command.map.confirmed-chunks", result.claimedChunks()), NamedTextColor.WHITE))
                .append(Component.text(msg("command.map.confirmed-owner", result.nationName()), NamedTextColor.GRAY)));
            sender.sendMessage(Component.text(msg("command.map.confirmed-cost"), NamedTextColor.GRAY)
                .append(Component.text(result.price().toPlainString(), NamedTextColor.WHITE)));
            emitClaimFeedback("web-confirmed", player);
        } catch (RuntimeException exception) {
            sender.sendMessage(Component.text(msg("command.map.confirm-failed", exception.getMessage()), NamedTextColor.RED));
            emitClaimFeedback("web-failed", player);
        }
    }

    private void mapCancel(CommandSender sender, String label, String[] args, MapService mapService) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(msg("command.player-only"), NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text(msg("command.map.cancel-usage", label), NamedTextColor.YELLOW));
            return;
        }
        try {
            WebClaimConfirmationResult result = mapService.cancelWebClaim(player.getUniqueId(), args[2]);
            if (!result.cancelled()) {
                sendWebClaimFailure(sender, result, "command.map.cancel-failed");
                emitClaimFeedback("web-failed", player);
                return;
            }
            sender.sendMessage(Component.text(msg("command.map.cancelled"), NamedTextColor.GREEN)
                .append(Component.text(msg("command.map.cancelled-id", result.pendingId()), NamedTextColor.WHITE)));
            sendClaimExplanation(sender, result.explanation());
            emitClaimFeedback("web-cancelled", player);
        } catch (RuntimeException exception) {
            sender.sendMessage(Component.text(msg("command.map.cancel-failed", exception.getMessage()), NamedTextColor.RED));
            emitClaimFeedback("web-failed", player);
        }
    }

    private void sendWebClaimFailure(CommandSender sender, WebClaimConfirmationResult result, String messageKey) {
        String message = result == null ? "" : result.message();
        sender.sendMessage(Component.text(msg(messageKey, message), NamedTextColor.RED));
        if (result != null) {
            sendClaimExplanation(sender, result.explanation());
        }
    }

    private String remoteAddressOf(Player player) {
        if (player.getAddress() == null || player.getAddress().getAddress() == null) {
            return null;
        }
        return player.getAddress().getAddress().getHostAddress();
    }

    private void sendPublicUrlHintIfNeeded(CommandSender sender, String address) {
        if (address == null) {
            return;
        }
        String normalized = address.toLowerCase(Locale.ROOT);
        if (normalized.contains("//127.0.0.1") || normalized.contains("//localhost") || normalized.contains("//0.0.0.0")) {
            sender.sendMessage(Component.text(msg("command.map.public-url-hint"), NamedTextColor.YELLOW));
        }
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(Component.text("STARCORE ", NamedTextColor.GOLD).append(Component.text(context.plugin().getPluginMeta().getVersion(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text(msg("command.status.platform"), NamedTextColor.GRAY).append(Component.text(context.platformAdapter().platformName(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text(msg("command.status.minecraft"), NamedTextColor.GRAY).append(Component.text(context.platformAdapter().minecraftVersion(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text(msg("command.status.compatibility"), NamedTextColor.GRAY).append(Component.text(context.platformAdapter().supportSummary(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text(msg("command.status.locale"), NamedTextColor.GRAY).append(Component.text(context.configuration().locale(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text(msg("command.status.database"), NamedTextColor.GRAY).append(Component.text(context.databaseService().summary(), NamedTextColor.WHITE)));
        context.serviceRegistry().find(ExternalProtectionService.class).ifPresent(protection -> sendExternalProtectionStatus(sender, protection));
        sender.sendMessage(Component.text(msg("command.status.services"), NamedTextColor.GRAY).append(Component.text(String.valueOf(context.serviceRegistry().snapshot().size()), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text(msg("command.status.modules"), NamedTextColor.GRAY).append(Component.text(String.valueOf(context.moduleManager().descriptors().stream().filter(module -> module.status() == ModuleStatus.ENABLED).count()), NamedTextColor.WHITE)));
        context.serviceRegistry().find(EpochService.class).ifPresent(epoch -> sender.sendMessage(Component.text(msg("command.status.epoch"), NamedTextColor.GRAY).append(Component.text(epoch.summary(), NamedTextColor.WHITE))));
        context.serviceRegistry().find(TimeSyncService.class).ifPresent(time -> sender.sendMessage(Component.text(msg("command.status.time-sync"), NamedTextColor.GRAY).append(Component.text(time.summary(), NamedTextColor.WHITE))));
    }

    private void sendExternalProtectionStatus(CommandSender sender, ExternalProtectionService protection) {
        sender.sendMessage(Component.text(msg("command.status.external-protection"), NamedTextColor.GRAY)
            .append(Component.text(protection.summary(), NamedTextColor.WHITE)));

        List<ExternalProtectionBridgeStatus> statuses = protection.bridgeStatuses().stream()
            .filter(status -> status != null)
            .toList();
        if (statuses.isEmpty()) {
            return;
        }

        boolean needsDetail = statuses.size() > 1 || statuses.stream().anyMatch(status -> !"active".equals(status.stateKey()));
        if (!needsDetail) {
            return;
        }

        for (ExternalProtectionBridgeStatus status : statuses) {
            sender.sendMessage(Component.text(
                msg("command.status.external-protection-detail", status.displayName(), status.stateDisplayName(), status.displaySummary()),
                NamedTextColor.DARK_GRAY
            ));
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        dev.starcore.starcore.foundation.message.StarMessages.header(sender, "星核指令");
        dev.starcore.starcore.foundation.message.StarMessages.entry(sender, "/" + label + " help", "查看本帮助");
        dev.starcore.starcore.foundation.message.StarMessages.entry(sender, "/" + label + " status", "查看插件运行状态");
        dev.starcore.starcore.foundation.message.StarMessages.entry(sender, "/" + label + " nation", "国家：建国/圈地/成员/改名等");
        dev.starcore.starcore.foundation.message.StarMessages.entry(sender, "/" + label + " treasury", "国库：存取/奖励");
        dev.starcore.starcore.foundation.message.StarMessages.entry(sender, "/" + label + " diplomacy", "外交：关系/结盟");
        dev.starcore.starcore.foundation.message.StarMessages.entry(sender, "/" + label + " war", "战争：宣战/停战");
        dev.starcore.starcore.foundation.message.StarMessages.entry(sender, "/" + label + " officer", "官员：任命/罢免");
        dev.starcore.starcore.foundation.message.StarMessages.entry(sender, "/" + label + " policy / technology", "政策与科技");
        dev.starcore.starcore.foundation.message.StarMessages.entry(sender, "/" + label + " resolution", "决议：提案/签署");
        dev.starcore.starcore.foundation.message.StarMessages.entry(sender, "/" + label + " map", "战略地图");
        dev.starcore.starcore.foundation.message.StarMessages.info(sender, "输入指令后按 Tab 可自动补全子命令");
    }

    private void sendModules(CommandSender sender) {
        sender.sendMessage(Component.text(msg("command.modules.title"), NamedTextColor.GOLD));
        for (ModuleDescriptor module : context.moduleManager().descriptors()) {
            sender.sendMessage(Component.text("- ", NamedTextColor.GRAY)
                .append(Component.text(module.metadata().displayName(), NamedTextColor.WHITE))
                .append(Component.text(" [" + module.metadata().id() + "] ", NamedTextColor.DARK_GRAY))
                .append(Component.text(module.status().name(), colorFor(module.status()))));
        }
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("starcore.admin")) {
            sender.sendMessage(Component.text(msg("command.reload.no-permission"), NamedTextColor.RED));
            return;
        }
        context.configuration().reload();
        messages.reload();
        if (context.configuration().ensureMapWebAccessSecretConfigured()) {
            sender.sendMessage(Component.text(msg("command.reload.generated-map-secret"), NamedTextColor.GREEN));
        }
        context.databaseService().restart();
        context.economyService().stop();
        context.economyService().start();
        context.serviceRegistry().find(TimeSyncService.class).ifPresent(TimeSyncService::restart);
        sender.sendMessage(Component.text(msg("command.reload.complete"), NamedTextColor.GREEN));
    }

    private NamedTextColor colorFor(ModuleStatus status) {
        return switch (status) {
            case REGISTERED -> NamedTextColor.YELLOW;
            case ENABLED -> NamedTextColor.GREEN;
            case DISABLED -> NamedTextColor.GRAY;
            case FAILED -> NamedTextColor.RED;
        };
    }

    private void emitClaimFeedback(String eventKey, Player player) {
        if (context.configuration() == null) {
            return;
        }
        emitConfiguredFeedback(eventKey, player, context.configuration()::nationClaimFeedbackProfile);
    }

    private void emitNationOperationFeedback(String eventKey, Player player) {
        if (context.configuration() == null) {
            return;
        }
        emitConfiguredFeedback(eventKey, player, context.configuration()::nationOperationFeedbackProfile);
    }

    private void emitStrategyFeedback(String eventKey, CommandSender sender) {
        if (sender instanceof Player player) {
            emitStrategyFeedback(eventKey, player);
        }
    }

    private void emitStrategyFeedback(String eventKey, Player player) {
        if (context.configuration() == null) {
            return;
        }
        emitConfiguredFeedback(eventKey, player, context.configuration()::nationStrategyFeedbackProfile);
    }

    private void emitDiplomacyFeedback(String eventKey, CommandSender sender) {
        if (sender instanceof Player player) {
            emitDiplomacyFeedback(eventKey, player);
        }
    }

    private void emitDiplomacyFeedback(String eventKey, Player player) {
        if (context.configuration() == null) {
            return;
        }
        emitConfiguredFeedback(eventKey, player, context.configuration()::nationDiplomacyFeedbackProfile);
    }

    private void emitOfficerFeedback(String eventKey, Player player) {
        if (context.configuration() == null) {
            return;
        }
        emitConfiguredFeedback(eventKey, player, context.configuration()::nationOfficerFeedbackProfile);
    }

    private void emitTreasuryFeedback(String eventKey, CommandSender sender, BigDecimal amount) {
        if (!(sender instanceof Player player) || context.configuration() == null || amount == null) {
            return;
        }
        BigDecimal threshold = context.configuration().nationTreasuryFeedbackMinimumAmount();
        if (amount.abs().compareTo(threshold) < 0) {
            return;
        }
        emitConfiguredFeedback(eventKey, player, context.configuration()::nationTreasuryFeedbackProfile);
    }

    private void sendClaimExplanation(CommandSender sender, ClaimSelectionPreview preview) {
        if (sender == null || preview == null || preview.explanation() == null) {
            return;
        }
        sendClaimExplanation(sender, preview.explanation());
    }

    private void sendClaimExplanation(CommandSender sender, ClaimSelectionExplanation explanation) {
        if (sender == null || explanation == null) {
            return;
        }
        explanation.reasons().stream()
            .map(ClaimSelectionReason::message)
            .filter(message -> message != null && !message.isBlank())
            .limit(3)
            .forEach(message -> sender.sendMessage(Component.text("- " + message, NamedTextColor.GRAY)));
    }

    private void emitConfiguredFeedback(String eventKey, Player player, Function<String, InGameFeedbackProfile> profiles) {
        if (player == null || context.plugin() == null || context.configuration() == null) {
            return;
        }
        new BukkitInGameFeedbackService(context.plugin(), profiles).emit(eventKey, player, player.getLocation());
    }

    private String msg(String key, Object... args) {
        return messages.format(key, args);
    }

    /**
     * 创建支持 § 颜色代码的消息组件
     */
    private Component coloredMsg(String key, Object... args) {
        String formatted = messages.format(key, args);
        return LegacyComponentSerializer.legacySection().deserialize(formatted);
    }

    private List<String> prefixMatches(List<String> candidates, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                suggestions.add(candidate);
            }
        }
        return suggestions;
    }

    private List<String> nationNameSuggestions() {
        NationService nationService = context.serviceRegistry().find(NationService.class).orElse(null);
        if (nationService == null) {
            return List.of();
        }
        return nationService.nations().stream()
            .map(Nation::name)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
    }

    private List<String> onlinePlayerNameSuggestions() {
        OnlinePlayerDirectory directory = context.serviceRegistry().find(OnlinePlayerDirectory.class).orElse(null);
        if (directory != null) {
            return directory.onlinePlayerNames().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        }
        return Bukkit.getOnlinePlayers().stream()
            .map(Player::getName)
            .filter(name -> name != null && !name.isBlank())
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
    }

    private List<String> merged(List<String> first, List<String> second) {
        List<String> merged = new ArrayList<>(first.size() + second.size());
        merged.addAll(first);
        merged.addAll(second);
        return merged;
    }

    private String normalizeRoot(String value) {
        return StarCoreCommandAliases.normalizeRoot(value);
    }

    private String normalizeNationSubcommand(String value) {
        return StarCoreCommandAliases.normalizeNationSubcommand(value);
    }

    private String normalizeEconomySubcommand(String value) {
        return StarCoreCommandAliases.normalizeEconomySubcommand(value);
    }

    private String normalizeResolutionSubcommand(String value) {
        return StarCoreCommandAliases.normalizeResolutionSubcommand(value);
    }

    private String normalizeGovernmentSubcommand(String value) {
        return StarCoreCommandAliases.normalizeGovernmentSubcommand(value);
    }

    private String normalizeMapSubcommand(String value) {
        return StarCoreCommandAliases.normalizeMapSubcommand(value);
    }

    private String normalizeDiplomacySubcommand(String value) {
        return StarCoreCommandAliases.normalizeDiplomacySubcommand(value);
    }

    private String normalizeTreasurySubcommand(String value) {
        return StarCoreCommandAliases.normalizeTreasurySubcommand(value);
    }

    private String normalizePolicySubcommand(String value) {
        return StarCoreCommandAliases.normalizePolicySubcommand(value);
    }

    private String normalizeResourceSubcommand(String value) {
        return StarCoreCommandAliases.normalizeResourceSubcommand(value);
    }

    private String normalizeTechnologySubcommand(String value) {
        return StarCoreCommandAliases.normalizeTechnologySubcommand(value);
    }

    private String normalizeWarSubcommand(String value) {
        return StarCoreCommandAliases.normalizeWarSubcommand(value);
    }

    private String normalizeOfficerSubcommand(String value) {
        return StarCoreCommandAliases.normalizeOfficerSubcommand(value);
    }

    private Optional<GovernmentType> parseGovernmentType(String value) {
        return switch (normalizeToken(value)) {
            case "君主", "君主制", "monarchy" -> Optional.of(GovernmentType.MONARCHY);
            case "独裁", "独裁制", "dictatorship" -> Optional.of(GovernmentType.DICTATORSHIP);
            case "共和", "共和制", "republic" -> Optional.of(GovernmentType.REPUBLIC);
            case "民主", "民主制", "democracy" -> Optional.of(GovernmentType.DEMOCRACY);
            default -> {
                try {
                    yield Optional.of(GovernmentType.valueOf(normalizeToken(value).toUpperCase(Locale.ROOT)));
                } catch (RuntimeException ignored) {
                    yield Optional.empty();
                }
            }
        };
    }

    private Optional<DiplomacyRelation> parseDiplomacyRelation(String value) {
        return switch (normalizeToken(value)) {
            case "中立", "neutral" -> Optional.of(DiplomacyRelation.NEUTRAL);
            case "友好", "friendly" -> Optional.of(DiplomacyRelation.FRIENDLY);
            case "盟友", "同盟", "allied" -> Optional.of(DiplomacyRelation.ALLIED);
            case "敌对", "hostile" -> Optional.of(DiplomacyRelation.HOSTILE);
            case "战争", "war" -> Optional.of(DiplomacyRelation.WAR);
            case "附庸", "vassal" -> Optional.of(DiplomacyRelation.VASSAL);
            default -> {
                try {
                    yield Optional.of(DiplomacyRelation.valueOf(normalizeToken(value).toUpperCase(Locale.ROOT)));
                } catch (RuntimeException ignored) {
                    yield Optional.empty();
                }
            }
        };
    }

    private Optional<NationId> parseNationId(String value) {
        try {
            return Optional.of(new NationId(UUID.fromString(value)));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private List<String> diplomacyRelationSuggestions() {
        return merged(Arrays.stream(DiplomacyRelation.values()).map(type -> type.name().toLowerCase(Locale.ROOT)).toList(), List.of("中立", "友好", "盟友", "敌对", "战争", "附庸"));
    }

    private String normalizeSimple(String value) {
        return StarCoreCommandAliases.normalizeSimple(value);
    }

    private String normalizeToken(String value) {
        return StarCoreCommandAliases.normalizeToken(value);
    }

    private void handleDebug(CommandSender sender, String[] args) {
        debugCommands.handle(sender, Arrays.copyOfRange(args, 1, args.length));
    }
}
