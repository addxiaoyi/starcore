package dev.starcore.starcore.module.blueprint;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * 立方体区域选择器
 */
public class CuboidRegion implements RegionSelection {
    private final String worldName;
    private final BlockVector3 min;
    private final BlockVector3 max;

    public CuboidRegion(String worldName, BlockVector3 min, BlockVector3 max) {
        this.worldName = worldName;
        // 确保 min 是最小点，max 是最大点
        int minX = Math.min(min.getX(), max.getX());
        int minY = Math.min(min.getY(), max.getY());
        int minZ = Math.min(min.getZ(), max.getZ());
        int maxX = Math.max(min.getX(), max.getX());
        int maxY = Math.max(min.getY(), max.getY());
        int maxZ = Math.max(min.getZ(), max.getZ());
        this.min = BlockVector3.at(minX, minY, minZ);
        this.max = BlockVector3.at(maxX, maxY, maxZ);
    }

    public CuboidRegion(Location loc1, Location loc2) {
        this(loc1.getWorld().getName(),
             BlockVector3.fromLocation(loc1),
             BlockVector3.fromLocation(loc2));
    }

    public static CuboidRegion fromCuboid(String worldName, int x1, int y1, int z1, int x2, int y2, int z2) {
        return new CuboidRegion(worldName, BlockVector3.at(x1, y1, z1), BlockVector3.at(x2, y2, z2));
    }

    @Override
    public String getType() {
        return "CUBOID";
    }

