package dev.starcore.starcore.achievement;
import java.util.Optional;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 成就服务接口
 */
public interface AchievementService {

    /**
     * 注册成就
     */
    void registerAchievement(Achievement achievement);

    /**
     * 批量注册成就
     */
    void registerAchievements(Achievement... achievements);

    /**
     * 获取成就
     */
    Optional<Achievement> getAchievement(NamespacedKey key);

    /**
     * 获取所有成就
     */
    Collection<Achievement> getAllAchievements();

    /**
     * 按分类获取成就
     */
    Collection<Achievement> getAchievementsByCategory(AchievementCategory category);

    /**
     * 检查玩家是否已完成成就
     */
    boolean hasAchievement(UUID playerId, NamespacedKey key);

    /**
     * 给予玩家成就
     */
    boolean grantAchievement(Player player, NamespacedKey key);

    /**
     * 获取玩家已完成的成就
     */
    Set<NamespacedKey> getPlayerAchievements(UUID playerId);

    /**
     * 获取玩家成就进度
     */
    int getPlayerProgress(UUID playerId);

    /**
     * 获取总成就数量
     */
    int getTotalAchievements();

    /**
     * 加载玩家数据
     */
    void loadPlayerData(UUID playerId, Set<NamespacedKey> achievements);

    /**
     * 保存玩家数据
     */
    Set<NamespacedKey> savePlayerData(UUID playerId);

    /**
     * 获取玩家进度追踪器
     */
    AchievementProgress getOrCreateProgress(UUID playerId);

    /**
     * 增加触发器计数
     */
    void incrementTrigger(UUID playerId, AchievementTrigger.TriggerType type, int amount);

    /**
     * 默认实现：显示成就通知
     */
    default void displayAchievement(Player player, Achievement achievement) {
        Plugin plugin = getPlugin();

        // 1. Toast 弹窗提示（原版样式）
        if (achievement.isShowToast()) {
            sendToast(player, achievement);
        }

        // 2. 聊天栏提示
        if (achievement.isAnnounceToChat()) {
            announceToChat(player, achievement);
        } else {
            sendPrivateMessage(player, achievement);
        }

        // 3. 音效
        playSound(player, achievement);

        // 4. Title显示（大标题）
        sendTitle(player, achievement);
    }

    /**
     * 获取插件实例
     */
    Plugin getPlugin();

