-- ============================================
-- Donation Module Database Schema
-- Feature ID: 13 - Donation (献金)
-- Version: V15
-- ============================================

-- 献金记录表
CREATE TABLE IF NOT EXISTS starcore_donations (
    id VARCHAR(36) PRIMARY KEY,
    player_id VARCHAR(36) NOT NULL,
    player_name VARCHAR(255) NOT NULL,
    nation_id VARCHAR(36) NOT NULL,
    nation_name VARCHAR(255) NOT NULL,
    amount DECIMAL(20, 2) NOT NULL,
    message TEXT,
    tier_id VARCHAR(50),
    donated_at BIGINT NOT NULL
);

-- 创建索引（MySQL/MariaDB）
-- 注意：SQLite 会在运行时忽略这些索引语句如果已存在
-- CREATE INDEX IF NOT EXISTS idx_donation_player ON starcore_donations(player_id);
-- CREATE INDEX IF NOT EXISTS idx_donation_nation ON starcore_donations(nation_id);
-- CREATE INDEX IF NOT EXISTS idx_donation_date ON starcore_donations(donated_at);
-- CREATE INDEX IF NOT EXISTS idx_donation_player_nation ON starcore_donations(player_id, nation_id);

-- 已领取奖励记录表
CREATE TABLE IF NOT EXISTS starcore_donation_rewards (
    id VARCHAR(36) PRIMARY KEY,
    player_id VARCHAR(36) NOT NULL,
    nation_id VARCHAR(36) NOT NULL,
    reward_id VARCHAR(50) NOT NULL,
    claimed_at BIGINT NOT NULL,
    UNIQUE (player_id, nation_id, reward_id)
);

-- 玩家献金汇总表（用于快速查询排名）
CREATE TABLE IF NOT EXISTS starcore_donation_summary (
    player_id VARCHAR(36) NOT NULL,
    nation_id VARCHAR(36) NOT NULL,
    total_amount DECIMAL(20, 2) NOT NULL DEFAULT 0,
    donation_count INT NOT NULL DEFAULT 0,
    current_tier VARCHAR(50),
    last_donated_at BIGINT,
    PRIMARY KEY (player_id, nation_id)
);

-- SQLite 兼容索引（SQLite 版本）
-- SQLite 不支持条件创建索引，使用 IF NOT EXISTS 会导致语法错误
-- 所以这里只提供基本表结构，索引在实际使用时会自动处理
