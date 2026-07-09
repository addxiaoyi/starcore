-- StarCore V13: 契约制度数据库
-- Version: 13
-- Description: 添加租借契约系统所需的表结构

-- ============================================
-- 租借契约主表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_lease_contracts (
    contract_id VARCHAR(36) PRIMARY KEY,
    lessor_nation_id VARCHAR(36) NOT NULL COMMENT '出租方国家ID',
    lessee_nation_id VARCHAR(36) NOT NULL COMMENT '承租方国家ID（可为空表示个人）',
    lessee_player_id VARCHAR(36) COMMENT '承租方玩家ID（nation_id为空时使用）',
    start_time BIGINT NOT NULL COMMENT '契约开始时间戳',
    end_time BIGINT NOT NULL COMMENT '契约结束时间戳',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/PENDING/EXPIRED/TERMINATED/COMPLETED',
    total_rent DECIMAL(20, 2) NOT NULL COMMENT '总租金',
    rent_per_day DECIMAL(20, 2) NOT NULL COMMENT '每日租金',
    rent_per_chunk DECIMAL(20, 2) NOT NULL COMMENT '每区块每日租金',
    chunks_count INT NOT NULL COMMENT '租借区块数量',
    world VARCHAR(64) NOT NULL COMMENT '租借领土所在世界',
    chunk_coords TEXT NOT NULL COMMENT '租借的区块坐标列表（JSON数组）',
    creation_fee DECIMAL(20, 2) DEFAULT 0.0 COMMENT '创建手续费',
    auto_renewal BOOLEAN DEFAULT FALSE COMMENT '是否启用自动续约',
    renewal_count INT DEFAULT 0 COMMENT '已续约次数',
    termination_reason VARCHAR(128) COMMENT '终止原因',
    terminated_by VARCHAR(36) COMMENT '终止执行者ID',
    terminated_at BIGINT COMMENT '终止时间戳',
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    INDEX idx_lessor (lessor_nation_id),
    INDEX idx_lessee_nation (lessee_nation_id),
    INDEX idx_lessee_player (lessee_player_id),
    INDEX idx_status (status),
    INDEX idx_end_time (end_time),
    INDEX idx_world_chunks (world, chunk_coords)
);

-- ============================================
-- 租借付款记录表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_lease_payments (
    payment_id VARCHAR(36) PRIMARY KEY,
    contract_id VARCHAR(36) NOT NULL,
    payer_id VARCHAR(36) NOT NULL COMMENT '付款方（国家或玩家ID）',
    payer_type VARCHAR(16) NOT NULL COMMENT 'NATION/PLAYER',
    amount DECIMAL(20, 2) NOT NULL COMMENT '付款金额',
    payment_type VARCHAR(32) NOT NULL COMMENT 'INITIAL/RENEWAL/DAILY/AUTO',
    payment_period_start BIGINT COMMENT '付款覆盖期间开始',
    payment_period_end BIGINT COMMENT '付款覆盖期间结束',
    payment_time BIGINT NOT NULL,
    INDEX idx_contract (contract_id),
    INDEX idx_payer (payer_id, payer_type),
    INDEX idx_payment_time (payment_time)
);

-- ============================================
-- 租借区块权限表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_lease_chunk_permissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id VARCHAR(36) NOT NULL,
    world VARCHAR(64) NOT NULL,
    chunk_x INT NOT NULL,
    chunk_z INT NOT NULL,
    lessee_can_build BOOLEAN DEFAULT TRUE,
    lessee_can_use_containers BOOLEAN DEFAULT TRUE,
    lessee_can_break_blocks BOOLEAN DEFAULT TRUE,
    lessee_can_interact BOOLEAN DEFAULT TRUE,
    custom_rules TEXT COMMENT '自定义权限规则（JSON）',
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    UNIQUE KEY uk_contract_chunk (contract_id, world, chunk_x, chunk_z),
    INDEX idx_chunk (world, chunk_x, chunk_z)
);

-- ============================================
-- 租借通知记录表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_lease_notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id VARCHAR(36) NOT NULL,
    notification_type VARCHAR(32) NOT NULL COMMENT 'EXPIRING_SOON/EXPIRED/RENEWED/TERMINATED',
    recipient_id VARCHAR(36) NOT NULL COMMENT '接收者ID',
    recipient_type VARCHAR(16) NOT NULL COMMENT 'NATION/PLAYER',
    message TEXT NOT NULL,
    sent_at BIGINT NOT NULL,
    acknowledged BOOLEAN DEFAULT FALSE,
    acknowledged_at BIGINT,
    INDEX idx_contract (contract_id),
    INDEX idx_recipient (recipient_id),
    INDEX idx_sent_at (sent_at)
);

-- ============================================
-- 租借统计表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_lease_stats (
    nation_id VARCHAR(36) PRIMARY KEY,
    total_contracts_signed INT DEFAULT 0,
    active_contracts_as_lessor INT DEFAULT 0,
    active_contracts_as_lessee INT DEFAULT 0,
    total_rent_earned DECIMAL(20, 2) DEFAULT 0.0,
    total_rent_paid DECIMAL(20, 2) DEFAULT 0.0,
    total_chunks_leased_out INT DEFAULT 0,
    total_chunks_leased_in INT DEFAULT 0,
    longest_contract_days INT DEFAULT 0,
    last_activity_at BIGINT,
    updated_at BIGINT NOT NULL
);

-- ============================================
-- 索引优化
-- ============================================
CREATE INDEX IF NOT EXISTS idx_lease_status_endtime
    ON starcore_lease_contracts(status, end_time);

CREATE INDEX IF NOT EXISTS idx_lease_active_nations
    ON starcore_lease_contracts(lessor_nation_id, lessee_nation_id, status);
