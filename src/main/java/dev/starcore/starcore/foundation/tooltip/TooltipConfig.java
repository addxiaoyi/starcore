package dev.starcore.starcore.foundation.tooltip;

import org.jetbrains.annotations.NotNull;

/**
 * 智能提示配置
 */
public class TooltipConfig {

    // 启用/禁用各种功能
    private boolean hotbarHintsEnabled = true;
    private boolean customTooltipsEnabled = true;
    private boolean rarityColorsEnabled = true;
    private boolean itemStatsEnabled = true;
    private boolean enchantmentInfoEnabled = true;

    // ActionBar 设置
    private int hotbarHintDuration = 3; // 秒
    private int hotbarHintCooldown = 1000; // 毫秒
    private boolean hotbarHintOnShift = true;

    // 提示样式
    private boolean showItemId = true;
    private boolean showDurability = true;
    private boolean showEnchantments = true;
    private boolean showRarity = true;
    private boolean showLore = true;

    // 稀有度颜色配置
    private String rarityCommon = "#9E9E9E";      // 灰色 - 普通
    private String rarityUncommon = "#4CAF50";    // 绿色 - 优秀
    private String rarityRare = "#2196F3";        // 蓝色 - 稀有
    private String rarityEpic = "#9C27B0";        // 紫色 - 史诗
    private String rarityLegendary = "#FF9800";   // 橙色 - 传说
    private String rarityMythic = "#F44336";      // 红色 - 神话

    // 性能设置
    private int maxTooltipLines = 20;
    private int cacheExpirationMs = 60000; // 1分钟缓存过期

    public TooltipConfig() {
    }

    // Getters and Setters

    public boolean isHotbarHintsEnabled() {
        return hotbarHintsEnabled;
    }

    public void setHotbarHintsEnabled(boolean hotbarHintsEnabled) {
        this.hotbarHintsEnabled = hotbarHintsEnabled;
    }

    public boolean isCustomTooltipsEnabled() {
        return customTooltipsEnabled;
    }

    public void setCustomTooltipsEnabled(boolean customTooltipsEnabled) {
        this.customTooltipsEnabled = customTooltipsEnabled;
    }

    public boolean isRarityColorsEnabled() {
        return rarityColorsEnabled;
    }

    public void setRarityColorsEnabled(boolean rarityColorsEnabled) {
        this.rarityColorsEnabled = rarityColorsEnabled;
    }

    public boolean isItemStatsEnabled() {
        return itemStatsEnabled;
    }

    public void setItemStatsEnabled(boolean itemStatsEnabled) {
        this.itemStatsEnabled = itemStatsEnabled;
    }

    public boolean isEnchantmentInfoEnabled() {
        return enchantmentInfoEnabled;
    }

    public void setEnchantmentInfoEnabled(boolean enchantmentInfoEnabled) {
        this.enchantmentInfoEnabled = enchantmentInfoEnabled;
    }

    public int getHotbarHintDuration() {
        return hotbarHintDuration;
    }

    public void setHotbarHintDuration(int hotbarHintDuration) {
        this.hotbarHintDuration = Math.max(1, Math.min(10, hotbarHintDuration));
    }

    public int getHotbarHintCooldown() {
        return hotbarHintCooldown;
    }

    public void setHotbarHintCooldown(int hotbarHintCooldown) {
        this.hotbarHintCooldown = Math.max(0, hotbarHintCooldown);
    }

    public boolean isHotbarHintOnShift() {
        return hotbarHintOnShift;
    }

    public void setHotbarHintOnShift(boolean hotbarHintOnShift) {
        this.hotbarHintOnShift = hotbarHintOnShift;
    }

    public boolean isShowItemId() {
        return showItemId;
    }

    public void setShowItemId(boolean showItemId) {
        this.showItemId = showItemId;
    }

    public boolean isShowDurability() {
        return showDurability;
    }

    public void setShowDurability(boolean showDurability) {
        this.showDurability = showDurability;
    }

    public boolean isShowEnchantments() {
        return showEnchantments;
    }

    public void setShowEnchantments(boolean showEnchantments) {
        this.showEnchantments = showEnchantments;
    }

    public boolean isShowRarity() {
        return showRarity;
    }

    public void setShowRarity(boolean showRarity) {
        this.showRarity = showRarity;
    }

    public boolean isShowLore() {
        return showLore;
    }

    public void setShowLore(boolean showLore) {
        this.showLore = showLore;
    }

    public String getRarityCommon() {
        return rarityCommon;
    }

    public void setRarityCommon(@NotNull String rarityCommon) {
        this.rarityCommon = rarityCommon;
    }

    public String getRarityUncommon() {
        return rarityUncommon;
    }

    public void setRarityUncommon(@NotNull String rarityUncommon) {
        this.rarityUncommon = rarityUncommon;
    }

    public String getRarityRare() {
        return rarityRare;
    }

    public void setRarityRare(@NotNull String rarityRare) {
        this.rarityRare = rarityRare;
    }

    public String getRarityEpic() {
        return rarityEpic;
    }

    public void setRarityEpic(@NotNull String rarityEpic) {
        this.rarityEpic = rarityEpic;
    }

    public String getRarityLegendary() {
        return rarityLegendary;
    }

    public void setRarityLegendary(@NotNull String rarityLegendary) {
        this.rarityLegendary = rarityLegendary;
    }

    public String getRarityMythic() {
        return rarityMythic;
    }

    public void setRarityMythic(@NotNull String rarityMythic) {
        this.rarityMythic = rarityMythic;
    }

    public int getMaxTooltipLines() {
        return maxTooltipLines;
    }

    public void setMaxTooltipLines(int maxTooltipLines) {
        this.maxTooltipLines = Math.max(1, Math.min(50, maxTooltipLines));
    }

    public int getCacheExpirationMs() {
        return cacheExpirationMs;
    }

    public void setCacheExpirationMs(int cacheExpirationMs) {
        this.cacheExpirationMs = Math.max(1000, cacheExpirationMs);
    }
}