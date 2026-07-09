package dev.starcore.starcore.achievement;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 成就服务抽象实现
 */
public abstract class AbstractAchievementService implements AchievementService {

    protected final Plugin plugin;

    // 所有注册的成就
    protected final Map<NamespacedKey, Achievement> achievements = new ConcurrentHashMap<>();

    // 玩家已完成的成就
    protected final Map<UUID, Set<NamespacedKey>> playerAchievements = new ConcurrentHashMap<>();

    // D-081: 内部经济服务引用；通过 setEconomyService 注入
    protected dev.starcore.starcore.foundation.economy.EconomyService economyService;

    public void setEconomyService(dev.starcore.starcore.foundation.economy.EconomyService economyService) {
        this.economyService = economyService;
    }

    @Override
    public dev.starcore.starcore.foundation.economy.EconomyService getInternalEconomy() {
        return economyService;
    }

    protected AbstractAchievementService(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Plugin getPlugin() {
        return plugin;
    }

    @Override
    public void registerAchievement(Achievement achievement) {
        achievements.put(achievement.getKey(), achievement);
    }

    @Override
    public void registerAchievements(Achievement... achievements) {
        for (Achievement achievement : achievements) {
            registerAchievement(achievement);
        }
    }

    @Override
    public Optional<Achievement> getAchievement(NamespacedKey key) {
        return Optional.ofNullable(achievements.get(key));
    }

    @Override
    public Collection<Achievement> getAllAchievements() {
        return new ArrayList<>(achievements.values());
    }

    @Override
    public Collection<Achievement> getAchievementsByCategory(AchievementCategory category) {
        return getAllAchievements();
    }

    @Override
    public boolean hasAchievement(UUID playerId, NamespacedKey key) {
        Set<NamespacedKey> completed = playerAchievements.get(playerId);
        return completed != null && completed.contains(key);
    }

    @Override
    public boolean grantAchievement(Player player, NamespacedKey key) {
        Achievement achievement = achievements.get(key);
        if (achievement == null) {
            return false;
        }

        // 检查是否已完成
        if (hasAchievement(player.getUniqueId(), key)) {
            return false;
        }

        // 检查父成就
        if (achievement.hasParent()) {
            if (!hasAchievement(player.getUniqueId(), achievement.getParent())) {
                return false;
            }
        }

        // 添加到已完成列表
        playerAchievements.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet())
            .add(key);

        // 触发成就解锁事件
        AchievementUnlockedEvent event = new AchievementUnlockedEvent(player, achievement);
        Bukkit.getPluginManager().callEvent(event);

        // 显示成就通知
        displayAchievement(player, achievement);

        // 给予奖励
        giveRewards(player, achievement);

        return true;
    }

    @Override
    public Set<NamespacedKey> getPlayerAchievements(UUID playerId) {
        Set<NamespacedKey> completed = playerAchievements.get(playerId);
        return completed != null ? new HashSet<>(completed) : Set.of();
    }

    @Override
    public int getPlayerProgress(UUID playerId) {
        Set<NamespacedKey> completed = playerAchievements.get(playerId);
        return completed != null ? completed.size() : 0;
    }

    @Override
    public int getTotalAchievements() {
        return achievements.size();
    }

    @Override
    public void loadPlayerData(UUID playerId, Set<NamespacedKey> achievements) {
        playerAchievements.put(playerId, ConcurrentHashMap.newKeySet());
        playerAchievements.get(playerId).addAll(achievements);
    }

    @Override
    public Set<NamespacedKey> savePlayerData(UUID playerId) {
        return getPlayerAchievements(playerId);
    }

    @Override
    public AchievementProgress getOrCreateProgress(UUID playerId) {
        return null; // 由子类实现
    }

    @Override
    public void incrementTrigger(UUID playerId, AchievementTrigger.TriggerType type, int amount) {
        // 由子类实现
    }
}
