package dev.starcore.starcore.module.map;

record TerrainTileRaster(
    String world,
    int minX,
    int minZ,
    int worldSize,
    int tileSize,
    int[] colors,
    int[] heights,
    byte[] lights,
    int heightMin,
    int heightMax,
    long renderedAtMillis
) {
}
