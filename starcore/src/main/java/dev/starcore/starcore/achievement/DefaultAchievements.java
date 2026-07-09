package dev.starcore.starcore.achievement;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

/**
 * 默认成就注册器
 * 包含大量预设成就，参考Minecraft Wiki
 */
public final class DefaultAchievements {

    public static void registerAll(Plugin plugin, AchievementService service) {
        NamespacedKey root = new NamespacedKey(plugin, "root");

        // ========== 根成就 ==========
        service.registerAchievement(new Achievement.Builder(root)
            .title(Component.text("STARCORE冒险", NamedTextColor.GOLD))
            .description(Component.text("开始你的冒险之旅"))
            .icon(Material.GRASS_BLOCK)
            .frameType(Achievement.FrameType.TASK)
            .trigger(new AchievementTrigger.Builder(AchievementTrigger.TriggerType.IMPOSSIBLE).build())
            .position(0, 0)
            .showToast(true)
            .announceToChat(false)
            .build());

        // ========== 基础成就 ==========
        registerBasicAchievements(plugin, service, root);

        // ========== 战斗成就 ==========
        registerCombatAchievements(plugin, service, root);

        // ========== 挖掘成就 ==========
        registerMiningAchievements(plugin, service, root);

        // ========== 农业成就 ==========
        registerFarmingAchievements(plugin, service, root);

        // ========== PvP成就 ==========
        registerPvPAchievements(plugin, service, root);

        // ========== 社交成就 ==========
        registerSocialAchievements(plugin, service, root);

        // ========== 探索成就 ==========
        registerExplorationAchievements(plugin, service, root);

        // ========== 特殊成就 ==========
        registerSpecialAchievements(plugin, service, root);
    }

    /**
     * 基础成就
     */
    private static void registerBasicAchievements(Plugin plugin, AchievementService service, NamespacedKey parent) {
        // 获得木头
        NamespacedKey getWood = new NamespacedKey(plugin, "get_wood");
        service.registerAchievement(new Achievement.Builder(getWood)
            .title(Component.text("获得木头", NamedTextColor.GREEN))
            .description(Component.text("砍倒一棵树"))
            .icon(Material.OAK_LOG)
            .parent(parent)
            .position(1, 0)
            .trigger(new AchievementTrigger.Builder(AchievementTrigger.TriggerType.INVENTORY_CHANGED)
                .item(Material.OAK_LOG)
                .build())
            .experience(10)
            .build());

        // 制作工作台
        NamespacedKey craftWorkbench = new NamespacedKey(plugin, "craft_workbench");
        service.registerAchievement(new Achievement.Builder(craftWorkbench)
            .title(Component.text("这是？工作台！", NamedTextColor.GREEN))
            .description(Component.text("制作一个工作台"))
            .icon(Material.CRAFTING_TABLE)
            .parent(getWood)
            .position(2, 0)
            .trigger(new AchievementTrigger.Builder(AchievementTrigger.TriggerType.RECIPE_CRAFTED)
                .item(Material.CRAFTING_TABLE)
                .build())
            .experience(10)
            .build());

        // 制作镐子
        NamespacedKey craftPickaxe = new NamespacedKey(plugin, "craft_pickaxe");
        service.registerAchievement(new Achievement.Builder(craftPickaxe)
            .title(Component.text("来硬的", NamedTextColor.GREEN))
            .description(Component.text("制作一把镐子"))
            .icon(Material.WOODEN_PICKAXE)
            .parent(craftWorkbench)
            .position(3, 0)
            .trigger(new AchievementTrigger.Builder(AchievementTrigger.TriggerType.RECIPE_CRAFTED)
                .items(Material.WOODEN_PICKAXE, Material.STONE_PICKAXE,
                       Material.IRON_PICKAXE, Material.GOLDEN_PICKAXE,
                       Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE)
                .build())
            .experience(10)
            .build());
    }

