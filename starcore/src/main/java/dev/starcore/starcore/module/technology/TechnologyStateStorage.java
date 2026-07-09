package dev.starcore.starcore.module.technology;

import java.util.Properties;

interface TechnologyStateStorage {
    Properties load();

    void save(Properties properties);

    default void saveAsync(Properties properties) {
        save(properties);
    }
}
