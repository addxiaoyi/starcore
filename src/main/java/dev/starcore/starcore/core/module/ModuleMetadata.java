package dev.starcore.starcore.core.module;

import java.util.List;

public record ModuleMetadata(
    String id,
    String displayName,
    ModuleLayer layer,
    List<String> dependencies,
    List<Class<?>> providedServices,
    String description
) {
    public ModuleMetadata {
        dependencies = List.copyOf(dependencies);
        providedServices = List.copyOf(providedServices);
    }
}
