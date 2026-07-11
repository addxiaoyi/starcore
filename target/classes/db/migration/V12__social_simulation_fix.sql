-- StarCore V12: Social Simulation 补充表
-- Version: 12
-- Description: 为社会模拟模块补充缺失的 relationships 表 (V11 跳过的修复)

-- ============================================
-- 关系网络表 (补充 V11 跳过的表)
-- ============================================

CREATE TABLE IF NOT EXISTS relationships (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player1 TEXT NOT NULL,
    player2 TEXT NOT NULL,
    type TEXT NOT NULL,
    strength INTEGER DEFAULT 50,
    last_interaction INTEGER,
    created_at INTEGER
);

CREATE INDEX IF NOT EXISTS idx_relationships_player1 ON relationships(player1);
CREATE INDEX IF NOT EXISTS idx_relationships_player2 ON relationships(player2);

-- 关系历史表
CREATE TABLE IF NOT EXISTS relationship_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player1 TEXT,
    player2 TEXT,
    action TEXT,
    strength_change INTEGER,
    description TEXT,
    timestamp INTEGER
);

-- ============================================
-- 社交联盟表 (补充)
-- ============================================

CREATE TABLE IF NOT EXISTS social_alliances (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    alliance_id TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    leader_uuid TEXT NOT NULL,
    created_at INTEGER,
    description TEXT
);

CREATE INDEX IF NOT EXISTS idx_social_alliances_leader ON social_alliances(leader_uuid);

CREATE TABLE IF NOT EXISTS social_alliance_members (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    alliance_id TEXT NOT NULL,
    player_uuid TEXT NOT NULL,
    joined_at INTEGER,
    UNIQUE(alliance_id, player_uuid)
);

CREATE INDEX IF NOT EXISTS idx_alliance_members_alliance ON social_alliance_members(alliance_id);
CREATE INDEX IF NOT EXISTS idx_alliance_members_player ON social_alliance_members(player_uuid);