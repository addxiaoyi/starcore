package dev.starcore.starcore.social.simulation;

import java.util.concurrent.ThreadLocalRandom;
import dev.starcore.starcore.event.player.PlayerAttackPlayerEvent;
import dev.starcore.starcore.event.player.PlayerHelpPlayerEvent;
import dev.starcore.starcore.foundation.util.RandomProvider;
import dev.starcore.starcore.social.simulation.SocialEventScheduler.EventEffects;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 社会模拟事件监听器
 *
 * 监听所有与社会模拟相关的事件:
 * - 玩家互动事件 (加入/退出/帮助/攻击)
 * - 经验与技能事件 (升级/经验获取)
 * - 生存事件 (死亡/击杀)
 * - 经济事件 (交易/制作/钓鱼)
 * - 成就事件
 * - 作弊检测
 *
 * 并与所有社会服务联动:
 * - ReputationService: 声望管理
 * - RelationshipNetwork: 关系网络
 * - SocialInfluenceService: 影响力
 * - NewsPropagationService: 新闻传播
 * - SocialEventScheduler: 社会事件
 * - CultureService: 文化服务
 * - SocialActivityService: 社交活动
 * - GossipService: 八卦服务
 */
public class SocialSimulationListener implements Listener {

    private final ReputationService reputationService;
    private final RelationshipNetwork relationshipNetwork;
    private final SocialInfluenceService influenceService;
    private final NewsPropagationService newsService;
    private final SocialEventScheduler eventScheduler;
    private final CultureService cultureService;
    private final SocialActivityService activityService;
    private final GossipService gossipService;

    private JavaPlugin registeredPlugin;
    private final Set<UUID> recentlyHelped = new HashSet<>();
    private final Map<UUID, Integer> killStreak = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastDeathTime = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> playerCraftedItems = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerFishCount = new ConcurrentHashMap<>();

    // 作弊检测配置
    private static final int MAX_EXP_RATE = 100; // 每秒最大经验获取
    private static final int FISH_INTERVAL_MIN = 500; // 钓鱼最小间隔(ms)
    private static final Set<Material> CRAFTABLE_ITEMS = EnumSet.noneOf(Material.class);

    static {
        // 初始化可制作物品列表
        for (Material mat : Material.values()) {
            if (mat.isItem()) {
                CRAFTABLE_ITEMS.add(mat);
            }
        }
    }

    public SocialSimulationListener(
            ReputationService reputationService,
            RelationshipNetwork relationshipNetwork,
            SocialInfluenceService influenceService,
            NewsPropagationService newsService,
            SocialEventScheduler eventScheduler,
            CultureService cultureService,
            SocialActivityService activityService,
            GossipService gossipService
    ) {
        this.reputationService = reputationService;
        this.relationshipNetwork = relationshipNetwork;
        this.influenceService = influenceService;
        this.newsService = newsService;
        this.eventScheduler = eventScheduler;
        this.cultureService = cultureService;
        this.activityService = activityService;
        this.gossipService = gossipService;
    }

