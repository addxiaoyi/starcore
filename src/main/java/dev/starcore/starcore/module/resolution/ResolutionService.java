package dev.starcore.starcore.module.resolution;

import dev.starcore.starcore.module.government.model.GovernmentType;
import dev.starcore.starcore.module.diplomacy.DiplomacyRelation;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.resolution.model.Resolution;
import dev.starcore.starcore.module.resolution.model.ResolutionState;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResolutionService {
    Resolution proposeJoin(Nation nation, UUID proposerId, String proposerName, UUID applicantId, String applicantName);

    Resolution proposeRename(Nation nation, UUID proposerId, String proposerName, String newName);

    Resolution proposeGovernmentChange(Nation nation, UUID proposerId, String proposerName, GovernmentType targetType);

    Resolution proposeDiplomacyChange(Nation nation, UUID proposerId, String proposerName, Nation targetNation, DiplomacyRelation relation);

    Optional<Resolution> find(UUID resolutionId);

    Collection<Resolution> openResolutions(Nation nation);

    /**
     * Sign a resolution. Returns true if the signature was added.
     * The resolution may still be open after signing if it has not passed yet.
     */
    boolean sign(UUID signerId, UUID resolutionId);

    /**
     * Cancel/rescind a resolution. Only the proposer or admins can cancel.
     * Returns true if the resolution was successfully cancelled.
     */
    boolean cancel(UUID resolutionId, UUID cancellerId);

    /**
     * Get completed/enacted/expired/failed resolutions for a nation.
     */
    List<Resolution> history(Nation nation);

    /**
     * Get resolutions proposed by a specific player.
     */
    List<Resolution> proposedBy(UUID playerId);

    /**
     * Get resolutions signed by a specific player.
     */
    List<Resolution> signedBy(UUID playerId);

    /**
     * Get details string for a resolution.
     */
    String details(UUID resolutionId);

    String summary();
}
