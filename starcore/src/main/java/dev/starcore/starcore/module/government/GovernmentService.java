package dev.starcore.starcore.module.government;

import dev.starcore.starcore.module.government.model.GovernmentType;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.resolution.model.Resolution;
import dev.starcore.starcore.module.resolution.model.ResolutionAction;

import java.util.UUID;

public interface GovernmentService {
    GovernmentType governmentOf(Nation nation);

    boolean setGovernment(NationId nationId, GovernmentType governmentType);

    boolean mayPropose(Nation nation, UUID proposerId, ResolutionAction action);

    boolean maySign(Nation nation, UUID signerId, Resolution resolution);

    boolean resolutionPasses(Nation nation, Resolution resolution);

    /**
     * Get the number of signatures required for a resolution to pass.
     */
    int requiredSignatures(Nation nation, Resolution resolution);

    String summary();
}
