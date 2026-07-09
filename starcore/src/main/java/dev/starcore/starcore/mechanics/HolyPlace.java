package dev.starcore.starcore.mechanics;

import org.bukkit.Location;

import java.util.UUID;

/**
 * 圣地
 * 宗教的神圣场所
 */
public class HolyPlace {

    private final UUID id;
    private final ReligionType religion;
    private final Location location;
    private final String name;
    private final int radius;
    private int holyPower; // 圣力值
    private long lastBlessing; // 上次祝福时间

    public HolyPlace(UUID id, ReligionType religion, Location location, String name, int radius) {
        this.id = id;
        this.religion = religion;
        this.location = location;
        this.name = name;
        this.radius = radius;
        this.holyPower = 100;
        this.lastBlessing = System.currentTimeMillis();
    }

    /**
     * 检查位置是否在圣地范围内
     */
    public boolean isInRange(Location loc) {
        if (!loc.getWorld().equals(location.getWorld())) {
            return false;
        }
        return loc.distanceSquared(location) <= radius * radius;
    }

    /**
     * 增加圣力
     */
    public void addHolyPower(int amount) {
        this.holyPower = Math.min(1000, this.holyPower + amount);
    }

    /**
     * 消耗圣力
     */
    public boolean consumeHolyPower(int amount) {
        if (holyPower >= amount) {
            this.holyPower -= amount;
            return true;
        }
        return false;
    }

    /**
     * 执行祝福
     */
    public void performBlessing() {
        this.lastBlessing = System.currentTimeMillis();
        consumeHolyPower(10);
    }

    /**
     * 检查是否可以进行祝福
     */
    public boolean canBless() {
        long timeSinceBlessing = System.currentTimeMillis() - lastBlessing;
        return timeSinceBlessing >= 60000 && holyPower >= 10; // 1分钟冷却
    }

    /**
     * 获取圣地描述
     */
    public String getDescription() {
        return String.format(
            "%s%s\n§7类型: %s\n§7圣力: §f%d/1000\n§7范围: §f%d格",
            religion.getColorCode(),
            name,
            religion.getColoredName(),
            holyPower,
            radius
        );
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public ReligionType getReligion() {
        return religion;
    }

    public Location getLocation() {
        return location.clone();
    }

    public String getName() {
        return name;
    }

    public int getRadius() {
        return radius;
    }

    public int getHolyPower() {
        return holyPower;
    }

    public void setHolyPower(int holyPower) {
        this.holyPower = Math.max(0, Math.min(1000, holyPower));
    }

    public long getLastBlessing() {
        return lastBlessing;
    }

    public void setLastBlessing(long lastBlessing) {
        this.lastBlessing = lastBlessing;
    }
}
