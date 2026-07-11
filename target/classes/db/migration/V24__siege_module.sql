-- V4__siege_module.sql
-- 攻城器械模块数据库迁移

-- 攻城器械单位表
CREATE TABLE IF NOT EXISTS siege_units (
    id VARCHAR(36) PRIMARY KEY,
    nation_id VARCHAR(36) NOT NULL,
    siege_type VARCHAR(32) NOT NULL,
    crew_size INT NOT NULL DEFAULT 10,
    health DOUBLE NOT NULL DEFAULT 100.0,
    max_health DOUBLE NOT NULL DEFAULT 100.0,
    location_world VARCHAR(64),
    location_x DOUBLE,
    location_y DOUBLE,
    location_z DOUBLE,
    state VARCHAR(32) NOT NULL DEFAULT 'CONSTRUCTED',
    morale DOUBLE NOT NULL DEFAULT 100.0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_nation_id (nation_id),
    INDEX idx_siege_type (siege_type),
    INDEX idx_state (state)
);

-- 攻城战记录表
CREATE TABLE IF NOT EXISTS siege_battles (
    id VARCHAR(36) PRIMARY KEY,
    attacker_nation_id VARCHAR(36) NOT NULL,
    defender_nation_id VARCHAR(36) NOT NULL,
    target_world VARCHAR(64),
    target_x INT,
    target_y INT,
    target_z INT,
    siege_units TEXT,
    start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    end_time TIMESTAMP,
    result VARCHAR(32),
    damage_dealt DOUBLE DEFAULT 0.0,
    buildings_destroyed INT DEFAULT 0,
    victor_nation_id VARCHAR(36),
    rewards_claimed BOOLEAN DEFAULT FALSE,
    INDEX idx_attacker (attacker_nation_id),
    INDEX idx_defender (defender_nation_id),
    INDEX idx_result (result),
    INDEX idx_start_time (start_time)
);

-- 攻城日志表
CREATE TABLE IF NOT EXISTS siege_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    battle_id VARCHAR(36) NOT NULL,
    siege_unit_id VARCHAR(36),
    action VARCHAR(32) NOT NULL,
    target_type VARCHAR(32),
    target_id VARCHAR(36),
    damage DOUBLE,
    result VARCHAR(64),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (battle_id) REFERENCES siege_battles(id) ON DELETE CASCADE,
    INDEX idx_battle (battle_id),
    INDEX idx_timestamp (timestamp)
);

-- 攻城器械状态快照表（用于定期保存）
CREATE TABLE IF NOT EXISTS siege_snapshots (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    siege_unit_id VARCHAR(36) NOT NULL,
    health DOUBLE NOT NULL,
    morale DOUBLE NOT NULL,
    crew_size INT NOT NULL,
    state VARCHAR(32) NOT NULL,
    snapshot_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_siege_unit (siege_unit_id),
    INDEX idx_snapshot_time (snapshot_time)
);
