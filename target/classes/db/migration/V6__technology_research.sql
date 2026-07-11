-- StarCore V6: 科技研究进度持久化表
-- Version: 6
-- Description: 添加研究进度的持久化表结构，用于服务器重启后恢复正在进行的研究

-- ============================================
-- 研究进度主表
-- ============================================
CREATE TABLE IF NOT EXISTS starcore_technology_research (
    property_key VARCHAR(191) PRIMARY KEY,
    property_value TEXT NOT NULL
);

-- ============================================
-- 索引
-- ============================================
-- property_key 已经是主键，无需额外索引
-- 该表使用 key-value 模式，通过前缀匹配查询

-- ============================================
-- 数据保留策略注释
-- ============================================
-- 该表存储 Properties 格式的研究状态数据
-- 格式示例:
--   research.nationCount=2
--   research.nation.0.nationId=uuid-1
--   research.nation.0.researchCount=1
--   research.nation.0.research.0.key=advanced_mining
--   research.nation.0.research.0.start=1735689600000
--   research.nation.0.research.0.estimated=1735693200000
--   research.nation.0.research.0.total=36000
--   research.nation.0.research.0.remaining=18000
