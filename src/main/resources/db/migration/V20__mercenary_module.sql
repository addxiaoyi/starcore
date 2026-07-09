-- V20: Mercenary Module
-- 雇佣兵系统数据库表

-- 雇佣兵合同表
CREATE TABLE IF NOT EXISTS mercenary_contracts (
    contract_id TEXT PRIMARY KEY,
    mercenary_id TEXT NOT NULL,
    employer_id TEXT NOT NULL,
    nation_id TEXT NOT NULL,
    type TEXT NOT NULL,
    rank TEXT NOT NULL,
    experience INTEGER DEFAULT 0,
    kills INTEGER DEFAULT 0,
    deaths INTEGER DEFAULT 0,
    missions_completed INTEGER DEFAULT 0,
    salary INTEGER DEFAULT 0,
    hired_at INTEGER NOT NULL,
    expires_at INTEGER,
    status TEXT NOT NULL,
    last_location TEXT,
    last_active INTEGER NOT NULL,
    created_at INTEGER DEFAULT (strftime('%s', 'now')),
    updated_at INTEGER DEFAULT (strftime('%s', 'now'))
);

-- 雇佣兵设置表
CREATE TABLE IF NOT EXISTS mercenary_settings (
    player_id TEXT PRIMARY KEY,
    available INTEGER DEFAULT 0,
    min_contract_days INTEGER DEFAULT 1,
    max_contract_days INTEGER DEFAULT 30,
    min_salary_per_day INTEGER DEFAULT 100,
    preferred_types TEXT,
    created_at INTEGER DEFAULT (strftime('%s', 'now')),
    updated_at INTEGER DEFAULT (strftime('%s', 'now'))
);

-- 雇佣兵雇佣历史表
CREATE TABLE IF NOT EXISTS mercenary_history (
    history_id INTEGER PRIMARY KEY AUTOINCREMENT,
    contract_id TEXT NOT NULL,
    mercenary_id TEXT NOT NULL,
    employer_id TEXT NOT NULL,
    nation_id TEXT NOT NULL,
    type TEXT NOT NULL,
    rank TEXT NOT NULL,
    total_salary INTEGER DEFAULT 0,
    total_kills INTEGER DEFAULT 0,
    total_deaths INTEGER DEFAULT 0,
    missions_completed INTEGER DEFAULT 0,
    end_reason TEXT,
    started_at INTEGER NOT NULL,
    ended_at INTEGER,
    created_at INTEGER DEFAULT (strftime('%s', 'now'))
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_mercenary_contracts_mercenary_id ON mercenary_contracts(mercenary_id);
CREATE INDEX IF NOT EXISTS idx_mercenary_contracts_employer_id ON mercenary_contracts(employer_id);
CREATE INDEX IF NOT EXISTS idx_mercenary_contracts_nation_id ON mercenary_contracts(nation_id);
CREATE INDEX IF NOT EXISTS idx_mercenary_contracts_status ON mercenary_contracts(status);
CREATE INDEX IF NOT EXISTS idx_mercenary_history_mercenary_id ON mercenary_history(mercenary_id);
CREATE INDEX IF NOT EXISTS idx_mercenary_history_nation_id ON mercenary_history(nation_id);
