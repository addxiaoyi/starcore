package dev.starcore.starcore.module.war;

import dev.starcore.starcore.module.nation.model.NationId;

import java.util.Collection;

public interface WarService {
    boolean declareWar(NationId left, NationId right);

    boolean endWar(NationId left, NationId right);

    boolean atWar(NationId left, NationId right);

    Collection<WarSnapshot> activeWars();

    Collection<WarSnapshot> activeWarsOf(NationId nationId);

    String summary();
}
