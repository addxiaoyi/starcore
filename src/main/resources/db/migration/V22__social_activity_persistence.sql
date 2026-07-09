-- StarCore V22: Social Activity Persistence
-- Version: 22
-- Description: 为社交活动系统创建数据库持久化表

-- ============================================
-- 社交活动表
-- ============================================

CREATE TABLE IF NOT EXISTS social_activities (
    id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    type VARCHAR(50) NOT NULL,
    host VARCHAR(36) NOT NULL,
    start_time BIGINT NOT NULL,
    duration BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    max_participants INT NOT NULL,
    metadata TEXT
);

CREATE INDEX IF NOT EXISTS idx_social_activities_host ON social_activities(host);
CREATE INDEX IF NOT EXISTS idx_social_activities_status ON social_activities(status);

-- ============================================
-- 社交活动参与者表
-- ============================================

CREATE TABLE IF NOT EXISTS social_activity_participants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id VARCHAR(100) NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    joined_at BIGINT NOT NULL,
    UNIQUE KEY unique_participant (activity_id, player_uuid)
);

CREATE INDEX IF NOT EXISTS idx_activity_participants_activity ON social_activity_participants(activity_id);
CREATE INDEX IF NOT EXISTS idx_activity_participants_player ON social_activity_participants(player_uuid);

-- ============================================
-- 待领取活动奖励表
-- ============================================

CREATE TABLE IF NOT EXISTS activity_rewards (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    activity_id VARCHAR(100) NOT NULL,
    activity_type VARCHAR(50) NOT NULL,
    reputation_amount INT NOT NULL,
    emoji VARCHAR(10),
    earned_at BIGINT NOT NULL,
    claimed INT DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_activity_rewards_player ON activity_rewards(player_uuid);
CREATE INDEX IF NOT EXISTS idx_activity_rewards_unclaimed ON activity_rewards(player_uuid, claimed);

-- ============================================
-- 八卦声誉影响表
-- ============================================

CREATE TABLE IF NOT EXISTS gossip_reputation_impacts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    gossip_id VARCHAR(100) NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    impact_amount INT NOT NULL,
    dimension VARCHAR(20) NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_gossip_impacts_player ON gossip_reputation_impacts(player_uuid);
CREATE INDEX IF NOT EXISTS idx_gossip_impacts_gossip ON gossip_reputation_impacts(gossip_id);