    public void register(JavaPlugin plugin) {
        this.registeredPlugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void unregister() {
        if (registeredPlugin != null) {
            PlayerJoinEvent.getHandlerList().unregisterAll(registeredPlugin);
            PlayerQuitEvent.getHandlerList().unregisterAll(registeredPlugin);
            PlayerExpChangeEvent.getHandlerList().unregisterAll(registeredPlugin);
            InventoryClickEvent.getHandlerList().unregisterAll(registeredPlugin);
            PlayerDeathEvent.getHandlerList().unregisterAll(registeredPlugin);
            EntityDeathEvent.getHandlerList().unregisterAll(registeredPlugin);
            CraftItemEvent.getHandlerList().unregisterAll(registeredPlugin);
            PlayerFishEvent.getHandlerList().unregisterAll(registeredPlugin);
        }
    }

    // ==================== 基础事件 ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 增加上线影响力
        influenceService.addInfluence(playerId, 10, "上线");

        // 检查是否有好友上线
        var friends = relationshipNetwork.getFriends(playerId);
        if (!friends.isEmpty()) {
            player.sendMessage("§a你的 " + friends.size() + " 位朋友在线！");

            // 通知好友
            for (UUID friendId : friends) {
                Player friend = Bukkit.getPlayer(friendId);
                if (friend != null && friend.isOnline()) {
                    friend.sendMessage("§a[社交] §e" + player.getName() + " §a上线了！");
                }
            }
        }

        // 检查社会动荡 - 高声望玩家回归
        int rep = reputationService.getReputation(playerId);
        if (rep > 100) {
            SocialInfluenceService.SocialStatus status = influenceService.getStatus(playerId);
            if (status.ordinal() >= SocialInfluenceService.SocialStatus.KNOWN.ordinal()) {
                // 高声望玩家回归广播
                newsService.publishNews(
                    player.getName() + " 回归了！",
                    "备受尊敬的 " + player.getName() + " 重新回到了服务器！",
                    playerId,
                    NewsPropagationService.NewsCategory.SOCIAL
                );
            }
        }

        // 恢复击杀 streak
        if (!killStreak.containsKey(playerId)) {
            killStreak.put(playerId, 0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        // 增加下线影响力衰减
        influenceService.addInfluence(playerId, -5, "下线");

        // 通知好友
        var friends = relationshipNetwork.getFriends(playerId);
        for (UUID friendId : friends) {
            Player friend = Bukkit.getPlayer(friendId);
            if (friend != null && friend.isOnline()) {
                // 检查关系强度决定是否通知
                var rel = relationshipNetwork.getRelationship(friendId, playerId);
                if (rel != null && rel.strength() > 50) {
                    friend.sendMessage("§7[社交] §e" + event.getPlayer().getName() + " §7下线了");
                }
            }
        }

        // 清理连杀记录
        killStreak.remove(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerHelpPlayer(PlayerHelpPlayerEvent event) {
        Player helper = event.getHelper();
        Player helped = event.getTarget();
        UUID helperId = helper.getUniqueId();
        UUID helpedId = helped.getUniqueId();

        // 获取事件加成
        EventEffects effects = eventScheduler.getActiveEffects();

        // 增加帮助者的声望 (带节日加成)
        int moralGain = (int) (5 * (1 + effects.reputationBonus()));
        reputationService.addMoral(helperId, moralGain, "帮助玩家: " + helped.getName());

        // 增加关系强度
        relationshipNetwork.increaseStrength(helperId, helpedId, 5);

        // 建立朋友关系如果还没有
        var existingRel = relationshipNetwork.getRelationship(helperId, helpedId);
        if (existingRel == null || existingRel.type() == RelationshipNetwork.RelationshipType.STRANGER) {
            relationshipNetwork.setRelationship(helperId, helpedId,
                RelationshipNetwork.RelationshipType.FRIEND, 30);
        }

        // 帮助者获得影响力
        int influenceGain = (int) (5 * (1 + effects.influenceBonus()));
        influenceService.addInfluence(helperId, influenceGain, "助人为乐");

        // 被帮助者增加魅力感知
        reputationService.addCharisma(helpedId, 2, "接受他人帮助");

        // 发布新闻
        newsService.publishNews(
            helper.getName() + " 帮助了 " + helped.getName(),
            helper.getName() + " 向 " + helped.getName() + " 伸出了援手，展现了互助精神。",
            helperId,
            NewsPropagationService.NewsCategory.SOCIAL
        );

        // 创建八卦 (正面)
        gossipService.createGossip(helperId,
            helper.getName() + " 帮助了 " + helped.getName(),
            GossipService.GossipTopic.HEROISM);

        // 防刷标记
        recentlyHelped.add(helperId);
        Bukkit.getScheduler().runTaskLater(
            Bukkit.getPluginManager().getPlugins()[0],
            () -> recentlyHelped.remove(helperId), 600L); // 30秒冷却
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerAttackPlayer(PlayerAttackPlayerEvent event) {
        Player attacker = event.getAttacker();
        Player victim = event.getTarget();
        UUID attackerId = attacker.getUniqueId();
        UUID victimId = victim.getUniqueId();

        // 获取事件加成
        EventEffects effects = eventScheduler.getActiveEffects();

        // 减少攻击者声望 (节日期间减少惩罚)
        int repLoss = (int) (-10 * (1 + effects.reputationBonus() * 0.5));
        reputationService.modifyReputation(
            attackerId,
            repLoss,
            ReputationService.ReputationReason.ATTACK_PLAYER
        );

        // 减少关系强度
        relationshipNetwork.decreaseStrength(attackerId, victimId, 20);

        // 可能转为敌对关系
        var rel = relationshipNetwork.getRelationship(attackerId, victimId);
        if (rel != null && rel.strength() > 0) {
            relationshipNetwork.setRelationship(attackerId, victimId,
                RelationshipNetwork.RelationshipType.ENEMY, -30);
        }

        // 受害者获得少量道德声望 (被攻击的受害者)
        reputationService.addMoral(victimId, 3, "被攻击但保持克制");

        // 发布新闻
        newsService.publishNews(
            attacker.getName() + " 攻击了 " + victim.getName(),
            "一场冲突发生在 " + attacker.getName() + " 和 " + victim.getName() + " 之间。",
            attackerId,
            NewsPropagationService.NewsCategory.MILITARY
        );

        // 创建八卦 (负面)
        gossipService.createGossip(attackerId,
            "听说 " + attacker.getName() + " 又在欺负人了",
            GossipService.GossipTopic.SCANDAL);

        // 社会动荡检测
        checkConflictUnrest(attackerId);
    }

    // ==================== 经验事件 ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerExpChange(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        int expGained = event.getAmount();

        if (expGained <= 0) return;

        // 获取事件加成
        EventEffects effects = eventScheduler.getActiveEffects();

        // 经验获取时增加能力声望 (基于获取量)
        int abilityGain = expGained / 10;
        if (abilityGain > 0) {
            int finalGain = (int) (abilityGain * (1 + effects.reputationBonus()));
            reputationService.addAbility(playerId, finalGain, "获得经验: " + expGained);
        }

        // 高额经验获取时增加影响力
        if (expGained > 50) {
            int influenceGain = expGained / 20;
            influenceService.addInfluence(playerId, influenceGain, "高效学习");

            // 可能触发新闻
            if (expGained > 200 && ThreadLocalRandom.current().nextDouble() < 0.1) {
                newsService.publishNews(
                    player.getName() + " 取得了巨大进步！",
                    player.getName() + " 获得了大量经验，展现出惊人的学习能力！",
                    playerId,
                    NewsPropagationService.NewsCategory.SOCIAL
                );
            }
        }

        // 检查升级
        int level = player.getLevel();
        if (level > 0 && expGained >= player.getExpToLevel()) {
            // 升级时额外奖励
            influenceService.addInfluence(playerId, 5, "升级");

            Player randomFriend = getRandomOnlineFriend(playerId);
            if (randomFriend != null) {
                // 分享喜悦给朋友
                relationshipNetwork.increaseStrength(playerId, randomFriend.getUniqueId(), 2);
            }
        }
    }

    // ==================== 作弊检测 ====================

    private final Map<UUID, Long> lastExpTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> recentExpSum = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID playerId = player.getUniqueId();

        // 检测异常行为
        if (event.isShiftClick()) {
            // 检测批量刷物品
            String inventoryName = event.getView().getTitle();

            // 如果是创造模式玩家在操作...跳过检测
            if (player.getGameMode() == GameMode.CREATIVE) return;

            // 检测可疑的快速点击
            // (实际作弊检测逻辑需要更复杂的实现)
        }

        // 检测铁砧/锻造台异常使用
        if (event.getInventory().getType() == InventoryType.ANVIL) {
            // 检测不正常的附魔操作
            // 可以记录并进行后续分析
        }
    }

    /**
     * 检测经验作弊
     */
    private void detectExpCheat(Player player, int expGained) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();

        // 如果是管理员或创造模式，跳过检测
        if (player.hasPermission("starcore.admin") ||
            player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        // 简单的时间窗口检测
        Long lastTime = lastExpTime.get(playerId);
        if (lastTime != null) {
            long delta = now - lastTime;

            // 如果经验获取异常频繁
            if (delta < 100 && expGained > 20) {
                // 可能是作弊
                Bukkit.getScheduler().runTask(
                    Bukkit.getPluginManager().getPlugins()[0],
                    () -> {
                        player.sendMessage("§c[警告] 检测到异常的经验获取行为");

                        // 扣除异常经验
                        if (player.getTotalExperience() >= expGained) {
                            player.setTotalExperience(player.getTotalExperience() - expGained);
                        }
                    }
                );

                // 记录到声望
                reputationService.modifyReputation(playerId, -20,
                    ReputationService.ReputationReason.GRIEFING);

                // 发布新闻
                newsService.publishNews(
                    player.getName() + " 被检测到作弊行为",
                    player.getName() + " 因异常行为被系统警告。",
                    null,
                    NewsPropagationService.NewsCategory.SCANDAL
                );
            }
        }

        lastExpTime.put(playerId, now);
    }

    // ==================== 死亡事件 ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        UUID victimId = victim.getUniqueId();

        // 获取事件加成
        EventEffects effects = eventScheduler.getActiveEffects();

        // 重置连杀
        killStreak.put(victimId, 0);

        // 记录死亡时间
        lastDeathTime.put(victimId, System.currentTimeMillis());

        // 检查是否有攻击者
        Player attacker = victim.getKiller();
        if (attacker != null) {
            UUID attackerId = attacker.getUniqueId();

            // 攻击者获得声望
            int battleGain = (int) (10 * (1 + effects.reputationBonus()));
            reputationService.addAbility(attackerId, battleGain, "击败: " + victim.getName());

            // 更新攻击者连杀
            int streak = killStreak.getOrDefault(attackerId, 0) + 1;
            killStreak.put(attackerId, streak);

            // 连杀奖励
            if (streak > 3) {
                influenceService.addInfluence(attackerId, streak, "连杀: " + streak);

                if (streak == 5) {
                    // 五杀新闻
                    newsService.publishNews(
                        attacker.getName() + " 完成五杀！",
                        attacker.getName() + " 以 " + streak + " 连杀震惊全场！",
                        attackerId,
                        NewsPropagationService.NewsCategory.MILITARY
                    );

                    // 创建八卦
                    gossipService.createGossip(attackerId,
                        attacker.getName() + " 刚刚完成了五杀！",
                        GossipService.GossipTopic.BATTLE);
                }
            }

            // 攻击者关系变化
            relationshipNetwork.decreaseStrength(attackerId, victimId, 15);

            // 被杀者惩罚
            int penalty = (int) (-5 * (1 + effects.reputationBonus() * 0.5));
            reputationService.modifyReputation(victimId, penalty,
                ReputationService.ReputationReason.ATTACK_PLAYER);

            // 可能的敌对关系
            var rel = relationshipNetwork.getRelationship(victimId, attackerId);
            if (rel == null || rel.strength() > -30) {
                relationshipNetwork.decreaseStrength(victimId, attackerId, 10);
            }
        } else {
            // 非玩家导致的死亡
            int penalty = (int) (-3 * (1 + effects.reputationBonus() * 0.3));
            reputationService.modifyReputation(victimId, penalty,
                ReputationService.ReputationReason.ATTACK_PLAYER);
        }

        // 检查社会动荡
        checkDeathUnrest(victimId);
    }

    // ==================== 击杀事件 ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityKill(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        UUID killerId = killer.getUniqueId();

        // 获取事件加成
        EventEffects effects = eventScheduler.getActiveEffects();

        // 实体类型奖励
        int baseReward = getEntityReward(event.getEntity());

        // 击杀稀有实体奖励
        if (event.getEntity() instanceof EnderDragon) {
            // 击杀末影龙
            influenceService.addInfluence(killerId, 500, "击杀末影龙");
            reputationService.addAbility(killerId, 50, "击杀末影龙");

            newsService.publishNews(
                killer.getName() + " 击败了末影龙！",
                killer.getName() + " 击败了沉睡的末影龙，成为了服务器传奇！",
                killerId,
                NewsPropagationService.NewsCategory.HEROIC
            );

        } else if (event.getEntity() instanceof Wither) {
            // 击杀凋零
            influenceService.addInfluence(killerId, 300, "击杀凋零");
            reputationService.addAbility(killerId, 30, "击杀凋零");

        } else if (event.getEntity() instanceof ElderGuardian) {
            // 击杀远古守卫者
            influenceService.addInfluence(killerId, 50, "击杀远古守卫者");
            reputationService.addAbility(killerId, 10, "击杀远古守卫者");
        }

        // 普通实体击杀增加能力声望
        int reward = (int) (baseReward * (1 + effects.reputationBonus()));
        if (reward > 0) {
            reputationService.addAbility(killerId, reward, "击杀: " + event.getEntityType().name());
        }
    }

    private int getEntityReward(Entity entity) {
        if (entity instanceof EnderDragon) return 100;
        if (entity instanceof Wither) return 50;
        if (entity instanceof ElderGuardian) return 20;
        if (entity instanceof Boss) return 15;
        if (entity instanceof Monster) return 3;
        if (entity instanceof Animals) return 1;
        return 0;
    }

    // ==================== 制作事件 ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID playerId = player.getUniqueId();

        Material item = event.getCurrentItem() != null ?
            event.getCurrentItem().getType() : null;

        if (item == null || item == Material.AIR) return;

        // 记录制作物品
        playerCraftedItems.computeIfAbsent(playerId, k -> new HashSet<>())
            .add(item.name());

        // 获取事件加成
        EventEffects effects = eventScheduler.getActiveEffects();

        // 制作增加能力声望
        int gain = 1;
        reputationService.addAbility(playerId, gain, "制作: " + getItemName(item));

        // 高价值物品制作奖励
        if (isValuableItem(item)) {
            int influenceGain = (int) (5 * (1 + effects.influenceBonus()));
            influenceService.addInfluence(playerId, influenceGain, "制作珍贵物品");

            // 通知社交圈
            Set<UUID> friends = relationshipNetwork.getFriends(playerId);
            for (UUID friendId : friends) {
                Player friend = Bukkit.getPlayer(friendId);
                if (friend != null && ThreadLocalRandom.current().nextDouble() < 0.1) {
                    friend.sendMessage("§6[社交] §e" + player.getName() + " §6制作了 " + getItemName(item));
                }
            }
        }

        // 检查成就进度
        int craftCount = playerCraftedItems.get(playerId).size();
        if (craftCount == 10) {
            checkAndAwardCraftAchievement(playerId, "初级工匠", 10);
        } else if (craftCount == 50) {
            checkAndAwardCraftAchievement(playerId, "中级工匠", 50);
        } else if (craftCount == 100) {
            checkAndAwardCraftAchievement(playerId, "高级工匠", 100);
        }
    }

