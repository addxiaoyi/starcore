package dev.starcore.starcore.module.blueprint;

import org.bukkit.Location;

/**
 * 二维坐标向量
 * 用于存储蓝图中的平面坐标位置
 */
public class BlockVector2 {
    private final int x;
    private final int z;

    public BlockVector2(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public static BlockVector2 at(int x, int z) {
        return new BlockVector2(x, z);
    }

    public static BlockVector2 fromLocation(Location location) {
        return new BlockVector2(
            location.getBlockX(),
            location.getBlockZ()
        );
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    public BlockVector2 add(int dx, int dz) {
        return new BlockVector2(x + dx, z + dz);
    }

    public BlockVector2 subtract(int dx, int dz) {
        return new BlockVector2(x - dx, z - dz);
    }

    public BlockVector2 multiply(int factor) {
        return new BlockVector2(x * factor, z * factor);
    }

    public double distance(BlockVector2 other) {
        double dx = x - other.x;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public double distanceSq(BlockVector2 other) {
        int dx = x - other.x;
        int dz = z - other.z;
        return dx * dx + dz * dz;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        BlockVector2 that = (BlockVector2) obj;
        return x == that.x && z == that.z;
    }

    @Override
    public int hashCode() {
        return (x * 73856093) ^ (z * 19349663);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + z + ")";
    }
}