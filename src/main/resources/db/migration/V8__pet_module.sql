-- StarCore V8: 宠物/坐骑模块持久化表
-- Version: 8
-- Description: 添加宠物和坐骑系统的数据库持久化表结构
-- Previous: V4__bankruptcy_and_i18n.sql

-- ============================================
-- 宠物/坐骑基础表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_pets (
    pet_id VARCHAR(36) PRIMARY KEY,
    owner_uuid VARCHAR(36) NOT NULL,
    pet_type VARCHAR(32) NOT NULL,
    pet_name VARCHAR(64),
    nickname VARCHAR(64),
    level INT DEFAULT 1,
    experience INT DEFAULT 0,
    health DOUBLE DEFAULT 20.0,
    max_health DOUBLE DEFAULT 20.0,
    hunger INT DEFAULT 100,
    mood INT DEFAULT 100,
    loyalty INT DEFAULT 100,
    is_summoned BOOLEAN DEFAULT FALSE,
    equipment TEXT,
    stats TEXT,
    created_at BIGINT NOT NULL,
    last_interaction BIGINT NOT NULL,
    INDEX idx_owner (owner_uuid),
    INDEX idx_pet_type (pet_type)
);

-- ============================================
-- 宠物技能表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_pet_skills (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pet_id VARCHAR(36) NOT NULL,
    skill_id VARCHAR(32) NOT NULL,
    skill_level INT DEFAULT 1,
    unlocked_at BIGINT NOT NULL,
    UNIQUE KEY uk_pet_skill (pet_id, skill_id),
    INDEX idx_pet (pet_id),
    FOREIGN KEY (pet_id) REFERENCES starcore_pets(pet_id) ON DELETE CASCADE
);

-- ============================================
-- 宠物进化记录表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_pet_evolution_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pet_id VARCHAR(36) NOT NULL,
    from_type VARCHAR(32) NOT NULL,
    to_type VARCHAR(32) NOT NULL,
    evolved_at BIGINT NOT NULL,
    INDEX idx_pet (pet_id),
    FOREIGN KEY (pet_id) REFERENCES starcore_pets(pet_id) ON DELETE CASCADE
);

-- ============================================
-- 坐骑表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_mounts (
    mount_id VARCHAR(36) PRIMARY KEY,
    owner_uuid VARCHAR(36) NOT NULL,
    mount_type VARCHAR(32) NOT NULL,
    mount_name VARCHAR(64),
    tier VARCHAR(16) DEFAULT 'COMMON',
    speed_bonus DOUBLE DEFAULT 0.0,
    jump_bonus DOUBLE DEFAULT 0.0,
    stamina INT DEFAULT 100,
    endurance INT DEFAULT 100,
    is_active BOOLEAN DEFAULT FALSE,
    appearance TEXT,
    stats TEXT,
    created_at BIGINT NOT NULL,
    last_ridden BIGINT,
    INDEX idx_owner (owner_uuid),
    INDEX idx_mount_type (mount_type)
);

-- ============================================
-- 坐骑装备表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_mount_equipment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mount_id VARCHAR(36) NOT NULL,
    slot VARCHAR(32) NOT NULL,
    item_id VARCHAR(64) NOT NULL,
    enchantments TEXT,
    UNIQUE KEY uk_mount_slot (mount_id, slot),
    INDEX idx_mount (mount_id),
    FOREIGN KEY (mount_id) REFERENCES starcore_mounts(mount_id) ON DELETE CASCADE
);

-- ============================================
-- 宠物/坐骑皮肤表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_pet_skins (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_uuid VARCHAR(36) NOT NULL,
    pet_id VARCHAR(36),
    skin_id VARCHAR(64) NOT NULL,
    skin_data TEXT NOT NULL,
    is_active BOOLEAN DEFAULT FALSE,
    unlocked_at BIGINT NOT NULL,
    INDEX idx_owner (owner_uuid),
    INDEX idx_pet (pet_id)
);

-- ============================================
-- 迁移元数据表扩展
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_migration_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    migration_version VARCHAR(16) NOT NULL,
    migration_name VARCHAR(255) NOT NULL,
    migration_type VARCHAR(32) NOT NULL,
    executed_at BIGINT NOT NULL,
    execution_time_ms BIGINT NOT NULL,
    success BOOLEAN NOT NULL,
    checksum VARCHAR(64),
    error_message TEXT,
    rollback_sql TEXT,
    executed_by VARCHAR(64),
    UNIQUE KEY uk_version (migration_version),
    INDEX idx_executed_at (executed_at),
    INDEX idx_success (success)
);

-- ============================================
-- 备份记录表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_backup_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    backup_id VARCHAR(36) NOT NULL UNIQUE,
    backup_type VARCHAR(32) NOT NULL,
    target_version VARCHAR(16),
    file_path VARCHAR(512),
    file_size BIGINT,
    created_at BIGINT NOT NULL,
    expires_at BIGINT,
    status VARCHAR(32) NOT NULL,
    description TEXT,
    INDEX idx_backup_type (backup_type),
    INDEX idx_created_at (created_at),
    INDEX idx_status (status)
);
