-- ===============================================
-- StarCore 卫星国模块数据库迁移
-- 迁移版本: V16__satellite_state
-- 描述: 添加卫星国关系表
-- ===============================================

-- 卫星国关系表
CREATE TABLE IF NOT EXISTS satellite_relations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    suzerain_id VARCHAR(36) NOT NULL COMMENT '宗主国ID (UUID)',
    satellite_id VARCHAR(36) NOT NULL COMMENT '卫星国ID (UUID)',
    relation_type VARCHAR(20) NOT NULL DEFAULT 'VASSAL' COMMENT '关系类型: DOMINION, VASSAL, PROTECTORATE, COLONY',
    tribute_rate DOUBLE NOT NULL DEFAULT 0.10 COMMENT '贡金税率 (0.0-1.0)',
    established_at INTEGER NOT NULL COMMENT '建立时间戳 (Unix epoch)',
    active BOOLEAN NOT NULL DEFAULT 1 COMMENT '是否活跃',
    established_reason TEXT DEFAULT '' COMMENT '建立原因',
    UNIQUE(suzerain_id, satellite_id)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_satellite_suzerain ON satellite_relations(suzerain_id);
CREATE INDEX IF NOT EXISTS idx_satellite_satellite ON satellite_relations(satellite_id);
CREATE INDEX IF NOT EXISTS idx_satellite_active ON satellite_relations(active);

-- 卫星国关系历史记录表（用于审计和统计）
CREATE TABLE IF NOT EXISTS satellite_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    suzerain_id VARCHAR(36) NOT NULL COMMENT '宗主国ID',
    satellite_id VARCHAR(36) NOT NULL COMMENT '卫星国ID',
    relation_type VARCHAR(20) NOT NULL COMMENT '关系类型',
    action_type VARCHAR(20) NOT NULL COMMENT '操作类型: ESTABLISH, DISSOLVE, INDEPENDENCE, RELEASE',
    action_at INTEGER NOT NULL COMMENT '操作时间戳',
    action_by VARCHAR(36) COMMENT '操作者ID (可为null表示系统)',
    details TEXT DEFAULT '' COMMENT '详细信息 (JSON格式)'
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_history_suzerain ON satellite_history(suzerain_id);
CREATE INDEX IF NOT EXISTS idx_history_satellite ON satellite_history(satellite_id);
CREATE INDEX IF NOT EXISTS idx_history_action_at ON satellite_history(action_at);

-- 贡金记录表
CREATE TABLE IF NOT EXISTS satellite_tribute_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    suzerain_id VARCHAR(36) NOT NULL COMMENT '宗主国ID',
    satellite_id VARCHAR(36) NOT NULL COMMENT '卫星国ID',
    amount DECIMAL(20,2) NOT NULL COMMENT '贡金金额',
    previous_balance DECIMAL(20,2) NOT NULL COMMENT '收取前宗主国余额',
    new_balance DECIMAL(20,2) NOT NULL COMMENT '收取后宗主国余额',
    collected_at INTEGER NOT NULL COMMENT '收取时间戳'
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_tribute_suzerain ON satellite_tribute_log(suzerain_id);
CREATE INDEX IF NOT EXISTS idx_tribute_satellite ON satellite_tribute_log(satellite_id);
CREATE INDEX IF NOT EXISTS idx_tribute_collected_at ON satellite_tribute_log(collected_at);

-- ===============================================
-- 迁移完成
-- ===============================================