package dev.starcore.starcore.mechanics;

import java.util.UUID;

/**
 * 文化值
 * 代表国家或城市的文化发展
 */
public class Culture {

    private final UUID ownerId; // 所有者ID（国家或城市）
    private final String ownerName;
    private int culturePoints; // 文化点数
    private int literature; // 文学
    private int art; // 艺术
    private int music; // 音乐
    private int architecture; // 建筑
    private int philosophy; // 哲学
    private long lastUpdate;

    public Culture(UUID ownerId, String ownerName) {
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.culturePoints = 0;
        this.literature = 0;
        this.art = 0;
        this.music = 0;
        this.architecture = 0;
        this.philosophy = 0;
        this.lastUpdate = System.currentTimeMillis();
    }

    /**
     * 增加文化点数
     */
    public void addCulturePoints(int amount) {
        this.culturePoints += amount;
        this.lastUpdate = System.currentTimeMillis();
    }

    /**
     * 增加特定类型的文化值
     */
    public void addLiterature(int amount) {
        this.literature += amount;
        addCulturePoints(amount);
    }

    public void addArt(int amount) {
        this.art += amount;
        addCulturePoints(amount);
    }

    public void addMusic(int amount) {
        this.music += amount;
        addCulturePoints(amount);
    }

    public void addArchitecture(int amount) {
        this.architecture += amount;
        addCulturePoints(amount);
    }

    public void addPhilosophy(int amount) {
        this.philosophy += amount;
        addCulturePoints(amount);
    }

    /**
     * 获取总文化值
     */
    public int getTotalCulture() {
        return literature + art + music + architecture + philosophy;
    }

    /**
     * 获取文化等级
     */
    public String getCultureLevel() {
        int total = getTotalCulture();
        if (total >= 10000) return "§6文明璀璨";
        if (total >= 5000) return "§e文化繁荣";
        if (total >= 2000) return "§a文化发达";
        if (total >= 1000) return "§f文化成长";
        if (total >= 500) return "§7文化萌芽";
        return "§8文化荒芜";
    }

    /**
     * 获取文化加成倍率
     */
    public double getCultureBonus() {
        int total = getTotalCulture();
        // 文化值转换为加成倍率（对声望、经验等）
        return 1.0 + (total / 1000.0) * 0.1; // 每1000文化值增加10%加成
    }

    /**
     * 获取文化影响力半径（格）
     */
    public int getInfluenceRadius() {
        int total = getTotalCulture();
        return Math.min(100 + (total / 100), 500); // 基础100格，最大500格
    }

    /**
     * 获取详细统计
     */
    public String getDetailedStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("§6§l文化统计 - ").append(ownerName).append("\n");
        sb.append("§e等级: ").append(getCultureLevel()).append("\n");
        sb.append("§e总文化值: §f").append(getTotalCulture()).append("\n");
        sb.append("§e文化加成: §a+").append(String.format("%.1f", (getCultureBonus() - 1.0) * 100)).append("%\n");
        sb.append("§e影响半径: §f").append(getInfluenceRadius()).append(" 格\n");
        sb.append("\n§6分类统计:\n");
        sb.append("  §7- §f文学: §a").append(literature).append("\n");
        sb.append("  §7- §f艺术: §a").append(art).append("\n");
        sb.append("  §7- §f音乐: §a").append(music).append("\n");
        sb.append("  §7- §f建筑: §a").append(architecture).append("\n");
        sb.append("  §7- §f哲学: §a").append(philosophy).append("\n");

        return sb.toString();
    }

    /**
     * 获取最强的文化类型
     */
    public String getDominantCulture() {
        int max = Math.max(literature, Math.max(art, Math.max(music, Math.max(architecture, philosophy))));

        if (max == literature) return "文学";
        if (max == art) return "艺术";
        if (max == music) return "音乐";
        if (max == architecture) return "建筑";
        if (max == philosophy) return "哲学";

        return "无";
    }

    // Getters and Setters

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public int getCulturePoints() {
        return culturePoints;
    }

    public void setCulturePoints(int culturePoints) {
        this.culturePoints = culturePoints;
    }

    public int getLiterature() {
        return literature;
    }

    public void setLiterature(int literature) {
        this.literature = literature;
    }

    public int getArt() {
        return art;
    }

    public void setArt(int art) {
        this.art = art;
    }

    public int getMusic() {
        return music;
    }

    public void setMusic(int music) {
        this.music = music;
    }

    public int getArchitecture() {
        return architecture;
    }

    public void setArchitecture(int architecture) {
        this.architecture = architecture;
    }

    public int getPhilosophy() {
        return philosophy;
    }

    public void setPhilosophy(int philosophy) {
        this.philosophy = philosophy;
    }

    public long getLastUpdate() {
        return lastUpdate;
    }
}
