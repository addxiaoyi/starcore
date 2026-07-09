package dev.starcore.starcore.core.service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ServiceRegistry {
    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    public <T> void register(Class<T> serviceType, T implementation) {
        services.put(serviceType, implementation);
    }

    public <T> Optional<T> find(Class<T> serviceType) {
        Object implementation = services.get(serviceType);
        if (implementation == null) {
            return Optional.empty();
        }
        return Optional.of(serviceType.cast(implementation));
    }

    public <T> void unregister(Class<T> serviceType) {
        services.remove(serviceType);
    }

    public <T> T require(Class<T> serviceType) {
        return find(serviceType).orElseThrow(() -> new IllegalStateException("Missing service: " + serviceType.getName()));
    }

    public Map<Class<?>, Object> snapshot() {
        return Map.copyOf(services);
    }
}
