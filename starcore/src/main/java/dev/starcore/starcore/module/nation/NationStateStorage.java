package dev.starcore.starcore.module.nation;

import java.util.Properties;

interface NationStateStorage {
    Properties load();

    void save(Properties properties);

    default void saveAsync(Properties properties) {
        save(properties);
    }
}
