package dev.starcore.starcore.module.resolution.model;

import dev.starcore.starcore.module.government.GovernmentService;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.NationId;

public interface ResolutionAction {
    ResolutionKind kind();

    NationId nationId();

    String summary();

    boolean execute(NationService nationService, GovernmentService governmentService, DiplomacyService diplomacyService);
}