    private boolean isValuableItem(Material item) {
        return switch (item) {
            case DIAMOND_HELMET, DIAMOND_CHESTPLATE, DIAMOND_LEGGINGS, DIAMOND_BOOTS,
                 DIAMOND_SWORD, DIAMOND_PICKAXE -> true;
            case NETHERITE_HELMET, NETHERITE_CHESTPLATE, NETHERITE_LEGGINGS, NETHERITE_BOOTS,
                 NETHERITE_SWORD, NETHERITE_PICKAXE -> true;
            case ENCHANTED_GOLDEN_APPLE, MUSIC_DISC_13, MUSIC_DISC_CAT,
                 ELYTRA, SHULKER_BOX -> true;
            default -> false;
        };
    }

    private String getItemName(Material item) {
        String name = item.name().toLowerCase().replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    private void checkAndAwardCraftAchievement(UUID playerId, String achievement, int count) {
        // 这里应该调用成就系统
        influenceService.addInfluence(playerId, count, achievement);
        reputationService.addAbility(playerId, count / 2, achievement);

        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.sendMessage("§6[成就] §e恭喜获得成就: " + achievement);
        }
    }

    // ==================== 钓鱼事件 ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFish(PlayerFishEvent event) {
        if (!(event.getHook().getShooter() instanceof Player player)) return;
        UUID playerId = player.getUniqueId();

