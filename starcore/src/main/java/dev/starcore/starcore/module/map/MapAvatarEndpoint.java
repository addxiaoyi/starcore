package dev.starcore.starcore.module.map;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class MapAvatarEndpoint {
    private static final String TEXT = "text/plain; charset=utf-8";
    private static final String PNG = "image/png";
    private static final int FRESH_CACHE_MAX_AGE_SECONDS = 86_400;
    private static final int DOWNLOADED_MAX_AGE_SECONDS = 3_600;
    private static final int STALE_FALLBACK_MAX_AGE_SECONDS = 300;

    private final AvatarDownloader downloader;

    MapAvatarEndpoint() {
        this(MapAvatarEndpoint::downloadFromUrl);
    }

    MapAvatarEndpoint(AvatarDownloader downloader) {
        this.downloader = downloader;
    }

    Response response(Map<String, String> params, Settings settings) {
        UUID playerId;
        try {
            playerId = parsePlayerId(params);
        } catch (IllegalArgumentException exception) {
            return text(400, exception.getMessage());
        }

        Path cacheFile = cacheFile(settings, playerId);
        if (settings.cacheEnabled() && isCacheFresh(cacheFile, settings.cacheTtlMinutes())) {
            Response cached = imageFile(cacheFile, FRESH_CACHE_MAX_AGE_SECONDS);
            if (cached != null) {
                return cached;
            }
        }

        try {
            byte[] avatarBytes = downloadAvatar(playerId, settings.upstreams());
            if (settings.cacheEnabled()) {
                writeCache(cacheFile, avatarBytes);
            }
            return image(avatarBytes, DOWNLOADED_MAX_AGE_SECONDS);
        } catch (IOException exception) {
            if (Files.exists(cacheFile)) {
                Response stale = imageFile(cacheFile, STALE_FALLBACK_MAX_AGE_SECONDS);
                if (stale != null) {
                    return stale;
                }
            }
            return text(502, "Avatar upstream unavailable");
        }
    }

    void cleanup(Settings settings) {
        Path cacheDirectory = settings.cacheDirectory();
        if (cacheDirectory == null || Files.notExists(cacheDirectory)) {
            return;
        }
        try (var files = Files.list(cacheDirectory)) {
            files.filter(Files::isRegularFile)
                .filter(file -> !isCacheFresh(file, settings.cacheTtlMinutes()))
                .forEach(file -> {
                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException ignored) {
                    }
                });
        } catch (IOException ignored) {
        }
    }

    String upstreamAvatarUrl(String upstream, UUID playerId) {
        return formatUpstream(upstream, playerId);
    }

    private UUID parsePlayerId(Map<String, String> params) {
        String id = params == null ? null : params.get("id");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Missing id");
        }
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid id");
        }
    }

    private Path cacheFile(Settings settings, UUID playerId) {
        return settings.cacheDirectory().resolve(playerId + ".png");
    }

    private boolean isCacheFresh(Path cacheFile, int ttlMinutes) {
        try {
            if (Files.notExists(cacheFile)) {
                return false;
            }
            Instant modifiedAt = Files.getLastModifiedTime(cacheFile).toInstant();
            Duration age = Duration.between(modifiedAt, Instant.now());
            return age.toMinutes() < ttlMinutes;
        } catch (IOException exception) {
            return false;
        }
    }

    private byte[] downloadAvatar(UUID playerId, List<String> upstreams) throws IOException {
        IOException lastException = null;
        for (String upstream : upstreams) {
            try {
                return downloader.download(formatUpstream(upstream, playerId));
            } catch (IOException exception) {
                lastException = exception;
            }
        }
        throw lastException != null ? lastException : new IOException("No avatar upstream configured");
    }

    private void writeCache(Path cacheFile, byte[] avatarBytes) throws IOException {
        Files.createDirectories(cacheFile.getParent());
        Files.write(cacheFile, avatarBytes);
    }

    private Response imageFile(Path file, int maxAgeSeconds) {
        try {
            return image(Files.readAllBytes(file), maxAgeSeconds);
        } catch (IOException exception) {
            return null;
        }
    }

    private static Response image(byte[] bytes, int maxAgeSeconds) {
        return new Response(200, PNG, bytes, maxAgeSeconds);
    }

    private static Response text(int status, String body) {
        return new Response(status, TEXT, body.getBytes(StandardCharsets.UTF_8), 0);
    }

    private static String formatUpstream(String upstream, UUID playerId) {
        String uuid = playerId.toString();
        return upstream
            .replace("{uuid}", uuid)
            .replace("{uuidNoDash}", uuid.replace("-", ""));
    }

    private static byte[] downloadFromUrl(String upstreamUrl) throws IOException {
        URL url = URI.create(upstreamUrl).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(6000);
        connection.setRequestProperty("User-Agent", "STARCORE/0.1.0");
        connection.setRequestProperty("Accept", "image/png,image/*;q=0.8,*/*;q=0.5");
        connection.connect();
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IOException("Avatar upstream returned status " + status);
        }
        try (InputStream input = connection.getInputStream()) {
            return input.readAllBytes();
        } finally {
            connection.disconnect();
        }
    }

    @FunctionalInterface
    interface AvatarDownloader {
        byte[] download(String upstreamUrl) throws IOException;
    }

    record Settings(Path cacheDirectory, boolean cacheEnabled, int cacheTtlMinutes, List<String> upstreams) {
        Settings {
            if (cacheDirectory == null) {
                throw new IllegalArgumentException("cacheDirectory is required");
            }
            cacheTtlMinutes = Math.max(1, cacheTtlMinutes);
            upstreams = upstreams == null ? List.of() : List.copyOf(upstreams);
        }
    }

    record Response(int status, String contentType, byte[] body, int maxAgeSeconds) {
    }
}
