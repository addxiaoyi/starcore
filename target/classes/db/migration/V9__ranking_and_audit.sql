-- StarCore V9: 排行榜系统和交易审计表
-- Version: 9
-- Description: 添加排行榜系统、交易审计和审计日志表结构

-- ============================================
-- 排行榜快照表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_ranking_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    snapshot_id VARCHAR(36) NOT NULL UNIQUE,
    ranking_type VARCHAR(32) NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    player_name VARCHAR(64),
    score DOUBLE NOT NULL,
    rank_position INT NOT NULL,
    nation_id VARCHAR(36),
    nation_name VARCHAR(64),
    metadata TEXT,
    snapshot_date DATE NOT NULL,
    created_at BIGINT NOT NULL,
    INDEX idx_ranking_type (ranking_type),
    INDEX idx_snapshot_date (snapshot_date),
    INDEX idx_rank (rank_position),
    INDEX idx_player (player_uuid),
    UNIQUE KEY uk_type_date_rank (ranking_type, snapshot_date, rank_position)
);

-- ============================================
-- KDA 统计表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_kda_stats (
    player_uuid VARCHAR(36) PRIMARY KEY,
    kills INT DEFAULT 0,
    deaths INT DEFAULT 0,
    assists INT DEFAULT 0,
    win_streak INT DEFAULT 0,
    lose_streak INT DEFAULT 0,
    best_streak INT DEFAULT 0,
    total_games INT DEFAULT 0,
    wins INT DEFAULT 0,
    total_damage_dealt DOUBLE DEFAULT 0.0,
    total_damage_taken DOUBLE DEFAULT 0.0,
    total_healing DOUBLE DEFAULT 0.0,
    last_updated BIGINT NOT NULL,
    INDEX idx_kills (kills DESC),
    INDEX idx_deaths (deaths ASC),
    INDEX idx_kda ((kills + assists) / NULLIF(deaths, 0))
);

-- ============================================
-- 交易订单表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_trade_orders (
    order_id VARCHAR(36) PRIMARY KEY,
    order_type VARCHAR(16) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    quantity DECIMAL(20, 4) NOT NULL,
    price_per_unit DECIMAL(20, 4) NOT NULL,
    total_price DECIMAL(20, 4) NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    status VARCHAR(32) NOT NULL,
    nation_id VARCHAR(36),
    created_at BIGINT NOT NULL,
    matched_at BIGINT,
    expired_at BIGINT,
    cancelled_at BIGINT,
    cancellation_reason TEXT,
    INDEX idx_type (order_type),
    INDEX idx_resource (resource_type),
    INDEX idx_player (player_uuid),
    INDEX idx_status (status),
    INDEX idx_created (created_at)
);

-- ============================================
-- 交易历史表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_trade_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trade_id VARCHAR(36) NOT NULL UNIQUE,
    buy_order_id VARCHAR(36),
    sell_order_id VARCHAR(36),
    resource_type VARCHAR(64) NOT NULL,
    quantity DECIMAL(20, 4) NOT NULL,
    price_per_unit DECIMAL(20, 4) NOT NULL,
    total_price DECIMAL(20, 4) NOT NULL,
    buyer_uuid VARCHAR(36) NOT NULL,
    seller_uuid VARCHAR(36) NOT NULL,
    buyer_nation_id VARCHAR(36),
    seller_nation_id VARCHAR(36),
    market_price_before DECIMAL(20, 4),
    market_price_after DECIMAL(20, 4),
    tax_amount DECIMAL(20, 4) DEFAULT 0.0,
    tax_rate DECIMAL(5, 4) DEFAULT 0.0,
    traded_at BIGINT NOT NULL,
    INDEX idx_resource (resource_type),
    INDEX idx_buyer (buyer_uuid),
    INDEX idx_seller (seller_uuid),
    INDEX idx_traded_at (traded_at)
);

-- ============================================
-- 审计日志表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    audit_id VARCHAR(36) NOT NULL UNIQUE,
    event_type VARCHAR(64) NOT NULL,
    category VARCHAR(32) NOT NULL,
    actor_uuid VARCHAR(36),
    actor_name VARCHAR(64),
    target_uuid VARCHAR(36),
    target_type VARCHAR(32),
    action VARCHAR(64) NOT NULL,
    details TEXT,
    old_value TEXT,
    new_value TEXT,
    ip_address VARCHAR(45),
    server_id VARCHAR(64),
    occurred_at BIGINT NOT NULL,
    INDEX idx_event_type (event_type),
    INDEX idx_category (category),
    INDEX idx_actor (actor_uuid),
    INDEX idx_target (target_uuid),
    INDEX idx_occurred (occurred_at)
);

-- ============================================
-- 委托任务表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_delegation_tasks (
    task_id VARCHAR(36) PRIMARY KEY,
    task_type VARCHAR(32) NOT NULL,
    task_name VARCHAR(128) NOT NULL,
    description TEXT,
    requester_uuid VARCHAR(36) NOT NULL,
    assigned_uuid VARCHAR(36),
    reward_type VARCHAR(32) NOT NULL,
    reward_amount DECIMAL(20, 4) NOT NULL,
    difficulty VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    progress INT DEFAULT 0,
    target INT DEFAULT 1,
    expires_at BIGINT,
    created_at BIGINT NOT NULL,
    accepted_at BIGINT,
    completed_at BIGINT,
    cancelled_at BIGINT,
    INDEX idx_type (task_type),
    INDEX idx_requester (requester_uuid),
    INDEX idx_assigned (assigned_uuid),
    INDEX idx_status (status),
    INDEX idx_created (created_at)
);

-- ============================================
-- 邮件附件表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_mail_attachments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mail_id VARCHAR(36) NOT NULL,
    item_id VARCHAR(64) NOT NULL,
    item_name VARCHAR(128) NOT NULL,
    quantity INT NOT NULL,
    metadata TEXT,
    slot INT,
    INDEX idx_mail (mail_id),
    FOREIGN KEY (mail_id) REFERENCES starcore_mail(id) ON DELETE CASCADE
);

-- ============================================
-- 索引优化
-- ============================================
CREATE INDEX IF NOT EXISTS idx_trade_orders_price
    ON starcore_trade_orders(order_type, resource_type, price_per_unit);

CREATE INDEX IF NOT EXISTS idx_audit_category_time
    ON starcore_audit_log(category, occurred_at DESC);
