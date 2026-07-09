-- ===============================================
-- StarCore 军事学说系统数据库迁移
-- 迁移版本: V19__doctrine_module
-- 描述: 添加军事学说系统相关表
-- Feature ID: 20
-- ===============================================

-- 国家学说表
CREATE TABLE IF NOT EXISTS starcore_nation_doctrines (
    nation_id VARCHAR(36) PRIMARY KEY COMMENT '国家ID (外键)',
    doctrine_key VARCHAR(50) NOT NULL DEFAULT 'none' COMMENT '学说键名',
    adopted_at INTEGER NOT NULL COMMENT '采用时间戳 (Unix epoch)',
    switch_count INTEGER DEFAULT 0 COMMENT '切换次数',
    adopted_by VARCHAR(100) COMMENT '采用/切换者名称',
    INDEX idx_doctrine (doctrine_key),
    INDEX idx_adopted_at (adopted_at)
);

-- 学说使用统计表（用于排行榜和数据分析）
CREATE TABLE IF NOT EXISTS starcore_doctrine_stats (
    doctrine_key VARCHAR(50) PRIMARY KEY COMMENT '学说键名',
    nation_count INTEGER DEFAULT 0 COMMENT '使用该学说的国家数量',
    total_battles INTEGER DEFAULT 0 COMMENT '使用该学说的战斗次数',
    victories INTEGER DEFAULT 0 COMMENT '胜利次数',
    last_updated INTEGER NOT NULL COMMENT '最后更新时间戳'
);

-- ===============================================
-- 迁移完成
-- ===============================================