    /**
     * 发送Toast弹窗
     */
    default void sendToast(Player player, Achievement achievement) {
        String frameColor = achievement.getFrameType().getColor();

        Component toastTitle = Component.text()
            .append(Component.text("[成就达成] ", NamedTextColor.YELLOW, TextDecoration.BOLD))
            .append(achievement.getTitle())
            .build();

        player.sendActionBar(toastTitle);
        player.sendMessage(Component.text("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬", NamedTextColor.GOLD));
        player.sendMessage(Component.empty());

        Component achievementText = Component.text()
            .append(Component.text("  * ", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.text("成就达成！", NamedTextColor.YELLOW, TextDecoration.BOLD))
            .append(Component.text(" *", NamedTextColor.GOLD, TextDecoration.BOLD))
            .build();

        player.sendMessage(achievementText);
        player.sendMessage(Component.empty());

        Component titleLine = Component.text()
            .append(Component.text("  【", NamedTextColor.GRAY))
            .append(achievement.getTitle().color(getFrameColor(achievement.getFrameType())))
            .append(Component.text("】", NamedTextColor.GRAY))
            .build();

        player.sendMessage(titleLine);

        Component descLine = Component.text()
            .append(Component.text("  ", NamedTextColor.GRAY))
            .append(achievement.getDescription().color(NamedTextColor.WHITE))
            .build();

        player.sendMessage(descLine);
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬", NamedTextColor.GOLD));
    }

    /**
     * 获取框架颜色
     */
    default NamedTextColor getFrameColor(Achievement.FrameType frameType) {
        return switch (frameType) {
            case TASK -> NamedTextColor.GREEN;
            case CHALLENGE -> NamedTextColor.LIGHT_PURPLE;
            case GOAL -> NamedTextColor.YELLOW;
        };
    }

    /**
     * 全服广播
     */
    default void announceToChat(Player player, Achievement achievement) {
        Component message = Component.text()
            .append(Component.text(player.getName(), NamedTextColor.AQUA))
            .append(Component.text(" 完成了成就 ", NamedTextColor.WHITE))
            .append(Component.text("[", NamedTextColor.GRAY))
            .append(achievement.getTitle().color(getFrameColor(achievement.getFrameType())))
            .append(Component.text("]", NamedTextColor.GRAY))
            .build();

        Bukkit.broadcast(message);
    }

    /**
     * 私聊消息
     */
    default void sendPrivateMessage(Player player, Achievement achievement) {
        player.sendMessage(Component.text()
            .append(Component.text("* ", NamedTextColor.GOLD))
            .append(Component.text("你完成了成就：", NamedTextColor.YELLOW))
            .append(achievement.getTitle().color(getFrameColor(achievement.getFrameType())))
            .build());
    }

    /**
     * 播放音效
     */
    default void playSound(Player player, Achievement achievement) {
        Sound sound = switch (achievement.getFrameType()) {
            case TASK -> Sound.ENTITY_PLAYER_LEVELUP;
            case CHALLENGE -> Sound.UI_TOAST_CHALLENGE_COMPLETE;
            case GOAL -> Sound.ENTITY_ENDER_DRAGON_GROWL;
        };

        player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
    }

    /**
     * 发送大标题
     */
    default void sendTitle(Player player, Achievement achievement) {
        Component title = Component.text()
            .append(Component.text("* ", NamedTextColor.GOLD))
            .append(Component.text("成就达成", NamedTextColor.YELLOW, TextDecoration.BOLD))
            .append(Component.text(" *", NamedTextColor.GOLD))
            .build();

        Component subtitle = achievement.getTitle().color(getFrameColor(achievement.getFrameType()));

        player.showTitle(Title.title(
            title,
            subtitle,
            Title.Times.times(
                Duration.ofMillis(500),
                Duration.ofSeconds(3),
                Duration.ofMillis(1000)
            )
        ));
    }

    /**
     * 给予奖励
     */
    default void giveRewards(Player player, Achievement achievement) {
        Plugin plugin = getPlugin();

        // 经验奖励
        if (achievement.getExperience() > 0) {
            player.giveExp(achievement.getExperience());
        }

        // 其他奖励
        for (String reward : achievement.getRewards()) {
            parseAndGiveReward(player, reward);
        }
    }

    /**
     * 解析并给予奖励
     */
    default void parseAndGiveReward(Player player, String reward) {
        if (reward == null || reward.isBlank()) {
            return;
        }

        String[] parts = reward.split(":", 2);
        if (parts.length < 2) {
            return;
        }

        String type = parts[0].toLowerCase();
        String data = parts[1];

        try {
            switch (type) {
                case "money", "coins" -> giveMoney(player, data);
                case "item" -> giveItem(player, data);
                case "exp", "experience" -> giveExperience(player, data);
                case "command", "cmd" -> executeCommand(player, data);
                default -> player.sendMessage(Component.text("未知的奖励类型: " + type, NamedTextColor.RED));
            }
        } catch (Exception e) {
            player.sendMessage(Component.text("处理奖励时出错: " + reward, NamedTextColor.RED));
            Bukkit.getLogger().warning("Failed to give reward '" + reward + "' to " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * 给予金币
     */
    default void giveMoney(Player player, String amountStr) {
        try {
            double amount = Double.parseDouble(amountStr);
            if (amount > 0) {
                if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
                    net.milkbowl.vault.economy.Economy economy = getVaultEconomy();
                    if (economy != null) {
                        economy.depositPlayer(player, amount);
                        player.sendMessage(Component.text("+ " + amount + " 金币", NamedTextColor.GOLD));
                    }
                } else {
                    useInternalEconomy(player, amount);
                }
            }
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("无效的金额: " + amountStr, NamedTextColor.RED));
        }
    }

    /**
     * 使用内部经济系统。D-081: 真正调用内部 EconomyService.deposit，否则玩家收不到金币。
     */
    default void useInternalEconomy(Player player, double amount) {
        dev.starcore.starcore.foundation.economy.EconomyService es = getInternalEconomy();
        if (es != null) {
            es.deposit(player.getUniqueId(), java.math.BigDecimal.valueOf(amount));
        }
        player.sendMessage(Component.text("+ " + amount + " 金币", NamedTextColor.GOLD));
    }

    /**
     * D-081: 子类可注入内部 EconomyService；默认返回 null 时使用 Vault 不可用消息。
     */
    default dev.starcore.starcore.foundation.economy.EconomyService getInternalEconomy() {
        return null;
    }

    /**
     * 给予物品
     */
    default void giveItem(Player player, String itemData) {
        String[] parts = itemData.split(":");
        if (parts.length < 2) {
            return;
        }

        try {
            org.bukkit.Material material = org.bukkit.Material.valueOf(parts[0].toUpperCase());
            int amount = Integer.parseInt(parts[1]);

            org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(material, amount);

            // 处理附魔
            if (parts.length >= 4) {
                for (int i = 2; i < parts.length; i += 2) {
                    if (i + 1 < parts.length) {
                        String enchantName = parts[i].toUpperCase();
                        try {
                            int level = Integer.parseInt(parts[i + 1]);
                            org.bukkit.enchantments.Enchantment enchant =
                                org.bukkit.enchantments.Enchantment.getByName(enchantName);
                            if (enchant != null) {
                                // D-084: 限制附魔等级为该附魔的最大合法等级，避免成就注入超界附魔（如 sharpness 32767）
                                int maxLevel = enchant.getMaxLevel();
                                int clampedLevel = Math.min(Math.max(1, level), maxLevel);
                                item.addUnsafeEnchantment(enchant, clampedLevel);
                            }
                        } catch (NumberFormatException nfe) {
                            Bukkit.getLogger().warning("Invalid enchantment level: " + parts[i + 1]);
                        } catch (Exception e) {
                            Bukkit.getLogger().warning("Unknown enchantment: " + enchantName);
                        }
                    }
                }
            }

            // D-082: addItem 溢出物品应掉落在玩家脚下，避免物品直接消失
            java.util.Map<Integer, org.bukkit.inventory.ItemStack> overflow =
                player.getInventory().addItem(item);
            for (org.bukkit.inventory.ItemStack left : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
            player.sendMessage(Component.text("获得 " + amount + "x ", NamedTextColor.GREEN)
                .append(Component.translatable(material.translationKey())));

        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("无效的物品: " + itemData, NamedTextColor.RED));
        } catch (Exception e) {
            if (e instanceof NumberFormatException) {
                player.sendMessage(Component.text("无效的数量或等级", NamedTextColor.RED));
            } else {
                getPlugin().getLogger().warning("给予物品时发生错误: " + e.getMessage());
            }
        }
    }

    /**
     * 给予经验
     */
    default void giveExperience(Player player, String expStr) {
        try {
            int exp = Integer.parseInt(expStr);
            if (exp > 0) {
                player.giveExp(exp);
                player.sendMessage(Component.text("+ " + exp + " 经验", NamedTextColor.AQUA));
            }
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("无效的经验值: " + expStr, NamedTextColor.RED));
        }
    }

    /**
     * 安全执行命令
     */
    default void executeCommand(Player player, String command) {
        String finalCommand = command.replace("{player}", player.getName())
                                    .replace("{uuid}", player.getUniqueId().toString());

        String baseCommand = finalCommand.split("\\s+")[0].toLowerCase();

        if (baseCommand.startsWith("minecraft:")) {
            baseCommand = baseCommand.substring(10);
        }

        // D-083: 收紧白名单，移除 give/gamemode/execute/summon 等可被滥用造成 OP 的命令
        Set<String> ALLOWED_COMMANDS = Set.of(
            "tp", "teleport", "effect", "particle", "playsound",
            "title", "tellraw", "clear",
            "advancement", "experience", "xp", "broadcast", "say"
        );

        if (!ALLOWED_COMMANDS.contains(baseCommand)) {
            getPlugin().getLogger().warning(
                "§c[安全] 拦截非法成就命令: '" + baseCommand + "' " +
                "(玩家: " + player.getName() + ")"
            );
            player.sendMessage("§c成就奖励命令被安全系统拦截，请联系管理员");
            return;
        }

        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
        } catch (Exception e) {
            getPlugin().getLogger().warning(
                "成就命令执行失败: " + finalCommand + " - " + e.getMessage()
            );
        }
    }

    /**
     * 获取 Vault 经济系统
     */
    default net.milkbowl.vault.economy.Economy getVaultEconomy() {
        try {
            org.bukkit.plugin.RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> rsp =
                Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
            if (rsp != null) {
                return rsp.getProvider();
            }
        } catch (Exception e) {
            // Vault 未安装
        }
        return null;
    }
}
