package dev.starcore.starcore.module.anniversary.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 国家纪念日模型
 * 代表一个国家的纪念日
 */
public final class NationAnniversary {
    private final UUID id;
    private final UUID nationId;
    private String name;
    private LocalDate date;
    private AnniversaryType type;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime lastCelebratedAt;
    private boolean isRecurring;  // 是否每年重复
    private String celebrationMessage;  // 庆祝消息

    public NationAnniversary(
        UUID id,
        UUID nationId,
        String name,
        LocalDate date,
        AnniversaryType type,
        String description,
        LocalDateTime createdAt,
        LocalDateTime lastCelebratedAt,
        boolean isRecurring,
        String celebrationMessage
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.nationId = Objects.requireNonNull(nationId, "nationId");
        this.name = Objects.requireNonNull(name, "name");
        this.date = Objects.requireNonNull(date, "date");
        this.type = Objects.requireNonNull(type, "type");
        this.description = description != null ? description : "";
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.lastCelebratedAt = lastCelebratedAt;
        this.isRecurring = isRecurring;
        this.celebrationMessage = celebrationMessage != null ? celebrationMessage : "";
    }

    /**
     * 创建新纪念日
     */
    public static NationAnniversary create(
        UUID nationId,
        String name,
        LocalDate date,
        AnniversaryType type,
        String description
    ) {
        return new NationAnniversary(
            UUID.randomUUID(),
            nationId,
            name,
            date,
            type,
            description,
            LocalDateTime.now(),
            null,
            true,  // 默认每年重复
            ""
        );
    }

    /**
     * 创建国家成立纪念日
     */
    public static NationAnniversary foundingAnniversary(UUID nationId, LocalDate foundingDate) {
        return new NationAnniversary(
            UUID.randomUUID(),
            nationId,
            "国家成立纪念日",
            foundingDate,
            AnniversaryType.FOUNDING,
            "庆祝国家成立",
            LocalDateTime.now(),
            null,
            true,
            "🎉 今天是国家成立纪念日！"
        );
    }

    // ==================== Getters ====================

    public UUID id() {
        return id;
    }

    public UUID nationId() {
        return nationId;
    }

    public String name() {
        return name;
    }

    public LocalDate date() {
        return date;
    }

    public AnniversaryType type() {
        return type;
    }

    public String description() {
        return description;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public LocalDateTime lastCelebratedAt() {
        return lastCelebratedAt;
    }

    public boolean isRecurring() {
        return isRecurring;
    }

    public String celebrationMessage() {
        return celebrationMessage;
    }

    // ==================== Setters ====================

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public void setDate(LocalDate date) {
        this.date = Objects.requireNonNull(date, "date");
    }

    public void setType(AnniversaryType type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    public void setDescription(String description) {
        this.description = description != null ? description : "";
    }

    public void setLastCelebratedAt(LocalDateTime lastCelebratedAt) {
        this.lastCelebratedAt = lastCelebratedAt;
    }

    public void setRecurring(boolean recurring) {
        this.isRecurring = recurring;
    }

    public void setCelebrationMessage(String celebrationMessage) {
        this.celebrationMessage = celebrationMessage != null ? celebrationMessage : "";
    }

    // ==================== 计算方法 ====================

    /**
     * 计算今天是第几周年
     */
    public int getCurrentYear() {
        LocalDate today = LocalDate.now();
        int yearDiff = today.getYear() - date.getYear();
        if (today.getDayOfYear() < date.getDayOfYear()) {
            yearDiff--;
        }
        return Math.max(1, yearDiff + 1);
    }

    /**
     * 计算距离今天还有多少天（负数表示已过）
     */
    public int daysUntil() {
        LocalDate today = LocalDate.now();
        LocalDate thisYearDate = LocalDate.of(today.getYear(), date.getMonth(), date.getDayOfMonth());

        if (thisYearDate.isBefore(today) || thisYearDate.isEqual(today)) {
            // 今年已过，计算到明年的天数
            LocalDate nextYearDate = LocalDate.of(today.getYear() + 1, date.getMonth(), date.getDayOfMonth());
            return (int) java.time.temporal.ChronoUnit.DAYS.between(today, nextYearDate);
        } else {
            return (int) java.time.temporal.ChronoUnit.DAYS.between(today, thisYearDate);
        }
    }

    /**
     * 检查今天是否是纪念日
     */
    public boolean isToday() {
        LocalDate today = LocalDate.now();
        return today.getMonth() == date.getMonth() && today.getDayOfMonth() == date.getDayOfMonth();
    }

    /**
     * 检查是否是里程碑纪念日（1、5、10、25、50、100周年等）
     */
    public boolean isMilestone() {
        int year = getCurrentYear();
        return year == 1 || year == 5 || year == 10 || year == 25 || year == 50 || year == 100
            || year % 25 == 0;
    }

    /**
     * 检查是否已庆祝
     */
    public boolean isCelebrated() {
        if (lastCelebratedAt == null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        return lastCelebratedAt.toLocalDate().equals(today);
    }

    /**
     * 获取今年的纪念日日期
     */
    public LocalDate getThisYearDate() {
        return LocalDate.of(LocalDate.now().getYear(), date.getMonth(), date.getDayOfMonth());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NationAnniversary other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("NationAnniversary{id=%s, nation=%s, name='%s', date=%s, type=%s, year=%d}",
            id, nationId, name, date, type, getCurrentYear());
    }
}
