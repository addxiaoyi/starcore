package dev.starcore.starcore.module.resolution;

import java.util.Properties;

interface ResolutionStateStorage {
    Properties load();

    void save(Properties properties);

    default void saveAsync(Properties properties) {
        save(properties);
    }
}
