-- ========================================
-- STARCORE 间谍模块数据库迁移
-- ========================================
-- Feature ID: 28
-- Module: Espionage
-- Description: 间谍训练、行动、反间谍系统

-- ========================================
-- 间谍表
-- ========================================
CREATE TABLE IF NOT EXISTS espionage_spies (
    spy_id VARCHAR(36) PRIMARY KEY,
    owner_nation_id VARCHAR(36) NOT NULL,
    owner_nation_name VARCHAR(255) NOT NULL,
    trainer_id VARCHAR(36) NOT NULL,
    spy_type VARCHAR(50) NOT NULL,
    experience INTEGER DEFAULT 0,
    missions_completed INTEGER DEFAULT 0,
    missions_failed INTEGER DEFAULT 0,
    recruited_at TIMESTAMP NOT NULL,
    last_mission_at TIMESTAMP,
    morale DOUBLE PRECISION DEFAULT 100.0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_spies_owner (owner_nation_id),
    INDEX idx_spies_type (spy_type),
    INDEX idx_spies_morale (morale)
);

-- ========================================
-- 间谍行动历史表
-- ========================================
CREATE TABLE IF NOT EXISTS espionage_operations (
    operation_id VARCHAR(36) PRIMARY KEY,
    spy_id VARCHAR(36) NOT NULL,
    source_nation_id VARCHAR(36) NOT NULL,
    source_nation_name VARCHAR(255) NOT NULL,
    target_nation_id VARCHAR(36) NOT NULL,
    target_nation_name VARCHAR(255) NOT NULL,
    operation_type VARCHAR(50) NOT NULL,
    difficulty INTEGER NOT NULL,
    cost DOUBLE PRECISION NOT NULL,
    start_time TIMESTAMP NOT NULL,
    duration_ticks BIGINT NOT NULL,
    was_detected BOOLEAN DEFAULT FALSE,
    status VARCHAR(20) NOT NULL,
    end_time TIMESTAMP,
    success BOOLEAN,
    report TEXT,
    reward TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ops_spy (spy_id),
    INDEX idx_ops_source (source_nation_id),
    INDEX idx_ops_target (target_nation_id),
    INDEX idx_ops_status (status),
    INDEX idx_ops_time (start_time)
);

-- ========================================
-- 国家反间谍等级表
-- ========================================
CREATE TABLE IF NOT EXISTS espionage_counter_intel (
    nation_id VARCHAR(36) PRIMARY KEY,
    intelligence_level INTEGER DEFAULT 1,
    total_detections INTEGER DEFAULT 0,
    total_spies_caught INTEGER DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (nation_id) REFERENCES espionage_spies(owner_nation_id) ON DELETE CASCADE
);

-- ========================================
-- 行动冷却记录表
-- ========================================
CREATE TABLE IF NOT EXISTS espionage_cooldowns (
    nation_id VARCHAR(36) PRIMARY KEY,
    last_operation_time TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ========================================
-- 间谍行动报告表
-- ========================================
CREATE TABLE IF NOT EXISTS espionage_reports (
    report_id VARCHAR(36) PRIMARY KEY,
    operation_id VARCHAR(36) NOT NULL,
    spy_id VARCHAR(36) NOT NULL,
    source_nation_id VARCHAR(36) NOT NULL,
    target_nation_id VARCHAR(36) NOT NULL,
    report_type VARCHAR(50) NOT NULL,
    report_content TEXT,
    is_sensitive BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_reports_operation (operation_id),
    INDEX idx_reports_spy (spy_id),
    INDEX idx_reports_source (source_nation_id)
);

-- ========================================
-- 渗透网络表（用于双面间谍）
-- ========================================
CREATE TABLE IF NOT EXISTS espionage_networks (
    network_id VARCHAR(36) PRIMARY KEY,
    source_nation_id VARCHAR(36) NOT NULL,
    target_nation_id VARCHAR(36) NOT NULL,
    spy_id VARCHAR(36) NOT NULL,
    network_strength INTEGER DEFAULT 1,
    is_active BOOLEAN DEFAULT TRUE,
    established_at TIMESTAMP NOT NULL,
    last_contact_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_networks_source (source_nation_id),
    INDEX idx_networks_target (target_nation_id),
    INDEX idx_networks_spy (spy_id),
    UNIQUE KEY uk_networks_spy (spy_id)
);

-- ========================================
-- 统计数据视图
-- ========================================
CREATE OR REPLACE VIEW espionage_stats_view AS
SELECT
    es.owner_nation_id,
    es.owner_nation_name,
    COUNT(DISTINCT es.spy_id) AS total_spies,
    COUNT(DISTINCT CASE WHEN eo.status = 'COMPLETED' AND eo.success = TRUE THEN eo.operation_id END) AS successful_operations,
    COUNT(DISTINCT CASE WHEN eo.status IN ('FAILED', 'EXPOSED') THEN eo.operation_id END) AS failed_operations,
    SUM(es.missions_completed) AS total_missions,
    AVG(es.morale) AS avg_morale,
    COALESCE(ci.intelligence_level, 1) AS counter_intel_level,
    COALESCE(ci.total_detections, 0) AS total_detections,
    COALESCE(ci.total_spies_caught, 0) AS total_spies_caught
FROM espionage_spies es
LEFT JOIN espionage_operations eo ON es.spy_id = eo.spy_id
LEFT JOIN espionage_counter_intel ci ON es.owner_nation_id = ci.nation_id
GROUP BY es.owner_nation_id, es.owner_nation_name, ci.intelligence_level, ci.total_detections, ci.total_spies_caught;
