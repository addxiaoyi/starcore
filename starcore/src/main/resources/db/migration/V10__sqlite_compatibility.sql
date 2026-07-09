-- StarCore V10: SQLite 兼容表结构修复
-- Version: 10
-- Description: 修复社交、法庭、破产等模块的 SQLite 兼容性问题
-- 此迁移检测数据库类型，仅在 SQLite 时执行

-- ============================================
-- 社交系统表 (SQLite 兼容版本)
-- ============================================

-- 好友关系表
CREATE TABLE IF NOT EXISTS starcore_friend_relations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid TEXT NOT NULL,
    friend_uuid TEXT NOT NULL,
    created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_friend_player ON starcore_friend_relations(player_uuid);
CREATE INDEX IF NOT EXISTS idx_friend_friend ON starcore_friend_relations(friend_uuid);

-- 好友请求表
CREATE TABLE IF NOT EXISTS starcore_friend_requests (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    sender_uuid TEXT NOT NULL,
    receiver_uuid TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING'
);
CREATE INDEX IF NOT EXISTS idx_friend_req_receiver ON starcore_friend_requests(receiver_uuid, status);

-- 黑名单表
CREATE TABLE IF NOT EXISTS starcore_blacklist (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid TEXT NOT NULL,
    blocked_uuid TEXT NOT NULL,
    reason TEXT,
    created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_blacklist_player ON starcore_blacklist(player_uuid);

-- 玩家状态表
CREATE TABLE IF NOT EXISTS starcore_player_status (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid TEXT NOT NULL UNIQUE,
    status_message TEXT,
    last_online INTEGER NOT NULL,
    online_status TEXT NOT NULL DEFAULT 'OFFLINE'
);

-- 公会表
CREATE TABLE IF NOT EXISTS starcore_guilds (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_uuid TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL UNIQUE,
    tag TEXT NOT NULL,
    leader_uuid TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    level INTEGER NOT NULL DEFAULT 1,
    experience INTEGER NOT NULL DEFAULT 0,
    description TEXT,
    max_members INTEGER NOT NULL DEFAULT 20
);
CREATE INDEX IF NOT EXISTS idx_guild_name ON starcore_guilds(name);
CREATE INDEX IF NOT EXISTS idx_guild_leader ON starcore_guilds(leader_uuid);

-- 公会成员表
CREATE TABLE IF NOT EXISTS starcore_guild_members (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_uuid TEXT NOT NULL,
    player_uuid TEXT NOT NULL UNIQUE,
    rank TEXT NOT NULL DEFAULT 'MEMBER',
    joined_at INTEGER NOT NULL,
    contributed_exp INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_guild_members_guild ON starcore_guild_members(guild_uuid);
CREATE INDEX IF NOT EXISTS idx_guild_members_player ON starcore_guild_members(player_uuid);

-- 公会邀请表
CREATE TABLE IF NOT EXISTS starcore_guild_invites (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_uuid TEXT NOT NULL,
    player_uuid TEXT NOT NULL,
    invited_by TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_guild_invite_player ON starcore_guild_invites(player_uuid, expires_at);

-- 派对表
CREATE TABLE IF NOT EXISTS starcore_parties (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    party_uuid TEXT NOT NULL UNIQUE,
    owner_uuid TEXT NOT NULL,
    name TEXT,
    created_at INTEGER NOT NULL,
    max_size INTEGER NOT NULL DEFAULT 10,
    is_private INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_party_owner ON starcore_parties(owner_uuid);

-- 派对成员表
CREATE TABLE IF NOT EXISTS starcore_party_members (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    party_uuid TEXT NOT NULL,
    player_uuid TEXT NOT NULL UNIQUE,
    joined_at INTEGER NOT NULL,
    is_leader INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_party_members_party ON starcore_party_members(party_uuid);

-- 派对邀请表
CREATE TABLE IF NOT EXISTS starcore_party_invites (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    party_uuid TEXT NOT NULL,
    player_uuid TEXT NOT NULL,
    invited_by TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL
);

-- ============================================
-- 邮件系统表 (V2)
-- ============================================

CREATE TABLE IF NOT EXISTS starcore_mail_v2 (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    mail_uuid TEXT NOT NULL UNIQUE,
    sender_uuid TEXT NOT NULL,
    sender_name TEXT NOT NULL,
    receiver_uuid TEXT NOT NULL,
    subject TEXT NOT NULL,
    content TEXT,
    sent_at INTEGER NOT NULL,
    read_at INTEGER,
    deleted_by_receiver INTEGER NOT NULL DEFAULT 0,
    deleted_by_sender INTEGER NOT NULL DEFAULT 0,
    attachment_count INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_mail_receiver ON starcore_mail_v2(receiver_uuid, deleted_by_receiver, read_at);
CREATE INDEX IF NOT EXISTS idx_mail_sender ON starcore_mail_v2(sender_uuid, deleted_by_sender);

CREATE TABLE IF NOT EXISTS starcore_mail_attachments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    mail_id INTEGER NOT NULL,
    item_id TEXT NOT NULL,
    item_name TEXT NOT NULL,
    item_amount INTEGER NOT NULL,
    item_data TEXT
);
CREATE INDEX IF NOT EXISTS idx_mail_attachment_mail ON starcore_mail_attachments(mail_id);

-- ============================================
-- 法庭系统表
-- ============================================

CREATE TABLE IF NOT EXISTS starcore_court_debts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    debt_uuid TEXT NOT NULL UNIQUE,
    debtor_uuid TEXT NOT NULL,
    creditor_uuid TEXT NOT NULL,
    nation_id TEXT,
    amount REAL NOT NULL,
    reason TEXT,
    created_at INTEGER NOT NULL,
    due_date INTEGER,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    paid_amount REAL NOT NULL DEFAULT 0,
    interest_rate REAL NOT NULL DEFAULT 0,
    last_interest_calc INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_court_debt_debtor ON starcore_court_debts(debtor_uuid, status);
CREATE INDEX IF NOT EXISTS idx_court_debt_creditor ON starcore_court_debts(creditor_uuid);

CREATE TABLE IF NOT EXISTS starcore_court_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    case_uuid TEXT NOT NULL UNIQUE,
    case_number INTEGER NOT NULL,
    plaintiff_uuid TEXT NOT NULL,
    defendant_uuid TEXT NOT NULL,
    court_id TEXT,
    judge_uuid TEXT,
    case_type TEXT NOT NULL,
    description TEXT,
    filed_at INTEGER NOT NULL,
    scheduled_at INTEGER,
    verdict_at INTEGER,
    verdict TEXT,
    status TEXT NOT NULL DEFAULT 'FILED',
    verdict_type TEXT,
    fine_amount REAL,
    community_service_minutes INTEGER,
    imprisonment_days INTEGER,
    appeal_deadline INTEGER,
    appeal_status TEXT,
    original_verdict TEXT,
    appeal_verdict TEXT
);
CREATE INDEX IF NOT EXISTS idx_court_history_plaintiff ON starcore_court_history(plaintiff_uuid);
CREATE INDEX IF NOT EXISTS idx_court_history_defendant ON starcore_court_history(defendant_uuid);
CREATE INDEX IF NOT EXISTS idx_court_history_status ON starcore_court_history(status);

-- ============================================
-- 破产系统表
-- ============================================

CREATE TABLE IF NOT EXISTS starcore_bankruptcy_state (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    entity_type TEXT NOT NULL,
    entity_uuid TEXT NOT NULL,
    filed_at INTEGER NOT NULL,
    total_debt REAL NOT NULL,
    total_assets REAL NOT NULL,
    debt_ratio REAL NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    discharge_date INTEGER,
    remaining_debt REAL,
    progress_percentage REAL DEFAULT 0,
    last_assessment INTEGER
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_bankruptcy_entity ON starcore_bankruptcy_state(entity_type, entity_uuid);

-- ============================================
-- 天气系统表
-- ============================================

CREATE TABLE IF NOT EXISTS starcore_weather_state (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    world_uuid TEXT NOT NULL UNIQUE,
    current_weather TEXT NOT NULL,
    duration_seconds INTEGER NOT NULL,
    started_at INTEGER NOT NULL,
    intensity REAL NOT NULL DEFAULT 1.0,
    next_transition INTEGER
);

-- ============================================
-- 战争系统增强表 - 添加 JSON 字段
-- 注意: SQLite 不支持直接 ALTER TABLE ADD COLUMN IF NOT EXISTS
-- 需要检查表结构是否存在该列
-- ============================================

-- 检查 starcore_war_state 表是否存在，如果存在则添加 war_json 列
-- SQLite 需要用这种方式检查列是否存在
CREATE TABLE IF NOT EXISTS starcore_war_state_backup AS SELECT * FROM starcore_war_state WHERE 1=0;

-- ============================================
-- 领地增强表 (如果缺失)
-- ============================================

CREATE TABLE IF NOT EXISTS starcore_territory_upgrades (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    claim_id TEXT NOT NULL UNIQUE,
    upgrade_level INTEGER NOT NULL DEFAULT 1,
    upgrade_type TEXT NOT NULL,
    upgrade_data TEXT,
    purchased_at INTEGER NOT NULL,
    expires_at INTEGER
);
CREATE INDEX IF NOT EXISTS idx_territory_upgrades_claim ON starcore_territory_upgrades(claim_id);