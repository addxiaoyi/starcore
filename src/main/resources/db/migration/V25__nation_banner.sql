-- =====================================================
-- Nation Banner Module Database Migration
-- Feature ID: 15
-- Version: V13__nation_banner.sql
-- =====================================================

-- Nation Banners table
CREATE TABLE IF NOT EXISTS nation_banners (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nation_id VARCHAR(36) NOT NULL UNIQUE,
    pattern VARCHAR(50) NOT NULL DEFAULT 'plain',
    base_color VARCHAR(20) NOT NULL DEFAULT 'WHITE',
    pattern_color VARCHAR(20) NOT NULL DEFAULT 'BLACK',
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    created_by VARCHAR(36),
    updated_by VARCHAR(36),
    is_custom BOOLEAN NOT NULL DEFAULT FALSE,
    metadata TEXT,
    version INT NOT NULL DEFAULT 1,
    INDEX idx_nation_id (nation_id),
    INDEX idx_pattern (pattern),
    INDEX idx_created_at (created_at),
    INDEX idx_is_custom (is_custom)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Banner Design History table (for audit and undo)
CREATE TABLE IF NOT EXISTS nation_banner_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nation_id VARCHAR(36) NOT NULL,
    previous_pattern VARCHAR(50),
    previous_base_color VARCHAR(20),
    previous_pattern_color VARCHAR(20),
    new_pattern VARCHAR(50) NOT NULL,
    new_base_color VARCHAR(20) NOT NULL,
    new_pattern_color VARCHAR(20) NOT NULL,
    changed_by VARCHAR(36),
    change_type VARCHAR(20) NOT NULL,
    change_reason TEXT,
    changed_at BIGINT NOT NULL,
    INDEX idx_nation_id (nation_id),
    INDEX idx_changed_at (changed_at),
    INDEX idx_change_type (change_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Banner Templates table (predefined designs)
CREATE TABLE IF NOT EXISTS nation_banner_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_name VARCHAR(100) NOT NULL UNIQUE,
    pattern VARCHAR(50) NOT NULL,
    base_color VARCHAR(20) NOT NULL,
    pattern_color VARCHAR(20) NOT NULL,
    description TEXT,
    category VARCHAR(50),
    popularity INT NOT NULL DEFAULT 0,
    is_featured BOOLEAN NOT NULL DEFAULT FALSE,
    created_at BIGINT NOT NULL,
    INDEX idx_category (category),
    INDEX idx_popularity (popularity DESC),
    INDEX idx_featured (is_featured)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert some default banner templates
INSERT INTO nation_banner_templates (template_name, pattern, base_color, pattern_color, description, category, popularity, is_featured, created_at) VALUES
('classic', 'cross', 'RED', 'WHITE', '经典红十字旗帜', 'classic', 100, TRUE, UNIX_TIMESTAMP() * 1000),
('royal', 'border', 'PURPLE', 'GOLD', '皇家紫金旗帜', 'royal', 80, TRUE, UNIX_TIMESTAMP() * 1000),
('noble', 'stripe_v', 'BLUE', 'SILVER', '贵族蓝银条纹', 'noble', 60, FALSE, UNIX_TIMESTAMP() * 1000),
('warrior', 'cross', 'BLACK', 'RED', '战士红黑十字', 'military', 90, TRUE, UNIX_TIMESTAMP() * 1000),
('nature', 'flower', 'GREEN', 'WHITE', '自然绿白花纹', 'nature', 70, FALSE, UNIX_TIMESTAMP() * 1000),
('ocean', 'diagonal', 'BLUE', 'WHITE', '海洋蓝白斜纹', 'nature', 65, FALSE, UNIX_TIMESTAMP() * 1000),
('sunset', 'gradient', 'ORANGE', 'PURPLE', '夕阳渐变旗帜', 'artistic', 55, FALSE, UNIX_TIMESTAMP() * 1000),
('diamond', 'diamond', 'WHITE', 'BLACK', '黑白菱形旗帜', 'geometric', 75, TRUE, UNIX_TIMESTAMP() * 1000),
('skull', 'skull', 'BLACK', 'WHITE', '骷髅海盗旗', 'military', 85, TRUE, UNIX_TIMESTAMP() * 1000),
('globe', 'globe', 'BLUE', 'GOLD', '环球金色旗帜', 'international', 50, FALSE, UNIX_TIMESTAMP() * 1000)
ON DUPLICATE KEY UPDATE popularity = popularity;

-- =====================================================
-- Rollback Guide:
-- DROP TABLE IF EXISTS nation_banner_templates;
-- DROP TABLE IF EXISTS nation_banner_history;
-- DROP TABLE IF EXISTS nation_banners;
-- =====================================================
