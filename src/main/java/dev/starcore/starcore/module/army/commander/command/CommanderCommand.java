package dev.starcore.starcore.module.army.commander.command;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.ArmyService;
import dev.starcore.starcore.module.army.commander.CommanderConfig;
import dev.starcore.starcore.module.army.commander.CommanderService;
import dev.starcore.starcore.module.army.commander.model.*;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 指挥官命令处理器
 * /sc commander <子命令>
 */
public final class CommanderCommand implements CommandExecutor, TabCompleter {
    private final CommanderService commanderService;
    private final NationService nationService;
    private final ArmyService armyService;
    private final MessageService messages;
    private final CommanderConfig config;

    public CommanderCommand(
        CommanderService commanderService,
        NationService nationService,
        ArmyService armyService,
        MessageService messages,
        CommanderConfig config
    ) {
        this.commanderService = commanderService;
        this.nationService = nationService;
        this.armyService = armyService;
        this.messages = messages;
        this.config = config;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(messages.format("command.player-only"), NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        try {
            switch (subCommand) {
                case "info", "i" -> handleInfo(player);
                case "skills", "s" -> handleSkills(player);
                case "unlock", "u" -> handleUnlock(player, args);
                case "upgrade", "up" -> handleUpgrade(player, args);
                case "use" -> handleUse(player, args);
                case "reset", "r" -> handleReset(player);
                case "exp", "e" -> handleExp(player);
                default -> showHelp(player);
            }
        } catch (Exception e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    private void handleInfo(Player player) {
        UUID playerId = player.getUniqueId();
        CommanderLevel level = commanderService.getCommanderLevel(playerId);
        int exp = commanderService.getExperience(playerId);
        int expToNext = level.experienceToNextLevel(exp);
        List<CommanderSkill> skills = commanderService.getUnlockedSkills(playerId);

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== " + messages.format("commander.info.title") + " ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text(messages.format("commander.info.level", level.levelNumber(), level.title()), NamedTextColor.YELLOW));
        player.sendMessage(Component.text(messages.format("commander.info.experience", exp, expToNext), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("commander.info.skills", skills.size(), config.maxSkillsPerCommander()), NamedTextColor.GRAY));

        if (!skills.isEmpty()) {
            player.sendMessage(Component.text(messages.format("commander.info.skill-list"), NamedTextColor.DARK_GRAY));
            for (CommanderSkill skill : skills) {
                player.sendMessage(Component.text(
                    "  " + skill.type().displayName() + " Lv." + skill.level() + " - " + skill.type().description(),
                    NamedTextColor.WHITE
                ));
            }
        }
        player.sendMessage(Component.text(""));
    }

    private void handleSkills(Player player) {
        UUID playerId = player.getUniqueId();
        CommanderLevel level = commanderService.getCommanderLevel(playerId);
        Map<SkillType, Integer> progress = commanderService.getSkillProgress(playerId);

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== " + messages.format("commander.skills.title") + " ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text(messages.format("commander.skills.your-level", level.levelNumber()), NamedTextColor.YELLOW));
        player.sendMessage(Component.text(""));

        // 按分类显示技能
        for (SkillCategory category : SkillCategory.values()) {
            List<SkillType> categorySkills = Arrays.stream(SkillType.values())
                .filter(s -> s.category() == category)
                .toList();

            player.sendMessage(Component.text("--- " + category.displayName() + " ---", NamedTextColor.DARK_GREEN));
            for (SkillType skillType : categorySkills) {
                int currentLevel = progress.getOrDefault(skillType, 0);
                boolean unlocked = currentLevel > 0;
                boolean canUnlock = level.ordinal() >= skillType.requiredLevel();

                String status;
                NamedTextColor color;
                if (unlocked) {
                    status = messages.format("commander.skills.unlocked", currentLevel, skillType.maxLevel());
                    color = NamedTextColor.GREEN;
                } else if (canUnlock) {
                    status = messages.format("commander.skills.available", skillType.unlockCost(level));
                    color = NamedTextColor.YELLOW;
                } else {
                    status = messages.format("commander.skills.locked", skillType.requiredLevel());
                    color = NamedTextColor.GRAY;
                }

                player.sendMessage(Component.text(
                    skillType.displayName() + ": " + skillType.description(),
                    NamedTextColor.WHITE
                ));
                player.sendMessage(Component.text("  " + status, color));
            }
            player.sendMessage(Component.text(""));
        }
    }

    private void handleUnlock(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("commander.unlock.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID playerId = player.getUniqueId();
        String skillName = args[1].toUpperCase();

        SkillType skillType;
        try {
            skillType = SkillType.valueOf(skillName);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(
                messages.format("commander.skill.not-found", args[1]),
                NamedTextColor.RED
            ));
            return;
        }

        // 检查国家
        Optional<Nation> nationOpt = nationService.getNationByMember(playerId);
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("commander.error.no-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        // 检查是否已达技能上限
        List<CommanderSkill> skills = commanderService.getUnlockedSkills(playerId);
        if (skills.size() >= config.maxSkillsPerCommander()) {
            player.sendMessage(Component.text(
                messages.format("commander.error.max-skills", config.maxSkillsPerCommander()),
                NamedTextColor.RED
            ));
            return;
        }

        // 解锁技能
        if (commanderService.unlockSkill(playerId, skillType)) {
            player.sendMessage(Component.text(
                messages.format("commander.unlock.success", skillType.displayName()),
                NamedTextColor.GREEN
            ));
        } else {
            player.sendMessage(Component.text(
                messages.format("commander.unlock.failed", skillType.displayName()),
                NamedTextColor.RED
            ));
        }
    }

    private void handleUpgrade(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("commander.upgrade.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID playerId = player.getUniqueId();
        String skillName = args[1].toUpperCase();

        SkillType skillType;
        try {
            skillType = SkillType.valueOf(skillName);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(
                messages.format("commander.skill.not-found", args[1]),
                NamedTextColor.RED
            ));
            return;
        }

        if (commanderService.upgradeSkill(playerId, skillType)) {
            player.sendMessage(Component.text(
                messages.format("commander.upgrade.success", skillType.displayName()),
                NamedTextColor.GREEN
            ));
        } else {
            player.sendMessage(Component.text(
                messages.format("commander.upgrade.failed", skillType.displayName()),
                NamedTextColor.RED
            ));
        }
    }

    private void handleUse(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("commander.use.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID playerId = player.getUniqueId();
        String skillName = args[1].toUpperCase();

        SkillType skillType;
        try {
            skillType = SkillType.valueOf(skillName);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(
                messages.format("commander.skill.not-found", args[1]),
                NamedTextColor.RED
            ));
            return;
        }

        // 解析目标ID（如果有）
        UUID targetId = null;
        if (args.length >= 3) {
            try {
                targetId = UUID.fromString(args[2]);
            } catch (IllegalArgumentException e) {
                player.sendMessage(Component.text(
                    messages.format("commander.use.invalid-target"),
                    NamedTextColor.RED
                ));
                return;
            }
        }

        // 使用技能
        String result = commanderService.useSkill(player, skillType, targetId);
        player.sendMessage(Component.text(result, NamedTextColor.GRAY));
    }

    private void handleReset(Player player) {
        if (!config.allowSkillReset()) {
            player.sendMessage(Component.text(
                messages.format("commander.reset.disabled"),
                NamedTextColor.RED
            ));
            return;
        }

        UUID playerId = player.getUniqueId();
        int refunded = commanderService.resetSkills(playerId);

        player.sendMessage(Component.text(
            messages.format("commander.reset.success", refunded),
            NamedTextColor.GREEN
        ));
    }

    private void handleExp(Player player) {
        UUID playerId = player.getUniqueId();
        int exp = commanderService.getExperience(playerId);
        CommanderLevel level = commanderService.getCommanderLevel(playerId);
        int expToNext = level.experienceToNextLevel(exp);

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== " + messages.format("commander.exp.title") + " ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text(messages.format("commander.exp.current", exp), NamedTextColor.YELLOW));
        player.sendMessage(Component.text(messages.format("commander.exp.to-next", level.nextLevel().title(), expToNext), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== " + messages.format("commander.help.title") + " ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text(messages.format("commander.help.info"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("commander.help.skills"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("commander.help.unlock"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("commander.help.upgrade"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("commander.help.use"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("commander.help.reset"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("commander.help.exp"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("info", "skills", "unlock", "upgrade", "use", "reset", "exp");
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("unlock") || args[0].equalsIgnoreCase("upgrade") || args[0].equalsIgnoreCase("use"))) {
            return Arrays.stream(SkillType.values())
                .map(Enum::name)
                .collect(Collectors.toList());
        }

        return List.of();
    }
}