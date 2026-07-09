package dev.starcore.starcore.module.map;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.IntSupplier;

final class TerrainTileEndpoint {
    private static final String TEXT = "text/plain; charset=utf-8";
    private static final String JSON = "application/json; charset=utf-8";
    private static final String BINARY = "application/octet-stream";
    private static final String PNG = "image/png";
    private static final String BUSY_MESSAGE = "Terrain renderer is busy, retry shortly";

    private final PngProvider pngProvider;
    private final BinaryProvider binaryProvider;
    private final RasterProvider rasterProvider;
    private final IntSupplier cacheSeconds;

    TerrainTileEndpoint(
        PngProvider pngProvider,
        BinaryProvider binaryProvider,
        RasterProvider rasterProvider,
        IntSupplier cacheSeconds
    ) {
        this.pngProvider = pngProvider;
        this.binaryProvider = binaryProvider;
        this.rasterProvider = rasterProvider;
        this.cacheSeconds = cacheSeconds;
    }

    Response response(Format format, Map<String, String> params) throws IOException {
        try {
            Request request = Request.parse(params);
            return switch (format) {
                case PNG -> pngResponse(request);
                case BINARY -> binaryResponse(request);
                case DATA -> dataResponse(request);
            };
        } catch (TerrainTileBusyException exception) {
            return text(503, BUSY_MESSAGE);
        } catch (IllegalArgumentException exception) {
            return text(400, exception.getMessage());
        }
    }

    private Response pngResponse(Request request) throws IOException {
        byte[] tile = pngProvider.png(request.world(), request.minX(), request.minZ(), request.worldSize());
        return tile == null ? text(404, "World not found") : new Response(200, PNG, tile, cacheSeconds());
    }

    private Response binaryResponse(Request request) throws IOException {
        byte[] tile = binaryProvider.binary(request.world(), request.minX(), request.minZ(), request.worldSize());
        return tile == null ? text(404, "World not found") : new Response(200, BINARY, tile, cacheSeconds());
    }

    private Response dataResponse(Request request) throws IOException {
        TerrainTileRaster raster = rasterProvider.raster(request.world(), request.minX(), request.minZ(), request.worldSize());
        if (raster == null) {
            return text(404, "World not found");
        }
        byte[] json = TerrainTileCodec.encodeJson(raster).getBytes(StandardCharsets.UTF_8);
        return new Response(200, JSON, json, cacheSeconds());
    }

    private int cacheSeconds() {
        return Math.max(0, cacheSeconds == null ? 0 : cacheSeconds.getAsInt());
    }

    private static Response text(int status, String body) {
        return new Response(status, TEXT, body.getBytes(StandardCharsets.UTF_8), 0);
    }

    enum Format {
        PNG,
        BINARY,
        DATA
    }

    @FunctionalInterface
    interface PngProvider {
        byte[] png(String worldName, int minX, int minZ, int worldSize) throws IOException;
    }

    @FunctionalInterface
    interface BinaryProvider {
        byte[] binary(String worldName, int minX, int minZ, int worldSize) throws IOException;
    }

    @FunctionalInterface
    interface RasterProvider {
        TerrainTileRaster raster(String worldName, int minX, int minZ, int worldSize) throws IOException;
    }

    record Response(int status, String contentType, byte[] body, int maxAgeSeconds) {
    }

    private record Request(String world, int minX, int minZ, int worldSize) {
        private static Request parse(Map<String, String> params) {
            String worldName = params == null ? null : params.get("world");
            if (worldName == null || worldName.isBlank()) {
                throw new IllegalArgumentException("Missing world");
            }
            return new Request(
                worldName,
                intParam(params, "x"),
                intParam(params, "z"),
                Math.clamp(intParam(params, "size"), 1, 4096)
            );
        }

        private static int intParam(Map<String, String> params, String name) {
            String value = params.get(name);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing " + name);
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid " + name);
            }
        }
    }
}
