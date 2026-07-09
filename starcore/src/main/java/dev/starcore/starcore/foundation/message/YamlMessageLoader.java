package dev.starcore.starcore.foundation.message;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

public final class YamlMessageLoader {
    private YamlMessageLoader() {
    }

    public static Map<String, String> load(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return load(reader);
        }
    }

    public static Map<String, String> load(InputStream input) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            return load(reader);
        }
    }

    private static Map<String, String> load(BufferedReader reader) throws IOException {
        Map<String, String> messages = new HashMap<>();
        ArrayDeque<PathPart> stack = new ArrayDeque<>();
        String raw;
        while ((raw = reader.readLine()) != null) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("- ")) {
                continue;
            }
            int separator = raw.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String key = raw.substring(0, separator).trim();
            if (key.isEmpty() || key.startsWith("- ")) {
                continue;
            }

            int indent = leadingSpaces(raw);
            while (!stack.isEmpty() && stack.peekLast().indent() >= indent) {
                stack.removeLast();
            }

            String path = path(stack, key);
            String value = raw.substring(separator + 1).trim();
            stack.addLast(new PathPart(indent, key));
            if (value.isEmpty() || "|".equals(value) || ">".equals(value) || value.startsWith("#")) {
                continue;
            }
            messages.put(path, unquote(value));
        }
        return messages;
    }

    private static int leadingSpaces(String value) {
        int count = 0;
        while (count < value.length() && value.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static String path(ArrayDeque<PathPart> stack, String key) {
        if (stack.isEmpty()) {
            return key;
        }
        StringBuilder builder = new StringBuilder();
        for (PathPart part : stack) {
            if (!builder.isEmpty()) {
                builder.append('.');
            }
            builder.append(part.key());
        }
        if (!builder.isEmpty()) {
            builder.append('.');
        }
        return builder.append(key).toString();
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private record PathPart(int indent, String key) {
    }
}
