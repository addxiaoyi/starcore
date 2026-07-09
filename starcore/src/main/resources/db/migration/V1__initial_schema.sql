-- StarCore 初始数据库架构
-- Version: 1
-- Description: 创建核心表结构

-- ============================================
-- 国家状态表 (Nation State)
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_nation_state (
    property_key VARCHAR(255) NOT NULL PRIMARY KEY,
    property_value TEXT NOT NULL
);

-- ============================================
-- 外交关系表 (Diplomacy)
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_diplomacy_state (
    property_key VARCHAR(255) NOT NULL PRIMARY KEY,
    property_value TEXT NOT NULL
);

-- ============================================
-- 政策状态表 (Policy)
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_policy_state (
    property_key VARCHAR(255) NOT NULL PRIMARY KEY,
    property_value TEXT NOT NULL
);

-- ============================================
-- 资源状态表 (Resource)
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_resource_state (
    property_key VARCHAR(255) NOT NULL PRIMARY KEY,
    property_value TEXT NOT NULL
);

-- ============================================
-- 科技状态表 (Technology)
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_technology_state (
    property_key VARCHAR(255) NOT NULL PRIMARY KEY,
    property_value TEXT NOT NULL
);

-- ============================================
-- 国库状态表 (Treasury)
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_treasury_state (
    property_key VARCHAR(255) NOT NULL PRIMARY KEY,
    property_value TEXT NOT NULL
);

-- ============================================
-- 战争状态表 (War)
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_war_state (
    property_key VARCHAR(255) NOT NULL PRIMARY KEY,
    property_value TEXT NOT NULL
);

-- ============================================
-- 官员状态表 (Officer)
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_officer_state (
    property_key VARCHAR(255) NOT NULL PRIMARY KEY,
    property_value TEXT NOT NULL
);

-- ============================================
-- 事件状态表 (Event)
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_event_state (
    property_key VARCHAR(255) NOT NULL PRIMARY KEY,
    property_value TEXT NOT NULL
);

-- ============================================
-- 决议状态表 (Resolution)
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_resolution_state (
    property_key VARCHAR(255) NOT NULL PRIMARY KEY,
    property_value TEXT NOT NULL
);

-- ============================================
-- 领地状态表 (Territory)
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_territory_state (
    property_key VARCHAR(255) NOT NULL PRIMARY KEY,
    property_value TEXT NOT NULL
);

-- ============================================
-- 玩家余额表 (Player Balance)
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_player_balance (
    player_uuid CHAR(36) NOT NULL PRIMARY KEY,
    balance DECIMAL(20, 2) NOT NULL DEFAULT 0.00,
    last_updated BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_balance ON starcore_player_balance(balance);

-- ============================================
-- 经济事务日志表 (Economy Transactions)
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_economy_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_uuid CHAR(36) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    amount DECIMAL(20, 2) NOT NULL,
    balance_before DECIMAL(20, 2) NOT NULL,
    balance_after DECIMAL(20, 2) NOT NULL,
    reason VARCHAR(255),
    timestamp BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_player_transactions ON starcore_economy_transactions(player_uuid, timestamp);
CREATE INDEX IF NOT EXISTS idx_transaction_time ON starcore_economy_transactions(timestamp);
