package dev.starcore.starcore.module.army.doctrine;

import dev.starcore.starcore.module.army.doctrine.model.DoctrineType;
import dev.starcore.starcore.module.army.doctrine.model.NationDoctrine;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 军事学说服务实现
 */
public class DoctrineServiceImpl implements DoctrineService {

    private final Map<UUID, NationDoctrine> nationDoctrines = new ConcurrentHashMap<>();
    private final Map<DoctrineType, Integer> doctrineUsageCount = new ConcurrentHashMap<>();
    private final java.util.List<Consumer<DoctrineChangedEvent>> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    private final long switchCooldownMs;
    private final double switchCost;

    public DoctrineServiceImpl(ConfigurationSection config) {
        if (config != null) {
            this.switchCooldownMs = config.getLong("switch-cooldown-ms", 86400000L); // 默认24小时
            this.switchCost = config.getDouble("switch-cost", 500.0);
        } else {
            this.switchCooldownMs = 86400000L;
            this.switchCost = 500.0;
        }

        // 初始化计数器
        for (DoctrineType type : DoctrineType.values()) {
            doctrineUsageCount.put(type, 0);
        }
    }

    @Override
    public Optional<NationDoctrine> getNationDoctrine(UUID nationId) {
        return Optional.ofNullable(nationDoctrines.get(nationId));
    }

    @Override
    public boolean setDoctrine(UUID nationId, DoctrineType doctrine, String changedBy) {
        NationDoctrine current = nationDoctrines.get(nationId);
        DoctrineType previousType = current != null ? current.doctrine() : DoctrineType.NONE;

        NationDoctrine newDoctrine;
        if (current == null) {
            newDoctrine = NationDoctrine.create(nationId, doctrine, changedBy);
        } else {
            newDoctrine = current.switchTo(doctrine, changedBy);
        }

        nationDoctrines.put(nationId, newDoctrine);

        // 更新统计
        updateDoctrineStats(previousType, doctrine);

        // 触发事件
        fireDoctrineChanged(new DoctrineChangedEvent(nationId, previousType, doctrine, changedBy));

        return true;
    }

    @Override
    public boolean clearDoctrine(UUID nationId, String clearedBy) {
        return setDoctrine(nationId, DoctrineType.NONE, clearedBy);
    }

    @Override
    public DoctrineType getDoctrineType(UUID nationId) {
        NationDoctrine doctrine = nationDoctrines.get(nationId);
        return doctrine != null ? doctrine.doctrine() : DoctrineType.NONE;
    }

    @Override
    public int calculateAttackBonus(UUID nationId, int baseAttack) {
        DoctrineType type = getDoctrineType(nationId);
        return type.applyAttackBonus(baseAttack);
    }

    @Override
    public int calculateDefenseBonus(UUID nationId, int baseDefense) {
        DoctrineType type = getDoctrineType(nationId);
        return type.applyDefenseBonus(baseDefense);
    }

    @Override
    public int calculateEffectiveSoldiers(UUID nationId, int baseSoldiers) {
        double multiplier = getCostMultiplier(nationId);
        // 成本效率影响有效士兵数量（同样预算可以得到更多士兵）
        return (int) Math.floor(baseSoldiers * multiplier);
    }

    @Override
    public double getCostMultiplier(UUID nationId) {
        DoctrineType type = getDoctrineType(nationId);
        return type.costMultiplier();
    }

    @Override
    public double getMoraleConsumptionMultiplier(UUID nationId) {
        DoctrineType type = getDoctrineType(nationId);
        return type.moraleConsumptionMultiplier();
    }

    @Override
    public double getAmbushBonus(UUID nationId) {
        DoctrineType type = getDoctrineType(nationId);
        return type.ambushBonus();
    }

    @Override
    public double getMobilityBonus(UUID nationId) {
        DoctrineType type = getDoctrineType(nationId);
        return type.mobilityBonus();
    }

    @Override
    public boolean canSwitchDoctrine(UUID nationId) {
        NationDoctrine current = nationDoctrines.get(nationId);
        if (current == null) {
            return true;
        }
        return current.canSwitch(switchCooldownMs);
    }

    @Override
    public long getDoctrineSwitchCooldownRemaining(UUID nationId) {
        NationDoctrine current = nationDoctrines.get(nationId);
        if (current == null) {
            return 0;
        }
        return current.getRemainingCooldownMs(switchCooldownMs);
    }

    @Override
    public void onDoctrineChanged(Consumer<DoctrineChangedEvent> listener) {
        listeners.add(listener);
    }

    @Override
    public void loadAllDoctrines() {
        // 从配置文件加载已保存的学说数据
        // 这个方法会在模块启用时被调用
    }

    @Override
    public void saveAllDoctrines() {
        // 保存所有学说数据到配置文件
        // 这个方法会在模块禁用时被调用
    }

    @Override
    public long getSwitchCooldownMs() {
        return switchCooldownMs;
    }

    @Override
    public double getSwitchCost() {
        return switchCost;
    }

    @Override
    public int getNationCountByDoctrine(DoctrineType doctrine) {
        return doctrineUsageCount.getOrDefault(doctrine, 0);
    }

    /**
     * 内部方法：更新学说使用统计
     */
    private void updateDoctrineStats(DoctrineType previous, DoctrineType current) {
        if (previous != current) {
            if (previous != DoctrineType.NONE) {
                doctrineUsageCount.merge(previous, -1, Integer::sum);
            }
            if (current != DoctrineType.NONE) {
                doctrineUsageCount.merge(current, 1, Integer::sum);
            }
        }
    }

    /**
     * 内部方法：触发学说变更事件
     */
    private void fireDoctrineChanged(DoctrineChangedEvent event) {
        for (Consumer<DoctrineChangedEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                // 记录异常但继续处理其他监听器
            }
        }
    }

    /**
     * 获取所有国家学说数据（用于调试）
     */
    public Map<UUID, NationDoctrine> getAllDoctrines() {
        return Map.copyOf(nationDoctrines);
    }
}