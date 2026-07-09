package dev.starcore.starcore.foundation.events;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 领域事件总线
 */
public final class DomainEventBus {

    private final Map<Class<?>, List<Consumer<?>>> handlers = new ConcurrentHashMap<>();

    /**
     * 注册事件处理器
     */
    public <E> void subscribe(Class<E> eventType, Consumer<E> handler) {
        handlers.computeIfAbsent(eventType, k -> Collections.synchronizedList(new ArrayList<>()))
            .add(handler);
    }

    /**
     * 发布事件
     */
    @SuppressWarnings("unchecked")
    public <E> void publish(E event) {
        List<Consumer<?>> eventHandlers = handlers.get(event.getClass());
        if (eventHandlers != null) {
            for (Consumer<?> handler : eventHandlers) {
                try {
                    ((Consumer<E>) handler).accept(event);
                } catch (Exception e) {
                    // 日志记录
                }
            }
        }
    }

    /**
     * 同步发布事件
     */
    public <E> EventResult<E> publishSync(E event) {
        List<Exception> errors = new ArrayList<>();
        List<Consumer<?>> eventHandlers = handlers.get(event.getClass());

        if (eventHandlers != null) {
            for (Consumer<?> handler : eventHandlers) {
                try {
                    ((Consumer<E>) handler).accept(event);
                } catch (Exception e) {
                    errors.add(e);
                }
            }
        }

        return new EventResult<>(event, errors);
    }

    public record EventResult<E>(E event, List<Exception> errors) {
        public boolean hasErrors() { return !errors.isEmpty(); }
    }
}
