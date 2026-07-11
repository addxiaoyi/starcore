-- =============================================
-- 军事联盟系统数据库迁移
-- Feature ID: 27
-- Package: dev.starcore.starcore.module.diplomacy.military
-- =============================================

-- 军事联盟主表
CREATE TABLE IF NOT EXISTS military_alliances (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nation1_id VARCHAR(36) NOT NULL,
    nation2_id VARCHAR(36) NOT NULL,
    nation1_name VARCHAR(255) NOT NULL,
    nation2_name VARCHAR(255) NOT NULL,
    pact_type VARCHAR(50) NOT NULL DEFAULT 'OBSERVER',
    formed_at BIGINT NOT NULL,
    upgraded_at BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(nation1_id, nation2_id)
);

-- 军事联盟邀请表
CREATE TABLE IF NOT EXISTS military_alliance_invites (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    inviter_id VARCHAR(36) NOT NULL,
    invited_id VARCHAR(36) NOT NULL,
    pact_type VARCHAR(50) NOT NULL DEFAULT 'OBSERVER',
    invited_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(inviter_id, invited_id)
);

-- 军事联盟冷却时间表
CREATE TABLE IF NOT EXISTS military_alliance_cooldowns (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nation1_id VARCHAR(36) NOT NULL,
    nation2_id VARCHAR(36) NOT NULL,
    cooldown_until BIGINT NOT NULL,
    reason VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(nation1_id, nation2_id)
);

-- 军事联盟事件日志表
CREATE TABLE IF NOT EXISTS military_alliance_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    event_type VARCHAR(50) NOT NULL,
    nation1_id VARCHAR(36),
    nation2_id VARCHAR(36),
    pact_type VARCHAR(50),
    performed_by VARCHAR(36),
    details TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_military_alliances_nation1 ON military_alliances(nation1_id);
CREATE INDEX IF NOT EXISTS idx_military_alliances_nation2 ON military_alliances(nation2_id);
CREATE INDEX IF NOT EXISTS idx_military_alliances_pact_type ON military_alliances(pact_type);

CREATE INDEX IF NOT EXISTS idx_military_invites_inviter ON military_alliance_invites(inviter_id);
CREATE INDEX IF NOT EXISTS idx_military_invites_invited ON military_alliance_invites(invited_id);
CREATE INDEX IF NOT EXISTS idx_military_invites_status ON military_alliance_invites(status);
CREATE INDEX IF NOT EXISTS idx_military_invites_expires ON military_alliance_invites(expires_at);

CREATE INDEX IF NOT EXISTS idx_military_cooldowns_nation1 ON military_alliance_cooldowns(nation1_id);
CREATE INDEX IF NOT EXISTS idx_military_cooldowns_nation2 ON military_alliance_cooldowns(nation2_id);
CREATE INDEX IF NOT EXISTS idx_military_cooldowns_until ON military_alliance_cooldowns(cooldown_until);

CREATE INDEX IF NOT EXISTS idx_military_events_type ON military_alliance_events(event_type);
CREATE INDEX IF NOT EXISTS idx_military_events_nation1 ON military_alliance_events(nation1_id);
CREATE INDEX IF NOT EXISTS idx_military_events_nation2 ON military_alliance_events(nation2_id);
CREATE INDEX IF NOT EXISTS idx_military_events_created ON military_alliance_events(created_at);

-- 事件触发器用于自动更新 updated_at
CREATE TRIGGER IF NOT EXISTS update_military_alliance_timestamp
AFTER UPDATE ON military_alliances
BEGIN
    UPDATE military_alliances SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;
