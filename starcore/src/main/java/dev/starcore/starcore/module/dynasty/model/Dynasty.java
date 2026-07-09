package dev.starcore.starcore.module.dynasty.model;
import java.util.Optional;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.*;

/**
 * 王朝数据模型
 * 代表一个国家的王朝状态
 */
public final class Dynasty {
    private final NationId nationId;
    private String dynastyName;
    private UUID currentMonarchId;
    private String currentMonarchName;
    private SuccessionType successionType;
    private final List<HeirInfo> successionOrder;
    private final Instant createdAt;
    private Instant monarchSince;
    private int reignCount;
    private Instant interregnumStart;
    private String successionTitle;

    public Dynasty(NationId nationId, UUID monarchId, String monarchName, String dynastyName) {
        this.nationId = Objects.requireNonNull(nationId, "nationId");
        this.dynastyName = dynastyName != null ? dynastyName : "New Dynasty";
        this.currentMonarchId = monarchId;
        this.currentMonarchName = monarchName;
        this.successionType = SuccessionType.MALE_PREMIogeniture;
        this.successionOrder = new ArrayList<>();
        this.createdAt = Instant.now();
        this.monarchSince = Instant.now();
        this.reignCount = 0;
        this.interregnumStart = null;
        this.successionTitle = "Monarch";
    }

    // Getters
    public NationId nationId() { return nationId; }
    public String dynastyName() { return dynastyName; }
    public UUID currentMonarchId() { return currentMonarchId; }
    public String currentMonarchName() { return currentMonarchName; }
    public SuccessionType successionType() { return successionType; }
    public List<HeirInfo> successionOrder() { return Collections.unmodifiableList(successionOrder); }
    public Instant createdAt() { return createdAt; }
    public Instant monarchSince() { return monarchSince; }
    public int reignCount() { return reignCount; }
    public Optional<Instant> interregnumStart() { return Optional.ofNullable(interregnumStart); }
    public String successionTitle() { return successionTitle; }

    public boolean hasMonarch() {
        return currentMonarchId != null;
    }

    public boolean isInInterregnum() {
        return currentMonarchId == null && interregnumStart != null;
    }

    /**
     * 检查玩家是否是当前君主
     */
    public boolean isMonarch(UUID playerId) {
        return currentMonarchId != null && currentMonarchId.equals(playerId);
    }

    /**
     * 检查玩家是否在继承顺序中
     */
    public boolean hasHeir(UUID playerId) {
        return successionOrder.stream().anyMatch(h -> h.playerId().equals(playerId));
    }

    /**
     * 获取玩家的继承顺位（从1开始）
     */
    public Optional<Integer> getHeirPosition(UUID playerId) {
        for (int i = 0; i < successionOrder.size(); i++) {
            if (successionOrder.get(i).playerId().equals(playerId)) {
                return Optional.of(i + 1);
            }
        }
        return Optional.empty();
    }

    /**
     * 获取第一位继承人
     */
    public Optional<HeirInfo> getFirstHeir() {
        return successionOrder.isEmpty() ? Optional.empty() : Optional.of(successionOrder.get(0));
    }

    // Setters
    public void setDynastyName(String dynastyName) {
        this.dynastyName = dynastyName != null ? dynastyName.trim() : "Unknown Dynasty";
    }

    public void setMonarch(UUID monarchId, String monarchName) {
        this.currentMonarchId = monarchId;
        this.currentMonarchName = monarchName;
        this.monarchSince = Instant.now();
        this.interregnumStart = null;
        this.reignCount++;
    }

    public void clearMonarch() {
        this.currentMonarchId = null;
        this.currentMonarchName = null;
        this.interregnumStart = Instant.now();
    }

    public void setSuccessionType(SuccessionType type) {
        this.successionType = Objects.requireNonNull(type, "successionType");
    }

    public void setSuccessionTitle(String title) {
        this.successionTitle = title != null ? title.trim() : "Monarch";
    }

    /**
     * 添加继承人（按顺序插入）
     */
    public void addHeir(UUID heirId, String heirName) {
        if (hasHeir(heirId)) {
            return;
        }
        successionOrder.add(new HeirInfo(heirId, heirName, Instant.now()));
    }

    /**
     * 移除继承人
     */
    public void removeHeir(UUID heirId) {
        successionOrder.removeIf(h -> h.playerId().equals(heirId));
    }

    /**
     * 调整继承人顺序
     */
    public void reorderHeirs(List<UUID> newOrder) {
        List<HeirInfo> reordered = new ArrayList<>();
        Map<UUID, HeirInfo> existing = new HashMap<>();
        for (HeirInfo heir : successionOrder) {
            existing.put(heir.playerId(), heir);
        }
        for (UUID id : newOrder) {
            HeirInfo heir = existing.get(id);
            if (heir != null) {
                reordered.add(heir);
            }
        }
        successionOrder.clear();
        successionOrder.addAll(reordered);
    }

    /**
     * 获取统治天数
     */
    public long getDaysSinceMonarch() {
        if (monarchSince == null) {
            return 0;
        }
        return java.time.Duration.between(monarchSince, Instant.now()).toDays();
    }

    /**
     * 获取空缺天数
     */
    public long getInterregnumDays() {
        if (interregnumStart == null) {
            return 0;
        }
        return java.time.Duration.between(interregnumStart, Instant.now()).toDays();
    }

    /**
     * 继承人信息记录
     */
    public record HeirInfo(UUID playerId, String playerName, Instant addedAt) {}

    @Override
    public String toString() {
        return "Dynasty{" +
                "nationId=" + nationId +
                ", name='" + dynastyName + '\'' +
                ", monarch=" + currentMonarchName +
                ", successionType=" + successionType +
                ", heirs=" + successionOrder.size() +
                '}';
    }
}