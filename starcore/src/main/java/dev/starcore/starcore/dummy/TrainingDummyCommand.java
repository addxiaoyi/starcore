package dev.starcore.starcore.dummy;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 训练假人命令
 * /dummy create <名称> - 创建假人
 * /dummy remove <ID> - 删除假人
 * /dummy list - 列出所有假人
 * /dummy tp <ID> - 传送到假人
 * /dummy stats <ID> - 显示假人统计
 * /dummy reset <ID> - 重置假人统计
 * /dummy set <ID> <属性> <值> - 设置属性
 * /dummy equip <ID> - 复制你的装备到假人
 * /dummy skin <ID> [玩家名] - 设置皮肤
 */
public final class TrainingDummyCommand implements CommandExecutor, TabCompleter {
    private final TrainingDummyManager dummyManager;

    public TrainingDummyCommand(TrainingDummyManager dummyManager) {
        this.dummyManager = dummyManager;
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
            case "stats", "statistics" -> handleStats(sender, args);
            case "reset" -> handleReset(sender, args);
            case "set" -> handleSet(sender, args);
            case "equip", "equipment" -> handleEquip(sender, args);
            case "skin" -> handleSkin(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    /**
     * 创建假人
     */
    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c用法: /dummy create <名称>");
            return;
        }

        String name = args[1];
        TrainingDummy dummy = dummyManager.createDummy(name, player.getLocation());

        // 默认复制玩家装备和皮肤
        dummy.copyEquipment(player);
        dummy.copySkin(player);

        dummyManager.spawnDummy(dummy.getId());

        sender.sendMessage("§a已创建训练假人: " + name);
        sender.sendMessage("§7ID: " + dummy.getId().toString().substring(0, 8));
        sender.sendMessage("§7已复制你的装备和皮肤");
    }