    @Override
    public List<BlockVector3> getBlocks() {
        List<BlockVector3> blocks = new ArrayList<>();
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    blocks.add(BlockVector3.at(x, y, z));
                }
            }
        }
        return blocks;
    }

    /**
     * 获取流式方块迭代器（内存效率更高）
     */
    public Stream<BlockVector3> getBlocksStream() {
        return IntStream.rangeClosed(min.getY(), max.getY())
            .boxed()
            .flatMap(y -> IntStream.rangeClosed(min.getX(), max.getX())
                .boxed()
                .flatMap(x -> IntStream.rangeClosed(min.getZ(), max.getZ())
                    .mapToObj(z -> BlockVector3.at(x, y, z))));
    }

    @Override
    public int getBlockCount() {
        long width = (long) max.getX() - min.getX() + 1;
        long height = (long) max.getY() - min.getY() + 1;
        long depth = (long) max.getZ() - min.getZ() + 1;
        return (int) Math.min(width * height * depth, Integer.MAX_VALUE);
    }

    @Override
    public int getWidth() {
        return max.getX() - min.getX() + 1;
    }

    @Override
    public int getHeight() {
        return max.getY() - min.getY() + 1;
    }

    @Override
    public int getDepth() {
        return max.getZ() - min.getZ() + 1;
    }

    @Override
    public boolean contains(BlockVector3 position) {
        return position.getX() >= min.getX() && position.getX() <= max.getX() &&
               position.getY() >= min.getY() && position.getY() <= max.getY() &&
               position.getZ() >= min.getZ() && position.getZ() <= max.getZ();
    }

    @Override
    public BlockVector3 getMinPoint() {
        return min;
    }

    @Override
    public BlockVector3 getMaxPoint() {
        return max;
    }

    @Override
    public BlockVector3 getCenter() {
        return BlockVector3.at(
            (min.getX() + max.getX()) / 2,
            (min.getY() + max.getY()) / 2,
            (min.getZ() + max.getZ()) / 2
        );
    }

    @Override
    public String getWorldName() {
        return worldName;
    }

    @Override
    public Set<String> getMaterialTypes() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return new HashSet<>();
        }

        Set<String> materials = new HashSet<>();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block != null && block.getType() != Material.AIR) {
                        materials.add(block.getType().name());
                    }
                }
            }
        }
        return materials;
    }

    @Override
    public CuboidRegion toRelative() {
        return new CuboidRegion(worldName, BlockVector3.at(0, 0, 0),
            BlockVector3.at(getWidth() - 1, getHeight() - 1, getDepth() - 1));
    }

    @Override
    public Optional<CuboidRegion> toCuboid() {
        return Optional.of(this);
    }

    @Override
    public Optional<PolygonalRegion> toPolygonal() {
        // 将立方体转换为多边形（使用底面投影作为矩形多边形）
        List<PolygonalRegion.BlockVector2> points = Arrays.asList(
            new PolygonalRegion.BlockVector2(min.getX(), min.getZ()),
            new PolygonalRegion.BlockVector2(max.getX(), min.getZ()),
            new PolygonalRegion.BlockVector2(max.getX(), max.getZ()),
            new PolygonalRegion.BlockVector2(min.getX(), max.getZ())
        );
        return Optional.of(new PolygonalRegion(worldName, points, min.getY(), max.getY()));
    }

    @Override
    public CuboidRegion shift(int dx, int dy, int dz) {
        return new CuboidRegion(worldName,
            min.add(dx, dy, dz),
            max.add(dx, dy, dz));
    }

    @Override
    public CuboidRegion expand(int amount) {
        return new CuboidRegion(worldName,
            min.subtract(amount, amount, amount),
            max.add(amount, amount, amount));
    }

    @Override
    public CuboidRegion contract(int amount) {
        int newMinX = min.getX() + amount;
        int newMinY = min.getY() + amount;
        int newMinZ = min.getZ() + amount;
        int newMaxX = max.getX() - amount;
        int newMaxY = max.getY() - amount;
        int newMaxZ = max.getZ() - amount;

        if (newMinX > newMaxX || newMinY > newMaxY || newMinZ > newMaxZ) {
            throw new IllegalArgumentException(
                "Cannot contract region by " + amount + " blocks: region would have invalid dimensions. " +
                "Current bounds: min=(" + min.getX() + "," + min.getY() + "," + min.getZ() + ") " +
                "max=(" + max.getX() + "," + max.getY() + "," + max.getZ() + ")");
        }

        return new CuboidRegion(worldName,
            BlockVector3.at(newMinX, newMinY, newMinZ),
            BlockVector3.at(newMaxX, newMaxY, newMaxZ));
    }

    @Override
    public CuboidRegion mirror(String axis) {
        switch (axis.toLowerCase()) {
            case "x":
                return new CuboidRegion(worldName,
                    BlockVector3.at(-max.getX(), min.getY(), min.getZ()),
                    BlockVector3.at(-min.getX(), max.getY(), max.getZ()));
            case "y":
                return new CuboidRegion(worldName,
                    BlockVector3.at(min.getX(), -max.getY(), min.getZ()),
                    BlockVector3.at(max.getX(), -min.getY(), max.getZ()));
            case "z":
                return new CuboidRegion(worldName,
                    BlockVector3.at(min.getX(), min.getY(), -max.getZ()),
                    BlockVector3.at(max.getX(), max.getY(), -min.getZ()));
            default:
                return this;
        }
    }

    @Override
    public CuboidRegion union(RegionSelection other) {
        if (other instanceof CuboidRegion otherCuboid) {
            if (!worldName.equals(otherCuboid.worldName)) {
                throw new IllegalArgumentException("Cannot union regions from different worlds");
            }
            BlockVector3 newMin = BlockVector3.at(
                Math.min(min.getX(), otherCuboid.min.getX()),
                Math.min(min.getY(), otherCuboid.min.getY()),
                Math.min(min.getZ(), otherCuboid.min.getZ())
            );
            BlockVector3 newMax = BlockVector3.at(
                Math.max(max.getX(), otherCuboid.max.getX()),
                Math.max(max.getY(), otherCuboid.max.getY()),
                Math.max(max.getZ(), otherCuboid.max.getZ())
            );
            return new CuboidRegion(worldName, newMin, newMax);
        }
        throw new IllegalArgumentException("Union with non-cuboid region not supported");
    }

    @Override
    public Optional<RegionSelection> intersection(RegionSelection other) {
        if (other instanceof CuboidRegion otherCuboid) {
            if (!worldName.equals(otherCuboid.worldName)) {
                return Optional.empty();
            }
            BlockVector3 newMin = BlockVector3.at(
                Math.max(min.getX(), otherCuboid.min.getX()),
                Math.max(min.getY(), otherCuboid.min.getY()),
                Math.max(min.getZ(), otherCuboid.min.getZ())
            );
            BlockVector3 newMax = BlockVector3.at(
                Math.min(max.getX(), otherCuboid.max.getX()),
                Math.min(max.getY(), otherCuboid.max.getY()),
                Math.min(max.getZ(), otherCuboid.max.getZ())
            );
            if (newMin.getX() > newMax.getX() || newMin.getY() > newMax.getY() || newMin.getZ() > newMax.getZ()) {
                return Optional.empty();
            }
            return Optional.of(new CuboidRegion(worldName, newMin, newMax));
        }
        throw new IllegalArgumentException("Intersection with non-cuboid region not supported");
    }

    /**
     * 获取区域内的方块集合（用于快速查找）
     */
    public Set<BlockVector3> getBlockSet() {
        return getBlocks().stream().collect(Collectors.toSet());
    }

    /**
     * 获取区域的边框
     */
    public List<BlockVector3> getBorder() {
        List<BlockVector3> border = new ArrayList<>();
        // 12条边
        for (int x = min.getX(); x <= max.getX(); x++) {
            border.add(BlockVector3.at(x, min.getY(), min.getZ()));
            border.add(BlockVector3.at(x, max.getY(), min.getZ()));
            border.add(BlockVector3.at(x, min.getY(), max.getZ()));
            border.add(BlockVector3.at(x, max.getY(), max.getZ()));
        }
        for (int y = min.getY(); y <= max.getY(); y++) {
            border.add(BlockVector3.at(min.getX(), y, min.getZ()));
            border.add(BlockVector3.at(max.getX(), y, min.getZ()));
            border.add(BlockVector3.at(min.getX(), y, max.getZ()));
            border.add(BlockVector3.at(max.getX(), y, max.getZ()));
        }
        for (int z = min.getZ(); z <= max.getZ(); z++) {
            border.add(BlockVector3.at(min.getX(), min.getY(), z));
            border.add(BlockVector3.at(max.getX(), min.getY(), z));
            border.add(BlockVector3.at(min.getX(), max.getY(), z));
            border.add(BlockVector3.at(max.getX(), max.getY(), z));
        }
        return border;
    }

    /**
     * 获取区域的某个面
     */
    public List<BlockVector3> getFace(String direction) {
        List<BlockVector3> face = new ArrayList<>();
        switch (direction.toLowerCase()) {
            case "north":
                for (int x = min.getX(); x <= max.getX(); x++) {
                    for (int y = min.getY(); y <= max.getY(); y++) {
                        face.add(BlockVector3.at(x, y, min.getZ()));
                    }
                }
                break;
            case "south":
                for (int x = min.getX(); x <= max.getX(); x++) {
                    for (int y = min.getY(); y <= max.getY(); y++) {
                        face.add(BlockVector3.at(x, y, max.getZ()));
                    }
                }
                break;
            case "east":
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    for (int y = min.getY(); y <= max.getY(); y++) {
                        face.add(BlockVector3.at(max.getX(), y, z));
                    }
                }
                break;
            case "west":
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    for (int y = min.getY(); y <= max.getY(); y++) {
                        face.add(BlockVector3.at(min.getX(), y, z));
                    }
                }
                break;
            case "up":
                for (int x = min.getX(); x <= max.getX(); x++) {
                    for (int z = min.getZ(); z <= max.getZ(); z++) {
                        face.add(BlockVector3.at(x, max.getY(), z));
                    }
                }
                break;
            case "down":
                for (int x = min.getX(); x <= max.getX(); x++) {
                    for (int z = min.getZ(); z <= max.getZ(); z++) {
                        face.add(BlockVector3.at(x, min.getY(), z));
                    }
                }
                break;
        }
        return face;
    }

    @Override
    public String toString() {
        return "CuboidRegion{" +
               "world=" + worldName +
               ", min=" + min +
               ", max=" + max +
               ", size=" + getWidth() + "x" + getHeight() + "x" + getDepth() +
               '}';
    }
}
