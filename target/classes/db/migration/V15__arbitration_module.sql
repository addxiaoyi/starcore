-- StarCore 领土仲裁模块数据库架构
-- Version: 15
-- Description: 创建仲裁模块相关表结构

-- ============================================
-- 仲裁案件表 (Arbitration Cases)
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_arbitration_case (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    claimant VARCHAR(36) NOT NULL,
    respondent VARCHAR(36) NOT NULL,
    case_type VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    disputed_chunks TEXT,
    claim_fee DECIMAL(20, 2) NOT NULL DEFAULT 0.00,
    arbitrator VARCHAR(36),
    defense TEXT,
    result VARCHAR(30),
    ruling TEXT,
    ruling_reason TEXT,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    accepted_at BIGINT,
    ruling_at BIGINT
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_arb_claimant ON starcore_arbitration_case(claimant);
CREATE INDEX IF NOT EXISTS idx_arb_respondent ON starcore_arbitration_case(respondent);
CREATE INDEX IF NOT EXISTS idx_arb_status ON starcore_arbitration_case(status);
CREATE INDEX IF NOT EXISTS idx_arb_arbitrator ON starcore_arbitration_case(arbitrator);

-- ============================================
-- 仲裁证据表 (Arbitration Evidence)
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_arbitration_evidence (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_id VARCHAR(36) NOT NULL,
    submitter VARCHAR(36) NOT NULL,
    evidence_type VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
    content TEXT NOT NULL,
    submitted_at BIGINT NOT NULL,
    FOREIGN KEY (case_id) REFERENCES starcore_arbitration_case(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_evidence_case ON starcore_arbitration_evidence(case_id);
CREATE INDEX IF NOT EXISTS idx_evidence_submitter ON starcore_arbitration_evidence(submitter);

-- ============================================
-- 仲裁历史记录表 (Arbitration History)
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_arbitration_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_id VARCHAR(36) NOT NULL,
    action VARCHAR(50) NOT NULL,
    actor VARCHAR(36),
    details TEXT,
    timestamp BIGINT NOT NULL,
    FOREIGN KEY (case_id) REFERENCES starcore_arbitration_case(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_history_case ON starcore_arbitration_history(case_id);
CREATE INDEX IF NOT EXISTS idx_history_timestamp ON starcore_arbitration_history(timestamp);

-- ============================================
-- 仲裁员表 (Arbitrators)
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_arbitrator (
    uuid VARCHAR(36) NOT NULL PRIMARY KEY,
    nation_id VARCHAR(36),
    name VARCHAR(255) NOT NULL,
    cases_handled INT NOT NULL DEFAULT 0,
    cases_won INT NOT NULL DEFAULT 0,
    rating DECIMAL(3, 2) DEFAULT 5.00,
    is_active BOOLEAN DEFAULT TRUE,
    registered_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_arbitrator_nation ON starcore_arbitrator(nation_id);
CREATE INDEX IF NOT EXISTS idx_arbitrator_active ON starcore_arbitrator(is_active);

-- ============================================
-- 仲裁配置表 (Arbitration Settings)
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_arbitration_settings (
    setting_key VARCHAR(100) NOT NULL PRIMARY KEY,
    setting_value TEXT NOT NULL,
    updated_at BIGINT NOT NULL
);

-- 插入默认配置
INSERT OR IGNORE INTO starcore_arbitration_settings (setting_key, setting_value, updated_at) VALUES
    ('filing-fee', '100', (strftime('%s', 'now') * 1000)),
    ('minimum-claim-fee', '500', (strftime('%s', 'now') * 1000)),
    ('maximum-claim-fee', '10000', (strftime('%s', 'now') * 1000)),
    ('charge-filing-fee', 'true', (strftime('%s', 'now') * 1000)),
    ('refund-on-withdrawal', 'true', (strftime('%s', 'now') * 1000)),
    ('max-evidence-per-side', '10', (strftime('%s', 'now') * 1000)),
    ('case-expiration-days', '30', (strftime('%s', 'now') * 1000));
