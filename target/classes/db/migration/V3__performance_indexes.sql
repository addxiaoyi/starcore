-- StarCore Performance Optimization
-- Version: 3
-- Description: 添加性能优化索引

-- ============================================
-- 国家状态表索引优化
-- ============================================
-- 加速按 nation_id 前缀的查询
CREATE INDEX IF NOT EXISTS idx_nation_keys
    ON starcore_nation_state(property_key)
    WHERE property_key LIKE 'nation.%';

-- ============================================
-- 外交关系表索引优化
-- ============================================
CREATE INDEX IF NOT EXISTS idx_diplomacy_keys
    ON starcore_diplomacy_state(property_key)
    WHERE property_key LIKE 'relation.%';

-- ============================================
-- 经济事务日志索引优化
-- ============================================
-- 优化按时间范围查询
CREATE INDEX IF NOT EXISTS idx_transactions_timestamp_desc
    ON starcore_economy_transactions(timestamp DESC);

-- 优化按玩家和类型查询
CREATE INDEX IF NOT EXISTS idx_transactions_player_type
    ON starcore_economy_transactions(player_uuid, transaction_type, timestamp);

-- ============================================
-- 地图区块索引优化
-- ============================================
-- 加速按世界和区域查询
CREATE INDEX IF NOT EXISTS idx_map_chunks_region
    ON starcore_map_chunks(world_name, chunk_x, chunk_z);

-- 加速按更新时间清理旧数据
CREATE INDEX IF NOT EXISTS idx_map_chunks_updated
    ON starcore_map_chunks(last_updated);

-- ============================================
-- 地图标记索引优化
-- ============================================
-- 加速按类型查询
CREATE INDEX IF NOT EXISTS idx_markers_type
    ON starcore_map_markers(marker_type, world_name);

-- 加速按位置范围查询
CREATE INDEX IF NOT EXISTS idx_markers_location
    ON starcore_map_markers(world_name, x, z);