        PlayerFishEvent.State state = event.getState();

        if (state == PlayerFishEvent.State.CAUGHT_FISH) {
            // 成功钓鱼
            int fishCount = playerFishCount.getOrDefault(playerId, 0) + 1;
            playerFishCount.put(playerId, fishCount);

            // 钓鱼增加魅力
            reputationService.addCharisma(playerId, 1, "休闲钓鱼");

            // 稀有物品钓鱼奖励
            if (event.getCaught() instanceof Item item) {
                Material caught = item.getItemStack().getType();

                if (caught == Material.DIAMOND || caught == Material.EMERALD) {
                    reputationService.addWealth(playerId, 5, "钓到贵重物品: " + caught.name());
                    influenceService.addInfluence(playerId, 3, "幸运渔夫");

                    // 创建八卦
                    gossipService.createGossip(playerId,
                        player.getName() + " 从水里钓出了 " + caught.name(),
                        GossipService.GossipTopic.WEALTH);
                }
            }

            // 检查钓鱼成就
            if (fishCount == 100) {
                influenceService.addInfluence(playerId, 20, "钓鱼达人");
            } else if (fishCount == 500) {
                influenceService.addInfluence(playerId, 50, "钓鱼大师");
            }
        } else if (state == PlayerFishEvent.State.IN_GROUND) {
            // 鱼钩卡住 - 轻微惩罚
            reputationService.addCharisma(playerId, -1, "钓鱼失败");
        }
    }

    // ==================== 社会动荡检测 ====================

    /**
     * 检测冲突引发的社会动荡
     */
    private void checkConflictUnrest(UUID playerId) {
        // 检查该玩家是否处于社会动荡状态
        Set<UUID> enemies = relationshipNetwork.getEnemies(playerId);
        Set<UUID> friends = relationshipNetwork.getFriends(playerId);

        // 高敌对比例检测
        if (enemies.size() > 5 && enemies.size() > friends.size()) {
            // 可能触发社会动荡事件
            if (ThreadLocalRandom.current().nextDouble() < 0.05) {
                triggerSocialUnrest(playerId, enemies);
            }
        }
    }

    /**
     * 检测死亡相关的社会动荡
     */
    private void checkDeathUnrest(UUID playerId) {
        Long lastDeath = lastDeathTime.get(playerId);
        if (lastDeath == null) return;

        long timeSinceDeath = System.currentTimeMillis() - lastDeath;

        // 24小时内死亡超过5次
        if (timeSinceDeath < 24 * 60 * 60 * 1000) {
            int deathCount = (int) lastDeathTime.keySet().stream()
                .filter(id -> id.equals(playerId))
                .count();

            if (deathCount > 5) {
                // 触发动荡检测
                int rep = reputationService.getReputation(playerId);
                if (rep < 0) {
                    // 低声望玩家频繁死亡可能触发事件
                    influenceService.addInfluence(playerId, -10, "频繁死亡");
                }
            }
        }
    }

    /**
     * 触发社会动荡事件
     */
    private void triggerSocialUnrest(UUID sourcePlayer, Set<UUID> affectedPlayers) {
        String playerName = Bukkit.getPlayer(sourcePlayer) != null ? Bukkit.getPlayer(sourcePlayer).getName() : "某玩家";

        // 发布动荡新闻
        newsService.publishNews(
            "社会动荡: " + playerName + " 引发冲突",
            playerName + " 与多人发生冲突，可能引发更大规模的动荡！",
            sourcePlayer,
            NewsPropagationService.NewsCategory.SOCIAL
        );

        // 创建八卦传播
        gossipService.createGossip(sourcePlayer,
            "听说 " + playerName + " 和一群人闹翻了",
            GossipService.GossipTopic.SCANDAL);

        // 对相关玩家产生影响
        for (UUID playerId : affectedPlayers) {
            if (Bukkit.getPlayer(playerId) != null) {
                // 通知在线的相关玩家
                // (实际发送系统消息)
            }
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取随机在线好友
     */
    private Player getRandomOnlineFriend(UUID playerId) {
        Set<UUID> friends = relationshipNetwork.getFriends(playerId);
        List<Player> onlineFriends = new ArrayList<>();

        for (UUID friendId : friends) {
            Player friend = Bukkit.getPlayer(friendId);
            if (friend != null && friend.isOnline()) {
                onlineFriends.add(friend);
            }
        }

        if (onlineFriends.isEmpty()) return null;
        return onlineFriends.get(RandomProvider.nextInt(onlineFriends.size()));
    }

    /**
     * 广播节日活动加成
     */
    public void broadcastEventEffects(Player player) {
        EventEffects effects = eventScheduler.getActiveEffects();
        if (effects.reputationBonus() > 0 || effects.influenceBonus() > 1.0) {
            player.sendMessage("§6【活动加成】当前事件效果:");
            if (effects.reputationBonus() > 0) {
                player.sendMessage("§7  - 声望获取: §a+" + (int)(effects.reputationBonus() * 100) + "%");
            }
            if (effects.influenceBonus() > 1.0) {
                player.sendMessage("§7  - 影响力获取: §a+" + (int)((effects.influenceBonus() - 1.0) * 100) + "%");
            }
        }
    }

    /**
     * 获取玩家社交统计
     */
    public Map<String, Object> getPlayerSocialStats(UUID playerId) {
        Map<String, Object> stats = new ConcurrentHashMap<>();

        stats.put("reputation", reputationService.getReputation(playerId));
        stats.put("reputationLevel", reputationService.getLevel(playerId));
        stats.put("influence", influenceService.getInfluence(playerId));
        stats.put("socialStatus", influenceService.getStatus(playerId));
        stats.put("friendCount", relationshipNetwork.getFriends(playerId).size());
        stats.put("enemyCount", relationshipNetwork.getEnemies(playerId).size());
        stats.put("killStreak", killStreak.getOrDefault(playerId, 0));
        stats.put("craftCount", playerCraftedItems.getOrDefault(playerId, Set.of()).size());
        stats.put("fishCount", playerFishCount.getOrDefault(playerId, 0));

        return stats;
    }
}
