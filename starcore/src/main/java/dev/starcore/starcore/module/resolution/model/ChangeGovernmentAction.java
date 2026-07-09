package dev.starcore.starcore.module.resolution.model;

import dev.starcore.starcore.module.government.GovernmentService;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.government.model.GovernmentType;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.NationId;

import java.util.Objects;

public record ChangeGovernmentAction(NationId nationId, GovernmentType from, GovernmentType to) implements ResolutionAction {
    public ChangeGovernmentAction {
        Objects.requireNonNull(nationId, "nationId");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
    }

    @Override
    public ResolutionKind kind() {
        return ResolutionKind.CHANGE_GOVERNMENT;
    }

    @Override
    public String summary() {
        return "Change government " + from.name() + " -> " + to.name();
    }

    @Override
    public boolean execute(NationService nationService, GovernmentService governmentService, DiplomacyService diplomacyService) {
        return governmentService.setGovernment(nationId, to);
    }
}
