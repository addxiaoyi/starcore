package dev.starcore.starcore.module.technology;

import java.math.BigDecimal;
import java.util.Map;

public record TechnologyCost(
    BigDecimal treasury,
    Map<String, Long> resources
) {
    public TechnologyCost {
        resources = Map.copyOf(resources);
    }
}
