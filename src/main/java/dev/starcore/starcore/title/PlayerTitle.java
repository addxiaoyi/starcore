package dev.starcore.starcore.title;
import java.util.Optional;

import java.time.Instant;
import java.util.*;

/**
 * 玩家称号和徽章记录
 * 存储玩家拥有和装备的称号、徽章信息
 */
public class PlayerTitle {
    private final UUID playerId;
    private final Set<String> unlockedTitles;
    private final Set<String> unlockedBadges;
    private final Map<String, Instant> unlockTimes;
    private String equippedTitle;
    private String equippedBadge;

    public PlayerTitle(UUID playerId) {
        this.playerId = playerId;
        this.unlockedTitles = new HashSet<>();
        this.unlockedBadges = new HashSet<>();
        this.unlockTimes = new HashMap<>();
        this.equippedTitle = null;
        this.equippedBadge = null;
    }

    /**
     * 解锁称号
     */
    public boolean unlockTitle(String titleId) {
        if (unlockedTitles.add(titleId)) {
            unlockTimes.put("title_" + titleId, Instant.now());
            return true;
        }
        return false;
    }

    /**
     * 解锁徽章
     */
    public boolean unlockBadge(String badgeId) {
        if (unlockedBadges.add(badgeId)) {
            unlockTimes.put("badge_" + badgeId, Instant.now());
            return true;
        }
        return false;
    }

    /**
     * 装备称号
     */
    public boolean equipTitle(String titleId) {
        if (titleId == null || unlockedTitles.contains(titleId)) {
            this.equippedTitle = titleId;
            return true;
        }
        return false;
    }

    /**
     * 装备徽章
     */
    public boolean equipBadge(String badgeId) {
        if (badgeId == null || unlockedBadges.contains(badgeId)) {
            this.equippedBadge = badgeId;
            return true;
        }
        return false;
    }

    /**
     * 卸下称号
     */
    public void unequipTitle() {
        this.equippedTitle = null;
    }

    /**
     * 卸下徽章
     */
    public void unequipBadge() {
        this.equippedBadge = null;
    }

    /**
     * 检查是否已解锁称号
     */
    public boolean hasTitleUnlocked(String titleId) {
        return unlockedTitles.contains(titleId);
    }

    /**
     * 检查是否已解锁徽章
     */
    public boolean hasBadgeUnlocked(String badgeId) {
        return unlockedBadges.contains(badgeId);
    }

    /**
     * 获取称号解锁时间
     */
    public Optional<Instant> getTitleUnlockTime(String titleId) {
        return Optional.ofNullable(unlockTimes.get("title_" + titleId));
    }

    /**
     * 获取徽章解锁时间
     */
    public Optional<Instant> getBadgeUnlockTime(String badgeId) {
        return Optional.ofNullable(unlockTimes.get("badge_" + badgeId));
    }

    // Getters
    public UUID getPlayerId() {
        return playerId;
    }

    public Set<String> getUnlockedTitles() {
        return Collections.unmodifiableSet(unlockedTitles);
    }

    public Set<String> getUnlockedBadges() {
        return Collections.unmodifiableSet(unlockedBadges);
    }

    public Optional<String> getEquippedTitle() {
        return Optional.ofNullable(equippedTitle);
    }

    public Optional<String> getEquippedBadge() {
        return Optional.ofNullable(equippedBadge);
    }

    public int getTitleCount() {
        return unlockedTitles.size();
    }

    public int getBadgeCount() {
        return unlockedBadges.size();
    }

    /**
     * 获取所有解锁时间记录
     */
    public Map<String, Instant> getUnlockTimes() {
        return Collections.unmodifiableMap(unlockTimes);
    }

    /**
     * 从数据库加载数据
     */
    public void loadFromData(Set<String> titles, Set<String> badges,
                            Map<String, Instant> times,
                            String equipped, String equippedBadge) {
        this.unlockedTitles.clear();
        this.unlockedTitles.addAll(titles);
        this.unlockedBadges.clear();
        this.unlockedBadges.addAll(badges);
        this.unlockTimes.clear();
        this.unlockTimes.putAll(times);
        this.equippedTitle = equipped;
        this.equippedBadge = equippedBadge;
    }
}
