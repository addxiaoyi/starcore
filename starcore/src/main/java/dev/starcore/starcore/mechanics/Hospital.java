package dev.starcore.starcore.mechanics;

import dev.starcore.starcore.util.ColorCodes;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 医院建筑
 * 提供疾病治疗服务的建筑
 */
public class Hospital {

    private final UUID id;
    private final String name;
    private final Location location;
    private final UUID ownerId; // 所有者（国家或城市）
    private int level; // 医院等级 (1-5)
    private int capacity; // 床位数量
    private int staff; // 医护人员数量
    private final Map<UUID, Long> patients; // 当前患者及其入院时间

    public Hospital(UUID id, String name, Location location, UUID ownerId) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.ownerId = ownerId;
        this.level = 1;
        this.capacity = 10;
        this.staff = 2;
        this.patients = new HashMap<>();
    }

    /**
     * 升级医院
     */
    public void upgrade() {
        if (level < 5) {
            this.level++;
            this.capacity += 10;
            this.staff += 2;
        }
    }

    /**
     * 检查是否有空床位
     */
    public boolean hasAvailableBed() {
        return patients.size() < capacity;
    }

    /**
     * 入院
     */
    public boolean admit(UUID playerId) {
        if (!hasAvailableBed()) {
            return false;
        }

        patients.put(playerId, System.currentTimeMillis());
        return true;
    }

    /**
     * 出院
     */
    public void discharge(UUID playerId) {
        patients.remove(playerId);
    }

    /**
     * 检查玩家是否在院
     */
    public boolean isPatient(UUID playerId) {
        return patients.containsKey(playerId);
    }

    /**
     * 获取住院时长（小时）
     */
    public long getHospitalizationHours(UUID playerId) {
        Long admitTime = patients.get(playerId);
        if (admitTime == null) return 0;

        return (System.currentTimeMillis() - admitTime) / (1000 * 60 * 60);
    }

    /**
     * 检查位置是否在医院范围内
     */
    public boolean isInRange(Location loc) {
        if (!loc.getWorld().equals(location.getWorld())) {
            return false;
        }

        int range = 50 + (level * 10); // 基础50格，每级+10格
        return loc.distanceSquared(location) <= range * range;
    }

    /**
     * 获取治疗效率加成
     */
    public double getTreatmentEfficiency() {
        // 基于等级和医护人员比例
        double staffRatio = (double) staff / capacity;
        return 1.0 + (level * 0.1) + (staffRatio * 0.2);
    }

    /**
     * 获取医院描述
     */
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("§b§l").append(name).append("\n");
        sb.append("§7等级: §f").append(level).append(" 级\n");
        sb.append("§7床位: §f").append(patients.size()).append("/").append(capacity).append("\n");
        sb.append("§7医护人员: §f").append(staff).append(" 人\n");
        sb.append("§7治疗效率: §a+").append(String.format("%.0f", (getTreatmentEfficiency() - 1.0) * 100)).append("%\n");

        if (patients.isEmpty()) {
            sb.append("\n§a当前无住院患者");
        } else {
            sb.append("\n§e当前住院: §f").append(patients.size()).append(" 人");
        }

        return sb.toString();
    }

    /**
     * 获取升级费用
     */
    public int getUpgradeCost() {
        return level * 10000;
    }

    /**
     * 获取每日维护费用
     */
    public int getMaintenanceCost() {
        return (capacity * 10) + (staff * 50);
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Location getLocation() {
        return location.clone();
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, Math.min(5, level));
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    public int getStaff() {
        return staff;
    }

    public void setStaff(int staff) {
        this.staff = Math.max(1, staff);
    }

    public Map<UUID, Long> getPatients() {
        return new HashMap<>(patients);
    }
}
