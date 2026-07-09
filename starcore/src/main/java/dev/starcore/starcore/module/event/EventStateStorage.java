package dev.starcore.starcore.module.event;

import java.util.Properties;

interface EventStateStorage {
    Properties load();

    void save(Properties properties);

    default void saveAsync(Properties properties) {
        save(properties);
    }
}