    /**
     * 战斗成就
     */
    private static void registerCombatAchievements(Plugin plugin, AchievementService service, NamespacedKey parent) {
        // 第一滴血
        NamespacedKey firstKill = new NamespacedKey(plugin, "first_kill");
        service.registerAchievement(new Achievement.Builder(firstKill)
            .title(Component.text("第一滴血", NamedTextColor.RED))
            .description(Component.text("击杀任意生物"))
            .icon(Material.IRON_SWORD)
            .parent(parent)
            .position(0, 1)
            .trigger(new AchievementTrigger.Builder(AchievementTrigger.TriggerType.PLAYER_KILLED_ENTITY)
                .build())
            .experience(10)
            .announceToChat(false)
            .build());

        // 怪物猎人
        NamespacedKey monsterHunter = new NamespacedKey(plugin, "monster_hunter");
        service.registerAchievement(new Achievement.Builder(monsterHunter)
            .title(Component.text("怪物猎人", NamedTextColor.RED))
            .description(Component.text("击杀一只敌对生物"))
            .icon(Material.DIAMOND_SWORD)
            .parent(firstKill)
            .position(1, 1)
            .trigger(new AchievementTrigger.Builder(AchievementTrigger.TriggerType.PLAYER_KILLED_ENTITY)
                .entities(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER,
                          EntityType.CREEPER, EntityType.ENDERMAN)
                .build())
            .experience(15)
            .build());

        // 狙击手的对决
        NamespacedKey sniperDuel = new NamespacedKey(plugin, "sniper_duel");
        service.registerAchievement(new Achievement.Builder(sniperDuel)
            .title(Component.text("狙击手的对决", NamedTextColor.YELLOW))
            .description(Component.text("从50格外击杀骷髅"))
            .icon(Material.BOW)
            .parent(monsterHunter)
            .position(2, 1)
            .frameType(Achievement.FrameType.CHALLENGE)
            .trigger(new AchievementTrigger.Builder(AchievementTrigger.TriggerType.PLAYER_KILLED_ENTITY)
                .entity(EntityType.SKELETON)
                .distance(50, 999)
                .build())
            .experience(50)
            .announceToChat(true)
            .build());

        // 击败末影龙
        NamespacedKey freeTheEnd = new NamespacedKey(plugin, "free_the_end");
        service.registerAchievement(new Achievement.Builder(freeTheEnd)
            .title(Component.text("解放末地", NamedTextColor.LIGHT_PURPLE))
            .description(Component.text("击败末影龙"))
            .icon(Material.DRAGON_HEAD)
            .parent(monsterHunter)
            .position(3, 1)
            .frameType(Achievement.FrameType.GOAL)
            .trigger(new AchievementTrigger.Builder(AchievementTrigger.TriggerType.PLAYER_KILLED_ENTITY)
                .entity(EntityType.ENDER_DRAGON)
                .build())
            .experience(100)
            .announceToChat(true)
            .reward("money:10000")
            .build());

        // 击败凋灵
        NamespacedKey witherSlayer = new NamespacedKey(plugin, "wither_slayer");
        service.registerAchievement(new Achievement.Builder(witherSlayer)
            .title(Component.text("开始了？", NamedTextColor.LIGHT_PURPLE))
            .description(Component.text("击败凋灵"))
            .icon(Material.NETHER_STAR)
            .parent(monsterHunter)
            .position(3, 2)
            .frameType(Achievement.FrameType.GOAL)
            .trigger(new AchievementTrigger.Builder(AchievementTrigger.TriggerType.PLAYER_KILLED_ENTITY)
                .entity(EntityType.WITHER)
                .build())
            .experience(100)
            .announceToChat(true)
            .reward("money:10000")
            .build());
    }

    /**
     * 挖掘成就
     */
    private static void registerMiningAchievements(Plugin plugin, AchievementService service, NamespacedKey parent) {
        // 获得石头
        NamespacedKey getStone = new NamespacedKey(plugin, "get_stone");
        service.registerAchievement(new Achievement.Builder(getStone)
            .title(Component.text("石器时代", NamedTextColor.GRAY))
            .description(Component.text("挖掘石头"))
            .icon(Material.COBBLESTONE)
            .parent(parent)
            .position(0, -1)
            .trigger(new AchievementTrigger.Builder(AchievementTrigger.TriggerType.BREAK_BLOCK)
                .block(Material.STONE)
                .build())
            .experience(10)
            .build());

        // 获得铁锭
        NamespacedKey getIron = new NamespacedKey(plugin, "get_iron");
        service.registerAchievement(new Achievement.Builder(getIron)
            .title(Component.text("来都来了", NamedTextColor.WHITE))
            .description(Component.text("冶炼铁锭"))
            .icon(Material.IRON_INGOT)
            .parent(getStone)
            .position(1, -1)
            .trigger(new AchievementTrigger.Builder(AchievementTrigger.TriggerType.INVENTORY_CHANGED)
                .item(Material.IRON_INGOT)
                .build())
            .experience(15)
            .build());

        // 获得钻石
        NamespacedKey getDiamond = new NamespacedKey(plugin, "get_diamond");
        service.registerAchievement(new Achievement.Builder(getDiamond)
            .title(Component.text("钻石！", NamedTextColor.AQUA))
            .description(Component.text("获得钻石"))
            .icon(Material.DIAMOND)
            .parent(getIron)
            .position(2, -1)
            .trigger(new AchievementTrigger.Builder(AchievementTrigger.TriggerType.INVENTORY_CHANGED)
                .item(Material.DIAMOND)
                .build())
            .experience(30)
            .announceToChat(true)
            .build());

        // 获得下界合金
        NamespacedKey getNetherite = new NamespacedKey(plugin, "get_netherite");
        service.registerAchievement(new Achievement.Builder(getNetherite)
            .title(Component.text("严重的奉献", NamedTextColor.DARK_PURPLE))
            .description(Component.text("获得下界合金"))
            .icon(Material.NETHERITE_INGOT)
            .parent(getDiamond)
            .position(3, -1)
            .frameType(Achievement.FrameType.CHALLENGE)
            .trigger(new AchievementTrigger.Builder(AchievementTrigger.TriggerType.INVENTORY_CHANGED)
                .item(Material.NETHERITE_INGOT)
                .build())
            .experience(50)
            .announceToChat(true)
            .build());
    }

