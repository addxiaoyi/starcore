package dev.starcore.starcore.module.policy;

import java.util.Properties;

interface PolicyStateStorage {
    Properties load();

    void save(Properties properties);

    default void saveAsync(Properties properties) {
        save(properties);
    }
}
