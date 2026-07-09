package dev.starcore.starcore.module.map;

record TerrainPrewarmTile(String world, int minX, int minZ, int worldSize, int sortOrder, long distanceSquared) {
    TerrainTileKey key() {
        return new TerrainTileKey(world, minX, minZ, worldSize);
    }
}
