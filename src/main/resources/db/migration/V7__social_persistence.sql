-- StarCore V7: 社交系统持久化表
-- Version: 7
-- Description: 添加好友、公会、派对系统的数据库持久化表结构

-- ============================================
-- 好友关系表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_friend_relations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    friend_uuid VARCHAR(36) NOT NULL,
    created_at BIGINT NOT NULL,
    UNIQUE KEY uk_friend (player_uuid, friend_uuid),
    INDEX idx_player (player_uuid),
    INDEX idx_friend (friend_uuid)
);

-- ============================================
-- 好友请求表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_friend_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_uuid VARCHAR(36) NOT NULL,
    sender_uuid VARCHAR(36) NOT NULL,
    created_at BIGINT NOT NULL,
    UNIQUE KEY uk_request (target_uuid, sender_uuid),
    INDEX idx_target (target_uuid),
    INDEX idx_sender (sender_uuid)
);

-- ============================================
-- 黑名单表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_blacklist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    blocked_uuid VARCHAR(36) NOT NULL,
    created_at BIGINT NOT NULL,
    UNIQUE KEY uk_blacklist (player_uuid, blocked_uuid),
    INDEX idx_player (player_uuid)
);

-- ============================================
-- 公会表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_guilds (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    tag VARCHAR(8) NOT NULL,
    leader_uuid VARCHAR(36) NOT NULL,
    level INT DEFAULT 1,
    experience INT DEFAULT 0,
    created_at BIGINT NOT NULL,
    description TEXT,
    UNIQUE KEY uk_name (name),
    INDEX idx_leader (leader_uuid)
);

-- ============================================
-- 公会成员表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_guild_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    guild_id VARCHAR(36) NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'MEMBER',
    joined_at BIGINT NOT NULL,
    UNIQUE KEY uk_membership (guild_id, player_uuid),
    INDEX idx_guild (guild_id),
    INDEX idx_player (player_uuid),
    FOREIGN KEY (guild_id) REFERENCES starcore_guilds(id) ON DELETE CASCADE
);

-- ============================================
-- 公会邀请表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_guild_invites (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    guild_id VARCHAR(36) NOT NULL,
    inviter_uuid VARCHAR(36) NOT NULL,
    target_uuid VARCHAR(36) NOT NULL,
    created_at BIGINT NOT NULL,
    UNIQUE KEY uk_invite (guild_id, target_uuid),
    INDEX idx_target (target_uuid),
    FOREIGN KEY (guild_id) REFERENCES starcore_guilds(id) ON DELETE CASCADE
);

-- ============================================
-- 派对表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_parties (
    id VARCHAR(36) PRIMARY KEY,
    leader_uuid VARCHAR(36) NOT NULL,
    created_at BIGINT NOT NULL,
    friendly_fire BOOLEAN DEFAULT FALSE,
    exp_share BOOLEAN DEFAULT TRUE,
    max_members INT DEFAULT 10,
    INDEX idx_leader (leader_uuid)
);

-- ============================================
-- 派对成员表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_party_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    party_id VARCHAR(36) NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    joined_at BIGINT NOT NULL,
    UNIQUE KEY uk_membership (party_id, player_uuid),
    INDEX idx_party (party_id),
    INDEX idx_player (player_uuid),
    FOREIGN KEY (party_id) REFERENCES starcore_parties(id) ON DELETE CASCADE
);

-- ============================================
-- 派对邀请表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_party_invites (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inviter_uuid VARCHAR(36) NOT NULL,
    target_uuid VARCHAR(36) NOT NULL,
    created_at BIGINT NOT NULL,
    UNIQUE KEY uk_invite (inviter_uuid, target_uuid),
    INDEX idx_target (target_uuid)
);

-- ============================================
-- 玩家在线状态表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_player_status (
    player_uuid VARCHAR(36) PRIMARY KEY,
    is_online BOOLEAN DEFAULT FALSE,
    last_seen BIGINT NOT NULL,
    last_server VARCHAR(64)
);
