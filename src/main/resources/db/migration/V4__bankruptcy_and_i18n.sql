-- StarCore V4: 破产管理系统表
-- Version: 4
-- Description: 添加破产记录表用于持久化国家破产状态

-- ============================================
-- 破产状态表 (Bankruptcy)
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_bankruptcy_state (
    property_key VARCHAR(255) NOT NULL PRIMARY KEY,
    property_value TEXT NOT NULL
);

-- ============================================
-- 玩家语言设置表 (Player Locale)
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_player_locale (
    player_uuid CHAR(36) NOT NULL PRIMARY KEY,
    locale VARCHAR(20) NOT NULL,
    last_updated BIGINT NOT NULL
);

-- ============================================
-- 转账配置表 (Transfer Config)
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_transfer_config (
    config_key VARCHAR(255) NOT NULL PRIMARY KEY,
    config_value TEXT NOT NULL
);
