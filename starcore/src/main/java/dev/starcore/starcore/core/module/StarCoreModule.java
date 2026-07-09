package dev.starcore.starcore.core.module;

import dev.starcore.starcore.core.StarCoreContext;

public interface StarCoreModule {
    ModuleMetadata metadata();

    default void enable(StarCoreContext context) {
    }

    default void disable(StarCoreContext context) {
    }
}
