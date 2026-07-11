-- ===============================================
-- 指挥官系统数据库迁移
-- V19__commander_module.sql
-- ===============================================

-- 指挥官玩家数据表
CREATE TABLE IF NOT EXISTS starcore_commanders (
    player_id VARCHAR(36) PRIMARY KEY,
    experience INT NOT NULL DEFAULT 0,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 指挥官技能表
CREATE TABLE IF NOT EXISTS starcore_commander_skills (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_id VARCHAR(36) NOT NULL,
    skill_type VARCHAR(32) NOT NULL,
    skill_level INT NOT NULL DEFAULT 1,
    unlocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (player_id) REFERENCES starcore_commanders(player_id) ON DELETE CASCADE,
    UNIQUE(player_id, skill_type)
);

-- 指挥官等级历史记录表（用于排行榜）
CREATE TABLE IF NOT EXISTS starcore_commander_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_id VARCHAR(36) NOT NULL,
    level INT NOT NULL,
    experience INT NOT NULL,
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_commander_skills_player ON starcore_commander_skills(player_id);
CREATE INDEX IF NOT EXISTS idx_commander_history_player ON starcore_commander_history(player_id);
CREATE INDEX IF NOT EXISTS idx_commander_history_level ON starcore_commander_history(level);

-- 迁移说明：
-- 1. starcore_commanders: 存储每个玩家的指挥官经验和最后更新时间
-- 2. starcore_commander_skills: 存储玩家已解锁的技能及其等级
-- 3. starcore_commander_history: 历史记录，用于排行榜和统计

-- 回滚脚本
-- DROP TABLE IF EXISTS starcore_commander_history;
-- DROP TABLE IF EXISTS starcore_commander_skills;
-- DROP TABLE IF EXISTS starcore_commanders;