-- StarCore V5: 领土系统持久化表
-- Version: 5
-- Description: 添加领土和子区域的持久化表结构

-- ============================================
-- 领土主表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_territories (
    territory_id CHAR(36) PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    owner_id CHAR(36) NOT NULL,
    nation_id CHAR(36),
    world_name VARCHAR(64) NOT NULL,
    min_x INT NOT NULL,
    min_y INT NOT NULL,
    min_z INT NOT NULL,
    max_x INT NOT NULL,
    max_y INT NOT NULL,
    max_z INT NOT NULL,
    type VARCHAR(32) NOT NULL DEFAULT 'RESIDENTIAL',
    spawn_x DOUBLE,
    spawn_y DOUBLE,
    spawn_z DOUBLE,
    enabled BOOLEAN DEFAULT TRUE,
    created_time BIGINT NOT NULL
);

-- 领土权限表
CREATE TABLE IF NOT EXISTS starcore_territories_permissions (
    territory_id CHAR(36) NOT NULL,
    permission VARCHAR(32) NOT NULL,
    level VARCHAR(16) NOT NULL,
    PRIMARY KEY (territory_id, permission),
    FOREIGN KEY (territory_id) REFERENCES starcore_territories(territory_id) ON DELETE CASCADE
);

-- ============================================
-- 成员表（同时存储领土和子区域成员）
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_territory_members (
    entity_id CHAR(36) NOT NULL,
    entity_type VARCHAR(16) NOT NULL,
    player_id CHAR(36) NOT NULL,
    permission_level VARCHAR(16) NOT NULL,
    PRIMARY KEY (entity_id, entity_type, player_id)
);

-- ============================================
-- 子区域表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_subregions (
    subregion_id CHAR(36) PRIMARY KEY,
    parent_territory_id CHAR(36) NOT NULL,
    name VARCHAR(64) NOT NULL,
    world_name VARCHAR(64) NOT NULL,
    min_x INT NOT NULL,
    min_y INT NOT NULL,
    min_z INT NOT NULL,
    max_x INT NOT NULL,
    max_y INT NOT NULL,
    max_z INT NOT NULL,
    priority INT DEFAULT 0,
    inherit_permissions BOOLEAN DEFAULT TRUE,
    description TEXT,
    enabled BOOLEAN DEFAULT TRUE,
    created_time BIGINT NOT NULL,
    FOREIGN KEY (parent_territory_id) REFERENCES starcore_territories(territory_id) ON DELETE CASCADE
);

-- 子区域权限覆盖表
CREATE TABLE IF NOT EXISTS starcore_subregions_permissions (
    subregion_id CHAR(36) NOT NULL,
    permission VARCHAR(32) NOT NULL,
    level VARCHAR(16) NOT NULL,
    PRIMARY KEY (subregion_id, permission),
    FOREIGN KEY (subregion_id) REFERENCES starcore_subregions(subregion_id) ON DELETE CASCADE
);

-- ============================================
-- 索引
-- ============================================
CREATE INDEX IF NOT EXISTS idx_territories_owner ON starcore_territories(owner_id);
CREATE INDEX IF NOT EXISTS idx_territories_nation ON starcore_territories(nation_id);
CREATE INDEX IF NOT EXISTS idx_territories_world ON starcore_territories(world_name);
CREATE INDEX IF NOT EXISTS idx_subregions_parent ON starcore_subregions(parent_territory_id);
CREATE INDEX IF NOT EXISTS idx_members_entity ON starcore_territory_members(entity_id, entity_type);
