package dev.starcore.starcore.social.simulation;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 声望事件自动触发服务
 *
 * 监听各种游戏事件，自动触发声望变化：
 * - PVP死亡
 * - 资源采集
 * - 社交互动
 * - 聊天友善度
 * - 钓鱼
 * - 制作
 * - 附魔
 *
 * 以及周期性奖励：
 * - 每日首次上线奖励
 * - 连续在线奖励
 * - 每周活跃奖励
 */
public class ReputationEventTrigger implements Listener {

    private final ReputationService reputationService;
    private final RelationshipNetwork relationshipNetwork;
    private final SocialInfluenceService influenceService;
    private final NewsPropagationService newsService;

    // 每日首次上线奖励追踪
    private final Map<UUID, LocalDate> dailyLoginRewards = new ConcurrentHashMap<>();

    // 连续在线时间追踪
    private final Map<UUID, Long> sessionStartTime = new ConcurrentHashMap<>();
    private final Map<UUID, Long> totalOnlineTimeToday = new ConcurrentHashMap<>();

    // 每周活跃度追踪
    private final Map<UUID, Set<LocalDate>> weeklyActivity = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> weeklyActivityScore = new ConcurrentHashMap<>();

    // 冷却追踪（防止事件刷声望）
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    // 配置参数
    private static final int DAILY_LOGIN_REWARD = 5;        // 每日首次上线奖励
    private static final int CONTINUOUS_ONLINE_THRESHOLD = 30; // 连续在线30分钟奖励
    private static final int CONTINUOUS_ONLINE_REWARD = 8;    // 连续在线奖励
    private static final int WEEKLY_ACTIVITY_REWARD = 15;    // 每周活跃奖励

    // 冷却时间（秒）
    private static final int BLOCK_BREAK_COOLDOWN = 60;
    private static final int FISH_COOLDOWN = 120;
    private static final int CRAFT_COOLDOWN = 180;
    private static final int ENCHANT_COOLDOWN = 300;
    private static final int CHAT_COOLDOWN = 30;

    // 资源采集声望配置
    private static final Set<Material> VALUABLE_ORES = Set.of(
        Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
        Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
        Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
        Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
        Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
        Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE
    );

    private static final Set<Material> RARE_RESOURCES = Set.of(
        Material.NETHER_QUARTZ_ORE,
        Material.ANCIENT_DEBRIS,
        Material.GLOWSTONE,
        Material.CRYING_OBSIDIAN
    );

    public ReputationEventTrigger(
            ReputationService reputationService,
            RelationshipNetwork relationshipNetwork,
            SocialInfluenceService influenceService,
            NewsPropagationService newsService
    ) {
        this.reputationService = reputationService;
        this.relationshipNetwork = relationshipNetwork;
        this.influenceService = influenceService;
        this.newsService = newsService;
    }

    // ==================== 玩家上线事件 ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 记录会话开始时间
        sessionStartTime.put(playerId, System.currentTimeMillis());

        // 初始化今日在线时间
        totalOnlineTimeToday.putIfAbsent(playerId, 0L);

        // 每日首次上线奖励
        checkDailyLoginReward(player);

        // 检查每周活跃
        checkWeeklyActivity(player);

        // 影响力增加
        influenceService.addInfluence(playerId, 10, "上线");

