package dev.starcore.starcore.module.map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class MapHttpRequestParser {
    // E-077: 限制 POST body 大小,防止攻击者发送超大 POST 导致OOM。
    // 默认上限 1 MiB,marker/search 接口的 JSON body 应小于此阈值。
    private static final int MAX_BODY_BYTES = 1 << 20;
    private static final Gson GSON = new Gson();

    private MapHttpRequestParser() {
    }

    static Map<String, String> requestParams(HttpExchange exchange) throws IOException {
        Map<String, String> params = new HashMap<>(query(exchange.getRequestURI()));
        String method = exchange.getRequestMethod();
        if (!"POST".equalsIgnoreCase(method)) {
            return params;
        }
        // E-077: 先检查 Content-Length 并拒绝超过 MAX_BODY_BYTES 的请求,
        // 避免 readAllBytes 直接把超大 body 加载到内存。同时使用有界读取。
        String contentLengthHeader = exchange.getRequestHeaders().getFirst("Content-Length");
        int declaredLength = -1;
        if (contentLengthHeader != null) {
            try {
                declaredLength = Integer.parseInt(contentLengthHeader.trim());
            } catch (NumberFormatException nfe) {
                declaredLength = -1;
            }
        }
        if (declaredLength > MAX_BODY_BYTES) {
            throw new IOException("Request body too large: declared " + declaredLength + " bytes (max " + MAX_BODY_BYTES + ")");
        }
        byte[] bytes;
        try (InputStream in = exchange.getRequestBody()) {
            bytes = readWithLimit(in, MAX_BODY_BYTES);
        }
        if (bytes.length == 0) {
            return params;
        }
        String body = new String(bytes, StandardCharsets.UTF_8).trim();
        if (body.isBlank()) {
            return params;
        }
        String contentType = Optional.ofNullable(exchange.getRequestHeaders().getFirst("Content-Type"))
            .orElse("")
            .toLowerCase(Locale.ROOT);
        if (contentType.contains("application/json") || body.startsWith("{")) {
            params.putAll(flatJsonObject(body));
            return params;
        }
        for (Map.Entry<String, String> entry : formBody(body).entrySet()) {
            params.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return params;
    }

    // E-077: 有界读取,最多读 maxBytes + 1 字节;若读出超过 maxBytes 即抛异常拒绝。
    private static byte[] readWithLimit(InputStream in, int maxBytes) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(maxBytes);
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
            baos.write(buffer, 0, read);
            if (baos.size() > maxBytes) {
                throw new IOException("Request body exceeded limit of " + maxBytes + " bytes");
            }
        }
        return baos.toByteArray();
    }

    static Map<String, String> query(URI uri) {
        Map<String, String> query = new HashMap<>();
        String raw = uri.getRawQuery();
        if (raw == null || raw.isBlank()) {
            return query;
        }
        for (String part : raw.split("&")) {
            int index = part.indexOf('=');
            if (index <= 0) {
                continue;
            }
            String key = URLDecoder.decode(part.substring(0, index), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(part.substring(index + 1), StandardCharsets.UTF_8);
            query.put(key, value);
        }
        return query;
    }

    static Map<String, String> formBody(String body) {
        Map<String, String> params = new HashMap<>();
        for (String part : body.split("&")) {
            int index = part.indexOf('=');
            if (index <= 0) {
                continue;
            }
            String key = URLDecoder.decode(part.substring(0, index), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(part.substring(index + 1), StandardCharsets.UTF_8);
            params.put(key, value);
        }
        return params;
    }

    static Map<String, String> flatJsonObject(String body) {
        // E-076: 原为手写 JSON 解析器,不支持 \\uXXXX、嵌套对象/数组、数字、boolean、null,
        // 非法 JSON 静默 break 返回部分参数。改用 Gson 解析:遇到非 JsonObject 或解析失败返回空 Map。
        // 仅保留扁平 key->string 映射(原 flatJsonObject 语义),嵌套对象/数组转为 toString。
        Map<String, String> params = new HashMap<>();
        if (body == null) return params;
        String trimmed = body.trim();
        if (trimmed.isEmpty()) return params;
        try {
            JsonElement element = GSON.fromJson(trimmed, JsonElement.class);
            if (element == null || !element.isJsonObject()) {
                return params;
            }
            JsonObject obj = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                JsonElement val = entry.getValue();
                if (val == null || val.isJsonNull()) {
                    params.put(entry.getKey(), "");
                } else if (val.isJsonPrimitive()) {
                    params.put(entry.getKey(), val.getAsString());
                } else {
                    // 嵌套对象/数组:用 toString 保留表示
                    params.put(entry.getKey(), val.toString());
                }
            }
        } catch (JsonParseException jpe) {
            // E-076: 非法 JSON 不再静默 break 返回部分参数,而是丢弃整条解析为空 Map。
            // 调用方在此情况下 params 为空,行为一致;不再有 putIfAbsent 行为不一致风险。
        }
        return params;
    }

    private static int skipJsonWhitespaceAndCommas(String input, int index) {
        while (index < input.length()) {
            char c = input.charAt(index);
            if (!Character.isWhitespace(c) && c != ',') {
                break;
            }
            index++;
        }
        return index;
    }

    private static int skipJsonWhitespace(String input, int index) {
        while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
            index++;
        }
        return index;
    }

    private static JsonString readJsonString(String input, int index) {
        StringBuilder builder = new StringBuilder();
        int cursor = index + 1;
        while (cursor < input.length()) {
            char c = input.charAt(cursor++);
            if (c == '"') {
                return new JsonString(builder.toString(), cursor);
            }
            if (c == '\\' && cursor < input.length()) {
                char escaped = input.charAt(cursor++);
                builder.append(switch (escaped) {
                    case '"', '\\', '/' -> escaped;
                    case 'b' -> '\b';
                    case 'f' -> '\f';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> escaped;
                });
                continue;
            }
            builder.append(c);
        }
        return new JsonString(builder.toString(), cursor);
    }

    private static JsonValue readJsonValue(String input, int index) {
        if (index < input.length() && input.charAt(index) == '"') {
            JsonString value = readJsonString(input, index);
            return new JsonValue(value.value(), value.nextIndex());
        }
        int cursor = index;
        while (cursor < input.length()) {
            char c = input.charAt(cursor);
            if (c == ',' || c == '}') {
                break;
            }
            cursor++;
        }
        return new JsonValue(input.substring(index, cursor).trim(), cursor);
    }

    private record JsonString(String value, int nextIndex) {
    }

    private record JsonValue(String value, int nextIndex) {
    }
}
