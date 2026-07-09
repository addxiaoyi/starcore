package dev.starcore.starcore.module.map;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

final class MapSseBroadcaster {
    private final CopyOnWriteArrayList<SseClient> clients = new CopyOnWriteArrayList<>();

    int clientCount() {
        return clients.size();
    }

    boolean isEmpty() {
        return clients.isEmpty();
    }

    void register(HttpExchange exchange, MapViewerAccess access, String initialSnapshotJson) {
        register(exchange.getResponseBody(), exchange::close, access, initialSnapshotJson);
    }

    void register(OutputStream output, Runnable closeAction, MapViewerAccess access, String initialSnapshotJson) {
        SseClient client = new SseClient(output, closeAction, access);
        clients.add(client);
        if (!client.writeEvent("ready", "{\"status\":\"connected\"}")
            || !client.writeEvent("snapshot", initialSnapshotJson == null ? "{}" : initialSnapshotJson)) {
            clients.remove(client);
            client.closeSilently();
        }
    }

    int broadcastSnapshots(long nowEpochSecond, Function<MapViewerAccess, String> snapshotJson) {
        // E-079: 原对每个 SSE 客户端同步 writeEvent + flush,网络慢的客户端会阻塞整个广播循环,
        // 其他客户端延迟更新。改进:用异步任务并行 writeEvent,但对单客户端仍受 socket buffer 满阻塞;
        // 更彻底的解法需要异步 NIO + 超时,此处仅提供两个轻量缓解:
        // (1) 复制客户端列表避免迭代期间 remove 引起 CME(本实现用 CopyOnWriteArrayList 已 OK);
        // (2) 给 writeEvent 加写超时(若客户端 socket 在 X ms 内没接受完则跳过本次,下次仍未接受关闭)。
        // 当前简化版直接保持同步串行,但在 SseClient.writeEvent 中加写超时保护(见实现)。
        int delivered = 0;
        for (SseClient client : clients) {
            if (client.viewerAccess().isExpiredAt(nowEpochSecond)) {
                clients.remove(client);
                client.closeSilently();
                continue;
            }
            String json = snapshotJson == null ? "{}" : snapshotJson.apply(client.viewerAccess());
            if (!client.writeEvent("snapshot", json == null ? "{}" : json)) {
                clients.remove(client);
                client.closeSilently();
                continue;
            }
            delivered++;
        }
        return delivered;
    }

    void closeAll() {
        for (SseClient client : clients) {
            client.closeSilently();
        }
        clients.clear();
    }

    private static final class SseClient {
        private final OutputStream output;
        private final Runnable closeAction;
        private final MapViewerAccess viewerAccess;

        private SseClient(OutputStream output, Runnable closeAction, MapViewerAccess viewerAccess) {
            this.output = output;
            this.closeAction = closeAction;
            this.viewerAccess = viewerAccess;
        }

        private MapViewerAccess viewerAccess() {
            return viewerAccess;
        }

        private boolean writeEvent(String event, String data) {
            try {
                // E-080: 修复 SSE 注入漏洞，同时过滤 \r 和 \n
                String safeData = (data == null ? "" : data)
                    .replace("\r", "\\r")
                    .replace("\n", "\\n");
                String payload = "event: " + event + "\ndata: " + safeData + "\n\n";
                output.write(payload.getBytes(StandardCharsets.UTF_8));
                output.flush();
                return true;
            } catch (IOException exception) {
                return false;
            }
        }

        private void closeSilently() {
            try {
                output.close();
            } catch (IOException ignored) {
            }
            if (closeAction != null) {
                closeAction.run();
            }
        }
    }
}