    /**
     * 农业成就
     */
    private static void registerFarmingAchievements(Plugin plugin, AchievementService service, NamespacedKey parent) {
        // 种植作物
        NamespacedKey plantSeed = new NamespacedKey(plugin, "plant_seed");
        service.registerAchievement(new Achievement.Builder(plantSeed)
            .title(Component.text("开荒", NamedTextColor.GREEN))
            .description(Component.text("种植一颗种子"))
            .icon(Material.WHEAT_SEEDS)
            .parent(parent)
            .position(0, 2)
            .trigger(new AchievementTrigger.Builder(AchievementTrigger.TriggerType.PLACED_BLOCK)
                .blocks(Material.WHEAT, Material.CARROTS, Material.POTATOES)
                .build())
            .experience(10)
            .build());

        // 繁殖动物
        NamespacedKey breedAnimals = new NamespacedKey(plugin, "breed_animals");
        service.registerAchievement(new Achievement.Builder(breedAnimals)
            .title(Component.text("我们需要更深入", NamedTextColor.GREEN))
            .description(Component.text("繁殖两只动物"))
            .icon(Material.WHEAT)
            .parent(plantSeed)
            .position(1, 2)
            .trigger(new AchievementTrigger.Builder(AchievementTrigger.TriggerType.BRED_ANIMALS)
                .build())
            .experience(15)
            .build());
    }

    /**
     * PvP成就
     */
    private static void registerPvPAchievements(Plugin plugin, AchievementService service, NamespacedKey parent) {
        // 首次击杀玩家
        NamespacedKey firstPlayerKill = new NamespacedKey(plugin, "first_player_kill");
        service.registerAchievement(new Achievement.Builder(firstPlayerKill)
            .title(Component.text("初次交锋", NamedTextColor.RED))
            .description(Component.text("击杀一名玩家"))
            .icon(Material.IRON_SWORD)
            .parent(parent)
            .position(-2, 0)
            .trigger(new AchievementTrigger.Builder(AchievementTrigger.TriggerType.CUSTOM_KILL_STREAK)
                .killCount(1)
                .build())
            .experience(20)
            .build());

        // 三连杀
        NamespacedKey tripleKill = new NamespacedKey(plugin, "triple_kill");
        service.registerAchievement(new Achievement.Builder(tripleKill)
            .title(Component.text("三连杀", NamedTextColor.GOLD))
            .description(Component.text("达成3连杀"))
            .icon(Material.GOLDEN_SWORD)
            .parent(firstPlayerKill)
            .position(-3, 0)
            .trigger(new AchievementTrigger.Builder(AchievementTrigger.TriggerType.CUSTOM_KILL_STREAK)
                .killCount(3)
                .build())
            .experience(30)
            .announceToChat(true)
            .build());

        // 十连杀
        NamespacedKey megaKill = new NamespacedKey(plugin, "mega_kill");
        service.registerAchievement(new Achievement.Builder(megaKill)
            .title(Component.text("无人能挡", NamedTextColor.DARK_RED))
            .description(Component.text("达成10连杀"))
            .icon(Material.DIAMOND_SWORD)
            .parent(tripleKill)
            .position(-4, 0)
            .frameType(Achievement.FrameType.CHALLENGE)
            .trigger(new AchievementTrigger.Builder(AchievementTrigger.TriggerType.CUSTOM_KILL_STREAK)
                .killCount(10)
                .build())
            .experience(100)
            .announceToChat(true)
            .reward("money:1000")
            .build());

        // 决斗胜利
        NamespacedKey winDuel = new NamespacedKey(plugin, "win_duel");
        service.registerAchievement(new Achievement.Builder(winDuel)
            .title(Component.text("决斗者", NamedTextColor.YELLOW))
            .description(Component.text("赢得一场决斗"))
            .icon(Material.IRON_SWORD)
            .parent(firstPlayerKill)
            .position(-3, 1)
            .trigger(new AchievementTrigger.Builder(AchievementTrigger.TriggerType.CUSTOM_DUEL_WIN)
                .build())
            .experience(25)
            .build());
    }

