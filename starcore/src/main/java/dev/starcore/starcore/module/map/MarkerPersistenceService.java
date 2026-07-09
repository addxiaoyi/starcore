package dev.starcore.starcore.module.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dev.starcore.starcore.module.map.model.CustomMapMarker;
import dev.starcore.starcore.module.map.model.MapMarkerCategory;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Comparator;

/**
 * 地图标记持久化服务
 * 负责保存和加载地图标记数据
 */
public class MarkerPersistenceService implements MapMarkerService.MarkerPersistenceService {

    private final Plugin plugin;
    private final Path dataDirectory;
    private final Gson gson;
    private final Map<String, CustomMapMarker> markerCache = new ConcurrentHashMap<>();
    // E-081/E-083: 使用单线程 executor 串行化保存操作，避免缓存与文件不一致
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MarkerPersistence");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean savePending = new AtomicBoolean(false);

    private static final String MARKER_FILE = "markers.json";
    private static final String MARKER_INDEX_FILE = "markers.index";

    public MarkerPersistenceService(Plugin plugin) {
        this.plugin = plugin;
        this.dataDirectory = plugin.getDataFolder().toPath().resolve("map");
        this.gson = createGson();
        ensureDataDirectory();
    }

    private void ensureDataDirectory() {
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            plugin.getLogger().warning("无法创建地图标记数据目录: " + e.getMessage());
        }
    }

    private Gson createGson() {
        return new GsonBuilder()
            .setPrettyPrinting()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .registerTypeAdapter(Instant.class, new InstantAdapter())
            .registerTypeAdapter(UUID.class, new UuidAdapter())
            .create();
    }

    @Override
    public void saveMarker(CustomMapMarker marker) {
        // E-083: 先更新缓存再异步保存，保持原子性
        markerCache.put(marker.getId(), marker);
        saveAllMarkers();
    }

    @Override
    public void deleteMarker(String markerId) {
        markerCache.remove(markerId);
        saveAllMarkers();
    }

    // E-081: 提供主动 flush 方法供模块关闭时调用
    public void flushSync() {
        savePending.set(false);
        saveAllMarkers();
        saveExecutor.shutdown();
        try {
            if (!saveExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                saveExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            saveExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public List<CustomMapMarker> loadAllMarkers() {
        File markerFile = dataDirectory.resolve(MARKER_FILE).toFile();

        if (!markerFile.exists()) {
            return List.of();
        }

        try (Reader reader = new InputStreamReader(
                new FileInputStream(markerFile), StandardCharsets.UTF_8)) {

            Type listType = new TypeToken<List<MarkerData>>(){}.getType();
            List<MarkerData> dataList = gson.fromJson(reader, listType);

            if (dataList == null || dataList.isEmpty()) {
                return List.of();
            }

            List<CustomMapMarker> markers = new ArrayList<>();
            for (MarkerData data : dataList) {
                try {
                    CustomMapMarker marker = data.toMarker();
                    markerCache.put(marker.getId(), marker);
                    markers.add(marker);
                } catch (Exception e) {
                    plugin.getLogger().warning("加载标记失败: " + data.id + " - " + e.getMessage());
                }
            }

            return markers;

        } catch (IOException e) {
            plugin.getLogger().warning("读取标记文件失败: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * 保存所有标记到磁盘
     * E-081: 异步保存避免阻塞主线程; E-082: 原子写入临时文件后 move
     */
    public void saveAllMarkers() {
        // E-081: 避免高频调用时重复保存，直接返回让后台任务合并
        if (!savePending.compareAndSet(false, true)) {
            return;
        }
        final Map<String, CustomMapMarker> snapshot = Map.copyOf(markerCache);
        saveExecutor.submit(() -> {
            try {
                File markerFile = dataDirectory.resolve(MARKER_FILE).toFile();
                Path tempFile = dataDirectory.resolve(MARKER_FILE + ".tmp");
                List<MarkerData> dataList = new ArrayList<>();
                for (CustomMapMarker marker : snapshot.values()) {
                    dataList.add(MarkerData.fromMarker(marker));
                }
                try (Writer writer = new OutputStreamWriter(
                        new FileOutputStream(tempFile.toFile()), StandardCharsets.UTF_8)) {
                    gson.toJson(dataList, writer);
                }
                // E-082: atomic move
                Files.move(tempFile, markerFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception e) {
                plugin.getLogger().warning("保存标记文件失败: " + e.getMessage());
            } finally {
                savePending.set(false);
            }
        });
    }

    /**
     * 重新加载所有标记
     */
    public List<CustomMapMarker> reload() {
        markerCache.clear();
        return loadAllMarkers();
    }

    /**
     * 获取标记缓存
     */
    public Collection<CustomMapMarker> getCachedMarkers() {
        return markerCache.values();
    }

    /**
     * 备份标记数据
     */
    public void backup(String backupName) throws IOException {
        File source = dataDirectory.resolve(MARKER_FILE).toFile();
        if (!source.exists()) {
            return;
        }

        Path backupDir = dataDirectory.resolve("backups");
        Files.createDirectories(backupDir);

        Path backupFile = backupDir.resolve("markers_" + backupName + ".json");
        Files.copy(source.toPath(), backupFile);

        plugin.getLogger().info("标记数据已备份到: " + backupFile.getFileName());
    }

    /**
     * 清理旧备份
     */
    public void cleanupOldBackups(int keepCount) {
        Path backupDir = dataDirectory.resolve("backups");
        if (!Files.exists(backupDir)) {
            return;
        }

        try {
            var backupFiles = Files.list(backupDir)
                .filter(p -> p.getFileName().toString().startsWith("markers_"))
                .filter(p -> p.getFileName().toString().endsWith(".json"))
                .sorted((p1, p2) -> {
                    try {
                        return Long.compare(Files.getLastModifiedTime(p2).toMillis(), Files.getLastModifiedTime(p1).toMillis());
                    } catch (java.io.IOException e) {
                        return 0;
                    }
                })
                .skip(keepCount)
                .toList();

            for (Path file : backupFiles) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("清理备份失败: " + e.getMessage());
        }
    }

    /**
     * 导出标记数据
     */
    public void exportToFile(Path targetPath) throws IOException {
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(targetPath.toFile()), StandardCharsets.UTF_8)) {

            List<MarkerData> dataList = new ArrayList<>();
            for (CustomMapMarker marker : markerCache.values()) {
                dataList.add(MarkerData.fromMarker(marker));
            }

            gson.toJson(dataList, writer);
        }
    }

    /**
     * 从文件导入标记数据
     */
    public int importFromFile(Path sourcePath) throws IOException {
        try (Reader reader = new InputStreamReader(
                new FileInputStream(sourcePath.toFile()), StandardCharsets.UTF_8)) {

            Type listType = new TypeToken<List<MarkerData>>(){}.getType();
            List<MarkerData> dataList = gson.fromJson(reader, listType);

            if (dataList == null) {
                return 0;
            }

            int imported = 0;
            for (MarkerData data : dataList) {
                try {
                    CustomMapMarker marker = data.toMarker();
                    if (!markerCache.containsKey(marker.getId())) {
                        markerCache.put(marker.getId(), marker);
                        imported++;
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("导入标记失败: " + data.id + " - " + e.getMessage());
                }
            }

            saveAllMarkers();
            return imported;

        }
    }

    // ==================== 内部数据类 ====================

    /**
     * 标记数据（用于JSON序列化）
     */
    private static class MarkerData {
        String id;
        String ownerId;
        String name;
        String world;
        double x;
        double y;
        double z;
        String category;
        String icon;
        String color;
        String description;
        boolean pinned;
        boolean visibleToNation;
        boolean visibleToAll;
        String createdAt;
        String updatedAt;
        Map<String, String> metadata;

        static MarkerData fromMarker(CustomMapMarker marker) {
            MarkerData data = new MarkerData();
            data.id = marker.getId();
            data.ownerId = marker.getOwnerId().toString();
            data.name = marker.getName();
            data.world = marker.getWorld();
            data.x = marker.getX();
            data.y = marker.getY();
            data.z = marker.getZ();
            data.category = marker.getCategory().name();
            data.icon = marker.getIcon();
            data.color = marker.getColor();
            data.description = marker.getDescription();
            data.pinned = marker.isPinned();
            data.visibleToNation = marker.isVisibleToNation();
            data.visibleToAll = marker.isVisibleToAll();
            data.createdAt = marker.getCreatedAt().toString();
            data.updatedAt = marker.getUpdatedAt().toString();
            data.metadata = new HashMap<>(marker.getMetadata());
            return data;
        }

        CustomMapMarker toMarker() {
            return CustomMapMarker.builder()
                .id(id)
                .ownerId(UUID.fromString(ownerId))
                .name(name)
                .world(world)
                .position(x, y, z)
                .category(MapMarkerCategory.valueOf(category))
                .icon(icon)
                .color(color)
                .description(description)
                .pinned(pinned)
                .visibleToNation(visibleToNation)
                .visibleToAll(visibleToAll)
                .createdAt(Instant.parse(createdAt))
                .updatedAt(Instant.parse(updatedAt))
                .metadata(metadata != null ? metadata : Map.of())
                .build();
        }
    }

    // ==================== Gson适配器 ====================

    private static class InstantAdapter extends TypeAdapter<Instant> {
        @Override
        public void write(JsonWriter out, Instant value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public Instant read(JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return Instant.parse(in.nextString());
        }
    }

    private static class UuidAdapter extends TypeAdapter<UUID> {
        @Override
        public void write(JsonWriter out, UUID value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public UUID read(JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return UUID.fromString(in.nextString());
        }
    }
}
