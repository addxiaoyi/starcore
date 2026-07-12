package dev.starcore.starcore.module.policy.command;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.policy.PolicyService;
import dev.starcore.starcore.module.policy.model.PolicyActivationFailure;
import dev.starcore.starcore.module.policy.model.PolicyActivationResult;
import dev.starcore.starcore.module.policy.model.PolicyDefinition;
import dev.starcore.starcore.module.policy.model.PolicyRuntimeState;
import dev.starcore.starcore.module.treasury.TreasuryService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 国策命令处理器
 * 提供 /policy activate, /policy clear, /policy list 命令
 *
 * 中文别名:
 *   activate/激活 → 激活国策
 *   clear/清除 → 清除国策
 *   list/列表 → 列出所有国策
 *   info/信息 → 查看国策信息
 *   status/状态 → 查看国家国策状态
 */
public class PolicyCommandHandler {

    private final StarCoreContext context;
    private final PolicyService policyService;
    private final TreasuryService treasuryService;
    private final MessageService messages;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    public PolicyCommandHandler(StarCoreContext context, PolicyService policyService,
                               TreasuryService treasuryService, MessageService messages) {
        this.context = context;
        this.policyService = policyService;
        this.treasuryService = treasuryService;
        this.messages = messages;
    }

    /**
     * 处理国策命令
     */
    public boolean handle(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sendUsage(sender);
            return true;
        }

        String subcommand = normalizeSubCommand(args[0].toLowerCase());
        switch (subcommand) {
            case "activate" -> handleActivate(sender, args);
            case "clear" -> handleClear(sender, args);
            case "list" -> handleList(sender, args);
            case "info" -> handleInfo(sender, args);
            case "status" -> handleStatus(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    /**
     * 规范化子命令，支持中英文别名
     */
    private String normalizeSubCommand(String input) {
        return switch (input.toLowerCase()) {
            case "activate", "激活", "启用", "开启" -> "activate";
            case "clear", "清除", "取消", "关闭" -> "clear";
            case "list", "列表", "列", "所有", "ls" -> "list";
            case "info", "信息", "详", "详情", "i" -> "info";
            case "status", "状态", "状", "查看", "s" -> "status";
            default -> input;
        };
    }

    /**
     * 激活国策: /policy activate <nation> <policy_key>
     */
    private void handleActivate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令需要玩家执行", NamedTextColor.RED));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(Component.text("用法: /policy activate <nation_name> <policy_key>", NamedTextColor.YELLOW));
            return;
        }

        String nationName = args[1];
        String policyKey = args[2].toLowerCase(Locale.ROOT);

        // 查找国家
        Optional<Nation> nationOpt = findNationByName(nationName);
        if (nationOpt.isEmpty()) {
            sender.sendMessage(Component.text("国家不存在: " + nationName, NamedTextColor.RED));
            return;
        }

        Nation nation = nationOpt.get();

        // 检查权限
        if (!hasPolicyAdminPermission(player, nation)) {
            sender.sendMessage(Component.text("没有权限管理国家国策", NamedTextColor.RED));
            return;
        }

        // 检查国策是否存在
        Optional<PolicyDefinition> policyOpt = policyService.policyDefinition(policyKey);
        if (policyOpt.isEmpty()) {
            sender.sendMessage(Component.text("国策不存在: " + policyKey, NamedTextColor.RED));
            return;
        }

        PolicyDefinition definition = policyOpt.get();

        // 激活国策
        PolicyActivationResult result = policyService.activatePolicy(nation.id(), policyKey, treasuryService);

        if (result.successful()) {
            sender.sendMessage(Component.text("成功激活国策: ", NamedTextColor.GREEN)
                .append(Component.text(definition.displayName(), NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("效果: " + describeEffects(definition.effects()), NamedTextColor.GRAY));

            BigDecimal cost = definition.treasuryCost();
            if (cost.signum() > 0) {
                BigDecimal balance = treasuryService.balance(nation.id());
                sender.sendMessage(Component.text("消耗: " + cost.toPlainString() + " | 剩余: " + balance.toPlainString(), NamedTextColor.GRAY));
            }
        } else {
            sender.sendMessage(Component.text("国策激活失败: " + describeFailure(result.failure()), NamedTextColor.RED));
        }
    }

    /**
     * 清除国策: /policy clear <nation>
     */
    private void handleClear(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令需要玩家执行", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /policy clear <nation_name>", NamedTextColor.YELLOW));
            return;
        }

        String nationName = args[1];

        // 查找国家
        Optional<Nation> nationOpt = findNationByName(nationName);
        if (nationOpt.isEmpty()) {
            sender.sendMessage(Component.text("国家不存在: " + nationName, NamedTextColor.RED));
            return;
        }

