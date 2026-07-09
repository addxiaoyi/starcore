package dev.starcore.starcore.storage;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 仓库实体类
 * 表示一个完整的仓库，包含所有物品和元数据
 */
public class Warehouse {
    private final UUID warehouseId;
    private String name;
    private final WarehouseType type;
    private UUID ownerId; // 玩家UUID或国家ID
    // E-017: level 由 WarehouseUpgradeService.scheduleUpgradeCompletion 在异步线程修改,主线程读,
    // 加 volatile 保证可见性(happens-before)
    private volatile int level;
    private final Map<Integer, StorageItem> items; // 槽位 -> 物品
    private final Instant createdTime;
    private Instant lastAccessTime;
    private boolean locked;

    // E-015: 缓存当前等级 WarehouseLevel,避免 getCapacity/getUsedCapacity/isFull 链式调用每次 new。
    // 升级时 clear 让下次重新生成
    private volatile WarehouseLevel cachedLevelConfig;

    /**
     * 完整构造函数
     * @param warehouseId 仓库唯一ID
     * @param name 仓库名称
     * @param type 仓库类型
     * @param ownerId 所有者ID
     * @param level 当前等级
     * @param createdTime 创建时间
     */
    public Warehouse(UUID warehouseId, String name, WarehouseType type,
                     UUID ownerId, int level, Instant createdTime) {
        this.warehouseId = Objects.requireNonNull(warehouseId, "warehouseId cannot be null");
        this.name = name != null ? name : "仓库";
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.ownerId = ownerId;
        this.level = Math.max(1, Math.min(level, type.getMaxLevel()));
        this.items = new ConcurrentHashMap<>();
        this.createdTime = createdTime != null ? createdTime : Instant.now();
        this.lastAccessTime = Instant.now();
        this.locked = false;
    }

    /**
     * 简化构造函数（创建新仓库）
     * @param type 仓库类型
     * @param ownerId 所有者ID
     * @param name 仓库名称
     */
    public Warehouse(WarehouseType type, UUID ownerId, String name) {
        this(UUID.randomUUID(), name, type, ownerId, 1, Instant.now());
    }

    // ==================== Getters ====================

    public UUID getWarehouseId() {
        return warehouseId;
    }

    public String getName() {
        return name;
    }

