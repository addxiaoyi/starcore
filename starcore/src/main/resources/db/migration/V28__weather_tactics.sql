-- ========================================
-- Weather Tactics Module Database Migration
-- Feature ID: 16
-- Package: dev.starcore.starcore.module.army.weather
-- ========================================

-- 天气战术升级表
CREATE TABLE IF NOT EXISTS weather_tactics (
    nation_id VARCHAR(36) NOT NULL COMMENT '国家ID',
    tactics_type VARCHAR(64) NOT NULL COMMENT '战术类型',
    level INT NOT NULL DEFAULT 0 COMMENT '当前等级',
    unlocked_at BIGINT NOT NULL COMMENT '解锁时间戳',
    updated_at BIGINT NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (nation_id, tactics_type),
    INDEX idx_nation_tactics (nation_id),
    INDEX idx_tactics_level (tactics_type, level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 天气战术加成表
CREATE TABLE IF NOT EXISTS weather_tactics_boosts (
    nation_id VARCHAR(36) NOT NULL COMMENT '国家ID',
    weather_type VARCHAR(32) NOT NULL COMMENT '天气类型',
    attack_mult DOUBLE NOT NULL DEFAULT 1.0 COMMENT '攻击力倍数',
    defense_mult DOUBLE NOT NULL DEFAULT 1.0 COMMENT '防御力倍数',
    movement_mult DOUBLE NOT NULL DEFAULT 1.0 COMMENT '移动力倍数',
    morale_bonus DOUBLE NOT NULL DEFAULT 1.0 COMMENT '士气加成',
    tactics_name VARCHAR(64) COMMENT '战术名称',
    description TEXT COMMENT '战术描述',
    created_at BIGINT NOT NULL COMMENT '创建时间戳',
    updated_at BIGINT NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (nation_id, weather_type),
    INDEX idx_nation_boosts (nation_id),
    INDEX idx_weather_boosts (weather_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 军队天气状态表
CREATE TABLE IF NOT EXISTS army_weather_states (
    army_id VARCHAR(36) NOT NULL COMMENT '军队ID',
    nation_id VARCHAR(36) NOT NULL COMMENT '国家ID',
    unit_type VARCHAR(32) NOT NULL COMMENT '兵种类型',
    current_weather VARCHAR(32) NOT NULL DEFAULT 'CLEAR' COMMENT '当前天气',
    previous_weather VARCHAR(32) NOT NULL DEFAULT 'CLEAR' COMMENT '之前天气',
    accumulated_advantage DOUBLE NOT NULL DEFAULT 0.0 COMMENT '累积优势值',
    last_weather_change BIGINT NOT NULL COMMENT '最后天气变化时间',
    created_at BIGINT NOT NULL COMMENT '创建时间戳',
    updated_at BIGINT NOT NULL COMMENT '更新时间戳',
    PRIMARY KEY (army_id),
    INDEX idx_army_nation (nation_id),
    INDEX idx_army_weather (current_weather),
    INDEX idx_army_advantage (accumulated_advantage)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 天气战术事件日志表
CREATE TABLE IF NOT EXISTS weather_tactics_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '事件ID',
    nation_id VARCHAR(36) NOT NULL COMMENT '国家ID',
    event_type VARCHAR(32) NOT NULL COMMENT '事件类型',
    weather_type VARCHAR(32) COMMENT '相关天气类型',
    tactics_type VARCHAR(64) COMMENT '相关战术类型',
    details JSON COMMENT '事件详情',
    occurred_at BIGINT NOT NULL COMMENT '发生时间戳',
    INDEX idx_event_nation (nation_id),
    INDEX idx_event_type (event_type),
    INDEX idx_event_time (occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 天气战术战斗记录表
CREATE TABLE IF NOT EXISTS weather_battle_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    attacker_id VARCHAR(36) NOT NULL COMMENT '攻击方ID',
    defender_id VARCHAR(36) NOT NULL COMMENT '防守方ID',
    nation_id VARCHAR(36) NOT NULL COMMENT '所属国家ID',
    weather_type VARCHAR(32) NOT NULL COMMENT '战斗时天气',
    attacker_bonus DOUBLE NOT NULL DEFAULT 1.0 COMMENT '攻击方加成',
    defender_bonus DOUBLE NOT NULL DEFAULT 1.0 COMMENT '防守方加成',
    result VARCHAR(16) COMMENT '战斗结果',
    damage_dealt DOUBLE COMMENT '造成的伤害',
    damage_taken DOUBLE COMMENT '受到的伤害',
    occurred_at BIGINT NOT NULL COMMENT '战斗时间戳',
    INDEX idx_battle_attacker (attacker_id),
    INDEX idx_battle_defender (defender_id),
    INDEX idx_battle_nation (nation_id),
    INDEX idx_battle_weather (weather_type),
    INDEX idx_battle_time (occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 战术升级历史表
CREATE TABLE IF NOT EXISTS weather_tactics_upgrade_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    nation_id VARCHAR(36) NOT NULL COMMENT '国家ID',
    player_id VARCHAR(36) NOT NULL COMMENT '操作玩家ID',
    tactics_type VARCHAR(64) NOT NULL COMMENT '升级的战术',
    from_level INT NOT NULL COMMENT '原等级',
    to_level INT NOT NULL COMMENT '新等级',
    cost DOUBLE NOT NULL COMMENT '消耗金币',
    upgraded_at BIGINT NOT NULL COMMENT '升级时间',
    INDEX idx_history_nation (nation_id),
    INDEX idx_history_player (player_id),
    INDEX idx_history_tactics (tactics_type),
    INDEX idx_history_time (upgraded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;