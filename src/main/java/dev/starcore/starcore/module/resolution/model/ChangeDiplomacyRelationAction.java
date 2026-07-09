package dev.starcore.starcore.module.resolution.model;

import dev.starcore.starcore.module.diplomacy.DiplomacyRelation;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.government.GovernmentService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.NationId;

import java.util.Objects;

public record ChangeDiplomacyRelationAction(
    NationId nationId,
    NationId targetNationId,
    String targetNationName,
    DiplomacyRelation relation
) implements ResolutionAction {
    public ChangeDiplomacyRelationAction {
        Objects.requireNonNull(nationId, "nationId");
        Objects.requireNonNull(targetNationId, "targetNationId");
        Objects.requireNonNull(targetNationName, "targetNationName");
        Objects.requireNonNull(relation, "relation");
    }

    @Override
    public ResolutionKind kind() {
        return ResolutionKind.CHANGE_DIPLOMACY_RELATION;
    }

    @Override
    public String summary() {
        return "Change diplomacy with " + targetNationName + " -> " + relation.name();
    }

    @Override
    public boolean execute(NationService nationService, GovernmentService governmentService, DiplomacyService diplomacyService) {
        if (diplomacyService == null || nationService.nationById(targetNationId).isEmpty()) {
            return false;
        }
        diplomacyService.setRelation(nationId, targetNationId, relation);
        return true;
    }
}
