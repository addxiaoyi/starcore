package dev.starcore.starcore.module.resource;

import dev.starcore.starcore.module.nation.model.NationId;

import java.util.Collection;
import java.util.Map;

public interface ResourceService {
    Collection<String> availableResourceTypes();

    Map<String, Long> stockpile(NationId nationId);

    long amount(NationId nationId, String resourceType);

    boolean grant(NationId nationId, String resourceType, long amount);

    boolean consume(NationId nationId, String resourceType, long amount);

    String summary();
}
