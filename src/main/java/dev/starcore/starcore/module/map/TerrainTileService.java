package dev.starcore.starcore.module.map;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

final class TerrainTileService {
    private final TerrainTileMemoryCache memoryCache = new TerrainTileMemoryCache();
    private final TerrainTileDiskCache diskCache;
    private final TerrainTileInvalidationService invalidation;
    private final TerrainTileRenderLimiter renderLimiter = new TerrainTileRenderLimiter();
    private final Map<TerrainTileKey, CompletableFuture<TerrainTileRaster>> rasterRenders = new ConcurrentHashMap<>();
    private final RasterRenderer renderer;
    private final RenderDispatcher renderDispatcher;

    TerrainTileService(
        Path dataFolder,
        TerrainTileInvalidationService invalidation,
        RasterRenderer renderer,
        RenderDispatcher renderDispatcher
    ) {
        this.diskCache = new TerrainTileDiskCache(dataFolder);
        this.invalidation = invalidation;
        this.renderer = renderer;
        this.renderDispatcher = renderDispatcher;
    }

    byte[] png(String worldName, int minX, int minZ, int worldSize, Settings settings) throws IOException {
        TerrainTileKey key = new TerrainTileKey(worldName, minX, minZ, worldSize);
        long now = System.currentTimeMillis();
        byte[] cached = memoryCache.png(key, now, settings.memoryCacheTtlMillis(), this::invalidatedAfter);
        if (cached != null) {
            return cached;
        }
        byte[] diskCached = diskCachedPng(key, now, settings);
        if (diskCached != null) {
            memoryCache.putPng(key, diskCached, now, settings.memoryCacheTtlMillis(), settings.memoryCacheMaxEntries());
            return diskCached;
        }

        TerrainTileRaster renderedRaster = raster(worldName, minX, minZ, worldSize, settings);
        if (renderedRaster == null) {
            return null;
        }
        byte[] rendered = TerrainTileCodec.encodePng(renderedRaster);
        long renderedAtMillis = renderedRaster.renderedAtMillis();
        memoryCache.putPng(key, rendered, renderedAtMillis, settings.memoryCacheTtlMillis(), settings.memoryCacheMaxEntries());
        writeDiskCacheAsync(key, renderedRaster.tileSize(), rendered, renderedAtMillis, settings);
        return rendered;
    }

    byte[] binary(String worldName, int minX, int minZ, int worldSize, Settings settings) throws IOException {
        TerrainTileKey key = new TerrainTileKey(worldName, minX, minZ, worldSize);
        long now = System.currentTimeMillis();
        byte[] cached = memoryCache.binary(key, now, settings.memoryCacheTtlMillis(), this::invalidatedAfter);
        if (cached != null) {
            return cached;
        }

        TerrainTileRaster renderedRaster = raster(worldName, minX, minZ, worldSize, settings);
        if (renderedRaster == null) {
            return null;
        }
        byte[] encoded = TerrainTileCodec.encodeBinary(renderedRaster);
        memoryCache.putBinary(key, encoded, renderedRaster.renderedAtMillis(), settings.memoryCacheTtlMillis(), settings.memoryCacheMaxEntries());
        return encoded;
    }

