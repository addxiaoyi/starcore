package dev.starcore.starcore.quest;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 委托板类
 * 管理和展示所有可用的委托
 *
 * @author StarCore Team
 * @since 1.0.0
 */
public class CommissionBoard {

    private final List<Commission> commissions;
    private final Map<String, Commission> commissionMap;

    /**
     * 排序类型
     */
    public enum SortType {
        REWARD_DESC("赏金从高到低"),
        REWARD_ASC("赏金从低到高"),
        TIME_DESC("最新发布"),
        TIME_ASC("最早发布"),
        DIFFICULTY_DESC("难度从高到低"),
        DIFFICULTY_ASC("难度从低到高");

        private final String displayName;

        SortType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 构造函数
     */
    public CommissionBoard() {
        this.commissions = new ArrayList<>();
        this.commissionMap = new HashMap<>();
    }

    /**
     * 添加委托
     */
    public void addCommission(Commission commission) {
        commissions.add(commission);
        commissionMap.put(commission.getId(), commission);
    }

    /**
     * 移除委托
     */
    public void removeCommission(String commissionId) {
        Commission commission = commissionMap.remove(commissionId);
        if (commission != null) {
            commissions.remove(commission);
        }
    }

    /**
     * 获取委托
     */
    public Commission getCommission(String commissionId) {
        return commissionMap.get(commissionId);
    }

    /**
     * 获取所有可用委托（未过期且未接取）
     */
    public List<Commission> getAvailableCommissions() {
        return commissions.stream()
                .filter(c -> !c.isExpired() && !c.isAccepted())
                .collect(Collectors.toList());
    }

    /**
     * 获取所有委托
     */
    public List<Commission> getAllCommissions() {
        return new ArrayList<>(commissions);
    }

    /**
     * 按类型筛选
     */
    public List<Commission> filterByType(Commission.CommissionType type) {
        return commissions.stream()
                .filter(c -> c.getType() == type && !c.isExpired() && !c.isAccepted())
                .collect(Collectors.toList());
    }

    /**
     * 按难度筛选
     */
    public List<Commission> filterByDifficulty(QuestDifficulty difficulty) {
        return commissions.stream()
                .filter(c -> c.getDifficulty() == difficulty && !c.isExpired() && !c.isAccepted())
                .collect(Collectors.toList());
    }

    /**
     * 按分类筛选
     */
    public List<Commission> filterByCategory(String category) {
        return commissions.stream()
                .filter(c -> c.getCategory().equals(category) && !c.isExpired() && !c.isAccepted())
                .collect(Collectors.toList());
    }

    /**
     * 按最低等级筛选
     */
    public List<Commission> filterByMinLevel(int playerLevel) {
        return commissions.stream()
                .filter(c -> c.getMinLevel() <= playerLevel && !c.isExpired() && !c.isAccepted())
                .collect(Collectors.toList());
    }

    /**
     * 按赏金范围筛选
     */
    public List<Commission> filterByRewardRange(double minReward, double maxReward) {
        return commissions.stream()
                .filter(c -> c.getReward() >= minReward && c.getReward() <= maxReward)
                .filter(c -> !c.isExpired() && !c.isAccepted())
                .collect(Collectors.toList());
    }

    /**
     * 搜索委托（标题或描述）
     */
    public List<Commission> search(String keyword) {
        String lowerKeyword = keyword.toLowerCase();
        return commissions.stream()
                .filter(c -> c.getTitle().toLowerCase().contains(lowerKeyword) ||
                           c.getDescription().toLowerCase().contains(lowerKeyword))
                .filter(c -> !c.isExpired() && !c.isAccepted())
                .collect(Collectors.toList());
    }

    /**
     * 排序委托列表
     */
    public List<Commission> sort(List<Commission> commissionList, SortType sortType) {
        List<Commission> sorted = new ArrayList<>(commissionList);

        switch (sortType) {
            case REWARD_DESC:
                sorted.sort(Comparator.comparingDouble(Commission::getReward).reversed());
                break;
            case REWARD_ASC:
                sorted.sort(Comparator.comparingDouble(Commission::getReward));
                break;
            case TIME_DESC:
                sorted.sort(Comparator.comparingLong(Commission::getPublishTime).reversed());
                break;
            case TIME_ASC:
                sorted.sort(Comparator.comparingLong(Commission::getPublishTime));
                break;
            case DIFFICULTY_DESC:
                sorted.sort(Comparator.comparingInt(c -> c.getDifficulty().getLevel()));
                Collections.reverse(sorted);
                break;
            case DIFFICULTY_ASC:
                sorted.sort(Comparator.comparingInt(c -> c.getDifficulty().getLevel()));
                break;
        }

        return sorted;
    }

