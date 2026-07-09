package dev.starcore.starcore.webmap;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * WebMap更新管理器
 * 管理地图数据的缓存和更新
 */
public class WebMapUpdateManager {

    private final Plugin plugin;
    private final MapDataProvider dataProvider;

    // 缓存数据
    private final Map<String, CachedData<?>> cache = new ConcurrentHashMap<>();

    // 更新间隔（ticks）
    private long updateInterval = 100; // 5秒

    // 是否启用
    private boolean enabled = true;

    public WebMapUpdateManager(Plugin plugin, MapDataProvider dataProvider) {
        this.plugin = plugin;
        this.dataProvider = dataProvider;
    }

    /**
     * 启动自动更新
     */
    public void startAutoUpdate() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (enabled) {
                    updateAllData();
                }
            }
        }.runTaskTimerAsynchronously(plugin, 0L, updateInterval);

        plugin.getLogger().info("WebMap自动更新已启动（间隔: " + (updateInterval / 20) + "秒）");
    }

    /**
     * 更新所有数据
     */
    public void updateAllData() {
        try {
            // 更新Territory数据
            cache.put("territories", new CachedData<>(
                dataProvider.getTerritories(),
                System.currentTimeMillis()
            ));

            // 更新Nation数据
            cache.put("nations", new CachedData<>(
                dataProvider.getNations(),
                System.currentTimeMillis()
            ));

            // 更新City数据
            cache.put("cities", new CachedData<>(
                dataProvider.getCities(),
                System.currentTimeMillis()
            ));

            // 更新玩家数据（更频繁）
            cache.put("players", new CachedData<>(
                dataProvider.getOnlinePlayers(),
                System.currentTimeMillis()
            ));

            // 更新统计数据
            cache.put("stats", new CachedData<>(
                dataProvider.getStats(),
                System.currentTimeMillis()
            ));

        } catch (Exception e) {
            plugin.getLogger().warning("更新WebMap数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取缓存数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getCachedData(String key) {
        CachedData<?> cached = cache.get(key);
        if (cached == null) {
            return null;
        }
        return (T) cached.data;
    }

    /**
     * 获取缓存时间
     */
    public long getCacheTime(String key) {
        CachedData<?> cached = cache.get(key);
        return cached != null ? cached.timestamp : 0;
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * 设置更新间隔
     */
    public void setUpdateInterval(long ticks) {
        this.updateInterval = Math.max(20, ticks); // 最少1秒
    }

    /**
     * 启用/禁用更新
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    // ==================== 内部类 ====================

    /**
     * 缓存数据
     */
    private record CachedData<T>(T data, long timestamp) {}
}
