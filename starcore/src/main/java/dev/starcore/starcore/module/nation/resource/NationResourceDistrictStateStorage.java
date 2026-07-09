package dev.starcore.starcore.module.nation.resource;

import java.util.Properties;

interface NationResourceDistrictStateStorage {
    Properties load();

    void save(Properties properties);

    default void saveAsync(Properties properties) {
        save(properties);
    }
}
