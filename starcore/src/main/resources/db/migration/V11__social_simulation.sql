-- StarCore V11: Social Simulation 模块数据库表
-- Version: 11
-- Description: 为社会模拟模块创建 SQLite 兼容的数据库表

-- ============================================
-- 关系网络表 (SQLite 兼容版本)
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
-- 声望系统表
-- ============================================

CREATE TABLE IF NOT EXISTS player_reputation (
    player_uuid TEXT PRIMARY KEY,
    reputation_score REAL DEFAULT 0,
    reputation_level INTEGER DEFAULT 1,
    total_positive INTEGER DEFAULT 0,
    total_negative INTEGER DEFAULT 0,
    last_updated INTEGER
);

CREATE TABLE IF NOT EXISTS reputation_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid TEXT NOT NULL,
    change_amount REAL NOT NULL,
    reason TEXT,
    source TEXT,
    timestamp INTEGER
);

CREATE INDEX IF NOT EXISTS idx_reputation_history_player ON reputation_history(player_uuid);

-- ============================================
-- 社会影响力表
-- ============================================

CREATE TABLE IF NOT EXISTS player_influence (
    player_uuid TEXT PRIMARY KEY,
    influence_score REAL DEFAULT 0,
    influence_rank INTEGER DEFAULT 0,
    followers_count INTEGER DEFAULT 0,
    total_reach INTEGER DEFAULT 0,
    last_calculated INTEGER
);

CREATE TABLE IF NOT EXISTS influence_sources (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid TEXT NOT NULL,
    source_type TEXT NOT NULL,
    source_id TEXT,
    influence_amount REAL NOT NULL,
    created_at INTEGER,
    expires_at INTEGER
);

CREATE INDEX IF NOT EXISTS idx_influence_sources_player ON influence_sources(player_uuid);

-- ============================================
-- 社交联盟表
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

-- ============================================
-- 影响力排行榜表
-- ============================================

CREATE TABLE IF NOT EXISTS influence_leaderboard_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid TEXT NOT NULL,
    rank_position INTEGER NOT NULL,
    influence_score REAL NOT NULL,
    recorded_at INTEGER
);

CREATE INDEX IF NOT EXISTS idx_leaderboard_history_player ON influence_leaderboard_history(player_uuid);

CREATE TABLE IF NOT EXISTS influence_leaderboard_titles (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid TEXT NOT NULL UNIQUE,
    title TEXT,
    earned_at INTEGER,
    expires_at INTEGER
);

CREATE TABLE IF NOT EXISTS leaderboard_notifications (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid TEXT NOT NULL,
    notification_type TEXT NOT NULL,
    content TEXT,
    created_at INTEGER,
    read INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_leaderboard_notifications_player ON leaderboard_notifications(player_uuid);
