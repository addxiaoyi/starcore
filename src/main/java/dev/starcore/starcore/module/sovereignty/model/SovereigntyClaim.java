package dev.starcore.starcore.module.sovereignty.model;

/**
 * 主权声明的领土区块记录
 * 表示主权声明所包含的具体领土范围
 *
 * @param world 世界名称
 * @param chunkX 区块X坐标
 * @param chunkZ 区块Z坐标
 */
public record SovereigntyClaim(
        String world,
        int chunkX,
        int chunkZ
) {

    /**
     * 获取区块坐标的标识符字符串
     */
    public String chunkKey() {
        return world + ":" + chunkX + ":" + chunkZ;
    }

    /**
     * 获取中心点的X坐标（区块左下角 + 8）
     */
    public int centerX() {
        return chunkX * 16 + 8;
    }

    /**
     * 获取中心点的Z坐标（区块左下角 + 8）
     */
    public int centerZ() {
        return chunkZ * 16 + 8;
    }

    @Override
    public String toString() {
        return "SovereigntyClaim{" +
                "world='" + world + '\'' +
                ", chunkX=" + chunkX +
                ", chunkZ=" + chunkZ +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SovereigntyClaim that)) return false;
        return chunkX == that.chunkX
                && chunkZ == that.chunkZ
                && world.equals(that.world);
    }

    @Override
    public int hashCode() {
        int result = world.hashCode();
        result = 31 * result + chunkX;
        result = 31 * result + chunkZ;
        return result;
    }
}