        // 检查好友在线
        var friends = relationshipNetwork.getFriends(playerId);
        if (!friends.isEmpty()) {
            int onlineCount = 0;
            for (UUID friendId : friends) {
                if (Bukkit.getPlayer(friendId) != null) {
                    onlineCount++;
                }
            }
            if (onlineCount > 0) {
                player.sendMessage("§a你的 " + friends.size() + " 位朋友中有 " + onlineCount + " 位在线！");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 统计今日在线时间
        Long startTime = sessionStartTime.remove(playerId);
        if (startTime != null) {
            long sessionDuration = System.currentTimeMillis() - startTime;
            totalOnlineTimeToday.merge(playerId, sessionDuration, Long::sum);

            // 检查是否达到连续在线奖励阈值
            checkContinuousOnlineReward(player, totalOnlineTimeToday.getOrDefault(playerId, 0L));
        }

        // 影响力减少
        influenceService.addInfluence(playerId, -5, "下线");

        // 清理会话数据
        cooldowns.remove(playerId);
    }

    // ==================== PVP死亡事件 ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        UUID victimId = victim.getUniqueId();

        // 检查是否有攻击者（PVP）
        Player killer = victim.getKiller();
        if (killer != null && killer != victim) {
            UUID killerId = killer.getUniqueId();

            // 检查是否是同阵营/国家（恶意击杀惩罚）
            if (isSameFaction(killerId, victimId)) {
                // 同阵营击杀惩罚
                reputationService.modifyReputation(killerId, -8, ReputationService.ReputationReason.ATTACK_PLAYER);
                killer.sendMessage("§c警告: 你攻击了同阵营成员，声望下降！");
            } else {
                // 正常PVP击杀
                reputationService.modifyReputation(killerId, 3, ReputationService.ReputationReason.WIN_BATTLE);
                killer.sendMessage("§aPVP击杀获得 3 点能力声望！");
            }

            // 增加关系强度（互相击杀会降低关系）
            relationshipNetwork.decreaseStrength(killerId, victimId, 10);

            // 发布新闻
            newsService.publishNews(
                killer.getName() + " 在PVP中击败了 " + victim.getName(),
                killer.getName() + " 在战斗中战胜了 " + victim.getName() + "。",
                killerId,
                NewsPropagationService.NewsCategory.CONFLICT
            );
        } else {
            // 非PVP死亡（怪物、环境等）
            reputationService.modifyReputation(victimId, -2, ReputationService.ReputationReason.ATTACK_PLAYER);
        }
    }

    // ==================== 资源采集事件 ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(PlayerInteractEvent event) {
        // 这个事件在方块被破坏时触发
        // 注意: 实际应该用 BlockBreakEvent，但这里用 PlayerInteractEvent 作为替代
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreakActual(org.bukkit.event.block.BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        Material blockType = event.getBlock().getType();

        // 检查冷却
        if (isOnCooldown(playerId, "block_break", BLOCK_BREAK_COOLDOWN)) {
            return;
        }

        // 稀有矿石采集
        if (VALUABLE_ORES.contains(blockType)) {
            reputationService.modifyReputation(playerId, 2, ReputationService.ReputationReason.RESEARCH_TECH);
            setCooldown(playerId, "block_break");
            return;
        }

        // 极稀有资源
        if (RARE_RESOURCES.contains(blockType)) {
            reputationService.modifyReputation(playerId, 5, ReputationService.ReputationReason.RESEARCH_TECH);
            influenceService.addInfluence(playerId, 3, "稀有资源采集");
            setCooldown(playerId, "block_break");
            return;
        }

        // 普通资源（概率触发）
        if (ThreadLocalRandom.current().nextDouble() < 0.1) { // 10%概率
            reputationService.modifyReputation(playerId, 1, ReputationService.ReputationReason.COMPLETE_QUEST);
            setCooldown(playerId, "block_break");
        }
    }

    // ==================== 钓鱼事件 ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 检查冷却
        if (isOnCooldown(playerId, "fishing", FISH_COOLDOWN)) {
            return;
        }

