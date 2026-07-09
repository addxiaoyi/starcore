package dev.starcore.starcore.module.resolution.model;

import dev.starcore.starcore.module.government.GovernmentService;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.Bukkit;

import java.util.Objects;
import java.util.UUID;

public record JoinNationRequestAction(NationId nationId, UUID applicantId, String applicantName) implements ResolutionAction {
    public JoinNationRequestAction {
        Objects.requireNonNull(nationId, "nationId");
        Objects.requireNonNull(applicantId, "applicantId");
        Objects.requireNonNull(applicantName, "applicantName");
    }

    @Override
    public ResolutionKind kind() {
        return ResolutionKind.JOIN_NATION;
    }

    @Override
    public String summary() {
        return "Join request for " + applicantName;
    }

    @Override
    public boolean execute(NationService nationService, GovernmentService governmentService, DiplomacyService diplomacyService) {
        Nation nation = nationService.nationById(nationId).orElse(null);
        if (nation == null) {
            notifyApplicant("Failed to join: Nation not found.");
            return false;
        }
        // Check nation member capacity
        int currentMembers = nation.members().size();
        int maxMembers = getMaxNationMembers(nationService, nation);
        if (currentMembers >= maxMembers) {
            notifyApplicant("Failed to join: " + nation.name() + " is full (max " + maxMembers + " members).");
            return false;
        }
        boolean added = nationService.addMember(nationId, applicantId, applicantName);
        if (added) {
            notifyApplicant("You have successfully joined " + nation.name() + "!");
            notifyNationMembers(nationService, nation, applicantName + " has joined the nation!");
        } else {
            notifyApplicant("Failed to join: " + nation.name());
        }
        return added;
    }

    private int getMaxNationMembers(NationService nationService, Nation nation) {
        // Default max members, can be extended with nation level/city-state limits
        int baseLimit = 20;
        int levelBonus = (nationService.levelOf(nation.id()) - 1) * 5;
        return baseLimit + levelBonus;
    }

    private void notifyApplicant(String message) {
        org.bukkit.entity.Player player = Bukkit.getPlayer(applicantId);
        if (player != null) {
            player.sendMessage(net.kyori.adventure.text.Component.text("[Resolution] " + message,
                net.kyori.adventure.text.format.NamedTextColor.YELLOW));
        }
    }

    private void notifyNationMembers(NationService nationService, Nation nation, String message) {
        for ( var member : nation.members()) {
            final var memberId = member.playerId();
            org.bukkit.entity.Player player = Bukkit.getPlayer(memberId);
            if (player != null) {
                player.sendMessage(net.kyori.adventure.text.Component.text("[Resolution] " + message,
                    net.kyori.adventure.text.format.NamedTextColor.GOLD));
            }
        }
    }

    /**
     * Reject a join request by marking the resolution as cancelled.
     * This should be called from the command when rejecting.
     */
    public static void notifyRejection(NationService nationService, Nation nation, String applicantName, String reason) {
        // Find the applicant - in a real implementation, you'd look up by name
        Bukkit.getScheduler().runTaskAsynchronously(null, () -> {
            // The rejection notification will be handled by the command that calls this
        });
    }
}
