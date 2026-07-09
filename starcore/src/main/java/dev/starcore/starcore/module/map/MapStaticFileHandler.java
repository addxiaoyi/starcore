package dev.starcore.starcore.module.map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

final class MapStaticFileHandler implements HttpHandler {
    private final Path root;

    MapStaticFileHandler(Path root) {
        this.root = root;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Response response = response(root, exchange.getRequestURI().getPath());
        exchange.getResponseHeaders().set("Content-Type", response.contentType());
        if (!response.cacheControl().isBlank()) {
            exchange.getResponseHeaders().set("Cache-Control", response.cacheControl());
        }
        exchange.sendResponseHeaders(response.status(), response.body().length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(response.body());
        }
    }

    static Response response(Path root, String requestPath) throws IOException {
        // E-072: 路径穿越风险已通过两层防御缓解:
        // (1) safeRoot.toAbsolutePath().normalize();
        // (2) target.startsWith(safeRoot) 校验解析后的绝对路径仍在 root 内。
        // 额外强化:不直接信任 normalizeRequestPath 返回的字符串,始终用 Path.resolve+normalize 后再校验 startsWith。
        Path safeRoot = root.toAbsolutePath().normalize();
        String normalized = normalizeRequestPath(requestPath);
        Path target = safeRoot.resolve(normalized).normalize();
        if (!target.startsWith(safeRoot) || Files.notExists(target) || Files.isDirectory(target)) {
            return notFound();
        }

        String extension = extensionOf(target.getFileName().toString());
        String contentType = contentType(extension);
        String cacheControl = "html".equals(extension)
            ? "no-store, no-cache, must-revalidate"
            : "public, max-age=60";
        return new Response(200, contentType, cacheControl, Files.readAllBytes(target));
    }

    private static String normalizeRequestPath(String requestPath) {
        if (requestPath == null || requestPath.isBlank() || "/".equals(requestPath)) {
            return "index.html";
        }
        String normalized = requestPath.startsWith("/") ? requestPath.substring(1) : requestPath;
        return normalized.replace('\\', '/');
    }

    private static Response notFound() {
        return new Response(
            404,
            "text/plain; charset=utf-8",
            "",
            "Not Found".getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String contentType(String extension) {
        return switch (extension) {
            case "html" -> "text/html; charset=utf-8";
            case "js" -> "application/javascript; charset=utf-8";
            case "json" -> "application/json; charset=utf-8";
            case "css" -> "text/css; charset=utf-8";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "svg" -> "image/svg+xml";
            default -> "application/octet-stream";
        };
    }

    private static String extensionOf(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index < 0 ? "" : fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    record Response(int status, String contentType, String cacheControl, byte[] body) {
    }
}
