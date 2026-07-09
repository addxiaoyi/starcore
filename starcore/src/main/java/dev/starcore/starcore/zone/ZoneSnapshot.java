package dev.starcore.starcore.zone;

import dev.starcore.starcore.module.nation.model.NationId;

import java.util.List;
import java.util.UUID;

/**
 * 经济区快照（用于存储和传输）
 */
public record ZoneSnapshot(
    UUID id,
    NationId nationId,
    String name,
    ZoneType type,
    double taxBonus,
    double productionBonus,
    List<ZoneEffect> effects,
    int level,
    long createdAt,
    boolean active
) {
    public ZoneSnapshot {
        if (effects == null) effects = List.of();
    }

    /**
     * 转换为完整Zone对象
     */
    public Zone toZone() {
        Zone zone = new Zone(id, nationId, name, type);
        zone.setTaxBonus(taxBonus);
        zone.setProductionBonus(productionBonus);
        zone.setEffects(effects);
        zone.setLevel(level);
        zone.setActive(active);
        return zone;
    }

    /**
     * 获取格式化税收加成
     */
    public String getFormattedTaxBonus() {
        return String.format("%.1f%%", taxBonus * 100);
    }

    /**
     * 获取格式化产出加成
     */
    public String getFormattedProductionBonus() {
        return String.format("%.1f%%", productionBonus * 100);
    }

    /**
     * 获取总加成
     */
    public double getTotalBonus() {
        return taxBonus + productionBonus;
    }
}
