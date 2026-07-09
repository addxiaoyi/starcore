package dev.starcore.starcore.daily;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 签到服务
 * 管理玩家每日签到
 */
public final class CheckInService {
    // 玩家签到记录 UUID -> CheckInData
    private final ConcurrentHashMap<UUID, CheckInData> checkInData = new ConcurrentHashMap<>();

    // D-058/060: 注入经济服务以实现扣费与回滚
    private dev.starcore.starcore.foundation.economy.EconomyService economyService;
    public void setEconomyService(dev.starcore.starcore.foundation.economy.EconomyService es) {
        this.economyService = es;
    }

    /**
     * 签到
     */
    public CheckInResult checkIn(UUID playerId) {
        CheckInData data = checkInData.computeIfAbsent(playerId, CheckInData::new);

        LocalDate today = LocalDate.now();

        // 检查今天是否已签到
        if (today.equals(data.lastCheckIn)) {
            return new CheckInResult(false, 0, "今天已经签到过了");
        }

        // 检查连续签到
        boolean isContinuous = data.lastCheckIn != null
            && data.lastCheckIn.plusDays(1).equals(today);

        if (isContinuous) {
            data.consecutiveDays++;
        } else {
            data.consecutiveDays = 1;
        }

        data.lastCheckIn = today;
        data.totalDays++;

        // 计算奖励
        int reward = calculateReward(data.consecutiveDays);

        return new CheckInResult(true, reward,
            String.format("签到成功！连续签到 %d 天", data.consecutiveDays));
    }

    /**
     * 计算签到奖励
     */
    private int calculateReward(int consecutiveDays) {
        // 基础奖励
        int baseReward = 100;

        // 连续签到奖励
        int bonus = 0;
        if (consecutiveDays >= 30) {
            bonus = 1000;
        } else if (consecutiveDays >= 14) {
            bonus = 500;
        } else if (consecutiveDays >= 7) {
            bonus = 200;
        } else if (consecutiveDays >= 3) {
            bonus = 50;
        }

        return baseReward + bonus + (consecutiveDays * 10);
    }

    /**
     * 获取签到数据
     */
    public CheckInData getData(UUID playerId) {
        return checkInData.computeIfAbsent(playerId, CheckInData::new);
    }

    /**
     * 补签
     * @param playerId 玩家ID
     * @param daysAgo 补签几天前（1表示昨天，2表示前天）
     * @param cost 补签消耗的货币（金币或特殊道具）
     * @return 补签结果
     */
    public MakeUpResult makeUpCheckIn(UUID playerId, int daysAgo, int cost) {
        if (daysAgo <= 0) {
            return new MakeUpResult(false, "补签天数必须大于0");
        }

        CheckInData data = checkInData.computeIfAbsent(playerId, CheckInData::new);
        LocalDate today = LocalDate.now();
        LocalDate targetDate = today.minusDays(daysAgo);

        // D-059: 新玩家（lastCheckIn==null）禁止补签，必须先正常签到
        if (data.lastCheckIn == null) {
            return new MakeUpResult(false, "请先完成今日正常签到，再进行补签");
        }

        // 检查目标日期是否已签到
        if (data.makeUpDates.contains(targetDate)) {
            return new MakeUpResult(false, "该日期已经补签过了");
        }

        // 检查目标日期是否是今天
        if (targetDate.equals(today)) {
            return new MakeUpResult(false, "不能补签今天，请使用正常签到");
        }

        // 检查目标日期是否在最后签到日期之后
        if (!targetDate.isBefore(data.lastCheckIn)) {
            return new MakeUpResult(false, "只能补签最后签到日期之前的日期");
        }

        // 检查补签限制（例如：只能补签最近7天）
        long daysBetween = ChronoUnit.DAYS.between(targetDate, today);
        if (daysBetween > 7) {
            return new MakeUpResult(false, "只能补签最近7天的签到");
        }

        // 计算补签费用（天数越久费用越高）
        int actualCost = calculateMakeUpCost(daysAgo);
        if (cost < actualCost) {
            return new MakeUpResult(false, "补签失败：需要 " + actualCost + " 金币");
        }

        // D-058: 原子化扣费 —— 若 economyService 注入，则先扣费再补签；扣费失败直接回退
        if (economyService != null) {
            // 不要相信调用方传入的 cost，实际扣费按 actualCost
            boolean ok = economyService.withdraw(playerId, java.math.BigDecimal.valueOf(actualCost));
            if (!ok) {
                return new MakeUpResult(false, "金币不足，需要 " + actualCost + " 金币");
            }
        }

        // 执行补签
        if (!data.makeUpDates.add(targetDate)) {
            // 防并发重复添加（D-061）
            if (economyService != null) {
                economyService.deposit(playerId, java.math.BigDecimal.valueOf(actualCost));
            }
            return new MakeUpResult(false, "该日期已经补签过了");
        }
        data.totalDays++;

        // 重新计算连续签到天数
        recalculateConsecutiveDays(data);

        return new MakeUpResult(true, "补签成功！花费 " + actualCost + " 金币");
    }