    /**
     * 删除假人
     */
    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c用法: /dummy remove <ID>");
            return;
        }

        try {
            java.util.UUID id = java.util.UUID.fromString(args[1]);
            if (dummyManager.removeDummy(id)) {
                sender.sendMessage("§a已删除假人: " + id);
            } else {
                sender.sendMessage("§c假人不存在: " + id);
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c无效的 UUID");
        }
    }

    /**
     * 列出所有假人
     */
    private void handleList(CommandSender sender) {
        var dummies = dummyManager.getAllDummies();

        if (dummies.isEmpty()) {
            sender.sendMessage("§e当前没有训练假人");
            return;
        }

        sender.sendMessage("§6========== 训练假人列表 ==========");
        for (TrainingDummy dummy : dummies) {
            String status = dummy.isSpawned() ? "§a已生成" : "§7未生成";
            String health = String.format("§c%.1f§7/§c%.1f ❤", dummy.getHealth(), dummy.getMaxHealth());

            sender.sendMessage(String.format("§e%s §7(ID: %s)",
                dummy.getName(), dummy.getId().toString().substring(0, 8)));
            sender.sendMessage("  §7状态: " + status + " §7血量: " + health);
            sender.sendMessage(String.format("  §7伤害: §c%d §7命中: §f%d",
                dummy.getTotalDamageReceived(), dummy.getHitCount()));
        }
        sender.sendMessage("§6总计: " + dummies.size() + " 个假人");
    }

    /**
     * 传送到假人
     */
    private void handleTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c用法: /dummy tp <ID>");
            return;
        }

        try {
            java.util.UUID id = java.util.UUID.fromString(args[1]);
            Optional<TrainingDummy> dummyOpt = dummyManager.getDummy(id);

            if (dummyOpt.isEmpty()) {
                sender.sendMessage("§c假人不存在");
                return;
            }

            player.teleport(dummyOpt.get().getLocation());
            sender.sendMessage("§a已传送到假人: " + dummyOpt.get().getName());

        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c无效的 UUID");
        }
    }

    /**
     * 显示统计
     */
    private void handleStats(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c用法: /dummy stats <ID>");
            return;
        }

        try {
            java.util.UUID id = java.util.UUID.fromString(args[1]);
            Optional<TrainingDummy> dummyOpt = dummyManager.getDummy(id);

            if (dummyOpt.isEmpty()) {
                sender.sendMessage("§c假人不存在");
                return;
            }

            TrainingDummy dummy = dummyOpt.get();

            sender.sendMessage("§6========== 训练假人统计 ==========");
            sender.sendMessage("§e名称: §f" + dummy.getName());
            sender.sendMessage("§e血量: " + String.format("§c%.1f§7/§c%.1f ❤", dummy.getHealth(), dummy.getMaxHealth()));
            sender.sendMessage("§e血量百分比: " + String.format("§c%.1f%%", dummy.getHealthPercentage()));
            sender.sendMessage("§e总伤害: §c" + dummy.getTotalDamageReceived());
            sender.sendMessage("§e命中次数: §f" + dummy.getHitCount());
            sender.sendMessage(String.format("§e平均伤害: §c%.2f", dummy.getAverageDamage()));
            sender.sendMessage(String.format("§eDPS: §c%.2f", dummy.getDPS()));

            if (dummy.getLastAttacker() != null) {
                Player attacker = Bukkit.getPlayer(dummy.getLastAttacker());
                String attackerName = attacker != null ? attacker.getName() : "未知";
                sender.sendMessage("§e最后攻击者: §f" + attackerName);
            }

            sender.sendMessage("§6================================");

        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c无效的 UUID");
        }
    }

    /**
     * 重置统计
     */
    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c用法: /dummy reset <ID>");
            return;
        }

        try {
            java.util.UUID id = java.util.UUID.fromString(args[1]);
            Optional<TrainingDummy> dummyOpt = dummyManager.getDummy(id);

            if (dummyOpt.isEmpty()) {
                sender.sendMessage("§c假人不存在");
                return;
            }

            TrainingDummy dummy = dummyOpt.get();
            dummy.resetHealth();
            dummy.resetStats();

            sender.sendMessage("§a已重置假人统计: " + dummy.getName());

        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c无效的 UUID");
        }
    }

    /**
     * 设置属性
     */
    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§c用法: /dummy set <ID> <属性> <值>");
            return;
        }

        try {
            java.util.UUID id = java.util.UUID.fromString(args[1]);
            Optional<TrainingDummy> dummyOpt = dummyManager.getDummy(id);

            if (dummyOpt.isEmpty()) {
                sender.sendMessage("§c假人不存在");
                return;
            }

            TrainingDummy dummy = dummyOpt.get();
            String property = args[2].toLowerCase();
            String value = args[3];

            switch (property) {
                case "health", "hp" -> {
                    try {
                        double health = Double.parseDouble(value);
                        if (health < 0) {
                            sender.sendMessage("§c血量不能为负数");
                            return;
                        }
                        dummy.setHealth(health);
                        sender.sendMessage("§a已设置血量: " + value);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§c无效的血量值: " + value);
                    }
                }
                case "maxhealth", "maxhp" -> {
                    try {
                        double maxHealth = Double.parseDouble(value);
                        if (maxHealth <= 0) {
                            sender.sendMessage("§c最大血量必须大于 0");
                            return;
                        }
                        dummy.setMaxHealth(maxHealth);
                        sender.sendMessage("§a已设置最大血量: " + value);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§c无效的最大血量值: " + value);
                    }
                }
                case "invulnerable", "god" -> {
                    dummy.setInvulnerable(Boolean.parseBoolean(value));
                    sender.sendMessage("§a已设置无敌: " + value);
                }
                case "healthbar" -> {
                    dummy.setShowHealthBar(Boolean.parseBoolean(value));
                    sender.sendMessage("§a已设置显示血量条: " + value);
                }
                case "autorespawn", "respawn" -> {
                    dummy.setAutoRespawn(Boolean.parseBoolean(value));
                    sender.sendMessage("§a已设置自动重生: " + value);
                }
                case "respawndelay", "delay" -> {
                    dummy.setRespawnDelay(Long.parseLong(value));
                    sender.sendMessage("§a已设置重生延迟: " + value + "ms");
                }
                case "lookatplayer", "look" -> {
                    dummy.setLookAtPlayer(Boolean.parseBoolean(value));
                    sender.sendMessage("§a已设置看向玩家: " + value);
                }
                default -> sender.sendMessage("§c未知属性: " + property);
            }

        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c无效的参数");
        }
    }

    /**
     * 复制装备
     */
    private void handleEquip(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c用法: /dummy equip <ID>");
            return;
        }

        try {
            java.util.UUID id = java.util.UUID.fromString(args[1]);
            Optional<TrainingDummy> dummyOpt = dummyManager.getDummy(id);

            if (dummyOpt.isEmpty()) {
                sender.sendMessage("§c假人不存在");
                return;
            }

            TrainingDummy dummy = dummyOpt.get();
            dummy.copyEquipment(player);

            // 重新生成以应用装备
            if (dummy.isSpawned()) {
                dummyManager.despawnDummy(id);
                dummyManager.spawnDummy(id);
            }

            sender.sendMessage("§a已复制你的装备到假人: " + dummy.getName());

        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c无效的 UUID");
        }
    }

    /**
     * 设置皮肤
     */
    private void handleSkin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c用法: /dummy skin <ID> [玩家名]");
            return;
        }

        try {
            java.util.UUID id = java.util.UUID.fromString(args[1]);
            Optional<TrainingDummy> dummyOpt = dummyManager.getDummy(id);

            if (dummyOpt.isEmpty()) {
                sender.sendMessage("§c假人不存在");
                return;
            }

            TrainingDummy dummy = dummyOpt.get();

            if (args.length >= 3) {
                dummy.setSkin(args[2]);
                sender.sendMessage("§a已设置皮肤为: " + args[2]);
            } else if (sender instanceof Player player) {
                dummy.copySkin(player);
                sender.sendMessage("§a已复制你的皮肤");
            } else {
                sender.sendMessage("§c请指定玩家名");
                return;
            }

            // 重新生成以应用皮肤
            if (dummy.isSpawned()) {
                dummyManager.despawnDummy(id);
                dummyManager.spawnDummy(id);
            }

        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c无效的 UUID");
        }
    }

    /**
     * 显示帮助
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6========== 训练假人命令 ==========");
        sender.sendMessage("§e/dummy create <名称> §7- 创建假人");
        sender.sendMessage("§e/dummy remove <ID> §7- 删除假人");
        sender.sendMessage("§e/dummy list §7- 列出所有假人");
        sender.sendMessage("§e/dummy tp <ID> §7- 传送到假人");
        sender.sendMessage("§e/dummy stats <ID> §7- 显示统计");
        sender.sendMessage("§e/dummy reset <ID> §7- 重置统计");
        sender.sendMessage("§e/dummy set <ID> <属性> <值> §7- 设置属性");
        sender.sendMessage("§e/dummy equip <ID> §7- 复制装备");
        sender.sendMessage("§e/dummy skin <ID> [玩家] §7- 设置皮肤");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                  @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "remove", "list", "tp", "stats", "reset", "set", "equip", "skin");
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return Arrays.asList("health", "maxhealth", "invulnerable", "healthbar",
                "autorespawn", "respawndelay", "lookatplayer");
        }

        return new ArrayList<>();
    }
}
