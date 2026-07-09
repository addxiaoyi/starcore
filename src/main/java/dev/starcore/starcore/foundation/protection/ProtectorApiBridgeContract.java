package dev.starcore.starcore.foundation.protection;

import org.bukkit.Location;

import java.lang.reflect.Method;
import java.util.Objects;

final class ProtectorApiBridgeContract {
    static final String PLUGIN_NAME = "ProtectorAPI";
    static final String API_CLASS_NAME = "io.github.lijinhong11.protectorapi.ProtectorAPI";
    static final String MODULE_CLASS_NAME = "io.github.lijinhong11.protectorapi.protection.IProtectionModule";
    static final String RANGE_CLASS_NAME = "io.github.lijinhong11.protectorapi.protection.IProtectionRange";

    private ProtectorApiBridgeContract() {
    }

    static Binding bind(ClassLoader loader) throws ReflectiveOperationException {
        Objects.requireNonNull(loader, "loader");
        Class<?> apiClass = Class.forName(API_CLASS_NAME, true, loader);
        Class<?> moduleClass = Class.forName(MODULE_CLASS_NAME, true, loader);
        Class<?> rangeClass = Class.forName(RANGE_CLASS_NAME, true, loader);
        return new Binding(
            requiredMethod(apiClass, "findModule", Location.class),
            requiredMethod(apiClass, "getAllAvailableProtectionModules"),
            requiredMethod(moduleClass, "getPluginName"),
            requiredMethod(moduleClass, "getProtectionRangeInfo", Location.class),
            requiredMethod(rangeClass, "getDisplayName"),
            requiredMethod(rangeClass, "getId")
        );
    }

    private static Method requiredMethod(Class<?> owner, String name, Class<?>... parameters) throws NoSuchMethodException {
        return owner.getMethod(name, parameters);
    }

    record Binding(
        Method findModule,
        Method getAllAvailableProtectionModules,
        Method getPluginName,
        Method getProtectionRangeInfo,
        Method getDisplayName,
        Method getId
    ) {
    }
}
