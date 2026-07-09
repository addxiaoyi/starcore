-- ===============================================
-- StarCore 合并公投模块数据库迁移
-- 迁移版本: V17__merge_referendum
-- 描述: 添加合并公投相关表
-- ===============================================

-- 合并公投表
CREATE TABLE IF NOT EXISTS merge_referendums (
    id VARCHAR(36) PRIMARY KEY COMMENT '公投ID (UUID)',
    proposer_id VARCHAR(36) NOT NULL COMMENT '发起者ID',
    proposer_name VARCHAR(255) NOT NULL COMMENT '发起者名称',
    proposer_nation_id VARCHAR(36) NOT NULL COMMENT '发起国家ID',
    target_nation_id VARCHAR(36) NOT NULL COMMENT '目标国家ID',
    target_nation_name VARCHAR(255) NOT NULL COMMENT '目标国家名称',
    new_nation_name VARCHAR(32) NOT NULL COMMENT '合并后国家名称',
    state VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING, APPROVED, REJECTED, EXPIRED, CANCELLED, EXECUTED, FAILED',
    created_at INTEGER NOT NULL COMMENT '创建时间戳 (Unix epoch)',
    expires_at INTEGER NOT NULL COMMENT '过期时间戳 (Unix epoch)',
    result_nation_id VARCHAR(36) COMMENT '结果国家ID (合并后)',
    result_message TEXT COMMENT '结果消息',
    INDEX idx_proposer_nation (proposer_nation_id),
    INDEX idx_target_nation (target_nation_id),
    INDEX idx_state (state)
);

-- 合并公投投票表
CREATE TABLE IF NOT EXISTS merge_votes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    referendum_id VARCHAR(36) NOT NULL COMMENT '公投ID',
    voter_id VARCHAR(36) NOT NULL COMMENT '投票者ID',
    approved BOOLEAN NOT NULL COMMENT '是否赞成',
    voted_at INTEGER NOT NULL COMMENT '投票时间戳',
    UNIQUE(referendum_id, voter_id),
    INDEX idx_referendum (referendum_id),
    INDEX idx_voter (voter_id)
);

-- 合并历史记录表（用于审计）
CREATE TABLE IF NOT EXISTS merge_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    referendum_id VARCHAR(36) NOT NULL COMMENT '公投ID',
    nation1_id VARCHAR(36) NOT NULL COMMENT '国家1ID',
    nation2_id VARCHAR(36) NOT NULL COMMENT '国家2ID',
    result_nation_id VARCHAR(36) COMMENT '结果国家ID',
    status VARCHAR(20) NOT NULL COMMENT '最终状态',
    total_votes INTEGER DEFAULT 0 COMMENT '总票数',
    approve_votes INTEGER DEFAULT 0 COMMENT '赞成票',
    reject_votes INTEGER DEFAULT 0 COMMENT '反对票',
    executed_at INTEGER COMMENT '执行时间戳',
    created_at INTEGER NOT NULL COMMENT '创建时间戳',
    INDEX idx_nation1 (nation1_id),
    INDEX idx_nation2 (nation2_id),
    INDEX idx_created (created_at)
);

-- ===============================================
-- 迁移完成
-- ===============================================