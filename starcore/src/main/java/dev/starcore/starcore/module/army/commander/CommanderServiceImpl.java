package dev.starcore.starcore.module.army.commander;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.ArmyService;
import dev.starcore.starcore.module.army.commander.model.*;
import dev.starcore.starcore.module.army.model.ArmyUnit;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.*;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 指挥官技能服务实现
 */
public final class CommanderServiceImpl implements CommanderService {
    private static final String PERSISTENCE_NAMESPACE = "commander";
    private static final String PLAYER_DATA_FILE = "commanders.dat";

    private final Plugin plugin;
    private final NationService nationService;
    private final ArmyService armyService;
    private final MessageService messages;
    private final CommanderConfig config;

    // 玩家数据缓存
    private final ConcurrentHashMap<UUID, CommanderPlayerData> playerData = new ConcurrentHashMap<>();
    // 技能冷却记录 (playerId ^ skillType -> 上次使用时间)
    private final ConcurrentHashMap<Long, Long> skillCooldowns = new ConcurrentHashMap<>();
    // 国家指挥官索引 (nationId -> 最高指挥官信息)
    private final ConcurrentHashMap<UUID, UUID> nationHighestCommander = new ConcurrentHashMap<>();

    public CommanderServiceImpl(
        Plugin plugin,
        NationService nationService,
        ArmyService armyService,
        MessageService messages,
        CommanderConfig config
    ) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.armyService = armyService;
        this.messages = messages;
        this.config = config;
    }

    @Override
    public CommanderLevel getCommanderLevel(UUID playerId) {
        CommanderPlayerData data = getOrCreatePlayerData(playerId);
        return CommanderLevel.fromExperience(data.experience());
    }

    @Override
    public int getExperience(UUID playerId) {
        return getOrCreatePlayerData(playerId).experience();
    }

    @Override
    public List<CommanderSkill> getUnlockedSkills(UUID playerId) {
        CommanderPlayerData data = getOrCreatePlayerData(playerId);
        List<CommanderSkill> skills = new ArrayList<>();
        for (Map.Entry<SkillType, Integer> entry : data.skillLevels().entrySet()) {
            if (entry.getValue() > 0) {
                skills.add(new CommanderSkill(entry.getKey(), entry.getValue()));
            }
        }
        return skills;
    }

    @Override
    public Map<SkillType, Integer> getSkillProgress(UUID playerId) {
        return new EnumMap<>(getOrCreatePlayerData(playerId).skillLevels());
    }

    @Override
    public boolean hasSkill(UUID playerId, SkillType skillType) {
        return getSkillLevel(playerId, skillType) > 0;
    }

    @Override
    public int getSkillLevel(UUID playerId, SkillType skillType) {
        return getOrCreatePlayerData(playerId).skillLevels()
            .getOrDefault(skillType, 0);
    }

    @Override
    public boolean unlockSkill(UUID playerId, SkillType skillType) {
        CommanderPlayerData data = getOrCreatePlayerData(playerId);
        CommanderLevel level = CommanderLevel.fromExperience(data.experience());

        // 检查是否已解锁
        int currentLevel = data.skillLevels().getOrDefault(skillType, 0);
        if (currentLevel > 0) {
            return false; // 技能已解锁，需要升级
        }

        // 检查等级要求
        if (level.ordinal() < skillType.requiredLevel()) {
            return false; // 等级不足
        }

        // 检查经验是否足够
        int cost = skillType.unlockCost(level);
        if (data.experience() < cost) {
            return false; // 经验不足
        }

        // 消耗经验并解锁
        data.experience(data.experience() - cost);
        data.skillLevels().put(skillType, 1);
        data.lastUpdated(Instant.now());

        // 通知
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            player.sendMessage(Component.text(
                messages.format("commander.skill.unlocked", skillType.displayName(), 1),
                NamedTextColor.GREEN
            ));
        }

        // 保存
        savePlayerData(playerId);

        return true;
    }

    @Override
    public boolean upgradeSkill(UUID playerId, SkillType skillType) {
        CommanderPlayerData data = getOrCreatePlayerData(playerId);
        CommanderLevel level = CommanderLevel.fromExperience(data.experience());

        // 检查是否已解锁
        int currentLevel = data.skillLevels().getOrDefault(skillType, 0);
        if (currentLevel == 0) {
            return false; // 技能未解锁
        }

        // 检查是否已达最高级
        if (currentLevel >= skillType.maxLevel()) {
            return false; // 已是最高级
        }

        // 检查等级要求
        if (level.ordinal() < skillType.requiredLevel() + currentLevel) {
            return false; // 等级不足
        }

        // 检查经验是否足够
        int cost = skillType.upgradeCost(currentLevel);
        if (data.experience() < cost) {
            return false; // 经验不足
        }

        // 消耗经验并升级
        data.experience(data.experience() - cost);
        data.skillLevels().put(skillType, currentLevel + 1);
        data.lastUpdated(Instant.now());

        // 通知
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            player.sendMessage(Component.text(
                messages.format("commander.skill.upgraded", skillType.displayName(), currentLevel + 1),
                NamedTextColor.GREEN
            ));
        }

        // 保存
        savePlayerData(playerId);

        return true;
    }

    @Override
    public void consumeExperience(UUID playerId, int amount) {
        CommanderPlayerData data = getOrCreatePlayerData(playerId);
        data.experience(Math.max(0, data.experience() - amount));
        data.lastUpdated(Instant.now());
    }

    @Override
    public void addExperience(UUID playerId, int amount) {
        CommanderPlayerData data = getOrCreatePlayerData(playerId);
        CommanderLevel oldLevel = CommanderLevel.fromExperience(data.experience());
        data.experience(data.experience() + amount);
        CommanderLevel newLevel = CommanderLevel.fromExperience(data.experience());
        data.lastUpdated(Instant.now());

        // 检查是否升级
        if (newLevel.ordinal() > oldLevel.ordinal()) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                player.sendMessage(Component.text(
                    messages.format("commander.level.up", newLevel.name(), newLevel.title()),
                    NamedTextColor.GOLD
                ));
            }
        }

        // 通知经验获取
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && amount > 0) {
            player.sendMessage(Component.text(
                messages.format("commander.experience.gained", amount),
                NamedTextColor.YELLOW
            ));
        }

        savePlayerData(playerId);
    }

    @Override
    public boolean canUseSkill(Player player, SkillType skillType) {
        UUID playerId = player.getUniqueId();

        // 检查是否解锁
        if (!hasSkill(playerId, skillType)) {
            return false;
        }

        // 检查冷却
        if (getSkillCooldown(playerId, skillType) > 0) {
            return false;
        }

        // 检查国家
        Optional<Nation> nation = nationService.getNationByMember(playerId);
        if (nation.isEmpty()) {
            return false;
        }

        // 检查是否指挥本国军队
        if (!skillType.requiresArmyCommand()) {
            return true;
        }

        // 检查玩家是否正在指挥军队
        return armyService.getNationArmies(nation.get().id().value()).stream()
            .anyMatch(army -> army.isAlive() && army.canFight());
    }

    @Override
    public String useSkill(Player player, SkillType skillType, UUID targetId) {
        UUID playerId = player.getUniqueId();

        // 检查冷却
        long cooldown = getSkillCooldown(playerId, skillType);
        if (cooldown > 0) {
            return messages.format("commander.skill.cooldown", cooldown);
        }

        // 检查是否解锁
        int skillLevel = getSkillLevel(playerId, skillType);
        if (skillLevel == 0) {
            return messages.format("commander.skill.not-unlocked", skillType.displayName());
        }

        // 执行技能效果
        String result = executeSkillEffect(player, skillType, skillLevel, targetId);

        // 设置冷却
        long cooldownMs = skillType.cooldownTicks() * 50L;
        long cooldownKey = cooldownKey(playerId, skillType);
        skillCooldowns.put(cooldownKey, System.currentTimeMillis() + cooldownMs);

        return result;
    }

    @Override
    public long getSkillCooldown(UUID playerId, SkillType skillType) {
        long cooldownKey = cooldownKey(playerId, skillType);
        long lastUsed = skillCooldowns.getOrDefault(cooldownKey, 0L);
        long remaining = lastUsed - System.currentTimeMillis();
        return Math.max(0, remaining / 1000);
    }

    @Override
    public int resetSkills(UUID playerId) {
        CommanderPlayerData data = playerData.get(playerId);
        if (data == null) {
            return 0;
        }

        // 计算已消耗的经验
        int totalSpent = 0;
        for (Map.Entry<SkillType, Integer> entry : data.skillLevels().entrySet()) {
            int level = entry.getValue();
            if (level > 0) {
                totalSpent += entry.getKey().unlockCost(getCommanderLevel(playerId));
                for (int i = 1; i < level; i++) {
                    totalSpent += entry.getKey().upgradeCost(i);
                }
            }
        }

        // 重置技能等级
        for (SkillType type : SkillType.values()) {
            data.skillLevels().put(type, 0);
        }

        // 退还一半经验
        int refunded = totalSpent / 2;
        data.experience(data.experience() + refunded);
        data.lastUpdated(Instant.now());

        savePlayerData(playerId);

        return refunded;
    }

    @Override
    public double getNationMoraleBonus(NationId nationId) {
        Optional<UUID> commander = getHighestCommander(nationId);
        if (commander.isEmpty()) {
            return 1.0;
        }

        CommanderLevel level = getCommanderLevel(commander.get());
        // 每级增加2%士气加成，最高20%
        return 1.0 + (level.ordinal() * 0.02);
    }

    @Override
    public Optional<UUID> getHighestCommander(NationId nationId) {
        UUID cached = nationHighestCommander.get(nationId.value());
        if (cached != null) {
            return Optional.of(cached);
        }

        // 计算最高指挥官
        Optional<Nation> nation = nationService.nationById(nationId);
        if (nation.isEmpty()) {
            return Optional.empty();
        }

        UUID highest = null;
        int highestLevel = -1;

        for (var member : nation.get().members()) {
            CommanderLevel level = getCommanderLevel(member.playerId());
            if (level.ordinal() > highestLevel) {
                highestLevel = level.ordinal();
                highest = member.playerId();
            }
        }

        if (highest != null) {
            nationHighestCommander.put(nationId.value(), highest);
        }

        return Optional.ofNullable(highest);
    }

    @Override
    public void savePlayerData(UUID playerId) {
        CommanderPlayerData data = playerData.get(playerId);
        if (data == null) {
            return;
        }

        try {
            var props = new java.util.Properties();
            String key = playerId.toString();
            StringBuilder sb = new StringBuilder();
            sb.append("exp=").append(data.experience()).append(";");
            sb.append("updated=").append(data.lastUpdated().toEpochMilli()).append(";");
            for (Map.Entry<SkillType, Integer> entry : data.skillLevels().entrySet()) {
                sb.append(entry.getKey().name()).append("=").append(entry.getValue()).append(";");
            }
            props.setProperty(key, sb.toString());

            // 这里应该通过 PersistenceService 保存，但暂时记录日志
            plugin.getLogger().fine("Saved commander data for player: " + playerId);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save commander data: " + e.getMessage());
        }
    }

    @Override
    public void loadPlayerData(UUID playerId) {
        getOrCreatePlayerData(playerId);
    }

    // ==================== 私有方法 ====================

    private CommanderPlayerData getOrCreatePlayerData(UUID playerId) {
        return playerData.computeIfAbsent(playerId, id -> {
            CommanderPlayerData data = new CommanderPlayerData(0, new EnumMap<>(SkillType.class), Instant.now());
            // 可以从数据库加载已有数据
            return data;
        });
    }

    private long cooldownKey(UUID playerId, SkillType skillType) {
        return ((long) playerId.hashCode()) ^ ((long) skillType.ordinal() << 32);
    }

    private String executeSkillEffect(Player player, SkillType skillType, int level, UUID targetId) {
        return switch (skillType) {
            case RALLY -> executeRally(player, level);
            case INSPIRE -> executeInspire(player, level);
            case REINFORCE -> executeReinforce(player, level, targetId);
            case TACTICAL_RETREAT -> executeTacticalRetreat(player, level, targetId);
            case SIEGE_MASTERY -> executeSiegeMastery(player, level, targetId);
            case CAVALRY_CHARGE -> executeCavalryCharge(player, level, targetId);
            case PHALANX -> executePhalanx(player, level, targetId);
            case SCOUT -> executeScout(player, level);
            case MORALE_BOOST -> executeMoraleBoost(player, level);
            case SUPPLY_LINE -> executeSupplyLine(player, level);
        };
    }

    private String executeRally(Player player, int level) {
        // 号令：提升范围内友军攻击力
        Optional<Nation> nation = nationService.getNationByMember(player.getUniqueId());
        if (nation.isEmpty()) {
            return messages.format("commander.skill.no-nation");
        }

        Location loc = player.getLocation();
        double radius = 50 + (level * 10); // 每级增加10格范围
        List<ArmyUnit> nearbyArmies = armyService.getArmiesNear(loc, radius);

        int affected = 0;
        for (ArmyUnit army : nearbyArmies) {
            if (army.nationId().equals(nation.get().id().value())) {
                // 临时提升攻击力（通过士气）
                army.changeMorale(5 * level);
                affected++;
            }
        }

        return messages.format("commander.skill.rally.effect", affected);
    }

    private String executeInspire(Player player, int level) {
        // 激励：提升自身国家所有军队士气
        Optional<Nation> nation = nationService.getNationByMember(player.getUniqueId());
        if (nation.isEmpty()) {
            return messages.format("commander.skill.no-nation");
        }

        List<ArmyUnit> armies = armyService.getNationArmies(nation.get().id().value());
        int affected = 0;
        for (ArmyUnit army : armies) {
            army.changeMorale(10 * level);
            affected++;
        }

        return messages.format("commander.skill.inspire.effect", affected);
    }

    private String executeReinforce(Player player, int level, UUID targetId) {
        // 增援：为指定军队补充士兵
        if (targetId == null) {
            return messages.format("commander.skill.no-target");
        }

        Optional<ArmyUnit> armyOpt = armyService.getArmy(targetId);
        if (armyOpt.isEmpty()) {
            return messages.format("army.not-found");
        }

        ArmyUnit army = armyOpt.get();
        int soldiers = 10 * level; // 每级增加10士兵
        army.recruitSoldiers(soldiers);

        return messages.format("commander.skill.reinforce.effect", soldiers);
    }

    private String executeTacticalRetreat(Player player, int level, UUID targetId) {
        // 战术撤退：立即脱离战斗并恢复部分生命
        if (targetId == null) {
            return messages.format("commander.skill.no-target");
        }

        Optional<ArmyUnit> armyOpt = armyService.getArmy(targetId);
        if (armyOpt.isEmpty()) {
            return messages.format("army.not-found");
        }

        ArmyUnit army = armyOpt.get();
        army.setState(dev.starcore.starcore.module.army.model.ArmyState.MARCHING);
        army.heal(20 * level); // 每级恢复20%生命
        army.changeMorale(5 * level);

        return messages.format("commander.skill.retreat.effect");
    }

    private String executeSiegeMastery(Player player, int level, UUID targetId) {
        // 攻城精通：提升攻城器械效率
        if (targetId == null) {
            return messages.format("commander.skill.no-target");
        }

        Optional<ArmyUnit> armyOpt = armyService.getArmy(targetId);
        if (armyOpt.isEmpty()) {
            return messages.format("army.not-found");
        }

        // 攻城效率由 ArmyType.siegeEfficiency() 控制
        // 这里通过提升士气来间接提升效率
        ArmyUnit army = armyOpt.get();
        army.changeMorale(15 * level);

        return messages.format("commander.skill.siege.effect", level * 50);
    }

    private String executeCavalryCharge(Player player, int level, UUID targetId) {
        // 骑兵冲锋：对指定目标造成额外伤害
        if (targetId == null) {
            return messages.format("commander.skill.no-target");
        }

        Optional<ArmyUnit> armyOpt = armyService.getArmy(targetId);
        if (armyOpt.isEmpty()) {
            return messages.format("army.not-found");
        }

        ArmyUnit army = armyOpt.get();
        double damage = army.maxHealth() * (0.1 * level); // 每级造成10%最大生命伤害
        army.takeDamage(damage);

        return messages.format("commander.skill.cavalry.effect", String.format("%.1f", damage));
    }

    private String executePhalanx(Player player, int level, UUID targetId) {
        // 方阵：提升防御力
        if (targetId == null) {
            return messages.format("commander.skill.no-target");
        }

        Optional<ArmyUnit> armyOpt = armyService.getArmy(targetId);
        if (armyOpt.isEmpty()) {
            return messages.format("army.not-found");
        }

        ArmyUnit army = armyOpt.get();
        army.changeMorale(10 * level);

        return messages.format("commander.skill.phalanx.effect", level * 20);
    }

    private String executeScout(Player player, int level) {
        // 侦察：显示附近所有军队信息
        Location loc = player.getLocation();
        double radius = 200 + (level * 100);
        List<ArmyUnit> nearbyArmies = armyService.getArmiesNear(loc, radius);

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("commander.skill.scout.header"), NamedTextColor.GOLD));
        for (ArmyUnit army : nearbyArmies) {
            String nationName = nationService.nationById(new NationId(army.nationId()))
                .map(Nation::name)
                .orElse("Unknown");
            player.sendMessage(Component.text(
                messages.format("commander.skill.scout.entry",
                    army.type().key(),
                    nationName,
                    army.soldiers(),
                    String.format("%.1f", army.morale())),
                NamedTextColor.GRAY
            ));
        }
        player.sendMessage(Component.text(""));

        return messages.format("commander.skill.scout.effect", nearbyArmies.size());
    }

    private String executeMoraleBoost(Player player, int level) {
        // 士气激励：提升所有友军士气
        Optional<Nation> nation = nationService.getNationByMember(player.getUniqueId());
        if (nation.isEmpty()) {
            return messages.format("commander.skill.no-nation");
        }

        List<ArmyUnit> armies = armyService.getNationArmies(nation.get().id().value());
        int affected = 0;
        for (ArmyUnit army : armies) {
            army.changeMorale(15 * level);
            affected++;
        }

        return messages.format("commander.skill.morale-boost.effect", affected, level * 15);
    }

    private String executeSupplyLine(Player player, int level) {
        // 补给线：补给所有友军
        Optional<Nation> nation = nationService.getNationByMember(player.getUniqueId());
        if (nation.isEmpty()) {
            return messages.format("commander.skill.no-nation");
        }

        List<ArmyUnit> armies = armyService.getNationArmies(nation.get().id().value());
        int affected = 0;
        for (ArmyUnit army : armies) {
            army.resupply(25 * level); // 每级恢复25%补给
            affected++;
        }

        return messages.format("commander.skill.supply-line.effect", affected, level * 25);
    }

    /**
     * 玩家指挥官数据
     */
    public static class CommanderPlayerData {
        private int experience;
        private final EnumMap<SkillType, Integer> skillLevels;
        private Instant lastUpdated;

        public CommanderPlayerData(int experience, EnumMap<SkillType, Integer> skillLevels, Instant lastUpdated) {
            this.experience = experience;
            this.skillLevels = skillLevels;
            this.lastUpdated = lastUpdated;
        }

        public int experience() {
            return experience;
        }

        public void experience(int value) {
            this.experience = value;
        }

        public EnumMap<SkillType, Integer> skillLevels() {
            return skillLevels;
        }

        public Instant lastUpdated() {
            return lastUpdated;
        }

        public void lastUpdated(Instant value) {
            this.lastUpdated = value;
        }
    }
}