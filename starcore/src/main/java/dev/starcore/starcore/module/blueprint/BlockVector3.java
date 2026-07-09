package dev.starcore.starcore.module.blueprint;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;

/**
 * 三维坐标向量
 * 用于存储蓝图中的方块位置
 */
public class BlockVector3 {
    private final int x;
    private final int y;
    private final int z;

    public BlockVector3(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static BlockVector3 at(int x, int y, int z) {
        return new BlockVector3(x, y, z);
    }

    public static BlockVector3 fromLocation(Location location) {
        return new BlockVector3(
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ()
        );
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public BlockVector3 add(int dx, int dy, int dz) {
        return new BlockVector3(x + dx, y + dy, z + dz);
    }

    public BlockVector3 subtract(int dx, int dy, int dz) {
        return new BlockVector3(x - dx, y - dy, z - dz);
    }

    public BlockVector3 multiply(int factor) {
        return new BlockVector3(x * factor, y * factor, z * factor);
    }

    public Vector toVector() {
        return new Vector(x, y, z);
    }

    public double distance(BlockVector3 other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public double distanceSq(BlockVector3 other) {
        int dx = x - other.x;
        int dy = y - other.y;
        int dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        BlockVector3 that = (BlockVector3) obj;
        return x == that.x && y == that.y && z == that.z;
    }

    @Override
    public int hashCode() {
        return (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }

    /**
     * 转换为长整数用于紧凑存储
     */
    public long toLong() {
        return ((long) x & 0x3FFFFFF) << 38 |
               ((long) z & 0x3FFFFFF) << 12 |
               ((long) y & 0xFFF);
    }

    /**
     * 从长整数恢复
     */
    public static BlockVector3 fromLong(long value) {
        int x = (int) ((value >> 38) & 0x3FFFFFF);
        int z = (int) ((value >> 12) & 0x3FFFFFF);
        int y = (int) (value & 0xFFF);
        if (x >= 0x2000000) x -= 0x4000000;
        if (z >= 0x2000000) z -= 0x4000000;
        if (y >= 0x800) y -= 0x1000;
        return new BlockVector3(x, y, z);
    }

    /**
     * 获取相对于另一个点的偏移量
     */
    public BlockVector3 subtract(BlockVector3 origin) {
        return new BlockVector3(x - origin.x, y - origin.y, z - origin.z);
    }

    /**
     * 获取相对于另一个点的偏移量（浮点）
     */
    public double getXOffset(BlockVector3 origin) {
        return x - origin.x;
    }

    public double getYOffset(BlockVector3 origin) {
        return y - origin.y;
    }

    public double getZOffset(BlockVector3 origin) {
        return z - origin.z;
    }
}
