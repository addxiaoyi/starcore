package dev.starcore.starcore.core.event;

import dev.starcore.starcore.core.scheduler.StarCoreScheduler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class StarCoreEventBus {
    private final Logger logger;
    private final StarCoreScheduler scheduler;
    private final Map<Class<?>, List<Consumer<?>>> listeners = new ConcurrentHashMap<>();
    private final Map<Object, CancellableEvent<?>> cancellableEvents = new ConcurrentHashMap<>();
    private final Map<Class<?>, EventMetrics> eventMetrics = new ConcurrentHashMap<>();
    private volatile boolean asyncPublishingEnabled = true;
    private volatile boolean metricsEnabled = true;
    private final AtomicLong eventIdGenerator = new AtomicLong(0);

    // Event priority levels (lower number = higher priority)
    public enum Priority {
        HIGHEST(0),     // System-critical events
        HIGH(25),        // High-priority game logic
        NORMAL(50),     // Default priority
        LOW(75),        // Optional functionality
        LOWEST(100);    // Debug/monitoring

        public final int order;

        Priority(int order) {
            this.order = order;
        }
    }

    public StarCoreEventBus(Logger logger) {
        this(logger, null);
    }

    public StarCoreEventBus(Logger logger, StarCoreScheduler scheduler) {
        this.logger = logger;
        this.scheduler = scheduler;
    }

    /**
     * 订阅事件
     * @param eventType 事件类型
     * @param listener 监听器
     * @return 取消订阅的 Runnable
     */
    public <T> Runnable subscribe(Class<T> eventType, Consumer<T> listener) {
        return subscribe(eventType, Priority.NORMAL, listener);
    }

    /**
     * 订阅事件，带优先级
     * @param eventType 事件类型
     * @param priority 事件优先级
     * @param listener 监听器
     * @return 取消订阅的 Runnable
     */
    @SuppressWarnings("unchecked")
    public <T> Runnable subscribe(Class<T> eventType, Priority priority, Consumer<T> listener) {
        List<Consumer<?>> eventListeners = listeners.computeIfAbsent(eventType, ignored -> new CopyOnWriteArrayList<>());
        PriorityListener<T> priorityListener = new PriorityListener<>(listener, priority);
        eventListeners.add(priorityListener);
        // Sort by priority after adding
        sortListeners(eventType);
        return () -> eventListeners.remove(priorityListener);
    }

    /**
     * 按优先级排序监听器
     */
    @SuppressWarnings("unchecked")
    private <T> void sortListeners(Class<T> eventType) {
        List<Consumer<?>> eventListeners = listeners.get(eventType);
        if (eventListeners != null && eventListeners.size() > 1) {
            // Use insertion sort for partially sorted lists
            eventListeners.sort(Comparator.comparingInt((Consumer<?> c) -> {
                if (c instanceof PriorityListener<?>) {
                    return ((PriorityListener<?>) c).priority.order;
                }
                return Priority.NORMAL.order;
            }));
        }
    }

    /**
     * 订阅一次性事件（触发后自动取消订阅）
     * @param eventType 事件类型
     * @param listener 监听器
     */
    public <T> void subscribeOnce(Class<T> eventType, Consumer<T> listener) {
        subscribeOnce(eventType, Priority.NORMAL, listener);
    }

    /**
     * 订阅一次性事件（触发后自动取消订阅），带优先级
     * @param eventType 事件类型
     * @param priority 事件优先级
     * @param listener 监听器
     */
    @SuppressWarnings("unchecked")
    public <T> void subscribeOnce(Class<T> eventType, Priority priority, Consumer<T> listener) {
        List<Consumer<?>> eventListeners = listeners.computeIfAbsent(eventType, ignored -> new CopyOnWriteArrayList<>());
        PriorityCancellableConsumer<T> cancellable = new PriorityCancellableConsumer<>(listener, priority, () -> {});
        cancellable.setRemoveAction(() -> {
            synchronized (eventListeners) {
                eventListeners.remove(cancellable);
            }
        });
        eventListeners.add(cancellable);
        sortListeners(eventType);
    }

    /**
     * 异步订阅事件（监听器在异步线程执行）
     * @param eventType 事件类型
     * @param listener 监听器
     * @return 取消订阅的 Runnable
     */
    public <T> Runnable subscribeAsync(Class<T> eventType, Consumer<T> listener) {
        return subscribeAsync(eventType, Priority.NORMAL, listener);
    }

    /**
     * 异步订阅事件（监听器在异步线程执行），带优先级
     * @param eventType 事件类型
     * @param priority 事件优先级
     * @param listener 监听器
     * @return 取消订阅的 Runnable
     */
    public <T> Runnable subscribeAsync(Class<T> eventType, Priority priority, Consumer<T> listener) {
        if (scheduler == null) {
            return subscribe(eventType, priority, listener);
        }
        List<Consumer<?>> eventListeners = listeners.computeIfAbsent(eventType, ignored -> new CopyOnWriteArrayList<>());
        PriorityConsumer<T> asyncListener = new PriorityConsumer<>(event -> scheduler.runAsync(() -> listener.accept(event)), priority);
        eventListeners.add(asyncListener);
        sortListeners(eventType);
        return () -> eventListeners.remove(asyncListener);
    }

    /**
     * 发布事件（同步）
     * @param event 事件
     * @return 是否所有监听器都成功执行
     */
    public boolean publish(Object event) {
        return publish(event, false);
    }

    /**
     * 发布事件
     * @param event 事件
     * @param async 是否异步发布
     * @return 如果异步发布，返回 CompletableFuture；同步发布返回是否所有监听器都成功执行
     */
    public boolean publish(Object event, boolean async) {
        if (async && asyncPublishingEnabled && scheduler != null) {
            scheduler.runAsync(() -> dispatchEvent(event));
            return true;
        }
        return dispatchEvent(event);
    }

    /**
     * 发布事件，返回 Future
     * @param event 事件
     * @return CompletableFuture
     */
    public CompletableFuture<Void> publishAsync(Object event) {
        if (scheduler == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        scheduler.runAsync(() -> {
            try {
                dispatchEvent(event);
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * 发布可取消的事件
     * @param eventId 事件 ID
     * @param event 事件
     * @param <T> 事件类型
     * @return 可取消的事件对象
     */
    public <T extends Cancellable> CancellableEvent<T> publishCancellable(Object eventId, T event) {
        CancellableEvent<T> cancellableEvent = new CancellableEvent<>(event);
        cancellableEvents.put(eventId, cancellableEvent);
        publish(event);
        return cancellableEvent;
    }

    /**
     * 取消事件
     * @param eventId 事件 ID
     * @return 是否成功取消
     */
    public boolean cancelEvent(Object eventId) {
        CancellableEvent<?> cancellable = cancellableEvents.get(eventId);
        if (cancellable != null) {
            cancellable.cancel();
            return true;
        }
        return false;
    }

    /**
     * 检查事件是否已取消
     * @param eventId 事件 ID
     * @return 是否已取消
     */
    public boolean isEventCancelled(Object eventId) {
        CancellableEvent<?> cancellable = cancellableEvents.get(eventId);
        return cancellable != null && cancellable.isCancelled();
    }

    /**
     * 发布事件并等待所有监听器完成
     * @param event 事件
     * @return CompletableFuture
     */
    public CompletableFuture<Void> publishAndWait(Object event) {
        if (scheduler == null) {
            dispatchEvent(event);
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        scheduler.runAsync(() -> {
            try {
                dispatchEvent(event);
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * 发布带条件过滤的事件
     * @param event 事件
     * @param filter 条件过滤器
     * @return 是否所有监听器都成功执行
     */
    public <T> boolean publishFiltered(T event, Predicate<T> filter) {
        if (filter.test(event)) {
            return publish(event);
        }
        return true;
    }

    /**
     * 获取事件指标
     * @param eventType 事件类型
     * @return 事件指标
     */
    public EventMetrics getMetrics(Class<?> eventType) {
        return eventMetrics.getOrDefault(eventType, EventMetrics.EMPTY);
    }

    /**
     * 获取所有事件指标
     */
    public Map<Class<?>, EventMetrics> getAllMetrics() {
        return new ConcurrentHashMap<>(eventMetrics);
    }

    /**
     * 启用/禁用异步发布
     */
    public void setAsyncPublishingEnabled(boolean enabled) {
        this.asyncPublishingEnabled = enabled;
    }

    /**
     * 启用/禁用指标收集
     */
    public void setMetricsEnabled(boolean enabled) {
        this.metricsEnabled = enabled;
    }

    private boolean dispatchEvent(Object event) {
        long startNanos = System.nanoTime();
        List<Consumer<?>> eventListeners = listeners.getOrDefault(event.getClass(), List.of());
        boolean allSuccess = true;

        for (Consumer<?> listener : eventListeners) {
            if (listener instanceof CancellableListener<?> cancellable) {
                if (cancellable.isCancelled()) {
                    continue;
                }
            }
            try {
                dispatch(listener, event);
            } catch (Exception e) {
                allSuccess = false;
                logger.log(Level.WARNING, "STARCORE event listener failed for " + event.getClass().getSimpleName(), e);
            }
        }

        // Record metrics
        if (metricsEnabled) {
            long durationNanos = System.nanoTime() - startNanos;
            eventMetrics.computeIfAbsent(event.getClass(), k -> new EventMetrics())
                .record(durationNanos, allSuccess);
        }

        return allSuccess;
    }

    @SuppressWarnings("unchecked")
    private <T> void dispatch(Consumer<?> listener, T event) {
        try {
            ((Consumer<T>) listener).accept(event);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "STARCORE event listener failed for " + event.getClass().getSimpleName(), exception);
        }
    }

    /**
     * 事件指标
     */
    public static final class EventMetrics {
        private static final EventMetrics EMPTY = new EventMetrics();
        private final AtomicLong invocationCount = new AtomicLong(0);
        private final AtomicLong failureCount = new AtomicLong(0);
        private final AtomicLong totalDurationNanos = new AtomicLong(0);
        private final AtomicLong maxDurationNanos = new AtomicLong(0);

        public void record(long durationNanos, boolean success) {
            invocationCount.incrementAndGet();
            if (!success) {
                failureCount.incrementAndGet();
            }
            totalDurationNanos.addAndGet(durationNanos);
            // Update max with CAS
            long currentMax;
            do {
                currentMax = maxDurationNanos.get();
                if (durationNanos <= currentMax) break;
            } while (!maxDurationNanos.compareAndSet(currentMax, durationNanos));
        }

        public long invocations() { return invocationCount.get(); }
        public long failures() { return failureCount.get(); }
        public double averageDurationMs() {
            long count = invocationCount.get();
            return count > 0 ? (double) totalDurationNanos.get() / count / 1_000_000 : 0;
        }
        public long maxDurationMs() { return maxDurationNanos.get() / 1_000_000; }
    }

    /**
     * 可取消事件接口
     */
    public interface Cancellable {
        default void cancel() {}
        default boolean isCancelled() { return false; }
    }

    /**
     * 可取消事件包装器
     */
    public static final class CancellableEvent<T extends Cancellable> implements Cancellable {
        private final T event;
        private volatile boolean cancelled = false;

        public CancellableEvent(T event) {
            this.event = event;
        }

        @Override
        public void cancel() {
            this.cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        public T getEvent() {
            return event;
        }
    }

    /**
     * 可取消的消费者
     */
    private static final class CancellableConsumer<T> implements Consumer<T>, Cancellable {
        private final Consumer<T> delegate;
        private Runnable onCancel;
        private Runnable onRemove;
        private volatile boolean cancelled = false;

        public CancellableConsumer(Consumer<T> delegate, Runnable onCancel) {
            this.delegate = delegate;
            this.onCancel = onCancel;
            this.onRemove = () -> {};
        }

        public void setRemoveAction(Runnable onRemove) {
            this.onRemove = onRemove;
        }

        @Override
        public void accept(T t) {
            if (!cancelled) {
                delegate.accept(t);
                if (onRemove != null) {
                    onRemove.run();
                }
            }
        }

        @Override
        public void cancel() {
            this.cancelled = true;
            onCancel.run();
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    /**
     * 优先级监听器包装
     */
    private static final class PriorityListener<T> implements Consumer<T>, Cancellable {
        private final Consumer<T> delegate;
        final Priority priority;
        private volatile boolean cancelled = false;

        PriorityListener(Consumer<T> delegate, Priority priority) {
            this.delegate = delegate;
            this.priority = priority;
        }

        @Override
        public void accept(T t) {
            delegate.accept(t);
        }

        @Override
        public void cancel() {
            this.cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    /**
     * 优先级消费者（用于异步事件）
     */
    private static final class PriorityConsumer<T> implements Consumer<T>, Cancellable {
        private final Consumer<T> delegate;
        final Priority priority;
        private volatile boolean cancelled = false;

        PriorityConsumer(Consumer<T> delegate, Priority priority) {
            this.delegate = delegate;
            this.priority = priority;
        }

        @Override
        public void accept(T t) {
            delegate.accept(t);
        }

        @Override
        public void cancel() {
            this.cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    /**
     * 优先级可取消消费者（用于一次性事件）
     */
    private static final class PriorityCancellableConsumer<T> implements Consumer<T>, Cancellable {
        private final Consumer<T> delegate;
        final Priority priority;
        private Runnable onCancel;
        private Runnable onRemove;
        private volatile boolean cancelled = false;

        PriorityCancellableConsumer(Consumer<T> delegate, Priority priority, Runnable onCancel) {
            this.delegate = delegate;
            this.priority = priority;
            this.onCancel = onCancel;
            this.onRemove = () -> {};
        }

        public void setRemoveAction(Runnable onRemove) {
            this.onRemove = onRemove;
        }

        @Override
        public void accept(T t) {
            if (!cancelled) {
                delegate.accept(t);
                onRemove.run();
            }
        }

        @Override
        public void cancel() {
            this.cancelled = true;
            onCancel.run();
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    /**
     * 可取消监听器接口
     */
    private interface CancellableListener<T> {
        boolean isCancelled();
    }
}
