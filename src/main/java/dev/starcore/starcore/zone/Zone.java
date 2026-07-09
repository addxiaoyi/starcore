package dev.starcore.starcore.zone;

import dev.starcore.starcore.module.nation.model.NationId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 经济区模型
 * 代表一个经济区，拥有税收加成、产出计算和特效
 */
public final class Zone {
    private final UUID id;
    private final NationId nationId;
    private String name;
    private ZoneType type;
    private double taxBonus;          // 税收加成百分比 (如 0.2 = 20%)
    private double productionBonus;    // 产出加成百分比
    private List<ZoneEffect> effects;  // 特效列表
    private int level;                 // 等级
    private long createdAt;
    private boolean active;

    public Zone(UUID id, NationId nationId, String name, ZoneType type) {
        this.id = Objects.requireNonNull(id, "id");
        this.nationId = Objects.requireNonNull(nationId, "nationId");
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.taxBonus = 0.0;
        this.productionBonus = 0.0;
        this.effects = new ArrayList<>();
        this.level = 1;
        this.createdAt = System.currentTimeMillis();
        this.active = true;
    }

    // ==================== 基础属性 ====================

    public UUID id() { return id; }
    public NationId nationId() { return nationId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ZoneType getType() { return type; }
    public void setType(ZoneType type) { this.type = type; }

    public double getTaxBonus() { return taxBonus; }
    public void setTaxBonus(double taxBonus) { this.taxBonus = Math.max(0, taxBonus); }

    public double getProductionBonus() { return productionBonus; }
    public void setProductionBonus(double productionBonus) { this.productionBonus = Math.max(0, productionBonus); }

    public List<ZoneEffect> getEffects() { return effects; }
    public void setEffects(List<ZoneEffect> effects) { this.effects = effects; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = Math.max(1, level); }

    public long getCreatedAt() { return createdAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    // ==================== 特效管理 ====================

    public void addEffect(ZoneEffect effect) {
        if (effect != null && !effects.contains(effect)) {
            effects.add(effect);
        }
    }

    public void removeEffect(ZoneEffect effect) {
        effects.remove(effect);
    }

    public void clearEffects() {
        effects.clear();
    }

    // ==================== 计算方法 ====================

    /**
     * 计算税收加成后的税额
     */
    public double calculateTax(double baseTax) {
        double bonus = 1.0 + taxBonus;
        return baseTax * bonus;
    }

    /**
     * 计算产出加成后的产量
     */
    public double calculateProduction(double baseProduction) {
        double bonus = 1.0 + productionBonus;
        // 等级加成
        double levelMultiplier = 1.0 + (level - 1) * 0.1;
        return baseProduction * bonus * levelMultiplier;
    }

    /**
     * 获取特效提供的总加成
     */
    public double getTotalEffectBonus() {
        return effects.stream()
            .mapToDouble(ZoneEffect::getBonus)
            .sum();
    }

    // ==================== 升级 ====================

    public boolean canUpgrade() {
        return level < type.getMaxLevel();
    }

    public void upgrade() {
        if (canUpgrade()) {
            level++;
            // 升级时增加加成
            taxBonus += type.getTaxBonusPerLevel();
            productionBonus += type.getProductionBonusPerLevel();
        }
    }

    // ==================== 快照 ====================

    public ZoneSnapshot snapshot() {
        return new ZoneSnapshot(
            id,
            nationId,
            name,
            type,
            taxBonus,
            productionBonus,
            new ArrayList<>(effects),
            level,
            createdAt,
            active
        );
    }

    @Override
    public String toString() {
        return String.format("Zone[id=%s, name=%s, type=%s, level=%d, taxBonus=%.1f%%, productionBonus=%.1f%%]",
            id, name, type, level, taxBonus * 100, productionBonus * 100);
    }
}
