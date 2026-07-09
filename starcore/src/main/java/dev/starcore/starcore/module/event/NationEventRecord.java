package dev.starcore.starcore.module.event;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record NationEventRecord(
    UUID id,
    NationId nationId,
    Instant occurredAt,
    String type,
    String message,
    String context
) {
    public static List<NationEventRecord> newestFirst(Collection<NationEventRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<NationEventRecord> ordered = new ArrayList<>(records);
        ordered.sort(Comparator.comparing(NationEventRecord::occurredAt));
        Collections.reverse(ordered);
        return List.copyOf(ordered);
    }
}
