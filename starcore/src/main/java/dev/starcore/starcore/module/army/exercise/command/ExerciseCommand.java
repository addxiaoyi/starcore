package dev.starcore.starcore.module.army.exercise.command;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.exercise.Exercise;
import dev.starcore.starcore.module.army.exercise.ExerciseResult;
import dev.starcore.starcore.module.army.exercise.ExerciseService;
import dev.starcore.starcore.module.army.exercise.ExerciseType;
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
 * 演习命令处理器
 * /exercise <子命令>
 */
public final class ExerciseCommand implements CommandExecutor, TabCompleter {
    private final ExerciseService exerciseService;
    private final NationService nationService;
    private final MessageService messages;

    public ExerciseCommand(ExerciseService exerciseService, NationService nationService, MessageService messages) {
        this.exerciseService = exerciseService;
        this.nationService = nationService;
        this.messages = messages;
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
                case "create" -> handleCreate(player, args);
                case "list" -> handleList(player, args);
                case "info" -> handleInfo(player, args);
                case "join" -> handleJoin(player, args);
                case "leave" -> handleLeave(player, args);
                case "start" -> handleStart(player, args);
                case "cancel" -> handleCancel(player, args);
                case "end" -> handleEnd(player, args);
                case "result" -> handleResult(player, args);
                case "pending" -> handlePending(player);
                default -> showHelp(player);
            }
        } catch (IllegalStateException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        } catch (Exception e) {
            player.sendMessage(Component.text("Error: " + e.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    private void handleCreate(Player player, String[] args) {
        // 检查玩家是否在国家中
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("exercise.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation = nationOpt.get();

        // 检查是否是国家领袖
        if (!nation.founderId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text(
                messages.format("exercise.not-leader"),
                NamedTextColor.RED
            ));
            return;
        }

        // 解析参数
        if (args.length < 3) {
            player.sendMessage(Component.text(
                messages.format("exercise.create.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        String name = args[1];
        ExerciseType type;
        try {
            type = ExerciseType.fromKey(args[2]);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(
                messages.format("exercise.invalid-type", args[2]),
                NamedTextColor.RED
            ));
            return;
        }

        // 创建演习
        Exercise exercise = exerciseService.createExercise(nation.id().value(), name, type);

        player.sendMessage(Component.text(
            messages.format("exercise.created", name, type.displayName()),
            NamedTextColor.GREEN
        ));

        // 显示加入提示
        player.sendMessage(Component.text(
            messages.format("exercise.join-hint", exercise.id().toString().substring(0, 8)),
            NamedTextColor.GRAY
        ));
    }

    private void handleList(Player player, String[] args) {
        List<Exercise> exercises = exerciseService.getActiveExercises();

        if (exercises.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("exercise.no-active"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("exercise.list.header"), NamedTextColor.GOLD));

        for (Exercise exercise : exercises) {
            String shortId = exercise.id().toString().substring(0, 8);
            player.sendMessage(Component.text(
                messages.format("exercise.list.entry",
                    shortId,
                    exercise.name(),
                    exercise.type().displayName(),
                    exercise.state().displayName(),
                    exercise.participantCount(),
                    exercise.getRemainingMinutes()),
                NamedTextColor.GRAY
            ));
        }
        player.sendMessage(Component.text(""));
    }

    private void handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("exercise.info.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID exerciseId = parseExerciseId(player, args[1]);
        Optional<Exercise> exerciseOpt = exerciseService.getExercise(exerciseId);

        if (exerciseOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("exercise.not-found", args[1]),
                NamedTextColor.RED
            ));
            return;
        }

        Exercise exercise = exerciseOpt.get();

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("exercise.info.header", exercise.name()), NamedTextColor.GOLD));
        player.sendMessage(Component.text(
            messages.format("exercise.info.id", exercise.id().toString().substring(0, 8)),
            NamedTextColor.GRAY
        ));
        player.sendMessage(Component.text(
            messages.format("exercise.info.type", exercise.type().displayName()),
            NamedTextColor.GRAY
        ));
        player.sendMessage(Component.text(
            messages.format("exercise.info.state", exercise.state().displayName()),
            NamedTextColor.GRAY
        ));
        player.sendMessage(Component.text(
            messages.format("exercise.info.participants", exercise.participantCount(), exercise.config().maxParticipants()),
            NamedTextColor.GRAY
        ));
        player.sendMessage(Component.text(
            messages.format("exercise.info.soldiers", exercise.totalSoldiers()),
            NamedTextColor.GRAY
        ));

        if (exercise.state().isActive()) {
            player.sendMessage(Component.text(
                messages.format("exercise.info.remaining", exercise.getRemainingMinutes()),
                NamedTextColor.GRAY
            ));
            player.sendMessage(Component.text(
                messages.format("exercise.info.battles", exercise.totalBattles()),
                NamedTextColor.GRAY
            ));
        }

        // 显示参与者列表
        if (!exercise.participants().isEmpty()) {
            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text(messages.format("exercise.info.participants-list"), NamedTextColor.YELLOW));
            for (var participant : exercise.participants()) {
                player.sendMessage(Component.text(
                    messages.format("exercise.info.participant-entry",
                        participant.nationName(),
                        participant.role().displayName(),
                        participant.effectiveSoldiers(),
                        participant.soldierCount(),
                        String.format("%.1f%%", participant.morale())),
                    NamedTextColor.GRAY
                ));
            }
        }

        player.sendMessage(Component.text(""));
    }

    private void handleJoin(Player player, String[] args) {
        // 检查玩家是否在国家中
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("exercise.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation = nationOpt.get();

        // 解析参数
        if (args.length < 3) {
            player.sendMessage(Component.text(
                messages.format("exercise.join.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID exerciseId = parseExerciseId(player, args[1]);
        int soldiers;
        try {
            soldiers = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text(
                messages.format("exercise.invalid-number", args[2]),
                NamedTextColor.RED
            ));
            return;
        }

        boolean success = exerciseService.joinExercise(exerciseId, nation.id().value(), soldiers);

        if (success) {
            player.sendMessage(Component.text(
                messages.format("exercise.joined", nation.name(), soldiers),
                NamedTextColor.GREEN
            ));
        } else {
            String reason = exerciseService.checkJoinEligibility(exerciseId, nation.id().value());
            player.sendMessage(Component.text(
                messages.format(reason),
                NamedTextColor.RED
            ));
        }
    }

    private void handleLeave(Player player, String[] args) {
        // 检查玩家是否在国家中
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("exercise.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation = nationOpt.get();

        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("exercise.leave.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID exerciseId = parseExerciseId(player, args[1]);
        boolean success = exerciseService.leaveExercise(exerciseId, nation.id().value());

        if (success) {
            player.sendMessage(Component.text(
                messages.format("exercise.left"),
                NamedTextColor.GREEN
            ));
        } else {
            player.sendMessage(Component.text(
                messages.format("exercise.leave-failed"),
                NamedTextColor.RED
            ));
        }
    }

    private void handleStart(Player player, String[] args) {
        // 检查玩家是否在国家中
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("exercise.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation = nationOpt.get();

        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("exercise.start.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID exerciseId = parseExerciseId(player, args[1]);
        Optional<Exercise> exerciseOpt = exerciseService.getExercise(exerciseId);

        if (exerciseOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("exercise.not-found", args[1]),
                NamedTextColor.RED
            ));
            return;
        }

        Exercise exercise = exerciseOpt.get();

        // 检查是否是组织者
        if (!exercise.organizerId().equals(nation.id().value())) {
            player.sendMessage(Component.text(
                messages.format("exercise.not-organizer"),
                NamedTextColor.RED
            ));
            return;
        }

        boolean success = exerciseService.startExercise(exerciseId);

        if (success) {
            player.sendMessage(Component.text(
                messages.format("exercise.started", exercise.name()),
                NamedTextColor.GREEN
            ));
        } else {
            player.sendMessage(Component.text(
                messages.format("exercise.start-failed"),
                NamedTextColor.RED
            ));
        }
    }

    private void handleCancel(Player player, String[] args) {
        // 检查玩家是否在国家中
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("exercise.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation = nationOpt.get();

        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("exercise.cancel.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID exerciseId = parseExerciseId(player, args[1]);
        Optional<Exercise> exerciseOpt = exerciseService.getExercise(exerciseId);

        if (exerciseOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("exercise.not-found", args[1]),
                NamedTextColor.RED
            ));
            return;
        }

        Exercise exercise = exerciseOpt.get();

        // 检查是否是组织者
        if (!exercise.organizerId().equals(nation.id().value())) {
            player.sendMessage(Component.text(
                messages.format("exercise.not-organizer"),
                NamedTextColor.RED
            ));
            return;
        }

        ExerciseResult result = exerciseService.endExercise(exerciseId, "组织者取消");

        player.sendMessage(Component.text(
            messages.format("exercise.cancelled", exercise.name()),
            NamedTextColor.YELLOW
        ));
    }

    private void handleEnd(Player player, String[] args) {
        // 与取消相同
        handleCancel(player, args);
    }

    private void handleResult(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("exercise.result.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        // 获取最近的结果
        if (exerciseService instanceof dev.starcore.starcore.module.army.exercise.ExerciseServiceImpl impl) {
            List<ExerciseResult> results = impl.getRecentResults(10);
            if (results.isEmpty()) {
                player.sendMessage(Component.text(
                    messages.format("exercise.no-results"),
                    NamedTextColor.YELLOW
                ));
                return;
            }

            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text(messages.format("exercise.result.header"), NamedTextColor.GOLD));

            for (ExerciseResult result : results) {
                player.sendMessage(Component.text(
                    messages.format("exercise.result.entry",
                        result.exerciseName(),
                        result.type().displayName(),
                        result.totalParticipants(),
                        result.endReason()),
                    NamedTextColor.GRAY
                ));
            }
            player.sendMessage(Component.text(""));
        } else {
            player.sendMessage(Component.text(
                messages.format("exercise.no-results"),
                NamedTextColor.YELLOW
            ));
        }
    }

    private void handlePending(Player player) {
        List<Exercise> pending = exerciseService.getPendingExercises();

        if (pending.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("exercise.no-pending"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("exercise.pending.header"), NamedTextColor.GOLD));

        for (Exercise exercise : pending) {
            String shortId = exercise.id().toString().substring(0, 8);
            player.sendMessage(Component.text(
                messages.format("exercise.pending.entry",
                    shortId,
                    exercise.name(),
                    exercise.type().displayName(),
                    exercise.participantCount()),
                NamedTextColor.GRAY
            ));
        }
        player.sendMessage(Component.text(""));
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("exercise.help.header"), NamedTextColor.GOLD));
        player.sendMessage(Component.text(messages.format("exercise.help.create"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("exercise.help.list"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("exercise.help.pending"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("exercise.help.join"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("exercise.help.start"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("exercise.help.cancel"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    private UUID parseExerciseId(Player player, String idStr) {
        // 支持完整UUID
        if (idStr.length() == 36) {
            return UUID.fromString(idStr);
        }
        // 支持短ID（前8位）
        if (idStr.length() <= 8) {
            // 从所有演习中查找匹配的
            for (Exercise exercise : exerciseService.getActiveExercises()) {
                if (exercise.id().toString().startsWith(idStr)) {
                    return exercise.id();
                }
            }
            for (Exercise exercise : exerciseService.getPendingExercises()) {
                if (exercise.id().toString().startsWith(idStr)) {
                    return exercise.id();
                }
            }
            throw new IllegalArgumentException("Exercise not found: " + idStr);
        }
        throw new IllegalArgumentException("Invalid exercise ID format: " + idStr);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("create", "list", "info", "join", "leave", "start", "cancel", "pending");
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "info", "join", "leave", "start", "cancel" -> {
                    // 返回活跃和待开始的演习ID（简短形式）
                    List<String> ids = new ArrayList<>();
                    for (Exercise e : exerciseService.getActiveExercises()) {
                        ids.add(e.id().toString().substring(0, 8));
                    }
                    for (Exercise e : exerciseService.getPendingExercises()) {
                        ids.add(e.id().toString().substring(0, 8));
                    }
                    yield ids;
                }
                default -> List.of();
            };
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            // 返回演习类型
            return Arrays.stream(ExerciseType.values())
                .map(ExerciseType::key)
                .collect(Collectors.toList());
        }

        return List.of();
    }
}
