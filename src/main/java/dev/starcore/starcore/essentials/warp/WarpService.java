package dev.starcore.starcore.essentials.warp;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.bukkit.Location;

/**
 * 传送点系统
 * 管理全服公共传送点
 */
public final class WarpService {
    // 所有传送点 名称 -> 位置 - 使用ConcurrentHashMap保证线程安全
    private final Map<String, Location> warps = new ConcurrentHashMap<>();

    /**
     * 设置传送点
     */
    public boolean setWarp(String name, Location location) {
        if (name == null || name.isBlank()) {
            return false;
        }

        warps.put(name.toLowerCase(), location.clone());
        return true;
    }

    /**
     * 删除传送点
     */
    public boolean deleteWarp(String name) {
        if (name == null) {
            return false;
        }

        return warps.remove(name.toLowerCase()) != null;
    }

    /**
     * 获取传送点
     */
    public Optional<Location> getWarp(String name) {
        if (name == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(warps.get(name.toLowerCase()));
    }

    /**
     * 获取所有传送点名称
     */
    public List<String> getWarpNames() {
        return List.copyOf(warps.keySet());
    }

    /**
     * 检查传送点是否存在
     */
    public boolean warpExists(String name) {
        if (name == null) {
            return false;
        }

        return warps.containsKey(name.toLowerCase());
    }

    /**
     * 获取传送点数量
     */
    public int getWarpCount() {
        return warps.size();
    }

    /**
     * 加载数据
     */
    public void loadData(Map<String, Location> data) {
        warps.clear();
        warps.putAll(data);
    }

    /**
     * 保存数据
     */
    public Map<String, Location> saveData() {
        return new ConcurrentHashMap<>(warps);
    }

    /**
     * 清空所有传送点
     */
    public void clear() {
        warps.clear();
    }
}
