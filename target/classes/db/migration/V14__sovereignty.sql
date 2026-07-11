-- Sovereignty Module Database Migration
-- V14__sovereignty.sql
-- Creates tables for sovereignty declaration system

-- 主权声明表
CREATE TABLE IF NOT EXISTS sovereignty_declarations (
    id VARCHAR(36) PRIMARY KEY,
    nation_id VARCHAR(36) NOT NULL,
    region_name VARCHAR(255) NOT NULL,
    description TEXT,
    significance VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    strength INT DEFAULT 0,
    declared_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE(region_name)
);

-- 主权领土声明表
CREATE TABLE IF NOT EXISTS sovereignty_claims (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    sovereignty_id VARCHAR(36) NOT NULL,
    world VARCHAR(255) NOT NULL,
    chunk_x INT NOT NULL,
    chunk_z INT NOT NULL,
    FOREIGN KEY (sovereignty_id) REFERENCES sovereignty_declarations(id) ON DELETE CASCADE
);

-- 创建索引以提高查询性能
CREATE INDEX IF NOT EXISTS idx_sovereignty_nation ON sovereignty_declarations(nation_id);
CREATE INDEX IF NOT EXISTS idx_sovereignty_status ON sovereignty_declarations(status);
CREATE INDEX IF NOT EXISTS idx_sovereignty_claims_sovereignty ON sovereignty_claims(sovereignty_id);
CREATE INDEX IF NOT EXISTS idx_sovereignty_claims_location ON sovereignty_claims(world, chunk_x, chunk_z);