        // 只有成功钓到物品才给奖励
        if (event.getCaught() != null) {
            // 钓鱼是休闲活动，增加魅力
            reputationService.modifyReputation(playerId, 1, ReputationService.ReputationReason.GIVE_GIFT);
            setCooldown(playerId, "fishing");

            // 稀有钓鱼给予额外奖励
            Entity caught = event.getCaught();
            if (caught instanceof Player) {
                // 钓到玩家（恶作剧）
                reputationService.modifyReputation(playerId, -3, ReputationService.ReputationReason.GRIEFING);
                player.sendMessage("§c将玩家拉出水面可能被视为恶作剧！");
            }
        }
    }

    // ==================== 制作事件 ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();

        // 检查冷却
        if (isOnCooldown(playerId, "craft", CRAFT_COOLDOWN)) {
            return;
        }

        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType() == Material.AIR) {
            return;
        }

        // 制作有价值物品给予能力声望
        reputationService.modifyReputation(playerId, 2, ReputationService.ReputationReason.COMPLETE_QUEST);
        influenceService.addInfluence(playerId, 2, "制作物品");
        setCooldown(playerId, "craft");

        // 稀有制作给予额外奖励
        if (isValuableCraft(result.getType())) {
            reputationService.modifyReputation(playerId, 3, ReputationService.ReputationReason.RESEARCH_TECH);
        }
    }

    // ==================== 附魔事件 ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        UUID playerId = player.getUniqueId();

        // 检查冷却
        if (isOnCooldown(playerId, "enchant", ENCHANT_COOLDOWN)) {
            return;
        }

        // 附魔给予能力声望
        int enchantLevel = event.getExpLevelCost();
        int reward = Math.min(enchantLevel / 2, 5); // 最高5点

        reputationService.modifyReputation(playerId, reward, ReputationService.ReputationReason.RESEARCH_TECH);
        influenceService.addInfluence(playerId, reward, "附魔");
        setCooldown(playerId, "enchant");
    }

    // ==================== 社交互动事件 ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 只处理右键交互
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR &&
            event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // 检查玩家是否持有物品
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            return;
        }

        // 赠送礼物行为
        if (isGiftItem(item.getType())) {
            // 检查附近是否有玩家
            for (Entity entity : player.getNearbyEntities(3, 3, 3)) {
                if (entity instanceof Player target) {
                    // 赠送礼物给其他玩家
                    reputationService.modifyReputation(playerId, 5, ReputationService.ReputationReason.GIVE_GIFT);
                    reputationService.modifyReputation(target.getUniqueId(), 3, ReputationService.ReputationReason.GIVE_GIFT);

                    // 增加关系强度
                    relationshipNetwork.increaseStrength(playerId, target.getUniqueId(), 10);

                    player.sendMessage("§a你向 " + target.getName() + " 赠送了礼物，声望提升！");
                    target.sendMessage("§a" + player.getName() + " 向你赠送了礼物！");

                    // 发布社交新闻
                    newsService.publishNews(
                        player.getName() + " 向 " + target.getName() + " 赠送了礼物",
                        "慷慨的 " + player.getName() + " 向 " + target.getName() + " 展示了友谊。",
                        playerId,
                        NewsPropagationService.NewsCategory.SOCIAL
                    );
                    break;
                }
            }
        }

        // 帮助物品（如药水、食物给其他玩家）
        if (isHelpfulItem(item.getType())) {
            for (Entity entity : player.getNearbyEntities(3, 3, 3)) {
                if (entity instanceof Player target && target != player) {
                    // 检查目标血量或状态
                    if (target.getHealth() < target.getMaxHealth() / 2) {
                        reputationService.modifyReputation(playerId, 8, ReputationService.ReputationReason.HELP_PLAYER);
                        relationshipNetwork.increaseStrength(playerId, target.getUniqueId(), 15);
                        player.sendMessage("§a你帮助了 " + target.getName() + "，声望提升！");
                        break;
                    }
                }
            }
        }
    }

    // ==================== 聊天友善度事件 ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String message = event.getMessage().toLowerCase();

        // 检查冷却
        if (isOnCooldown(playerId, "chat", CHAT_COOLDOWN)) {
            return;
        }

        int chatScore = calculateChatKindness(message);

        if (chatScore > 0) {
            // 友善聊天
            reputationService.modifyReputation(playerId, chatScore, ReputationService.ReputationReason.MEDIATE_DISPUTE);
            setCooldown(playerId, "chat");
        } else if (chatScore < 0) {
            // 不友善聊天
            reputationService.modifyReputation(playerId, chatScore, ReputationService.ReputationReason.SPAM);
            setCooldown(playerId, "chat");
        }
    }

    // ==================== 定时检查任务 ====================

    /**
     * 检查每日首次上线奖励
     */
    private void checkDailyLoginReward(Player player) {
        UUID playerId = player.getUniqueId();
        LocalDate today = LocalDate.now();

        if (!today.equals(dailyLoginRewards.get(playerId))) {
            dailyLoginRewards.put(playerId, today);

            // 给予每日首次上线奖励
            reputationService.modifyReputation(playerId, DAILY_LOGIN_REWARD, ReputationService.ReputationReason.HELP_PLAYER);
            influenceService.addInfluence(playerId, 5, "每日上线");

            player.sendMessage("§6[声望] §a每日首次上线奖励: +" + DAILY_LOGIN_REWARD + " 道德声望！");
        }
    }

    /**
     * 检查连续在线奖励
     */
    private void checkContinuousOnlineReward(Player player, long totalMilliseconds) {
        UUID playerId = player.getUniqueId();

        // 每30分钟检查一次
        long thresholdMs = CONTINUOUS_ONLINE_THRESHOLD * 60 * 1000L;
        if (totalMilliseconds >= thresholdMs) {
            reputationService.modifyReputation(playerId, CONTINUOUS_ONLINE_REWARD, ReputationService.ReputationReason.VOLUNTEER_WORK);
            player.sendMessage("§6[声望] §a连续在线 " + CONTINUOUS_ONLINE_THRESHOLD + " 分钟奖励: +" + CONTINUOUS_ONLINE_REWARD + " 道德声望！");
        }
    }

    /**
     * 检查每周活跃奖励
     */
    private void checkWeeklyActivity(Player player) {
        UUID playerId = player.getUniqueId();
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(java.time.DayOfWeek.MONDAY);

        // 初始化本周活跃记录
        weeklyActivity.putIfAbsent(playerId, new HashSet<>());
        weeklyActivityScore.putIfAbsent(playerId, 0);

        // 记录今日活跃
        Set<LocalDate> activity = weeklyActivity.get(playerId);
        if (!activity.contains(today)) {
            activity.add(today);
            weeklyActivityScore.merge(playerId, 1, Integer::sum);

            // 检查是否达到本周活跃天数要求（5天）
            if (activity.size() >= 5 && today.getDayOfWeek() != java.time.DayOfWeek.MONDAY) {
                // 检查是否已经领取过本周奖励
                String weekKey = weekStart.toString();
                if (!isOnCooldown(playerId, "weekly_" + weekKey, Integer.MAX_VALUE)) {
                    reputationService.modifyReputation(playerId, WEEKLY_ACTIVITY_REWARD, ReputationService.ReputationReason.VOLUNTEER_WORK);
                    influenceService.addInfluence(playerId, 10, "每周活跃");
                    setCooldown(playerId, "weekly_" + weekKey, 86400 * 7); // 7天冷却

                    player.sendMessage("§6[声望] §a本周活跃奖励: +" + WEEKLY_ACTIVITY_REWARD + " 道德声望！");
                }
            }
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 检查是否同阵营
     */
    private boolean isSameFaction(UUID player1, UUID player2) {
        // 检查国家关系
        try {
            var nationService = getNationService();
            if (nationService != null) {
                UUID nation1 = getPlayerNation(player1);
                UUID nation2 = getPlayerNation(player2);
                return nation1 != null && nation1.equals(nation2);
            }
        } catch (Exception e) {
            // NationService 不可用
        }

        // 检查派系关系
        try {
            Object clanManagerObj = getClanManager();
            if (clanManagerObj != null) {
                java.lang.reflect.Method getClanMethod = clanManagerObj.getClass().getMethod("getPlayerClan", UUID.class);
                Object clan1 = getClanMethod.invoke(clanManagerObj, player1);
                Object clan2 = getClanMethod.invoke(clanManagerObj, player2);
                if (clan1 != null && clan1.equals(clan2)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // ClanManager 不可用
        }

        return false;
    }

    private Object getNationService() {
        // 通过反射获取 NationService
        try {
            var registration = Bukkit.getServicesManager().getRegistration(
                Class.forName("dev.starcore.starcore.module.nation.NationService")
            );
            return registration != null ? registration.getProvider() : null;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private UUID getPlayerNation(UUID playerId) {
        try {
            Object nationService = getNationService();
            if (nationService != null) {
                java.lang.reflect.Method method = nationService.getClass().getMethod("getPlayerNation", UUID.class);
                Object nation = method.invoke(nationService, playerId);
                if (nation != null) {
                    java.lang.reflect.Method getId = nation.getClass().getMethod("getId");
                    return (UUID) getId.invoke(nation);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private Object getClanManager() {
        try {
            return Bukkit.getPluginManager().getPlugin("StarCore")
                .getClass().getField("clanManager").get(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 判断是否是有价值的制作物品
     */
    private boolean isValuableCraft(Material material) {
        return Set.of(
            Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE,
            Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
            Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
            Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
            Material.ENCHANTED_GOLDEN_APPLE,
            Material.TOTEM_OF_UNDYING,
            Material.ELYTRA
        ).contains(material);
    }

    /**
     * 判断是否是礼物物品
     */
    private boolean isGiftItem(Material material) {
        return Set.of(
            Material.CAKE, Material.COOKIE,
            Material.GOLDEN_APPLE, Material.ENCHANTED_GOLDEN_APPLE,
            Material.ALLIUM, Material.AZALEA, Material.FERN, Material.LARGE_FERN, Material.FLOWER_POT,
            Material.COOKED_BEEF, Material.COOKED_PORKCHOP, Material.COOKED_CHICKEN, Material.COOKED_MUTTON
        ).contains(material);
    }

    /**
     * 判断是否是有帮助的物品
     */
    private boolean isHelpfulItem(Material material) {
        return Set.of(
            Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION,
            Material.MILK_BUCKET,
            Material.GOLDEN_APPLE, Material.ENCHANTED_GOLDEN_APPLE,
            Material.CHORUS_FRUIT,
            Material.HONEY_BOTTLE,
            Material.COOKED_BEEF, Material.COOKED_PORKCHOP,
            Material.GOLDEN_CARROT
        ).contains(material);
    }

    /**
     * 计算聊天友善度
     */
    private int calculateChatKindness(String message) {
        // 友善词汇
        Set<String> kindWords = Set.of(
            "谢谢", "感谢", "你好", "您好", "帮忙", "帮帮我",
            "加油", "鼓励", "恭喜", "祝贺", "祝福", "吉祥",
            "thx", "ty", "gg", "glhf", "well played",
            "please", "thanks", "good job", "nice", "congrats"
        );

        // 不友善词汇
        Set<String> unkindWords = Set.of(
            "傻", "笨", "垃圾", "废物", "滚", "草", "操",
            "fuck", "shit", "idiot", "stupid", "loser", "kys",
            "骂", "喷", "喷子", "外挂", "作弊"
        );

        int score = 0;
        for (String word : kindWords) {
            if (message.contains(word)) {
                score += 2;
            }
        }
        for (String word : unkindWords) {
            if (message.contains(word)) {
                score -= 3;
            }
        }

        // 限制范围
        return Math.max(-5, Math.min(5, score));
    }

    /**
     * 检查冷却
     */
    private boolean isOnCooldown(UUID playerId, String action, int cooldownSeconds) {
        Map<String, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns == null) {
            return false;
        }

        Long lastAction = playerCooldowns.get(action);
        if (lastAction == null) {
            return false;
        }

        long elapsed = (System.currentTimeMillis() - lastAction) / 1000;
        return elapsed < cooldownSeconds;
    }

    /**
     * 设置冷却
     */
    private void setCooldown(UUID playerId, String action) {
        setCooldown(playerId, action, BLOCK_BREAK_COOLDOWN);
    }

    private void setCooldown(UUID playerId, String action, int cooldownSeconds) {
        cooldowns.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
            .put(action, System.currentTimeMillis() + (cooldownSeconds * 1000L));
    }

    // ==================== 公共访问方法 ====================

    /**
     * 获取玩家今日在线时间（分钟）
     */
    public int getTodayOnlineMinutes(UUID playerId) {
        long ms = totalOnlineTimeToday.getOrDefault(playerId, 0L);
        return (int) (ms / 60000);
    }

    /**
     * 获取玩家本周活跃天数
     */
    public int getWeeklyActivityDays(UUID playerId) {
        return weeklyActivity.getOrDefault(playerId, Set.of()).size();
    }

    /**
     * 检查今日是否已领取上线奖励
     */
    public boolean hasReceivedDailyReward(UUID playerId) {
        return today().equals(dailyLoginRewards.get(playerId));
    }

    private LocalDate today() {
        return LocalDate.now();
    }
}
