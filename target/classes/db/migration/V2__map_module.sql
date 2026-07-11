-- StarCore Map Module Schema
-- Version: 2
-- Description: 添加地图模块相关表

-- ============================================
-- 地图区块数据表 (Map Chunk Data)
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_map_chunks (
    world_name VARCHAR(255) NOT NULL,
    chunk_x INT NOT NULL,
    chunk_z INT NOT NULL,
    biome VARCHAR(100),
    height_map TEXT,
    last_updated BIGINT NOT NULL,
    PRIMARY KEY (world_name, chunk_x, chunk_z)
);

CREATE INDEX IF NOT EXISTS idx_map_world ON starcore_map_chunks(world_name);
CREATE INDEX IF NOT EXISTS idx_map_updated ON starcore_map_chunks(last_updated);

-- ============================================
-- 地图标记表 (Map Markers)
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_map_markers (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    world_name VARCHAR(255) NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    z INT NOT NULL,
    marker_type VARCHAR(50) NOT NULL,
    label VARCHAR(255),
    icon VARCHAR(50),
    color VARCHAR(20),
    owner_uuid CHAR(36),
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_marker_world ON starcore_map_markers(world_name);
CREATE INDEX IF NOT EXISTS idx_marker_owner ON starcore_map_markers(owner_uuid);
