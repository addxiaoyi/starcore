package dev.starcore.starcore.module.blueprint;
import java.util.Optional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 多边形区域选择器
 * 用于平面的多边形选区
 */
public class PolygonalRegion implements RegionSelection {
    private final String worldName;
    private final List<BlockVector2> points; // 2D 多边形顶点
    private final int minY;
    private final int maxY;
    private final BlockVector2 center2D;

    public PolygonalRegion(String worldName, List<BlockVector2> points, int minY, int maxY) {
        this.worldName = worldName;
        this.points = new ArrayList<>(points);
        this.minY = minY;
        this.maxY = maxY;

        // 计算中心点
        double cx = points.stream().mapToInt(BlockVector2::getX).average().orElse(0);
        double cz = points.stream().mapToInt(BlockVector2::getZ).average().orElse(0);
        this.center2D = new BlockVector2((int) cx, (int) cz);
    }

    public static PolygonalRegion create(String worldName, List<BlockVector2> points, int height) {
        return new PolygonalRegion(worldName, points, 0, height - 1);
    }

    @Override
    public String getType() {
        return "POLYGON";
    }

    @Override
    public List<BlockVector3> getBlocks() {
        List<BlockVector3> blocks = new ArrayList<>();
        int minX = points.stream().mapToInt(BlockVector2::getX).min().orElse(0);
        int maxX = points.stream().mapToInt(BlockVector2::getX).max().orElse(0);
        int minZ = points.stream().mapToInt(BlockVector2::getZ).min().orElse(0);
        int maxZ = points.stream().mapToInt(BlockVector2::getZ).max().orElse(0);

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (contains2D(x, z)) {
                        blocks.add(BlockVector3.at(x, y, z));
                    }
                }
            }
        }
        return blocks;
    }

    /**
     * 检查2D点是否在多边形内（使用射线投射算法）
     */
    private boolean contains2D(int x, int z) {
        if (points.size() < 3) return false;

        boolean inside = false;
        int n = points.size();

        for (int i = 0, j = n - 1; i < n; j = i++) {
            int xi = points.get(i).getX();
            int zi = points.get(i).getZ();
            int xj = points.get(j).getX();
            int zj = points.get(j).getZ();

            if (((zi > z) != (zj > z)) &&
                (x < (xj - xi) * (z - zi) / (double) (zj - zi) + xi)) {
                inside = !inside;
            }
        }

        return inside;
    }

    @Override
    public int getBlockCount() {
        return getBlocks().size();
    }

    @Override
    public int getWidth() {
        if (points.isEmpty()) return 0;
        int minX = points.stream().mapToInt(BlockVector2::getX).min().orElse(0);
        int maxX = points.stream().mapToInt(BlockVector2::getX).max().orElse(0);
        return maxX - minX + 1;
    }

    @Override
    public int getHeight() {
        return maxY - minY + 1;
    }

    @Override
    public int getDepth() {
        if (points.isEmpty()) return 0;
        int minZ = points.stream().mapToInt(BlockVector2::getZ).min().orElse(0);
        int maxZ = points.stream().mapToInt(BlockVector2::getZ).max().orElse(0);
        return maxZ - minZ + 1;
    }

    @Override
    public boolean contains(BlockVector3 position) {
        return position.getY() >= minY && position.getY() <= maxY && contains2D(position.getX(), position.getZ());
    }

    @Override
    public BlockVector3 getMinPoint() {
        int minX = points.stream().mapToInt(BlockVector2::getX).min().orElse(0);
        int minZ = points.stream().mapToInt(BlockVector2::getZ).min().orElse(0);
        return BlockVector3.at(minX, minY, minZ);
    }

    @Override
    public BlockVector3 getMaxPoint() {
        int maxX = points.stream().mapToInt(BlockVector2::getX).max().orElse(0);
        int maxZ = points.stream().mapToInt(BlockVector2::getZ).max().orElse(0);
        return BlockVector3.at(maxX, maxY, maxZ);
    }

    @Override
    public BlockVector3 getCenter() {
        return BlockVector3.at(center2D.getX(), (minY + maxY) / 2, center2D.getZ());
    }

    @Override
    public String getWorldName() {
        return worldName;
    }

    @Override
    public Set<String> getMaterialTypes() {
        if (worldName == null || worldName.isEmpty()) {
            return new HashSet<>();
        }
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
        if (world == null) {
            return new HashSet<>();
        }
        Set<String> materials = new HashSet<>();
        for (BlockVector3 pos : getBlocks()) {
            materials.add(world.getBlockAt(pos.getX(), pos.getY(), pos.getZ()).getType().name());
        }
        return materials;
    }

    @Override
    public PolygonalRegion toRelative() {
        // 获取最小点作为原点偏移
        BlockVector3 min = getMinPoint();
        List<BlockVector2> relativePoints = points.stream()
            .map(p -> new BlockVector2(p.getX() - min.getX(), p.getZ() - min.getZ()))
            .collect(Collectors.toList());
        return new PolygonalRegion(worldName, relativePoints, minY - min.getY(), maxY - min.getY());
    }

    @Override
    public Optional<CuboidRegion> toCuboid() {
        // 转换为包围盒
        BlockVector3 min = getMinPoint();
        BlockVector3 max = getMaxPoint();
        return Optional.of(new CuboidRegion(worldName, min, max));
    }

    @Override
    public Optional<PolygonalRegion> toPolygonal() {
        return Optional.of(this);
    }

    @Override
    public PolygonalRegion shift(int dx, int dy, int dz) {
        List<BlockVector2> shiftedPoints = points.stream()
            .map(p -> new BlockVector2(p.getX() + dx, p.getZ() + dz))
            .collect(Collectors.toList());
        return new PolygonalRegion(worldName, shiftedPoints, minY + dy, maxY + dy);
    }

    @Override
    public PolygonalRegion expand(int amount) {
        // 简化的扩展：向每个方向扩展
        List<BlockVector2> expandedPoints = new ArrayList<>();
        int centerX = center2D.getX();
        int centerZ = center2D.getZ();

        for (BlockVector2 p : points) {
            int dx = p.getX() - centerX;
            int dz = p.getZ() - centerZ;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 0) {
                double factor = (dist + amount) / dist;
                int newX = centerX + (int) (dx * factor);
                int newZ = centerZ + (int) (dz * factor);
                expandedPoints.add(new BlockVector2(newX, newZ));
            } else {
                expandedPoints.add(p);
            }
        }

        return new PolygonalRegion(worldName, expandedPoints, minY, maxY);
    }

    @Override
    public PolygonalRegion contract(int amount) {
        return expand(-amount);
    }

    @Override
    public PolygonalRegion mirror(String axis) {
        List<BlockVector2> mirroredPoints = new ArrayList<>();
        for (BlockVector2 p : points) {
            switch (axis.toLowerCase()) {
                case "x":
                    mirroredPoints.add(new BlockVector2(-p.getX(), p.getZ()));
                    break;
                case "z":
                    mirroredPoints.add(new BlockVector2(p.getX(), -p.getZ()));
                    break;
                default:
                    mirroredPoints.add(p);
            }
        }
        return new PolygonalRegion(worldName, mirroredPoints, minY, maxY);
    }

    @Override
    public PolygonalRegion union(RegionSelection other) {
        // 转换为包围盒计算联合
        Optional<CuboidRegion> thisCuboid = this.toCuboid();
        if (thisCuboid.isEmpty()) {
            return null;
        }

        if (other instanceof CuboidRegion otherCuboid) {
            CuboidRegion union = thisCuboid.get().union(otherCuboid);
            return union.toPolygonal().orElse(null);
        }

        Optional<CuboidRegion> otherCuboidOpt = other.toCuboid();
        if (otherCuboidOpt.isEmpty()) {
            return null;
        }

        CuboidRegion union = thisCuboid.get().union(otherCuboidOpt.get());
        return union.toPolygonal().orElse(null);
    }

    @Override
    public Optional<RegionSelection> intersection(RegionSelection other) {
        // 转换为包围盒计算交集
        Optional<CuboidRegion> thisCuboid = this.toCuboid();
        if (thisCuboid.isEmpty()) {
            return Optional.empty();
        }

        if (other instanceof CuboidRegion otherCuboid) {
            Optional<RegionSelection> intersection = thisCuboid.get().intersection(otherCuboid);
            return intersection.flatMap(r -> r.toPolygonal());
        }

        Optional<CuboidRegion> otherCuboidOpt = other.toCuboid();
        if (otherCuboidOpt.isEmpty()) {
            return Optional.empty();
        }

        Optional<RegionSelection> intersection = thisCuboid.get().intersection(otherCuboidOpt.get());
        return intersection.flatMap(r -> r.toPolygonal());
    }

    /**
     * 获取多边形顶点
     */
    public List<BlockVector2> getPoints() {
        return Collections.unmodifiableList(points);
    }

    /**
     * 获取多边形周长
     */
    public double getPerimeter() {
        if (points.size() < 2) return 0;
        double perimeter = 0;
        for (int i = 0; i < points.size(); i++) {
            BlockVector2 p1 = points.get(i);
            BlockVector2 p2 = points.get((i + 1) % points.size());
            perimeter += p1.distance(p2);
        }
        return perimeter;
    }

    /**
     * 获取多边形面积
     */
    public double getArea() {
        if (points.size() < 3) return 0;
        double area = 0;
        for (int i = 0; i < points.size(); i++) {
            BlockVector2 p1 = points.get(i);
            BlockVector2 p2 = points.get((i + 1) % points.size());
            area += p1.getX() * p2.getZ() - p2.getX() * p1.getZ();
        }
        return Math.abs(area / 2);
    }

    @Override
    public String toString() {
        return "PolygonalRegion{" +
               "world=" + worldName +
               ", points=" + points.size() +
               ", height=" + getHeight() +
               ", area=" + getArea() +
               '}';
    }

    /**
     * 2D 坐标向量
     */
    public static class BlockVector2 {
        private final int x;
        private final int z;

        public BlockVector2(int x, int z) {
            this.x = x;
            this.z = z;
        }

        public int getX() {
            return x;
        }

        public int getZ() {
            return z;
        }

        public double distance(BlockVector2 other) {
            int dx = x - other.x;
            int dz = z - other.z;
            return Math.sqrt(dx * dx + dz * dz);
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
}
