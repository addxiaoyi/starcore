package dev.starcore.starcore.module.dynasty.command;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.dynasty.DynastyService;
import dev.starcore.starcore.module.dynasty.model.Dynasty;
import dev.starcore.starcore.module.dynasty.model.SuccessionType;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
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
 * 王朝命令处理器
 * /dynasty <子命令>
 */
public final class DynastyCommand implements CommandExecutor, TabCompleter {
    private final DynastyService dynastyService;
    private final NationService nationService;
    private final MessageService messages;
    private org.bukkit.plugin.Plugin plugin;

    public DynastyCommand(DynastyService dynastyService, NationService nationService, MessageService messages) {
        this.dynastyService = dynastyService;
        this.nationService = nationService;
        this.messages = messages;
    }

    public void setPlugin(org.bukkit.plugin.Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
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
                case "info", "i" -> handleInfo(player, args);
                case "abdicate", "a" -> handleAbdicate(player, args);
                case "inherit", "in" -> handleInherit(player, args);
                case "heir", "h" -> handleHeir(player, args);
                case "addheir", "ah" -> handleAddHeir(player, args);
                case "removeheir", "rh" -> handleRemoveHeir(player, args);
                case "type", "t" -> handleSetType(player, args);
                case "claim", "c" -> handleClaimCrown(player);
                case "list", "ls" -> handleList(player);
                case "interregnum", "ir" -> handleInterregnum(player);
                default -> showHelp(player);
            }
        } catch (Exception e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    /**
     * 显示帮助信息
     */
    private void showHelp(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 王位继承系统 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/dynasty info [国家] - 查看王朝信息", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/dynasty abdicate <玩家> [原因] - 禅让王位", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/dynasty inherit - 继承王位", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/dynasty heir - 查看继承人列表", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/dynasty addheir <玩家> - 添加继承人", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/dynasty removeheir <玩家> - 移除继承人", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/dynasty type <类型> - 设置继承类型", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/dynasty claim - 索取王位", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/dynasty list - 列出所有空缺王位", NamedTextColor.GRAY));
        player.sendMessage(Component.text("继承类型: primogeniture, elective, appointment, hereditary, absolute, parliamentary", NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.text(""));
    }

    /**
     * 查看王朝信息
     */
    private void handleInfo(Player player, String[] args) {
        NationId nationId = getTargetNation(player, args);

        Dynasty dynasty = dynastyService.getDynasty(nationId).orElse(null);
        if (dynasty == null) {
            player.sendMessage(Component.text("未找到该国家的王朝信息", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 王朝信息 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("国家: " + getNationName(nationId), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("王朝名: " + dynasty.dynastyName(), NamedTextColor.WHITE));

        if (dynasty.hasMonarch()) {
            player.sendMessage(Component.text("君主: " + dynasty.currentMonarchName(), NamedTextColor.GREEN));
            player.sendMessage(Component.text("在位时间: " + dynasty.getDaysSinceMonarch() + " 天", NamedTextColor.GRAY));
        } else {
            player.sendMessage(Component.text("状态: 空缺期", NamedTextColor.RED));
            player.sendMessage(Component.text("空缺时间: " + dynasty.getInterregnumDays() + " 天", NamedTextColor.GRAY));
        }

        player.sendMessage(Component.text("继承类型: " + dynasty.successionType().displayName(), NamedTextColor.WHITE));
        player.sendMessage(Component.text("继承类型描述: " + dynasty.successionType().description(), NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.text("继承人数量: " + dynasty.successionOrder().size(), NamedTextColor.WHITE));

        // 显示继承人列表
        if (!dynasty.successionOrder().isEmpty()) {
            player.sendMessage(Component.text("继承人顺序:", NamedTextColor.YELLOW));
            for (int i = 0; i < dynasty.successionOrder().size(); i++) {
                Dynasty.HeirInfo heir = dynasty.successionOrder().get(i);
                player.sendMessage(Component.text(
                    (i + 1) + ". " + heir.playerName(),
                    i == 0 ? NamedTextColor.GOLD : NamedTextColor.GRAY
                ));
            }
        }

        player.sendMessage(Component.text(""));
    }

    /**
     * 禅让王位
     */
    private void handleAbdicate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("/dynasty abdicate <玩家> [原因]", NamedTextColor.YELLOW));
            return;
        }

        UUID newMonarch = parsePlayerUUID(args[1]);
        String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "自愿禅让";

        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("你不在任何国家中", NamedTextColor.RED));
            return;
        }

        Nation nation = nationOpt.get();
        Dynasty dynasty = dynastyService.getDynasty(nation.id()).orElse(null);
        if (dynasty == null || !dynasty.isMonarch(player.getUniqueId())) {
            player.sendMessage(Component.text("你不是该国的君主", NamedTextColor.RED));
            return;
        }

        DynastyService.SuccessionResult result = dynastyService.abdicate(nation.id(), player.getUniqueId(), newMonarch, reason);
        if (result.success()) {
            player.sendMessage(Component.text("禅让成功！新君主: " + result.dynasty().currentMonarchName(), NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("禅让失败: " + result.message(), NamedTextColor.RED));
        }
    }

    /**
     * 继承王位
     */
    private void handleInherit(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("你不在任何国家中", NamedTextColor.RED));
            return;
        }

        Nation nation = nationOpt.get();
        Dynasty dynasty = dynastyService.getDynasty(nation.id()).orElse(null);
        if (dynasty == null || dynasty.hasMonarch()) {
            player.sendMessage(Component.text("该国王位没有空缺", NamedTextColor.RED));
            return;
        }

        DynastyService.SuccessionResult result = dynastyService.inherit(nation.id(), player.getUniqueId());
        if (result.success()) {
            player.sendMessage(Component.text("继承成功！你现在是 " + nation.name() + " 的君主", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("继承失败: " + result.message(), NamedTextColor.RED));
        }
    }

    /**
     * 查看继承人列表
     */
    private void handleHeir(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("你不在任何国家中", NamedTextColor.RED));
            return;
        }

        Nation nation = nationOpt.get();
        List<DynastyService.HeirRecord> heirs = dynastyService.getHeirs(nation.id());

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== " + nation.name() + " 继承人列表 ===", NamedTextColor.GOLD));

        if (heirs.isEmpty()) {
            player.sendMessage(Component.text("暂无继承人", NamedTextColor.YELLOW));
        } else {
            for (DynastyService.HeirRecord heir : heirs) {
                String prefix = heir.position() == 1 ? ">> " : "   ";
                player.sendMessage(Component.text(
                    prefix + heir.position() + ". " + heir.playerName(),
                    heir.position() == 1 ? NamedTextColor.GOLD : NamedTextColor.GRAY
                ));
            }
        }
        player.sendMessage(Component.text(""));
    }

    /**
     * 添加继承人
     */
    private void handleAddHeir(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("/dynasty addheir <玩家>", NamedTextColor.YELLOW));
            return;
        }

        UUID heirId = parsePlayerUUID(args[1]);

        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("你不在任何国家中", NamedTextColor.RED));
            return;
        }

        Nation nation = nationOpt.get();
        Dynasty dynasty = dynastyService.getDynasty(nation.id()).orElse(null);
        if (dynasty == null || !dynasty.isMonarch(player.getUniqueId())) {
            player.sendMessage(Component.text("你不是该国的君主", NamedTextColor.RED));
            return;
        }

        DynastyService.SuccessionResult result = dynastyService.addHeir(nation.id(), player.getUniqueId(), heirId);
        if (result.success()) {
            player.sendMessage(Component.text("继承人添加成功", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("添加失败: " + result.message(), NamedTextColor.RED));
        }
    }

    /**
     * 移除继承人
     */
    private void handleRemoveHeir(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("/dynasty removeheir <玩家>", NamedTextColor.YELLOW));
            return;
        }

        UUID heirId = parsePlayerUUID(args[1]);

        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("你不在任何国家中", NamedTextColor.RED));
            return;
        }

        Nation nation = nationOpt.get();
        Dynasty dynasty = dynastyService.getDynasty(nation.id()).orElse(null);
        if (dynasty == null || !dynasty.isMonarch(player.getUniqueId())) {
            player.sendMessage(Component.text("你不是该国的君主", NamedTextColor.RED));
            return;
        }

        DynastyService.SuccessionResult result = dynastyService.removeHeir(nation.id(), player.getUniqueId(), heirId);
        if (result.success()) {
            player.sendMessage(Component.text("继承人移除成功", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("移除失败: " + result.message(), NamedTextColor.RED));
        }
    }

    /**
     * 设置继承类型
     */
    private void handleSetType(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("/dynasty type <类型>", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("可用类型: male_prem, ultim, primogeniture, elective, appointment, hereditary, absolute, parliamentary", NamedTextColor.GRAY));
            return;
        }

        SuccessionType type = parseSuccessionType(args[1]);
        if (type == null) {
            player.sendMessage(Component.text("无效的继承类型: " + args[1], NamedTextColor.RED));
            return;
        }

        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("你不在任何国家中", NamedTextColor.RED));
            return;
        }

        Nation nation = nationOpt.get();
        Dynasty dynasty = dynastyService.getDynasty(nation.id()).orElse(null);
        if (dynasty == null || !dynasty.isMonarch(player.getUniqueId())) {
            player.sendMessage(Component.text("你不是该国的君主", NamedTextColor.RED));
            return;
        }

        DynastyService.SuccessionResult result = dynastyService.setSuccessionType(nation.id(), player.getUniqueId(), type);
        if (result.success()) {
            player.sendMessage(Component.text("继承类型已设置为: " + type.displayName(), NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("设置失败: " + result.message(), NamedTextColor.RED));
        }
    }

    /**
     * 索取王位
     */
    private void handleClaimCrown(Player player) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("你不在任何国家中", NamedTextColor.RED));
            return;
        }

        Nation nation = nationOpt.get();
        DynastyService.SuccessionResult result = dynastyService.claimCrown(nation.id(), player.getUniqueId());
        if (result.success()) {
            player.sendMessage(Component.text("你成功加冕为 " + nation.name() + " 的君主！", NamedTextColor.GOLD));
        } else {
            player.sendMessage(Component.text("加冕失败: " + result.message(), NamedTextColor.RED));
        }
    }

    /**
     * 列出所有空缺王位的国家
     */
    private void handleList(Player player) {
        Collection<NationId> interregnumNations = dynastyService.getInterregnumNations();

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 王位空缺的国家 ===", NamedTextColor.GOLD));

        if (interregnumNations.isEmpty()) {
            player.sendMessage(Component.text("目前没有王位空缺的国家", NamedTextColor.YELLOW));
        } else {
            for (NationId nationId : interregnumNations) {
                String nationName = getNationName(nationId);
                Optional<java.time.Instant> startTime = dynastyService.getInterregnumStart(nationId);
                String duration = startTime.map(instant -> {
                    long days = java.time.Duration.between(instant, java.time.Instant.now()).toDays();
                    return days + " 天";
                }).orElse("未知");

                player.sendMessage(Component.text(
                    "- " + nationName + " (空缺 " + duration + ")",
                    NamedTextColor.YELLOW
                ));
            }
        }
        player.sendMessage(Component.text(""));
    }

    /**
     * 查看空缺期详情
     */
    private void handleInterregnum(Player player) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text("你不在任何国家中", NamedTextColor.RED));
            return;
        }

        Nation nation = nationOpt.get();
        if (!dynastyService.isInterregnum(nation.id())) {
            player.sendMessage(Component.text("该国目前没有王位空缺", NamedTextColor.RED));
            return;
        }

        Optional<java.time.Instant> startTime = dynastyService.getInterregnumStart(nation.id());
        long days = startTime.map(instant ->
            java.time.Duration.between(instant, java.time.Instant.now()).toDays()
        ).orElse(0L);

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 王位空缺期 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("国家: " + nation.name(), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("空缺天数: " + days + " 天", NamedTextColor.RED));
        player.sendMessage(Component.text("提示: 使用 /dynasty inherit 继承王位", NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    /**
     * 获取目标国家ID
     */
    private NationId getTargetNation(Player player, String[] args) {
        if (args.length >= 2) {
            // 尝试通过国家名获取
            Optional<Nation> nationOpt = nationService.nationByName(args[1]);
            if (nationOpt.isPresent()) {
                return nationOpt.get().id();
            }
        }

        // 使用玩家所在国家
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isPresent()) {
            return nationOpt.get().id();
        }

        throw new IllegalStateException("你不在任何国家中");
    }

    /**
     * 获取国家名称
     */
    private String getNationName(NationId nationId) {
        return nationService.nationById(nationId)
                .map(Nation::name)
                .orElse("Unknown");
    }

    /**
     * 解析玩家UUID
     */
    private UUID parsePlayerUUID(String input) {
        // 尝试作为在线玩家解析
        Player target = playerByName(input);
        if (target != null) {
            return target.getUniqueId();
        }

        // 尝试作为UUID解析
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的玩家: " + input);
        }
    }

    /**
     * 通过名称查找玩家
     */
    private Player playerByName(String name) {
        if (plugin != null) {
            return plugin.getServer().getPlayer(name);
        }
        return null;
    }

    /**
     * 解析继承类型
     */
    private SuccessionType parseSuccessionType(String input) {
        return switch (input.toLowerCase()) {
            case "male_prem", "maleprem" -> SuccessionType.MALE_PREMIogeniture;
            case "ultim", "ultimogeniture" -> SuccessionType.ULTIMogeniture;
            case "primogeniture", "primo" -> SuccessionType.PRIMOGENITURE;
            case "elective", "election" -> SuccessionType.ELECTIVE_MONARCHY;
            case "appointment", "appoint" -> SuccessionType.APPOINTMENT;
            case "hereditary", "heredity" -> SuccessionType.HEREDITARY;
            case "absolute", "abs" -> SuccessionType.ABSOLUTE;
            case "parliamentary", "parliament" -> SuccessionType.PARLIAMENTARY;
            default -> null;
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("info", "abdicate", "inherit", "heir", "addheir", "removeheir", "type", "claim", "list", "interregnum");
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "type" -> {
                    return List.of("male_prem", "ultim", "primogeniture", "elective", "appointment", "hereditary", "absolute", "parliamentary");
                }
                case "info" -> {
                    return nationService.nations().stream()
                            .map(Nation::name)
                            .collect(Collectors.toList());
                }
            }
        }

        return List.of();
    }
}