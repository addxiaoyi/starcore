package dev.starcore.starcore.mechanics;

import dev.starcore.starcore.util.MessageUtil;

import java.util.UUID;

/**
 * 宗教信仰
 * 玩家的宗教信仰数据
 */
public class Religion {

    private final UUID playerId;
    private ReligionType type;
    private int faith; // 信仰值 (0-1000)
    private long joinTime;
    private int contributions; // 贡献值
    private boolean blessed; // 是否受到祝福

    public Religion(UUID playerId, ReligionType type) {
        this.playerId = playerId;
        this.type = type;
        this.faith = 0;
        this.joinTime = System.currentTimeMillis();
        this.contributions = 0;
        this.blessed = false;
    }

    /**
     * 增加信仰值
     */
    public void addFaith(int amount) {
        this.faith = Math.min(1000, this.faith + amount);
    }

    /**
     * 减少信仰值
     */
    public void reduceFaith(int amount) {
        this.faith = Math.max(0, this.faith - amount);
    }

    /**
     * 增加贡献值
     */
    public void addContribution(int amount) {
        this.contributions += amount;
        // 贡献也会增加信仰值
        addFaith(amount / 10);
    }

    /**
     * 改变信仰
     */
    public void changeFaith(ReligionType newType) {
        this.type = newType;
        this.faith = 0; // 重置信仰值
        this.joinTime = System.currentTimeMillis();
        this.blessed = false;
    }

    /**
     * 获取信仰等级
     */
    public String getFaithLevel() {
        if (faith >= 800) return MessageUtil.GOLD + "虔诚信徒";
        if (faith >= 600) return MessageUtil.YELLOW + "狂热者";
        if (faith >= 400) return MessageUtil.GREEN + "信徒";
        if (faith >= 200) return MessageUtil.WHITE + "追随者";
        if (faith >= 100) return MessageUtil.GRAY + "初学者";
        return MessageUtil.DARK_GRAY + "新手";
    }

    /**
     * 获取信仰描述
     */
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(MessageUtil.GOLD).append(MessageUtil.BOLD).append("宗教信息\n");
        sb.append(MessageUtil.YELLOW).append("信仰: ").append(type.getColoredName()).append("\n");
        sb.append(MessageUtil.YELLOW).append("等级: ").append(getFaithLevel()).append("\n");
        sb.append(MessageUtil.YELLOW).append("信仰值: ").append(MessageUtil.WHITE).append(faith).append("/1000\n");
        sb.append(MessageUtil.YELLOW).append("贡献值: ").append(MessageUtil.WHITE).append(contributions).append("\n");

        long days = (System.currentTimeMillis() - joinTime) / (1000 * 60 * 60 * 24);
        sb.append(MessageUtil.YELLOW).append("信仰时长: ").append(MessageUtil.WHITE).append(days).append(" 天\n");

        sb.append("\n").append(MessageUtil.YELLOW).append("教义: ").append(MessageUtil.GRAY).append(type.getDescription()).append("\n");
        sb.append(MessageUtil.YELLOW).append("祝福效果: ").append(MessageUtil.GREEN).append(type.getBlessingEffect()).append("\n");
        sb.append(MessageUtil.YELLOW).append("禁忌: ").append(MessageUtil.RED).append(type.getTaboo()).append("\n");

        if (blessed) {
            sb.append("\n").append(MessageUtil.GREEN).append(MessageUtil.BOLD).append("当前受到神的祝福！");
        }

        return sb.toString();
    }

    /**
     * 检查是否可以接受祝福
     */
    public boolean canReceiveBlessing() {
        return faith >= 100 && !blessed;
    }

    // Getters and Setters

    public UUID getPlayerId() {
        return playerId;
    }

    public ReligionType getType() {
        return type;
    }

    public int getFaith() {
        return faith;
    }

    public void setFaith(int faith) {
        this.faith = Math.max(0, Math.min(1000, faith));
    }

    public long getJoinTime() {
        return joinTime;
    }

    public void setJoinTime(long joinTime) {
        this.joinTime = joinTime;
    }

    public int getContributions() {
        return contributions;
    }

    public void setContributions(int contributions) {
        this.contributions = contributions;
    }

    public boolean isBlessed() {
        return blessed;
    }

    public void setBlessed(boolean blessed) {
        this.blessed = blessed;
    }
}
