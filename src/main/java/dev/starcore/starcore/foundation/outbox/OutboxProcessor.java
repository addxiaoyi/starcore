package dev.starcore.starcore.foundation.outbox;

import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Outbox轮询处理器
 *
 * 定期处理Outbox中的待发送事件
 */
public final class OutboxProcessor {

    private final OutboxService outboxService;
    private final EventDispatcher dispatcher;
    private final Logger logger;
    private final ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    public OutboxProcessor(OutboxService outboxService, EventDispatcher dispatcher, Logger logger) {
        this.outboxService = outboxService;
        this.dispatcher = dispatcher;
        this.logger = logger;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Outbox-Processor");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 启动处理器
     */
    public void start(long intervalMs) {
        if (running) {
            logger.warn("OutboxProcessor already running");
            return;
        }

        running = true;
        scheduler.scheduleAtFixedRate(this::process, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        logger.info("OutboxProcessor started with interval {}ms", intervalMs);
    }

    /**
     * 停止处理器
     */
    public void stop() {
        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("OutboxProcessor stopped");
    }

    private void process() {
        if (!running) return;

        try {
            List<OutboxService.OutboxEvent> events = outboxService.findPendingEvents(100);

            if (events.isEmpty()) {
                return;
            }

            logger.debug("Processing {} outbox events", events.size());

            for (OutboxService.OutboxEvent event : events) {
                try {
                    outboxService.markProcessing(event.id());

                    boolean dispatched = dispatcher.dispatch(event);

                    if (dispatched) {
                        outboxService.markProcessed(event.id());
                        logger.trace("Dispatched event: {} for {}", event.eventType(), event.aggregateId());
                    } else {
                        outboxService.markFailed(event.id(), "Dispatch returned false");
                    }
                } catch (Exception e) {
                    outboxService.markFailed(event.id(), e.getMessage());
                    logger.error("Failed to process event {}: {}", event.id(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            logger.error("Outbox processing error: {}", e.getMessage(), e);
        }
    }

    @FunctionalInterface
    public interface EventDispatcher {
        boolean dispatch(OutboxService.OutboxEvent event);
    }
}
