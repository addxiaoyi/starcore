package dev.starcore.starcore.module.map;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

final class TerrainTileMemoryCache {
    private final Map<TerrainTileKey, ByteEntry> pngTiles = new ConcurrentHashMap<>();
    private final Map<TerrainTileKey, ByteEntry> binaryTiles = new ConcurrentHashMap<>();
    private final Map<TerrainTileKey, RasterEntry> rasters = new ConcurrentHashMap<>();

    byte[] png(TerrainTileKey key, long nowMillis, long ttlMillis, BiPredicate<TerrainTileKey, Long> invalidatedAfter) {
        return bytes(pngTiles, key, nowMillis, ttlMillis, invalidatedAfter);
    }

    void putPng(TerrainTileKey key, byte[] bytes, long createdAtMillis, long ttlMillis, int maxEntries) {
        putBytes(pngTiles, key, bytes, createdAtMillis, ttlMillis, maxEntries);
    }

    byte[] binary(TerrainTileKey key, long nowMillis, long ttlMillis, BiPredicate<TerrainTileKey, Long> invalidatedAfter) {
        return bytes(binaryTiles, key, nowMillis, ttlMillis, invalidatedAfter);
    }

    void putBinary(TerrainTileKey key, byte[] bytes, long createdAtMillis, long ttlMillis, int maxEntries) {
        putBytes(binaryTiles, key, bytes, createdAtMillis, ttlMillis, maxEntries);
    }

    TerrainTileRaster raster(TerrainTileKey key, long nowMillis, long ttlMillis, BiPredicate<TerrainTileKey, Long> invalidatedAfter) {
        RasterEntry entry = rasters.get(key);
        if (entry == null) {
            return null;
        }
        if (fresh(entry.createdAtMillis(), nowMillis, ttlMillis) && !isInvalidated(key, entry.createdAtMillis(), invalidatedAfter)) {
            return entry.raster();
        }
        rasters.remove(key, entry);
        return null;
    }

    void putRaster(TerrainTileKey key, TerrainTileRaster raster, long createdAtMillis, long ttlMillis, int maxEntries) {
        if (key == null || raster == null) {
            return;
        }
        rasters.put(key, new RasterEntry(raster, createdAtMillis));
        pruneRasters(createdAtMillis, ttlMillis, maxEntries);
    }

    void remove(TerrainTileKey key) {
        pngTiles.remove(key);
        binaryTiles.remove(key);
        rasters.remove(key);
    }

    void clear() {
        pngTiles.clear();
        binaryTiles.clear();
        rasters.clear();
    }

    int pngSize() {
        return pngTiles.size();
    }

    int binarySize() {
        return binaryTiles.size();
    }

    int rasterSize() {
        return rasters.size();
    }

    private byte[] bytes(Map<TerrainTileKey, ByteEntry> cache, TerrainTileKey key, long nowMillis, long ttlMillis, BiPredicate<TerrainTileKey, Long> invalidatedAfter) {
        ByteEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (fresh(entry.createdAtMillis(), nowMillis, ttlMillis) && !isInvalidated(key, entry.createdAtMillis(), invalidatedAfter)) {
            return entry.bytes();
        }
        cache.remove(key, entry);
        return null;
    }

    private void putBytes(Map<TerrainTileKey, ByteEntry> cache, TerrainTileKey key, byte[] bytes, long createdAtMillis, long ttlMillis, int maxEntries) {
        if (key == null || bytes == null || bytes.length == 0) {
            return;
        }
        cache.put(key, new ByteEntry(bytes, createdAtMillis));
        pruneBytes(cache, createdAtMillis, ttlMillis, maxEntries);
    }

    private boolean fresh(long createdAtMillis, long nowMillis, long ttlMillis) {
        return ttlMillis > 0L && nowMillis - createdAtMillis < ttlMillis;
    }

    private boolean isInvalidated(TerrainTileKey key, long createdAtMillis, BiPredicate<TerrainTileKey, Long> invalidatedAfter) {
        return invalidatedAfter != null && invalidatedAfter.test(key, createdAtMillis);
    }

    private void pruneBytes(Map<TerrainTileKey, ByteEntry> cache, long nowMillis, long ttlMillis, int maxEntries) {
        // E-084: 简化清理逻辑，先删除过期项，再按需删除超出容量的旧项，避免 O(n log n) 排序
        cache.entrySet().removeIf(entry -> !fresh(entry.getValue().createdAtMillis(), nowMillis, ttlMillis));
        if (cache.size() <= Math.max(1, maxEntries)) {
            return;
        }
        // 按创建时间排序后删除最早的，超出部分最多删 maxEntries/2 条以减少频繁清理
        int toRemove = Math.min(cache.size() - maxEntries, Math.max(10, maxEntries / 10));
        List<TerrainTileKey> keysToRemove = cache.entrySet().stream()
            .sorted(Map.Entry.comparingByValue((left, right) -> Long.compare(left.createdAtMillis(), right.createdAtMillis())))
            .limit(toRemove)
            .map(Map.Entry::getKey)
            .toList();
        keysToRemove.forEach(cache::remove);
    }

    private void pruneRasters(long nowMillis, long ttlMillis, int maxEntries) {
        rasters.entrySet().removeIf(entry -> !fresh(entry.getValue().createdAtMillis(), nowMillis, ttlMillis));
        if (rasters.size() <= Math.max(1, maxEntries)) {
            return;
        }
        int toRemove = Math.min(rasters.size() - maxEntries, Math.max(10, maxEntries / 10));
        List<TerrainTileKey> keysToRemove = rasters.entrySet().stream()
            .sorted(Map.Entry.comparingByValue((left, right) -> Long.compare(left.createdAtMillis(), right.createdAtMillis())))
            .limit(toRemove)
            .map(Map.Entry::getKey)
            .toList();
        keysToRemove.forEach(rasters::remove);
    }

    private record ByteEntry(byte[] bytes, long createdAtMillis) {
    }

    private record RasterEntry(TerrainTileRaster raster, long createdAtMillis) {
    }
}
