package dev.starcore.starcore.module.officer;

import java.util.Properties;

interface OfficerStateStorage {
    Properties load();

    void save(Properties properties);

    default void saveAsync(Properties properties) {
        save(properties);
    }
}