    TerrainTileRaster raster(String worldName, int minX, int minZ, int worldSize, Settings settings) throws IOException {
        TerrainTileKey key = new TerrainTileKey(worldName, minX, minZ, worldSize);
        long now = System.currentTimeMillis();
        TerrainTileRaster cached = memoryCache.raster(key, now, settings.memoryCacheTtlMillis(), this::invalidatedAfter);
        if (cached != null) {
            return cached;
        }

        CompletableFuture<TerrainTileRaster> future = rasterRenders.computeIfAbsent(key, ignored -> startRasterRender(key, settings));
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Terrain tile rendering interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Terrain tile rendering failed", cause);
        } finally {
            rasterRenders.remove(key, future);
        }
    }

    List<TerrainTileKey> markDirty(String worldName, int blockX, int blockZ, boolean includeNeighbors, long dirtyAt, long dirtyTtlMillis) {
        List<TerrainTileKey> dirtyKeys = invalidation.markDirty(worldName, blockX, blockZ, includeNeighbors, dirtyAt, dirtyTtlMillis);
        dirtyKeys.forEach(this::remove);
        return dirtyKeys;
    }

    long revision() {
        return invalidation.revision();
    }

    void scheduleRevisionBroadcast(org.bukkit.plugin.java.JavaPlugin plugin, boolean mapWebEnabled, Runnable broadcast) {
        invalidation.scheduleRevisionBroadcast(plugin, mapWebEnabled, broadcast);
    }

    void rememberWorld(String worldName, Path worldFolder) {
        diskCache.rememberWorld(worldName, worldFolder);
    }

    boolean regionFileExists(String worldName, int chunkX, int chunkZ) {
        return diskCache.regionFileExists(worldName, chunkX, chunkZ);
    }

    boolean isRendering(TerrainTileKey key) {
        return rasterRenders.containsKey(key);
    }

    int activeRenderJobs() {
        return renderLimiter.activeJobs();
    }

    void remove(TerrainTileKey key) {
        memoryCache.remove(key);
        rasterRenders.remove(key);
    }

    void clear() {
        invalidation.clear();
        memoryCache.clear();
        rasterRenders.clear();
        renderLimiter.reset();
        diskCache.clearRememberedWorlds();
    }

    private CompletableFuture<TerrainTileRaster> startRasterRender(TerrainTileKey key, Settings settings) {
        if (!renderLimiter.tryAcquire(settings.maxConcurrentRenders())) {
            return CompletableFuture.failedFuture(new TerrainTileBusyException());
        }
        CompletableFuture<TerrainTileRaster> renderFuture = new CompletableFuture<>();
        Runnable renderTask = () -> {
            long renderStartedAtMillis = System.currentTimeMillis();
            try {
                TerrainTileRaster rendered = renderer.render(key.world(), key.minX(), key.minZ(), key.worldSize(), renderStartedAtMillis);
                if (rendered != null) {
                    memoryCache.putRaster(key, rendered, renderStartedAtMillis, settings.memoryCacheTtlMillis(), settings.memoryCacheMaxEntries());
                    invalidation.clearDirtyBefore(key, renderStartedAtMillis);
                }
                renderFuture.complete(rendered);
            } catch (Throwable throwable) {
                renderFuture.completeExceptionally(throwable);
            } finally {
                renderLimiter.release();
            }
        };
        try {
            renderDispatcher.dispatch(renderTask);
        } catch (Throwable throwable) {
            renderLimiter.release();
            renderFuture.completeExceptionally(throwable);
        }
        return renderFuture;
    }

    private byte[] diskCachedPng(TerrainTileKey key, long nowMillis, Settings settings) {
        if (!settings.diskCacheEnabled()) {
            return null;
        }
        return diskCache.read(key, settings.tilePixels(), nowMillis, settings.diskCacheTtlMillis(), invalidation::dirtyAfter);
    }

    private void writeDiskCacheAsync(TerrainTileKey key, int tilePixels, byte[] bytes, long renderedAtMillis, Settings settings) {
        if (!settings.diskCacheEnabled() || settings.diskCacheWriter() == null) {
            return;
        }
        try {
            settings.diskCacheWriter().execute(() -> diskCache.write(key, tilePixels, bytes, renderedAtMillis));
        } catch (RuntimeException ignored) {
        }
    }

    private boolean invalidatedAfter(TerrainTileKey key, long cacheModifiedAtMillis) {
        return invalidation.invalidatedAfter(key, cacheModifiedAtMillis, diskCache::sourceModifiedAfter);
    }

    @FunctionalInterface
    interface RasterRenderer {
        TerrainTileRaster render(String worldName, int minX, int minZ, int worldSize, long renderedAtMillis) throws IOException;
    }

    @FunctionalInterface
    interface RenderDispatcher {
        void dispatch(Runnable renderTask);
    }

    record Settings(
        long memoryCacheTtlMillis,
        int memoryCacheMaxEntries,
        boolean diskCacheEnabled,
        long diskCacheTtlMillis,
        int tilePixels,
        int maxConcurrentRenders,
        Executor diskCacheWriter
    ) {
    }
}
