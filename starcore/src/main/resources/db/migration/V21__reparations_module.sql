-- ==========================================
-- StarCore 战争赔款模块数据库迁移
-- Feature ID: 30 - War Reparations
-- ==========================================

-- 赔款记录表
CREATE TABLE IF NOT EXISTS war_reparations (
    id VARCHAR(36) PRIMARY KEY,
    treaty_id VARCHAR(36) NOT NULL,
    payer_id VARCHAR(36) NOT NULL,
    receiver_id VARCHAR(36) NOT NULL,
    total_amount DECIMAL(20, 2) NOT NULL,
    paid_amount DECIMAL(20, 2) NOT NULL DEFAULT 0.00,
    total_installments INT NOT NULL,
    paid_installments INT NOT NULL DEFAULT 0,
    start_date BIGINT NOT NULL,
    last_payment_date BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    INDEX idx_treaty_id (treaty_id),
    INDEX idx_payer_id (payer_id),
    INDEX idx_receiver_id (receiver_id),
    INDEX idx_status (status)
);

-- 赔款支付历史表
CREATE TABLE IF NOT EXISTS war_reparation_payments (
    id VARCHAR(36) PRIMARY KEY,
    reparation_id VARCHAR(36) NOT NULL,
    amount DECIMAL(20, 2) NOT NULL,
    paid_at BIGINT NOT NULL,
    payer_balance_before DECIMAL(20, 2) NOT NULL,
    payer_balance_after DECIMAL(20, 2) NOT NULL,
    receiver_balance_before DECIMAL(20, 2) NOT NULL,
    receiver_balance_after DECIMAL(20, 2) NOT NULL,
    notes TEXT,
    FOREIGN KEY (reparation_id) REFERENCES war_reparations(id) ON DELETE CASCADE,
    INDEX idx_reparation_id (reparation_id),
    INDEX idx_paid_at (paid_at)
);

-- 赔款违约记录表
CREATE TABLE IF NOT EXISTS war_reparation_defaults (
    id VARCHAR(36) PRIMARY KEY,
    reparation_id VARCHAR(36) NOT NULL,
    defaulted_at BIGINT NOT NULL,
    outstanding_amount DECIMAL(20, 2) NOT NULL,
    reason VARCHAR(255),
    penalty_applied BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (reparation_id) REFERENCES war_reparations(id) ON DELETE CASCADE,
    INDEX idx_reparation_id (reparation_id),
    INDEX idx_defaulted_at (defaulted_at)
);

-- 赔款事件日志表（用于审计和历史追踪）
CREATE TABLE IF NOT EXISTS war_reparation_events (
    id VARCHAR(36) PRIMARY KEY,
    reparation_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_data TEXT,
    created_at BIGINT NOT NULL,
    created_by VARCHAR(36),
    FOREIGN KEY (reparation_id) REFERENCES war_reparations(id) ON DELETE CASCADE,
    INDEX idx_reparation_id (reparation_id),
    INDEX idx_event_type (event_type),
    INDEX idx_created_at (created_at)
);

-- ==========================================
-- 回滚语句（如果需要回滚此迁移）
-- ==========================================
-- DROP TABLE IF EXISTS war_reparation_events;
-- DROP TABLE IF EXISTS war_reparation_defaults;
-- DROP TABLE IF EXISTS war_reparation_payments;
-- DROP TABLE IF EXISTS war_reparations;