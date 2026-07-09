-- ============================================
-- StarCore 伤兵模块数据库架构
-- Version: V19__wounded_module.sql
-- Description: 创建伤兵模块表结构
-- ============================================

-- 伤兵状态表
CREATE TABLE IF NOT EXISTS starcore_wounded_state (
    wounded_id CHAR(36) NOT NULL PRIMARY KEY,
    nation_id CHAR(36) NOT NULL,
    army_id CHAR(36),
    player_id CHAR(36),
    original_soldiers INT NOT NULL DEFAULT 0,
    current_wounded INT NOT NULL DEFAULT 0,
    severity VARCHAR(20) NOT NULL DEFAULT 'MODERATE',
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    injury_location TEXT,
    hospital_location TEXT,
    injured_at BIGINT NOT NULL,
    healing_started_at BIGINT DEFAULT 0,
    expected_recovery_at BIGINT DEFAULT 0,
    healing_progress DOUBLE DEFAULT 0.0,
    INDEX idx_nation_id (nation_id),
    INDEX idx_player_id (player_id),
    INDEX idx_status (status),
    INDEX idx_injured_at (injured_at)
);

-- 伤兵历史记录表（用于统计和分析）
CREATE TABLE IF NOT EXISTS starcore_wounded_history (
    record_id CHAR(36) NOT NULL PRIMARY KEY,
    nation_id CHAR(36) NOT NULL,
    army_id CHAR(36),
    player_id CHAR(36),
    original_soldiers INT NOT NULL DEFAULT 0,
    final_soldiers INT NOT NULL DEFAULT 0,
    severity VARCHAR(20) NOT NULL,
    final_status VARCHAR(20) NOT NULL,
    injured_at BIGINT NOT NULL,
    healed_at BIGINT DEFAULT 0,
    duration_seconds BIGINT DEFAULT 0,
    INDEX idx_nation_id (nation_id),
    INDEX idx_injured_at (injured_at),
    INDEX idx_healed_at (healed_at)
);

-- ============================================
-- 注释
-- ============================================

COMMENT ON TABLE starcore_wounded_state IS '伤兵状态表 - 存储当前所有伤兵记录';
COMMENT ON TABLE starcore_wounded_history IS '伤兵历史记录表 - 存储已结束伤兵记录用于统计';
COMMENT ON COLUMN starcore_wounded_state.wounded_id IS '伤兵记录唯一标识符';
COMMENT ON COLUMN starcore_wounded_state.nation_id IS '所属国家ID';
COMMENT ON COLUMN starcore_wounded_state.army_id IS '所属军队ID';
COMMENT ON COLUMN starcore_wounded_state.player_id IS '关联玩家ID（可选）';
COMMENT ON COLUMN starcore_wounded_state.original_soldiers IS '原始士兵数量（受伤前）';
COMMENT ON COLUMN starcore_wounded_state.current_wounded IS '当前伤兵数量';
COMMENT ON COLUMN starcore_wounded_state.severity IS '严重程度: LIGHT, MODERATE, SEVERE, CRITICAL';
COMMENT ON COLUMN starcore_wounded_state.status IS '状态: WAITING, HEALING, RECOVERED, DEAD';
COMMENT ON COLUMN starcore_wounded_state.injury_location IS '负伤位置（JSON格式）';
COMMENT ON COLUMN starcore_wounded_state.hospital_location IS '医院位置（JSON格式）';
COMMENT ON COLUMN starcore_wounded_state.injured_at IS '负伤时间戳（毫秒）';
COMMENT ON COLUMN starcore_wounded_state.healing_started_at IS '开始治疗时间戳（毫秒）';
COMMENT ON COLUMN starcore_wounded_state.expected_recovery_at IS '预计康复时间戳（毫秒）';
COMMENT ON COLUMN starcore_wounded_state.healing_progress IS '治疗进度（0.0-1.0）';
