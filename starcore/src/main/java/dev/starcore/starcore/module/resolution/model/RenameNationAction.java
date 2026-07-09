package dev.starcore.starcore.module.resolution.model;

import dev.starcore.starcore.module.government.GovernmentService;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.NationId;

import java.util.Objects;

public record RenameNationAction(NationId nationId, String oldName, String newName) implements ResolutionAction {
    public RenameNationAction {
        Objects.requireNonNull(nationId, "nationId");
        Objects.requireNonNull(oldName, "oldName");
        Objects.requireNonNull(newName, "newName");
    }

    @Override
    public ResolutionKind kind() {
        return ResolutionKind.RENAME_NATION;
    }

    @Override
    public String summary() {
        return "Rename nation " + oldName + " -> " + newName;
    }

    @Override
    public boolean execute(NationService nationService, GovernmentService governmentService, DiplomacyService diplomacyService) {
        return nationService.renameNation(nationId, newName);
    }
}
