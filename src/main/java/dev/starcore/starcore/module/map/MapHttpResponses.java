package dev.starcore.starcore.module.map;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class MapHttpResponses {
    private static final String NO_STORE = "no-store, no-cache, must-revalidate";

    private MapHttpResponses() {
    }

    static void writeJson(HttpExchange exchange, String json, List<String> allowedOrigins) throws IOException {
        writeJson(exchange, 200, json, allowedOrigins);
    }

    static void writeJson(HttpExchange exchange, int status, String json, List<String> allowedOrigins) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        applyCorsHeaders(exchange, allowedOrigins);
        headers.set("Cache-Control", NO_STORE);
        write(exchange, status, bytes);
    }

    static void writeClaimResponse(
        HttpExchange exchange,
        int status,
        String json,
        String contentType,
        String filename,
        List<String> allowedOrigins
    ) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        if (filename != null && !filename.isBlank()) {
            headers.set("Content-Disposition", "attachment; filename=\"" + filename.replace("\"", "") + "\"");
        }
        applyCorsHeaders(exchange, allowedOrigins);
        headers.set("Cache-Control", NO_STORE);
        write(exchange, status, bytes);
    }

    static void writeText(
        HttpExchange exchange,
        int status,
        String contentType,
        String body,
        List<String> allowedOrigins
    ) throws IOException {
        writeBytes(exchange, body.getBytes(StandardCharsets.UTF_8), contentType, status, 0, allowedOrigins);
    }

    static void writeBytes(
        HttpExchange exchange,
        byte[] bytes,
        String contentType,
        int status,
        int maxAgeSeconds,
        List<String> allowedOrigins
    ) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        applyCorsHeaders(exchange, allowedOrigins);
        headers.set("Cache-Control", maxAgeSeconds > 0 ? "public, max-age=" + maxAgeSeconds : NO_STORE);
        write(exchange, status, bytes);
    }

    static boolean handleCorsPreflight(HttpExchange exchange, List<String> allowedOrigins) throws IOException {
        if (!"OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            return false;
        }
        Headers headers = exchange.getResponseHeaders();
        applyCorsHeaders(exchange, allowedOrigins);
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Accept, Content-Type");
        headers.set("Cache-Control", NO_STORE);
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
        return true;
    }

    static void applyCorsHeaders(HttpExchange exchange, List<String> allowedOrigins) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null || origin.isBlank() || allowedOrigins == null) {
            return;
        }
        if (allowedOrigins.contains("*")) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            return;
        }
        if (allowedOrigins.contains(origin)) {
            Headers headers = exchange.getResponseHeaders();
            headers.set("Access-Control-Allow-Origin", origin);
            headers.set("Vary", "Origin");
        }
    }

    private static void write(HttpExchange exchange, int status, byte[] bytes) throws IOException {
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(bytes);
        }
    }
}
