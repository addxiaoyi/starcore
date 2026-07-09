package dev.starcore.starcore.module.nation.resource;

import dev.starcore.starcore.core.config.ConfigurationService;
import dev.starcore.starcore.foundation.economy.InternalEconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.ClaimSelectionExplanation;
import dev.starcore.starcore.module.nation.model.ClaimSelectionReason;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.officer.OfficerService;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class NationResourceDistrictCommandSupport {
    private NationResourceDistrictCommandSupport() {
    }

    public static CommandState resolve(
        ConfigurationService configuration,
        InternalEconomyService economyService,
        NationService nationService,
        OnlinePlayerDirectory onlinePlayerDirectory,
        UUID viewerId,
        NationResourceDistrictSnapshot district,
        String fallbackActionState
    ) {
        return resolve(
            configuration,
            economyService,
            nationService,
            null,
            onlinePlayerDirectory,
            viewerId,
            district,
            fallbackActionState
        );
    }

    public static CommandState resolve(
        ConfigurationService configuration,
        InternalEconomyService economyService,
        NationService nationService,
        OfficerService officerService,
        OnlinePlayerDirectory onlinePlayerDirectory,
        UUID viewerId,
        NationResourceDistrictSnapshot district,
        String fallbackActionState
    ) {
        String districtNationId = district == null || district.nationId() == null ? null : district.nationId().toString();
        String migrationState = district == null ? null : district.migrationState();
        return resolve(
            configuration,
            economyService,
            nationService,
            officerService,
            onlinePlayerDirectory,
            viewerId,
            districtNationId,
            migrationState,
            fallbackActionState
        );
    }

    public static CommandState resolve(
        ConfigurationService configuration,
        InternalEconomyService economyService,
        NationService nationService,
        OnlinePlayerDirectory onlinePlayerDirectory,
        UUID viewerId,
        String districtNationId,
        String migrationState,
        String fallbackActionState
    ) {
        return resolve(
            configuration,
            economyService,
            nationService,
            null,
            onlinePlayerDirectory,
            viewerId,
            districtNationId,
            migrationState,
            fallbackActionState
        );
    }

    public static CommandState resolve(
        ConfigurationService configuration,
        InternalEconomyService economyService,
        NationService nationService,
        OfficerService officerService,
        OnlinePlayerDirectory onlinePlayerDirectory,
        UUID viewerId,
        String districtNationId,
        String migrationState,
        String fallbackActionState
    ) {
        BigDecimal viewerBalance = viewerId == null || economyService == null ? BigDecimal.ZERO : economyService.balance(viewerId);
        BigDecimal migrationCost = configuration == null ? BigDecimal.ZERO : configuration.nationResourceMigrationCost();
        Nation viewerNation = viewerId == null || nationService == null ? null : nationService.nationOf(viewerId).orElse(null);
        String viewerNationId = viewerNation == null ? null : viewerNation.id().toString();
        boolean viewerIsNationLeader = viewerNation != null && viewerNation.founderId().equals(viewerId);
        String authorizedOfficerRole = authorizedOfficerRole(
            officerService,
            viewerNation,
            viewerId,
            configuration == null ? List.of() : configuration.nationResourceMigrationOfficerRoles()
        );
        boolean viewerCanManageMigration = viewerIsNationLeader || !authorizedOfficerRole.isBlank();
        boolean viewerOnline = viewerId != null
            && onlinePlayerDirectory != null
            && onlinePlayerDirectory.findOnlinePlayer(viewerId).map(Player::isOnline).orElse(false);
        return evaluate(
            viewerBalance,
            migrationCost,
            viewerNationId,
            viewerIsNationLeader,
            viewerCanManageMigration,
            authorizedOfficerRole,
            viewerOnline,
            districtNationId,
            migrationState,
            fallbackActionState
        );
    }

    public static CommandState evaluate(
        BigDecimal viewerBalance,
        BigDecimal migrationCost,
        String viewerNationId,
        boolean viewerIsNationLeader,
        boolean viewerOnline,
        String districtNationId,
        String migrationState,
        String fallbackActionState
    ) {
        return evaluate(
            viewerBalance,
            migrationCost,
            viewerNationId,
            viewerIsNationLeader,
            viewerIsNationLeader,
            "",
            viewerOnline,
            districtNationId,
            migrationState,
            fallbackActionState
        );
    }

    public static CommandState evaluate(
        BigDecimal viewerBalance,
        BigDecimal migrationCost,
        String viewerNationId,
        boolean viewerIsNationLeader,
        boolean viewerCanManageMigration,
        String viewerAuthorizedOfficerRole,
        boolean viewerOnline,
        String districtNationId,
        String migrationState,
        String fallbackActionState
    ) {
        BigDecimal safeViewerBalance = viewerBalance == null ? BigDecimal.ZERO : viewerBalance;
        BigDecimal safeMigrationCost = migrationCost == null ? BigDecimal.ZERO : migrationCost;
        boolean viewerOwnsDistrictNation = viewerNationId != null
            && !viewerNationId.isBlank()
            && districtNationId != null
            && !districtNationId.isBlank()
            && viewerNationId.equals(districtNationId);
        boolean migrationCostRequired = "none".equalsIgnoreCase(String.valueOf(migrationState));
        BigDecimal migrationBalanceShortfall = migrationCostRequired
            ? safeMigrationCost.subtract(safeViewerBalance).max(BigDecimal.ZERO)
            : BigDecimal.ZERO;
        boolean viewerCanAffordMigration = !migrationCostRequired || safeViewerBalance.compareTo(safeMigrationCost) >= 0;
        String actionState = actionState(
            districtNationId,
            migrationState,
            viewerOwnsDistrictNation,
            viewerCanManageMigration,
            viewerOnline,
            viewerCanAffordMigration,
            fallbackActionState
        );
        return new CommandState(
            safeViewerBalance,
            safeMigrationCost,
            migrationCostRequired,
            migrationBalanceShortfall,
            viewerOwnsDistrictNation,
            viewerIsNationLeader,
            viewerCanManageMigration,
            viewerAuthorizedOfficerRole,
            viewerOnline,
            viewerCanAffordMigration,
            "ready".equals(actionState),
            actionState
        );
    }

    public static CommandPresentation presentation(MessageService messages, CommandState state) {
        MessageService messageService = Objects.requireNonNull(messages, "messages");
        CommandState commandState = Objects.requireNonNull(state, "state");
        return switch (normalizeState(commandState.migrationActionState())) {
            case "district-not-found" -> new CommandPresentation(
                messageService.format("resource.district.menu.operation.stage-blocked"),
                messageService.format("resource.district.menu.operation.next-step-check-state"),
                messageService.format("resource.district.menu.operation.restriction-district-missing"),
                messageService.format("resource.district.menu.operation.restriction-district-missing"),
                affordLabel(messageService, commandState.viewerCanAffordMigration())
            );
            case "not-own-nation" -> new CommandPresentation(
                messageService.format("resource.district.menu.operation.stage-blocked"),
                messageService.format("resource.district.menu.operation.next-step-own-district"),
                messageService.format("resource.district.menu.operation.restriction-need-own-nation"),
                messageService.format("resource.district.menu.operation.restriction-need-own-nation"),
                affordLabel(messageService, commandState.viewerCanAffordMigration())
            );
            case "leader-only" -> new CommandPresentation(
                messageService.format("resource.district.menu.operation.stage-blocked"),
                messageService.format("resource.district.menu.operation.next-step-ask-leader"),
                messageService.format("resource.district.menu.operation.restriction-need-leader"),
                messageService.format("resource.district.menu.operation.restriction-need-leader"),
                affordLabel(messageService, commandState.viewerCanAffordMigration())
            );
            case "player-offline" -> new CommandPresentation(
                messageService.format("resource.district.menu.operation.stage-blocked"),
                messageService.format("resource.district.menu.operation.next-step-go-online"),
                messageService.format("resource.district.menu.operation.restriction-online-only"),
                messageService.format("resource.district.menu.operation.restriction-online-only"),
                affordLabel(messageService, commandState.viewerCanAffordMigration())
            );
            case "awaiting-target" -> new CommandPresentation(
                messageService.format("resource.district.menu.operation.stage-awaiting-target"),
                messageService.format("resource.district.menu.operation.next-step-select-target"),
                messageService.format("resource.district.menu.operation.restriction-none"),
                messageService.format("resource.district.menu.operation.restriction-none"),
                affordLabel(messageService, commandState.viewerCanAffordMigration())
            );
            case "waiting-depletion" -> new CommandPresentation(
                messageService.format("resource.district.menu.operation.stage-waiting-depletion"),
                messageService.format("resource.district.menu.operation.next-step-wait-depletion"),
                messageService.format("resource.district.menu.operation.restriction-none"),
                messageService.format("resource.district.menu.operation.restriction-none"),
                affordLabel(messageService, commandState.viewerCanAffordMigration())
            );
            case "insufficient-balance" -> new CommandPresentation(
                messageService.format("resource.district.menu.operation.stage-blocked"),
                messageService.format("resource.district.menu.operation.next-step-top-up"),
                messageService.format("resource.district.menu.operation.restriction-need-balance"),
                messageService.format(
                    "resource.district.menu.operation.restriction-with-shortfall",
                    messageService.format("resource.district.menu.operation.restriction-need-balance"),
                    commandState.migrationBalanceShortfall().toPlainString()
                ),
                affordLabel(messageService, false)
            );
            case "ready" -> new CommandPresentation(
                messageService.format("resource.district.menu.operation.stage-ready"),
                messageService.format("resource.district.menu.operation.next-step-ready"),
                messageService.format("resource.district.menu.operation.restriction-none"),
                messageService.format("resource.district.menu.operation.restriction-none"),
                affordLabel(messageService, true)
            );
            default -> new CommandPresentation(
                messageService.format("resource.district.menu.operation.stage-unknown"),
                messageService.format("resource.district.menu.operation.next-step-check-state"),
                messageService.format("resource.district.menu.operation.restriction-unknown"),
                messageService.format("resource.district.menu.operation.restriction-unknown"),
                affordLabel(messageService, commandState.viewerCanAffordMigration())
            );
        };
    }

    public static ClaimSelectionExplanation explanation(MessageService messages, CommandState state) {
        return explanation(state, presentation(messages, state));
    }

    public static ClaimSelectionExplanation explanation(CommandState state, CommandPresentation presentation) {
        if (state == null) {
            return ClaimSelectionExplanation.basic(false, "");
        }
        CommandPresentation safePresentation = presentation == null
            ? new CommandPresentation("", "", "", "", "")
            : presentation;
        String actionState = normalizeActionState(state.migrationActionState());
        String summary = summary(safePresentation);
        String reasonMessage = primaryReasonMessage(actionState, safePresentation);
        Map<String, String> details = new LinkedHashMap<>();
        details.put("stage", safePresentation.stage());
        details.put("nextStep", safePresentation.nextStep());
        details.put("restriction", safePresentation.restriction());
        details.put("restrictionDetail", safePresentation.restrictionDetail());
        details.put("affordLabel", safePresentation.affordLabel());
        details.put("viewerBalance", state.viewerBalance().toPlainString());
        details.put("migrationCost", state.migrationCost().toPlainString());
        details.put("migrationCostRequired", String.valueOf(state.migrationCostRequired()));
        details.put("migrationBalanceShortfall", state.migrationBalanceShortfall().toPlainString());
        details.put("viewerOwnsDistrictNation", String.valueOf(state.viewerOwnsDistrictNation()));
        details.put("viewerIsNationLeader", String.valueOf(state.viewerIsNationLeader()));
        details.put("viewerCanManageMigration", String.valueOf(state.viewerCanManageMigration()));
        details.put("viewerAuthorizedOfficerRole", state.viewerAuthorizedOfficerRole());
        details.put("viewerOnline", String.valueOf(state.viewerOnline()));
        details.put("viewerCanAffordMigration", String.valueOf(state.viewerCanAffordMigration()));
        details.put("canStartMigration", String.valueOf(state.canStartMigration()));
        return ClaimSelectionExplanation.of(
            actionState,
            severity(actionState),
            summary,
            List.of(ClaimSelectionReason.of(actionState, reasonMessage, details))
        );
    }

    public static String actionStateForResultCode(String code) {
        if (code == null || code.isBlank()) {
            return "error";
        }
        return switch (code) {
            case "migration-started" -> "awaiting-target";
            case "district-not-found", "not-found" -> "district-not-found";
            case "leader-only" -> "leader-only";
            case "player-offline" -> "player-offline";
            case "insufficient-balance" -> "insufficient-balance";
            case "already-waiting" -> "waiting-depletion";
            default -> "error";
        };
    }

    private static String actionState(
        String districtNationId,
        String migrationState,
        boolean viewerOwnsDistrictNation,
        boolean viewerCanManageMigration,
        boolean viewerOnline,
        boolean viewerCanAffordMigration,
        String fallbackActionState
    ) {
        if (districtNationId == null || districtNationId.isBlank()) {
            return fallbackActionState == null || fallbackActionState.isBlank() ? "district-not-found" : fallbackActionState;
        }
        if (!viewerOwnsDistrictNation) {
            return "not-own-nation";
        }
        if (!viewerCanManageMigration) {
            return "leader-only";
        }
        if (!viewerOnline) {
            return "player-offline";
        }
        String normalizedMigrationState = normalizeState(migrationState);
        if ("awaiting_target".equals(normalizedMigrationState)) {
            return "awaiting-target";
        }
        if ("waiting_depletion".equals(normalizedMigrationState)) {
            return "waiting-depletion";
        }
        if (!viewerCanAffordMigration) {
            return "insufficient-balance";
        }
        return "ready";
    }

    private static String affordLabel(MessageService messages, boolean canAfford) {
        return messages.format(canAfford
            ? "resource.district.menu.operation.afford-yes"
            : "resource.district.menu.operation.afford-no");
    }

    private static String normalizeState(String migrationState) {
        return migrationState == null ? "" : migrationState.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeActionState(String actionState) {
        String normalized = normalizeState(actionState);
        return normalized.isBlank() ? "unknown" : normalized;
    }

    private static String severity(String actionState) {
        return switch (actionState) {
            case "ready" -> "success";
            case "awaiting-target", "waiting-depletion" -> "info";
            case "district-not-found", "not-own-nation", "leader-only", "player-offline", "insufficient-balance" -> "error";
            default -> "warning";
        };
    }

    private static String summary(CommandPresentation presentation) {
        if (presentation.stage() == null || presentation.stage().isBlank()) {
            return firstNonBlank(presentation.nextStep(), presentation.restrictionDetail(), presentation.restriction());
        }
        String nextStep = presentation.nextStep();
        if (nextStep == null || nextStep.isBlank()) {
            return presentation.stage();
        }
        return presentation.stage() + " - " + nextStep;
    }

    private static String primaryReasonMessage(String actionState, CommandPresentation presentation) {
        return switch (actionState) {
            case "ready", "awaiting-target", "waiting-depletion" ->
                firstNonBlank(presentation.nextStep(), presentation.restrictionDetail(), presentation.stage());
            default -> firstNonBlank(presentation.restrictionDetail(), presentation.nextStep(), presentation.stage());
        };
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String authorizedOfficerRole(OfficerService officerService, Nation viewerNation, UUID viewerId, List<String> allowedRoles) {
        if (officerService == null || viewerNation == null || viewerId == null || allowedRoles == null || allowedRoles.isEmpty()) {
            return "";
        }
        for (String role : allowedRoles) {
            String normalized = normalizeState(role).replace('_', '-');
            if (normalized.isBlank()) {
                continue;
            }
            boolean appointed = officerService.officer(viewerNation.id(), normalized)
                .map(appointment -> viewerId.equals(appointment.playerId()))
                .orElse(false);
            if (appointed) {
                return normalized;
            }
        }
        return "";
    }

    public record CommandState(
        BigDecimal viewerBalance,
        BigDecimal migrationCost,
        boolean migrationCostRequired,
        BigDecimal migrationBalanceShortfall,
        boolean viewerOwnsDistrictNation,
        boolean viewerIsNationLeader,
        boolean viewerCanManageMigration,
        String viewerAuthorizedOfficerRole,
        boolean viewerOnline,
        boolean viewerCanAffordMigration,
        boolean canStartMigration,
        String migrationActionState
    ) {
        public CommandState(
            BigDecimal viewerBalance,
            BigDecimal migrationCost,
            boolean migrationCostRequired,
            BigDecimal migrationBalanceShortfall,
            boolean viewerOwnsDistrictNation,
            boolean viewerIsNationLeader,
            boolean viewerOnline,
            boolean viewerCanAffordMigration,
            boolean canStartMigration,
            String migrationActionState
        ) {
            this(
                viewerBalance,
                migrationCost,
                migrationCostRequired,
                migrationBalanceShortfall,
                viewerOwnsDistrictNation,
                viewerIsNationLeader,
                viewerIsNationLeader,
                "",
                viewerOnline,
                viewerCanAffordMigration,
                canStartMigration,
                migrationActionState
            );
        }

        public CommandState {
            viewerAuthorizedOfficerRole = viewerAuthorizedOfficerRole == null
                ? ""
                : viewerAuthorizedOfficerRole.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    public record CommandPresentation(
        String stage,
        String nextStep,
        String restriction,
        String restrictionDetail,
        String affordLabel
    ) {
    }
}
