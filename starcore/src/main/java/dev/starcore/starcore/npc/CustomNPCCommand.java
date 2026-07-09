package dev.starcore.starcore.npc;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 自定义 NPC 命令
 * /npc create <名称> <类型> - 创建 NPC
 * /npc remove <ID> - 删除 NPC
 * /npc list - 列出所有 NPC
 * /npc tp <ID> - 传送到 NPC
 * /npc set <ID> <属性> <值> - 设置 NPC 属性
 * /npc dialogue <ID> add/remove/clear <文本> - 管理对话
 * /npc command <ID> add/remove/clear <命令> - 管理命令
 */
public final class CustomNPCCommand implements CommandExecutor, TabCompleter {
    private final CustomNPCManager npcManager;

    public CustomNPCCommand(CustomNPCManager npcManager) {
        this.npcManager = npcManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create" -> handleCreate(sender, args);
            case "remove", "delete" -> handleRemove(sender, args);
            case "list" -> handleList(sender);
            case "tp", "teleport" -> handleTeleport(sender, args);
            case "set" -> handleSet(sender, args);
            case "dialogue", "dialog" -> handleDialogue(sender, args);
            case "command", "cmd" -> handleCommand(sender, args);
            case "info" -> handleInfo(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    /**
     * 创建 NPC
     */
    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage("§c用法: /npc create <名称> <类型>");
            return;
        }

        String name = args[1];
        EntityType type;

        try {
            type = EntityType.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c无效的实体类型: " + args[2]);
            return;
        }

        CustomNPC npc = npcManager.createNPC(name, type, player.getLocation());
        npcManager.spawnNPC(npc.getId());

        sender.sendMessage("§a已创建 NPC: " + name + " (ID: " + npc.getId() + ")");
    }