    /**
     * 社交成就
     */
    private static void registerSocialAchievements(Plugin plugin, AchievementService service, NamespacedKey parent) {
        // 添加好友
        NamespacedKey addFriend = new NamespacedKey(plugin, "add_friend");
        service.registerAchievement(new Achievement.Builder(addFriend)
            .title(Component.text("星链建立", NamedTextColor.AQUA))
            .description(Component.text("添加第一个好友"))
            .icon(Material.PLAYER_HEAD)
            .parent(parent)
            .position(0, 3)
            .trigger(new AchievementTrigger.Builder(AchievementTrigger.TriggerType.CUSTOM_FRIEND_ADDED)
                .build())
            .experience(15)
            .build());

        // 创建公会
        NamespacedKey createGuild = new NamespacedKey(plugin, "create_guild");
        service.registerAchievement(new Achievement.Builder(createGuild)
            .title(Component.text("星座诞生", NamedTextColor.GOLD))
            .description(Component.text("创建一个公会"))
            .icon(Material.WHITE_BANNER)
            .parent(addFriend)
            .position(1, 3)
            .trigger(new AchievementTrigger.Builder(AchievementTrigger.TriggerType.CUSTOM_GUILD_CREATED)
                .build())
            .experience(50)
            .announceToChat(true)
            .build());

        // 每日签到
        NamespacedKey dailyCheckIn = new NamespacedKey(plugin, "daily_checkin");
        service.registerAchievement(new Achievement.Builder(dailyCheckIn)
            .title(Component.text("每日一签", NamedTextColor.GREEN))
            .description(Component.text("完成一次签到"))
            .icon(Material.BOOK)
            .parent(parent)
            .position(0, 4)
            .trigger(new AchievementTrigger.Builder(AchievementTrigger.TriggerType.CUSTOM_DAILY_CHECKIN)
                .build())
            .experience(10)
            .build());
    }

    /**
     * 探索成就
     */
    private static void registerExplorationAchievements(Plugin plugin, AchievementService service, NamespacedKey parent) {
        // 进入下界
        NamespacedKey enterNether = new NamespacedKey(plugin, "enter_nether");
        service.registerAchievement(new Achievement.Builder(enterNether)
            .title(Component.text("见鬼去吧", NamedTextColor.RED))
            .description(Component.text("进入下界"))
            .icon(Material.NETHERRACK)
            .parent(parent)
            .position(2, -2)
            .trigger(new AchievementTrigger.Builder(AchievementTrigger.TriggerType.CHANGED_DIMENSION)
                .world("world_nether")
                .build())
            .experience(20)
            .build());

        // 进入末地
        NamespacedKey enterEnd = new NamespacedKey(plugin, "enter_end");
        service.registerAchievement(new Achievement.Builder(enterEnd)
            .title(Component.text("结束了？", NamedTextColor.DARK_PURPLE))
            .description(Component.text("进入末地"))
            .icon(Material.END_STONE)
            .parent(enterNether)
            .position(3, -2)
            .trigger(new AchievementTrigger.Builder(AchievementTrigger.TriggerType.CHANGED_DIMENSION)
                .world("world_the_end")
                .build())
            .experience(30)
            .build());
    }

    /**
     * 特殊成就
     */
    private static void registerSpecialAchievements(Plugin plugin, AchievementService service, NamespacedKey parent) {
        // 百万富翁
        NamespacedKey millionaire = new NamespacedKey(plugin, "millionaire");
        service.registerAchievement(new Achievement.Builder(millionaire)
            .title(Component.text("星尘大亨", NamedTextColor.GOLD))
            .description(Component.text("拥有100万星尘"))
            .icon(Material.GOLD_BLOCK)
            .parent(parent)
            .position(2, 3)
            .frameType(Achievement.FrameType.CHALLENGE)
            .trigger(new AchievementTrigger.Builder(AchievementTrigger.TriggerType.CUSTOM_MONEY_EARNED)
                .custom("amount", 1000000)
                .build())
            .experience(100)
            .announceToChat(true)
            .reward("item:DIAMOND:64")
            .build());
    }
}