    /**
     * 获取委托板页面
     */
    public CommissionPage getPage(int page, int pageSize, SortType sortType) {
        List<Commission> available = getAvailableCommissions();
        List<Commission> sorted = sort(available, sortType);

        int totalPages = (int) Math.ceil((double) sorted.size() / pageSize);
        page = Math.max(1, Math.min(page, totalPages));

        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, sorted.size());

        List<Commission> pageCommissions = sorted.subList(startIndex, endIndex);

        return new CommissionPage(page, totalPages, pageCommissions);
    }

    /**
     * 获取推荐委托（基于玩家等级）
     */
    public List<Commission> getRecommendedCommissions(int playerLevel, int count) {
        return commissions.stream()
                .filter(c -> !c.isExpired() && !c.isAccepted())
                .filter(c -> c.getMinLevel() <= playerLevel)
                .filter(c -> c.getMinLevel() >= playerLevel - 10) // 推荐接近玩家等级的
                .sorted(Comparator.comparingDouble(Commission::getReward).reversed())
                .limit(count)
                .collect(Collectors.toList());
    }

    /**
     * 获取高赏金委托
     */
    public List<Commission> getHighRewardCommissions(int count) {
        return commissions.stream()
                .filter(c -> !c.isExpired() && !c.isAccepted())
                .sorted(Comparator.comparingDouble(Commission::getReward).reversed())
                .limit(count)
                .collect(Collectors.toList());
    }

    /**
     * 获取即将过期的委托
     */
    public List<Commission> getExpiringCommissions(long thresholdMillis, int count) {
        long currentTime = System.currentTimeMillis();
        return commissions.stream()
                .filter(c -> !c.isExpired() && !c.isAccepted())
                .filter(c -> c.getExpireTime() - currentTime <= thresholdMillis)
                .sorted(Comparator.comparingLong(Commission::getExpireTime))
                .limit(count)
                .collect(Collectors.toList());
    }

    /**
     * 获取统计信息
     */
    public BoardStatistics getStatistics() {
        int total = commissions.size();
        int available = (int) commissions.stream()
                .filter(c -> !c.isExpired() && !c.isAccepted())
                .count();
        int inProgress = (int) commissions.stream()
                .filter(c -> c.isAccepted() && !c.isCompleted())
                .count();
        int completed = (int) commissions.stream()
                .filter(Commission::isCompleted)
                .count();
        int expired = (int) commissions.stream()
                .filter(Commission::isExpired)
                .count();

        return new BoardStatistics(total, available, inProgress, completed, expired);
    }

    /**
     * 清空委托板
     */
    public void clear() {
        commissions.clear();
        commissionMap.clear();
    }

    /**
     * 委托页面类
     */
    public static class CommissionPage {
        private final int currentPage;
        private final int totalPages;
        private final List<Commission> commissions;

        public CommissionPage(int currentPage, int totalPages, List<Commission> commissions) {
            this.currentPage = currentPage;
            this.totalPages = totalPages;
            this.commissions = commissions;
        }

        public int getCurrentPage() {
            return currentPage;
        }

        public int getTotalPages() {
            return totalPages;
        }

        public List<Commission> getCommissions() {
            return new ArrayList<>(commissions);
        }

        public boolean hasNextPage() {
            return currentPage < totalPages;
        }

        public boolean hasPreviousPage() {
            return currentPage > 1;
        }
    }

    /**
     * 委托板统计类
     */
    public static class BoardStatistics {
        private final int total;
        private final int available;
        private final int inProgress;
        private final int completed;
        private final int expired;

        public BoardStatistics(int total, int available, int inProgress, int completed, int expired) {
            this.total = total;
            this.available = available;
            this.inProgress = inProgress;
            this.completed = completed;
            this.expired = expired;
        }

        public int getTotal() {
            return total;
        }

        public int getAvailable() {
            return available;
        }

        public int getInProgress() {
            return inProgress;
        }

        public int getCompleted() {
            return completed;
        }

        public int getExpired() {
            return expired;
        }

        public List<String> getSummary() {
            List<String> summary = new ArrayList<>();
            summary.add("§6===== 委托板统计 =====");
            summary.add(String.format("§7总委托数: §e%d", total));
            summary.add(String.format("§7可接取: §a%d", available));
            summary.add(String.format("§7进行中: §e%d", inProgress));
            summary.add(String.format("§7已完成: §b%d", completed));
            summary.add(String.format("§7已过期: §c%d", expired));
            summary.add("§6=====================");
            return summary;
        }
    }
}
