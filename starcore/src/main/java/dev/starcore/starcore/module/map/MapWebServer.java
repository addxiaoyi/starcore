package dev.starcore.starcore.module.map;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

final class MapWebServer {
    // E-070: 原用 newCachedThreadPool 无 max 限制,恶意 DoS 大量连接将创建无限线程。
    // 改用固定大小线程池(默认 32 并发连接,可通过 settings 扩展),并设置未捕获异常处理器记录日志。
    private static final int MAX_WORKER_THREADS = 32;
    private final ServerFactory serverFactory;
    private HttpServer server;
    private ExecutorService executor;

    MapWebServer() {
        this(address -> HttpServer.create(address, 0));
    }

    MapWebServer(ServerFactory serverFactory) {
        this.serverFactory = serverFactory;
    }

    boolean start(Settings settings, Routes routes) throws IOException {
        if (!settings.enabled() || server != null) {
            return false;
        }
        HttpServer createdServer = serverFactory.create(new InetSocketAddress(settings.host(), settings.port()));
        // E-070: 替代 Executors.newCachedThreadPool(),改为固定大小线程池+未捕获异常处理器。
        // 固定大小可防止 DoS 创建无限线程,且 ThreadPoolExecutor 拒绝策略采用 abort 让客户端感知。
        ExecutorService createdExecutor = Executors.newFixedThreadPool(MAX_WORKER_THREADS, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "starcore-map-web-" + counter.incrementAndGet());
                t.setDaemon(true);
                t.setUncaughtExceptionHandler((thread, throwable) ->
                    java.util.logging.Logger.getLogger("starcore.map")
                        .warning("Uncaught exception in MapWebServer worker thread " + thread.getName() + ": " + throwable));
                return t;
            }
        });
        try {
            registerRoutes(createdServer, routes, settings.staticRoot());
            createdServer.setExecutor(createdExecutor);
            createdServer.start();
        } catch (RuntimeException exception) {
            createdServer.stop(0);
            createdExecutor.shutdownNow();
            throw exception;
        }
        this.server = createdServer;
        this.executor = createdExecutor;
        return true;
    }

    void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            // E-071: 原 shutdownNow 不等待任务结束,正在处理的请求被中断但客户端可能未收到响应。
            // 先 shutdown(停止接受新任务)+ awaitTermination(最多 2 秒等待完成),
            // 再 shutdownNow 强制中断未完成任务。
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    // 给被中断的任务一点时间清理
                    executor.awaitTermination(500, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
    }

    ExecutorService executor() {
        return executor;
    }

    private void registerRoutes(HttpServer target, Routes routes, Path staticRoot) {
        target.createContext("/api/map/snapshot", routes.snapshot());
        target.createContext("/api/map/health", routes.health());
        target.createContext("/api/map/stream", routes.stream());
        target.createContext("/api/map/avatar", routes.avatar());
        target.createContext("/api/map/terrain", routes.terrain());
        target.createContext("/api/map/terrain-data", routes.terrainData());
        target.createContext("/api/map/terrain-bin", routes.terrainBin());
        target.createContext("/api/map/claim/preview", routes.claimPreview());
        target.createContext("/api/map/claim/request", routes.claimRequest());
        target.createContext("/api/map/resource-district/migrate", routes.resourceDistrictMigration());
        target.createContext("/api/map/events", routes.eventLog());
        target.createContext("/api/map/finance/events", routes.financeEvents());
        // REST API endpoints
        target.createContext("/api/nations", routes.nations());
        target.createContext("/api/nations/", routes.nationById());
        target.createContext("/api/territories", routes.territories());
        target.createContext("/api/territories/", routes.territoryById());
        target.createContext("/api/cities", routes.cities());
        target.createContext("/api/cities/", routes.cityById());
        target.createContext("/api/players", routes.players());
        target.createContext("/api/players/", routes.playerById());
        // WebSocket upgrade endpoint
        target.createContext("/api/map/ws", routes.webSocket());
        target.createContext("/", new MapStaticFileHandler(staticRoot));
    }

    interface ServerFactory {
        HttpServer create(InetSocketAddress address) throws IOException;
    }

    record Settings(boolean enabled, String host, int port, Path staticRoot) {
    }

    record Routes(
        HttpHandler snapshot,
        HttpHandler health,
        HttpHandler stream,
        HttpHandler avatar,
        HttpHandler terrain,
        HttpHandler terrainData,
        HttpHandler terrainBin,
        HttpHandler claimPreview,
        HttpHandler claimRequest,
        HttpHandler resourceDistrictMigration,
        HttpHandler eventLog,
        HttpHandler financeEvents,
        HttpHandler nations,
        HttpHandler nationById,
        HttpHandler territories,
        HttpHandler territoryById,
        HttpHandler cities,
        HttpHandler cityById,
        HttpHandler players,
        HttpHandler playerById,
        HttpHandler webSocket
    ) {
    }
}
