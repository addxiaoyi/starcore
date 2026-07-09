-- V15__lease_contracts.sql
-- 租约契约系统数据库表
-- Feature ID: 5

-- 租约契约主表
CREATE TABLE IF NOT EXISTS lease_contracts (
    id VARCHAR(36) PRIMARY KEY,
    lessor_nation_id VARCHAR(36),
    lessor_player_id VARCHAR(36) NOT NULL,
    tenant_nation_id VARCHAR(36),
    tenant_player_id VARCHAR(36),
    type VARCHAR(32) NOT NULL,
    region_id VARCHAR(255) NOT NULL,
    monthly_rent DECIMAL(19,2) NOT NULL,
    total_value DECIMAL(19,2) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    signed_at TIMESTAMP,
    start_date TIMESTAMP,
    end_date TIMESTAMP,
    next_payment_due TIMESTAMP,
    last_payment_at TIMESTAMP,
    lessor_signed BOOLEAN DEFAULT FALSE,
    tenant_signed BOOLEAN DEFAULT FALSE,
    overdue_days INT DEFAULT 0,
    termination_reason TEXT,
    INDEX idx_lessor_nation (lessor_nation_id),
    INDEX idx_tenant_nation (tenant_nation_id),
    INDEX idx_lessor_player (lessor_player_id),
    INDEX idx_tenant_player (tenant_player_id),
    INDEX idx_region (region_id),
    INDEX idx_status (status),
    INDEX idx_end_date (end_date),
    INDEX idx_next_payment (next_payment_due)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 租约历史记录表（用于审计）
CREATE TABLE IF NOT EXISTS lease_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id VARCHAR(36) NOT NULL,
    action VARCHAR(32) NOT NULL,
    actor_id VARCHAR(36),
    actor_type VARCHAR(16) NOT NULL,
    details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_contract (contract_id),
    INDEX idx_actor (actor_id),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 租约签署记录表
CREATE TABLE IF NOT EXISTS lease_signatures (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id VARCHAR(36) NOT NULL,
    signer_id VARCHAR(36) NOT NULL,
    signer_type VARCHAR(16) NOT NULL,
    signed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    signature_data TEXT,
    INDEX idx_contract (contract_id),
    UNIQUE INDEX idx_unique_signature (contract_id, signer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 租约支付记录表
CREATE TABLE IF NOT EXISTS lease_payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contract_id VARCHAR(36) NOT NULL,
    payer_id VARCHAR(36) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    months_paid INT NOT NULL,
    payment_method VARCHAR(32),
    transaction_id VARCHAR(64),
    paid_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_contract (contract_id),
    INDEX idx_payer (payer_id),
    INDEX idx_paid_at (paid_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
