package dev.starcore.starcore.module.resource;

import java.util.Properties;

interface ResourceStateStorage {
    Properties load();

    void save(Properties properties);

    default void saveAsync(Properties properties) {
        save(properties);
    }
}
