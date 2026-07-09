package dev.starcore.starcore.core.service;

import dev.starcore.starcore.core.StarCoreContext;

public interface StarCoreService {
    default void start(StarCoreContext context) {
    }

    default void stop(StarCoreContext context) {
    }
}
