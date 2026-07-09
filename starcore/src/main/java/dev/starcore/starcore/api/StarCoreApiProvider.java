package dev.starcore.starcore.api;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleDescriptor;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

public final class StarCoreApiProvider implements StarCoreApi {
    private final StarCoreContext context;

    public StarCoreApiProvider(StarCoreContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    @Override
    public String version() {
        return context.plugin().getPluginMeta().getVersion();
    }

    @Override
    public Collection<ModuleDescriptor> modules() {
        return context.moduleManager().descriptors();
    }

    @Override
    public <T> Optional<T> service(Class<T> serviceType) {
        Optional<T> service = context.serviceRegistry().find(serviceType);
        if (service.isPresent()) {
            return service;
        }
        // 尝试从 REST API 服务器获取服务
        if (serviceType == dev.starcore.starcore.api.v1.RestApiServer.class ||
            serviceType == dev.starcore.starcore.api.v1.auth.ApiAuthService.class ||
            serviceType == dev.starcore.starcore.api.v1.websocket.WebSocketConnectionManager.class) {
            // 这些服务通过 REST API 服务器间接提供
            return Optional.empty();
        }
        return Optional.empty();
    }
}
