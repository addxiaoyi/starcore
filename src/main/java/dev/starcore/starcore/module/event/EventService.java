package dev.starcore.starcore.module.event;

import dev.starcore.starcore.module.nation.model.NationId;

import java.util.Collection;

public interface EventService {
    NationEventRecord record(NationId nationId, String type, String message);

    NationEventRecord record(NationId nationId, String type, String message, String context);

    Collection<NationEventRecord> eventsOf(NationId nationId);

    boolean clear(NationId nationId);

    String summary();
}