        Nation nation = nationOpt.get();

        // 检查权限
        if (!hasPolicyAdminPermission(player, nation)) {
            sender.sendMessage(Component.text("没有权限管理国家国策", NamedTextColor.RED));
            return;
        }

        // 检查是否有激活的国策
        Optional<String> activeKey = policyService.activePolicy(nation.id());
        if (activeKey.isEmpty()) {
            sender.sendMessage(Component.text("国家没有激活的国策", NamedTextColor.YELLOW));
            return;
        }

        // 清除国策
        boolean cleared = policyService.clearActivePolicy(nation.id());
        if (cleared) {
            sender.sendMessage(Component.text("已清除国家国策: " + nation.name(), NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("清除国策失败", NamedTextColor.RED));
        }
    }

    /**
     * 列出所有国策: /policy list [nation_name]
     */
    private void handleList(CommandSender sender, String[] args) {
        String nationName = null;
        if (args.length >= 2) {
            nationName = args[1];
        } else if (sender instanceof Player player) {
            // 如果没有指定国家，尝试获取玩家所在国家
            Optional<Nation> playerNation = getNationOfPlayer(player);
            if (playerNation.isPresent()) {
                nationName = playerNation.get().name();
            }
        }

        sender.sendMessage(Component.text("=== 国策列表 ===", NamedTextColor.GOLD));

        Collection<PolicyDefinition> allPolicies = policyService.policyDefinitions();
        if (allPolicies.isEmpty()) {
            sender.sendMessage(Component.text("暂无国策", NamedTextColor.GRAY));
            return;
        }

        NationId nationId = null;
        Optional<String> activeKey = Optional.empty();
        Collection<String> unlockedKeys = List.of();

        if (nationName != null) {
            Optional<Nation> nationOpt = findNationByName(nationName);
            if (nationOpt.isPresent()) {
                Nation nation = nationOpt.get();
                nationId = nation.id();
                activeKey = policyService.activePolicy(nationId);
                unlockedKeys = policyService.unlockedPolicies(nationId);
            }
        }

        for (PolicyDefinition def : allPolicies) {
            String prefix = "  ";
            String suffix = "";

            if (nationId != null) {
                if (def.key().equals(activeKey.orElse(null))) {
                    prefix = ">";
                    suffix = " §a[激活中]";
                } else if (unlockedKeys.contains(def.key())) {
                    prefix = " ";
                    suffix = " §e[可激活]";
                } else {
                    prefix = " ";
                    suffix = " §7[需解锁]";
                }
                // audit B-157: 显示冷却剩余时间
                long cooldownRemaining = getPolicyCooldownRemaining(nationId, def);
                if (cooldownRemaining > 0 && !def.key().equals(activeKey.orElse(null))) {
                    suffix += " §c[冷却: " + formatDuration(cooldownRemaining) + "]";
                }
            }

            String category = def.category().name().substring(0, 3);
            sender.sendMessage(Component.text(prefix + "[" + category + "] ")
                .append(Component.text(def.displayName(), NamedTextColor.WHITE))
                .append(Component.text(suffix, NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("    §7消耗: " + def.treasuryCost().toPlainString()
                + " | 持续: " + formatDuration(def.durationSeconds())));
        }
    }

    /**
     * 获取某国家某政策还剩多少秒冷却
     * audit B-157: 用于在 list/status 中显示冷却剩余时间
     */
    private long getPolicyCooldownRemaining(NationId nationId, PolicyDefinition def) {
        // 使用 PolicyService 新增的 cooldownRemaining API 获取精确冷却时间
        return policyService.cooldownRemaining(nationId, def.key(), Instant.now());
    }

    /**
     * 查看国策信息: /policy info <policy_key>
     */
    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /policy info <policy_key>", NamedTextColor.YELLOW));
            return;
        }

        String policyKey = args[1].toLowerCase(Locale.ROOT);
        Optional<PolicyDefinition> policyOpt = policyService.policyDefinition(policyKey);

        if (policyOpt.isEmpty()) {
            sender.sendMessage(Component.text("国策不存在: " + policyKey, NamedTextColor.RED));
            return;
        }

        PolicyDefinition def = policyOpt.get();
        sender.sendMessage(Component.text("=== 国策信息 ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("名称: ", NamedTextColor.GRAY)
            .append(Component.text(def.displayName(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("分类: ", NamedTextColor.GRAY)
            .append(Component.text(def.category().name(), NamedTextColor.YELLOW)));
        sender.sendMessage(Component.text("Key: ", NamedTextColor.GRAY)
            .append(Component.text(def.key(), NamedTextColor.DARK_GRAY)));
        sender.sendMessage(Component.text("消耗: ", NamedTextColor.GRAY)
            .append(Component.text(def.treasuryCost().toPlainString(), NamedTextColor.GOLD)));
        sender.sendMessage(Component.text("持续: ", NamedTextColor.GRAY)
            .append(Component.text(formatDuration(def.durationSeconds()), NamedTextColor.AQUA)));
        // audit B-158: 冷却时间 0 表示无冷却，应显示 "无" 而非 "无限"
        long coolSeconds = def.cooldownSeconds();
        sender.sendMessage(Component.text("冷却: ", NamedTextColor.GRAY)
            .append(Component.text(coolSeconds == 0 ? "无" : formatDuration(coolSeconds), NamedTextColor.RED)));

        if (!def.prerequisiteKeys().isEmpty()) {
            sender.sendMessage(Component.text("前置: ", NamedTextColor.GRAY)
                .append(Component.text(String.join(", ", def.prerequisiteKeys()), NamedTextColor.LIGHT_PURPLE)));
        }

        if (!def.conflictKeys().isEmpty()) {
            sender.sendMessage(Component.text("冲突: ", NamedTextColor.GRAY)
                .append(Component.text(String.join(", ", def.conflictKeys()), NamedTextColor.DARK_RED)));
        }

        sender.sendMessage(Component.text("效果:", NamedTextColor.GOLD));
        for (var effect : def.effects()) {
            sender.sendMessage(Component.text("  - " + effect.description(), NamedTextColor.GREEN));
            // audit B-158: 修负 modifier 显示 "+-X"，改为带符号百分比
            double pct = effect.modifier() * 100;
            String sign = pct >= 0 ? "+" : "-";
            sender.sendMessage(Component.text("    范围: " + effect.scope() + " | 加成: " + sign
                + String.format("%.0f", Math.abs(pct)) + "%", NamedTextColor.DARK_GRAY));
        }
    }

    /**
     * 查看国家国策状态: /policy status [nation_name]
     */
    private void handleStatus(CommandSender sender, String[] args) {
        String nationName = null;
        if (args.length >= 2) {
            nationName = args[1];
        } else if (sender instanceof Player player) {
            Optional<Nation> playerNation = getNationOfPlayer(player);
            if (playerNation.isPresent()) {
                nationName = playerNation.get().name();
            }
        }

        if (nationName == null) {
            sender.sendMessage(Component.text("用法: /policy status [nation_name]", NamedTextColor.YELLOW));
            return;
        }

        Optional<Nation> nationOpt = findNationByName(nationName);
        if (nationOpt.isEmpty()) {
            sender.sendMessage(Component.text("国家不存在: " + nationName, NamedTextColor.RED));
            return;
        }

        Nation nation = nationOpt.get();
        NationId nationId = nation.id();

        sender.sendMessage(Component.text("=== 国策状态: " + nation.name() + " ===", NamedTextColor.GOLD));

        Optional<PolicyDefinition> activeDef = policyService.activePolicyDefinition(nationId);
        Optional<PolicyRuntimeState> activeState = policyService.activePolicyState(nationId);

        if (activeDef.isPresent() && activeState.isPresent()) {
            PolicyDefinition def = activeDef.get();
            PolicyRuntimeState state = activeState.get();

            sender.sendMessage(Component.text("当前国策: ", NamedTextColor.GRAY)
                .append(Component.text(def.displayName(), NamedTextColor.GREEN)));
            sender.sendMessage(Component.text("效果: " + describeEffects(def.effects()), NamedTextColor.DARK_GRAY));

            Instant now = Instant.now();
            if (state.expiresAt().isAfter(now)) {
                long remaining = state.expiresAt().getEpochSecond() - now.getEpochSecond();
                sender.sendMessage(Component.text("剩余时间: " + formatDuration(remaining), NamedTextColor.YELLOW));
            } else {
                sender.sendMessage(Component.text("状态: 已过期", NamedTextColor.RED));
            }

            sender.sendMessage(Component.text("过期时间: " + DATE_FORMATTER.format(state.expiresAt()), NamedTextColor.DARK_GRAY));
        } else {
            sender.sendMessage(Component.text("当前没有激活的国策", NamedTextColor.GRAY));
        }

        // 显示已解锁的国策
        Collection<String> unlocked = policyService.unlockedPolicies(nationId);
        if (!unlocked.isEmpty()) {
            sender.sendMessage(Component.text("已解锁国策: " + String.join(", ", unlocked), NamedTextColor.DARK_GRAY));
        }
    }

    /**
     * Tab补全
     */
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // 第一级补全：所有子命令（中英文）
            return List.of("activate", "激活", "clear", "清除", "list", "列表", "info", "信息", "status", "状态");
        }

        if (args.length == 2) {
            String normalized = normalizeSubCommand(args[0]);
            if ("clear".equals(normalized) || "status".equals(normalized)) {
                return getOnlineNationNames();
            }
            if ("info".equals(normalized) || "activate".equals(normalized)) {
                return new java.util.ArrayList<>(policyService.availablePolicies());
            }
        }

        if (args.length == 3 && "activate".equals(normalizeSubCommand(args[0]))) {
            return new java.util.ArrayList<>(policyService.availablePolicies());
        }

        return List.of();
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("=== 国策命令 ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/policy activate <nation> <key> - 激活国策", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/policy clear <nation> - 清除国策", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/policy list [nation] - 列出所有国策", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/policy info <key> - 查看国策信息", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/policy status [nation] - 查看国策状态", NamedTextColor.YELLOW));
    }

    private Optional<Nation> findNationByName(String name) {
        var nationService = context.serviceRegistry().find(dev.starcore.starcore.module.nation.NationService.class).orElse(null);
        if (nationService == null) {
            return Optional.empty();
        }
        // audit B-157: 国家名做忽略大小写匹配，先精确匹配再 fallback
        Optional<Nation> exact = nationService.nationByName(name);
        if (exact.isPresent()) {
            return exact;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return nationService.nations().stream()
            .filter(n -> n.name() != null && n.name().toLowerCase(Locale.ROOT).equals(lower))
            .findFirst();
    }

    private Optional<Nation> getNationOfPlayer(Player player) {
        var nationService = context.serviceRegistry().find(dev.starcore.starcore.module.nation.NationService.class).orElse(null);
        if (nationService == null) {
            return Optional.empty();
        }
        return nationService.nationOf(player.getUniqueId());
    }

    private boolean hasPolicyAdminPermission(Player player, Nation nation) {
        // 检查是否是管理员
        // audit B-156: starcore.admin 绕过检查需记录到审计日志
        if (player.hasPermission("starcore.admin")) {
            try {
                var audit = context.serviceRegistry().find(dev.starcore.starcore.audit.AuditLogService.class);
                if (audit.isPresent()) {
                    audit.get().logPermissionChange(
                        player.getUniqueId(),
                        player.getName(),
                        "starcore.admin.policy.override:" + nation.id().value(),
                        true
                    );
                } else {
                    context.plugin().getLogger().warning("Policy admin override: " + player.getName() + " used starcore.admin on nation " + nation.name());
                }
            } catch (Throwable ignored) {
                // audit B-156 (partial): AuditService 未启用或不存在时仅日志
            }
            return true;
        }
        // 检查是否是国家创建者
        if (nation.founderId().equals(player.getUniqueId())) {
            return true;
        }
        // 检查是否是国家管理员
        return nation.members().stream()
            .filter(m -> m.playerId().equals(player.getUniqueId()))
            .anyMatch(m -> "admin".equals(m.rank()));
    }

    private List<String> getOnlineNationNames() {
        var nationService = context.serviceRegistry().find(dev.starcore.starcore.module.nation.NationService.class).orElse(null);
        if (nationService == null) {
            return List.of();
        }
        return nationService.nations().stream()
            .map(Nation::name)
            .toList();
    }

    private String describeFailure(PolicyActivationFailure failure) {
        if (failure == null) return "未知错误";
        return switch (failure) {
            case UNKNOWN_POLICY -> "国策不存在";
            case ALREADY_ACTIVE -> "国策已在激活状态";
            case CONFLICTING_POLICY -> "与当前激活的国策冲突";
            case MISSING_PREREQUISITE -> "缺少前置国策";
            case ON_COOLDOWN -> "国策处于冷却中";
            case MISSING_TREASURY_SERVICE -> "国库服务不可用";
            case INSUFFICIENT_TREASURY -> "国库余额不足";
            case COOLDOWN_NOT_EXPIRED -> "国策处于冷却中";
            case PREREQUISITES_NOT_MET -> "缺少前置国策";
            case MUTUALLY_EXCLUSIVE -> "与当前激活的国策冲突";
            case NOT_LEADER -> "没有国策管理权限";
        };
    }

    private String describeEffects(List<dev.starcore.starcore.module.policy.model.PolicyEffect> effects) {
        if (effects == null || effects.isEmpty()) return "无效果";
        return effects.stream()
            .map(e -> "+" + String.format("%.0f", e.modifier() * 100) + "% " + e.description())
            .reduce((a, b) -> a + " | " + b)
            .orElse("无效果");
    }

    private String formatDuration(long seconds) {
        if (seconds <= 0) return "无限";
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 24) {
            return (hours / 24) + "天" + (hours % 24) + "小时";
        }
        if (hours > 0) {
            return hours + "小时" + minutes + "分钟";
        }
        return minutes + "分钟";
    }
}