    public WarehouseType getType() {
        return type;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public int getLevel() {
        return level;
    }

    public Instant getCreatedTime() {
        return createdTime;
    }

    public Instant getLastAccessTime() {
        return lastAccessTime;
    }

    public boolean isLocked() {
        return locked;
    }

    // ==================== Setters ====================

    public void setName(String name) {
        this.name = name != null ? name : "仓库";
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    /**
     * 更新最后访问时间
     */
    public void updateAccessTime() {
        this.lastAccessTime = Instant.now();
    }

    // ==================== 容量管理 ====================

    /**
     * 获取当前容量（格子数）
     * @return 容量
     */
    public int getCapacity() {
        // E-015: 缓存 levelConfig 避免每次 getCapacity/getRemainingCapacity/isFull 链式调用都 new WarehouseLevel
        WarehouseLevel cfg = cachedLevelConfig;
        if (cfg == null) {
            synchronized (this) {
                cfg = cachedLevelConfig;
                if (cfg == null) {
                    cfg = WarehouseLevel.createDefault(type, level);
                    cachedLevelConfig = cfg;
                }
            }
        }
        return cfg.getCapacity();
    }

    /**
     * 获取已使用的格子数
     * @return 已使用容量
     */
    public int getUsedCapacity() {
        return items.size();
    }

    /**
     * 获取剩余容量
     * @return 剩余格子数
     */
    public int getRemainingCapacity() {
        return getCapacity() - getUsedCapacity();
    }

    /**
     * 检查是否已满
     * @return true如果仓库已满
     */
    public boolean isFull() {
        return getUsedCapacity() >= getCapacity();
    }

    /**
     * 获取使用率（百分比）
     * @return 0.0 到 1.0
     */
    public double getUsagePercentage() {
        int capacity = getCapacity();
        return capacity > 0 ? (double) getUsedCapacity() / capacity : 0.0;
    }

    // ==================== 物品管理 ====================

    /**
     * 获取所有物品
     * @return 物品副本的不可变映射
     */
    public Map<Integer, StorageItem> getItems() {
        return Collections.unmodifiableMap(items);
    }

    /**
     * 获取指定槽位的物品
     * @param slot 槽位
     * @return 物品，如果槽位为空则返回null
     */
    public StorageItem getItem(int slot) {
        return items.get(slot);
    }

    /**
     * 设置指定槽位的物品
     * @param slot 槽位
     * @param item 物品
     * @return true如果设置成功
     */
    public boolean setItem(int slot, StorageItem item) {
        if (slot < 0 || slot >= getCapacity()) {
            return false;
        }
        if (item == null) {
            items.remove(slot);
        } else {
            items.put(slot, item.withSlot(slot));
        }
        return true;
    }

    /**
     * 移除指定槽位的物品
     * @param slot 槽位
     * @return 被移除的物品，如果槽位为空则返回null
     */
    public StorageItem removeItem(int slot) {
        return items.remove(slot);
    }

    /**
     * 清空所有物品
     */
    public void clearItems() {
        items.clear();
    }

    /**
     * 查找第一个空槽位
     * @return 槽位索引，如果已满则返回-1
     */
    public int findEmptySlot() {
        int capacity = getCapacity();
        for (int i = 0; i < capacity; i++) {
            if (!items.containsKey(i)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 添加物品到仓库（自动寻找空槽位）
     * @param item 物品
     * @return true如果添加成功
     */
    public boolean addItem(StorageItem item) {
        if (item == null) {
            return false;
        }
        // E-016: 原 findEmptySlot + setItem 两步非原子,多线程并发添加时两个线程可能拿到同一 empty slot
        // 然后 setItem 后者覆盖前者,导致玩家物品丢失。改用 putIfAbsent 原子扫描+占位,
        // 命中已被占用的 slot 时跳到下一个,避免覆盖。
        int capacity = getCapacity();
        for (int i = 0; i < capacity; i++) {
            StorageItem toInsert = item.withSlot(i);
            StorageItem prev = items.putIfAbsent(i, toInsert);
            if (prev == null) {
                // 成功占用空槽
                return true;
            }
            // 该槽已有物品,继续找下一个 (i++)
        }
        return false;
    }

    // ==================== 升级管理 ====================

    /**
     * 升级仓库
     * @return true如果升级成功
     */
    public boolean upgrade() {
        if (level >= type.getMaxLevel()) {
            return false;
        }
        level++;
        // E-015: 升级后 capacity 改变,旧缓存失效
        cachedLevelConfig = null;
        return true;
    }

    /**
     * 获取下一级配置
     * @return 下一级的配置，如果已达最大等级则返回null
     */
    public WarehouseLevel getNextLevelConfig() {
        if (level >= type.getMaxLevel()) {
            return null;
        }
        return WarehouseLevel.createDefault(type, level + 1);
    }

    /**
     * 获取当前等级配置
     * @return 当前等级的配置
     */
    public WarehouseLevel getCurrentLevelConfig() {
        return WarehouseLevel.createDefault(type, level);
    }

    /**
     * 检查是否可以升级
     * @return true如果未达最大等级
     */
    public boolean canUpgrade() {
        return level < type.getMaxLevel();
    }

    // ==================== 工具方法 ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Warehouse warehouse = (Warehouse) o;
        return Objects.equals(warehouseId, warehouse.warehouseId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(warehouseId);
    }

    @Override
    public String toString() {
        return "Warehouse{" +
                "id=" + warehouseId +
                ", name='" + name + '\'' +
                ", type=" + type +
                ", owner=" + ownerId +
                ", level=" + level +
                ", capacity=" + getCapacity() +
                ", used=" + getUsedCapacity() +
                ", locked=" + locked +
                '}';
    }
}
