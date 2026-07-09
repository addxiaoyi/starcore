-- ===============================================
-- StarCore 王位继承系统数据库迁移
-- 迁移版本: V18__dynasty_succession
-- 描述: 添加王位继承系统相关表
-- ===============================================

-- 王朝主表
CREATE TABLE IF NOT EXISTS starcore_dynasties (
    nation_id VARCHAR(36) PRIMARY KEY COMMENT '国家ID (外键)',
    dynasty_name VARCHAR(100) NOT NULL COMMENT '王朝名称',
    current_monarch_id VARCHAR(36) COMMENT '当前君主ID',
    current_monarch_name VARCHAR(100) COMMENT '当前君主名称',
    succession_type VARCHAR(50) NOT NULL DEFAULT 'MALE_PREMIogeniture' COMMENT '继承类型',
    created_at INTEGER NOT NULL COMMENT '创建时间戳 (Unix epoch)',
    monarch_since INTEGER COMMENT '君主在位开始时间戳',
    reign_count INTEGER DEFAULT 0 COMMENT '君主更替次数',
    interregnum_start INTEGER COMMENT '空位期开始时间戳',
    succession_title VARCHAR(50) DEFAULT 'Monarch' COMMENT '君主称号',
    INDEX idx_monarch (current_monarch_id),
    INDEX idx_succession_type (succession_type)
);

-- 王朝继承人表
CREATE TABLE IF NOT EXISTS starcore_dynasty_heirs (
    nation_id VARCHAR(36) NOT NULL COMMENT '国家ID',
    player_id VARCHAR(36) NOT NULL COMMENT '继承人ID',
    player_name VARCHAR(100) NOT NULL COMMENT '继承人名称',
    position INTEGER NOT NULL COMMENT '继承顺位 (1=第一顺位)',
    added_at INTEGER NOT NULL COMMENT '添加时间戳',
    PRIMARY KEY (nation_id, player_id),
    INDEX idx_position (nation_id, position),
    FOREIGN KEY (nation_id) REFERENCES starcore_dynasties(nation_id) ON DELETE CASCADE
);

-- 王朝历史记录表（用于审计）
CREATE TABLE IF NOT EXISTS starcore_dynasty_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nation_id VARCHAR(36) NOT NULL COMMENT '国家ID',
    previous_monarch_id VARCHAR(36) COMMENT '前任君主ID',
    previous_monarch_name VARCHAR(100) COMMENT '前任君主名称',
    new_monarch_id VARCHAR(36) COMMENT '新君主ID',
    new_monarch_name VARCHAR(100) COMMENT '新君主名称',
    succession_type VARCHAR(50) NOT NULL COMMENT '继承类型',
    succession_kind VARCHAR(20) NOT NULL COMMENT '继承方式: ABDICATION, INHERITANCE, CORONATION, FORCE_MAJORE',
    reason TEXT COMMENT '继承原因/备注',
    reign_days INTEGER COMMENT '前任君主在位天数',
    occurred_at INTEGER NOT NULL COMMENT '发生时间戳',
    INDEX idx_nation (nation_id),
    INDEX idx_date (occurred_at)
);

-- ===============================================
-- 迁移完成
-- ===============================================
