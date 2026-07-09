package dev.starcore.starcore.core.module;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.service.ServiceRegistry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ModuleManager {
    private final Logger logger;
    private final ServiceRegistry serviceRegistry;
    private final Map<String, StarCoreModule> modules = new LinkedHashMap<>();
    private final Map<String, ModuleDescriptor> descriptors = new LinkedHashMap<>();

    public ModuleManager(Logger logger, ServiceRegistry serviceRegistry) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.serviceRegistry = Objects.requireNonNull(serviceRegistry, "serviceRegistry");
    }

    public void register(StarCoreModule module) {
        Objects.requireNonNull(module, "module");
        ModuleMetadata metadata = module.metadata();
        if (modules.containsKey(metadata.id())) {
            throw new ModuleException("Duplicate module id: " + metadata.id());
        }
        modules.put(metadata.id(), module);
        descriptors.put(metadata.id(), new ModuleDescriptor(metadata, ModuleStatus.REGISTERED, Instant.now(), Instant.now(), null));
    }

    public Collection<StarCoreModule> modules() {
        return Collections.unmodifiableCollection(modules.values());
    }

    public Collection<ModuleDescriptor> descriptors() {
        return Collections.unmodifiableCollection(descriptors.values());
    }

    public ServiceRegistry serviceRegistry() {
        return serviceRegistry;
    }

    public void enableAll(StarCoreContext context) {
        for (StarCoreModule module : sortedModules()) {
            enableModule(module, context);
        }
    }

    public void disableAll(StarCoreContext context) {
        List<StarCoreModule> sorted = sortedModules();
        for (int index = sorted.size() - 1; index >= 0; index--) {
            StarCoreModule module = sorted.get(index);
            try {
                module.disable(context);
                updateStatus(module.metadata().id(), ModuleStatus.DISABLED, null);
                logger.info("Disabled STARCORE module: " + module.metadata().displayName() + " (" + module.metadata().id() + ")");
            } catch (RuntimeException exception) {
                updateStatus(module.metadata().id(), ModuleStatus.FAILED, exception.getMessage());
                logger.log(Level.WARNING, "Failed to disable STARCORE module: " + module.metadata().id(), exception);
            }
        }
    }

    private void enableModule(StarCoreModule module, StarCoreContext context) {
        ModuleMetadata metadata = module.metadata();
        try {
            for (String dependency : metadata.dependencies()) {
                ModuleDescriptor dependencyDescriptor = descriptors.get(dependency);
                if (dependencyDescriptor == null) {
                    throw new ModuleException("Module '" + metadata.id() + "' depends on missing module '" + dependency + "'");
                }
                if (dependencyDescriptor.status() != ModuleStatus.ENABLED) {
                    throw new ModuleException("Module '" + metadata.id() + "' depends on module '" + dependency + "' which is not enabled");
                }
            }
            module.enable(context);
            registerProvidedServices(metadata, module);
            updateStatus(metadata.id(), ModuleStatus.ENABLED, null);
            logger.info("Enabled STARCORE module: " + metadata.displayName() + " (" + metadata.id() + ")");
        } catch (RuntimeException exception) {
            updateStatus(metadata.id(), ModuleStatus.FAILED, exception.getMessage());
            logger.log(Level.SEVERE, "Failed to enable STARCORE module: " + metadata.id(), exception);
        }
    }

    @SuppressWarnings("unchecked")
    private void registerProvidedServices(ModuleMetadata metadata, StarCoreModule module) {
        for (Class<?> serviceType : metadata.providedServices()) {
            if (!serviceType.isInstance(module)) {
                logger.warning("STARCORE module '" + metadata.id() + "' declares service " + serviceType.getName()
                    + " but the module instance does not implement it; expecting module to register a concrete service explicitly.");
                continue;
            }
            serviceRegistry.register((Class<Object>) serviceType, module);
        }
    }

    private void updateStatus(String id, ModuleStatus status, String failureReason) {
        ModuleDescriptor previous = descriptors.get(id);
        descriptors.put(id, new ModuleDescriptor(previous.metadata(), status, previous.registeredAt(), Instant.now(), failureReason));
    }

    private List<StarCoreModule> sortedModules() {
        Map<String, StarCoreModule> remaining = new HashMap<>(modules);
        List<StarCoreModule> sorted = new ArrayList<>();

        while (!remaining.isEmpty()) {
            List<String> enabledIds = sorted.stream().map(module -> module.metadata().id()).toList();
            List<StarCoreModule> ready = remaining.values().stream()
                .filter(module -> module.metadata().dependencies().stream()
                    .filter(modules::containsKey)
                    .allMatch(enabledIds::contains))
                .sorted(Comparator.comparing((StarCoreModule module) -> module.metadata().layer())
                    .thenComparing(module -> module.metadata().id()))
                .toList();

            if (ready.isEmpty()) {
                throw new ModuleException("Cyclic or unresolved STARCORE module dependencies: " + remaining.keySet());
            }

            for (StarCoreModule module : ready) {
                sorted.add(module);
                remaining.remove(module.metadata().id());
            }
        }

        return sorted;
    }
}