    /**
     * 计算补签费用
     */
    private int calculateMakeUpCost(int daysAgo) {
        // 基础费用
        int baseCost = 100;

        // 天数越久费用越高
        return baseCost * daysAgo;
    }

    /**
     * 重新计算连续签到天数
     * 考虑补签日期
     */
    private void recalculateConsecutiveDays(CheckInData data) {
        if (data.lastCheckIn == null) {
            data.consecutiveDays = 0;
            return;
        }

        LocalDate today = LocalDate.now();
        int consecutive = 0;
        LocalDate checkDate = data.lastCheckIn;
        Set<LocalDate> counted = new HashSet<>(); // D-060: 防止 lastCheckIn 与 makeUpDates 重叠重复计入

        // 从最后签到日期往前推算
        while (checkDate != null) {
            // 检查这一天是否签到过（正常签到或补签）；lastCheckIn 只计入一次
            boolean isLast = checkDate.equals(data.lastCheckIn);
            boolean isMakeUp = data.makeUpDates.contains(checkDate);
            LocalDate thisDate = checkDate;
            if ((isLast && !counted.contains(thisDate)) || (isMakeUp && !counted.contains(thisDate))) {
                consecutive++;
                counted.add(thisDate);
                checkDate = checkDate.minusDays(1);
            } else if (!isLast && !isMakeUp) {
                break;
            } else {
                // 已计入过这一日期，但下一日期仍可能签到，继续往前推
                checkDate = checkDate.minusDays(1);
                if (consecutive > 365) break;
            }

            // 防止无限循环
            if (consecutive > 365) {
                break;
            }
        }

        data.consecutiveDays = consecutive;
    }

    /**
     * D-057: 统一命名（savePlayerData/getPlayerData），消除 loadPlayerData/getPlayerData 语义混乱。
     * 保存玩家数据
     */
    public void savePlayerData(UUID playerId, CheckInData data) {
        checkInData.put(playerId, data);
    }

    /**
     * 获取玩家数据
     */
    public CheckInData getPlayerData(UUID playerId) {
        return checkInData.computeIfAbsent(playerId, CheckInData::new);
    }

    /**
     * 签到数据
     */
    public static class CheckInData {
        private final UUID playerId;
        private LocalDate lastCheckIn;
        private int consecutiveDays;
        private int totalDays;
        private final Set<LocalDate> makeUpDates; // 补签日期记录

        public CheckInData(UUID playerId) {
            this.playerId = playerId;
            this.consecutiveDays = 0;
            this.totalDays = 0;
            this.makeUpDates = ConcurrentHashMap.newKeySet();
        }

        public UUID getPlayerId() { return playerId; }
        public LocalDate getLastCheckIn() { return lastCheckIn; }
        public int getConsecutiveDays() { return consecutiveDays; }
        public int getTotalDays() { return totalDays; }
        public Set<LocalDate> getMakeUpDates() { return new HashSet<>(makeUpDates); }

        public void setLastCheckIn(LocalDate date) { this.lastCheckIn = date; }
        public void setConsecutiveDays(int days) { this.consecutiveDays = days; }
        public void setTotalDays(int days) { this.totalDays = days; }
    }

    /**
     * 签到结果
     */
    public record CheckInResult(
        boolean success,
        int reward,
        String message
    ) {}

    /**
     * 补签结果
     */
    public record MakeUpResult(
        boolean success,
        String message
    ) {}
}