    /**
     * 删除 NPC
     */
    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c用法: /npc remove <ID>");
            return;
        }

        try {
            java.util.UUID id = java.util.UUID.fromString(args[1]);
            if (npcManager.removeNPC(id)) {
                sender.sendMessage("§a已删除 NPC: " + id);
            } else {
                sender.sendMessage("§cNPC 不存在: " + id);
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c无效的 UUID");
        }
    }

    /**
     * 列出所有 NPC
     */
    private void handleList(CommandSender sender) {
        var npcs = npcManager.getAllNPCs();

        if (npcs.isEmpty()) {
            sender.sendMessage("§e当前没有 NPC");
            return;
        }

        sender.sendMessage("§6========== NPC 列表 ==========");
        for (CustomNPC npc : npcs) {
            String status = npc.isSpawned() ? "§a已生成" : "§7未生成";
            sender.sendMessage(String.format("§e%s §7(ID: %s) %s",
                npc.getName(), npc.getId().toString().substring(0, 8), status));
        }
        sender.sendMessage("§6总计: " + npcs.size() + " 个 NPC");
    }

    /**
     * 传送到 NPC
     */
    private void handleTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c用法: /npc tp <ID>");
            return;
        }

        try {
            java.util.UUID id = java.util.UUID.fromString(args[1]);
            Optional<CustomNPC> npcOpt = npcManager.getNPC(id);

            if (npcOpt.isEmpty()) {
                sender.sendMessage("§cNPC 不存在");
                return;
            }

            player.teleport(npcOpt.get().getLocation());
            sender.sendMessage("§a已传送到 NPC: " + npcOpt.get().getName());

        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c无效的 UUID");
        }
    }

    /**
     * 设置 NPC 属性
     */
    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§c用法: /npc set <ID> <属性> <值>");
            return;
        }

        try {
            java.util.UUID id = java.util.UUID.fromString(args[1]);
            Optional<CustomNPC> npcOpt = npcManager.getNPC(id);

            if (npcOpt.isEmpty()) {
                sender.sendMessage("§cNPC 不存在");
                return;
            }

            CustomNPC npc = npcOpt.get();
            String property = args[2].toLowerCase();
            String value = args[3];

            switch (property) {
                case "name" -> {
                    npc.setName(value);
                    sender.sendMessage("§a已设置名称: " + value);
                }
                case "displayname" -> {
                    npc.setDisplayName(value.replace("&", "§"));
                    sender.sendMessage("§a已设置显示名称");
                }
                case "invulnerable" -> {
                    npc.setInvulnerable(Boolean.parseBoolean(value));
                    sender.sendMessage("§a已设置无敌: " + value);
                }
                case "lookatplayer" -> {
                    npc.setLookAtPlayer(Boolean.parseBoolean(value));
                    sender.sendMessage("§a已设置看向玩家: " + value);
                }
                case "range" -> {
                    npc.setInteractionRange(Double.parseDouble(value));
                    sender.sendMessage("§a已设置交互范围: " + value);
                }
                case "permission" -> {
                    npc.setPermission(value);
                    sender.sendMessage("§a已设置权限: " + value);
                }
                default -> sender.sendMessage("§c未知属性: " + property);
            }

        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c无效的参数");
        }
    }

    /**
     * 管理对话
     */
    private void handleDialogue(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c用法: /npc dialogue <ID> add/remove/clear [文本]");
            return;
        }

        try {
            java.util.UUID id = java.util.UUID.fromString(args[1]);
            Optional<CustomNPC> npcOpt = npcManager.getNPC(id);

            if (npcOpt.isEmpty()) {
                sender.sendMessage("§cNPC 不存在");
                return;
            }

            CustomNPC npc = npcOpt.get();
            String action = args[2].toLowerCase();

            switch (action) {
                case "add" -> {
                    if (args.length < 4) {
                        sender.sendMessage("§c请提供对话文本");
                        return;
                    }
                    String dialogue = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                    npc.addDialogue(dialogue.replace("&", "§"));
                    sender.sendMessage("§a已添加对话");
                }
                case "remove" -> {
                    if (args.length < 4) {
                        sender.sendMessage("§c请提供要删除的对话");
                        return;
                    }
                    String dialogue = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                    npc.removeDialogue(dialogue.replace("&", "§"));
                    sender.sendMessage("§a已删除对话");
                }
                case "clear" -> {
                    npc.clearDialogues();
                    sender.sendMessage("§a已清空所有对话");
                }
                default -> sender.sendMessage("§c未知操作: " + action);
            }

        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c无效的 UUID");
        }
    }

    /**
     * 管理命令
     */
    private void handleCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c用法: /npc command <ID> add/remove/clear [命令]");
            return;
        }

        try {
            java.util.UUID id = java.util.UUID.fromString(args[1]);
            Optional<CustomNPC> npcOpt = npcManager.getNPC(id);

            if (npcOpt.isEmpty()) {
                sender.sendMessage("§cNPC 不存在");
                return;
            }

            CustomNPC npc = npcOpt.get();
            String action = args[2].toLowerCase();

            switch (action) {
                case "add" -> {
                    if (args.length < 4) {
                        sender.sendMessage("§c请提供命令");
                        return;
                    }
                    String cmd = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                    npc.addCommand(cmd);
                    sender.sendMessage("§a已添加命令");
                }
                case "remove" -> {
                    if (args.length < 4) {
                        sender.sendMessage("§c请提供要删除的命令");
                        return;
                    }
                    String cmd = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                    npc.removeCommand(cmd);
                    sender.sendMessage("§a已删除命令");
                }
                case "clear" -> {
                    npc.clearCommands();
                    sender.sendMessage("§a已清空所有命令");
                }
                default -> sender.sendMessage("§c未知操作: " + action);
            }

        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c无效的 UUID");
        }
    }

    /**
     * 显示 NPC 信息
     */
    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c用法: /npc info <ID>");
            return;
        }

        try {
            java.util.UUID id = java.util.UUID.fromString(args[1]);
            Optional<CustomNPC> npcOpt = npcManager.getNPC(id);

            if (npcOpt.isEmpty()) {
                sender.sendMessage("§cNPC 不存在");
                return;
            }

            CustomNPC npc = npcOpt.get();

            sender.sendMessage("§6========== NPC 信息 ==========");
            sender.sendMessage("§eID: §f" + npc.getId());
            sender.sendMessage("§e名称: §f" + npc.getName());
            sender.sendMessage("§e显示名称: " + npc.getDisplayName());
            sender.sendMessage("§e类型: §f" + npc.getEntityType());
            sender.sendMessage("§e状态: " + (npc.isSpawned() ? "§a已生成" : "§7未生成"));
            sender.sendMessage("§e对话数量: §f" + npc.getDialogues().size());
            sender.sendMessage("§e命令数量: §f" + npc.getCommands().size());
            sender.sendMessage("§e交互范围: §f" + npc.getInteractionRange());

        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c无效的 UUID");
        }
    }

    /**
     * 显示帮助
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6========== NPC 命令 ==========");
        sender.sendMessage("§e/npc create <名称> <类型> §7- 创建 NPC");
        sender.sendMessage("§e/npc remove <ID> §7- 删除 NPC");
        sender.sendMessage("§e/npc list §7- 列出所有 NPC");
        sender.sendMessage("§e/npc tp <ID> §7- 传送到 NPC");
        sender.sendMessage("§e/npc info <ID> §7- 显示 NPC 信息");
        sender.sendMessage("§e/npc set <ID> <属性> <值> §7- 设置属性");
        sender.sendMessage("§e/npc dialogue <ID> add/remove/clear [文本]");
        sender.sendMessage("§e/npc command <ID> add/remove/clear [命令]");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                  @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "remove", "list", "tp", "set", "dialogue", "command", "info");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            return null; // 玩家输入名称
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            return Arrays.stream(EntityType.values())
                .filter(EntityType::isAlive)
                .map(EntityType::name)
                .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return Arrays.asList("name", "displayname", "invulnerable", "lookatplayer", "range", "permission");
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("dialogue") || args[0].equalsIgnoreCase("command"))) {
            return Arrays.asList("add", "remove", "clear");
        }

        return new ArrayList<>();
    }
}
