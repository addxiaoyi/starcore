package dev.starcore.starcore.module.war;

import java.util.Properties;

interface WarStateStorage {
    Properties load();

    void save(Properties properties);

    default void saveAsync(Properties properties) {
        save(properties);
    }
}
